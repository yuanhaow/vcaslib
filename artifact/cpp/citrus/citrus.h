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

#ifndef _DICTIONARY_H_
#define _DICTIONARY_H_

#include <stdbool.h>
#include <utility>
#include <signal.h>

#ifndef MAX_NODES_INSERTED_OR_DELETED_ATOMICALLY
    // define BEFORE including rq_provider.h
    #define MAX_NODES_INSERTED_OR_DELETED_ATOMICALLY 4
#endif
#include "rq_provider.h"
using namespace std;

#define LOGICAL_DELETION_USAGE false

//#define INSERT_REPLACE

template <typename K, typename V>
struct node_t {
    K key;
    V value;
    node_t<K,V> * volatile child[2];
    volatile long long itime; // for use by range query algorithm
    volatile long long dtime; // for use by range query algorithm
    int tag[2];
    volatile int lock;
    bool marked;
};

#define nodeptr node_t<K,V> *

template <typename K, typename V, class RecManager>
class citrustree {
private:
    RecManager * const recordmgr;
    RQProvider<K, V, node_t<K,V>, citrustree<K,V,RecManager>, RecManager, LOGICAL_DELETION_USAGE, false> * const rqProvider;
    
    volatile char padding0[PREFETCH_SIZE_BYTES];
    nodeptr root;
    volatile char padding1[PREFETCH_SIZE_BYTES];
    
#ifdef USE_DEBUGCOUNTERS
    debugCounters * const counters;
#endif
    
    inline nodeptr newNode(const int tid, K key, V value);
    long long debugKeySum(nodeptr root);
    
    bool validate(const int tid, nodeptr prev, int tag, nodeptr curr, int direction);

    void dfsDeallocateBottomUp(nodeptr const u, int * numNodes) {
        if (u == NULL) return;
        if (u->child[0]) dfsDeallocateBottomUp(u->child[0], numNodes);
        if (u->child[1]) dfsDeallocateBottomUp(u->child[1], numNodes);
        MEMORY_STATS ++(*numNodes);
        recordmgr->deallocate(0 /* tid */, u);
    }
    
    const V doInsert(const int tid, const K& key, const V& value, bool onlyIfAbsent);
    int init[MAX_TID_POW2] = {0,};

public:
    const K NO_KEY;
    const V NO_VALUE;
    citrustree(const K max_key, const V NO_VALUE, int numProcesses);
    ~citrustree();

    const V insert(const int tid, const K& key, const V& value);
    const V insertIfAbsent(const int tid, const K& key, const V& value);
    const pair<V, bool> erase(const int tid, const K& key);
    const pair<V, bool> find(const int tid, const K& key);
    int rangeQuery(const int tid, const K& lo, const K& hi, K * const resultKeys, V * const resultValues);
    bool contains(const int tid, const K& key);
    int size(); // warning: this is a linear time operation, and is not linearizable

    node_t<K,V> * debug_getEntryPoint() { return root; }
    
    /**
     * BEGIN FUNCTIONS FOR RANGE QUERY SUPPORT
     */
        
    inline bool isLogicallyDeleted(const int tid, nodeptr node) {
        return false;
    }
    
    inline bool isLogicallyInserted(const int tid, nodeptr node) {
        return true;
    }

    inline int getKeys(const int tid, node_t<K,V> * node, K * const outputKeys, V * const outputValues) {
        if (node->key >= NO_KEY) return 0;
        outputKeys[0] = node->key;
        outputValues[0] = node->value;
        return 1;
    }
    
    bool isInRange(const K& key, const K& lo, const K& hi) {
        return (key != NO_KEY && lo <= key && key <= hi);
    }

    /**
     * END FUNCTIONS FOR RANGE QUERY SUPPORT
     */
#ifdef USE_DEBUGCOUNTERS
    debugCounters * debugGetCounters() { return counters; }
#endif
    long long debugKeySum();
    void clearCounters() {
#ifdef USE_DEBUGCOUNTERS
        counters->clear();
#endif
        //recmgr->clearCounters();
}
    
    bool validate(int unused, bool unused2) {
        return true;
    }
    
    RecManager * const debugGetRecMgr() {
        return recordmgr;
    }
    
    long long getSizeInNodes(nodeptr const u) {
        if (u == NULL) return 0;
        return 1 + getSizeInNodes(u->child[0])
                 + getSizeInNodes(u->child[1]);
    }
    long long getSizeInNodes() {
        return getSizeInNodes(root);
    }
    string getSizeString() {
        stringstream ss;
        ss<<getSizeInNodes()<<" nodes in data structure";
        return ss.str();
    }
    long long getSize() {
        return getSizeInNodes();
    }
    
    /**
     * This function must be called once by each thread that will
     * invoke any functions on this class.
     * 
     * It must be okay that we do this with the main thread and later with another thread!!!
     */
    void initThread(const int tid) {
        if (init[tid]) return; else init[tid] = !init[tid];

        recordmgr->initThread(tid);
        rqProvider->initThread(tid);
    }
    
    void deinitThread(const int tid) {
        if (!init[tid]) return; else init[tid] = !init[tid];

        rqProvider->deinitThread(tid);
        recordmgr->deinitThread(tid);
    }    
};

#endif
