/* 
 * File:   rlu_citrus_impl.h
 * Author: Maya Arbel-Raviv
 *
 * Created on May 7, 2017, 10:09 AM
 */

#ifndef RLU_CITRUS_IMPL_H
#define RLU_CITRUS_IMPL_H

#include <stdlib.h>
#include <stdio.h>

#include "rlu.h"
#include "rlu_citrus.h"

#ifdef ADD_DELAY_BEFORE_DTIME
extern Random rngs[MAX_TID_POW2*PREFETCH_SIZE_WORDS];
#define GET_RAND(tid,n) (rngs[(tid)*PREFETCH_SIZE_WORDS].nextNatural((n)))
#define DELAY_UP_TO(n) { \
    unsigned __r = GET_RAND(tid,(n)); \
    for (int __i=0;__i<__r;++__i) { \
        SOFTWARE_BARRIER; \
    } \
}
#else
#define DELAY_UP_TO(n) 
#endif

extern __thread rlu_thread_data_t * rlu_self;

template<typename K, typename V>
class node_t {
public:
    K key;
    V val;
    nodeptr volatile child[2];
//    volatile char padding[24];
    //uint8_t padding[PREFETCH_SIZE_BYTES - sizeof (skey_t) - sizeof (sval_t) - sizeof (struct nodeptr) - sizeof (lock_type) - sizeof (uint8_t) ];
};

template <typename K, typename V>
rlucitrus<K,V>::rlucitrus(const int numProcesses, const K KEY_MAX, const V _NO_VALUE)
        :
#ifdef USE_DEBUGCOUNTERS
          counters(new debugCounters(numProcesses))
          ,
#endif
          NO_KEY(KEY_MAX)
        , NO_VALUE(_NO_VALUE) {
    const int tid = 0;
    root = new_node(tid,NO_KEY,0);
    root->child[0] = new_node(tid,NO_KEY,0);
}

template <typename K, typename V>
rlucitrus<K,V>::~rlucitrus() {
    // TODO
}

template <typename K, typename V>
nodeptr rlucitrus<K,V>::new_node(const int tid, const K& key, const V& val) {
    nodeptr p_new_node = (nodeptr)RLU_ALLOC(sizeof(node_t<K,V>));
    if (p_new_node == NULL){
        printf("out of memory\n");
	exit(1); 
    }  
    p_new_node->key=key;
    p_new_node->val=val; 
    p_new_node->child[0]=NULL;
    p_new_node->child[1]=NULL;
    
#ifdef __HANDLE_STATS
    GSTATS_APPEND(tid, node_allocated_addresses, ((long long) p_new_node)%(1<<12));
#endif
    return p_new_node;
}

template <typename K, typename V>
nodeptr rlucitrus<K,V>::copy_node(const int tid, nodeptr node_to_copy, const K& key, const V& val) {
    nodeptr p_new_node = (nodeptr)RLU_ALLOC(sizeof(node_t<K,V>));
    if (p_new_node == NULL){
        printf("out of memory\n");
	exit(1); 
    }  
    p_new_node->key=key;
    p_new_node->val=val; 
    p_new_node->child[0]=node_to_copy->child[0];
    p_new_node->child[1]=node_to_copy->child[1];
    
    return p_new_node;
}

//template <typename K, typename V>
//void rlucitrus<K,V>::free_node(const int tid, nodeptr p_node){
//	RLU_FREE(rlu_self, p_node);
//}

template <typename K, typename V>
const pair<V,bool> rlucitrus<K,V>::find(const int tid, const K& key) {
    K ckey;
    nodeptr curr;

    RLU_READER_LOCK(rlu_self);

    curr = (nodeptr)RLU_DEREF(rlu_self, root);	
    curr = (nodeptr)RLU_DEREF(rlu_self, curr->child[0]);
    ckey = curr->key ;

    while (curr != NULL && ckey != key){
        if (ckey > key) {
            curr = (nodeptr)RLU_DEREF(rlu_self, curr->child[0]);
        }
        if (ckey < key) {
            curr = (nodeptr)RLU_DEREF(rlu_self, curr->child[1]);
        }
        if (curr != NULL) {
            ckey = curr->key ;
        }
    }

    V result = NO_VALUE;
    if (curr) result = curr->val;
    RLU_READER_UNLOCK(rlu_self);
    return pair<V,bool>(result, curr != NULL);
}

template <typename K, typename V>
bool rlucitrus<K,V>::contains(const int tid, const K& key) {
    return find(tid, key).second;
}

template <typename K, typename V>
V rlucitrus<K,V>::doInsert(const int tid, const K& key, const V& val, bool onlyIfAbsent) {
    restart:
	RLU_READER_LOCK(rlu_self);

	nodeptr prev = (nodeptr)RLU_DEREF(rlu_self, root);
	nodeptr curr = (nodeptr)RLU_DEREF(rlu_self, prev->child[0]);
	int direction = 0;
	K ckey = curr->key;

	while (curr != NULL && ckey != key){
            prev = curr;
            if (ckey > key) {
                curr = (nodeptr)RLU_DEREF(rlu_self, curr->child[0]);
                direction = 0;
            }
            if (ckey < key) {
                curr = (nodeptr)RLU_DEREF(rlu_self, curr->child[1]);
                direction = 1;
            }
            if (curr != NULL) { 
                ckey = curr->key ;
            }
	}

	if (curr != NULL && onlyIfAbsent) {
            V result = curr->val;
            RLU_READER_UNLOCK(rlu_self);
            return result;
	}
	
	if (!RLU_TRY_LOCK(rlu_self, &prev)) {
            RLU_ABORT(rlu_self);
            goto restart;
	}
        
        if (curr != NULL) { // !onlyIfAbsent, so this is insert-replace
            if (!RLU_TRY_LOCK(rlu_self, &curr)) {
                RLU_ABORT(rlu_self);
                goto restart;
            }
            
            nodeptr nnode = copy_node(tid,curr,key,val);
            RLU_ASSIGN_PTR(rlu_self, &(prev->child[direction]), nnode);	
            V result = curr->val;
            RLU_FREE(rlu_self, curr);
            RLU_READER_UNLOCK(rlu_self);
            return result;
        }

	nodeptr nnode = new_node(tid,key,val);
	RLU_ASSIGN_PTR(rlu_self, &(prev->child[direction]), nnode);	
        assert(!curr);
	RLU_READER_UNLOCK(rlu_self);
	return NO_VALUE;
}

template<class K, class V>
V rlucitrus<K,V>::insertIfAbsent(const int tid, const K& key, const V& val) {
    return doInsert(tid, key, val, true);
}

template<class K, class V>
V rlucitrus<K,V>::insert(const int tid, const K& key, const V& val) {
    return doInsert(tid, key, val, false);
}

template <typename K, typename V>
const pair<V,bool> rlucitrus<K,V>::erase(const int tid, const K& key) {
    restart:
	RLU_READER_LOCK(rlu_self);	
    
        nodeptr prev = (nodeptr)RLU_DEREF(rlu_self, root);
	nodeptr curr = (nodeptr)RLU_DEREF(rlu_self, prev->child[0]);
	int direction = 0;
	K ckey = curr->key;
	while (curr != NULL && ckey != key){
            prev = curr;
            if (ckey > key) {
                curr = (nodeptr)RLU_DEREF(rlu_self, curr->child[0]);
                direction = 0;
            }
            if (ckey < key) {
                curr = (nodeptr)RLU_DEREF(rlu_self, curr->child[1]);
                direction = 1;
            }
            if (curr != NULL) { 
                ckey = curr->key;
            }
	}
	
	if (curr == NULL){
            RLU_READER_UNLOCK(rlu_self);	
            return pair<V,bool>(NO_VALUE, false);
	}
	
	if (!RLU_TRY_LOCK(rlu_self, &prev)) {
            RLU_ABORT(rlu_self);
            goto restart;
	}
	if (!RLU_TRY_LOCK(rlu_self, &curr)) {
            RLU_ABORT(rlu_self);
            goto restart;
	}
	
	if (curr->child[0] == NULL) {
            RLU_ASSIGN_PTR(rlu_self, &(prev->child[direction]), curr->child[1]);
            V result = curr->val;
            RLU_FREE(rlu_self, curr);
            RLU_READER_UNLOCK(rlu_self);
            return pair<V,bool>(result, true);
	}
	
	if (curr->child[1] == NULL) {
            RLU_ASSIGN_PTR(rlu_self, &(prev->child[direction]), curr->child[0]);
            V result = curr->val;
            RLU_FREE(rlu_self, curr);
            RLU_READER_UNLOCK(rlu_self);
            return pair<V,bool>(result, true);
	}
	
	nodeptr prevSucc = curr;
	nodeptr succ = (nodeptr)RLU_DEREF(rlu_self, curr->child[1]); 
	nodeptr next = (nodeptr)RLU_DEREF(rlu_self, succ->child[0]);
	while (next != NULL) {
            prevSucc = succ;
            succ = next;
            next = (nodeptr)RLU_DEREF(rlu_self, next->child[0]);
	}		
	
	if (!RLU_TRY_LOCK(rlu_self, &succ)) {
            RLU_ABORT(rlu_self);
            goto restart;
	}
	
	if (RLU_IS_SAME_PTRS(prevSucc, curr)) {	
            RLU_ASSIGN_PTR(rlu_self, &(succ->child[0]), curr->child[0]);
		
	} else {
//            DELAY_UP_TO(100000);
            if (!RLU_TRY_LOCK(rlu_self, &prevSucc)) {
                RLU_ABORT(rlu_self);
                goto restart;
            }
		
            RLU_ASSIGN_PTR(rlu_self, &(prevSucc->child[0]), succ->child[1]);	
            RLU_ASSIGN_PTR(rlu_self, &(succ->child[0]), curr->child[0]);
            RLU_ASSIGN_PTR(rlu_self, &(succ->child[1]), curr->child[1]);
	}
	
	RLU_ASSIGN_PTR(rlu_self, &(prev->child[direction]), succ);
        V result = curr->val;
	RLU_FREE(rlu_self, curr);
        RLU_READER_UNLOCK(rlu_self);
        return pair<V,bool>(result, true);
}

template <typename K, typename V>
int rlucitrus<K,V>::rangeQuery(const int tid, const K& lo, const K& hi, K * const resultKeys, V * const resultValues) {
    block<node_t<K,V> > stack (NULL);
    RLU_READER_LOCK(rlu_self);
    
    // depth first traversal (of interesting subtrees)
    int size = 0;
    stack.push((nodeptr) RLU_DEREF(rlu_self, root));
    while (!stack.isEmpty()) {
        nodeptr node = stack.pop();
        
        // check if we should add node's key to the traversal
        if (lo <= node->key && node->key <= hi) {
            resultKeys[size] = node->key;
            resultValues[size] = node->val;
            ++size;
        }

        // if internal node, explore its children
        nodeptr left = (nodeptr) RLU_DEREF(rlu_self, node->child[0]);
        nodeptr right = (nodeptr) RLU_DEREF(rlu_self, node->child[1]);
        if (left != NULL && lo < node->key) {
            stack.push(left);
        }
        if (right != NULL && hi > node->key) {
            stack.push(right);
        }
    }
    RLU_READER_UNLOCK(rlu_self);
    return size;
}

//template <typename K, typename V>
//int rlucitrus<K,V>::rangeQuery(const int tid, const K& lo, const K& hi, K * const resultKeys, V * const resultValues) {
//    int cnt = 0;
//    RLU_READER_LOCK(rlu_self);
//    nodeptr p_node = (nodeptr) RLU_DEREF(rlu_self, root);
//    cnt = rangeQuery(tid, p_node, cnt, lo, hi, resultKeys, resultValues);
//    RLU_READER_UNLOCK(rlu_self);
//    return cnt;
//}
//
//template <typename K, typename V>
//int rlucitrus<K,V>::rangeQuery(const int tid, nodeptr p_node, int cnt, const K& lo, const K& hi, K * const resultKeys, V * const resultValues) {
//    int count = cnt;
//    if (p_node != NULL) {
//        int key = p_node->key;
//        if (lo <= key && key <= hi) {
//            resultKeys[count] = key;
//            resultValues[count] = p_node->val;
//            ++count;
//        }
//        if (lo < key) {
//            nodeptr p_left = (nodeptr) RLU_DEREF(rlu_self, p_node->child[0]);
//            count = rangeQuery(tid, p_left, count, lo, hi, resultKeys, resultValues);
//        }
//        if (key < hi) {
//            nodeptr p_right = (nodeptr) RLU_DEREF(rlu_self, p_node->child[1]);
//            count = rangeQuery(tid, p_right, count, lo, hi, resultKeys, resultValues);
//        }
//    }
//    return count;
//}

template <typename K, typename V>
long long rlucitrus<K,V>::getSize(nodeptr p_node) {
    long long size = 0;
		
    if (p_node != NULL) {
            size += getSize(p_node->child[0]);
            size += getSize(p_node->child[1]);
            
            if (p_node->key != NO_KEY) {
                size += 1;
            }
    }
    return size;	  
}

template <typename K, typename V>
long long rlucitrus<K,V>::debugKeySum(nodeptr p_node) {
    long long sum = 0;
    
    if (p_node != NULL) {
        sum += debugKeySum(p_node->child[0]);
        sum += debugKeySum(p_node->child[1]);

        if (p_node->key != NO_KEY) {
            sum += p_node->key;
        }
    }
    return sum;
}

template <typename K, typename V>
long long rlucitrus<K,V>::debugKeySum() {
    return debugKeySum(root);
}

#endif /* RLU_CITRUS_IMPL_H */

