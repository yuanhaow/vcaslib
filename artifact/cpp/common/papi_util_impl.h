/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
#ifndef PAPI_UTIL_IMPL_H
#define PAPI_UTIL_IMPL_H
#include "papi_util.h"
#include "plaf.h"
#include <iostream>

using namespace std;

int event_sets[MAX_TID_POW2] = {0,};
long long counter_values[nall_cpu_counters];

char *cpu_counter(int c) {
#ifdef USE_PAPI
    char counter[PAPI_MAX_STR_LEN];

    PAPI_event_code_to_name(c, counter);
    return strdup(counter);
#endif
    return NULL;
}

void papi_init_program(const int numProcesses){
#ifdef USE_PAPI
    if (PAPI_library_init(PAPI_VER_CURRENT) != PAPI_VER_CURRENT) {
        fprintf(stderr, "Error: Failed to init PAPI\n");
        exit(2);
    }
 
    if (PAPI_thread_init(pthread_self) != PAPI_OK) {
       fprintf(stderr, "PAPI_ERROR: failed papi_thread_init()\n");
       exit(2);
    }
    for (int i=0;i<numProcesses;++i) event_sets[i]= PAPI_NULL;
    for (int i=0;i<nall_cpu_counters;++i) counter_values[i]= 0;
#endif
}

void papi_deinit_program() {
#ifdef USE_PAPI
    PAPI_shutdown();
#endif
}

void papi_create_eventset(int id){
#ifdef USE_PAPI
    int * event_set = &event_sets[id];
    int result; 
    if ((result = PAPI_create_eventset(event_set)) != PAPI_OK) {
       fprintf(stderr, "PAPI_ERROR: thread %d cannot create event set: %s\n", id, PAPI_strerror(result));
       exit(2);
    }
    for (int i = 0; i < nall_cpu_counters; i++) {
        int c = all_cpu_counters[i];
        if (PAPI_query_event(c) != PAPI_OK) {
            continue;
        }
        if ((result = PAPI_add_event(*event_set, c)) != PAPI_OK) {
            if (result != PAPI_ECNFLCT) {
                fprintf(stderr, "PAPI ERROR: thread %d unable to add event %s: %s\n", id, cpu_counter(c), PAPI_strerror(result));
                exit(2);
            }
            /* Not enough hardware resources, disable this counter and move on. */
            all_cpu_counters[i] = PAPI_END + 1;
        }
    }
#endif
}

void papi_start_counters(int id){
#ifdef USE_PAPI
    int * event_set = &event_sets[id];
    int result; 
    if ((result = PAPI_start(*event_set)) != PAPI_OK) {
       fprintf(stderr, "PAPI ERROR: thread %d unable to start counters: %s\n", id, PAPI_strerror(result));
       exit(2);
    }
#endif
}

void papi_stop_counters(int id){
#ifdef USE_PAPI
    int * event_set = &event_sets[id];
    long long values[nall_cpu_counters];
    for (int i=0;i<nall_cpu_counters; i++) values[i]=0;
    
    int r;

    /* Get cycles from hardware to account for time stolen by co-scheduled threads. */
    if ((r = PAPI_stop(*event_set, values)) != PAPI_OK) {
       fprintf(stderr, "PAPI ERROR: thread %d unable to stop counters: %s\n", id, PAPI_strerror(r));
       exit(2);
    }
    int j= 0; 
    for (int i = 0; i < nall_cpu_counters; i++) {
        int c = all_cpu_counters[i];
        if (PAPI_query_event(c) != PAPI_OK)
            continue;
        __sync_fetch_and_add(&counter_values[j], values[j]);
        j++;
    }
    if ((r = PAPI_cleanup_eventset(*event_set)) != PAPI_OK) {
       fprintf(stderr, "PAPI ERROR: thread %d unable to cleanup event set: %s\n", id, PAPI_strerror(r));
       exit(2);
    }
    if ((r = PAPI_destroy_eventset(event_set)) != PAPI_OK) {
       fprintf(stderr, "PAPI ERROR: thread %d unable to destroy event set: %s\n", id, PAPI_strerror(r));
       exit(2);
    }
    if ((r = PAPI_unregister_thread()) != PAPI_OK) {
       fprintf(stderr, "PAPI ERROR: thread %d unable to unregister thread: %s\n", id, PAPI_strerror(r));
       exit(2);
    }
#endif
}
void papi_print_counters(long long num_operations){
#ifdef USE_PAPI
    int i, j;
    for (i = j = 0; i < nall_cpu_counters; i++) {
        int c = all_cpu_counters[i];
        if (PAPI_query_event(c) != PAPI_OK) {
            cout<<all_cpu_counters_strings[i]<<"=-1"<<endl;
            continue;
        }
        cout<<all_cpu_counters_strings[i]<<"="<<((double)counter_values[j]/num_operations)<<endl;
        //printf("%s=%.3f\n", cpu_counter(c), (double)counter_values[j]/num_operations);
        j++;
    }
#endif
}
#endif