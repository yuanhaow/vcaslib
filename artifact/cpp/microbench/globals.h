/* 
 * File:   globals.h
 * Author: trbot
 *
 * Created on March 9, 2015, 1:32 PM
 */

#ifndef GLOBALS_H
#define	GLOBALS_H

#include <string>
using namespace std;

#include "plaf.h"

#ifndef SOFTWARE_BARRIER
#define SOFTWARE_BARRIER asm volatile("": : :"memory")
#endif

#include "debugprinting.h"

double INS;
double DEL;
double RQ;
int RQSIZE;
int MAXKEY;
int MILLIS_TO_RUN;
bool PREFILL;
int WORK_THREADS;
int RQ_THREADS;
int TOTAL_THREADS;

/**
 * Configure global statistics using stats_global.h and stats.h
 */

#define __HANDLE_STATS(handle_stat) \
    handle_stat(LONG_LONG, node_allocated_addresses, 100, { \
            stat_output_item(PRINT_RAW, FIRST, BY_INDEX) \
    }) \
    handle_stat(LONG_LONG, descriptor_allocated_addresses, 100, { \
            stat_output_item(PRINT_RAW, FIRST, BY_INDEX) \
    }) \
    handle_stat(LONG_LONG, extra_type1_allocated_addresses, 100, { \
            stat_output_item(PRINT_RAW, FIRST, BY_INDEX) \
    }) \
    handle_stat(LONG_LONG, extra_type2_allocated_addresses, 100, { \
            stat_output_item(PRINT_RAW, FIRST, BY_INDEX) \
    }) \
    handle_stat(LONG_LONG, extra_type3_allocated_addresses, 100, { \
            stat_output_item(PRINT_RAW, FIRST, BY_INDEX) \
    }) \
    handle_stat(LONG_LONG, extra_type4_allocated_addresses, 100, { \
            stat_output_item(PRINT_RAW, FIRST, BY_INDEX) \
    }) \
    handle_stat(LONG_LONG, num_updates, 1, { \
            stat_output_item(PRINT_RAW, SUM, TOTAL) \
    }) \
    handle_stat(LONG_LONG, num_searches, 1, { \
            stat_output_item(PRINT_RAW, SUM, TOTAL) \
    }) \
    handle_stat(LONG_LONG, num_rq, 1, { \
            stat_output_item(PRINT_RAW, SUM, TOTAL) \
    }) \
    handle_stat(LONG_LONG, num_operations, 1, { \
            stat_output_item(PRINT_RAW, SUM, BY_THREAD) \
          C stat_output_item(PRINT_RAW, AVERAGE, TOTAL) \
          C stat_output_item(PRINT_RAW, SUM, TOTAL) \
    }) \
    handle_stat(LONG_LONG, visited_in_bags, 10000, { \
            stat_output_item(PRINT_HISTOGRAM_LOG, NONE, FULL_DATA) \
          C stat_output_item(PRINT_RAW, SUM, TOTAL) \
          C stat_output_item(PRINT_RAW, AVERAGE, TOTAL) \
          C stat_output_item(PRINT_RAW, STDEV, TOTAL) \
    }) \
    handle_stat(LONG_LONG, skipped_in_bags, 10000, { \
            stat_output_item(PRINT_HISTOGRAM_LOG, NONE, FULL_DATA) \
          C stat_output_item(PRINT_RAW, SUM, TOTAL) \
          C stat_output_item(PRINT_RAW, AVERAGE, TOTAL) \
          C stat_output_item(PRINT_RAW, STDEV, TOTAL) \
    }) \
    handle_stat(LONG_LONG, latency_rqs, 10000, { \
            stat_output_item(PRINT_HISTOGRAM_LOG, NONE, FULL_DATA) \
          /*C stat_output_item(PRINT_RAW, NONE, FULL_DATA)*/ \
          C stat_output_item(PRINT_RAW, SUM, TOTAL) \
          C stat_output_item(PRINT_RAW, AVERAGE, TOTAL) \
          C stat_output_item(PRINT_RAW, STDEV, TOTAL) \
          C stat_output_item(PRINT_RAW, MIN, TOTAL) \
          C stat_output_item(PRINT_RAW, MAX, TOTAL) \
    }) \
    handle_stat(LONG_LONG, latency_updates, 10000, { \
            stat_output_item(PRINT_HISTOGRAM_LOG, NONE, FULL_DATA) \
          /*C stat_output_item(PRINT_RAW, NONE, FULL_DATA)*/ \
          C stat_output_item(PRINT_RAW, SUM, TOTAL) \
          C stat_output_item(PRINT_RAW, AVERAGE, TOTAL) \
          C stat_output_item(PRINT_RAW, STDEV, TOTAL) \
          C stat_output_item(PRINT_RAW, MIN, TOTAL) \
          C stat_output_item(PRINT_RAW, MAX, TOTAL) \
    }) \
    handle_stat(LONG_LONG, latency_searches, 10000, { \
            stat_output_item(PRINT_HISTOGRAM_LOG, NONE, FULL_DATA) \
          /*C stat_output_item(PRINT_RAW, NONE, FULL_DATA)*/ \
          C stat_output_item(PRINT_RAW, SUM, TOTAL) \
          C stat_output_item(PRINT_RAW, AVERAGE, TOTAL) \
          C stat_output_item(PRINT_RAW, STDEV, TOTAL) \
          C stat_output_item(PRINT_RAW, MIN, TOTAL) \
          C stat_output_item(PRINT_RAW, MAX, TOTAL) \
    }) \
    handle_stat(LONG_LONG, skiplist_inserted_on_level, 30, { \
            /*stat_output_item(PRINT_RAW, NONE, FULL_DATA)*/ \
          /*C stat_output_item(PRINT_RAW, SUM, BY_INDEX)*/ \
    }) \
    handle_stat(LONG_LONG, key_checksum, 1, {}) \
    handle_stat(LONG_LONG, prefill_size, 1, {}) \
    handle_stat(LONG_LONG, timer_latency, 1, {})

#include "stats_global.h"
GSTATS_DECLARE_STATS_OBJECT(MAX_TID_POW2);
GSTATS_DECLARE_ALL_STAT_IDS;

#endif	/* GLOBALS_H */

