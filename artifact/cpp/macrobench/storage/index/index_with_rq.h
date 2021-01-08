/* 
 * File:   index_with_rq.h
 * Author: trbot
 *
 * Created on May 28, 2017, 3:03 PM
 */

#ifndef INDEX_WITH_RQ_H
#define INDEX_WITH_RQ_H

#define INDEX_HAS_RQ

#include <limits>
#include <csignal>
#include <cstring>
#include "index_base.h"     // for table_t declaration, and parent class inheritance

#include <ctime>
#include "random.h"
#include "plaf.h"
static Random rngs[MAX_TID_POW2*PREFETCH_SIZE_WORDS]; // create per-thread random number generators (padded to avoid false sharing)

/**
 * Define index data structure and record manager types
 */

typedef allocator_new_segregated<> ALLOCATOR_TYPE;
typedef pool_none<> POOL_TYPE;
#ifdef INDEX_NO_RECLAMATION
typedef reclaimer_none<> RECLAIMER_TYPE;
#else
typedef reclaimer_debra<> RECLAIMER_TYPE;
#endif

#if 0
#elif (INDEX_STRUCT == IDX_BST_RQ_LOCKFREE) || \
      (INDEX_STRUCT == IDX_CITRUS_RQ_LOCKFREE) || \
      (INDEX_STRUCT == IDX_ABTREE_RQ_LOCKFREE) || \
      (INDEX_STRUCT == IDX_BSLACK_RQ_LOCKFREE) || \
      (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_LOCKFREE)
    #define RQ_LOCKFREE
#elif (INDEX_STRUCT == IDX_BST_RQ_RWLOCK) || \
      (INDEX_STRUCT == IDX_CITRUS_RQ_RWLOCK) || \
      (INDEX_STRUCT == IDX_ABTREE_RQ_RWLOCK) || \
      (INDEX_STRUCT == IDX_BSLACK_RQ_RWLOCK) || \
      (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_RWLOCK)
    #define RQ_RWLOCK
#elif (INDEX_STRUCT == IDX_BST_RQ_HTM_RWLOCK) || \
      (INDEX_STRUCT == IDX_CITRUS_RQ_HTM_RWLOCK) || \
      (INDEX_STRUCT == IDX_ABTREE_RQ_HTM_RWLOCK) || \
      (INDEX_STRUCT == IDX_BSLACK_RQ_HTM_RWLOCK) || \
      (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_HTM_RWLOCK)
    #define RQ_HTM_RWLOCK
#elif (INDEX_STRUCT == IDX_BST_RQ_UNSAFE) || \
      (INDEX_STRUCT == IDX_CITRUS_RQ_UNSAFE) || \
      (INDEX_STRUCT == IDX_ABTREE_RQ_UNSAFE) || \
      (INDEX_STRUCT == IDX_BSLACK_RQ_UNSAFE) || \
      (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_UNSAFE)
    #define RQ_UNSAFE
#elif (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_SNAPCOLLECTOR)
    #define RQ_SNAPCOLLECTOR
#endif

#if 0
#elif (INDEX_STRUCT == IDX_BST_RQ_LOCKFREE) || \
      (INDEX_STRUCT == IDX_BST_RQ_RWLOCK) || \
      (INDEX_STRUCT == IDX_BST_RQ_HTM_RWLOCK) || \
      (INDEX_STRUCT == IDX_BST_RQ_UNSAFE)
    #include "bst_impl.h"
    using namespace bst_ns;
    typedef Node<KEY_TYPE, VALUE_TYPE> NODE_TYPE;
    typedef SCXRecord<KEY_TYPE, VALUE_TYPE> DESCRIPTOR_TYPE;
    typedef record_manager<RECLAIMER_TYPE, ALLOCATOR_TYPE, POOL_TYPE, NODE_TYPE> RECORD_MANAGER_TYPE;
    typedef bst<KEY_TYPE, VALUE_TYPE, less<KEY_TYPE>, RECORD_MANAGER_TYPE> INDEX_TYPE;
    #define INDEX_CONSTRUCTOR_ARGS __NO_KEY, __NO_VALUE, g_thread_cnt, SIGQUIT
    #define CALL_CALCULATE_INDEX_STATS_FOREACH_CHILD(x, depth) { \
        calculate_index_stats((x)->left, (depth)); \
        calculate_index_stats((x)->right, (depth)); \
    }
    #define ISLEAF(x) ((x)->left == NULL)
    #define VALUES_ARRAY_TYPE VALUE_TYPE *
    
#elif (INDEX_STRUCT == IDX_CITRUS_RQ_LOCKFREE) || \
      (INDEX_STRUCT == IDX_CITRUS_RQ_RWLOCK) || \
      (INDEX_STRUCT == IDX_CITRUS_RQ_HTM_RWLOCK) || \
      (INDEX_STRUCT == IDX_CITRUS_RQ_UNSAFE)
    #include "citrus_impl.h"
    typedef node_t<KEY_TYPE, VALUE_TYPE> NODE_TYPE;
    typedef bool DESCRIPTOR_TYPE; // no descriptor
    typedef record_manager<RECLAIMER_TYPE, ALLOCATOR_TYPE, POOL_TYPE, NODE_TYPE> RECORD_MANAGER_TYPE;
    typedef citrustree<KEY_TYPE, VALUE_TYPE, RECORD_MANAGER_TYPE> INDEX_TYPE;
    #define INDEX_CONSTRUCTOR_ARGS numeric_limits<KEY_TYPE>::max(), __NO_VALUE, g_thread_cnt
    #define CALL_CALCULATE_INDEX_STATS_FOREACH_CHILD(x, depth) { \
        calculate_index_stats((x)->child[0], (depth)); \
        calculate_index_stats((x)->child[1], (depth)); \
    }
    #define ISLEAF(x) ((x)->child[0] == NULL && (x)->child[1] == NULL)
    #define VALUES_ARRAY_TYPE VALUE_TYPE *

#elif (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_LOCKFREE) || \
      (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_RWLOCK) || \
      (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_HTM_RWLOCK) || \
      (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_UNSAFE) || \
      (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_SNAPCOLLECTOR)
    #include "skiplist_lock_impl.h"
    typedef node_t<KEY_TYPE, VALUE_TYPE> NODE_TYPE;
    typedef bool DESCRIPTOR_TYPE; // no descriptor
#if (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_SNAPCOLLECTOR)
    typedef record_manager<RECLAIMER_TYPE, ALLOCATOR_TYPE, POOL_TYPE, NODE_TYPE, SnapCollector<node_t<KEY_TYPE, VALUE_TYPE>, KEY_TYPE>, SnapCollector<node_t<KEY_TYPE, VALUE_TYPE>, KEY_TYPE>::NodeWrapper, ReportItem, CompactReportItem> RECORD_MANAGER_TYPE;
#else
    typedef record_manager<RECLAIMER_TYPE, ALLOCATOR_TYPE, POOL_TYPE, NODE_TYPE> RECORD_MANAGER_TYPE;
#endif
    typedef skiplist<KEY_TYPE, VALUE_TYPE, RECORD_MANAGER_TYPE> INDEX_TYPE;
    #define INDEX_CONSTRUCTOR_ARGS g_thread_cnt, numeric_limits<KEY_TYPE>::min(), numeric_limits<KEY_TYPE>::max()-1, __NO_VALUE, rngs
    #define CALL_CALCULATE_INDEX_STATS_FOREACH_CHILD(x, depth) 
    #define ISLEAF(x) false
    #define VALUES_ARRAY_TYPE VALUE_TYPE *

#elif (INDEX_STRUCT == IDX_CITRUS_RQ_RLU)
    #include "rlu_citrus_impl.h"
    typedef node_t<KEY_TYPE, VALUE_TYPE> NODE_TYPE;
    typedef bool DESCRIPTOR_TYPE; // no descriptor
    typedef rlucitrus<KEY_TYPE, VALUE_TYPE> INDEX_TYPE;
    #define INDEX_CONSTRUCTOR_ARGS g_thread_cnt, numeric_limits<KEY_TYPE>::max(), __NO_VALUE
    #define CALL_CALCULATE_INDEX_STATS_FOREACH_CHILD(x, depth) { \
        calculate_index_stats((x)->child[0], (depth)); \
        calculate_index_stats((x)->child[1], (depth)); \
    }
    #define ISLEAF(x) ((x)->child[0] == NULL && (x)->child[1] == NULL)
    #define VALUES_ARRAY_TYPE VALUE_TYPE *

#elif (INDEX_STRUCT == IDX_ABTREE_RQ_LOCKFREE) || \
      (INDEX_STRUCT == IDX_ABTREE_RQ_RWLOCK) || \
      (INDEX_STRUCT == IDX_ABTREE_RQ_HTM_RWLOCK) || \
      (INDEX_STRUCT == IDX_ABTREE_RQ_UNSAFE) || \
      (INDEX_STRUCT == IDX_BSLACK_RQ_LOCKFREE) || \
      (INDEX_STRUCT == IDX_BSLACK_RQ_RWLOCK) || \
      (INDEX_STRUCT == IDX_BSLACK_RQ_HTM_RWLOCK) || \
      (INDEX_STRUCT == IDX_BSLACK_RQ_UNSAFE)

    #if (INDEX_STRUCT == IDX_ABTREE_RQ_LOCKFREE) || \
          (INDEX_STRUCT == IDX_ABTREE_RQ_RWLOCK) || \
          (INDEX_STRUCT == IDX_ABTREE_RQ_HTM_RWLOCK) || \
          (INDEX_STRUCT == IDX_ABTREE_RQ_UNSAFE)
        #define USE_SIMPLIFIED_ABTREE_REBALANCING
    #endif
    #include "bslack_impl.h"
    using namespace bslack_ns;
    #define ABTREE_DEGREE 16
    typedef Node<ABTREE_DEGREE, KEY_TYPE> NODE_TYPE;
    typedef SCXRecord<ABTREE_DEGREE, KEY_TYPE> DESCRIPTOR_TYPE;
    typedef record_manager<RECLAIMER_TYPE, ALLOCATOR_TYPE, POOL_TYPE, NODE_TYPE> RECORD_MANAGER_TYPE;
    typedef bslack<ABTREE_DEGREE, KEY_TYPE, less<KEY_TYPE>, RECORD_MANAGER_TYPE> INDEX_TYPE;
    #define INDEX_CONSTRUCTOR_ARGS g_thread_cnt, ABTREE_DEGREE, __NO_KEY, SIGQUIT
    #define CALL_CALCULATE_INDEX_STATS_FOREACH_CHILD(x, depth) { \
        for (int __i=0;__i<(x)->getABDegree();++__i) { \
            calculate_index_stats((NODE_TYPE *) (x)->ptrs[__i], (depth)); \
        } \
    }
    #define ISLEAF(x) (x)->isLeaf()
    #define VALUES_ARRAY_TYPE void **
#endif

/**
 * Create an adapter class for the DBx1000 index interface
 */

class index_with_rq : public index_base {
private:
    INDEX_TYPE * index;
    
    unsigned long alignment[9] = {0}; 
    unsigned long sum_nodes_depths = 0;
    unsigned long sum_leaf_depths = 0;
    unsigned long num_leafs = 0;
    unsigned long num_nodes = 0;
    unsigned long num_keys = 0;
    int max_depth = 0;
    
    void calculate_index_stats(NODE_TYPE * curr, int depth) {
        if (curr == NULL) return; 
        unsigned node_alignment = (unsigned long) curr & 63UL;
        assert((node_alignment % 8) == 0);
        alignment[node_alignment/8]++; 
        sum_nodes_depths += depth;
        ++num_nodes;
        if (depth > max_depth) {
            max_depth = depth;
        }
        if (ISLEAF(curr)) {
            ++num_leafs;
            ++num_keys; // TODO: fix number of keys for the (a,b)-tree!!!
            sum_leaf_depths += depth;
        } else {
            CALL_CALCULATE_INDEX_STATS_FOREACH_CHILD(curr, 1+depth);
        }
    }
public:
    // WARNING: DO NOT OVERLOAD init() WITH NO ARGUMENTS!!!
    RC init(uint64_t part_cnt, table_t * table) {
        if (part_cnt != 1) error("part_cnt != 1 unsupported");

        srand(time(NULL));
        for (int i=0;i<MAX_TID_POW2;++i) {
            rngs[i*PREFETCH_SIZE_WORDS].setSeed(rand());
        }        
        
        index = new INDEX_TYPE(INDEX_CONSTRUCTOR_ARGS);
        this->table = table;
        
        return RCOK;
    }
    RC index_insert(KEY_TYPE key, VALUE_TYPE newItem, int part_id = -1) {
#if 0
        newItem->next = NULL;
        lock_key(key);
            const void * oldVal = index->insertIfAbsent(tid, key, newItem);
            VALUE_TYPE oldItem = (VALUE_TYPE) oldVal;
            if (oldVal != index->NO_VALUE) {
                // adding to existing list
                newItem->next = oldItem->next;
                oldItem->next = newItem;
            }
        unlock_key(key);
#else
        const void * oldVal = index->insertIfAbsent(tid, key, newItem);
//#ifndef NDEBUG
//        if (oldVal != index->NO_VALUE) {
//            cout<<"index_insert found element already existed."<<endl;
//            cout<<"index name="<<index_name<<endl;
//            cout<<"key="<<key<<endl;
//        }
//        assert(oldVal == index->NO_VALUE);
//#endif
        // TODO: determine if there are index collisions in anything but orderline
        //       (and determine why they happen in orderline, and if it's ok)
#endif
        INCREMENT_NUM_INSERTS(tid);
        return RCOK;
    }
    RC index_read(KEY_TYPE key, VALUE_TYPE * item, int part_id = -1, int thd_id = 0) {
        *item = (VALUE_TYPE) index->find(tid, key).first;
        INCREMENT_NUM_READS(tid);
        return RCOK;
    }
    // finds all keys in the set in [low, high],
    // saves the number N of keys in numResults,
    // saves the keys themselves in resultKeys[0...N-1],
    // and saves their values in resultValues[0...N-1].
    RC index_range_query(KEY_TYPE low, KEY_TYPE high, KEY_TYPE * resultKeys, VALUE_TYPE * resultValues, int * numResults, int part_id = -1) {
        *numResults = index->rangeQuery(tid, low, high, resultKeys, (VALUES_ARRAY_TYPE) resultValues);
        INCREMENT_NUM_RQS(tid);
        return RCOK;
    }
    void initThread(const int tid) {
        index->initThread(tid);
    }
    void deinitThread(const int tid) {
        index->deinitThread(tid);
    }
    
    size_t getNodeSize() {
        return sizeof(NODE_TYPE);
    }
    
    size_t getDescriptorSize() {
        return sizeof(DESCRIPTOR_TYPE);
    }
    
    void print_stats(){
        calculate_index_stats(index->debug_getEntryPoint(),0);
        cout << "Nodes: "<< num_nodes << endl;
        cout << "Leafs: "<< num_leafs << endl;
        cout << "Keys: "<< num_keys << endl;
        cout << "Node size: " << sizeof(NODE_TYPE) << endl;
        cout << "Descriptor size: " << sizeof(DESCRIPTOR_TYPE) << endl;
        cout << "Max path length: " << max_depth << endl;
        cout << "Avg depth: " << (num_nodes?sum_nodes_depths/num_nodes:0) << endl;
        cout << "Avg leaf depth: " << (num_leafs?sum_leaf_depths/num_leafs:0) << endl; 
        for (int i=0; i<8 ;i++){
            cout << "Alignment " << i*8 << ": " << (alignment[i]/(double)num_nodes)*100 << "%" << endl;
        }
    }
};

#endif /* INDEX_WITH_RQ_H */
