/**
 * Preliminary C++ implementation of binary search tree using LLX/SCX.
 * 
 * Copyright (C) 2014 Trevor Brown
 * 
 */

#ifndef NODE_H
#define	NODE_H

#include <iostream>
#include <iomanip>
#include <atomic>
#include <set>
#include "scxrecord.h"
#include "rq_provider.h"
#ifdef USE_RECLAIMER_RCU
#include <urcu.h>
#define RECLAIM_RCU_RCUHEAD_DEFN struct rcu_head rcuHeadField
#else
#define RECLAIM_RCU_RCUHEAD_DEFN 
#endif
using namespace std;

namespace bst_ns {

    #define nodeptr Node<K,V> * volatile

    #if defined(RQ_UNSAFE)
        template <class K, class V>
        class Node {
        public:
            K key;
            V value;
            atomic_uintptr_t scxRecord;
            atomic_bool marked; // might be able to combine this elegantly with scx record pointer... (maybe we can piggyback on the version number mechanism, using the same bit to indicate ver# OR marked)
            nodeptr left;
            nodeptr right;
            // volatile long long itime; // for use by range query algorithm
            // volatile long long dtime; // for use by range query algorithm
            RECLAIM_RCU_RCUHEAD_DEFN;

            Node() {}
            Node(const Node& node) {}

            friend ostream& operator<<(ostream& os, const Node<K,V>& obj) {}
            void printTreeFile(ostream& os) {}
            void printTreeFileWeight(ostream& os, set< Node<K,V>* > *seen) {}
            void printTreeFileWeight(ostream& os) {}

        };
    #else
        template <class K, class V>
        class Node {
        public:
            V value;
            K key;
            atomic_uintptr_t scxRecord;
            atomic_bool marked; // might be able to combine this elegantly with scx record pointer... (maybe we can piggyback on the version number mechanism, using the same bit to indicate ver# OR marked)
            nodeptr left;
            nodeptr right;
            volatile long long itime; // for use by range query algorithm
            volatile long long dtime; // for use by range query algorithm
            RECLAIM_RCU_RCUHEAD_DEFN;

            Node() {}
            Node(const Node& node) {}

            friend ostream& operator<<(ostream& os, const Node<K,V>& obj) {}
            void printTreeFile(ostream& os) {}
            void printTreeFileWeight(ostream& os, set< Node<K,V>* > *seen) {}
            void printTreeFileWeight(ostream& os) {}

        };
    #endif

}

#endif	/* NODE_H */

