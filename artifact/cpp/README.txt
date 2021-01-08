This is a software artifact to accompany the paper:

Harnessing epoch-based reclamation for efficient range queries.
Maya Arbel-Raviv and Trevor Brown.

In this artifact, we implement the following data structures:
1. External binary search tree - lock-free
   (./bst/)
2. Internal binary search tree - fine-grained locks and read-copy-update (RCU)
   (./citrus/)
3. Internal binary search tree - read-lock-update (RLU)
   (./rlu_citrus/)
4. Relaxed b-slack tree - lock-free
   (./bslack_reuse/)
5. Relaxed (a,b)-tree - lock-free
   (./bslack_reuse/ compiled with -DUSE_SIMPLIFIED_ABTREE_REBALANCING)
6. Skip list - fine-grained locks
   (./skiplist_lock/)
7. Linked list - lock-free
   (./lockfree_list/)
8. Linked list - fine-grained locks and optimistic validation
   (./lazylist/)
9. Linked list - read-log-update (RLU)
   (./rlu_linked_list/)

We implement several methods for adding range queries to these data structures:
a. Our lock-based technique                     (./rq/rq_rwlock.h)
b. Our HTM and lock-based technique             (./rq/rq_htm_rwlock.h)
c. Our lock-free technique                      (./rq/rq_lockfree.h)
d. A non-linearizable single-collect technique  (./rq/rq_unsafe.h)
e. The snap collector of Petrank and Timnat     (./rq/rq_snapcollector.h)
f. RLU snapshots (can only be used with RLU-based data structures)

The following pairs of data structures and range query techniques can be used:

                Data structures
       | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9
    ---+---+---+---+---+---+---+---+---+---
     a | x | x |   | x | x | x | x | x |
     b | x | x |   | x | x | x | x | x |
 R   c | x | x |   | x | x | x | x | x |
 Q   d | x | x |   | x | x | x | x | x |    (non-linearizable)
     e |   |   |   |   |   | x | x | x |
     f |   |   | x |   |   |   |   |   | x

Note that RLU (used in data structures 3 and 9) is a full synchronization
methodology that dictates how all queries and updates are implemented.
Conceptually, data structures 2 and 3 are the same (and 8 and 9 are the same).
In practice, the code must be completely rewritten to use RLU instead of the
other synchronization methods. Thus, range query techniques a-e cannot be used
with data structures 3 and 9. However, since this is the only way that RLU can
be used, it is reasonable to compare 2(a-d) with 3(f) and 8(a-e) and 9(f).

The epoch-based memory reclamation for our range query techniques (1, 2 and 3)
is a slightly modified version of the following algorithm.
DEBRA: distributed epoch-based reclamation (DISC 2015).

Two benchmarks are included: microbench and macrobench.
    - Microbench is a simple set/dictionary microbenchmark.
    - Macrobench is a modified version of the DBx1000 TPC-C database benchmark.
      The original DBx1000 implementation did not actually implement real
      range queries. It used hash tables (with chaining) for its database index,
      and merely "simulated" the cost of range queries by traversing hash table
      buckets and returning arbitrary elements.
      We implemented real support for range queries, and replaced the original
      index with several of our data structure implementations.

Prerequisites:
    - Multicore system (preferably w/at least 16 h/w threads)
      Memory model: total store order (modern Intel/AMD/Oracle processors)
    - Recent Linux (tested on Ubuntu 14.04 LTS)
    - For HTM results, an Intel system with TSX support is needed
      (On other systems, the benchmarks will run, but data will not
       be produced for the HTM-based algorithms.)
    - Python 2.7+ (NOT 3.x)
      with libraries: numpy 1.12+, matplotlib 2.1+, pandas 0.13.1+
    - GCC 4.8+
    - Make

Usage:
    1. Edit config.mk, following the instructions therein.
    2. Microbench:
       cd microbench
       make -j
       ./runscript.sh
       python graphs.py
    3. Macrobench:
       cd macrobench
       ./compile.sh
       ./runscript.sh
       ./makecsv.sh > dbx.csv
       python graph.py
       
Compilation yields the following binaries:

  [[Microbenchmark]] ./microbench/*.out
    bst.rq_rwlock.out                               Implementation 1a
    bst.rq_htm_rwlock.out                           Implementation 1b
    bst.rq_lockfree.out                             Implementation 1c
    bst.rq_unsafe.out                               Implementation 1d
    citrus.rq_rwlock.out                            Implementation 2a
    citrus.rq_htm_rwlock.out                        Implementation 2b
    citrus.rq_lockfree.out                          Implementation 2c
    citrus.rq_unsafe.out                            Implementation 2d
    citrus.rq_rlu.out                               Implementation 3f
    bslack.rq_rwlock.out                            Implementation 4a
    bslack.rq_htm_rwlock.out                        Implementation 4b
    bslack.rq_lockfree.out                          Implementation 4c
    bslack.rq_unsafe.out                            Implementation 4d
    abtree.rq_rwlock.out                            Implementation 5a
    abtree.rq_htm_rwlock.out                        Implementation 5b
    abtree.rq_lockfree.out                          Implementation 5c
    abtree.rq_unsafe.out                            Implementation 5d
    skiplistlock.rq_rwlock.out                      Implementation 6a
    skiplistlock.rq_htm_rwlock.out                  Implementation 6b
    skiplistlock.rq_lockfree.out                    Implementation 6c
    skiplistlock.rq_unsafe.out                      Implementation 6d
    skiplistlock.rq_snapcollector.out               Implementation 6e
    lflist.rq_rwlock.out                            Implementation 7a
    lflist.rq_htm_rwlock.out                        Implementation 7b
    lflist.rq_lockfree.out                          Implementation 7c
    lflist.rq_unsafe.out                            Implementation 7d
    lflist.rq_snapcollector.out                     Implementation 7e
    lazylist.rq_rwlock.out                          Implementation 8a
    lazylist.rq_htm_rwlock.out                      Implementation 8b
    lazylist.rq_lockfree.out                        Implementation 8c
    lazylist.rq_unsafe.out                          Implementation 8d
    lazylist.rq_rlu.out                             Implementation 9f

  [[Macrobenchmark]] ./macrobench/bin/HOSTNAME/*.out
    (where HOSTNAME is the output of `hostname`)
    rundb_TPCC_BST_RQ_RWLOCK.out                    Implementation 1a
    rundb_TPCC_BST_RQ_HTM_RWLOCK.out                Implementation 1b
    rundb_TPCC_BST_RQ_LOCKFREE.out                  Implementation 1c
    rundb_TPCC_BST_RQ_UNSAFE.out                    Implementation 1d
    rundb_TPCC_CITRUS_RQ_RWLOCK.out                 Implementation 2a
    rundb_TPCC_CITRUS_RQ_HTM_RWLOCK.out             Implementation 2b
    rundb_TPCC_CITRUS_RQ_LOCKFREE.out               Implementation 2c
    rundb_TPCC_CITRUS_RQ_UNSAFE.out                 Implementation 2d
    rundb_TPCC_CITRUS_RQ_RLU.out                    Implementation 3f
    rundb_TPCC_BSLACK_RQ_RWLOCK.out                 Implementation 4a
    rundb_TPCC_BSLACK_RQ_HTM_RWLOCK.out             Implementation 4b
    rundb_TPCC_BSLACK_RQ_LOCKFREE.out               Implementation 4c
    rundb_TPCC_BSLACK_RQ_UNSAFE.out                 Implementation 4d
    rundb_TPCC_ABTREE_RQ_RWLOCK.out                 Implementation 5a
    rundb_TPCC_ABTREE_RQ_HTM_RWLOCK.out             Implementation 5b
    rundb_TPCC_ABTREE_RQ_LOCKFREE.out               Implementation 5c
    rundb_TPCC_ABTREE_RQ_UNSAFE.out                 Implementation 5d
    rundb_TPCC_SKIPLISTLOCK_RQ_RWLOCK.out           Implementation 6a
    rundb_TPCC_SKIPLISTLOCK_RQ_HTM_RWLOCK.out       Implementation 6b
    rundb_TPCC_SKIPLISTLOCK_RQ_LOCKFREE.out         Implementation 6c
    rundb_TPCC_SKIPLISTLOCK_RQ_UNSAFE.out           Implementation 6d
    rundb_TPCC_SKIPLISTLOCK_RQ_SNAPCOLLECTOR.out    Implementation 6e
    rundb_TPCC_HASH.out                             original TPC-C index.
    (Note: the original HASH index does NOT support real range queries.
           The authors of DBx "faked" range query support in their TPC-C
           implementation by performing some arbitrary work that they
           estimated would be approximately as computationally expensive
           as a range query.
           Of course, hash tables cannot be used to compute general range
           queries, so it is not really fair to compare HASH with the
           other indexes.
           That said, the ABTREE and BSLACK indexes actually outperform HASH!
           This software artifact may very well contain the first concurrent
           ORDERED dictionaries that outperform concurrent hash tables.)

The microbenchmark binaries take the following arguments (in any order)
    -nrq NN         number of "range query" threads (which perform 100% RQs)
    -nwork NN       number of "worker" threads (which perform a mixed workload)
    -i NN           percentage of insertion operations for worker threads
    -d NN           percentage of deletion operations for worker threads
    -rq NN          percentage of range query operations for worker threads
                    (workers perform (100 - i - d - rq)% searches)
    -rqsize NN      maximum size of a range query (number of keys)
    -k NN           size of fixed key range
                    (keys for ins/del/search are drawn uniformly from [0, k).)
    -t NN           number of milliseconds to run a trial
    -p              optional: if present, the trees will be prefilled to
                    contain 1/2 of key range [0, k) at the start of each trial.
    -bind XX        optional: thread pinning/binding policy
                    XX is a comma separated list of logical processor indexes
                    or ranges of logical processors.
                    for example, "-bind 0-11,24-35,12-23,36-47" will cause
                    the first 48 threads to be pinned to logical processors:
                    0,1,2,3,4,5,6,7,8,9,1,11,24,25,26,27,...
                    if -bind is not specified, then threads will not be pinned.
                    if there are both "worker" and "range query" threads, then
                    worker threads are pinned first, followed by range query
                    threads.

The macrobenchmark binaries take the following arguments (in any order)
    -t NN           number of threads performing database transactions
    -n NN           number of warehouses in the TPC-C workload
                    (recommended: same as the maximum number of threads)
    -pin XX         optional: thread pinning/binding policy
                    (same as -bind above)

Note that we include a scalable allocator implementation in lib/
This scalable allocator, jemalloc, overrides C++ new/delete and malloc/free.
To use jemalloc (RECOMMENDED):
    Prefix your MICRO BENCHMARK command line with:
        LD_PRELOAD=../lib/libjemalloc.so
    Prefix your MACRO BENCHMARK command line with:
        LD_PRELOAD=../lib/libjemalloc.so TREE_MALLOC=../lib/libjemalloc.so

Running individual executions:

Example: run a 1-second micro benchmark
         with 47 threads doing 50% insert and 50% delete
         and 1 thread doing 100% range queries,
         with key range [0, 10^6) and range queries of size 10000,
         with prefilling,
         using the fast allocator jemalloc,
         on a relaxed (a,b)-tree that uses the lock-based range query algorithm,
         with threads pinned to logical processors 0-11,24-35,12-23,36-47 (in that order)
    Starting in directory "microbenchmark/" run
    $ LD_PRELOAD=../lib/libjemalloc.so ./`hostname`.abtree.rq_rwlock.out -i 50 -d 50 -k 1000000 -rq 0 -rqsize 10000 -t 1000 -p -nrq 1 -nwork 47 -bind 0-11,24-35,12-23,36-47

Example: run the TPC-C macro benchmark with 48 warehouses and 48 threads,
         using the lock-free BST and the lock-free range query algorithm,
         and using the fast allocator jemalloc
    Starting in directory "macrobenchmark/" run
    $ LD_PRELOAD=../lib/libjemalloc.so TREE_MALLOC=../lib/libjemalloc.so ./bin/`hostname`/rundb_TPCC_BST_RQ_LOCKFREE.out -t48 -n48
