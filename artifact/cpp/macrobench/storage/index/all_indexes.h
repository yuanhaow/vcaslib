/* 
 * File:   all_indexes.h
 * Author: trbot
 *
 * Created on May 28, 2017, 4:33 PM
 */

#ifndef ALL_INDEXES_H
#define ALL_INDEXES_H

#include "config.h"

#if 0 // nothing (convenience #if to make ALL below use #elif)
#elif (INDEX_STRUCT == IDX_BST_RQ_LOCKFREE) || \
      (INDEX_STRUCT == IDX_BST_RQ_RWLOCK) || \
      (INDEX_STRUCT == IDX_BST_RQ_HTM_RWLOCK) || \
      (INDEX_STRUCT == IDX_BST_RQ_UNSAFE) || \
      (INDEX_STRUCT == IDX_CITRUS_RQ_LOCKFREE) || \
      (INDEX_STRUCT == IDX_CITRUS_RQ_RWLOCK) || \
      (INDEX_STRUCT == IDX_CITRUS_RQ_HTM_RWLOCK) || \
      (INDEX_STRUCT == IDX_CITRUS_RQ_UNSAFE) || \
      (INDEX_STRUCT == IDX_CITRUS_RQ_RLU) || \
      (INDEX_STRUCT == IDX_ABTREE_RQ_LOCKFREE) || \
      (INDEX_STRUCT == IDX_ABTREE_RQ_RWLOCK) || \
      (INDEX_STRUCT == IDX_ABTREE_RQ_HTM_RWLOCK) || \
      (INDEX_STRUCT == IDX_ABTREE_RQ_UNSAFE) || \
      (INDEX_STRUCT == IDX_BSLACK_RQ_LOCKFREE) || \
      (INDEX_STRUCT == IDX_BSLACK_RQ_RWLOCK) || \
      (INDEX_STRUCT == IDX_BSLACK_RQ_HTM_RWLOCK) || \
      (INDEX_STRUCT == IDX_BSLACK_RQ_UNSAFE) || \
      (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_LOCKFREE) || \
      (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_RWLOCK) || \
      (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_HTM_RWLOCK) || \
      (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_UNSAFE) || \
      (INDEX_STRUCT == IDX_SKIPLISTLOCK_RQ_SNAPCOLLECTOR)
#include "index_with_rq.h"
#elif (INDEX_STRUCT == IDX_BTREE)
#include "index_btree.h"
#elif (INDEX_STRUCT == IDX_HASH)
#include "index_hash.h"
#else
#error Must define INDEX_STRUCT to be one of the options in storage/index/all_indexes.h
#endif

#endif /* ALL_INDEXES_H */

