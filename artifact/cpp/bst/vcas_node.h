
#ifndef VCAS_NODE_H
#define	VCAS_NODE_H

#include <iostream>
#include <iomanip>
#include <atomic>
#include <set>
#include "vcas_scxrecord.h"
#include "rq_provider.h"
#ifdef USE_RECLAIMER_RCU
#include <urcu.h>
#define RECLAIM_RCU_RCUHEAD_DEFN struct rcu_head rcuHeadField
#else
#define RECLAIM_RCU_RCUHEAD_DEFN 
#endif
using namespace std;

namespace vcas_bst_ns {

    #define nodeptr Node<K,V> * volatile

    template <class K, class V>
    class Node {
    public:
        K key;
        V value;
        atomic_uintptr_t scxRecord;
        atomic_bool marked; // might be able to combine this elegantly with scx record pointer... (maybe we can piggyback on the version number mechanism, using the same bit to indicate ver# OR marked)
        // vcas_nodeptr<K,V> left;
        // vcas_nodeptr<K,V> right;
        nodeptr left;
        nodeptr right;
        RECLAIM_RCU_RCUHEAD_DEFN;

        volatile long long ts;
        nodeptr nextv;

        Node() {}
        Node(const Node& node) {}

        friend ostream& operator<<(ostream& os, const Node<K,V>& obj) {}
        void printTreeFile(ostream& os) {}
        void printTreeFileWeight(ostream& os, set< Node<K,V>* > *seen) {}
        void printTreeFileWeight(ostream& os) {}
    };
}

#endif	/* NODE_H */

