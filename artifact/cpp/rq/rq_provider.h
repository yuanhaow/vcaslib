/* 
 * File:   rq_provider.h
 * Author: trbot
 *
 * Created on May 16, 2017, 5:14 PM
 */

#ifndef RQ_PROVIDER_H
#define RQ_PROVIDER_H

#ifndef MAX_NODES_INSERTED_OR_DELETED_ATOMICALLY
    #define MAX_NODES_INSERTED_OR_DELETED_ATOMICALLY (32)
#endif

#if defined RQ_LOCKFREE
#include "rq_lockfree.h"
#elif defined RQ_RWLOCK
#include "rq_rwlock.h"
#elif defined RQ_HTM_RWLOCK
#include "rq_htm_rwlock.h"
#elif defined RQ_UNSAFE
#include "rq_unsafe.h"
#elif defined RQ_VCAS
#include "rq_vcas.h"
#elif defined RQ_SNAPCOLLECTOR
#include "rq_snapcollector.h"
#else
#error NO RQ PROVIDER DEFINED
#endif

#endif /* RQ_PROVIDER_H */

