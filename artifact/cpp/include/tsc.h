#ifndef _TSC_H_
#define _TSC_H_

#include <stdint.h>

static inline uint64_t read_tsc(void)
{
    unsigned upper, lower;
    asm volatile ("rdtsc" : "=a"(lower), "=d"(upper)::"memory");
    return ((uint64_t)lower)|(((uint64_t)upper)<<32 );
}

static inline void wait_cycles(uint64_t stop)
{
        while (read_tsc() < stop)
            continue;
}

#endif
