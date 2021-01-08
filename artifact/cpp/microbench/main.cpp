/**
 * Range Query Provider library:
 * adding range query operations to concurrent data structures
 *
 * This is an artifact to accompany the paper (s):
 *     Harnessing epoch-based reclamation for efficient range queries.
 *     Maya Arbel-Raviv and Trevor Brown.
 * 
 * Copyright (C) 2017 Trevor Brown and Maya Arbel-Raviv
 *
 * 
 *
 */

#define MICROBENCH

#ifdef __CYGWIN__
    typedef long long __syscall_slong_t;
#endif

typedef long long test_type;

#if defined(VCAS_STATS)
    #define PADDING 64
    long long versionNodesTraversed[144*PADDING];
    long long distinctNodesVisited[144*PADDING]; // including version lst
    // long long nodesVisited[144*PADDING]; // including version list
    // thread_local std::unordered_set<void*> nodesSeen;
#endif

// TODO: use system clock (chrono::) to precisely calibrate CPU_FREQ_GHZ in a setup program (rather than having the user enter a specific GHZ number); then, get rid of chrono:: usage.

#include <limits>
#include <cstring>
#include <ctime>
#include <pthread.h>
#include <atomic>
#include <chrono>
#include <cassert>
#include "globals.h"
#include "globals_extern.h"
#include "rq_debugging.h"
#include "random.h"
#include "plaf.h"
#include "binding.h"
#include "papi_util_impl.h"
#include "urcu_impl.h"
#ifdef USE_DEBUGCOUNTERS
    #include "debugcounters.h"
#endif
#include "data_structures.h"

#include <unistd.h>
#include <ios>
#include <iostream>
#include <fstream>
#include <string>

using namespace std;

#if !defined USE_DEBUGCOUNTERS && !defined USE_GSTATS
#error "Must define either USE_GSTATS or USE_DEBUGCOUNTERS."
#endif

struct main_globals_t {
    volatile char padding0[PREFETCH_SIZE_BYTES];
    Random rngs[MAX_TID_POW2*PREFETCH_SIZE_WORDS]; // create per-thread random number generators (padded to avoid false sharing)
    volatile char padding1[PREFETCH_SIZE_BYTES];

    // variables used in the concurrent test
    volatile char padding2[PREFETCH_SIZE_BYTES];
    chrono::time_point<chrono::high_resolution_clock> startTime;
    chrono::time_point<chrono::high_resolution_clock> endTime;
    volatile char padding3[PREFETCH_SIZE_BYTES];
    long elapsedMillis;
    long elapsedMillisNapping;
    volatile char padding4[PREFETCH_SIZE_BYTES];

    bool start;
    bool done;
    volatile char padding5[PREFETCH_SIZE_BYTES];
    atomic_int running; // number of threads that are running
    volatile char padding6[PREFETCH_SIZE_BYTES];
#ifdef USE_DEBUGCOUNTERS
    debugCounter * keysum; // key sum hashes for all threads (including for prefilling)
    debugCounter * prefillSize;
#endif
    volatile char padding7[PREFETCH_SIZE_BYTES];

    void *__ds; // the data structure

    volatile char padding8[PREFETCH_SIZE_BYTES];
    test_type __garbage;

    volatile char padding9[PREFETCH_SIZE_BYTES];
    volatile long long prefillIntervalElapsedMillis;
    volatile char padding10[PREFETCH_SIZE_BYTES];
    long long prefillKeySum;
    volatile char padding11[PREFETCH_SIZE_BYTES];
};

main_globals_t glob = {0,};

const long long PREFILL_INTERVAL_MILLIS = 100;

#define STR(x) XSTR(x)
#define XSTR(x) #x

#define PRINTI(name) { cout<<#name<<"="<<name<<endl; }
#define PRINTS(name) { cout<<#name<<"="<<STR(name)<<endl; }

#ifndef OPS_BETWEEN_TIME_CHECKS
#define OPS_BETWEEN_TIME_CHECKS 500
#endif
#ifndef RQS_BETWEEN_TIME_CHECKS
#define RQS_BETWEEN_TIME_CHECKS 10
#endif

#ifdef USE_DEBUGCOUNTERS
    #define GET_COUNTERS ds->debugGetCounters()
    #define CLEAR_COUNTERS ds->clearCounters();
#else
    #define GET_COUNTERS 
    #define CLEAR_COUNTERS 
#endif
    
void *thread_prefill(void *_id) {
    int tid = *((int*) _id);
    binding_bindThread(tid, LOGICAL_PROCESSORS);
    Random *rng = &glob.rngs[tid*PREFETCH_SIZE_WORDS];
    DS_DECLARATION * ds = (DS_DECLARATION *) glob.__ds;
    test_type garbage = 0;

    double insProbability = (INS > 0 ? 100*INS/(INS+DEL) : 50.);
    
    INIT_THREAD(tid);
    glob.running.fetch_add(1);
    __sync_synchronize();
    while (!glob.start) { __sync_synchronize(); TRACE COUTATOMICTID("waiting to start"<<endl); } // wait to start
    int cnt = 0;
    chrono::time_point<chrono::high_resolution_clock> __endTime = glob.startTime;
    while (!glob.done) {
        if (((++cnt) % OPS_BETWEEN_TIME_CHECKS) == 0) {
            __endTime = chrono::high_resolution_clock::now();
            if (chrono::duration_cast<chrono::milliseconds>(__endTime-glob.startTime).count() >= PREFILL_INTERVAL_MILLIS) {
                glob.done = true;
                __sync_synchronize();
                break;
            }
        }
        
        VERBOSE if (cnt&&((cnt % 1000000) == 0)) COUTATOMICTID("op# "<<cnt<<endl);
        int key = rng->nextNatural(MAXKEY);
        double op = rng->nextNatural(100000000) / 1000000.;
        GSTATS_TIMER_RESET(tid, timer_latency);
        if (op < insProbability) {
            if (INSERT_AND_CHECK_SUCCESS) {
                GSTATS_ADD(tid, key_checksum, key);
                GSTATS_ADD(tid, prefill_size, 1);
#ifdef USE_DEBUGCOUNTERS
                glob.keysum->add(tid, key);
                glob.prefillSize->add(tid, 1);
                GET_COUNTERS->insertSuccess->inc(tid);
            } else {
                GET_COUNTERS->insertFail->inc(tid);
#endif
            }
            GSTATS_ADD(tid, num_updates, 1);
        } else {
            if (DELETE_AND_CHECK_SUCCESS) {
                GSTATS_ADD(tid, key_checksum, -key);
                GSTATS_ADD(tid, prefill_size, -1);
#ifdef USE_DEBUGCOUNTERS
                glob.keysum->add(tid, -key);
                glob.prefillSize->add(tid, -1);
                GET_COUNTERS->eraseSuccess->inc(tid);
            } else {
                GET_COUNTERS->eraseFail->inc(tid);
#endif
            }
            GSTATS_ADD(tid, num_updates, 1);
        }
        GSTATS_ADD(tid, num_operations, 1);
        GSTATS_TIMER_APPEND_ELAPSED(tid, timer_latency, latency_updates);
    }

    glob.running.fetch_add(-1);
    while (glob.running.load()) {
        // wait
    }
    
    DEINIT_THREAD(tid);
    glob.__garbage += garbage;
    __sync_bool_compare_and_swap(&glob.prefillIntervalElapsedMillis, 0, chrono::duration_cast<chrono::milliseconds>(__endTime-glob.startTime).count());
    pthread_exit(NULL);
}

void prefill(DS_DECLARATION * ds) {
    chrono::time_point<chrono::high_resolution_clock> prefillStartTime = chrono::high_resolution_clock::now();

    const double PREFILL_THRESHOLD = 0.01;
    const int MAX_ATTEMPTS = 1000;
    const double expectedFullness = (INS+DEL ? INS / (double)(INS+DEL) : 0.5); // percent full in expectation
    const int expectedSize = (int)(MAXKEY * expectedFullness);

    long long totalThreadsPrefillElapsedMillis = 0;
    
    int sz = 0;
    int attempts;
    for (attempts=0;attempts<MAX_ATTEMPTS;++attempts) {
        INIT_ALL;
        DS_DECLARATION * ds = (DS_DECLARATION *) glob.__ds;
        
        // create threads
        pthread_t *threads = new pthread_t[TOTAL_THREADS];
        int *ids = new int[TOTAL_THREADS];
        for (int i=0;i<TOTAL_THREADS;++i) {
            ids[i] = i;
            glob.rngs[i*PREFETCH_SIZE_WORDS].setSeed(rand());
        }

        // start all threads
        for (int i=0;i<TOTAL_THREADS;++i) {
            if (pthread_create(&threads[i], NULL, thread_prefill, &ids[i])) {
                cerr<<"ERROR: could not create thread"<<endl;
                exit(-1);
            }
        }

        TRACE COUTATOMIC("main thread: waiting for threads to START prefilling running="<<glob.running.load()<<endl);
        while (glob.running.load() < TOTAL_THREADS) {}
        TRACE COUTATOMIC("main thread: starting prefilling timer..."<<endl);
        glob.startTime = chrono::high_resolution_clock::now();
        
        glob.prefillIntervalElapsedMillis = 0;
        __sync_synchronize();
        glob.start = true;
        
        /**
         * START INFINITE LOOP DETECTION CODE
         */
        // amount of time for main thread to wait for children threads
        timespec tsExpected;
        tsExpected.tv_sec = 0;
        tsExpected.tv_nsec = PREFILL_INTERVAL_MILLIS * ((__syscall_slong_t) 1000000);
        // short nap
        timespec tsNap;
        tsNap.tv_sec = 0;
        tsNap.tv_nsec = 10000000; // 10ms

        nanosleep(&tsExpected, NULL);
        glob.done = true;
        __sync_synchronize();

        const long MAX_NAPPING_MILLIS = 5000;
        glob.elapsedMillis = chrono::duration_cast<chrono::milliseconds>(chrono::high_resolution_clock::now() - glob.startTime).count();
        glob.elapsedMillisNapping = 0;
        while (glob.running.load() > 0 && glob.elapsedMillisNapping < MAX_NAPPING_MILLIS) {
            nanosleep(&tsNap, NULL);
            glob.elapsedMillisNapping = chrono::duration_cast<chrono::milliseconds>(chrono::high_resolution_clock::now() - glob.startTime).count() - glob.elapsedMillis;
        }
        if (glob.running.load() > 0) {
            COUTATOMIC(endl);
            COUTATOMIC("Validation FAILURE: "<<glob.running.load()<<" non-responsive thread(s) [during prefill]"<<endl);
            COUTATOMIC(endl);
            exit(-1);
        }
        /**
         * END INFINITE LOOP DETECTION CODE
         */
        
        TRACE COUTATOMIC("main thread: waiting for threads to STOP prefilling running="<<glob.running.load()<<endl);
        while (glob.running.load() > 0) {}

        for (int i=0;i<TOTAL_THREADS;++i) {
            if (pthread_join(threads[i], NULL)) {
                cerr<<"ERROR: could not join prefilling thread"<<endl;
                exit(-1);
            }
        }
        
        delete[] threads;
        delete[] ids;

        glob.start = false;
        glob.done = false;

#ifdef USE_DEBUGCOUNTERS
        sz = glob.prefillSize->getTotal();
#elif defined USE_GSTATS
        sz = GSTATS_OBJECT_NAME.get_sum<long long>(prefill_size);
#endif
        if (sz > expectedSize*(1-PREFILL_THRESHOLD)) {
            break;
        } else {
            cout << " finished attempt ds seize: " << sz << endl; 
        }
        
        totalThreadsPrefillElapsedMillis += glob.prefillIntervalElapsedMillis;
        DEINIT_ALL;
    }
    
    if (attempts >= MAX_ATTEMPTS) {
        cerr<<"ERROR: could not prefill to expected size "<<expectedSize<<". reached size "<<sz<<" after "<<attempts<<" attempts"<<endl;
        exit(-1);
    }
    
    chrono::time_point<chrono::high_resolution_clock> prefillEndTime = chrono::high_resolution_clock::now();
    auto elapsed = chrono::duration_cast<chrono::milliseconds>(prefillEndTime-prefillStartTime).count();

#ifdef USE_DEBUGCOUNTERS
    debugCounters * const counters = GET_COUNTERS;
    const long totalSuccUpdates = counters->insertSuccess->getTotal()+counters->eraseSuccess->getTotal();
    COUTATOMIC("finished prefilling to size "<<sz<<" for expected size "<<expectedSize<<" keysum="<<glob.keysum->getTotal()<<" dskeysum="<<ds->debugKeySum()<<" dssize="<<ds->getSize()<<", performing "<<totalSuccUpdates<<" successful updates in "<<(totalThreadsPrefillElapsedMillis/1000.) /*(elapsed/1000.)*/<<" seconds (total time "<<(elapsed/1000.)<<"s)"<<endl);
    CLEAR_COUNTERS;
#endif
#ifdef USE_GSTATS
    GSTATS_PRINT;
    const long totalSuccUpdates = GSTATS_GET_STAT_METRICS(num_updates, TOTAL)[0].sum;
    glob.prefillKeySum = GSTATS_GET_STAT_METRICS(key_checksum, TOTAL)[0].sum;
    COUTATOMIC("finished prefilling to size "<<sz<<" for expected size "<<expectedSize<<" keysum="<< glob.prefillKeySum <<" dskeysum="<<ds->debugKeySum()<<" dssize="<<ds->getSize()<<", performing "<<totalSuccUpdates<<" successful updates in "<<(totalThreadsPrefillElapsedMillis/1000.) /*(elapsed/1000.)*/<<" seconds (total time "<<(elapsed/1000.)<<"s)"<<endl);
    GSTATS_CLEAR_ALL;
#endif
}

void *thread_timed(void *_id) {
    int tid = *((int*) _id);
    binding_bindThread(tid, LOGICAL_PROCESSORS);
    test_type garbage = 0;
    Random *rng = &glob.rngs[tid*PREFETCH_SIZE_WORDS];
    DS_DECLARATION * ds = (DS_DECLARATION *) glob.__ds;

    test_type * rqResultKeys = new test_type[RQSIZE+RQ_DEBUGGING_MAX_KEYS_PER_NODE];
    VALUE_TYPE * rqResultValues = new VALUE_TYPE[RQSIZE+RQ_DEBUGGING_MAX_KEYS_PER_NODE];
    
    INIT_THREAD(tid);
    papi_create_eventset(tid);
    glob.running.fetch_add(1);
    __sync_synchronize();
    while (!glob.start) { __sync_synchronize(); TRACE COUTATOMICTID("waiting to start"<<endl); } // wait to start
    papi_start_counters(tid);
    int cnt = 0;
    int rq_cnt = 0;
    while (!glob.done) {
        if (((++cnt) % OPS_BETWEEN_TIME_CHECKS) == 0 || (rq_cnt % RQS_BETWEEN_TIME_CHECKS) == 0) {
            chrono::time_point<chrono::high_resolution_clock> __endTime = chrono::high_resolution_clock::now();
            if (chrono::duration_cast<chrono::milliseconds>(__endTime-glob.startTime).count() >= abs(MILLIS_TO_RUN)) {
                __sync_synchronize();
                glob.done = true;
                __sync_synchronize();
                break;
            }
        }
        
        VERBOSE if (cnt&&((cnt % 1000000) == 0)) COUTATOMICTID("op# "<<cnt<<endl);
        int key = rng->nextNatural(MAXKEY);
        double op = rng->nextNatural(100000000) / 1000000.;
        if (op < INS) {
            GSTATS_TIMER_RESET(tid, timer_latency);
            if (INSERT_AND_CHECK_SUCCESS) {
                GSTATS_ADD(tid, key_checksum, key);
#ifdef USE_DEBUGCOUNTERS
                glob.keysum->add(tid, key);
                GET_COUNTERS->insertSuccess->inc(tid);
            } else {
                GET_COUNTERS->insertFail->inc(tid);
#endif
            }
            GSTATS_TIMER_APPEND_ELAPSED(tid, timer_latency, latency_updates);
            GSTATS_ADD(tid, num_updates, 1);
        } else if (op < INS+DEL) {
            GSTATS_TIMER_RESET(tid, timer_latency);
            if (DELETE_AND_CHECK_SUCCESS) {
                GSTATS_ADD(tid, key_checksum, -key);
#ifdef USE_DEBUGCOUNTERS
                glob.keysum->add(tid, -key);
                GET_COUNTERS->eraseSuccess->inc(tid);
            } else {
                GET_COUNTERS->eraseFail->inc(tid);
#endif
            }
            GSTATS_TIMER_APPEND_ELAPSED(tid, timer_latency, latency_updates);
            GSTATS_ADD(tid, num_updates, 1);
        } else if (op < INS+DEL+RQ) {
            unsigned _key = rng->nextNatural() % max(1, MAXKEY - RQSIZE);
            assert(_key >= 0);
            assert(_key < MAXKEY);
            assert(_key < max(1, MAXKEY - RQSIZE));
            assert(MAXKEY > RQSIZE || _key == 0);
            key = (int) _key;
            
            ++rq_cnt;
            int rqcnt;
            GSTATS_TIMER_RESET(tid, timer_latency);
            if (RQ_AND_CHECK_SUCCESS(rqcnt)) { // prevent rqResultKeys and count from being optimized out
                garbage += RQ_GARBAGE(rqcnt);
#ifdef USE_DEBUGCOUNTERS
                GET_COUNTERS->rqSuccess->inc(tid);
            } else {
                GET_COUNTERS->rqFail->inc(tid);
#endif
            }
            GSTATS_TIMER_APPEND_ELAPSED(tid, timer_latency, latency_rqs);
            GSTATS_ADD(tid, num_rq, 1);
        } else {
            GSTATS_TIMER_RESET(tid, timer_latency);
            if (FIND_AND_CHECK_SUCCESS) {
#ifdef USE_DEBUGCOUNTERS
                GET_COUNTERS->findSuccess->inc(tid);
            } else {
                GET_COUNTERS->findFail->inc(tid);
#endif
            }
            GSTATS_TIMER_APPEND_ELAPSED(tid, timer_latency, latency_searches);
            GSTATS_ADD(tid, num_searches, 1);
        }
        GSTATS_ADD(tid, num_operations, 1);
    }
    glob.running.fetch_add(-1);
    while (glob.running.load()) { /* wait */ }
    
    papi_stop_counters(tid);
    DEINIT_THREAD(tid);
    delete[] rqResultKeys;
    delete[] rqResultValues;
    glob.__garbage += garbage;
    pthread_exit(NULL);
}

void *thread_rq(void *_id) {
    int tid = *((int*) _id);
    binding_bindThread(tid, LOGICAL_PROCESSORS);
    test_type garbage = 0;
    Random *rng = &glob.rngs[tid*PREFETCH_SIZE_WORDS];
    DS_DECLARATION * ds = (DS_DECLARATION *) glob.__ds;

    test_type * rqResultKeys = new test_type[RQSIZE+RQ_DEBUGGING_MAX_KEYS_PER_NODE];
    VALUE_TYPE * rqResultValues = new VALUE_TYPE[RQSIZE+RQ_DEBUGGING_MAX_KEYS_PER_NODE];
    
    INIT_THREAD(tid);
    papi_create_eventset(tid);
    glob.running.fetch_add(1);
    __sync_synchronize();
    while (!glob.start) { __sync_synchronize(); TRACE COUTATOMICTID("waiting to start"<<endl); } // wait to start
    papi_start_counters(tid);
    int cnt = 0;
    while (!glob.done) {
        if (((++cnt) % RQS_BETWEEN_TIME_CHECKS) == 0) {
            chrono::time_point<chrono::high_resolution_clock> __endTime = chrono::high_resolution_clock::now();
            if (chrono::duration_cast<chrono::milliseconds>(__endTime-glob.startTime).count() >= MILLIS_TO_RUN) {
                __sync_synchronize();
                glob.done = true;
                __sync_synchronize();
                break;
            }
        }
        
        VERBOSE if (cnt&&((cnt % 1000000) == 0)) COUTATOMICTID("op# "<<cnt<<endl);
        unsigned _key = rng->nextNatural() % max(1, MAXKEY - RQSIZE);
        assert(_key >= 0);
        assert(_key < MAXKEY);
        assert(_key < max(1, MAXKEY - RQSIZE));
        assert(MAXKEY > RQSIZE || _key == 0);
        
        int key = (int) _key;
        int rqcnt;
        GSTATS_TIMER_RESET(tid, timer_latency);
        if (RQ_AND_CHECK_SUCCESS(rqcnt)) { // prevent rqResultKeys and count from being optimized out
            garbage += RQ_GARBAGE(rqcnt);
#ifdef USE_DEBUGCOUNTERS
                GET_COUNTERS->rqSuccess->inc(tid);
            } else {
                GET_COUNTERS->rqFail->inc(tid);
#endif
        }
        GSTATS_TIMER_APPEND_ELAPSED(tid, timer_latency, latency_rqs);
        GSTATS_ADD(tid, num_rq, 1);
        GSTATS_ADD(tid, num_operations, 1);
    }
    glob.running.fetch_add(-1);
    while (glob.running.load()) {
        // wait
    }
    
    papi_stop_counters(tid);
    DEINIT_THREAD(tid);
    delete[] rqResultKeys;
    delete[] rqResultValues;
    glob.__garbage += garbage;
    pthread_exit(NULL);
}

void trial() {
    INIT_ALL;
    papi_init_program(TOTAL_THREADS);

    glob.elapsedMillis = 0;
    glob.elapsedMillisNapping = 0;
    glob.start = false;
    glob.done = false;
    glob.running = 0;
    glob.__ds = (void *) DS_CONSTRUCTOR;
    glob.prefillIntervalElapsedMillis = 0;
    glob.prefillKeySum = 0;
    DS_DECLARATION * ds = (DS_DECLARATION *) glob.__ds;
    
    // get random number generator seeded with time
    // we use this rng to seed per-thread rng's that use a different algorithm
    srand(time(NULL));

    // create thread data
    pthread_t *threads[TOTAL_THREADS];
    int ids[TOTAL_THREADS];
    for (int i=0;i<TOTAL_THREADS;++i) {
        threads[i] = new pthread_t;
        ids[i] = i;
        glob.rngs[i*PREFETCH_SIZE_WORDS].setSeed(rand());
    }

    DEINIT_ALL;
    
    if (PREFILL) prefill((DS_DECLARATION *) glob.__ds);

    INIT_ALL;

    // amount of time for main thread to wait for children threads
    timespec tsExpected;
    tsExpected.tv_sec = MILLIS_TO_RUN / 1000;
    tsExpected.tv_nsec = (MILLIS_TO_RUN % 1000) * ((__syscall_slong_t) 1000000);
    // short nap
    timespec tsNap;
    tsNap.tv_sec = 0;
    tsNap.tv_nsec = 10000000; // 10ms

    // start all threads
    for (int i=0;i<TOTAL_THREADS;++i) {
        if (pthread_create(threads[i], NULL,
                    (i < WORK_THREADS
                       ? thread_timed
                       : thread_rq), &ids[i])) {
            cerr<<"ERROR: could not create thread"<<endl;
            exit(-1);
        }
    }

    while (glob.running.load() < TOTAL_THREADS) {
        TRACE COUTATOMIC("main thread: waiting for threads to START running="<<glob.running.load()<<endl);
    } // wait for all threads to be ready
    COUTATOMIC("main thread: starting timer..."<<endl);
    
    COUTATOMIC(endl);
    COUTATOMIC("###############################################################################"<<endl);
    COUTATOMIC("################################ BEGIN RUNNING ################################"<<endl);
    COUTATOMIC("###############################################################################"<<endl);
    COUTATOMIC(endl);
    
    SOFTWARE_BARRIER;
    glob.startTime = chrono::high_resolution_clock::now();
    __sync_synchronize();
    glob.start = true;
    SOFTWARE_BARRIER;

    // pthread_join is replaced with sleeping, and kill threads if they run too long
    // method: sleep for the desired time + a small epsilon,
    //      then check "running.load()" to see if we're done.
    //      if not, loop and sleep in small increments for up to 5s,
    //      and exit(-1) if running doesn't hit 0.

    if (MILLIS_TO_RUN > 0) {
        nanosleep(&tsExpected, NULL);
        SOFTWARE_BARRIER;
        glob.done = true;
        __sync_synchronize();
    }

    const long MAX_NAPPING_MILLIS = (MILLIS_TO_RUN > 0 ? 5000 : 30000);
    glob.elapsedMillis = chrono::duration_cast<chrono::milliseconds>(chrono::high_resolution_clock::now() - glob.startTime).count();
    glob.elapsedMillisNapping = 0;
    while (glob.running.load() > 0 && glob.elapsedMillisNapping < MAX_NAPPING_MILLIS) {
        nanosleep(&tsNap, NULL);
        glob.elapsedMillisNapping = chrono::duration_cast<chrono::milliseconds>(chrono::high_resolution_clock::now() - glob.startTime).count() - glob.elapsedMillis;
    }
    if (glob.running.load() > 0) {
        COUTATOMIC(endl);
        COUTATOMIC("Validation FAILURE: "<<glob.running.load()<<" non-terminating thread(s) [did we exhaust physical memory and experience excessive slowdown due to swap mem?]"<<endl);
        COUTATOMIC(endl);
        COUTATOMIC("elapsedMillis="<<glob.elapsedMillis<<" elapsedMillisNapping="<<glob.elapsedMillisNapping<<endl);
        
//        for (int i=0;i<TOTAL_THREADS;++i) {
//            pthread_cancel(*(threads[i]));
//        }
        
        DS_DECLARATION * ds = (DS_DECLARATION *) glob.__ds;
        if (ds->validate(0, false)) {
            cout<<"Structural validation OK"<<endl;
        } else {
            cout<<"Structural validation FAILURE."<<endl;
        }
        MEMMGMT_T * recmgr = (MEMMGMT_T *) ds->debugGetRecMgr();
        if (recmgr) recmgr->printStatus();
        DEBUG_VALIDATE_RQ(TOTAL_THREADS);
        exit(-1);
    }

    // join all threads
    for (int i=0;i<TOTAL_THREADS;++i) {
        COUTATOMIC("joining thread "<<i<<endl);
        if (pthread_join(*(threads[i]), NULL)) {
            cerr<<"ERROR: could not join thread"<<endl;
            exit(-1);
        }
    }

    COUTATOMIC(endl);
    COUTATOMIC("###############################################################################"<<endl);
    COUTATOMIC("################################# END RUNNING #################################"<<endl);
    COUTATOMIC("###############################################################################"<<endl);
    COUTATOMIC(endl);
    
    COUTATOMIC(((glob.elapsedMillis+glob.elapsedMillisNapping)/1000.)<<"s"<<endl);

    papi_deinit_program();
    DEINIT_ALL;
   
    for (int i=0;i<TOTAL_THREADS;++i) {
        delete threads[i];
    }
}

void printOutput() {
    cout<<"PRODUCING OUTPUT"<<endl;
    DS_DECLARATION * ds = (DS_DECLARATION *) glob.__ds;

#ifdef USE_GSTATS
    GSTATS_PRINT;
    cout<<endl;
#endif
    
    long long threadsKeySum = 0;
#ifdef USE_DEBUGCOUNTERS
    {
        threadsKeySum = glob.keysum->getTotal();
        long long dsKeySum = ds->debugKeySum();
        if (threadsKeySum == dsKeySum) {
            cout<<"Keysum Validation OK"<<endl;
        } else {
            cout<<"Keysum Validation FAILURE: threadsKeySum = "<<threadsKeySum<<" dsKeySum="<<dsKeySum<<endl;
            exit(-1);
        }
    }
#endif
    
#ifdef USE_GSTATS
    {
        threadsKeySum = GSTATS_GET_STAT_METRICS(key_checksum, TOTAL)[0].sum + glob.prefillKeySum;
        long long dsKeySum = ds->debugKeySum();
        if (threadsKeySum == dsKeySum) {
            cout<<"Keysum Validation OK"<<endl;
        } else {
            cout<<"Keysum Validation FAILURE: threadsKeySum = "<<threadsKeySum<<" dsKeySum="<<dsKeySum<<endl;
            exit(-1);
        }
    }
#endif
    
    if (ds->validate(threadsKeySum, true)) {
        cout<<"Structural validation OK"<<endl;
    } else {
        cout<<"Structural validation FAILURE."<<endl;
        exit(-1);
    }

    long long totalAll = 0;

#ifdef USE_DEBUGCOUNTERS
    debugCounters * const counters = GET_COUNTERS;
    {
        const long long totalSearches = counters->findSuccess->getTotal() + counters->findFail->getTotal();
        const long long totalRQs = counters->rqSuccess->getTotal() + counters->rqFail->getTotal();
        const long long totalQueries = totalSearches + totalRQs;
        const long long totalUpdates = counters->insertSuccess->getTotal() + counters->insertFail->getTotal()
                + counters->eraseSuccess->getTotal() + counters->eraseFail->getTotal();

        const double SECONDS_TO_RUN = (MILLIS_TO_RUN)/1000.;
        totalAll = totalUpdates + totalQueries;
        const long long throughputSearches = (long long) (totalSearches / SECONDS_TO_RUN);
        const long long throughputRQs = (long long) (totalRQs / SECONDS_TO_RUN);
        const long long throughputQueries = (long long) (totalQueries / SECONDS_TO_RUN);
        const long long throughputUpdates = (long long) (totalUpdates / SECONDS_TO_RUN);
        const long long throughputAll = (long long) (totalAll / SECONDS_TO_RUN);
        COUTATOMIC(endl);
        COUTATOMIC("total find                    : "<<totalSearches<<endl);
        COUTATOMIC("total rq                      : "<<totalRQs<<endl);
        COUTATOMIC("total updates                 : "<<totalUpdates<<endl);
        COUTATOMIC("total queries                 : "<<totalQueries<<endl);
        COUTATOMIC("total ops                     : "<<totalAll<<endl);
        COUTATOMIC("find throughput               : "<<throughputSearches<<endl);
        COUTATOMIC("rq throughput                 : "<<throughputRQs<<endl);
        COUTATOMIC("update throughput             : "<<throughputUpdates<<endl);
        COUTATOMIC("query throughput              : "<<throughputQueries<<endl);
        COUTATOMIC("total throughput              : "<<throughputAll<<endl);
    #if defined(VCAS_STATS)
        long long totalDistinctNodes = 0;
        long long totalVersionNodesTraversed = 0;
        for(int i = 0; i < 141; i++) {
            totalDistinctNodes += distinctNodesVisited[i*PADDING];
            totalVersionNodesTraversed += versionNodesTraversed[i*PADDING];
        }
        COUTATOMIC("Distinct nodes visited       : "<<totalDistinctNodes<<endl);
        COUTATOMIC("Version nodes traversed      : "<<totalVersionNodesTraversed<<endl);
    #endif
        // COUTATOMIC("version nodes traversed       : "<<versionNodesTraversed<<endl);
        // COUTATOMIC("avg rq size       : "<<(1.0*sum_sizes/totalRQs)<<endl);
        COUTATOMIC(endl);
    }
#endif

#ifdef USE_GSTATS
    {
        const long long totalSearches = GSTATS_GET_STAT_METRICS(num_searches, TOTAL)[0].sum;
        const long long totalRQs = GSTATS_GET_STAT_METRICS(num_rq, TOTAL)[0].sum;
        const long long totalQueries = totalSearches + totalRQs;
        const long long totalUpdates = GSTATS_GET_STAT_METRICS(num_updates, TOTAL)[0].sum;

        const double SECONDS_TO_RUN = (MILLIS_TO_RUN)/1000.;
        totalAll = totalUpdates + totalQueries;
        const long long throughputSearches = (long long) (totalSearches / SECONDS_TO_RUN);
        const long long throughputRQs = (long long) (totalRQs / SECONDS_TO_RUN);
        const long long throughputQueries = (long long) (totalQueries / SECONDS_TO_RUN);
        const long long throughputUpdates = (long long) (totalUpdates / SECONDS_TO_RUN);
        const long long throughputAll = (long long) (totalAll / SECONDS_TO_RUN);
        COUTATOMIC(endl);
        COUTATOMIC("total find                    : "<<totalSearches<<endl);
        COUTATOMIC("total rq                      : "<<totalRQs<<endl);
        COUTATOMIC("total updates                 : "<<totalUpdates<<endl);
        COUTATOMIC("total queries                 : "<<totalQueries<<endl);
        COUTATOMIC("total ops                     : "<<totalAll<<endl);
        COUTATOMIC("find throughput               : "<<throughputSearches<<endl);
        COUTATOMIC("rq throughput                 : "<<throughputRQs<<endl);
        COUTATOMIC("update throughput             : "<<throughputUpdates<<endl);
        COUTATOMIC("query throughput              : "<<throughputQueries<<endl);
        COUTATOMIC("total throughput              : "<<throughputAll<<endl);
        // COUTATOMIC("version nodes traversed       : "<<versionNodesTraversed<<endl);
        // COUTATOMIC("avg rq size       : "<<(1.0*sum_sizes/totalRQs)<<endl);
    #if defined(VCAS_STATS)
        long long totalDistinctNodes = 0;
        long long totalVersionNodesTraversed = 0;
        for(int i = 0; i < 141; i++) {
            totalDistinctNodes += distinctNodesVisited[i*PADDING];
            totalVersionNodesTraversed += versionNodesTraversed[i*PADDING];
        }
        COUTATOMIC("Distinct nodes visited per rq  : "<<1.0*totalDistinctNodes/totalRQs<<endl);
        COUTATOMIC("Version nodes traversed per rq : "<<1.0*totalVersionNodesTraversed/totalRQs<<endl);
    #endif
        COUTATOMIC(endl);
    }
#endif
    
    COUTATOMIC("elapsed milliseconds          : "<<glob.elapsedMillis<<endl);
    COUTATOMIC("napping milliseconds overtime : "<<glob.elapsedMillisNapping<<endl);
    COUTATOMIC("data structure size           : "<<ds->getSizeString()<<endl);
    COUTATOMIC(endl);
    
#if defined(USE_DEBUGCOUNTERS) || defined(USE_GSTATS)
    cout<<"begin papi_print_counters..."<<endl;
    papi_print_counters(totalAll);
    cout<<"end papi_print_counters."<<endl;
#endif
    
    // free ds
    cout<<"begin delete ds..."<<endl;
    delete ds;
    cout<<"end delete ds."<<endl;
    
#ifdef USE_DEBUGCOUNTERS
    VERBOSE COUTATOMIC("main thread: garbage#=");
    VERBOSE COUTATOMIC(counters->garbage->getTotal()<<endl);
#endif
}

int main(int argc, char** argv) {
    
    // setup default args
    PREFILL = false;            // must be false, or else there's no way to specify no prefilling on the command line...
    MILLIS_TO_RUN = 1000;
    RQ_THREADS = 0;
    WORK_THREADS = 4;
    RQSIZE = 0;
    RQ = 0;
    INS = 10;
    DEL = 10;
    MAXKEY = 100000;
    
    // read command line args
    // example args: -i 25 -d 25 -k 10000 -rq 0 -rqsize 1000 -p -t 1000 -nrq 0 -nwork 8
    for (int i=1;i<argc;++i) {
        if (strcmp(argv[i], "-i") == 0) {
            INS = atof(argv[++i]);
        } else if (strcmp(argv[i], "-d") == 0) {
            DEL = atof(argv[++i]);
        } else if (strcmp(argv[i], "-rq") == 0) {
            RQ = atof(argv[++i]);
        } else if (strcmp(argv[i], "-rqsize") == 0) {
            RQSIZE = atoi(argv[++i]);
        } else if (strcmp(argv[i], "-k") == 0) {
            MAXKEY = atoi(argv[++i]);
        } else if (strcmp(argv[i], "-nrq") == 0) {
            RQ_THREADS = atoi(argv[++i]);
        } else if (strcmp(argv[i], "-nwork") == 0) {
            WORK_THREADS = atoi(argv[++i]);
        } else if (strcmp(argv[i], "-t") == 0) {
            MILLIS_TO_RUN = atoi(argv[++i]);
        } else if (strcmp(argv[i], "-p") == 0) {
            PREFILL = true;
        } else if (strcmp(argv[i], "-bind") == 0) { // e.g., "-bind 1,2,3,8-11,4-7,0"
            binding_parseCustom(argv[++i]); // e.g., "1,2,3,8-11,4-7,0"
            cout<<"parsed custom binding: "<<argv[i]<<endl;
        } else {
            cout<<"bad argument "<<argv[i]<<endl;
            exit(1);
        }
    }
    TOTAL_THREADS = WORK_THREADS + RQ_THREADS;
    
    // print used args
    PRINTS(FIND_FUNC);
    PRINTS(INSERT_FUNC);
    PRINTS(ERASE_FUNC);
    PRINTS(RQ_FUNC);
    PRINTS(RECLAIM);
    PRINTS(ALLOC);
    PRINTS(POOL);
    PRINTI(PREFILL);
    PRINTI(MILLIS_TO_RUN);
    PRINTI(INS);
    PRINTI(DEL);
    PRINTI(RQ);
    PRINTI(RQSIZE);
    PRINTI(MAXKEY);
    PRINTI(WORK_THREADS);
    PRINTI(RQ_THREADS);
#ifdef WIDTH_SEQ
    PRINTI(WIDTH_SEQ);
#endif
    
    // print object sizes, to help debugging/sanity checking memory layouts
    PRINT_OBJ_SIZES;
    
    // setup thread pinning/binding
    binding_configurePolicy(TOTAL_THREADS, LOGICAL_PROCESSORS);
    
    // print actual thread pinning/binding layout
    cout<<"ACTUAL_THREAD_BINDINGS=";
    for (int i=0;i<TOTAL_THREADS;++i) {
        cout<<(i?",":"")<<binding_getActualBinding(i, LOGICAL_PROCESSORS);
    }
    cout<<endl;
    if (!binding_isInjectiveMapping(TOTAL_THREADS, LOGICAL_PROCESSORS)) {
        cout<<"ERROR: thread binding maps more than one thread to a single logical processor"<<endl;
        exit(-1);
    }

    // setup per-thread statistics
    GSTATS_CREATE_ALL;
    
#ifdef USE_DEBUGCOUNTERS
    // per-thread stats for prefilling and key-checksum validation of the data structure
    glob.keysum = new debugCounter(MAX_TID_POW2);
    glob.prefillSize = new debugCounter(MAX_TID_POW2);
#endif
    
    trial();
    printOutput();
    
    binding_deinit(LOGICAL_PROCESSORS);
    cout<<"garbage="<<glob.__garbage<<endl; // to prevent certain steps from being optimized out
#ifdef USE_DEBUGCOUNTERS
    delete glob.keysum;
    delete glob.prefillSize;
#endif
    GSTATS_DESTROY;
    return 0;
}
