/* 
 * File:   papi_util.h
 * Author: Maya Arbel-Raviv
 *
 * Created on June 29, 2016, 3:18 PM
 */

#ifndef PAPI_UTIL_H
#define PAPI_UTIL_H
#include <papi.h>
#include <string>

int all_cpu_counters[] = {
    PAPI_L1_DCM,
    PAPI_L2_TCM,
    PAPI_L3_TCM,
    PAPI_RES_STL,
    PAPI_TOT_CYC,
    PAPI_TOT_INS //,
};
string all_cpu_counters_strings[] = {
    "PAPI_L1_DCM",
    "PAPI_L2_TCM",
    "PAPI_L3_TCM",
    "PAPI_RES_STL",
    "PAPI_TOT_CYC",
    "PAPI_TOT_ISR" //,
//    "PAPI_TLB_DM",
};
const int nall_cpu_counters = sizeof(all_cpu_counters) / sizeof(all_cpu_counters[0]);

void papi_init_program(const int numProcesses);
void papi_deinit_program();
void papi_create_eventset(int id);
void papi_start_counters(int id);
void papi_stop_counters(int id);
void papi_print_counters(long long all_ops);

#endif /* PAPI_UTIL_H */

