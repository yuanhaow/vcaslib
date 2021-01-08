#ifndef DATA_STRUCTURE_H
#define DATA_STRUCTURE_H

const test_type NO_VALUE = -1;
const test_type KEY_MIN = numeric_limits<test_type>::min()+1;
const test_type KEY_MAX = numeric_limits<test_type>::max()-1; // must be less than max(), because the snap collector needs a reserved key larger than this!

#ifdef RQ_SNAPCOLLECTOR
    #define RQ_SNAPCOLLECTOR_OBJECT_TYPES , SnapCollector<node_t<test_type, test_type>, test_type>, SnapCollector<node_t<test_type, test_type>, test_type>::NodeWrapper, ReportItem, CompactReportItem
    #define RQ_SNAPCOLLECTOR_OBJ_SIZES <<" SnapCollector="<<(sizeof(SnapCollector<node_t<test_type, test_type>, test_type>))<<" NodeWrapper="<<(sizeof(SnapCollector<node_t<test_type, test_type>, test_type>::NodeWrapper))<<" ReportItem="<<(sizeof(ReportItem))<<" CompactReportItem="<<(sizeof(CompactReportItem))
#else
    #define RQ_SNAPCOLLECTOR_OBJECT_TYPES 
    #define RQ_SNAPCOLLECTOR_OBJ_SIZES 
#endif

#ifndef RQ_FUNC
    #define RQ_FUNC rangeQuery
#endif

#ifndef INSERT_FUNC
    #define INSERT_FUNC insert
#endif

#ifndef ERASE_FUNC
    #define ERASE_FUNC erase
#endif

#ifndef FIND_FUNC
    #define FIND_FUNC contains
#endif

#if defined ABTREE || defined BSLACK
    #define VALUE ((void*) (int64_t) key)
    #define KEY keys[0]
    #define VALUE_TYPE void *
#else
    #define VALUE key
    #define KEY key
    #define VALUE_TYPE test_type
#endif

#if defined ABTREE || defined BSLACK
    #if defined ABTREE
        #define USE_SIMPLIFIED_ABTREE_REBALANCING
    #endif
    #define ABTREE_DEGREE 16
    #include "record_manager.h"
    #include "bslack_impl.h"
    using namespace bslack_ns;

    #define DS_DECLARATION bslack<ABTREE_DEGREE, test_type, less<test_type>, MEMMGMT_T>
    #define MEMMGMT_T record_manager<RECLAIM, ALLOC, POOL, Node<ABTREE_DEGREE, test_type> >
    #define DS_CONSTRUCTOR new DS_DECLARATION(TOTAL_THREADS, ABTREE_DEGREE, KEY_MAX, SIGQUIT)

    // note: INSERT success checks use "== NO_VALUE" so that prefilling can tell that a new KEY has been inserted
    #define INSERT_AND_CHECK_SUCCESS ds->INSERT_FUNC(tid, key, VALUE) == ds->NO_VALUE
    #define DELETE_AND_CHECK_SUCCESS ds->ERASE_FUNC(tid, key).second
    #define FIND_AND_CHECK_SUCCESS ds->FIND_FUNC(tid, key)
    #define RQ_AND_CHECK_SUCCESS(rqcnt) (rqcnt) = ds->RQ_FUNC(tid, key, key+RQSIZE-1, rqResultKeys, (VALUE_TYPE *) rqResultValues)
    #define RQ_GARBAGE(rqcnt) rqResultKeys[0] + rqResultKeys[(rqcnt)-1]
    #define INIT_THREAD(tid) ds->initThread(tid)
    #define DEINIT_THREAD(tid) ds->deinitThread(tid)
    #define INIT_ALL
    #define DEINIT_ALL

    #define PRINT_OBJ_SIZES cout<<"sizes: node="<<(sizeof(Node<ABTREE_DEGREE, test_type>))<<" descriptor="<<(sizeof(SCXRecord<ABTREE_DEGREE, test_type>))<<endl;

#elif defined(BST)
    #include "record_manager.h"
    #include "bst_impl.h"
    using namespace bst_ns;

    #define DS_DECLARATION bst<test_type, test_type, less<test_type>, MEMMGMT_T>
    #define MEMMGMT_T record_manager<RECLAIM, ALLOC, POOL, Node<test_type, test_type> >
    #define DS_CONSTRUCTOR new DS_DECLARATION(KEY_MAX, NO_VALUE, TOTAL_THREADS, SIGQUIT)

    #define INSERT_AND_CHECK_SUCCESS ds->INSERT_FUNC(tid, key, VALUE) == ds->NO_VALUE
    #define DELETE_AND_CHECK_SUCCESS ds->ERASE_FUNC(tid, key).second
    #define FIND_AND_CHECK_SUCCESS ds->FIND_FUNC(tid, key)
    #define RQ_AND_CHECK_SUCCESS(rqcnt) (rqcnt) = ds->RQ_FUNC(tid, key, key+RQSIZE-1, rqResultKeys, (VALUE_TYPE *) rqResultValues)
    #define RQ_GARBAGE(rqcnt) rqResultKeys[0] + rqResultKeys[(rqcnt)-1]
    #define INIT_THREAD(tid) ds->initThread(tid)
    #define DEINIT_THREAD(tid) ds->deinitThread(tid)
    #define INIT_ALL 
    #define DEINIT_ALL

    #define PRINT_OBJ_SIZES cout<<"sizes: node="<<(sizeof(Node<test_type, test_type>))<<" descriptor="<<(sizeof(SCXRecord<test_type, test_type>))<<endl;

#elif defined(VCASBST)
    #include "record_manager.h"
    #include "vcas_bst_impl.h"
    using namespace vcas_bst_ns;

    #define DS_DECLARATION vcas_bst<test_type, test_type, less<test_type>, MEMMGMT_T>
    #define MEMMGMT_T record_manager<RECLAIM, ALLOC, POOL, Node<test_type, test_type> >
    #define DS_CONSTRUCTOR new DS_DECLARATION(KEY_MAX, NO_VALUE, TOTAL_THREADS, SIGQUIT)

    #define INSERT_AND_CHECK_SUCCESS ds->INSERT_FUNC(tid, key, VALUE) == ds->NO_VALUE
    #define DELETE_AND_CHECK_SUCCESS ds->ERASE_FUNC(tid, key).second
    #define FIND_AND_CHECK_SUCCESS ds->FIND_FUNC(tid, key)
    #define RQ_AND_CHECK_SUCCESS(rqcnt) (rqcnt) = ds->RQ_FUNC(tid, key, key+RQSIZE-1, rqResultKeys, (VALUE_TYPE *) rqResultValues)
    #define RQ_GARBAGE(rqcnt) rqResultKeys[0] + rqResultKeys[(rqcnt)-1]
    #define INIT_THREAD(tid) ds->initThread(tid)
    #define DEINIT_THREAD(tid) ds->deinitThread(tid)
    #define INIT_ALL 
    #define DEINIT_ALL

    #define PRINT_OBJ_SIZES cout<<"sizes: node="<<(sizeof(Node<test_type, test_type>))<<" descriptor="<<(sizeof(SCXRecord<test_type, test_type>))<<endl;

#elif defined(CITRUS)
    #include "record_manager.h"
    #include "citrus_impl.h"

    #define DS_DECLARATION citrustree<test_type, test_type, MEMMGMT_T>
    #define MEMMGMT_T record_manager<RECLAIM, ALLOC, POOL, node_t<test_type, test_type> >
    #define DS_CONSTRUCTOR new DS_DECLARATION(MAXKEY, NO_VALUE, TOTAL_THREADS)

    #define INSERT_AND_CHECK_SUCCESS ds->INSERT_FUNC(tid, key, VALUE) == ds->NO_VALUE
    #define DELETE_AND_CHECK_SUCCESS ds->ERASE_FUNC(tid, key).second
    #define FIND_AND_CHECK_SUCCESS ds->FIND_FUNC(tid, key)
    #define RQ_AND_CHECK_SUCCESS(rqcnt) rqcnt = ds->RQ_FUNC(tid, key, key+RQSIZE-1, rqResultKeys, (VALUE_TYPE *) rqResultValues)
    #define RQ_GARBAGE(rqcnt) rqResultKeys[0] + rqResultKeys[rqcnt-1]
    #define INIT_THREAD(tid) ds->initThread(tid); urcu::registerThread(tid);
    #define DEINIT_THREAD(tid) ds->deinitThread(tid); urcu::unregisterThread();
    #define INIT_ALL urcu::init(TOTAL_THREADS);
    #define DEINIT_ALL urcu::deinit(TOTAL_THREADS);

    #define PRINT_OBJ_SIZES cout<<"sizes: node="<<(sizeof(node_t<test_type, test_type>))<<endl;

#elif defined(LAZYLIST)
    #include "record_manager.h"
    #include "lazylist_impl.h"

    #define DS_DECLARATION lazylist<test_type, test_type, MEMMGMT_T>
    #define MEMMGMT_T record_manager<RECLAIM, ALLOC, POOL, node_t<test_type, test_type> >
    #define DS_CONSTRUCTOR new DS_DECLARATION(TOTAL_THREADS, KEY_MIN, KEY_MAX, NO_VALUE)

    #define INSERT_AND_CHECK_SUCCESS ds->INSERT_FUNC(tid, key, VALUE) == ds->NO_VALUE
    #define DELETE_AND_CHECK_SUCCESS ds->ERASE_FUNC(tid, key) != ds->NO_VALUE
    #define FIND_AND_CHECK_SUCCESS ds->FIND_FUNC(tid, key)
    #define RQ_AND_CHECK_SUCCESS(rqcnt) (rqcnt = ds->RQ_FUNC(tid, key, key+RQSIZE-1, rqResultKeys, (VALUE_TYPE *) rqResultValues))
    #define RQ_GARBAGE(rqcnt) rqResultKeys[0] + rqResultKeys[rqcnt-1]
    #define INIT_THREAD(tid) ds->initThread(tid)
    #define DEINIT_THREAD(tid) ds->deinitThread(tid);
    #define INIT_ALL 
    #define DEINIT_ALL

    #define PRINT_OBJ_SIZES cout<<"sizes: node="<<(sizeof(node_t<test_type, test_type>))<<endl;

#elif defined(SKIPLISTLOCK)
    #include "record_manager.h"
    #include "skiplist_lock_impl.h"

    #define DS_DECLARATION skiplist<test_type, test_type, MEMMGMT_T>
    #define MEMMGMT_T record_manager<RECLAIM, ALLOC, POOL, node_t<test_type, test_type> RQ_SNAPCOLLECTOR_OBJECT_TYPES>
    #define DS_CONSTRUCTOR new DS_DECLARATION(TOTAL_THREADS, KEY_MIN, KEY_MAX, NO_VALUE, glob.rngs)

    #define INSERT_AND_CHECK_SUCCESS ds->INSERT_FUNC(tid, key, VALUE) == ds->NO_VALUE
    #define DELETE_AND_CHECK_SUCCESS ds->ERASE_FUNC(tid, key) != ds->NO_VALUE
    #define FIND_AND_CHECK_SUCCESS ds->FIND_FUNC(tid, key)
    #define RQ_AND_CHECK_SUCCESS(rqcnt) (rqcnt = ds->RQ_FUNC(tid, key, key+RQSIZE-1, rqResultKeys, (VALUE_TYPE *) rqResultValues))
    #define RQ_GARBAGE(rqcnt) rqResultKeys[0] + rqResultKeys[rqcnt-1]
    #define INIT_THREAD(tid) ds->initThread(tid)
    #define DEINIT_THREAD(tid) ds->deinitThread(tid);
    #define INIT_ALL 
    #define DEINIT_ALL

    #define PRINT_OBJ_SIZES cout<<"sizes: node="<<(sizeof(node_t<test_type, test_type>)) RQ_SNAPCOLLECTOR_OBJ_SIZES<<endl;

#elif defined(LFLIST)
    #include "record_manager.h"
    #include "lockfree_list_impl.h"

    #define DS_DECLARATION lflist<test_type, test_type, MEMMGMT_T>
    #define MEMMGMT_T record_manager<RECLAIM, ALLOC, POOL, node_t<test_type, test_type> RQ_SNAPCOLLECTOR_OBJECT_TYPES>
    #define DS_CONSTRUCTOR new DS_DECLARATION(TOTAL_THREADS, KEY_MIN, KEY_MAX, NO_VALUE)

    #define INSERT_AND_CHECK_SUCCESS ds->INSERT_FUNC(tid, key, VALUE) == ds->NO_VALUE
    #define DELETE_AND_CHECK_SUCCESS ds->ERASE_FUNC(tid, key) != ds->NO_VALUE
    #define FIND_AND_CHECK_SUCCESS ds->FIND_FUNC(tid, key)
    #define RQ_AND_CHECK_SUCCESS(rqcnt) rqcnt = ds->RQ_FUNC(tid, key, key+RQSIZE-1, rqResultKeys, (VALUE_TYPE *) rqResultValues)
    #define RQ_GARBAGE(rqcnt) rqResultKeys[0] + rqResultKeys[rqcnt-1]
    #define INIT_THREAD(tid) ds->initThread(tid)
    #define DEINIT_THREAD(tid) ds->deinitThread(tid);
    #define INIT_ALL 
    #define DEINIT_ALL

    #define PRINT_OBJ_SIZES cout<<"sizes: node="<<(sizeof(node_t<test_type, test_type>)) RQ_SNAPCOLLECTOR_OBJ_SIZES<<endl;

#elif defined(LFSKIPLIST)
    #include "record_manager.h"
    #include "lockfree_skiplist_impl.h"

    #define DS_DECLARATION lfskiplist<test_type, test_type, MEMMGMT_T>
    #define MEMMGMT_T record_manager<RECLAIM, ALLOC, POOL, node_t<test_type, test_type> RQ_SNAPCOLLECTOR_OBJECT_TYPES>
    #define DS_CONSTRUCTOR new DS_DECLARATION(TOTAL_THREADS, KEY_MIN, KEY_MAX, NO_VALUE)

    #define INSERT_AND_CHECK_SUCCESS ds->INSERT_FUNC(tid, key, VALUE) == ds->NO_VALUE
    #define DELETE_AND_CHECK_SUCCESS ds->ERASE_FUNC(tid, key) != ds->NO_VALUE
    #define FIND_AND_CHECK_SUCCESS ds->FIND_FUNC(tid, key)
    #define RQ_AND_CHECK_SUCCESS(rqcnt) rqcnt = ds->RQ_FUNC(tid, key, key+RQSIZE-1, rqResultKeys, (VALUE_TYPE *) rqResultValues)
    #define RQ_GARBAGE(rqcnt) rqResultKeys[0] + rqResultKeys[rqcnt-1]
    #define INIT_THREAD(tid) ds->initThread(tid)
    #define DEINIT_THREAD(tid) ds->deinitThread(tid);
    #define INIT_ALL 
    #define DEINIT_ALL

    #define PRINT_OBJ_SIZES cout<<"sizes: node="<<(sizeof(node_t<test_type, test_type>)) RQ_SNAPCOLLECTOR_OBJ_SIZES<<endl;

#elif defined(RLU_LIST)
    #include "record_manager.h"
    #include "rlu.h"
    #include "rlu_list_impl.h"

    #define DS_DECLARATION rlulist<test_type, test_type>
    #define MEMMGMT_T record_manager<RECLAIM, ALLOC, POOL, node_t<test_type, test_type> >
    #define DS_CONSTRUCTOR new DS_DECLARATION(TOTAL_THREADS, KEY_MIN, KEY_MAX, NO_VALUE)

    #define INSERT_AND_CHECK_SUCCESS ds->INSERT_FUNC(tid, key, VALUE) == ds->NO_VALUE
    #define DELETE_AND_CHECK_SUCCESS ds->ERASE_FUNC(tid, key).second
    #define FIND_AND_CHECK_SUCCESS ds->FIND_FUNC(tid, key)
    #define RQ_AND_CHECK_SUCCESS(rqcnt) rqcnt = ds->RQ_FUNC(tid, key, key+RQSIZE-1, rqResultKeys, (VALUE_TYPE *) rqResultValues)
    #define RQ_GARBAGE(rqcnt) rqResultKeys[0] + rqResultKeys[rqcnt-1]
    __thread rlu_thread_data_t * rlu_self;
    rlu_thread_data_t * rlu_tdata = NULL;
    #define INIT_THREAD(tid) rlu_self = &rlu_tdata[tid]; RLU_THREAD_INIT(rlu_self);
    #define DEINIT_THREAD(tid) RLU_THREAD_FINISH(rlu_self);
    #define INIT_ALL rlu_tdata = new rlu_thread_data_t[MAX_TID_POW2]; RLU_INIT(RLU_TYPE_FINE_GRAINED, 1)
    #define DEINIT_ALL RLU_FINISH(); delete[] rlu_tdata;

    #define PRINT_OBJ_SIZES cout<<"sizes: node="<<((sizeof(node_t<test_type, test_type>))+RLU_OBJ_HEADER_SIZE)<<" including header="<<RLU_OBJ_HEADER_SIZE<<endl;
    
#elif defined(RLU_CITRUS)
    #include "record_manager.h"
    #include "rlu.h"
    #include "rlu_citrus_impl.h"

    #define DS_DECLARATION rlucitrus<test_type, test_type>
    #define MEMMGMT_T record_manager<RECLAIM, ALLOC, POOL, node_t<test_type, test_type> >
    #define DS_CONSTRUCTOR new DS_DECLARATION(TOTAL_THREADS, KEY_MAX, NO_VALUE)

    #define INSERT_AND_CHECK_SUCCESS ds->INSERT_FUNC(tid, key, VALUE) == ds->NO_VALUE
    #define DELETE_AND_CHECK_SUCCESS ds->ERASE_FUNC(tid, key).second
    #define FIND_AND_CHECK_SUCCESS ds->FIND_FUNC(tid, key)
    #define RQ_AND_CHECK_SUCCESS(rqcnt) rqcnt = ds->RQ_FUNC(tid, key, key+RQSIZE-1, rqResultKeys, (VALUE_TYPE *) rqResultValues)
    #define RQ_GARBAGE(rqcnt) rqResultKeys[0] + rqResultKeys[rqcnt-1]
    __thread rlu_thread_data_t * rlu_self;
    rlu_thread_data_t * rlu_tdata = NULL;
    #define INIT_THREAD(tid) rlu_self = &rlu_tdata[tid]; RLU_THREAD_INIT(rlu_self);
    #define DEINIT_THREAD(tid) RLU_THREAD_FINISH(rlu_self);
    #define INIT_ALL rlu_tdata = new rlu_thread_data_t[MAX_TID_POW2]; RLU_INIT(RLU_TYPE_FINE_GRAINED, 1)
    #define DEINIT_ALL RLU_FINISH(); delete[] rlu_tdata;

    #define PRINT_OBJ_SIZES cout<<"sizes: node="<<((sizeof(node_t<test_type, test_type>))+RLU_OBJ_HEADER_SIZE)<<" including header="<<RLU_OBJ_HEADER_SIZE<<endl;

#else
    #error "Failed to define a data structure"
#endif

#endif /* DATA_STRUCTURE_H */

