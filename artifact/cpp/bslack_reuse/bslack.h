/**
 * Implementation of the dictionary ADT with a lock-free B-slack tree.
 * Copyright (C) 2016 Trevor Brown
 * Contact (me [at] tbrown [dot] pro) with questions or comments.
 *
 * Details of the B-slack tree algorithm appear in the paper:
 *    Brown, Trevor. B-slack trees: space efficient B-trees. SWAT 2014.
 * 
 * The paper leaves it up to the implementer to decide when and how to perform
 * rebalancing steps (i.e., Root-Zero, Root-Replace, Absorb, Split, Compress
 * and One-Child). In this implementation, we keep track of violations and fix
 * them using a recursive cleanup procedure, which is designed as follows.
 * After performing a rebalancing step that replaced a set R of nodes,
 * recursive invocations are made for every violation that appears at a newly
 * created node. Thus, any violations that were present at nodes in R are either
 * eliminated by the rebalancing step, or will be fixed by recursive calls.
 * This way, if an invocation I of this cleanup procedure is trying to fix a
 * violation at a node that has been replaced by another invocation I' of cleanup,
 * then I can hand off responsibility for fixing the violation to I'.
 * Designing the rebalancing procedure to allow responsibility to be handed
 * off in this manner is not difficult; it simply requires going through each
 * rebalancing step S and determining which nodes involved in S can have
 * violations after S (and then making a recursive call for each violation).
 * 
 * -----------------------------------------------------------------------------
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#ifndef BSLACK_H
#define	BSLACK_H

#include <string>
#include <cstring>
#include <fstream>
#include <iostream>
#include <sstream>
#include <set>
#include <unistd.h>
#include <sys/types.h>
#include "record_manager.h"
#include "random.h"
#include "descriptors.h"

// define BEFORE including rq_provider.h
#ifndef MAX_NODES_INSERTED_OR_DELETED_ATOMICALLY
    #ifdef USE_SIMPLIFIED_ABTREE_REBALANCING
        #define MAX_NODES_INSERTED_OR_DELETED_ATOMICALLY 6
    #else
        #define MAX_NODES_INSERTED_OR_DELETED_ATOMICALLY 32
    #endif
#endif
#include "rq_provider.h"

namespace bslack_ns {

    #ifndef TRACE
    #define TRACE if(0)
    #endif
    #ifndef DEBUG
    #define DEBUG if(0)
    #endif
    #ifndef DEBUG1
    #define DEBUG1 if(0)
    #endif
    #ifndef DEBUG2
    #define DEBUG2 if(0)
    #endif

    //#define REBALANCING_NONE
    //#define REBALANCING_WEIGHT_ONLY

    //#define NO_NONROOT_SLACK_VIOLATION_FIXING

    //#define NO_VALIDATION

    //#define NO_HELPING

    #define OPTIMIZATION_PRECHECK_DEGREE_VIOLATIONS

    #define BSLACK_ENABLE_DESTRUCTOR

    //#define USE_SIMPLIFIED_ABTREE_REBALANCING

    using namespace std;
    
    template <int DEGREE, typename K>
    struct Node;
    
    template <int DEGREE, typename K>
    struct SCXRecord;
    
    template <int DEGREE, typename K>
    class wrapper_info {
    public:
        const static int MAX_NODES = DEGREE+2;
        Node<DEGREE,K> * nodes[MAX_NODES];
        SCXRecord<DEGREE,K> * scxPtrs[MAX_NODES];
        Node<DEGREE,K> * newNode;
        Node<DEGREE,K> * volatile * field;
        int state;
        char numberOfNodes;
        char numberOfNodesToFreeze;
        char numberOfNodesAllocated;

        // for rqProvider
        Node<DEGREE,K> * insertedNodes[MAX_NODES+1];
        Node<DEGREE,K> * deletedNodes[MAX_NODES+1];
    };

    template <int DEGREE, typename K>
    struct SCXRecord {
        const static int STATE_INPROGRESS = 0;
        const static int STATE_COMMITTED = 1;
        const static int STATE_ABORTED = 2;
        union {
            struct {
                volatile mutables_t mutables;

                int numberOfNodes;
                int numberOfNodesToFreeze;

                Node<DEGREE,K> * newNode;
                Node<DEGREE,K> * volatile * field;
                Node<DEGREE,K> * nodes[wrapper_info<DEGREE,K>::MAX_NODES];            // array of pointers to nodes
                SCXRecord<DEGREE,K> * scxPtrsSeen[wrapper_info<DEGREE,K>::MAX_NODES]; // array of pointers to scx records

                // for rqProvider
                Node<DEGREE,K> * insertedNodes[wrapper_info<DEGREE,K>::MAX_NODES+1];
                Node<DEGREE,K> * deletedNodes[wrapper_info<DEGREE,K>::MAX_NODES+1];
            } __attribute__((packed)) c; // WARNING: be careful with atomicity because of packed attribute!!! (this means no atomic vars smaller than word size, and all atomic vars must start on a word boundary when fields are packed tightly)
            char bytes[2*PREFETCH_SIZE_BYTES];
        };
        const static int size = sizeof(c);
        //const static int size = 2*PREFETCH_SIZE_BYTES; // sizeof(mutables)+sizeof(numberOfNodes)+sizeof(numberOfNodesToFreeze)+sizeof(newNode)+sizeof(field)+sizeof(nodes)+sizeof(scxPtrsSeen);
    } /*__attribute__((aligned (PREFETCH_SIZE_BYTES)))*/;
        
    template <int DEGREE, typename K>
    struct Node {
        SCXRecord<DEGREE,K> * volatile scxPtr;
        int leaf; // 0 or 1
        volatile int marked; // 0 or 1
        int weight; // 0 or 1
        int size; // degree of node
        K searchKey;
        volatile long long itime; // for use by range query algorithm
        volatile long long dtime; // for use by range query algorithm
        K keys[DEGREE];
        Node<DEGREE,K> * volatile ptrs[DEGREE];

        inline bool isLeaf() {
            return leaf;
        }
        inline int getKeyCount() {
            return isLeaf() ? size : size-1;
        }
        inline int getABDegree() {
            return size;
        }
        template <class Compare>
        inline int getChildIndex(const K& key, Compare cmp) {
            int nkeys = getKeyCount();
            int retval = 0;
            while (retval < nkeys && !cmp(key, (const K&) keys[retval])) {
                ++retval;
            }
            return retval;
        }
        template <class Compare>
        inline int getKeyIndex(const K& key, Compare cmp) {
            int nkeys = getKeyCount();
            int retval = 0;
            while (retval < nkeys && cmp((const K&) keys[retval], key)) {
                ++retval;
            }
            return retval;

    //        // useful if we have unordered key/value pairs in leaves
    //        for (int i=0;i<nkeys;++i) {
    //            if (!cmp(key, (const K&) keys[i]) && !cmp((const K&) keys[i], key)) return i;
    //        }
    //        return nkeys;
        }
        // somewhat slow version that detects cycles in the tree
        void printTreeFile(ostream& os, set<Node<DEGREE,K> *> *seen) {
            int __state = scxPtr->state;
            //os<<"@"<<(long long)(void *)this;
            os<<"("<<((__state & SCXRecord<DEGREE,K>::STATE_COMMITTED) ? "" : (__state & SCXRecord<DEGREE,K>::STATE_ABORTED) ? "A" : "I")
                   <<(marked?"m":"")
    //               <<getKeyCount()
                   <<(weight ? "w1" : "w0")
                   <<(isLeaf() ? "L" : "");
            os<<"[";
    //        os<<",[";
            for (int i=0;i<getKeyCount();++i) {
                os<<(i?",":"")<<keys[i];
            }
            os<<"]"; //,["<<marked<<","<<scxPtr->state<<"]";
            if (isLeaf()) {
    //            for (int i=0;i<getKeyCount();++i) {
    //                os<<","<<(long long) ptrs[i];
    //            }
            } else {
                for (int i=0;i<1+getKeyCount();++i) {
                    Node<DEGREE,K> * __node = ptrs[i];
    //                if (getKeyCount()) os<<",";
                    os<<",";
                    if (__node == NULL) {
                        os<<"-";
                    } else if (seen->find(__node) != seen->end()) { // for finding cycles
                        os<<"!"; // cycle!                          // for finding cycles
                    } else {
                        seen->insert(__node);
                        __node->printTreeFile(os, seen);
                    }                
                }
            }
            os<<")";
        }
        void printTreeFile(ostream& os) {
            set<Node<DEGREE,K> *> seen;
            printTreeFile(os, &seen);
        }
    } /*__attribute__((aligned (PREFETCH_SIZE_BYTES)))*/;

    template <int DEGREE, typename K, class Compare, class RecManager>
    class bslack {

        // the following bool determines whether the optimization to guarantee
        // amortized constant rebalancing (at the cost of decreasing average degree
        // by at most one) is used.
        // if it is false, then an amortized logarithmic number of rebalancing steps
        // may be performed per operation, but average degree increases slightly.
        const bool ALLOW_ONE_EXTRA_SLACK_PER_NODE;

        const int b;
    #ifdef USE_SIMPLIFIED_ABTREE_REBALANCING
        const int a;
    #endif

        RecManager * const recordmgr;
        RQProvider<K, void *, Node<DEGREE,K>, bslack<DEGREE,K,Compare,RecManager>, RecManager, false, false> * const rqProvider;
        char padding0[PREFETCH_SIZE_BYTES];
        Compare cmp;

        // descriptor reduction algorithm
        #ifndef comma
            #define comma ,
        #endif
        #define DESC1_ARRAY records
        #define DESC1_T SCXRecord<DEGREE comma K>
        #define MUTABLES1_OFFSET_ALLFROZEN 0
        #define MUTABLES1_OFFSET_STATE 1
        #define MUTABLES1_MASK_ALLFROZEN 0x1
        #define MUTABLES1_MASK_STATE 0x6
        #define MUTABLES1_NEW(mutables) \
            ((((mutables)&MASK1_SEQ)+(1<<OFFSET1_SEQ)) \
            | (SCXRecord<DEGREE comma K>::STATE_INPROGRESS<<MUTABLES1_OFFSET_STATE))
        #define MUTABLES1_INIT_DUMMY SCXRecord<DEGREE comma K>::STATE_COMMITTED<<MUTABLES1_OFFSET_STATE | MUTABLES1_MASK_ALLFROZEN<<MUTABLES1_OFFSET_ALLFROZEN
        #include "../descriptors/descriptors_impl.h"
        char __padding_desc[PREFETCH_SIZE_BYTES];
        DESC1_T DESC1_ARRAY[LAST_TID1+1] __attribute__ ((aligned(64)));

        char padding1[PREFETCH_SIZE_BYTES];
        Node<DEGREE,K> * entry;
        char padding2[PREFETCH_SIZE_BYTES];

        #define DUMMY       ((SCXRecord<DEGREE,K>*) (void*) TAGPTR1_STATIC_DESC(0))
        #define FINALIZED   ((SCXRecord<DEGREE,K>*) (void*) TAGPTR1_DUMMY_DESC(1))
        #define FAILED      ((SCXRecord<DEGREE,K>*) (void*) TAGPTR1_DUMMY_DESC(2))

        // the following variable is only useful for single threaded execution
        const bool SEQUENTIAL_STAT_TRACKING;

        // these variables are only used by single threaded executions,
        // and only if SEQUENTIAL_STAT_TRACKING == true.
        // they simply track various events in the b-slack tree.
        int operationCount;
        int overflows;
        int weightChecks;
        int weightCheckSearches;
        int weightFixAttempts;
        int weightFixes;
        int weightEliminated;
        int slackChecks;
        int slackCheckTotaling;
        int slackCheckSearches;
        int slackFixTotaling;
        int slackFixAttempts;
        int slackFixSCX;
        int slackFixes;

        #define arraycopy(src, srcStart, dest, destStart, len) \
            for (int ___i=0;___i<(len);++___i) { \
                (dest)[(destStart)+___i] = (src)[(srcStart)+___i]; \
            }
        #define arraycopy_ptrs(src, srcStart, dest, destStart, len) \
            for (int ___i=0;___i<(len);++___i) { \
                rqProvider->write_addr(tid, &(dest)[(destStart)+___i], \
                        rqProvider->read_addr(tid, &(src)[(srcStart)+___i])); \
            }

    private:
        string tagptrToString(uintptr_t tagptr) {
            stringstream ss;
            if (tagptr) {
                if ((void*) tagptr == DUMMY) {
                    ss<<"dummy";
                } else {
                    SCXRecord<DEGREE,K> *ptr;
                    ss<<"<seq="<<UNPACK1_SEQ(tagptr)<<",tid="<<TAGPTR1_UNPACK_TID(tagptr)<<">";
                    ptr = TAGPTR1_UNPACK_PTR(tagptr);

                    // print contents of actual scx record
                    intptr_t mutables = ptr->c.mutables;
                    ss<<"[";
                    ss<<"state="<<MUTABLES1_UNPACK_FIELD(mutables, MUTABLES1_MASK_STATE, MUTABLES1_OFFSET_STATE);
                    ss<<" ";
                    ss<<"allFrozen="<<MUTABLES1_UNPACK_FIELD(mutables, MUTABLES1_MASK_ALLFROZEN, MUTABLES1_OFFSET_ALLFROZEN);
                    ss<<" ";
                    ss<<"seq="<<UNPACK1_SEQ(mutables);
                    ss<<"]";
                }
            } else {
                ss<<"null";
            }
            return ss.str();
        }

        void* doInsert(const int tid, const K& key, void * const value, const bool replace);

        // returns true if the invocation of this method
        // (and not another invocation of a method performed by this method)
        // performed an scx, and false otherwise
        bool fixWeightViolation(const int tid, Node<DEGREE,K>* viol);

        // returns true if the invocation of this method
        // (and not another invocation of a method performed by this method)
        // performed an scx, and false otherwise
        bool fixDegreeOrSlackViolation(const int tid, Node<DEGREE,K>* viol);

        bool llx(const int tid, Node<DEGREE,K>* r, Node<DEGREE,K> ** snapshot, const int i, SCXRecord<DEGREE,K> ** ops, Node<DEGREE,K> ** nodes);
        SCXRecord<DEGREE,K>* llx(const int tid, Node<DEGREE,K>* r, Node<DEGREE,K> ** snapshot);
        bool scx(const int tid, wrapper_info<DEGREE,K> * info);
        void helpOther(const int tid, tagptr_t tagptr);
        int help(const int tid, const tagptr_t tagptr, SCXRecord<DEGREE,K> const * const snap, const bool helpingOther);

        SCXRecord<DEGREE,K>* createSCXRecord(const int tid, wrapper_info<DEGREE,K> * info);
        Node<DEGREE,K>* allocateNode(const int tid);

        void freeSubtree(Node<DEGREE,K>* node, int* nodes) {
            const int tid = 0;
            if (node == NULL) return;
            if (!node->isLeaf()) {
                for (int i=0;i<node->getABDegree();++i) {
                    freeSubtree(node->ptrs[i], nodes);
                }
            }
            ++(*nodes);
            recordmgr->retire(tid, node);
        }

        int init[MAX_TID_POW2] = {0,};
public:
        void * const NO_VALUE;
        const int NUM_PROCESSES;
    #ifdef USE_DEBUGCOUNTERS
        debugCounters * const counters; // debug info
    #endif

        /**
         * This function must be called once by each thread that will
         * invoke any functions on this class.
         * 
         * It must be okay that we do this with the main thread and later with another thread!
         */
        void initThread(const int tid) {
            if (init[tid]) return; else init[tid] = !init[tid];
            
            recordmgr->initThread(tid);
            rqProvider->initThread(tid);
        }
        void deinitThread(const int tid) {
            if (!init[tid]) return; else init[tid] = !init[tid];

            rqProvider->deinitThread(tid);
            recordmgr->deinitThread(tid);
        }

        /**
         * Creates a new B-slack tree wherein: <br>
         *      each internal node has up to <code>nodeCapacity</code> child pointers, and <br>
         *      each leaf has up to <code>nodeCapacity</code> key/value pairs, and <br>
         *      keys are ordered according to the provided comparator.
         */
        bslack(const int numProcesses, 
                const int nodeCapacity,
                const K anyKey,
                int suspectedCrashSignal)
        : ALLOW_ONE_EXTRA_SLACK_PER_NODE(true)
        , b(nodeCapacity)
    #ifdef USE_SIMPLIFIED_ABTREE_REBALANCING
        , a(nodeCapacity/2 - 2)
    #endif
        , recordmgr(new RecManager(numProcesses, suspectedCrashSignal))
        , rqProvider(new RQProvider<K, void *, Node<DEGREE,K>, bslack<DEGREE,K,Compare,RecManager>, RecManager, false, false>(numProcesses, this, recordmgr))
        , SEQUENTIAL_STAT_TRACKING(false)
        , NO_VALUE((void *) -1LL)
        , NUM_PROCESSES(numProcesses) 
    #ifdef USE_DEBUGCOUNTERS
        , counters(new debugCounters(numProcesses))
    #endif
        {
#if !defined USE_SIMPLIFIED_ABTREE_REBALANCING
            assert(MAX_NODES_INSERTED_OR_DELETED_ATOMICALLY >= DEGREE+2);
#endif
            cmp = Compare();
            
            const int tid = 0;
            initThread(tid);

            recordmgr->enterQuiescentState(tid);
            
            DESC1_INIT_ALL(numProcesses);

            SCXRecord<DEGREE,K> *dummy = TAGPTR1_UNPACK_PTR(DUMMY);
            dummy->c.mutables = MUTABLES1_INIT_DUMMY;
            TRACE COUTATOMICTID("DUMMY mutables="<<dummy->c.mutables<<endl);

            // initial tree: entry is a sentinel node (with one pointer and no keys)
            //               that points to an empty node (no pointers and no keys)
            Node<DEGREE,K>* _entryLeft = allocateNode(tid);
            _entryLeft->scxPtr = DUMMY;
            _entryLeft->leaf = true;
            _entryLeft->marked = false;
            _entryLeft->weight = true;
            _entryLeft->size = 0;
            _entryLeft->searchKey = anyKey;

            Node<DEGREE,K>* _entry = allocateNode(tid);
            _entry = allocateNode(tid);
            _entry->scxPtr = DUMMY;
            _entry->leaf = false;
            _entry->marked = false;
            _entry->weight = true;
            _entry->size = 1;
            _entry->searchKey = anyKey;
            _entry->ptrs[0] = _entryLeft;
            
            // need to simulate real insertion of root and the root's child,
            // since range queries will actually try to add these nodes,
            // and we don't want blocking rq providers to spin forever
            // waiting for their itimes to be set to a positive number.
            Node<DEGREE,K>* insertedNodes[] = {_entry, _entryLeft, NULL};
            Node<DEGREE,K>* deletedNodes[] = {NULL};
            rqProvider->linearize_update_at_write(tid, &entry, _entry, insertedNodes, deletedNodes);

            operationCount = 0;
            overflows = 0;
            weightChecks = 0;
            weightCheckSearches = 0;
            weightFixAttempts = 0;
            weightFixes = 0;
            weightEliminated = 0;
            slackChecks = 0;
            slackCheckTotaling = 0;
            slackCheckSearches = 0;
            slackFixTotaling = 0;
            slackFixAttempts = 0;
            slackFixSCX = 0;
            slackFixes = 0;

    #ifdef USE_SIMPLIFIED_ABTREE_REBALANCING
            COUTATOMIC("NOTICE: (a,b)-tree rebalancing enabled"<<endl);
    #else
            COUTATOMIC("NOTICE: B-slack tree rebalancing enabled"<<endl);
    #endif
        }

    #ifdef BSLACK_ENABLE_DESTRUCTOR    
        ~bslack() {
            int nodes = 0;
            freeSubtree(entry, &nodes);
            COUTATOMIC("main thread: deleted tree containing "<<nodes<<" nodes"<<endl);
            delete rqProvider;
            recordmgr->printStatus();
            delete recordmgr;
    #ifdef USE_DEBUGCOUNTERS
            delete counters;
    #endif
        }
    #endif

        Node<DEGREE,K> * debug_getEntryPoint() { return entry; }

    private:
        /*******************************************************************
         * Utility functions for integration with the test harness
         *******************************************************************/

        int sequentialSize(Node<DEGREE,K>* node) {
            if (node->isLeaf()) {
                return node->getKeyCount();
            }
            int retval = 0;
            for (int i=0;i<node->getABDegree();++i) {
                Node<DEGREE,K>* child = node->ptrs[i];
                retval += sequentialSize(child);
            }
            return retval;
        }
        int sequentialSize() {
            return sequentialSize(entry->ptrs[0]);
        }

        int getNumberOfLeaves(Node<DEGREE,K>* node) {
            if (node == NULL) return 0;
            if (node->isLeaf()) return 1;
            int result = 0;
            for (int i=0;i<node->getABDegree();++i) {
                result += getNumberOfLeaves(node->ptrs[i]);
            }
            return result;
        }
        const int getNumberOfLeaves() {
            return getNumberOfLeaves(entry->ptrs[0]);
        }
        int getNumberOfInternals(Node<DEGREE,K>* node) {
            if (node == NULL) return 0;
            if (node->isLeaf()) return 0;
            int result = 1;
            for (int i=0;i<node->getABDegree();++i) {
                result += getNumberOfInternals(node->ptrs[i]);
            }
            return result;
        }
        const int getNumberOfInternals() {
            return getNumberOfInternals(entry->ptrs[0]);
        }
        const int getNumberOfNodes() {
            return getNumberOfLeaves() + getNumberOfInternals();
        }

        int getSumOfKeyDepths(Node<DEGREE,K>* node, int depth) {
            if (node == NULL) return 0;
            if (node->isLeaf()) return depth * node->getKeyCount();
            int result = 0;
            for (int i=0;i<node->getABDegree();i++) {
                result += getSumOfKeyDepths(node->ptrs[i], 1+depth);
            }
            return result;
        }
        const int getSumOfKeyDepths() {
            return getSumOfKeyDepths(entry->ptrs[0], 0);
        }
        const double getAverageKeyDepth() {
            long sz = sequentialSize();
            return (sz == 0) ? 0 : getSumOfKeyDepths() / sz;
        }

        int getHeight(Node<DEGREE,K>* node, int depth) {
            if (node == NULL) return 0;
            if (node->isLeaf()) return 0;
            int result = 0;
            for (int i=0;i<node->getABDegree();i++) {
                int retval = getHeight(node->ptrs[i], 1+depth);
                if (retval > result) result = retval;
            }
            return result+1;
        }
        const int getHeight() {
            return getHeight(entry->ptrs[0], 0);
        }

        int getKeyCount(Node<DEGREE,K>* entry) {
            if (entry == NULL) return 0;
            if (entry->isLeaf()) return entry->getKeyCount();
            int sum = 0;
            for (int i=0;i<entry->getABDegree();++i) {
                sum += getKeyCount(entry->ptrs[i]);
            }
            return sum;
        }
        int getTotalDegree(Node<DEGREE,K>* entry) {
            if (entry == NULL) return 0;
            int sum = entry->getKeyCount();
            if (entry->isLeaf()) return sum;
            for (int i=0;i<entry->getABDegree();++i) {
                sum += getTotalDegree(entry->ptrs[i]);
            }
            return 1+sum; // one more children than keys
        }
        int getNodeCount(Node<DEGREE,K>* entry) {
            if (entry == NULL) return 0;
            if (entry->isLeaf()) return 1;
            int sum = 1;
            for (int i=0;i<entry->getABDegree();++i) {
                sum += getNodeCount(entry->ptrs[i]);
            }
            return sum;
        }
        double getAverageDegree() {
            return getTotalDegree(entry) / (double) getNodeCount(entry);
        }
        double getSpacePerKey() {
            return getNodeCount(entry)*2*b / (double) getKeyCount(entry);
        }

        long long getSumOfKeys(Node<DEGREE,K>* node) {
            TRACE COUTATOMIC("  getSumOfKeys("<<node<<"): isLeaf="<<node->isLeaf()<<endl);
            long long sum = 0;
            if (node->isLeaf()) {
                TRACE COUTATOMIC("      leaf sum +=");
                for (int i=0;i<node->getKeyCount();++i) {
                    sum += (long long) node->keys[i];
                    TRACE COUTATOMIC(node->keys[i]);
                }
                TRACE COUTATOMIC(endl);
            } else {
                for (int i=0;i<node->getABDegree();++i) {
                    sum += getSumOfKeys(node->ptrs[i]);
                }
            }
            TRACE COUTATOMIC("  getSumOfKeys("<<node<<"): sum="<<sum<<endl);
            return sum;
        }
        long long getSumOfKeys() {
            TRACE COUTATOMIC("getSumOfKeys()"<<endl);
            return getSumOfKeys(entry);
        }

        /**
         * Functions for verifying that the data structure is a B-slack tree
         */

        bool satisfiesP1(Node<DEGREE,K>* node, int height, int depth) {
            if (node->isLeaf()) return (height == depth);
            for (int i=0;i<node->getABDegree();++i) {
                if (!satisfiesP1(node->ptrs[i], height, depth+1)) return false;
            }
            return true;
        }
        bool satisfiesP1() {
            return satisfiesP1(entry->ptrs[0], getHeight(), 0);
        }

        bool satisfiesP2(Node<DEGREE,K>* node) {
            if (node->isLeaf()) return true;
            if (node->getABDegree() < 2) return false;
            if (node->getKeyCount() + 1 != node->getABDegree()) return false;
            for (int i=0;i<node->getABDegree();++i) {
                if (!satisfiesP2(node->ptrs[i])) return false;
            }
            return true;
        }
        bool satisfiesP2() {
            return satisfiesP2(entry->ptrs[0]);
        }

        bool noWeightViolations(Node<DEGREE,K>* node) {
            if (!node->weight) return false;
            if (!node->isLeaf()) {
                for (int i=0;i<node->getABDegree();++i) {
                    if (!noWeightViolations(node->ptrs[i])) return false;
                }
            }
            return true;
        }
        bool noWeightViolations() {
            return noWeightViolations(entry->ptrs[0]);
        }

    #ifdef USE_SIMPLIFIED_ABTREE_REBALANCING
        bool abtree_noDegreeViolations(Node<DEGREE,K>* node) {
            if (!(node->size >= a || node == entry || node == entry->ptrs[0])) {
                cerr<<"degree violation found: node->size="<<node->size<<" a="<<a<<endl;
                return false;
            }
            if (!node->isLeaf()) {
                for (int i=0;i<node->getABDegree();++i) {
                    if (!abtree_noDegreeViolations(node->ptrs[i])) return false;
                }
            }
            return true;
        }
        bool abtree_noDegreeViolations() {
            return abtree_noDegreeViolations(entry->ptrs[0]);
        }
    #endif

        bool childrenAreAllLeavesOrInternal(Node<DEGREE,K>* node) {
            if (node->isLeaf()) return true;
            bool leafChild = false;
            for (int i=0;i<node->getABDegree();++i) {
                if (node->ptrs[i]->isLeaf()) leafChild = true;
                else if (leafChild) return false;
            }
            return true;
        }
        bool childrenAreAllLeavesOrInternal() {
            return childrenAreAllLeavesOrInternal(entry->ptrs[0]);
        }

        bool satisfiesP4(Node<DEGREE,K>* node) {
            // note: this function assumes that childrenAreAllLeavesOrInternal() = true
            if (node->isLeaf()) return true;
            int totalDegreeOfChildren = 0;
            for (int i=0;i<node->getABDegree();++i) {
                Node<DEGREE,K>* c = node->ptrs[i];
                if (!satisfiesP4(c)) return false;
                totalDegreeOfChildren += (c->isLeaf() ? c->getKeyCount() : c->getABDegree());
            }
            int slack = node->getABDegree() * b - totalDegreeOfChildren;
            if (slack >= b + (ALLOW_ONE_EXTRA_SLACK_PER_NODE ? node->getABDegree() : 0)) {
                return false;
            }
            return true;
        }
        bool satisfiesP4() {
            return satisfiesP4(entry->ptrs[0]);
        }

        void bslack_error(string s) {
            cerr<<"ERROR: "<<s<<endl;
            exit(-1);
        }

        bool isBSlackTree() {
            if (!satisfiesP1()) bslack_error("satisfiesP1() == false");
            if (!satisfiesP2()) bslack_error("satisfiesP2() == false");
            if (!noWeightViolations()) bslack_error("noWeightViolations() == false");
            if (!childrenAreAllLeavesOrInternal()) bslack_error("childrenAreAllLeavesOrInternal() == false");
    #ifdef USE_SIMPLIFIED_ABTREE_REBALANCING
            if (!abtree_noDegreeViolations()) bslack_error("abtree_noDegreeViolations() == false");
    #else
            if (!satisfiesP4()) bslack_error("satisfiesP4() == false");
    #endif
            return true;
        }

        void debugPrint() {
            if (SEQUENTIAL_STAT_TRACKING) {
                cout<<"overflows="<<overflows<<endl;
                cout<<"weightChecks="<<weightChecks<<endl;
                cout<<"weightCheckSearches="<<weightCheckSearches<<endl;
                cout<<"weightFixAttempts="<<weightFixAttempts<<endl;
                cout<<"weightFixes="<<weightFixes<<endl;
                cout<<"weightEliminated="<<weightEliminated<<endl;
                cout<<"slackChecks="<<slackChecks<<endl;
                cout<<"slackCheckTotaling="<<slackCheckTotaling<<endl;
                cout<<"slackCheckSearches="<<slackCheckSearches<<endl;
                cout<<"slackFixTotaling="<<slackFixTotaling<<endl;
                cout<<"slackFixAttempts="<<slackFixAttempts<<endl;
                cout<<"slackFixSCX="<<slackFixSCX<<endl;
                cout<<"slackFixes="<<slackFixes<<endl;
            }
            cout<<"averageDegree="<<getAverageDegree()<<endl;
            cout<<"averageDepth="<<getAverageKeyDepth()<<endl;
            cout<<"height="<<getHeight()<<endl;
            cout<<"internalNodes="<<getNumberOfInternals()<<endl;
            cout<<"leafNodes="<<getNumberOfLeaves()<<endl;
        }

    public:
        const void * insert(const int tid, const K& key, void * const val) {
            return doInsert(tid, key, val, true);
        }
        const void * insertIfAbsent(const int tid, const K& key, void * const val) {
            return doInsert(tid, key, val, false);
        }
        const pair<void*,bool> erase(const int tid, const K& key);
        const pair<void*,bool> find(const int tid, const K& key);
        bool contains(const int tid, const K& key);
        int rangeQuery(const int tid, const K& low, const K& hi, K * const resultKeys, void ** const resultValues);
        bool validate(const long long keysum, const bool checkkeysum) {
            if (checkkeysum) {
                long long treekeysum = getSumOfKeys();
                if (treekeysum != keysum) {
                    cerr<<"ERROR: tree keysum "<<treekeysum<<" did not match thread keysum "<<keysum<<endl;
                    return false;
                }
            }

            debugPrint();
            bool isbslack = isBSlackTree();
            return isbslack;
        }

        /**
         * BEGIN FUNCTIONS FOR RANGE QUERY SUPPORT
         */

        inline bool isLogicallyDeleted(const int tid, Node<DEGREE,K> * node) {
            return false;
        }

        inline int getKeys(const int tid, Node<DEGREE,K> * node, K * const outputKeys, void ** const outputValues) {
            if (node->isLeaf()) {
                // leaf ==> its keys are in the set.
                const int sz = node->getKeyCount();
                for (int i=0;i<sz;++i) {
                    outputKeys[i] = node->keys[i];
                    outputValues[i] = (void *) node->ptrs[i];
                }
                return sz;
            }
            // note: internal ==> its keys are NOT in the set
            return 0;
        }

        inline bool isLogicallyInserted(const int tid, Node<DEGREE,K> * node) {
            return true;
        }

        bool isInRange(const K& key, const K& lo, const K& hi) {
            return (!cmp(key, lo) && !cmp(hi, key));
        }

        /**
         * END FUNCTIONS FOR RANGE QUERY SUPPORT
         */

        long long getSizeInNodes() {
            return getNumberOfNodes();
        }
        string getSizeString() {
            stringstream ss;
            int preallocated = wrapper_info<DEGREE,K>::MAX_NODES * recordmgr->NUM_PROCESSES;
            ss<<getSizeInNodes()<<" nodes in tree";
            return ss.str();
        }
        long long getSize(Node<DEGREE,K> * node) {
            return sequentialSize(node);
        }
        long long getSize() {
            return sequentialSize();
        }
        RecManager * const debugGetRecMgr() {
            return recordmgr;
        }
        long long debugKeySum() {
            return getSumOfKeys();
        }
    #ifdef USE_DEBUGCOUNTERS
        debugCounters * const debugGetCounters() {
            return counters;
        }
    #endif
    //    void debugPrintTree() {
    //        entry->printTreeFile(cout);
    //    }
        void debugPrintToFile(string prefix, long id1, string infix, long id2, string suffix) {
            stringstream ss;
            ss<<prefix<<id1<<infix<<id2<<suffix;
            COUTATOMIC("print to filename \""<<ss.str()<<"\""<<endl);
            fstream fs (ss.str().c_str(), fstream::out);
            entry->printTreeFile(fs);
            fs.close();
        }
    #ifdef USE_DEBUGCOUNTERS
        void clearCounters() {
            counters->clear();
        }
    #endif
    };
} // namespace

#endif	/* BSLACK_H */

