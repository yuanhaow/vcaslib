
#include "vcas_bst.h"
#include <cassert>
#include <cstdlib>
using namespace std;

#ifdef NOREBALANCING
#define IFREBALANCING if (0)
#else
#define IFREBALANCING if (1)
#endif

template<class K, class V, class Compare, class RecManager>
vcas_bst_ns::Node<K,V>* vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::allocateNode(const int tid) {
    //this->recmgr->getDebugInfo(NULL)->addToPool(tid, 1);
    Node<K,V> *newnode = recmgr->template allocate<Node<K,V> >(tid);
    if (newnode == NULL) {
        COUTATOMICTID("ERROR: could not allocate node"<<endl);
        exit(-1);
    }
#ifdef __HANDLE_STATS
    GSTATS_APPEND(tid, node_allocated_addresses, ((long long) newnode)%(1<<12));
#endif
    return newnode;
}

template<class K, class V, class Compare, class RecManager>
long long vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::debugKeySum(Node<K,V> * node) {
    if (node == NULL) return 0;
    if ((void*) node->left == NULL) return (long long) node->key;
    return debugKeySum(node->left)
         + debugKeySum(node->right);
}

template<class K, class V, class Compare, class RecManager>
bool vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::validate(Node<K,V> * const node, const int currdepth, const int leafdepth) {
    return true;
}

template<class K, class V, class Compare, class RecManager>
bool vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::validate(const long long keysum, const bool checkkeysum) {
    return true;
}

template<class K, class V, class Compare, class RecManager>
inline int vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::size() {
    return computeSize((root->left)->left);
}
    
template<class K, class V, class Compare, class RecManager>
inline int vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::computeSize(Node<K,V> * const root) {
    if (root == NULL) return 0;
    if (root->left != NULL) { // if internal node
        return computeSize(root->left)
                + computeSize(root->right);
    } else { // if leaf
        return 1;
//        printf(" %d", root->key);
    }
}

template<class K, class V, class Compare, class RecManager>
bool vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::contains(const int tid, const K& key) {
    pair<V,bool> result = find(tid, key);
    return result.second;
}

// long long sum_sizes = 0;

template<class K, class V, class Compare, class RecManager>
int vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::rangeQuery(const int tid, const K& lo, const K& hi, K * const resultKeys, V * const resultValues) {
    block<Node<K,V> > stack (NULL);
    recmgr->leaveQuiescentState(tid, true);
    long long ts = rqProvider->traversal_start(tid);
    // volatile long long sum = 0;
    // for(int i = 0; i < 500000; i++)
    //     sum += i;
    // depth first traversal (of interesting subtrees)
    int size = 0;
    stack.push(root);
    while (!stack.isEmpty()) {
        Node<K,V> * node = stack.pop();
        assert(node);
        Node<K,V> * left = rqProvider->read_addr(tid, &node->left, ts);

        #if defined(VCAS_STATS)
            // if(nodesSeen.find(node) == nodesSeen.end())
            {
                // nodesSeen.insert(node);
                distinctNodesVisited[tid*PADDING]++;
            }
        #endif

        // if internal node, explore its children
        if (left != NULL) {
            if (node->key != this->NO_KEY && !cmp(hi, node->key)) {
                Node<K,V> * right = rqProvider->read_addr(tid, &node->right, ts);
                assert(right);
                stack.push(right);
            }
            if (node->key == this->NO_KEY || cmp(lo, node->key)) {
                assert(left);
                stack.push(left);
            }
            else {
                #if defined(VCAS_STATS)
                    // if(nodesSeen.find(node) == nodesSeen.end())
                    {
                        // nodesSeen.insert(node);
                        distinctNodesVisited[tid*PADDING]++;
                    }
                #endif
            }
            
        // else if leaf node, check if we should add its key to the traversal
        } else {
            rqProvider->traversal_try_add(tid, node, resultKeys, resultValues, &size, lo, hi, ts);
        }
    }

    rqProvider->traversal_end(tid, resultKeys, resultValues, &size, lo, hi);
    recmgr->enterQuiescentState(tid);
    // sum_sizes+=size;
    return size;
}

template<class K, class V, class Compare, class RecManager>
const pair<V,bool> vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::find(const int tid, const K& key) {
    pair<V,bool> result;
    Node<K,V> *p;
    Node<K,V> *l;
    for (;;) {
        TRACE COUTATOMICTID("find(tid="<<tid<<" key="<<key<<")"<<endl);
        recmgr->leaveQuiescentState(tid, true);
        p = rqProvider->read_addr(tid, &root->left);
        l = rqProvider->read_addr(tid, &p->left);
        if (l == NULL) {
            result = pair<V,bool>(NO_VALUE, false); // no keys in data structure
            recmgr->enterQuiescentState(tid);
            return result; // success
        }

        while (rqProvider->read_addr(tid, &l->left) != NULL) {
            TRACE COUTATOMICTID("traversing tree; l="<<*l<<endl);
            p = l; // note: the new p is currently protected
            assert(p->key != NO_KEY);
            if (cmp(key, p->key)) {
                l = rqProvider->read_addr(tid, &p->left);
            } else {
                l = rqProvider->read_addr(tid, &p->right);
            }
        }
        if (key == l->key) {
            result = pair<V,bool>(l->value, true);
        } else {
            result = pair<V,bool>(NO_VALUE, false);
        }
        recmgr->enterQuiescentState(tid);
        return result; // success
    }
    assert(0);
    return pair<V,bool>(NO_VALUE, false);
}

//template<class K, class V, class Compare, class RecManager>
//const V vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::insert(const int tid, const K& key, const V& val) {
//    bool onlyIfAbsent = false;
//    V result = NO_VALUE;
//    void *input[] = {(void*) &key, (void*) &val, (void*) &onlyIfAbsent};
//    void *output[] = {(void*) &result};
//
//    ReclamationInfo<K,V> info;
//    bool finished = 0;
//    for (;;) {
//        recmgr->leaveQuiescentState(tid);
//        finished = updateInsert_search_llx_scx(&info, tid, input, output);
//        recmgr->enterQuiescentState(tid);
//        if (finished) {
//            break;
//        }
//    }
//    return result;
//}

template<class K, class V, class Compare, class RecManager>
const V vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::doInsert(const int tid, const K& key, const V& val, bool onlyIfAbsent) {
    V result = NO_VALUE;
    void *input[] = {(void*) &key, (void*) &val, (void*) &onlyIfAbsent};
    void *output[] = {(void*) &result};

    ReclamationInfo<K,V> info;
    bool finished = 0;
    for (;;) {
        recmgr->leaveQuiescentState(tid);
        finished = updateInsert_search_llx_scx(&info, tid, input, output);
        recmgr->enterQuiescentState(tid);
        if (finished) {
            break;
        }
    }
    return result;
}

template<class K, class V, class Compare, class RecManager>
const V vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::insertIfAbsent(const int tid, const K& key, const V& val) {
    return doInsert(tid, key, val, true);
}

template<class K, class V, class Compare, class RecManager>
const V vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::insert(const int tid, const K& key, const V& val) {
    return doInsert(tid, key, val, false);
}

template<class K, class V, class Compare, class RecManager>
const pair<V,bool> vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::erase(const int tid, const K& key) {
    V result = NO_VALUE;
    void *input[] = {(void*) &key};
    void *output[] = {(void*) &result};
    
    ReclamationInfo<K,V> info;
    bool finished = 0;
    for (;;) {
        recmgr->leaveQuiescentState(tid);
        finished = updateErase_search_llx_scx(&info, tid, input, output);
        recmgr->enterQuiescentState(tid);
        if (finished) {
            break;
        }
    }
    return pair<V,bool>(result, (result != NO_VALUE));
}

template<class K, class V, class Compare, class RecManager>
inline bool vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::updateInsert_search_llx_scx(
            ReclamationInfo<K,V> * const info, const int tid, void **input, void **output) {
    const K& key = *((const K*) input[0]);
    const V& val = *((const V*) input[1]);
    const bool onlyIfAbsent = *((const bool*) input[2]);
    V *result = (V*) output[0];
    
    TRACE COUTATOMICTID("updateInsert_search_llx_scx(tid="<<tid<<", key="<<key<<")"<<endl);
    
    Node<K,V> *p = root, *l;
    l = rqProvider->read_addr(tid, &root->left);
    if (rqProvider->read_addr(tid, &l->left) != NULL) { // the tree contains some node besides sentinels...
        p = l;
        l = rqProvider->read_addr(tid, &l->left);    // note: l must have key infinity, and l->left must not.
        while (rqProvider->read_addr(tid, &l->left) != NULL) {
            p = l;
            if (cmp(key, p->key)) {
                l = rqProvider->read_addr(tid, &p->left);
            } else {
                l = rqProvider->read_addr(tid, &p->right);
            }
        }
    }
    // if we find the key in the tree already
    if (key == l->key) {
        if (onlyIfAbsent) {
            TRACE COUTATOMICTID("return true5\n");
            *result = val; // for insertIfAbsent, we don't care about the particular value, just whether we inserted or not. so, we use val to signify not having inserted (and NO_VALUE to signify having inserted).
            return true; // success
        }
        Node<K,V> *pleft, *pright;
        if ((info->llxResults[0] = llx(tid, p, &pleft, &pright)) == NULL) {
            return false;
        } //RETRY;
        if (l != pleft && l != pright) {
            return false;
        } //RETRY;
        *result = l->value;
        initializeNode(tid, GET_ALLOCATED_NODE_PTR(tid, 0), key, val, NULL, NULL);
        info->numberOfNodes = 2;
        info->numberOfNodesToFreeze = 1;
        info->numberOfNodesToReclaim = 1; // only reclaim l (reclamation starts at nodes[1])
        info->numberOfNodesAllocated = 1;
        info->type = SCXRecord<K,V>::TYPE_REPLACE;
        info->nodes[0] = p;
        info->nodes[1] = l;
        assert(l);

        bool isInsertingKey = false;
        K insertedKey = NO_KEY;
        Node<K,V> * insertedNodes[] = {GET_ALLOCATED_NODE_PTR(tid, 0), NULL};
        bool isDeletingKey = false;
        K deletedKey = NO_KEY;
        Node<K,V> * deletedNodes[] = {l, NULL};

        bool retval = scx(tid, info, (l == pleft ? &p->left : &p->right), GET_ALLOCATED_NODE_PTR(tid, 0), insertedNodes, deletedNodes);
        if (retval) {
        }
        return retval;
    } else {
        Node<K,V> *pleft, *pright;
        if ((info->llxResults[0] = llx(tid, p, &pleft, &pright)) == NULL) {
            return false;
        } //RETRY;
        if (l != pleft && l != pright) {
            return false;
        } //RETRY;
        initializeNode(tid, GET_ALLOCATED_NODE_PTR(tid, 0), key, val, NULL, NULL);
//        initializeNode(tid, GET_ALLOCATED_NODE_PTR(tid, 1), l->key, l->value, /*1,*/ NULL, NULL);
        // TODO: change all equality comparisons with NO_KEY to use cmp()
        if (l->key == NO_KEY || cmp(key, l->key)) {
            initializeNode(tid, GET_ALLOCATED_NODE_PTR(tid, 1), l->key, l->value, GET_ALLOCATED_NODE_PTR(tid, 0), l);
        } else {
            initializeNode(tid, GET_ALLOCATED_NODE_PTR(tid, 1), key, val, l, GET_ALLOCATED_NODE_PTR(tid, 0));
        }
        *result = NO_VALUE;
        info->numberOfNodes = 2;
        info->numberOfNodesToReclaim = 0;
        info->numberOfNodesToFreeze = 1; // only freeze nodes[0]
        info->numberOfNodesAllocated = 2;
        info->type = SCXRecord<K,V>::TYPE_INS;
        info->nodes[0] = p;
        info->nodes[1] = l; // note: used as OLD value for CAS that changes p's child pointer (but is not frozen or marked)

        Node<K,V> * insertedNodes[] = {GET_ALLOCATED_NODE_PTR(tid, 0), GET_ALLOCATED_NODE_PTR(tid, 1), NULL};
        Node<K,V> * deletedNodes[] = {NULL};

        bool retval = scx(tid, info, (l == pleft ? &p->left : &p->right), GET_ALLOCATED_NODE_PTR(tid, 1), insertedNodes, deletedNodes);
        return retval;
    }
}

template<class K, class V, class Compare, class RecManager>
inline bool vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::updateErase_search_llx_scx(
            ReclamationInfo<K,V> * const info, const int tid, void **input, void **output) { // input consists of: const K& key
    const K& key = *((const K*) input[0]);
    V *result = (V*) output[0];

    TRACE COUTATOMICTID("updateErase_search_llx_scx(tid="<<tid<<", key="<<key<<")"<<endl);

    Node<K,V> *gp, *p, *l;
    l = rqProvider->read_addr(tid, &root->left);
    if (rqProvider->read_addr(tid, &l->left) == NULL) {
        *result = NO_VALUE;
        return true;
    } // only sentinels in tree...
    gp = root;
    p = l;
    l = rqProvider->read_addr(tid, &p->left);    // note: l must have key infinity, and l->left must not.
    while (rqProvider->read_addr(tid, &l->left) != NULL) {
        gp = p;
        p = l;
        if (cmp(key, p->key)) {
            l = rqProvider->read_addr(tid, &p->left);
        } else {
            l = rqProvider->read_addr(tid, &p->right);
        }
    }
    // if we fail to find the key in the tree
    if (key != l->key) {
        *result = NO_VALUE;
        return true; // success
    } else {
        Node<K,V> *gpleft, *gpright;
        Node<K,V> *pleft, *pright;
        Node<K,V> *sleft, *sright;
        if ((info->llxResults[0] = llx(tid, gp, &gpleft, &gpright)) == NULL) return false;
        if (p != gpleft && p != gpright) return false;
        if ((info->llxResults[1] = llx(tid, p, &pleft, &pright)) == NULL) return false;
        if (l != pleft && l != pright) return false;
        *result = l->value;
        // Read fields for the sibling s of l
        Node<K,V> *s = (l == pleft ? pright : pleft);
        if ((info->llxResults[2] = llx(tid, s, &sleft, &sright)) == NULL) return false;
        // Now, if the op. succeeds, all structure is guaranteed to be just as we verified
        initializeNode(tid, GET_ALLOCATED_NODE_PTR(tid, 0), s->key, s->value, /*newWeight,*/ sleft, sright);
        info->numberOfNodes = 4;
        info->numberOfNodesToReclaim = 3; // reclaim p, s, l (reclamation starts at nodes[1])
        info->numberOfNodesToFreeze = 3;
        info->numberOfNodesAllocated = 1;
        info->type = SCXRecord<K,V>::TYPE_DEL;
        info->nodes[0] = gp;
        info->nodes[1] = p;
        info->nodes[2] = s;
        info->nodes[3] = l;
        assert(gp); assert(p); assert(s); assert(l);

        Node<K,V> * insertedNodes[] = {GET_ALLOCATED_NODE_PTR(tid, 0), NULL};
        Node<K,V> * deletedNodes[] = {p, s, l, NULL};
        bool retval = scx(tid, info, (p == gpleft ? &gp->left : &gp->right), GET_ALLOCATED_NODE_PTR(tid, 0), insertedNodes, deletedNodes);
        return retval;
    }
}

template<class K, class V, class Compare, class RecManager>
vcas_bst_ns::Node<K,V>* vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::initializeNode(
            const int tid,
            Node<K,V> * const newnode,
            const K& key,
            const V& value,
            Node<K,V> * const left,
            Node<K,V> * const right) {
    newnode->key = key;
    newnode->value = value;
    rqProvider->init_node(tid, newnode);
    // note: synchronization is not necessary for the following accesses,
    // since a memory barrier will occur before this object becomes reachable
    // from an entry point to the data structure.
    rqProvider->write_addr(tid, &newnode->left, left);
    rqProvider->write_addr(tid, &newnode->right, right);
    newnode->scxRecord.store((uintptr_t) DUMMY_SCXRECORD, memory_order_relaxed);
    newnode->marked.store(false, memory_order_relaxed);
    return newnode;
}

// you may call this only in a quiescent state.
// the scx records in scxRecordsSeen must be protected (or we must know no one can have freed them--this is the case in this implementation).
// if this is being called from crash recovery, all nodes in nodes[] and the scx record must be Qprotected.
template<class K, class V, class Compare, class RecManager>
void vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::reclaimMemoryAfterSCX(
            const int tid,
            ReclamationInfo<K,V> * info) {
        
    Node<K,V> ** const nodes = info->nodes;
    SCXRecord<K,V> * const * const scxRecordsSeen = (SCXRecord<K,V> * const * const) info->llxResults;
    const int state = info->state;
    const int operationType = info->type;
    
    // NOW, WE ATTEMPT TO RECLAIM ANY RETIRED NODES
    int highestIndexReached = (state == SCXRecord<K,V>::STATE_COMMITTED 
            ? info->numberOfNodesToFreeze
            : 0);
    const int maxNodes = MAX_NODES;
    assert(highestIndexReached>=0);
    assert(highestIndexReached<=maxNodes);
    
    const int state_aborted = SCXRecord<K,V>::STATE_ABORTED;
    const int state_inprogress = SCXRecord<K,V>::STATE_INPROGRESS;
    const int state_committed = SCXRecord<K,V>::STATE_COMMITTED;
    if (highestIndexReached == 0) {
        assert(state == state_aborted || state == state_inprogress);
        return;
    } else {
        assert(!recmgr->supportsCrashRecovery() || recmgr->isQuiescent(tid));
        // if the state was COMMITTED, then we cannot reuse the nodes the we
        // took from allocatedNodes[], either, so we must replace these nodes.
        if (state == SCXRecord<K,V>::STATE_COMMITTED) {
            //cout<<"replacing allocated nodes"<<endl;
            for (int i=0;i<info->numberOfNodesAllocated;++i) {
                REPLACE_ALLOCATED_NODE(tid, i);
            }
//            // nodes[1], nodes[2], ..., nodes[nNodes-1] are now retired
//            for (int j=0;j<info->numberOfNodesToReclaim;++j) {
//                recmgr->retire(tid, nodes[1+j]);
//            }
        } else {
            assert(state >= state_aborted); /* is ABORTED */
        }
    }
}

// you may call this only if each node in nodes is protected by a call to recmgr->protect
template<class K, class V, class Compare, class RecManager>
bool vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::scx(
            const int tid,
            ReclamationInfo<K,V> * const info,
            Node<K,V> * volatile * field,        // pointer to a "field pointer" that will be changed
            Node<K,V> *newNode,
            Node<K,V> * const * const insertedNodes,
            Node<K,V> * const * const deletedNodes) {
    TRACE COUTATOMICTID("scx(tid="<<tid<<" type="<<info->type<<")"<<endl);
    
    SCXRecord<K,V> *newdesc = DESC1_NEW(tid);
    newdesc->c.newNode = newNode;
    for (int i=0;i<info->numberOfNodes;++i) {
        newdesc->c.nodes[i] = info->nodes[i];
    }
    for (int i=0;i<info->numberOfNodesToFreeze;++i) {
        newdesc->c.scxRecordsSeen[i] = (SCXRecord<K,V> *) info->llxResults[i];
    }

    int i;
    for (i=0;insertedNodes[i];++i) newdesc->c.insertedNodes[i] = insertedNodes[i];
    newdesc->c.insertedNodes[i] = NULL;
    for (i=0;deletedNodes[i];++i) newdesc->c.deletedNodes[i] = deletedNodes[i];
    newdesc->c.deletedNodes[i] = NULL;

    newdesc->c.field = field;
    newdesc->c.numberOfNodes = (char) info->numberOfNodes;
    newdesc->c.numberOfNodesToFreeze = (char) info->numberOfNodesToFreeze;

    // note: writes equivalent to the following two are already done by DESC1_NEW()
    //rec->state.store(SCXRecord<K,V>::STATE_INPROGRESS, memory_order_relaxed);
    //rec->allFrozen.store(false, memory_order_relaxed);
    DESC1_INITIALIZED(tid); // mark descriptor as being in a consistent state
    
    SOFTWARE_BARRIER;
    int state = help(tid, TAGPTR1_NEW(tid, newdesc->c.mutables), newdesc, false);
    info->state = state; // rec->state.load(memory_order_relaxed);
    reclaimMemoryAfterSCX(tid, info);
    return state & SCXRecord<K,V>::STATE_COMMITTED;
}

template <class K, class V, class Compare, class RecManager>
void vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::helpOther(const int tid, tagptr_t tagptr) {
    if ((void*) tagptr == DUMMY_SCXRECORD) {
        TRACE COUTATOMICTID("helpOther dummy descriptor"<<endl);
        return; // deal with the dummy descriptor
    }
    SCXRecord<K,V> newdesc;
    //cout<<"sizeof(newrec)="<<sizeof(newrec)<<" computed size="<<SCXRecord<K,V>::size<<endl;
    if (DESC1_SNAPSHOT(&newdesc, tagptr, SCXRecord<K comma1 V>::size /* sizeof(newrec) /*- sizeof(newrec.padding)*/)) {
        help(tid, tagptr, &newdesc, true);
    } else {
        TRACE COUTATOMICTID("helpOther unable to get snapshot of "<<tagptrToString(tagptr)<<endl);
    }
}

// returns the state field of the scx record "scx."
template <class K, class V, class Compare, class RecManager>
int vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::help(const int tid, tagptr_t tagptr, SCXRecord<K,V> *snap, bool helpingOther) {
    TRACE COUTATOMICTID("help "<<tagptrToString(tagptr)<<endl);
    SCXRecord<K,V> *ptr = TAGPTR1_UNPACK_PTR(tagptr);
    
    // TODO: make SCX_WRITE_STATE into regular write for the owner of this descriptor
    // (might not work in general, but here we can prove that allfrozen happens
    //  before state changes, and the sequence number does not change until the
    //  owner is finished with his operation; in general, seems like the last
    //  write done by helpers/owner can be a write, not a CAS when done
    //  by the owner)
    for (int i=helpingOther; i<snap->c.numberOfNodesToFreeze; ++i) { // freeze sub-tree
        if (snap->c.scxRecordsSeen[i] == LLX_RETURN_IS_LEAF) {
            TRACE COUTATOMICTID("nodes["<<i<<"] is a leaf");
            assert(i > 0); // nodes[0] cannot be a leaf...
            continue; // do not freeze leaves
        }
        
        uintptr_t exp = (uintptr_t) snap->c.scxRecordsSeen[i];
        bool successfulCAS = snap->c.nodes[i]->scxRecord.compare_exchange_strong(exp, tagptr); // MEMBAR ON X86/64
        if (successfulCAS || exp == tagptr) continue; // if node is already frozen for our operation

        // read mutable allFrozen field of descriptor
        bool succ;
        bool allFrozen = DESC1_READ_FIELD(succ, ptr->c.mutables, tagptr, MUTABLES_MASK_ALLFROZEN, MUTABLES_OFFSET_ALLFROZEN);
        if (!succ) return SCXRecord<K,V>::STATE_ABORTED;
        
        int newState = (allFrozen) ? SCXRecord<K,V>::STATE_COMMITTED : SCXRecord<K,V>::STATE_ABORTED;
        TRACE COUTATOMICTID("help return state "<<newState<<" after failed freezing cas on nodes["<<i<<"]"<<endl);
        MUTABLES1_WRITE_FIELD(ptr->c.mutables, snap->c.mutables, newState, MUTABLES_MASK_STATE, MUTABLES_OFFSET_STATE);
        return newState;
    }
    
    MUTABLES1_WRITE_BIT(ptr->c.mutables, snap->c.mutables, MUTABLES_MASK_ALLFROZEN);
    for (int i=1; i<snap->c.numberOfNodesToFreeze; ++i) {
        if (snap->c.scxRecordsSeen[i] == LLX_RETURN_IS_LEAF) continue; // do not mark leaves
        snap->c.nodes[i]->marked.store(true, memory_order_relaxed); // finalize all but first node
    }
    
    // CAS in the new sub-tree (update CAS)
//    uintptr_t expected = (uintptr_t) snap->nodes[1];
    rqProvider->linearize_update_at_cas(tid, snap->c.field, snap->c.nodes[1], snap->c.newNode, snap->c.insertedNodes, snap->c.deletedNodes);
    
    // todo: add #ifdef CASing scx record pointers to set an "invalid" bit,
    // and add to llx a test that determines whether a pointer is invalid.
    // if so, the record no longer exists and no longer needs help.
    //
    // the goal of this is to prevent a possible ABA problem that can occur
    // if there is VERY long lived data in the tree.
    // specifically, if a node whose scx record pointer is CAS'd by this scx
    // was last modified by the owner of this scx, and the owner performed this
    // modification exactly 2^SEQ_WIDTH (of its own) operations ago,
    // then an operation running now may confuse a pointer to an old version of
    // this scx record with the current version (since the sequence #'s match).
    //
    // actually, there may be another solution. a problem arises only if a
    // helper can take an effective action not described by an active operation.
    // even if a thread erroneously believes an old pointer reflects an active
    // operation, if it simply helps the active operation, there is no issue.
    // the issue arises when the helper believes it sees an active operation,
    // and begins helping by looking at the scx record, but the owner of that
    // operation is currently in the process of initializing the scx record,
    // so the helper sees an inconsistent scx record and takes invalid steps.
    // (recall that a helper can take a snapshot of an scx record whenever
    //  the sequence # it sees in a tagged pointer matches the sequence #
    //  it sees in the scx record.)
    // the solution here seems to be to ensure scx records can be helped only
    // when they are consistent.
    
    MUTABLES1_WRITE_FIELD(ptr->c.mutables, snap->c.mutables, SCXRecord<K comma1 V>::STATE_COMMITTED, MUTABLES_MASK_STATE, MUTABLES_OFFSET_STATE);
    
    TRACE COUTATOMICTID("help return COMMITTED after performing update cas"<<endl);
    return SCXRecord<K,V>::STATE_COMMITTED; // success
}

// you may call this only if node is protected by a call to recmgr->protect
template<class K, class V, class Compare, class RecManager>
void * vcas_bst_ns::vcas_bst<K,V,Compare,RecManager>::llx(
            const int tid,
            Node<K,V> *node,
            Node<K,V> **retLeft,
            Node<K,V> **retRight) {
    TRACE COUTATOMICTID("llx(tid="<<tid<<", node="<<*node<<")"<<endl);
    
    tagptr_t tagptr1 = node->scxRecord.load(memory_order_relaxed);
        
    // read mutable state field of descriptor
    bool succ;
    int state = DESC1_READ_FIELD(succ, TAGPTR1_UNPACK_PTR(tagptr1)->c.mutables, tagptr1, MUTABLES_MASK_STATE, MUTABLES_OFFSET_STATE);
    if (!succ) state = SCXRecord<K,V>::STATE_COMMITTED;
    // note: special treatment for alg in the case where the descriptor has already been reallocated (impossible before the transformation, assuming safe memory reclamation)
    
    SOFTWARE_BARRIER;       // prevent compiler from moving the read of marked before the read of state (no hw barrier needed on x86/64, since there is no read-read reordering)
    bool marked = node->marked.load(memory_order_relaxed);
    SOFTWARE_BARRIER;       // prevent compiler from moving the reads tagptr2=node->scxRecord or tagptr3=node->scxRecord before the read of marked. (no h/w barrier needed on x86/64 since there is no read-read reordering)
    if ((state & SCXRecord<K,V>::STATE_COMMITTED && !marked) || state & SCXRecord<K,V>::STATE_ABORTED) {
        SOFTWARE_BARRIER;       // prevent compiler from moving the reads tagptr2=node->scxRecord or tagptr3=node->scxRecord before the read of marked. (no h/w barrier needed on x86/64 since there is no read-read reordering)
        *retLeft = rqProvider->read_addr(tid, &node->left);
        *retRight = rqProvider->read_addr(tid, &node->right);
        if (*retLeft == NULL) {
            TRACE COUTATOMICTID("llx return2.a (tid="<<tid<<" state="<<state<<" marked="<<marked<<" key="<<node->key<<")\n"); 
            return LLX_RETURN_IS_LEAF;
        }
        SOFTWARE_BARRIER; // prevent compiler from moving the read of node->scxRecord before the read of left or right
        tagptr_t tagptr2 = node->scxRecord.load(memory_order_relaxed);
        if (tagptr1 == tagptr2) {
            TRACE COUTATOMICTID("llx return2 (tid="<<tid<<" state="<<state<<" marked="<<marked<<" key="<<node->key<<" desc1="<<tagptr1<<")\n"); 
            // on x86/64, we do not need any memory barrier here to prevent mutable fields of node from being moved before our read of desc1, because the hardware does not perform read-read reordering. on another platform, we would need to ensure no read from after this point is reordered before this point (technically, before the read that becomes desc1)...
            return (void*) tagptr1;    // success
        } else {
            if (recmgr->shouldHelp()) {
                TRACE COUTATOMICTID("llx help 1 tid="<<tid<<endl);
                helpOther(tid, tagptr2);
            }
        }
    } else if (state == SCXRecord<K,V>::STATE_INPROGRESS) {
        if (recmgr->shouldHelp()) {
            TRACE COUTATOMICTID("llx help 2 tid="<<tid<<endl);
            helpOther(tid, tagptr1);
        }
    } else {
        // state committed and marked
        assert(state == 1); /* SCXRecord<K,V>::STATE_COMMITTED */
        assert(marked);
        if (recmgr->shouldHelp()) {
            tagptr_t tagptr3 = node->scxRecord.load(memory_order_relaxed);
            TRACE COUTATOMICTID("llx help 3 tid="<<tid<<" tagptr3="<<tagptrToString(tagptr3)<<endl);
            helpOther(tid, tagptr3);
        }
    }
    TRACE COUTATOMICTID("llx return5 (tid="<<tid<<" state="<<state<<" marked="<<marked<<" key="<<node->key<<")\n");
    return NULL;            // fail
}
