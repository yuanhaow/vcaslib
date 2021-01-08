/* 
 * File:   globals_extern.h
 * Author: trbot
 *
 * Created on March 9, 2015, 1:32 PM
 */

#ifndef GLOBALS_EXTERN_H
#define	GLOBALS_EXTERN_H

// enable USE_TRACE if you want low level functionality tracing using cout
//#define USE_TRACE

#include <string>
using namespace std;

#include "debugprinting.h"
#include "plaf.h"
#include <atomic>

#ifndef __rtm_force_inline
#define __rtm_force_inline __attribute__((always_inline)) inline
#endif

#ifndef SOFTWARE_BARRIER
#define SOFTWARE_BARRIER asm volatile("": : :"memory")
#endif

#ifndef DEBUG
#define DEBUG if(0)
#define DEBUG1 if(0)
#define DEBUG2 if(0)
#define DEBUG3 if(0) /* rarely used */
#endif

#ifdef __unix__
#define POSIX_SYSTEM
#else
#error NOT UNIX SYSTEM
#endif

#ifdef USE_TRACE
extern std::atomic_bool ___trace;
#define TRACE_TOGGLE {bool ___t = ___trace; ___trace = !___t;}
#define TRACE_ON {___trace = true;}
#define TRACE DEBUG if(___trace)
extern std::atomic_bool ___validateops;
#define VALIDATEOPS_ON {___validateops = true;}
#define VALIDATEOPS DEBUG if(___validateops)
#endif

extern double INS;
extern double DEL;
extern double RQ;
extern int RQSIZE;
extern int MAXKEY;
extern int MILLIS_TO_RUN;
extern bool PREFILL;
extern int WORK_THREADS;
extern int RQ_THREADS;
extern int TOTAL_THREADS;

#define NUMBER_OF_PATHS 1

/**
 * Configure global statistics using stats_global.h and stats.h
 */

#include "stats_global.h"

/**
 * Setup timing code
 */

#include "server_clock.h"

/**
 * Configure record manager: reclaimer, allocator and pool
 */

#define RECLAIM reclaimer_debra<test_type>
#define ALLOC allocator_new_segregated<test_type>
#define POOL pool_none<test_type>

#endif	/* GLOBALS_EXTERN_H */

