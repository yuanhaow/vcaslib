/* 
 * File:   rlu_list.h
 * Based on 
 * https://github.com/rlu-sync/rlu/blob/master/hash-list.h (a1e7e9e  on Aug 23, 2015)
 *
 * Created on April 26, 2017, 10:58 AM
 */

#ifndef RLU_LIST_H
#define RLU_LIST_H

#include <limits>
using namespace std;

template <typename K, typename V>
class node_t;
#define nodeptr node_t<K,V> *

template <typename K, typename V>
class rlulist {
private:
#ifdef USE_DEBUGCOUNTERS
    debugCounters * const counters;
#endif
    const int NUM_PROCESSES;
    nodeptr head;

    nodeptr new_node(const int tid);
    void free_node(const int tid, nodeptr p_node);
    long long debugKeySum(nodeptr head);

    V doInsert(const int tid, const K& key, const V& value, bool onlyIfAbsent);
    
public:
    const K MAX_KEY;
    const V NO_VALUE;
    rlulist(int numProcesses, const K _MIN_KEY, const K _MAX_KEY, const V _NO_VALUE);
    ~rlulist();
    bool contains(const int tid, const K& key);
    V insert(const int tid, const K& key, const V& value) {
        return doInsert(tid, key, value, false);
    }
    V insertIfAbsent(const int tid, const K& key, const V& value) {
        return doInsert(tid, key, value, true);
    }
    const pair<V,bool> erase(const int tid, const K& key);
    int rangeQuery(const int tid, int lo, int hi, K * const resultKeys, V * const resultValues);
    
    /**
     * This function must be called once by each thread that will
     * invoke any functions on this class.
     * 
     * It must be okay that we do this with the main thread and later with another thread!!!
     */
#ifdef USE_DEBUGCOUNTERS
    debugCounters * debugGetCounters() { return counters; }
    void clearCounters() { counters->clear(); }
#endif
    long long debugKeySum();
    bool validate(const long long keysum, const bool checkkeysum) {
        return true;
    }
    long long getSize() {
        long long sum = 0;
        for (nodeptr curr = head->p_next; curr->key != MAX_KEY; curr = curr->p_next) {
            ++sum;
        }
        return sum;
    }
    
    long long getSizeInNodes() {
        long long size = 0;
        nodeptr curr = head->p_next;
        while (curr->key != MAX_KEY) {
            curr = curr->p_next;
            ++size;
        }
        return size;
    }
    string getSizeString() {
        stringstream ss;
        ss<<getSizeInNodes()<<" nodes in data structure";
        return ss.str();
    }
    
    void * const debugGetRecMgr() {
        return NULL;
    }
    
    inline int getKeys(const int tid, node_t<K,V> * node, K * const outputKeys, V * const outputValues){
        //ignore marked
        outputKeys[0] = node->key;
        outputValues[0] = node->val;
        return 1;
    }
    
    bool isInRange(const K& key, const K& lo, const K& hi) {
        return (lo <= key && key <= hi);
    }
    inline bool isLogicallyDeleted(const int tid, node_t<K,V> * node){return false;}

    node_t<K,V> * debug_getEntryPoint() { return head; }
};

#endif /* RLU_LIST_H */

