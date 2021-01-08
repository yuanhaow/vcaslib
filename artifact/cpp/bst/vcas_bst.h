
#ifndef VCAS_BST_H
#define	VCAS_BST_H

#include <string>
#include <cstring>
#include <fstream>
#include <iostream>
#include <sstream>
#include <set>
#include <csignal>
#include <unistd.h>
#include <sys/types.h>
#include <pthread.h>
#include <stdexcept>
#include <bitset>
#include "record_manager.h"
#include "random.h"
#include "vcas_scxrecord.h"
#include "vcas_node.h"

#ifndef MAX_NODES_INSERTED_OR_DELETED_ATOMICALLY
    // define BEFORE including rq_provider.h
    #define MAX_NODES_INSERTED_OR_DELETED_ATOMICALLY 4
#endif
#include "rq_provider.h"

using namespace std;

namespace vcas_bst_ns {

    template <class K, class V>
    class ReclamationInfo {
    public:
        int type;
        void *llxResults[MAX_NODES];
        Node<K,V> *nodes[MAX_NODES];
        int state;
        int numberOfNodes;
        int numberOfNodesToFreeze;
        int numberOfNodesToReclaim;
        int numberOfNodesAllocated;
        int path;
        //bool capacityAborted[NUMBER_OF_PATHS];
        int lastAbort;
    };

    template <class K, class V, class Compare, class RecManager>
    class vcas_bst {
    private:
        RecManager * const recmgr;
        RQProvider<K, V, Node<K,V>, vcas_bst<K,V,Compare,RecManager>, RecManager, false, false> * const rqProvider;
        volatile int lock; // used for TLE

        const int N; // number of violations to allow on a search path before we fix everything on it
        Node<K,V> *root;        // actually const
        Compare cmp;

        // allocatedNodes[tid*PREFETCH_SIZE_WORDS+i] = an allocated node
        //     for i = 0..MAX_NODES-2
        Node<K,V> **allocatedNodes;
        #define GET_ALLOCATED_NODE_PTR(tid, i) allocatedNodes[tid*(PREFETCH_SIZE_WORDS+MAX_NODES)+i]
        #define REPLACE_ALLOCATED_NODE(tid, i) { GET_ALLOCATED_NODE_PTR(tid, i) = allocateNode(tid); /*GET_ALLOCATED_NODE_PTR(tid, i)->left.store((uintptr_t) NULL, memory_order_relaxed);*/ }

        #define IS_SENTINEL(node, parent) ((node)->key == NO_KEY || (parent)->key == NO_KEY)

    #ifdef USE_DEBUGCOUNTERS
        // debug info
        debugCounters * const counters;
    #endif

        // descriptor reduction algorithm
        #define DESC1_ARRAY records
        #define DESC1_T SCXRecord<K,V>
        #define MUTABLES_OFFSET_ALLFROZEN 0
        #define MUTABLES_OFFSET_STATE 1
        #define MUTABLES_MASK_ALLFROZEN 0x1
        #define MUTABLES_MASK_STATE 0x6
        #define MUTABLES1_NEW(mutables) \
            ((((mutables)&MASK1_SEQ)+(1<<OFFSET1_SEQ)) \
            | (SCXRecord<K comma1 V>::STATE_INPROGRESS<<MUTABLES_OFFSET_STATE))
        #define MUTABLES_INIT_DUMMY SCXRecord<K comma1 V>::STATE_COMMITTED<<MUTABLES_OFFSET_STATE | MUTABLES_MASK_ALLFROZEN<<MUTABLES_OFFSET_ALLFROZEN
        #include "descriptors_impl.h"
        char __padding_desc[PREFETCH_SIZE_BYTES];
        DESC1_T DESC1_ARRAY[LAST_TID1+1] __attribute__ ((aligned(64)));

        /**
         * this is what LLX returns when it is performed on a leaf.
         * the important qualities of this value are:
         *      - it is not NULL
         *      - it cannot be equal to any pointer to an scx record
         */
        #define LLX_RETURN_IS_LEAF ((void*) TAGPTR1_DUMMY_DESC(0))
        #define DUMMY_SCXRECORD ((void*) TAGPTR1_STATIC_DESC(0))

        // private function declarations

        inline Node<K,V>* allocateNode(const int tid);
        inline Node<K,V>* initializeNode(
                    const int,
                    Node<K,V> * const,
                    const K&,
                    const V&,
                    Node<K,V> * const,
                    Node<K,V> * const);
        inline SCXRecord<K,V>* initializeSCXRecord(
                    const int,
                    SCXRecord<K,V> * const,
                    ReclamationInfo<K,V> * const,
                    atomic_uintptr_t * const,
                    Node<K,V> * const);
        int rangeQuery_lock(ReclamationInfo<K,V> * const, const int, void **input, void **output);
        int rangeQuery_vlx(ReclamationInfo<K,V> * const, const int, void **input, void **output);
        bool updateInsert_search_llx_scx(ReclamationInfo<K,V> * const, const int, void **input, void **output); // input consists of: const K& key, const V& val, const bool onlyIfAbsent
        bool updateErase_search_llx_scx(ReclamationInfo<K,V> * const, const int, void **input, void **output); // input consists of: const K& key, const V& val, const bool onlyIfAbsent
        void reclaimMemoryAfterSCX(
                    const int tid,
                    ReclamationInfo<K,V> * info);
        void helpOther(const int tid, tagptr_t tagptr);
        int help(const int tid, tagptr_t tagptr, SCXRecord<K,V> *ptr, bool helpingOther);
        inline void* llx(
                const int tid,
                Node<K,V> *node,
                Node<K,V> **retLeft,
                Node<K,V> **retRight);
        inline bool scx(
                    const int tid,
                    ReclamationInfo<K,V> * const,
                    Node<K,V> * volatile * field,         // pointer to a "field pointer" that will be changed
                    Node<K,V> *newNode,
                    Node<K,V> * const * const insertedNodes,
                    Node<K,V> * const * const deletedNodes);
        inline int computeSize(Node<K,V>* node);

        long long debugKeySum(Node<K,V> * node);
        bool validate(Node<K,V> * const node, const int currdepth, const int leafdepth);

        const V doInsert(const int tid, const K& key, const V& val, bool onlyIfAbsent);
        
        int init[MAX_TID_POW2] = {0,};

public:
        const K NO_KEY;
        const V NO_VALUE;

        /**
         * This function must be called once by each thread that will
         * invoke any functions on this class.
         * 
         * It must be okay that we do this with the main thread and later with another thread!!!
         */
        void initThread(const int tid) {
            if (init[tid]) return; else init[tid] = !init[tid];

            recmgr->initThread(tid);
            rqProvider->initThread(tid);

            for (int i=0;i<MAX_NODES;++i) {
                REPLACE_ALLOCATED_NODE(tid, i);
            }
        }
        void deinitThread(const int tid) {
            if (!init[tid]) return; else init[tid] = !init[tid];

            rqProvider->deinitThread(tid);
            recmgr->deinitThread(tid);
        }

        vcas_bst(const K _NO_KEY,
                    const V _NO_VALUE,
                    const int numProcesses,
                    int suspectedCrashSignal,
                    int allowedViolationsPerPath = 6)
            : N(allowedViolationsPerPath)
                    , NO_KEY(_NO_KEY)
                    , NO_VALUE(_NO_VALUE)
                    , recmgr(new RecManager(numProcesses, suspectedCrashSignal))
                    , rqProvider(new RQProvider<K, V, Node<K,V>, vcas_bst<K,V,Compare,RecManager>, RecManager, false, false>(numProcesses, this, recmgr))
    #ifdef USE_DEBUGCOUNTERS
                    , counters(new debugCounters(numProcesses))
    #endif
        {

            VERBOSE DEBUG COUTATOMIC("constructor vcas_bst"<<endl);
            allocatedNodes = new Node<K,V>*[numProcesses*(PREFETCH_SIZE_WORDS+MAX_NODES)];
            cmp = Compare();

            const int tid = 0;
            initThread(tid);

            DESC1_INIT_ALL(numProcesses);
            SCXRecord<K,V> *dummy = TAGPTR1_UNPACK_PTR(DUMMY_SCXRECORD);
            dummy->c.mutables = MUTABLES_INIT_DUMMY;

            recmgr->enterQuiescentState(tid); // block crash recovery signal for this thread, and enter an initial quiescent state.
            Node<K,V> *rootleft = initializeNode(tid, allocateNode(tid), NO_KEY, NO_VALUE, NULL, NULL);
            Node<K,V> *_root = initializeNode(tid, allocateNode(tid), NO_KEY, NO_VALUE, rootleft, NULL);
            
            // need to simulate real insertion of root and the root's child,
            // since range queries will actually try to add these nodes,
            // and we don't want blocking rq providers to spin forever
            // waiting for their itimes to be set to a positive number.
            Node<K,V>* insertedNodes[] = {_root, rootleft, NULL};
            Node<K,V>* deletedNodes[] = {NULL};
            rqProvider->linearize_update_at_write(tid, &root, _root, insertedNodes, deletedNodes);
        }

        Node<K,V> * debug_getEntryPoint() { return root; }

        long long getSizeInNodes(Node<K,V> * const u) {
            if (u == NULL) return 0;
            return 1 + getSizeInNodes(u->left)
                     + getSizeInNodes(u->right);
        }
        long long getSizeInNodes() {
            return getSizeInNodes(root);
        }
        string getSizeString() {
            stringstream ss;
            int preallocated = MAX_NODES * recmgr->NUM_PROCESSES;
            ss<<getSizeInNodes()<<" nodes in tree and "<<preallocated<<" preallocated but unused";
            return ss.str();
        }
        long long getSize(Node<K,V> * const u) {
            if (u == NULL) return 0;
            if (u->left == NULL) return 1; // is leaf
            return getSize(u->left)
                 + getSize(u->right);
        }
        long long getSize() {
            return getSize(root);
        }

        void dfsDeallocateBottomUp(Node<K,V> * const u, int *numNodes) {
            if (u == NULL) return;
            if (u->left != NULL) {
                dfsDeallocateBottomUp(u->left, numNodes);
                dfsDeallocateBottomUp(u->right, numNodes);
            }
            MEMORY_STATS ++(*numNodes);
            recmgr->deallocate(0 /* tid */, u);
        }
        ~vcas_bst() {
            VERBOSE DEBUG COUTATOMIC("destructor vcas_bst");
            // free every node and scx record currently in the data structure.
            // an easy DFS, freeing from the leaves up, handles all nodes.
            // cleaning up scx records is a little bit harder if they are in progress or aborted.
            // they have to be collected and freed only once, since they can be pointed to by many nodes.
            // so, we keep them in a set, then free each set element at the end.
            int numNodes = 0;
            dfsDeallocateBottomUp(root, &numNodes);
            VERBOSE DEBUG COUTATOMIC(" deallocated nodes "<<numNodes<<endl);
            for (int tid=0;tid<recmgr->NUM_PROCESSES;++tid) {
                for (int i=0;i<MAX_NODES;++i) {
                    recmgr->deallocate(tid, GET_ALLOCATED_NODE_PTR(tid, i));
                }
            }
            delete[] allocatedNodes;
            delete rqProvider;
            recmgr->printStatus();
            delete recmgr;
    #ifdef USE_DEBUGCOUNTERS
            delete counters;
    #endif
        }

        Node<K,V> *getRoot(void) { return root; }
        const V insert(const int tid, const K& key, const V& val);
        const V insertIfAbsent(const int tid, const K& key, const V& val);
        const pair<V,bool> erase(const int tid, const K& key);
        const pair<V,bool> find(const int tid, const K& key);
        int rangeQuery(const int tid, const K& lo, const K& hi, K * const resultKeys, V * const resultValues);
        bool contains(const int tid, const K& key);
        int size(void); /** warning: size is a LINEAR time operation, and does not return consistent results with concurrency **/

        /**
         * BEGIN FUNCTIONS FOR RANGE QUERY SUPPORT
         */

        inline bool isLogicallyDeleted(const int tid, Node<K,V> * node) {
            return false;
        }

        inline bool isLogicallyInserted(const int tid, Node<K,V> * node) {
            return true;
        }

        inline int getKeys(const int tid, Node<K,V> * node, K * const outputKeys, V * const outputValues, const long long ts) {
            if (rqProvider->read_addr(tid, &node->left, ts) == NULL && node->key != NO_KEY) {
                // leaf ==> its key is in the set.
                outputKeys[0] = node->key;
                outputValues[0] = node->value;
                return 1;
            }
            // note: internal ==> its key is NOT in the set
            return 0;
        }

        bool isInRange(const K& key, const K& lo, const K& hi) {
            return (key != NO_KEY && !cmp(key, lo) && !cmp(hi, key));
        }

        /**
         * END FUNCTIONS FOR RANGE QUERY SUPPORT
         */

        void debugPrintAllocatorStatus() {
            recmgr->printStatus();
        }
        void debugPrintToFile(string prefix, long id1, string infix, long id2, string suffix) {
            stringstream ss;
            ss<<prefix<<id1<<infix<<id2<<suffix;
            COUTATOMIC("print to filename \""<<ss.str()<<"\""<<endl);
            fstream fs (ss.str().c_str(), fstream::out);
            root->printTreeFile(fs);
            fs.close();
        }

        string tagptrToString(uintptr_t tagptr) {
            stringstream ss;
            if (tagptr) {
                if ((void*) tagptr == DUMMY_SCXRECORD) {
                    ss<<"dummy";
                } else {
                    SCXRecord<K,V> *ptr;
    //                if (TAGPTR_TEST(tagptr)) {
                        ss<<"<seq="<<UNPACK1_SEQ(tagptr)<<",tid="<<TAGPTR1_UNPACK_TID(tagptr)<<">";
                        ptr = TAGPTR1_UNPACK_PTR(tagptr);
    //                }

                    // print contents of actual scx record
                    intptr_t mutables = ptr->c.mutables;
                    ss<<"[";
                    ss<<"state="<<MUTABLES1_UNPACK_FIELD(mutables, MUTABLES_MASK_STATE, MUTABLES_OFFSET_STATE);
                    ss<<" ";
                    ss<<"allFrozen="<<MUTABLES1_UNPACK_FIELD(mutables, MUTABLES_MASK_ALLFROZEN, MUTABLES_OFFSET_ALLFROZEN);
                    ss<<" ";
                    ss<<"seq="<<UNPACK1_SEQ(mutables);
                    ss<<"]";
                }
            } else {
                ss<<"null";
            }
            return ss.str();
        }

    //    friend ostream& operator<<(ostream& os, const SCXRecord<K,V>& obj) {
    //        ios::fmtflags f( os.flags() );
    ////        cout<<"obj.type = "<<obj.type<<endl;
    //        intptr_t mutables = obj.mutables;
    //        os<<"["//<<"type="<<NAME_OF_TYPE[obj.type]
    //          <<" state="<<SCX_READ_STATE(mutables)//obj.state
    //          <<" allFrozen="<<SCX_READ_ALLFROZEN(mutables)//obj.allFrozen
    ////          <<"]";
    ////          <<" nodes="<<obj.nodes
    ////          <<" ops="<<obj.ops
    //          <<"]" //" subtree="+subtree+"]";
    //          <<"@0x"<<hex<<(long)(&obj);
    //        os.flags(f);
    //        return os;
    //    }

    #ifdef USE_DEBUGCOUNTERS
        void clearCounters() {
            counters->clear();
    //        recmgr->clearCounters();
        }
        debugCounters * const debugGetCounters() {
            return counters;
        }
    #endif
        RecManager * const debugGetRecMgr() {
            return recmgr;
        }

        bool validate(const long long keysum, const bool checkkeysum);
        long long debugKeySum() {
            return debugKeySum((root->left)->left);
        }
    };
    
}

#endif	/* vcas_bst_H */

