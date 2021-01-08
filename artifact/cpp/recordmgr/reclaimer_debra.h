/**
 * Preliminary C++ implementation of binary search tree using LLX/SCX and DEBRA(+).
 * 
 * Copyright (C) 2015 Trevor Brown
 * 
 */

#ifndef RECLAIM_EPOCH_H
#define	RECLAIM_EPOCH_H

#include <cassert>
#include <iostream>
#include <sstream>
#include <limits.h>
#include "blockbag.h"
#include "plaf.h"
#include "allocator_interface.h"
#include "reclaimer_interface.h"
using namespace std;



template <typename T = void, class Pool = pool_interface<T> >
class reclaimer_debra : public reclaimer_interface<T, Pool> {
protected:
#define EPOCH_INCREMENT 2
#define BITS_EPOCH(ann) ((ann)&~(EPOCH_INCREMENT-1))
#define QUIESCENT(ann) ((ann)&1)
#define GET_WITH_QUIESCENT(ann) ((ann)|1)

#ifdef RAPID_RECLAMATION
#define MIN_OPS_BEFORE_READ 1
//#define MIN_OPS_BEFORE_CAS_EPOCH 1
#else
#define MIN_OPS_BEFORE_READ 20
//#define MIN_OPS_BEFORE_CAS_EPOCH 100
#endif
    
#define NUMBER_OF_EPOCH_BAGS 9
#define NUMBER_OF_ALWAYS_EMPTY_EPOCH_BAGS 3
    
    class ThreadData {
    private:
        volatile char padding0[PREFETCH_SIZE_BYTES];
    public:
        atomic_long announcedEpoch;
        long localvar_announcedEpoch; // copy of the above, but without the volatile tag, to try to make the read in enterQstate more efficient
    private:
        volatile char padding1[PREFETCH_SIZE_BYTES];
    public:
        blockbag<T> * epochbags[NUMBER_OF_EPOCH_BAGS];
        // note: oldest bag is number (index+1)%NUMBER_OF_EPOCH_BAGS
        int index; // index of currentBag in epochbags for this process
    private:
        volatile char padding2[PREFETCH_SIZE_BYTES];
    public:
        blockbag<T> * currentBag;  // pointer to current epoch bag for this process
        int checked;               // how far we've come in checking the announced epochs of other threads
        int opsSinceRead;
        ThreadData() {}
    private:
        volatile char padding3[PREFETCH_SIZE_BYTES];
    };
    
    volatile char padding0[PREFETCH_SIZE_BYTES];
    ThreadData threadData[MAX_TID_POW2];
    volatile char padding1[PREFETCH_SIZE_BYTES];
    
    // for epoch based reclamation
    volatile long epoch;
    volatile char padding2[PREFETCH_SIZE_BYTES];
    //long *index;                        
    
public:
    template<typename _Tp1>
    struct rebind {
        typedef reclaimer_debra<_Tp1, Pool> other;
    };
    template<typename _Tp1, typename _Tp2>
    struct rebind2 {
        typedef reclaimer_debra<_Tp1, _Tp2> other;
    };
    
//    inline int getOldestBlockbagIndexOffset(const int tid) {
//        long long min_val = LLONG_MAX;
//        int min_i = -1;
//        for (int i=0;i<NUMBER_OF_EPOCH_BAGS;++i) {
//            long long reclaimCount = epochbags[tid*NUMBER_OF_EPOCH_BAGS+i]->getReclaimCount();
//            if (reclaimCount % 1) { // bag's contents are currently being freed
//                return i;
//            }
//            if (reclaimCount < min_val) {
//                min_val = reclaimCount;
//                min_i = i;
//            }
//        }
//        return min_i;
//    }
//    
//    inline set_of_bags<T> getBlockbags() { // blockbag_iterator<T> ** const output) {
////        int cnt=0;
////        for (int tid=0;tid<NUM_PROCESSES;++tid) {
////            for (int j=0;j<NUMBER_OF_EPOCH_BAGS;++j) {
////                output[cnt++] = epochbags[NUMBER_OF_EPOCH_BAGS*tid+j];
////            }
////        }
////        return cnt;
//        return {epochbags, this->NUM_PROCESSES*NUMBER_OF_EPOCH_BAGS};
//    }
//    
//    inline void getOldestTwoBlockbags(const int tid, blockbag<T> ** oldest, blockbag<T> ** secondOldest) {
//        long long min_val = LLONG_MAX;
//        int min_i = -1;
//        for (int i=0;i<NUMBER_OF_EPOCH_BAGS;++i) {
//            long long reclaimCount = epochbags[tid*NUMBER_OF_EPOCH_BAGS+i]->getReclaimCount();
//            if (reclaimCount % 1) { // bag's contents are currently being freed
//                min_i = i;
//                break;
//            }
//            if (reclaimCount < min_val) {
//                min_val = reclaimCount;
//                min_i = i;
//            }
//        }
//        if (min_i == -1) {
//            *oldest = *secondOldest = NULL;
//        } else {
//            *oldest = epochbags[tid*NUMBER_OF_EPOCH_BAGS + min_i];
//            *secondOldest = epochbags[tid*NUMBER_OF_EPOCH_BAGS + ((min_i+1)%NUMBER_OF_EPOCH_BAGS)];
//        }
//    }
    
    inline void getSafeBlockbags(const int tid, blockbag<T> ** bags) {
        SOFTWARE_BARRIER;
        int ix = threadData[tid].index;
        bags[0] = threadData[tid].epochbags[ix];
        bags[1] = threadData[tid].epochbags[(ix+NUMBER_OF_EPOCH_BAGS-1)%NUMBER_OF_EPOCH_BAGS];
        bags[2] = threadData[tid].epochbags[(ix+NUMBER_OF_EPOCH_BAGS-2)%NUMBER_OF_EPOCH_BAGS];
        bags[3] = NULL;
        SOFTWARE_BARRIER;
        
//        SOFTWARE_BARRIER;
//        // find first dangerous blockbag
//        long long min_val = LLONG_MAX;
//        int min_i = -1;
//        for (int i=0;i<NUMBER_OF_EPOCH_BAGS;++i) {
//            long long reclaimCount = epochbags[tid*NUMBER_OF_EPOCH_BAGS+i]->getReclaimCount();
//            if (reclaimCount % 1) { // bag's contents are currently being freed
//                min_i = i;
//                break;
//            }
//            if (reclaimCount < min_val) {
//                min_val = reclaimCount;
//                min_i = i;
//            }
//        }
//        assert(min_i != -1);
//        min_i = (min_i + NUMBER_OF_ALWAYS_EMPTY_EPOCH_BAGS) % NUMBER_OF_EPOCH_BAGS;
//        
//        // process might free from bag at offset min_i, or the next one.
//        // the others are safe.
//        int i;
//        for (i=0;i<NUMBER_OF_EPOCH_BAGS-NUMBER_OF_UNSAFE_EPOCH_BAGS;++i) {
//            bags[i] = epochbags[tid*NUMBER_OF_EPOCH_BAGS + ((min_i + NUMBER_OF_UNSAFE_EPOCH_BAGS + i)%NUMBER_OF_EPOCH_BAGS)];
//        }
//        bags[i] = NULL; // null terminated array
//        
////        bags[0] = epochbags[tid*NUMBER_OF_EPOCH_BAGS + ((min_i + NUMBER_OF_UNSAFE_EPOCH_BAGS)%NUMBER_OF_EPOCH_BAGS)];
////        bags[1] = NULL; // null terminated array
////        bags[0] = NULL;
//
//        SOFTWARE_BARRIER; 

//        SOFTWARE_BARRIER;
//        /**
//         * find first dangerous blockbag.
//         * a process may free bag index+i+NUMBER_OF_ALWAYS_EMPTY_EPOCH_BAGS,
//         * where i=1,2,...,(NUMBER_OF_EPOCH_BAGS - NUMBER_OF_ALWAYS_EMPTY_EPOCH_BAGS).
//         * the rest are safe, and
//         * MUST contain all nodes retired in this epoch or the last.
//         */
//        int ix = (index[tid*PREFETCH_SIZE_WORDS]+1+NUMBER_OF_ALWAYS_EMPTY_EPOCH_BAGS) % NUMBER_OF_EPOCH_BAGS;
//        SOFTWARE_BARRIER;
//        int i;
//        // #safebags = total - #unsafe
//        for (i=0;i<NUMBER_OF_EPOCH_BAGS-NUMBER_OF_UNSAFE_EPOCH_BAGS;++i) {
//            // find i-th safe bag
//            int ix2 = (ix+NUMBER_OF_UNSAFE_EPOCH_BAGS+i)%NUMBER_OF_EPOCH_BAGS; // UNFINISHED CODE FROM HERE DOWN
//            bags[i] = epochbags[tid*NUMBER_OF_EPOCH_BAGS + ix2];
//        }
//        bags[i] = NULL; // null terminated array
//        SOFTWARE_BARRIER;
    }
    
    long long getSizeInNodes() {
        long long sum = 0;
        for (int tid=0;tid<this->NUM_PROCESSES;++tid) {
            for (int j=0;j<NUMBER_OF_EPOCH_BAGS;++j) {
                sum += threadData[tid].epochbags[j]->computeSize();
            }
        }
        return sum;
    }
    string getSizeString() {
        stringstream ss;
        ss<<getSizeInNodes()<<" in epoch bags";
        return ss.str();
    }
    
    inline static bool quiescenceIsPerRecordType() { return false; }
    
    inline bool isQuiescent(const int tid) {
        return QUIESCENT(threadData[tid].announcedEpoch.load(memory_order_relaxed));
    }

    inline static bool isProtected(const int tid, T * const obj) {
        return true;
    }
    inline static bool isQProtected(const int tid, T * const obj) {
        return false;
    }
    inline static bool protect(const int tid, T * const obj, CallbackType notRetiredCallback, CallbackArg callbackArg, bool memoryBarrier = true) {
        return true;
    }
    inline static void unprotect(const int tid, T * const obj) {}
    inline static bool qProtect(const int tid, T * const obj, CallbackType notRetiredCallback, CallbackArg callbackArg, bool memoryBarrier = true) {
        return true;
    }
    inline static void qUnprotectAll(const int tid) {}
    inline static bool shouldHelp() { return true; }
    
    // rotate the epoch bags and reclaim any objects retired two epochs ago.
    inline void rotateEpochBags(const int tid) {
        int nextIndex = (threadData[tid].index+1) % NUMBER_OF_EPOCH_BAGS;
        blockbag<T> * const freeable = threadData[tid].epochbags[(nextIndex+NUMBER_OF_ALWAYS_EMPTY_EPOCH_BAGS) % NUMBER_OF_EPOCH_BAGS];
        this->pool->addMoveFullBlocks(tid, freeable); // moves any full blocks (may leave a non-full block behind)
        SOFTWARE_BARRIER;
        threadData[tid].index = nextIndex;
        threadData[tid].currentBag = threadData[tid].epochbags[nextIndex];
    }

    // objects reclaimed by this epoch manager.
    // returns true if the call rotated the epoch bags for thread tid
    // (and reclaimed any objects retired two epochs ago).
    // otherwise, the call returns false.
    inline bool leaveQuiescentState(const int tid, void * const * const reclaimers, const int numReclaimers, const bool readOnly = false) {
        SOFTWARE_BARRIER; // prevent any bookkeeping from being moved after this point by the compiler.
        bool result = false;

        long readEpoch = epoch;
        const long ann = threadData[tid].localvar_announcedEpoch;
        threadData[tid].localvar_announcedEpoch = readEpoch;
        threadData[tid].announcedEpoch.store(readEpoch, memory_order_relaxed); // note: this must be written, regardless of whether the announced epochs are the same, because the quiescent bit will vary
        // note: readEpoch, when written to announcedEpoch[tid],
        //       sets the state to non-quiescent and non-neutralized

        // if our announced epoch was different from the current epoch
        if (readEpoch != ann /* invariant: ann is not quiescent */) {
            // rotate the epoch bags and
            // reclaim any objects retired two epochs ago.
            threadData[tid].checked = 0;
            for (int i=0;i<numReclaimers;++i) {
                ((reclaimer_debra<T, Pool> * const) reclaimers[i])->rotateEpochBags(tid);
            }
            result = true;
        }

#ifndef DEBRA_DISABLE_READONLY_OPT
        if (!readOnly) {
#endif
            // incrementally scan the announced epochs of all threads
            if (++threadData[tid].opsSinceRead == MIN_OPS_BEFORE_READ) {
                threadData[tid].opsSinceRead = 0;
                int otherTid = threadData[tid].checked;
                long otherAnnounce = threadData[otherTid].announcedEpoch.load(memory_order_relaxed);
                if (BITS_EPOCH(otherAnnounce) == readEpoch || QUIESCENT(otherAnnounce)) {
                    const int c = ++threadData[tid].checked;
                    if (c >= this->NUM_PROCESSES /*&& c > MIN_OPS_BEFORE_CAS_EPOCH*/) {
                        __sync_bool_compare_and_swap(&epoch, readEpoch, readEpoch+EPOCH_INCREMENT);
                    }
                }
            }
#ifndef DEBRA_DISABLE_READONLY_OPT
        }
#endif
        return result;
    }
    
    inline void enterQuiescentState(const int tid) {
        threadData[tid].announcedEpoch.store(GET_WITH_QUIESCENT(threadData[tid].localvar_announcedEpoch), memory_order_relaxed);
    }
    
    // for all schemes except reference counting
    inline void retire(const int tid, T* p) {
        threadData[tid].currentBag->add(p);
        DEBUG2 this->debug->addRetired(tid, 1);
    }
    
    void debugPrintStatus(const int tid) {
        if (tid == 0) {
            cout<<"global epoch counter="<<epoch<<endl;
        }
    }

    void initThread(const int tid) {
//        threadData[tid].currentBag = threadData[tid].epochbags[0];
//        threadData[tid].opsSinceRead = 0;
//        threadData[tid].checked = 0;
//        for (int i=0;i<NUMBER_OF_EPOCH_BAGS;++i) {
//            threadData[tid].epochbags[i] = new blockbag<T>(tid, this->pool->blockpools[tid]);
//        }
    }
    
    reclaimer_debra(const int numProcesses, Pool *_pool, debugInfo * const _debug, RecoveryMgr<void *> * const _recoveryMgr = NULL)
            : reclaimer_interface<T, Pool>(numProcesses, _pool, _debug, _recoveryMgr) {
        VERBOSE cout<<"constructor reclaimer_debra helping="<<this->shouldHelp()<<endl;// scanThreshold="<<scanThreshold<<endl;
        epoch = 0;
        for (int tid=0;tid<numProcesses;++tid) {
            threadData[tid].index = 0;
            threadData[tid].localvar_announcedEpoch = GET_WITH_QUIESCENT(0);
            threadData[tid].announcedEpoch.store(GET_WITH_QUIESCENT(0), memory_order_relaxed);
            for (int i=0;i<NUMBER_OF_EPOCH_BAGS;++i) {
                threadData[tid].epochbags[i] = NULL;
            }

            threadData[tid].opsSinceRead = 0;
            threadData[tid].checked = 0;
            for (int i=0;i<NUMBER_OF_EPOCH_BAGS;++i) {
                threadData[tid].epochbags[i] = new blockbag<T>(tid, this->pool->blockpools[tid]);
            }
            threadData[tid].currentBag = threadData[tid].epochbags[0];
        }
    }
    ~reclaimer_debra() {
        VERBOSE DEBUG cout<<"destructor reclaimer_debra"<<endl;
        for (int tid=0;tid<this->NUM_PROCESSES;++tid) {
            for (int i=0;i<NUMBER_OF_EPOCH_BAGS;++i) {
                if (threadData[tid].epochbags[i]) {
                    this->pool->addMoveAll(tid, threadData[tid].epochbags[i]);
                    delete threadData[tid].epochbags[i];
                }
            }
        }
    }

};

#endif

