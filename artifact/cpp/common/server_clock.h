/* 
 * File:   server_clock.h
 * Author: trbot
 *
 * Created on August 2, 2017, 6:37 PM
 */

#ifndef SERVER_CLOCK_H
#define SERVER_CLOCK_H

#ifndef CPU_FREQ_GHZ
#error "Must define CPU_FREQ_GHZ for server_clock.h"
#endif

inline uint64_t get_server_clock() {
#if defined(__i386__)
    uint64_t ret;
    __asm__ __volatile__("rdtsc" : "=A" (ret));
#elif defined(__x86_64__)
    unsigned hi, lo;
    __asm__ __volatile__ ("rdtsc" : "=a"(lo), "=d"(hi));
    uint64_t ret = ( (uint64_t)lo)|( ((uint64_t)hi)<<32 );
        ret = (uint64_t) ((double)ret / CPU_FREQ_GHZ);
#else 
        timespec * tp = new timespec;
    clock_gettime(CLOCK_REALTIME, tp);
    uint64_t ret = tp->tv_sec * 1000000000 + tp->tv_nsec;
#endif
    return ret;
}

//class ClockSplitter {
//private:
//    uint64_t time;
//    
//    inline uint64_t get_server_clock() {
//#if defined(__i386__)
//        uint64_t ret;
//        __asm__ __volatile__("rdtsc" : "=A" (ret));
//#elif defined(__x86_64__)
//        unsigned hi, lo;
//        __asm__ __volatile__ ("rdtsc" : "=a"(lo), "=d"(hi));
//        uint64_t ret = ( (uint64_t)lo)|( ((uint64_t)hi)<<32 );
//            ret = (uint64_t) ((double)ret / CPU_FREQ_GHZ);
//#else 
//            timespec * tp = new timespec;
//        clock_gettime(CLOCK_REALTIME, tp);
//        uint64_t ret = tp->tv_sec * 1000000000 + tp->tv_nsec;
//#endif
//        return ret;
//    }
//    
//public:
//    ClockSplitter() {}
//    void reset() {
//        time = get_server_clock();
//    }
//    uint64_t split() {
//        uint64_t old = time;
//        time = get_server_clock();
//        return time - old;
//    }
//};

#endif /* SERVER_CLOCK_H */

