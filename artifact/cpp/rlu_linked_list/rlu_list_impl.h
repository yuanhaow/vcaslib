/* 
 * File:   rlu_list_impl.h
 * Based on:
 * https://github.com/rlu-sync/rlu/blob/master/hash-list.c (a1e7e9e  on Aug 23, 2015)
 *
 * Created on April 26, 2017, 10:58 AM
 */

#ifndef RLU_LIST_IMPL_H
#define RLU_LIST_IMPL_H

#include <stdlib.h>
#include <stdio.h>

#include "rlu.h"
#include "rlu_list.h"
#include "rq_debugging.h"

extern __thread rlu_thread_data_t * rlu_self;

template<typename K, typename V>
class node_t {
public:
    K key;
    volatile V val;
    nodeptr volatile p_next;
    //uint8_t padding[PREFETCH_SIZE_BYTES - sizeof (skey_t) - sizeof (sval_t) - sizeof (struct nodeptr) - sizeof (lock_type) - sizeof (uint8_t) ];
};

template <typename K, typename V>
rlulist<K,V>::rlulist(const int numProcesses, const K _MIN_KEY, const K _MAX_KEY, const V _NO_VALUE)
        :
#ifdef USE_DEBUGCOUNTERS
          counters(new debugCounters(numProcesses))
        ,
#endif
          NUM_PROCESSES(numProcesses)
        , MAX_KEY(_MAX_KEY)
        , NO_VALUE(_NO_VALUE) {
    const int tid = 0;
    DEBUG_INIT_RQPROVIDER(numProcesses);
    nodeptr p_min_node;
    nodeptr p_max_node;

    p_max_node = new_node(tid);
    p_max_node->key = _MAX_KEY;
    p_max_node->val = _NO_VALUE;
    p_max_node->p_next = NULL;
	
    p_min_node = new_node(tid);
    p_min_node->key = _MIN_KEY;
    p_min_node->val = _NO_VALUE;
    p_min_node->p_next = p_max_node;
	
    head = p_min_node;
}

template <typename K, typename V>
rlulist<K,V>::~rlulist() {
    DEBUG_DEINIT_RQPROVIDER(NUM_PROCESSES);
    // TODO
}

template <typename K, typename V>
nodeptr rlulist<K,V>::new_node(const int tid) {
    nodeptr p_new_node = (nodeptr)RLU_ALLOC(sizeof(node_t<K,V>));
    if (p_new_node == NULL){
        printf("out of memory\n");
	exit(1); 
    }    
#ifdef __HANDLE_STATS
    GSTATS_APPEND(tid, node_allocated_addresses, ((long long) p_new_node)%(1<<12));
#endif
    return p_new_node;
}

template <typename K, typename V>
void rlulist<K,V>::free_node(const int tid, nodeptr p_node){
	RLU_FREE(rlu_self, p_node);
}

template <typename K, typename V>
bool rlulist<K,V>::contains(const int tid, const K& key) {
    TRACE COUTATOMICTID("contains "<<key<<endl);
    bool result;
    K k;
    nodeptr p_prev;
    nodeptr p_next;

    RLU_READER_LOCK(rlu_self);

    p_prev = (nodeptr)RLU_DEREF(rlu_self, head);
    p_next = (nodeptr)RLU_DEREF(rlu_self, (p_prev->p_next));
    while (1) {
            k = p_next->key;
            if (k >= key) {
                    break;
            }
            p_prev = p_next;
            p_next = (nodeptr)RLU_DEREF(rlu_self, (p_prev->p_next));
    }

    result = (k == key);
    RLU_READER_UNLOCK(rlu_self);
    return result;
}

template <typename K, typename V>
V rlulist<K,V>::doInsert(const int tid, const K& key, const V& val, bool onlyIfAbsent) {
    TRACE COUTATOMICTID("insert "<<key<<endl);
    int result;
    nodeptr p_prev;
    nodeptr p_next;
    nodeptr p_new_node;
    K k;

restart:
    RLU_READER_LOCK(rlu_self);

    p_prev = (nodeptr)RLU_DEREF(rlu_self, head);
    p_next = (nodeptr)RLU_DEREF(rlu_self, (p_prev->p_next));
    while (1) {
            k = p_next->key;
            if (k >= key) {
                    break;
            }
            p_prev = p_next;
            p_next = (nodeptr)RLU_DEREF(rlu_self, (p_prev->p_next));
    }

    result = (k != key);

    V result_val = NO_VALUE;
    if (result) {
            if (!RLU_TRY_LOCK(rlu_self, &p_prev)) {
                    RLU_ABORT(rlu_self);
                    goto restart;
            }
            if (!RLU_TRY_LOCK(rlu_self, &p_next)) {
                    RLU_ABORT(rlu_self);
                    goto restart;
            }
            p_new_node = new_node(tid);
            p_new_node->key = key;
            p_new_node->val = val;
            RLU_ASSIGN_PTR(rlu_self, &(p_new_node->p_next), p_next);
            RLU_ASSIGN_PTR(rlu_self, &(p_prev->p_next), p_new_node);
    } else {
        // key already exists -- get its value
        result_val = p_next->val;
    }
    RLU_READER_UNLOCK(rlu_self);
    if(result){//writer_version is set at read-unlock
        nodeptr insertedNodes[] = {p_new_node, NULL};
        nodeptr deletedNodes[]={NULL};
#ifdef USE_RQ_DEBUGGING
        DEBUG_RECORD_UPDATE_CHECKSUM<K,V>(tid, rlu_self->last_writer_version, insertedNodes, deletedNodes, this);
#endif
    } else {
        // key already exists
        if (!onlyIfAbsent) {
            cout<<"ERROR: insert-replace functionality not implemented for rlu_list_impl"<<endl;
            exit(-1);
        }
    }
    return result_val;
}

template <typename K, typename V>
const pair<V,bool> rlulist<K,V>::erase(const int tid, const K& key) {
    TRACE COUTATOMICTID("erase "<<key<<endl);
    int result;
    nodeptr p_prev;
    nodeptr p_next;
    nodeptr n;
    K k;
	
restart:
    RLU_READER_LOCK(rlu_self);

    p_prev = (nodeptr)RLU_DEREF(rlu_self, head);
    p_next = (nodeptr)RLU_DEREF(rlu_self, (p_prev->p_next));
    while (1) {
            k = p_next->key;
            if (k >= key) {
                    break;
            }
            p_prev = p_next;
            p_next = (nodeptr)RLU_DEREF(rlu_self, (p_prev->p_next));
    }

    result = (k == key);

    if (result) {
            n = (nodeptr)RLU_DEREF(rlu_self, (p_next->p_next));
            V result_val = n->val;
            if (!RLU_TRY_LOCK(rlu_self, &p_prev)) {
                    RLU_ABORT(rlu_self);
                    goto restart;
            }
            if (!RLU_TRY_LOCK(rlu_self, &p_next)) {
                    RLU_ABORT(rlu_self);
                    goto restart;
            }
            RLU_ASSIGN_PTR(rlu_self, &(p_prev->p_next), n);
            RLU_FREE(rlu_self, p_next);
            RLU_READER_UNLOCK(rlu_self);
            //if(result){ //writer_version is set at read-unlock
            nodeptr insertedNodes[] = {NULL};
            nodeptr deletedNodes[]={p_next, NULL};
#ifdef USE_RQ_DEBUGGING
            DEBUG_RECORD_UPDATE_CHECKSUM<K,V>(tid, rlu_self->last_writer_version, insertedNodes, deletedNodes, this);
#endif
            //}
            return pair<V,bool>(result_val, true);
    }
    RLU_READER_UNLOCK(rlu_self);
    return pair<V,bool>(NO_VALUE, false);
}

template <typename K, typename V>
int rlulist<K,V>::rangeQuery(const int tid, int lo, int hi, K * const resultKeys, V * const resultValues) {
    int cnt = 0;
    nodeptr p_prev;
    nodeptr p_next;
    
    RLU_READER_LOCK(rlu_self);
    
    p_prev = (nodeptr)RLU_DEREF(rlu_self, head);
    p_next = (nodeptr)RLU_DEREF(rlu_self, (p_prev->p_next));
    while (p_next->key < lo) {
        p_next = (nodeptr)RLU_DEREF(rlu_self, (p_next->p_next));
    }
    while (p_next->key <= hi) {
        resultKeys[cnt] = p_next->key;
        resultValues[cnt] = p_next->val;
        ++cnt;
        p_next = (nodeptr)RLU_DEREF(rlu_self, (p_next->p_next));
    }
#ifdef USE_RQ_DEBUGGING
    DEBUG_RECORD_RQ_CHECKSUM<K>(tid, rlu_self->local_version+1, resultKeys, cnt);
#endif
    RLU_READER_UNLOCK(rlu_self);
    return cnt;
}

template <typename K, typename V>
long long rlulist<K,V>::debugKeySum(nodeptr head) {
    long long result = 0;
    nodeptr curr = head->p_next;
    while (curr->key < MAX_KEY) {
        result += curr->key;
        curr = curr->p_next;
    }
    return result;
}

template <typename K, typename V>
long long rlulist<K,V>::debugKeySum() {
    return debugKeySum(head);
}

#endif /* RLU_LIST_IMPL_H */

