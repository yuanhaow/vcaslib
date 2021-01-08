/**
 * Copyright 2015
 * Maya Arbel (mayaarl [at] cs [dot] technion [dot] ac [dot] il).
 * Adam Morrison (mad [at] cs [dot] technion [dot] ac [dot] il).
 *
 * This file is part of Predicate RCU.
 *
 * Predicate RCU is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors Maya Arbel and Adam Morrison
 * Converted into a class and implemented as a 3-path algorithm by Trevor Brown
 */

#ifndef CITRUS_IMPL_H
#define CITRUS_IMPL_H

#include <stdlib.h>
#include <stdio.h>
#include <assert.h>
#include <pthread.h>
#include <utility>
#include "citrus.h"
#include "urcu.h"
#include "locks_impl.h"
using namespace std;
using namespace urcu;

template <typename K, typename V, class RecManager>
nodeptr citrustree<K,V,RecManager>::newNode(const int tid, K key, V value) {
    nodeptr nnode = recordmgr->template allocate<node_t<K,V> >(tid);
    if (nnode == NULL) {
        printf("out of memory\n");
        exit(1);
    }
    rqProvider->init_node(tid, nnode);
    nnode->key = key;
    nnode->marked = false;
    rqProvider->write_addr(tid, &nnode->child[0], (nodeptr) NULL);
    rqProvider->write_addr(tid, &nnode->child[1], (nodeptr) NULL);
    nnode->tag[0] = 0;
    nnode->tag[1] = 0;
    nnode->value = value;
//    if (pthread_mutex_init(&(nnode->lock), NULL) != 0) {
//        printf("\n mutex init failed\n");
//    }
    nnode->lock = false;
#ifdef __HANDLE_STATS
    GSTATS_APPEND(tid, node_allocated_addresses, ((long long) nnode)%(1<<12));
#endif
    return nnode;
}

template <typename K, typename V, class RecManager>
citrustree<K,V,RecManager>::citrustree(const K bigger_than_max_key, const V _NO_VALUE, const int numProcesses)
        : recordmgr(new RecManager(numProcesses, SIGQUIT))
        , rqProvider(new RQProvider<K, V, node_t<K,V>, citrustree<K,V,RecManager>, RecManager, LOGICAL_DELETION_USAGE, false>(numProcesses, this, recordmgr))
#ifdef USE_DEBUGCOUNTERS
        , counters(new debugCounters(numProcesses))
#endif
        , NO_KEY(bigger_than_max_key)
        , NO_VALUE(_NO_VALUE)
{
//    cout<<"IN CONSTRUCTOR: NO_VALUE="<<NO_VALUE<<" AND _NO_VALUE="<<_NO_VALUE<<endl;
    const int tid = 0;
    initThread(tid);
    // finish initializing RCU

#if 1
    // need to simulate real insertion of root and rootchild,
    // since range queries will actually try to add these nodes,
    // and we don't want blocking rq providers to spin forever
    // waiting for their itimes to be set to a positive number.
    nodeptr _rootchild = newNode(tid, NO_KEY, NO_VALUE);
    nodeptr _root = newNode(tid, NO_KEY, NO_VALUE);
    rqProvider->write_addr(tid, &_root->child[0], _rootchild);
    nodeptr insertedNodes[] = {_root, _rootchild, NULL};
    nodeptr deletedNodes[] = {NULL};
    root = NULL; // to prevent reading from uninitialized root pointer in the following call (which, depending on the rq provider, may read root, e.g., to perform a cas)
    rqProvider->linearize_update_at_write(tid, &root, _root, insertedNodes, deletedNodes);
#else
    root = newNode(tid, NO_KEY, NO_VALUE);
    rqProvider->write_addr(tid, &root->child[0], newNode(tid, NO_KEY, NO_VALUE));
#endif
}

template <typename K, typename V, class RecManager>
citrustree<K,V,RecManager>::~citrustree() {
    int numNodes = 0;
    dfsDeallocateBottomUp(root, &numNodes);
    VERBOSE DEBUG COUTATOMIC(" deallocated nodes "<<numNodes<<endl);
    delete rqProvider;
    recordmgr->printStatus();
    delete recordmgr;
#ifdef USE_DEBUGCOUNTERS
    delete counters;
#endif
}

template <typename K, typename V, class RecManager>
const pair<V, bool> citrustree<K,V,RecManager>::find(const int tid, const K& key) {
    recordmgr->leaveQuiescentState(tid, true);
    readLock();
    nodeptr curr = rqProvider->read_addr(tid, &root->child[0]);
    K ckey = curr->key;
    while (curr != NULL && ckey != key) {
        if (ckey > key)
            curr = rqProvider->read_addr(tid, &curr->child[0]);
        if (ckey < key)
            curr = rqProvider->read_addr(tid, &curr->child[1]);
        if (curr != NULL)
            ckey = curr->key;
    }
    readUnlock();
    if (curr == NULL) {
        recordmgr->enterQuiescentState(tid);
        return pair<V, bool>(NO_VALUE, false);
    }
    V result = curr->value;
    recordmgr->enterQuiescentState(tid);
    return pair<V, bool>(result, true);
}

template <typename K, typename V, class RecManager>
bool citrustree<K,V,RecManager>::contains(const int tid, const K& key) {
    return find(tid, key).second;
}

template <typename K, typename V, class RecManager>
bool citrustree<K,V,RecManager>::validate(const int tid, nodeptr prev, int tag, nodeptr curr, int direction) {
    if (curr == NULL) {
        return (!prev->marked && (rqProvider->read_addr(tid, &prev->child[direction]) == curr) && (prev->tag[direction] == tag));
    } else {
        return (!prev->marked && !curr->marked && rqProvider->read_addr(tid, &prev->child[direction]) == curr);
    }
}



#define SEARCH \
        prev = root;\
        curr = rqProvider->read_addr(tid, &root->child[0]);\
        direction = 0;\
        ckey = curr->key;\
        while (curr != NULL && ckey != key) {\
            prev = curr;\
            if (ckey > key) {\
                curr = rqProvider->read_addr(tid, &curr->child[0]);\
                direction = 0;\
            }\
            if (ckey < key) {\
                curr = rqProvider->read_addr(tid, &curr->child[1]);\
                direction = 1;\
            }\
            if (curr != NULL)\
                ckey = curr->key;\
        }

template <typename K, typename V, class RecManager>
const V citrustree<K,V,RecManager>::doInsert(const int tid, const K& key, const V& value, bool onlyIfAbsent) {
    nodeptr prev;
    nodeptr curr;
    int direction;
    K ckey;
    int tag;
    
    
retry:
    recordmgr->leaveQuiescentState(tid);
    readLock();
    SEARCH;
    tag = prev->tag[direction];
    readUnlock();
    if (curr != NULL) {
        if (onlyIfAbsent) {
            V result = curr->value;
            recordmgr->enterQuiescentState(tid);
            assert(result != NO_VALUE);
            return result;
        } else {
            acquireLock(&(prev->lock));
            if (validate(tid, prev, tag, curr, direction)) {
                acquireLock(&(curr->lock));
                curr->marked = true;
                nodeptr oldnode = rqProvider->read_addr(tid, &prev->child[direction]);
                nodeptr nnode = newNode(tid, key, value);
                rqProvider->write_addr(tid, &nnode->child[0], rqProvider->read_addr(tid, &curr->child[0]));
                rqProvider->write_addr(tid, &nnode->child[1], rqProvider->read_addr(tid, &curr->child[1]));

                nodeptr insertedNodes[] = {nnode, NULL};
                nodeptr deletedNodes[] = {oldnode, NULL};
                rqProvider->linearize_update_at_write(tid, &prev->child[direction], nnode, insertedNodes, deletedNodes);

                V result = oldnode->value;

                releaseLock(&(curr->lock));
                releaseLock(&(prev->lock));
                recordmgr->enterQuiescentState(tid);
                assert(result != NO_VALUE);
                return result;
            }
            releaseLock(&(prev->lock));
            recordmgr->enterQuiescentState(tid);
            goto retry;
        }
    }

    acquireLock(&(prev->lock));
    if (validate(tid, prev, tag, curr, direction)) {
        nodeptr nnode = newNode(tid, key, value);

        nodeptr insertedNodes[] = {nnode, NULL};
        nodeptr deletedNodes[] = {NULL};
        rqProvider->linearize_update_at_write(tid, &prev->child[direction], nnode, insertedNodes, deletedNodes);
        
        releaseLock(&(prev->lock));
        recordmgr->enterQuiescentState(tid);
        return NO_VALUE;
    } else {
        releaseLock(&(prev->lock));
        recordmgr->enterQuiescentState(tid);
        goto retry;
    }
}

template<class K, class V, class RecManager>
const V citrustree<K,V,RecManager>::insertIfAbsent(const int tid, const K& key, const V& val) {
    return doInsert(tid, key, val, true);
}

template<class K, class V, class RecManager>
const V citrustree<K,V,RecManager>::insert(const int tid, const K& key, const V& val) {
    return doInsert(tid, key, val, false);
}

template <typename K, typename V, class RecManager>
const pair<V, bool> citrustree<K,V,RecManager>::erase(const int tid, const K& key) {
    nodeptr prev;
    nodeptr curr;
    int direction;
    K ckey;
    
    nodeptr prevSucc;
    nodeptr succ;
    nodeptr next;
    nodeptr nnode;
    
    int min_bucket;
        
retry:
    recordmgr->leaveQuiescentState(tid);
    readLock();
    SEARCH;
    readUnlock();
    if (curr == NULL) {
        recordmgr->enterQuiescentState(tid);
        return pair<V, bool>(NO_VALUE, false);
    }
    acquireLock(&(prev->lock));
    acquireLock(&(curr->lock));
    if (!validate(tid, prev, 0, curr, direction)) {
        releaseLock(&(prev->lock));
        releaseLock(&(curr->lock));
        recordmgr->enterQuiescentState(tid);
        goto retry;
    }
    if (rqProvider->read_addr(tid, &curr->child[0]) == NULL) {
        curr->marked = true;

        nodeptr insertedNodes[] = {NULL};
        nodeptr deletedNodes[] = {curr, NULL};
        rqProvider->linearize_update_at_write(tid, &prev->child[direction], rqProvider->read_addr(tid, &curr->child[1]), insertedNodes, deletedNodes);

        if (rqProvider->read_addr(tid, &prev->child[direction]) == NULL) {
            prev->tag[direction]++;
        }
        
        V result = curr->value;
        
        releaseLock(&(prev->lock));
        releaseLock(&(curr->lock));
        recordmgr->enterQuiescentState(tid);
        return pair<V, bool>(result, true);
    }
    if (rqProvider->read_addr(tid, &curr->child[1]) == NULL) {
        curr->marked = true;

        nodeptr insertedNodes[] = {NULL};
        nodeptr deletedNodes[] = {curr, NULL};
        rqProvider->linearize_update_at_write(tid, &prev->child[direction], rqProvider->read_addr(tid, &curr->child[0]), insertedNodes, deletedNodes);

        if (rqProvider->read_addr(tid, &prev->child[direction]) == NULL) {
            prev->tag[direction]++;
        }

        V result = curr->value;
        
        releaseLock(&(prev->lock));
        releaseLock(&(curr->lock));
        recordmgr->enterQuiescentState(tid);
        return pair<K, bool>(result, true);
    }
    prevSucc = curr;
    succ = rqProvider->read_addr(tid, &curr->child[1]);
    next = rqProvider->read_addr(tid, &succ->child[0]);
    while (next != NULL) {
        prevSucc = succ;
        succ = next;
        next = rqProvider->read_addr(tid, &next->child[0]);
    }
    int succDirection = 1;
    if (prevSucc != curr) {
        acquireLock(&(prevSucc->lock));
        succDirection = 0;
    }
    acquireLock(&(succ->lock));
    if (validate(tid, prevSucc, 0, succ, succDirection) && validate(tid, succ, succ->tag[0], NULL, 0)) {
        curr->marked = true;
        nnode = newNode(tid, succ->key, succ->value);
        rqProvider->write_addr(tid, &nnode->child[0], rqProvider->read_addr(tid, &curr->child[0]));
        rqProvider->write_addr(tid, &nnode->child[1], rqProvider->read_addr(tid, &curr->child[1]));
        acquireLock(&(nnode->lock));

        nodeptr insertedNodes[] = {nnode, NULL};
        nodeptr deletedNodes[] = {curr, succ, NULL};
        rqProvider->linearize_update_at_write(tid, &prev->child[direction], nnode, insertedNodes, deletedNodes);
        
        synchronize();

        succ->marked = true;
        if (prevSucc == curr) {
            rqProvider->write_addr(tid, &nnode->child[1], rqProvider->read_addr(tid, &succ->child[1]));
            if (rqProvider->read_addr(tid, &nnode->child[1]) == NULL) {
                nnode->tag[1]++;
            }
        } else {
            rqProvider->write_addr(tid, &prevSucc->child[0], succ->child[1]);
            if (rqProvider->read_addr(tid, &prevSucc->child[0]) == NULL) {
                prevSucc->tag[0]++;
            }
        }
        
        V result = curr->value;
        
        releaseLock(&(prev->lock));
        releaseLock(&(nnode->lock));
        releaseLock(&(curr->lock));
        if (prevSucc != curr) releaseLock(&(prevSucc->lock));
        releaseLock(&(succ->lock));
        recordmgr->enterQuiescentState(tid);
        return pair<V, bool>(result, true);
    }
    releaseLock(&(prev->lock));
    releaseLock(&(curr->lock));
    if (prevSucc != curr) releaseLock(&(prevSucc->lock));
    releaseLock(&(succ->lock));
    recordmgr->enterQuiescentState(tid);
    goto retry;
}

template <typename K, typename V, class RecManager>
int citrustree<K,V,RecManager>::rangeQuery(const int tid, const K& lo, const K& hi, K * const resultKeys, V * const resultValues) {
    block<node_t<K,V> > stack (NULL);
    recordmgr->leaveQuiescentState(tid, true);
    rqProvider->traversal_start(tid);
    
    // depth first traversal (of interesting subtrees)
    int size = 0;
    stack.push(root);
    while (!stack.isEmpty()) {
        nodeptr node = stack.pop();
        
        // what (if anything) we need to do with CITRUS' validation function?
        // answer: nothing, because searches don't need to do anything with it.
        
        // check if we should add node's key to the traversal
        rqProvider->traversal_try_add(tid, node, resultKeys, resultValues, &size, lo, hi);

        // if internal node, explore its children
        nodeptr left = rqProvider->read_addr(tid, &node->child[0]);
        nodeptr right = rqProvider->read_addr(tid, &node->child[1]);
        if (left != NULL && lo < node->key) {
            stack.push(left);
        }
        if (right != NULL && hi > node->key) {
            stack.push(right);
        }
    }
    rqProvider->traversal_end(tid, resultKeys, resultValues, &size, lo, hi);
    recordmgr->enterQuiescentState(tid);
    return size;
}

template <typename K, typename V, class RecManager>
long long citrustree<K,V,RecManager>::debugKeySum(nodeptr root) {
    if (root == NULL) return 0;
    return root->key + debugKeySum(root->child[0]) + debugKeySum(root->child[1]);
}

template <typename K, typename V, class RecManager>
long long citrustree<K,V,RecManager>::debugKeySum() {
    return debugKeySum(root->child[0]->child[0]);
}

#endif