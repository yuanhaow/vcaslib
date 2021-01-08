/* 
 * File:   lockfree_list_impl.h
 * Author: Maya Arbel-Raviv
 * Based on: The Art of Multiprocessor Programming by Maurice Herlihy and Nir Shavit
 * Created on May 11, 2017, 3:05 PM
 */

#ifndef LOCKFREE_LIST_IMPL_H
#define LOCKFREE_LIST_IMPL_H

#include "lockfree_list.h"

#define MARK_BIT 0x2
#define BOOL_CAS __sync_bool_compare_and_swap

// TODO: REMOVE THIS DEBUG DEFINE AND THE CORRESPONDING DEBUG ASSERTS
#ifndef DCSSP_TAGBIT
#define DCSSP_TAGBIT 1
#endif

template<typename K, typename V>
class node_t {
public:
    union {
        struct {
            K key;
            volatile V val;
            nodeptr volatile next;
            volatile long long itime;
            volatile long long dtime;
        };
//        char bytes[64];
    };
    
    template <typename RQProvider>
    bool isMarked(const int tid, RQProvider * const prov){
        return ((casword_t)(prov->read_addr(tid,&next)) & MARK_BIT);
    }
};

template<typename K, typename V>
inline bool isMarked(nodeptr val){
    return ((casword_t) val & MARK_BIT);
}
template<typename K, typename V>
inline nodeptr getUnmarked(nodeptr val) {
    return (nodeptr)((casword_t) val & ~MARK_BIT);
}
template<typename K, typename V>
inline nodeptr getMarked(nodeptr val) {
    return (nodeptr)((casword_t)val | MARK_BIT);
}

template <typename K, typename V, class RecManager>
lflist<K,V,RecManager>::lflist(const int numProcesses, const K _KEY_MIN, const K _KEY_MAX, const V _NO_VALUE)
        :
#ifdef USE_DEBUGCOUNTERS
          counters(new debugCounters(numProcesses))
        ,
#endif
          recordmgr(new RecManager(numProcesses, SIGQUIT))
        , KEY_MIN(_KEY_MIN)
        , KEY_MAX(_KEY_MAX)
        , NO_VALUE(_NO_VALUE)
{
    rqProvider = new RQProvider<K, V, node_t<K,V>, lflist<K,V,RecManager>, RecManager, true, true>(numProcesses, this, recordmgr);
    
    // note: initThread calls rqProvider->initThread

    const int tid = 0;
    initThread(tid);
    nodeptr max = new_node(tid, KEY_MAX, 0, NULL);
    head = new_node(tid, KEY_MIN, 0, max);
}

template <typename K, typename V, class RecManager>
lflist<K,V,RecManager>::~lflist() {
    int tid = 0; 
    nodeptr pred;
    nodeptr curr;
    pred = head;
    curr = head->next; //head is never marked
    assert(((casword_t) curr->next & DCSSP_TAGBIT) == 0);
    while (curr->key < KEY_MAX) {
        recordmgr->deallocate(tid,pred);
        pred = curr;
        curr = getUnmarked(curr->next);
        assert(((casword_t) curr->next & DCSSP_TAGBIT) == 0);
    }
    recordmgr->deallocate(tid,pred);
    recordmgr->deallocate(tid,curr);
    delete rqProvider;
    recordmgr->printStatus();
    delete recordmgr;
#ifdef USE_DEBUGCOUNTERS
    delete counters;
#endif
}

template <typename K, typename V, class RecManager>
void lflist<K,V,RecManager>::initThread(const int tid) {
    if (init[tid]) return; else init[tid] = !init[tid];

    recordmgr->initThread(tid);
    rqProvider->initThread(tid);
}

template <typename K, typename V, class RecManager>
void lflist<K,V,RecManager>::deinitThread(const int tid) {
    if (!init[tid]) return; else init[tid] = !init[tid];

    recordmgr->deinitThread(tid);
    rqProvider->deinitThread(tid);
}

template <typename K, typename V, class RecManager>
nodeptr lflist<K,V,RecManager>::new_node(const int tid, const K& key, const V& val, nodeptr next) {
    nodeptr nnode = recordmgr->template allocate<node_t<K,V> >(tid);
    if (nnode == NULL) {
        printf("out of memory\n");
        exit(1);
    }
    rqProvider->init_node(tid, nnode);
    nnode->key = key;
    nnode->val = val;
    rqProvider->write_addr(tid, &nnode->next, next);
#ifdef __HANDLE_STATS
    GSTATS_APPEND(tid, node_allocated_addresses, ((long long) nnode)%(1<<12));
#endif
    return nnode;
}

template <typename K, typename V, class RecManager>
bool lflist<K,V,RecManager>::contains(const int tid, const K& key) {
    bool res; 
    recordmgr->leaveQuiescentState(tid, true);
    nodeptr curr = rqProvider->read_addr(tid,&head->next);
    while(curr->key < key){
        curr = (nodeptr) getUnmarked(rqProvider->read_addr(tid, &curr->next)); 
    }
    if (curr->key != key){
        recordmgr->enterQuiescentState(tid);
        return false;
    }
#ifdef RQ_SNAPCOLLECTOR
    rqProvider->search_report_target_key(tid, key, curr);
#endif
    recordmgr->enterQuiescentState(tid);
    return !curr->isMarked(tid, rqProvider);
}

template <typename K, typename V, class RecManager>
V lflist<K,V,RecManager>::doInsert(const int tid, const K& key, const V& val, bool onlyIfAbsent) {
    nodeptr pred;
    nodeptr curr;
    nodeptr succ;
    nodeptr node = NULL;
    V result = NO_VALUE;
    int count = 0;
    while(true){
retry_insert:
        recordmgr->leaveQuiescentState(tid);
        pred = head;
        curr = rqProvider->read_addr(tid,&head->next); //head is never marked
        assert(((casword_t)curr & MARK_BIT) == 0);
        while(true){
            nodeptr succ_field = rqProvider->read_addr(tid, &curr->next);
            succ = getUnmarked(succ_field);
            while(isMarked(succ_field)){  
                assert(((casword_t)curr & MARK_BIT) == 0);
                assert(((casword_t)succ & MARK_BIT) == 0);
                nodeptr deletedNodes[] = {curr, NULL};
                rqProvider->announce_physical_deletion(tid, deletedNodes);
                assert(curr->isMarked(tid, rqProvider));
                if(BOOL_CAS(&(pred->next), (casword_t)curr, (casword_t)succ)){
                    rqProvider->physical_deletion_succeeded(tid, deletedNodes);
                    assert(curr->isMarked(tid,rqProvider));
                    assert(getUnmarked(rqProvider->read_addr(tid, &curr->next)) == succ);
                }else{
                    rqProvider->physical_deletion_failed(tid, deletedNodes);
                    assert(curr->isMarked(tid,rqProvider));
                    assert(getUnmarked(rqProvider->read_addr(tid, &curr->next)) == succ);
                    recordmgr->enterQuiescentState(tid);
                    goto retry_insert;
                }
                curr = succ; //getUnmarked(rqProvider->read_addr(tid,&pred->next));//
                succ_field = rqProvider->read_addr(tid, &curr->next);
                succ = getUnmarked(succ_field);
            }
            if(curr->key >= key) break; 
            pred = curr;
            curr = succ;
        }
        if(curr->key == key) {
            result = curr->val;
#ifdef RQ_SNAPCOLLECTOR
            rqProvider->insert_readonly_report_target_key(tid, curr);
#endif
            
            if (onlyIfAbsent) {
                recordmgr->enterQuiescentState(tid);
                return result;
            } else {
                error("Insert-replace functionality is NOT implemented for this data structure!");
#if 0
                succ = getUnmarked(rqProvider->read_addr(tid, &curr->next));
                node = new_node(tid, key, val, succ);
                nodeptr insertedNodes[] = {NULL};
                nodeptr deletedNodes[] = {curr, NULL};
                if (rqProvider->linearize_update_at_cas(tid, &curr->next, succ, getMarked(succ), insertedNodes, deletedNodes) == succ) {

                    // TODO: implement a correct protocol, here. we need to INSERT AND MARK at the SAME TIME?? i'm not so sure this is possible with this list...
                    
                    // attempt physical deletion of the old (marked) node once
                    // if we fail, threads will simply attempt physical deletion if they encounter the node
                    rqProvider->announce_physical_deletion(tid, deletedNodes);
                    assert(curr->isMarked(tid, rqProvider));
                    if (BOOL_CAS(&(pred->next), (casword_t) curr, (casword_t) succ)) {
                        rqProvider->physical_deletion_succeeded(tid, deletedNodes);
                    } else {
                        rqProvider->physical_deletion_failed(tid, deletedNodes);
                    }

                    recordmgr->enterQuiescentState(tid);
                    return result;                    
                } else {
                    recordmgr->deallocate(tid, node);
                    recordmgr->enterQuiescentState(tid);
                    goto retry_insert;
                }
#endif
            }
        }
        node = new_node(tid,key,val,curr);
        assert(((casword_t)pred & MARK_BIT) == 0);
        assert(((casword_t)curr & MARK_BIT) == 0);
        nodeptr insertedNodes[] = {node, NULL};
        nodeptr deletedNodes[] = {NULL};
        if(rqProvider->linearize_update_at_cas(tid,&pred->next,curr,node,insertedNodes, deletedNodes) ==  curr){
            recordmgr->enterQuiescentState(tid);
            return result;
        } else {
            recordmgr->deallocate(tid,node);
            recordmgr->enterQuiescentState(tid);
            goto retry_insert;
        }
    }
}

template <typename K, typename V, class RecManager>
V lflist<K,V,RecManager>::erase(const int tid, const K& key) {
    nodeptr pred;
    nodeptr curr;
    nodeptr succ;
    V result = NO_VALUE;
    int count = 0;
    while(true){
retry_erase:
        recordmgr->leaveQuiescentState(tid);
        pred = head;
        curr = rqProvider->read_addr(tid,&head->next); //head is never marked
        assert(((casword_t)curr & MARK_BIT) == 0);
        while(true){
            assert(curr != NULL);
            nodeptr succ_field = rqProvider->read_addr(tid, &curr->next);
            succ = getUnmarked(succ_field);
            while(isMarked(succ_field)){  
                assert(((casword_t)curr & MARK_BIT) == 0);
                assert(((casword_t)succ & MARK_BIT) == 0);
                nodeptr deletedNodes[] = {curr, NULL};
                rqProvider->announce_physical_deletion(tid, deletedNodes);
                assert(curr->isMarked(tid, rqProvider));
                if(BOOL_CAS(&(pred->next), (casword_t)curr, (casword_t)succ)){
                    rqProvider->physical_deletion_succeeded(tid, deletedNodes);
                    assert(curr->isMarked(tid,rqProvider));
                    assert(getUnmarked(rqProvider->read_addr(tid, &curr->next)) == succ);
                }else{
                    rqProvider->physical_deletion_failed(tid, deletedNodes);
                    assert(curr->isMarked(tid,rqProvider));
                    assert(getUnmarked(rqProvider->read_addr(tid, &curr->next)) == succ);
                    recordmgr->enterQuiescentState(tid);
                    goto retry_erase;
                }
                curr = succ; //getUnmarked(rqProvider->read_addr(tid,&pred->next));//
                succ_field = rqProvider->read_addr(tid, &curr->next);
                succ = getUnmarked(succ_field);
            }
            if(curr->key >= key) break; 
            pred = curr;
            curr = succ;
        }

        if(curr->key != key){
            recordmgr->enterQuiescentState(tid);
            return result;
        }
        succ = getUnmarked(rqProvider->read_addr(tid, &curr->next));
        assert(((casword_t)curr & MARK_BIT) == 0);
        assert(((casword_t)succ & MARK_BIT) == 0);
        nodeptr insertedNodes[] = {NULL};
        nodeptr deletedNodes[] = {curr, NULL};
        if(rqProvider->linearize_update_at_cas(tid,&curr->next,succ,getMarked(succ),insertedNodes,deletedNodes)== succ){
            assert(isMarked(rqProvider->read_addr(tid, &curr->next)));
            assert(curr->isMarked(tid,rqProvider));
            result = curr->val;
            
            // attempt physical deletion of the old (marked) node once
            // if we fail, threads will simply attempt physical deletion if they encounter the node
            rqProvider->announce_physical_deletion(tid, deletedNodes);
            assert(curr->isMarked(tid, rqProvider));
            if (BOOL_CAS(&(pred->next), (casword_t)curr, (casword_t)succ)){
                rqProvider->physical_deletion_succeeded(tid, deletedNodes);
            } else {
                rqProvider->physical_deletion_failed(tid, deletedNodes);
            }
            recordmgr->enterQuiescentState(tid);
            return result;
        }
        recordmgr->enterQuiescentState(tid);
    }    
}

template <typename K, typename V, class RecManager>
int lflist<K,V,RecManager>::rangeQuery(const int tid, const K& lo, const K& hi, K * const resultKeys, V * const resultValues) {
    recordmgr->leaveQuiescentState(tid, true);
    rqProvider->traversal_start(tid);
    int cnt = 0;
    nodeptr curr = rqProvider->read_addr(tid, &head->next);
//    int iterations = 0;
#ifdef RQ_SNAPCOLLECTOR
    while (rqProvider->traversal_is_active(tid)) {
        nodeptr nextptr = rqProvider->read_addr(tid, &curr->next);
        if (!isMarked(nextptr)) {
            curr = rqProvider->traversal_try_add(tid, curr, resultKeys, resultValues, &cnt, lo, hi);
        }
        if (curr == NULL || curr->key == KEY_MAX) {
            break;
        }
        curr = (nodeptr) getUnmarked(rqProvider->read_addr(tid, &curr->next));
//        if (++iterations > 10) {
//            cout<<"iterations > 10"<<endl;
//            exit(-1);
//        }
//        else {
//            cout<<"traversal ["<<lo<<", "<<hi<<") sees key="<<curr->key<<" (KEY_MAX="<<KEY_MAX<<")"<<endl;
//        }
//    while (curr && curr->key < KEY_MAX && rqProvider->traversal_is_active(tid)) {
//        curr = rqProvider->traversal_try_add(tid, curr, resultKeys, resultValues, &cnt, lo, hi);
//        if (curr == NULL || curr->key == KEY_MAX) break;
//        curr = (nodeptr) getUnmarked(rqProvider->read_addr(tid, &curr->next));
//    }
    }
#else
    while (curr->key < lo) {
        curr = (nodeptr) getUnmarked(rqProvider->read_addr(tid, &curr->next)); 
    }
    while (curr->key <= hi) {
        rqProvider->traversal_try_add(tid, curr, resultKeys, resultValues, &cnt, lo, hi);
        curr = (nodeptr) getUnmarked(rqProvider->read_addr(tid, &curr->next)); 
    }
#endif
    rqProvider->traversal_end(tid, resultKeys, resultValues, &cnt, lo, hi);
#ifdef SNAPCOLLECTOR_PRINT_RQS
    cout<<"rqSize="<<cnt<<endl;
#endif
    recordmgr->enterQuiescentState(tid);
    return cnt;
}

template <typename K, typename V, class RecManager>
long long lflist<K,V,RecManager>::debugKeySum(nodeptr head) {
    long long result = 0;
    nodeptr volatile curr = (nodeptr) head->next; 
    assert(((casword_t) curr->next & DCSSP_TAGBIT) == 0);
    while (curr->key < KEY_MAX) {
        if (!isMarked(curr->next)){
            result += curr->key;
        }
        curr = (nodeptr) getUnmarked(curr->next);
        assert(((casword_t) curr->next & DCSSP_TAGBIT) == 0);
    }
    return result;
}

template <typename K, typename V, class RecManager>
long long lflist<K,V,RecManager>::debugKeySum() {
    return debugKeySum(head);
}

template <typename K, typename V, class RecManager>
bool lflist<K,V,RecManager>::validate(const long long keysum, const bool checkkeysum){
    nodeptr pred;
    nodeptr curr;
    pred = head;
    curr = head->next; //head is never marked
    assert(((casword_t) curr->next & DCSSP_TAGBIT) == 0);
    while(curr->key < KEY_MAX ){
            if(curr->key <= pred->key){
            return false; 
        }
        pred = curr;
        curr = getUnmarked(curr->next);
        assert(((casword_t) curr->next & DCSSP_TAGBIT) == 0);
    }
    return true; 
}

template <typename K, typename V, class RecManager>
long long lflist<K,V,RecManager>::getSize() {
    long long sum = 0;
    for (nodeptr curr = head->next; curr->key != KEY_MAX; curr = getUnmarked(curr->next)) {
        assert(((casword_t) curr->next & DCSSP_TAGBIT) == 0);
        if (!isMarked(curr->next)){
            ++sum;
        }
    }
    return sum;
}

template <typename K, typename V, class RecManager>
inline bool lflist<K,V,RecManager>::isLogicallyDeleted(const int tid, node_t<K,V> * node){
    return node->isMarked(tid, rqProvider);
}
#endif /* LOCKFREE_LIST_IMPL_H */

