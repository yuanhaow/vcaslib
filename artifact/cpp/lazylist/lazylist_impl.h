/**
 *   This algorithm is based on: 
 *   A Lazy Concurrent List-Based Set Algorithm,
 *   S. Heller, M. Herlihy, V. Luchangco, M. Moir, W.N. Scherer III, N. Shavit
 *   OPODIS 2005
 *   
 *   The implementation is based on implementations by:
 *   Vincent Gramoli https://sites.google.com/site/synchrobench/
 *   Vasileios Trigonakis http://lpd.epfl.ch/site/ascylib - http://lpd.epfl.ch/site/optik
 */

#ifndef LAZYLIST_IMPL_H
#define	LAZYLIST_IMPL_H

#include <cassert>
#include <csignal>
#include "locks_impl.h"
#include "lazylist.h"

#ifndef casword_t
#define casword_t uintptr_t
#endif

template<typename K, typename V>
class node_t {
public:
    K key;
    volatile V val;
    nodeptr volatile next;
    volatile int lock;
    volatile long long marked; // is stored as a long long simply so it is large enough to be used with the lock-free RQProvider (which requires all fields that are modified at linearization points of operations to be at least as large as a machine word)
    volatile long long itime;
    volatile long long dtime;
    
    template <typename RQProvider>
    bool isMarked(const int tid, RQProvider * const prov){
        return prov->read_addr(tid, (casword_t *) &marked);
    }
    //uint8_t padding[PREFETCH_SIZE_BYTES - sizeof (skey_t) - sizeof (sval_t) - sizeof (struct nodeptr) - sizeof (lock_type) - sizeof (uint8_t) ];
};

template <typename K, typename V, class RecManager>
lazylist<K,V,RecManager>::lazylist(const int numProcesses, const K _KEY_MIN, const K _KEY_MAX, const V _NO_VALUE)
        : recordmgr(new RecManager(numProcesses, SIGQUIT))
        , rqProvider(new RQProvider<K, V, node_t<K,V>, lazylist<K,V,RecManager>, RecManager, true, false>(numProcesses, this, recordmgr))
#ifdef USE_DEBUGCOUNTERS
        , counters(new debugCounters(numProcesses))
#endif
        , KEY_MIN(_KEY_MIN)
        , KEY_MAX(_KEY_MAX)
        , NO_VALUE(_NO_VALUE) {
    const int tid = 0;
    initThread(tid);
    nodeptr max = new_node(tid, KEY_MAX, 0, NULL);
    head = new_node(tid, KEY_MIN, 0, max);
}

template <typename K, typename V, class RecManager>
lazylist<K,V,RecManager>::~lazylist() {
    const int dummyTid = 0;
    nodeptr curr = head;
    while (curr->key < KEY_MAX) {
        nodeptr next = curr->next;
        recordmgr->deallocate(dummyTid, curr);
        curr = next;
    }
    recordmgr->deallocate(dummyTid, curr);
    delete rqProvider;
    delete recordmgr;
#ifdef USE_DEBUGCOUNTERS
    delete counters;
#endif
}

template <typename K, typename V, class RecManager>
void lazylist<K,V,RecManager>::initThread(const int tid) {
    if (init[tid]) return; else init[tid] = !init[tid];

    recordmgr->initThread(tid);
    rqProvider->initThread(tid);
}

template <typename K, typename V, class RecManager>
void lazylist<K,V,RecManager>::deinitThread(const int tid) {
    if (!init[tid]) return; else init[tid] = !init[tid];

    recordmgr->deinitThread(tid);
    rqProvider->deinitThread(tid);
}

template <typename K, typename V, class RecManager>
nodeptr lazylist<K,V,RecManager>::new_node(const int tid, const K& key, const V& val, nodeptr next) {
    nodeptr nnode = recordmgr->template allocate<node_t<K,V> >(tid);
    if (nnode == NULL) {
        cout<<"out of memory"<<endl;
        exit(1);
    }
    rqProvider->init_node(tid, nnode);
    nnode->key = key;
    nnode->val = val;
    rqProvider->write_addr(tid, &nnode->next, next);
    rqProvider->write_addr(tid, &nnode->marked, 0LL);
    nnode->lock = false;
#ifdef __HANDLE_STATS
    GSTATS_APPEND(tid, node_allocated_addresses, ((long long) nnode)%(1<<12));
#endif
    return nnode;
}

template <typename K, typename V, class RecManager>
inline int lazylist<K,V,RecManager>::validateLinks(const int tid, nodeptr pred, nodeptr curr) {
    return (!rqProvider->read_addr(tid, &pred->marked)
            && !rqProvider->read_addr(tid, &curr->marked)
            && (rqProvider->read_addr(tid, &pred->next) == curr));
}

template <typename K, typename V, class RecManager>
bool lazylist<K,V,RecManager>::contains(const int tid, const K& key) {
    recordmgr->leaveQuiescentState(tid, true);
    nodeptr curr = head;
    while (curr->key < key) {
        curr = rqProvider->read_addr(tid, &curr->next);
    }

    V res = NO_VALUE; 
    if ((curr->key == key) && !rqProvider->read_addr(tid, &curr->marked)) {
        res = curr->val;
    }
    recordmgr->enterQuiescentState(tid);
    return (res != NO_VALUE);
}

template <typename K, typename V, class RecManager>
V lazylist<K,V,RecManager>::doInsert(const int tid, const K& key, const V& val, bool onlyIfAbsent) {
    nodeptr curr;
    nodeptr pred;
    nodeptr newnode;
    V result;
    while (true) {
        recordmgr->leaveQuiescentState(tid);
        pred = head;
        curr = rqProvider->read_addr(tid, &pred->next);
        while (curr->key < key) {
            pred = curr;
            curr = rqProvider->read_addr(tid, &curr->next);
        }
        acquireLock(&(pred->lock));
        if (validateLinks(tid, pred, curr)) {
            if (curr->key == key) {
                if (rqProvider->read_addr(tid, &curr->marked)) { // this is an optimization
                    releaseLock(&(pred->lock));
                    recordmgr->enterQuiescentState(tid);
                    continue;
                }
                // node containing key is not marked
                if (onlyIfAbsent) {
                    V result = curr->val;
                    releaseLock(&(pred->lock));
                    recordmgr->enterQuiescentState(tid);
                    return result;
                }
                acquireLock(&(curr->lock));
                result = curr->val;
                //nodeptr insertedNodes[] = {NULL};
                //nodeptr deletedNodes[] = {NULL};
                //rqProvider->linearize_update_at_write(tid, &curr->val, val, insertedNodes, deletedNodes); // shifting KEY_MAX, and not using read_addr to access keys, makes this a problem. no need to run this through the rqProvider, since we just need a write, and we never access keys using the rqProvider.
                curr->val = val; // original linearization point
                releaseLock(&(curr->lock));
                releaseLock(&(pred->lock));
                recordmgr->enterQuiescentState(tid);
                return result;
            }
            // key is not in list
            assert(curr->key != key);
            result = NO_VALUE;
            newnode = new_node(tid, key, val, curr);
            nodeptr insertedNodes[] = {newnode, NULL};
            nodeptr deletedNodes[] = {NULL};
            rqProvider->linearize_update_at_write(tid, &pred->next, newnode, insertedNodes, deletedNodes);
            releaseLock(&(pred->lock));
            return result;
        }
        releaseLock(&(pred->lock));
        recordmgr->enterQuiescentState(tid);
    }
}

/*
 * Logically remove an element by setting a mark bit to 1 
 * before removing it physically.
 */
template <typename K, typename V, class RecManager>
V lazylist<K,V,RecManager>::erase(const int tid, const K& key) {
    nodeptr pred;
    nodeptr curr;
    V result;
    while (true) {
        recordmgr->leaveQuiescentState(tid);
        pred = head;
        curr = rqProvider->read_addr(tid, &pred->next);
        while (curr->key < key) {
            pred = curr;
            curr = rqProvider->read_addr(tid, &curr->next);
        }

        if (curr->key != key) {
            result = NO_VALUE;
            recordmgr->enterQuiescentState(tid);
            return result;
        }
        acquireLock(&(pred->lock));
        acquireLock(&(curr->lock));
        if (validateLinks(tid, pred, curr)) {
            // TODO: maybe implement version with atomic removal of consecutive marked nodes
            assert(curr->key == key);
            result = curr->val;
            nodeptr c_nxt = rqProvider->read_addr(tid, &curr->next);

//            curr->marked = 1;                                                   // LINEARIZATION POINT
            nodeptr insertedNodes[] = {NULL};
            nodeptr deletedNodes[] = {curr, NULL};
            rqProvider->linearize_update_at_write(tid, &curr->marked, 1LL, insertedNodes, deletedNodes);

            rqProvider->announce_physical_deletion(tid, deletedNodes);
            rqProvider->write_addr(tid, &pred->next, c_nxt);
            rqProvider->physical_deletion_succeeded(tid, deletedNodes);

            releaseLock(&(curr->lock));
            releaseLock(&(pred->lock));
            recordmgr->enterQuiescentState(tid);
            return result;
        }
        releaseLock(&(curr->lock));
        releaseLock(&(pred->lock));
        recordmgr->enterQuiescentState(tid);
    }
}

template <typename K, typename V, class RecManager>
int lazylist<K,V,RecManager>::rangeQuery(const int tid, const K& lo, const K& hi, K * const resultKeys, V * const resultValues) {
    recordmgr->leaveQuiescentState(tid, true);
    rqProvider->traversal_start(tid);
    int cnt = 0;
    nodeptr curr = rqProvider->read_addr(tid, &head->next);
    while (curr->key < lo) {
        curr = rqProvider->read_addr(tid, &curr->next);
    }
    while (curr->key <= hi) {
        __builtin_prefetch(curr->next);
        rqProvider->traversal_try_add(tid, curr, resultKeys, resultValues, &cnt, lo, hi);
        curr = rqProvider->read_addr(tid, &curr->next);
    }
    rqProvider->traversal_end(tid, resultKeys, resultValues, &cnt, lo, hi);
    recordmgr->enterQuiescentState(tid);
    return cnt;
}

template <typename K, typename V, class RecManager>
long long lazylist<K,V,RecManager>::debugKeySum(nodeptr head) {
    long long result = 0;
    nodeptr curr = head->next;
    while (curr->key < KEY_MAX) {
        result += curr->key;
        curr = curr->next;
    }
    return result;
}

template <typename K, typename V, class RecManager>
long long lazylist<K,V,RecManager>::debugKeySum() {
    return debugKeySum(head);
}

template <typename K, typename V, class RecManager>
inline bool lazylist<K,V,RecManager>::isLogicallyDeleted(const int tid, node_t<K,V> * node){
    return node->isMarked(tid, rqProvider);
}
#endif	/* LAZYLIST_IMPL_H */

