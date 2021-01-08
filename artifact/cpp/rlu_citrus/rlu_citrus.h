/* 
 * File:   rlu_citrus.h
 * Author: Maya Arbel-Raviv
 *
 * Created on May 7, 2017, 10:02 AM
 */

#ifndef RLU_CITRUS_H
#define RLU_CITRUS_H

template <typename K, typename V>
class node_t;
#define nodeptr node_t<K,V> *

template <typename K, typename V>
class rlucitrus {
private:
#ifdef USE_DEBUGCOUNTERS
    debugCounters * const counters;
#endif
    
    nodeptr root;
    
    nodeptr new_node(const int tid, const K& key, const V& val);
    nodeptr copy_node(const int tid, nodeptr node_to_copy, const K& key, const V& val);
    void free_node(const int tid, nodeptr p_node);
    long long debugKeySum(nodeptr head);
    long long getSize(nodeptr p_node);
//    int rangeQuery(const int tid, nodeptr p_node, int cnt, const K& lo, const K& hi, K * const resultKeys, V * const resultValues);
    V doInsert(const int tid, const K& key, const V& val, bool onlyIfAbsent);
public:
    const K NO_KEY;
    const V NO_VALUE;
    rlucitrus(int numProcesses, const K NO_KEY, const V NO_VALUE);
    ~rlucitrus();
    bool contains(const int tid, const K& key);
    V insert(const int tid, const K& key, const V& value);
    V insertIfAbsent(const int tid, const K& key, const V& value);
    const pair<V,bool> find(const int tid, const K& key);
    const pair<V,bool> erase(const int tid, const K& key);
    int rangeQuery(const int tid, const K& lo, const K& hi, K * const resultKeys, V * const resultValues);
    
    node_t<K,V> * debug_getEntryPoint() { return root; }
    
    void initThread(const int tid) {}
    void deinitThread(const int tid) {}
    
#ifdef USE_DEBUGCOUNTERS
    debugCounters * debugGetCounters() { return counters; }
    void clearCounters() { counters->clear(); }
#endif
    long long debugKeySum();
    bool validate(const long long keysum, const bool checkkeysum) {
        //TODO
        return true;
    }
    long long getSize() {
        return getSize(root);
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
    void * const debugGetRecMgr() {
        return NULL;
    }
};


#endif /* RLU_CITRUS_H */

