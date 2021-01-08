/* 
 * File:   cohort_locks.h
 * Author: trbot
 *
 * This code is based (heavily) on the Cohort Locking paper by Dice et al.
 * 
 * Currently, I think this implementation is broken, because I get what appear
 * to be infinite loops on tapuz.
 * 
 * Created on August 1, 2017, 6:25 PM
 */

#ifndef COHORT_LOCKS_H
#define COHORT_LOCKS_H

#include "plaf.h"
#include <assert.h>
#include <utmpx.h>
#include <numa.h>

#define MAX_LOCAL_HANDOFFS_BEFORE_RELEASE_TOP 64
#define MAX_ITERATIONS_READERS_WAIT_UNTIL_BLOCKING_WRITERS 1000

/**
 * TODO: determine if we should assume numa nodes don't change,
 *       and have threads memoize their own numa node.
 *      (and determine if not doing so is a problem for these locks.
 *       i seem to recall from the paper that it is not.)
 */

#ifndef SOFTWARE_BARRIER
#define SOFTWARE_BARRIER asm volatile("": : :"memory")
#endif

int getNumberOfNumaNodes() {
    return numa_max_node();
}
int getCurrentProcessor() {
    return sched_getcpu();
}
int getCurrentNumaNode() {
    return numa_node_of_cpu(getCurrentProcessor());
}

// padded to avoid false sharing
class TicketLock {
public:
    volatile long request;
    volatile long grant; // if equal to OUR request, we hold the ticket lock
    volatile bool topGrant; // if true, we had the "top" cohort lock granted to us
    volatile int batchCount; // tally of consecutive local handoffs (counts down)
    volatile char padding[PREFETCH_SIZE_BYTES];
    
    TicketLock() {
        request = 0;
        grant = 0;
        topGrant = false;
        batchCount = MAX_LOCAL_HANDOFFS_BEFORE_RELEASE_TOP;
    }
};

// padded to avoid false sharing
class PartitionedTicketLock {
public:
    volatile char padding0[PREFETCH_SIZE_BYTES];
    volatile long request;
    // note: grants[...] is PADDED to avoid false sharing.
    // instead of grants[i], use grants[i*PREFETCH_SIZE_WORDS].
    volatile long * grants; // for good perf., should be >= # numa nodes
    volatile long ownerTicket;
    volatile char padding1[PREFETCH_SIZE_BYTES];
    
    PartitionedTicketLock() {
        int numNumaNodes = getNumberOfNumaNodes();
        grants = new volatile long[numNumaNodes*PREFETCH_SIZE_WORDS];
        request = 0;
        for (int i=0;i<numNumaNodes;++i) {
            grants[i*PREFETCH_SIZE_WORDS] = 0;
        }
        ownerTicket = 0;
    }
    ~PartitionedTicketLock() {
        delete[] grants;
    }
};

/**
 * Cohort lock that uses a partitioned ticket lock as its top-level lock,
 * and ticket locks as its per-numa-node local locks.
 * (padded to avoid false sharing)
 */

class PTL_TKT_Lock {
private:
    // leading padding embedded in below
    PartitionedTicketLock topLock;
    // terminal padding embedded in above
    TicketLock * volatile topHome;
    volatile char padding1[PREFETCH_SIZE_BYTES];
    TicketLock * localLock;
    volatile char padding2[PREFETCH_SIZE_BYTES];
    
public:
    const int NUM_NUMA_NODES;

    PTL_TKT_Lock() : NUM_NUMA_NODES(getNumberOfNumaNodes()) {
        topHome = NULL;
        localLock = new TicketLock[NUM_NUMA_NODES];
    }
    ~PTL_TKT_Lock() {
        delete[] localLock;
    }
    
    void acquire() {
        TicketLock * l = &localLock[NUM_NUMA_NODES];
        
        // acquire local ticket lock
        long t = __sync_fetch_and_add(&l->request, 1);
//        cout<<"GOT HERE2.a"<<endl;
        while (l->grant != t) {} // spin
//        cout<<"GOT HERE2.b"<<endl;
        
        // we own the local lock
        // acquire top-level lock
        
        // check if another thread in our cohort granted it to us
        if (l->topGrant) {
            assert(topHome == l);
            l->topGrant = false;
//            cout<<"GOT HERE2.c"<<endl;
            return;
        }
        
        // physically acquire top-level lock
//        cout<<"GOT HERE2.d"<<endl;
        t = __sync_fetch_and_add(&topLock.request, 1);
        while (topLock.grants[(t % NUM_NUMA_NODES)*PREFETCH_SIZE_WORDS] != t) {} // spin
//        cout<<"GOT HERE2.e"<<endl;
        
        topLock.ownerTicket = t;
        topHome = l;

        SOFTWARE_BARRIER;
    }
    
    void release() {
        SOFTWARE_BARRIER;

        TicketLock * l = topHome;
        long g = l->grant + 1;
        if (l->request != g) { // check "Alone?" predicate
            // cohort detection: local lock has waiters.
            //  the existence of a local cohort is a stable property
            //  as long as the local lock remains held.
            if (--l->batchCount >= 0) {
                // forward ownership to next thread in cohort
                l->topGrant = true;
                l->grant = g;
                return;
            }
            l->batchCount = MAX_LOCAL_HANDOFFS_BEFORE_RELEASE_TOP;
        }
        
        // release both locks (safe in either order)
        long t = topLock.ownerTicket + 1; // release top-lock
        topLock.grants[(t % NUM_NUMA_NODES)*PREFETCH_SIZE_WORDS] = t;
        l->grant = g; // release local lock
        
        SOFTWARE_BARRIER;
    }
    
    bool isLocked() {
        long t = topLock.request;
        return t == topLock.grants[(t % NUM_NUMA_NODES)*PREFETCH_SIZE_WORDS] + 1;
    }
};

/**
 * Distributed ingress/egress reader indicators
 * (padded to avoid false sharing)
 */

class ReadIndicator {
private:
    volatile long ingress;
    volatile long egress;
    volatile char padding[PREFETCH_SIZE_BYTES];
    
public:
    ReadIndicator() {
        ingress = 0;
        egress = 0;
    }
    
    inline void arrive() {
        __sync_fetch_and_add(&ingress, 1);
    }
    inline void depart() {
        __sync_fetch_and_add(&egress, 1);
    }
    inline bool isEmpty() {
        // note: egress must be read before ingress for this algorithm
        //       (per the last paragraph of section 3.6 of the paper)
        long _egress = egress;
        SOFTWARE_BARRIER;
        long _ingress = ingress;
        return _ingress == _egress;
    }
};

/**
 * Reader-writer lock that prioritizes writers over readers,
 *  and uses PTL_TKT_Lock as its internal cohort lock,
 *  and distributed reader indicators
 *  (implemented with 64-bit ingress/egress counters).
 * (padded to avoid false sharing)
 */

static __thread int threadNumaNode = -1;

class C_RW_WP_Lock {
private:
    // leading padding embedded in the below
    PTL_TKT_Lock cohortLock;
    // terminal padding embedded in the above
    ReadIndicator * readIndicator;
    // terminal padding embedded in the above
    int wBarrier;
    volatile char padding0[PREFETCH_SIZE_BYTES];
    
    inline bool readIndicatorsAreEmpty() {
        for (int i=0;i<cohortLock.NUM_NUMA_NODES;++i) {
            if (!readIndicator[i].isEmpty()) return false;
        }
        return true;
    }
    
public:
    
    C_RW_WP_Lock() {
        readIndicator = new ReadIndicator[cohortLock.NUM_NUMA_NODES];
        wBarrier = 0;
    }
    ~C_RW_WP_Lock() {
        delete[] readIndicator;
    }
    
    inline bool isWriteLocked() {
        return cohortLock.isLocked();
    }
    
    inline void acquireReader() {
        threadNumaNode = getCurrentNumaNode();
        bool bRaised = false;
    start:
        readIndicator[threadNumaNode].arrive(); // implies fence (on x86/64)
        if (cohortLock.isLocked()) {
            readIndicator[threadNumaNode].depart();
            int patience = MAX_ITERATIONS_READERS_WAIT_UNTIL_BLOCKING_WRITERS;
            while (cohortLock.isLocked()) {
                if (--patience <= 0 && !bRaised) {
                    __sync_fetch_and_add(&wBarrier, 1);
                    bRaised = true;
                }
            }
            goto start;
        }
        if (bRaised) {
            __sync_fetch_and_add(&wBarrier, -1);
        }
        SOFTWARE_BARRIER;
    }
    
    inline void releaseReader() {
        readIndicator[threadNumaNode].depart(); // implies fence (on x86/64)
    }
    
    void acquireWriter() {
//        cout<<"GOT HERE1"<<endl;
        while (wBarrier) {}
//        cout<<"GOT HERE2"<<endl;
        cohortLock.acquire(); // implies fence (on x86/64)
//        cout<<"GOT HERE3"<<endl;
        while (!readIndicatorsAreEmpty()) {}
//        cout<<"GOT HERE4"<<endl;
        SOFTWARE_BARRIER;
    }
    
    inline void releaseWriter() {
        cohortLock.release(); // implies software_barrier
    }
};

#endif /* COHORT_LOCKS_H */

