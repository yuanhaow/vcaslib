package algorithms.chromatic;

/*
Concurrent, Non-blocking Chromatic Tree with Batched Leaves and Non-atomic Multi-point Queries

This is an implementation of the CT-64 algorithm mentioned in the paper:
    "Constant-Time Snapshots with Applications to Concurrent Data Structures"
    Yuanhao Wei, Naama Ben-David, Guy E. Blelloch, Panagiota Fatourou, Eric Ruppert, Yihan Sun
    PPoPP 2021

This data structure supports linearizable get(), containsKey(), putIfAbsent(), and remove(),
as well as non-linearizable rangeScan(), findIf(), multiSearch() and successor(). 
All operations are lock-free.

Copyright (C) 2021 Yuanhao Wei

This implementation based on the following non-blocking Chromatic Tree implementation by Trevor Brown:
https://bitbucket.org/trbot86/implementations/src/master/java/src/algorithms/published/LockFreeChromaticMap.java

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

import main.support.Element;

import java.lang.reflect.Array;

import java.util.ArrayList;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Predicate;

public class LockFreeBatchChromaticMap<K extends Comparable<? super K>,V> {
    private final int BATCHING_DEGREE;
    private final int d; // this is the number of violations to allow on a search path before we fix everything on it. if d is zero, then each update fixes any violation it created before returning.
    private static final int DEFAULT_d = 6; // experimentally determined to yield good performance for both random workloads, and operations on sorted sequences
    private final InternalNode root;
    private static final Operation dummy = new Operation();
    private static final AtomicReferenceFieldUpdater<InternalNode, Operation> updateOp = AtomicReferenceFieldUpdater.newUpdater(InternalNode.class, Operation.class, "op");
    private static final AtomicReferenceFieldUpdater<InternalNode, Node> updateLeft = AtomicReferenceFieldUpdater.newUpdater(InternalNode.class, Node.class, "left");
    private static final AtomicReferenceFieldUpdater<InternalNode, Node> updateRight = AtomicReferenceFieldUpdater.newUpdater(InternalNode.class, Node.class, "right");

    public LockFreeBatchChromaticMap() {
        this(16, DEFAULT_d);
    }

    public LockFreeBatchChromaticMap(final int BATCHING_DEGREE) {
        this(BATCHING_DEGREE, DEFAULT_d); 
    }
    public LockFreeBatchChromaticMap(final int BATCHING_DEGREE, final int allowedViolationsPerPath) {
        System.out.println("BATCHING DEGREE: " + BATCHING_DEGREE);
        this.BATCHING_DEGREE = BATCHING_DEGREE;
        d = allowedViolationsPerPath;
        root = new InternalNode(null, 1, new InternalNode(null, 1, new LeafNode(0, 1), null), null);
    }

    /**
     * size() is NOT a constant time method, and the result is only guaranteed to
     * be consistent if no concurrent updates occur.
     * Note: linearizable size( and iterators can be implemented, so contact
     *       the author if they are needed for some application.
     */
    public final int size() {
        return sequentialSize(root);
    }
    private int sequentialSize(final Node node) {
        if (node == null) return 0;
        if (node instanceof LeafNode) return ((LeafNode)node).getSize();
        InternalNode n = (InternalNode) node;
        return sequentialSize(n.left) + sequentialSize(n.right);
    }

    public final boolean containsKey(final K key) {
        return get(key) != null;
    }

    public final V get(final K key) {
        InternalNode p = (InternalNode) root.left;
        while(true) {
            Node l = (p.key == null || key.compareTo((K) p.key) < 0) ? p.left : p.right;
            if(l instanceof LeafNode) return (V) ((LeafNode)l).getValue(key);
            p = (InternalNode) l;
        }
    }

    // public final V put(final K key, final V value) {
    //     return doPut(key, value, false);
    // }

    public final V putIfAbsent(final K key, final V value) {
        Operation op = null;
        InternalNode p = null;
        LeafNode l = null;
        Node n;
        int count = 0;
        boolean structuralChange = false;
        
        while (true) {
            while (op == null) {
                p = root;
                n = root.left;
                if (n instanceof InternalNode) {
                    count = 0;
                    p = (InternalNode) n;
                    n = p.left; // note: before executing this line, l must have key infinity, and l.left must not.
                    while (n instanceof InternalNode) {
                        InternalNode nn = (InternalNode) n;
                        if (d > 0 && (nn.weight > 1 || nn.weight == 0 && p.weight == 0)) ++count;
                        p = nn;
                        n = (key.compareTo((K) nn.key) < 0) ? nn.left : nn.right;
                    }
                }
                l = (LeafNode) n;
                V ret = (V) l.getValue(key);
                if(ret != null) return ret; // if we find the key in the tree already
                if(l.getSize() == BATCHING_DEGREE) {// leaf is full
                    op = createInsertOp(p, l, key, value);
                    structuralChange = true;
                }
                else {
                    op = createInsertReplaceOp(p, l, key, value);
                    structuralChange = false;
                }
            }
            if (helpSCX(op, 0)) {
                // clean up violations if necessary
                if(!structuralChange) return null;
                if (d == 0) {
                    if (p.weight == 0 && l.weight == 1) fixToKey(key);
                } else {
                    if (count >= d) fixToKey(key);
                }
                // we may have found the key and replaced its value (and, if so, the old value is stored in the old node)
                return null;
            }
            op = null;
        }
    }

    // private V doPut(final K key, final V value, final boolean onlyIfAbsent) {
    //     boolean found = false;
    //     Operation op = null;
    //     Node p = null, l = null;
    //     int count = 0;
    //     V ret = null;
        
    //     while (true) {
    //         while (op == null) {
    //             p = root;
    //             l = root.left;
    //             if (l.left != null) {
    //                 count = 0;
    //                 p = l;
    //                 l = l.left; // note: before executing this line, l must have key infinity, and l.left must not.
    //                 while (l.left != null) {
    //                     if (d > 0 && (l.weight > 1 || l.weight == 0 && p.weight == 0)) ++count;
    //                     p = l;
    //                     l = (key.compareTo((K) l.key) < 0) ? l.left : l.right;
    //                 }
    //             }
                
    //             ret = (V) l.getValue(key);
    //             // if we find the key in the tree already
    //             if (ret != null) {
    //                 found = true;
    //                 if (onlyIfAbsent) return ret;
    //                 op = createReplaceOp(p, l, key, value);
    //             } else {
    //                 found = false;
    //                 op = createInsertOp(p, l, key, value);
    //             }
    //         }
    //         if (helpSCX(op, 0)) {
    //             // clean up violations if necessary
    //             if (d == 0) {
    //                 if (!found && p.weight == 0 && l.weight == 1) fixToKey(key);
    //             } else {
    //                 if (count >= d) fixToKey(key);
    //             }
    //             // we may have found the key and replaced its value (and, if so, the old value is stored in the old node)
    //             return (found ? ret : null);
    //         }
    //         op = null;
    //     }
    // }

    public final V remove(final K key) {
        InternalNode gp, p = null, nn;
        LeafNode l = null;
        Node n;
        Operation op = null;
        int count = 0;
        V ret = null;
        boolean structuralChange = false;
        
        while (true) {
            while (op == null) {
                gp = root;
                p = root;
                n = root.left;
                if (n instanceof InternalNode) {
                    nn = (InternalNode) n;
                    count = 0;
                    gp = p;
                    p = nn;
                    n = nn.left; // note: before executing this line, l must have key infinity, and l.left must not.
                    while (n instanceof InternalNode) {
                        nn = (InternalNode) n;
                        if (d > 0 && (nn.weight > 1 || nn.weight == 0 && p.weight == 0)) ++count;
                        gp = p;
                        p = nn;
                        n = (key.compareTo((K) nn.key) < 0) ? nn.left : nn.right;
                    }
                }
                l = (LeafNode) n;
                ret = (V) l.getValue(key);
                // the key was not in the tree at the linearization point, so no value was removed
                if(ret == null) return null;
                if(l.getSize() == 1 && !isSentinel(l)) {
                    op = createDeleteOp(gp, p, l);
                    structuralChange = true;
                }
                else {
                    op = createDeleteReplaceOp(p, l, key);
                    structuralChange = false;
                }
            }
            if (helpSCX(op, 0)) {
                // clean up violations if necessary
                if(!structuralChange) return ret;
                if (d == 0) {
                    if (p.weight > 0 && l.weight > 0 && !isSentinel(p)) fixToKey(key);
                } else {
                    if (count >= d) fixToKey(key);
                }
                // we deleted a key, so we return the removed value (saved in the old node)
                return ret;
            }
            op = null;
        }
    }

    public final void fixToKey(final K key) {
        while (true) {
            InternalNode ggp, gp, p, nn;
            Node n = root.left;

            if (n instanceof LeafNode) return; // only sentinels in tree...
            nn = (InternalNode) n;
            ggp = gp = root;
            p = nn;
            n = nn.left; // note: before executing this line, l must have key infinity, and l.left must not.
            while (n instanceof InternalNode && n.weight <= 1 && (n.weight != 0 || p.weight != 0)) {
                nn = (InternalNode) n;
                ggp = gp;
                gp = p;
                p = nn;
                n = (key.compareTo((K) nn.key) < 0) ? nn.left : nn.right;
            }

            if (n.weight == 1) return; // if no violation, then the search hit a leaf, so we can stop

            final Operation op = createBalancingOp(ggp, gp, p, n);
            if (op != null) {
                helpSCX(op, 0);
            }
        }
    }

    private boolean isSentinel(final Node node) {
        return (node == ((InternalNode)((InternalNode)root).left).left || (node instanceof InternalNode && ((InternalNode)node).key == null));
    }
    
    // This weaker form of LLX does not return a linearizable snapshot.
    // However, we do not use the fact that LLX returns a snapshot anywhere in
    //   the proof of SCX (help), and we do not need the snapshot capability
    //   to satisfy the precondition of SCX (that there be an LLX linked to SCX
    //   for each node in V).
    // Note: using a full LLX slows things by ~3%.
    private Operation weakLLX(final Node r) {
        if(r instanceof InternalNode) {
            InternalNode n = (InternalNode) r;
            final Operation rinfo = n.op;
            final int state = rinfo.state;
            if (state == Operation.STATE_ABORTED || (state == Operation.STATE_COMMITTED && !n.marked)) {
                return rinfo;
            }
            if (rinfo.state == Operation.STATE_INPROGRESS) {
                helpSCX(rinfo, 1);
            } else if (n.op.state == Operation.STATE_INPROGRESS) {
                helpSCX(n.op, 1);
            }
            return null;
        } else {
            return dummy;
        }
    }
    // helper function to use the results of a weakLLX more conveniently
    private boolean weakLLX(final Node r, final int i, final Operation[] ops, final Node[] nodes) {
        if ((ops[i] = weakLLX(r)) == null) return false;
        nodes[i] = r;
        return true;
    }
    
    // this function is essentially an SCX without the creation of V, R, fld, new
    // (which are stored in an operation object).
    // the creation of the operation object is simply inlined in other methods.
    private boolean helpSCX(final Operation op, int i) {
        // get local references to some fields of op, in case we later null out fields of op (to help the garbage collector)
        final Node[] nodes = op.nodes;
        final Operation[] ops = op.ops;
        final Node subtree = op.subtree;
        InternalNode node;
        // if we see aborted or committed, no point in helping (already done).
        // further, if committed, variables may have been nulled out to help the garbage collector.
        // so, we return.
        if (op.state != Operation.STATE_INPROGRESS) return true;
        
        // freeze sub-tree
        for (; i<ops.length; ++i) {
            if(nodes[i] instanceof InternalNode) {
                node = (InternalNode) nodes[i];
                if (!updateOp.compareAndSet(node, ops[i], op) && node.op != op) { // if work was not done
                    if (op.allFrozen) {
                        return true;
                    } else {
                        op.state = Operation.STATE_ABORTED;
                        // help the garbage collector (must be AFTER we set state committed or aborted)
                        op.nodes = null;
                        op.ops = null;
                        op.subtree = null;
                        return false;
                    }
                }
            }
        }
        op.allFrozen = true;
        for (i=1; i<ops.length; ++i) 
            if(nodes[i] instanceof InternalNode)
                ((InternalNode)nodes[i]).marked = true; // finalize all but first node
        
        // CAS in the new sub-tree (child-cas)
        node = (InternalNode) nodes[0];
        if (node.left == nodes[1]) {
            updateLeft.compareAndSet(node, nodes[1], subtree);
            // node.compareAndSetLeft(nodes[1], subtree);
        } else { // assert: nodes[0].right == nodes[1]
            updateRight.compareAndSet(node, nodes[1], subtree);
            // node.compareAndSetRight(nodes[1], subtree); // splice in new sub-tree (as a right child)  
        }
        op.state = Operation.STATE_COMMITTED;
        
        // help the garbage collector (must be AFTER we set state committed or aborted)
        op.nodes = null;
        op.ops = null;
        op.subtree = null;
        return true;
    }

    private Operation createInsertOp(final InternalNode p, final LeafNode l, final K key, final V value) {
        final Operation[] ops = new Operation[]{null};
        final Node[] nodes = new Node[]{null, l};

        if (!weakLLX(p, 0, ops, nodes)) return null;

        if (l != p.left && l != p.right) return null;

        // Compute the weight for the new parent node
        final int newWeight = (isSentinel(l) ? 1 : l.weight - 1);               // (maintain sentinel weights at 1)

        // Build new sub-tree
        final LeafNode newLeft, newRight;
        final InternalNode newInternal;
        if(l.shouldBePutLeft(key)) {
            newLeft = l.splitLeftAndPut(key, value);
            newRight = l.splitRight();
        } else {
            newLeft = l.splitLeft();
            newRight = l.splitRightAndPut(key, value);                        
        }
        newInternal = new InternalNode(newRight.keys[0], newWeight, newLeft, newRight);
        return new Operation(nodes, ops, newInternal);
    }
    
    // Just like insert, except this replaces any existing value.
    private Operation createInsertReplaceOp(final InternalNode p, final LeafNode l, final K key, final V value) {
        final Operation[] ops = new Operation[]{null};
        final Node[] nodes = new Node[]{null, l};

        if (!weakLLX(p, 0, ops, nodes)) return null;

        if (l != p.left && l != p.right) return null;

        // Build new sub-tree
        final Node subtree = l.put(key, value);
        return new Operation(nodes, ops, subtree);
    }

    // Just like insert, except this replaces any existing value.
    private Operation createDeleteReplaceOp(final InternalNode p, final LeafNode l, final K key) {
        final Operation[] ops = new Operation[]{null};
        final Node[] nodes = new Node[]{null, l};

        if (!weakLLX(p, 0, ops, nodes)) return null;

        if (l != p.left && l != p.right) return null;

        // Build new sub-tree
        final Node subtree = l.remove(key);
        return new Operation(nodes, ops, subtree);
    }

    private Operation createDeleteOp(final InternalNode gp, final InternalNode p, final LeafNode l) {
        final Operation[] ops = new Operation[]{null, null, null};
        final Node[] nodes = new Node[]{null, null, null};

        if (!weakLLX(gp, 0, ops, nodes)) return null;
        if (!weakLLX(p, 1, ops, nodes)) return null;
        
        if (p != gp.left && p != gp.right) return null;
        final boolean left = (l == p.left);
        if (!left && l != p.right) return null;

        // Read fields for the sibling of l into ops[2], nodes[2] = s
        if (!weakLLX(left ? p.right : p.left, 2, ops, nodes)) return null;
        final Node s = nodes[2];

        // Now, if the op. succeeds, all structure is guaranteed to be just as we verified

        // Compute weight for the new node (to replace to deleted leaf l and parent p)
        final int newWeight = (isSentinel(p) ? 1 : p.weight + s.weight); // weights of parent + sibling of deleted leaf

        // Build new sub-tree
        final Node newP = s.copy(newWeight);
        return new Operation(nodes, ops, newP);
    }

    private Operation createBalancingOp(final InternalNode f, final InternalNode fX, final InternalNode fXX, final Node fXXX) {
        final Operation opf = weakLLX(f);
        if (opf == null || !f.hasChild(fX)) return null;

        final Operation opfX = weakLLX(fX);
        if (opfX == null) return null;
        final Node fXL = fX.left;
        final Node fXR = fX.right;
        final boolean fXXleft = (fXX == fXL);
        if (!fXXleft && fXX != fXR) return null;
        
        final Operation opfXX = weakLLX(fXX);
        if (opfXX == null) return null;
        final Node fXXL = fXX.left;
        final Node fXXR = fXX.right;
        final boolean fXXXleft = (fXXX == fXXL);
        if (!fXXXleft && fXXX != fXXR) return null;
        
        // Overweight violation
        if (fXXX.weight > 1) {
            if (fXXXleft) {
                final Operation opfXXL = weakLLX(fXXL);
                if (opfXXL == null) return null;
                return createOverweightLeftOp(f, fX, fXX, fXXL, opf, opfX, opfXX, opfXXL, fXL, fXR, fXXR, fXXleft);

            } else {
                final Operation opfXXR = weakLLX(fXXR);
                if (opfXXR == null) return null;
                return createOverweightRightOp(f, fX, fXX, fXXR, opf, opfX, opfXX, opfXXR, fXR, fXL, fXXL, !fXXleft);
            }
        // Red-red violation
        } else {
            if (fXXleft) {
                if (fXR.weight == 0) {
                    final Operation opfXR = weakLLX(fXR);
                    if (opfXR == null) return null;
                    return createBlkOp(new Node[] {f, fX, fXX, fXR}, new Operation[] {opf, opfX, opfXX, opfXR});
                    
                } else if (fXXXleft) {
                    return createRb1Op(new Node[] {f, fX, fXX}, new Operation[] {opf, opfX, opfXX});
                    
                } else {
                    final Operation opfXXR = weakLLX(fXXR);
                    if (opfXXR == null) return null;
                    return createRb2Op(new Node[] {f, fX, fXX, fXXR}, new Operation[] {opf, opfX, opfXX, opfXXR});
                }
            } else {
                if (fXL.weight == 0) {
                    final Operation opfXL = weakLLX(fXL);
                    if (opfXL == null) return null;
                    return createBlkOp(new Node[] {f, fX, fXL, fXX}, new Operation[] {opf, opfX, opfXL, opfXX});
                    
                } else if (!fXXXleft) {
                    return createRb1SymOp(new Node[] {f, fX, fXX}, new Operation[] {opf, opfX, opfXX});
                    
                } else {
                    final Operation opfXXL = weakLLX(fXXL);
                    if (opfXXL == null) return null;
                    return createRb2SymOp(new Node[] {f, fX, fXX, fXXL}, new Operation[] {opf, opfX, opfXX, opfXXL});
                }
            }
        }
    }
    
    private Operation createOverweightLeftOp(final Node f,
                                             final Node fX,
                                             final Node fXX,
                                             final Node fXXL,
                                             final Operation opf,
                                             final Operation opfX,
                                             final Operation opfXX,
                                             final Operation opfXXL,
                                             final Node fXL,
                                             final Node fXR,
                                             final Node fXXR,
                                             final boolean fXXlef) {
        if (fXXR.weight == 0) {
            if (fXX.weight == 0) {
                if (fXXlef) {
                    if (fXR.weight == 0) {
                        final Operation opfXR = weakLLX(fXR);
                        if (opfXR == null) return null;
                        return createBlkOp(new Node[] {f, fX, fXX, fXR}, new Operation[] {opf, opfX, opfXX, opfXR});
                    } else { // assert: fXR.weight > 0
                        final Operation opfXXR = weakLLX(fXXR);
                        if (opfXXR == null) return null;
                        return createRb2Op(new Node[] {f, fX, fXX, fXXR}, new Operation[] {opf, opfX, opfXX, opfXXR});
                    }
                } else { // assert: fXX == fXR
                    if (fXL.weight == 0) {
                        final Operation opfXL = weakLLX(fXL);
                        if (opfXL == null) return null;
                        return createBlkOp(new Node[] {f, fX, fXL, fXX}, new Operation[] {opf, opfX, opfXL, opfXX});
                    } else {
                        return createRb1SymOp(new Node[] {f, fX, fXX}, new Operation[] {opf, opfX, opfXX});
                    }
                }
            } else { // assert: fXX.weight > 0
                final Operation opfXXR = weakLLX(fXXR);
                if (opfXXR == null) return null;
                
                final Node fXXRL = ((InternalNode)fXXR).left;
                final Operation opfXXRL = weakLLX(fXXRL);
                if (opfXXRL == null) return null;
                
                if (fXXRL.weight > 1) {
                    return createW1Op(new Node[] {fX, fXX, fXXL, fXXR, fXXRL}, new Operation[] {opfX, opfXX, opfXXL, opfXXR, opfXXRL});
                } else if (fXXRL.weight == 0) {
                    return createRb2SymOp(new Node[] {fX, fXX, fXXR, fXXRL}, new Operation[] {opfX, opfXX, opfXXR, opfXXRL});
                } else { // assert: fXXRL.weight == 1
                    final Node fXXRLR = ((InternalNode)fXXRL).right;
                    if (fXXRLR == null) return null;
                    if (fXXRLR.weight == 0) {
                        final Operation opfXXRLR = weakLLX(fXXRLR);
                        if (opfXXRLR == null) return null;
                        return createW4Op(new Node[] {fX, fXX, fXXL, fXXR, fXXRL, fXXRLR}, new Operation[] {opfX, opfXX, opfXXL, opfXXR, opfXXRL, opfXXRLR});
                    } else { // assert: fXXRLR.weight > 0
                        final Node fXXRLL = ((InternalNode)fXXRL).left;
                        if (fXXRLL == null) return null;
                        if (fXXRLL.weight == 0) {
                            final Operation opfXXRLL = weakLLX(fXXRLL);
                            if (opfXXRLL == null) return null;
                            return createW3Op(new Node[] {fX, fXX, fXXL, fXXR, fXXRL, fXXRLL}, new Operation[] {opfX, opfXX, opfXXL, opfXXR, opfXXRL, opfXXRLL});
                        } else { // assert: fXXRLL.weight > 0
                            return createW2Op(new Node[] {fX, fXX, fXXL, fXXR, fXXRL}, new Operation[] {opfX, opfXX, opfXXL, opfXXR, opfXXRL});
                        }
                    }
                }
            }
        } else if (fXXR.weight == 1) {
            final Operation opfXXR = weakLLX(fXXR);
            if (opfXXR == null) return null;
            
            final Node fXXRL = ((InternalNode)fXXR).left;
            if (fXXRL == null) return null;
            final Node fXXRR = ((InternalNode)fXXR).right; // note: if fXXRR is null, then fXXRL is null, since tree is always a full binary tree, and children of leaves don't change
            if (fXXRR.weight == 0) {
                final Operation opfXXRR = weakLLX(fXXRR);
                if (opfXXRR == null) return null;
                return createW5Op(new Node[] {fX, fXX, fXXL, fXXR, fXXRR}, new Operation[] {opfX, opfXX, opfXXL, opfXXR, opfXXRR});
            } else if (fXXRL.weight == 0) {
                final Operation opfXXRL = weakLLX(fXXRL);
                if (opfXXRL == null) return null;
                return createW6Op(new Node[] {fX, fXX, fXXL, fXXR, fXXRL}, new Operation[] {opfX, opfXX, opfXXL, opfXXR, opfXXRL});
            } else {
                return createPushOp(new Node[] {fX, fXX, fXXL, fXXR}, new Operation[] {opfX, opfXX, opfXXL, opfXXR});
            }
        } else {
            final Operation opfXXR = weakLLX(fXXR);
            if (opfXXR == null) return null;
            return createW7Op(new Node[] {fX, fXX, fXXL, fXXR}, new Operation[] {opfX, opfXX, opfXXL, opfXXR});
        }
    }
    
    public static abstract class Node {
        public final int weight;

        public Node(final int weight) {
            this.weight = weight;
        }

        public abstract Node copy(final int weight);
    }

    public static final class LeafNode extends Node {
        public Comparable[] keys;
        public Object[] values;

        LeafNode(final int size, final int weight) {
            super(weight);
            this.keys = new Comparable[size];
            this.values = new Object[size];
        } 

        public Node copy(final int weight) {
            int size = getSize();
            LeafNode newNode = new LeafNode(size, weight);
            if(size > 0) {
                System.arraycopy(keys, 0, newNode.keys, 0, size);
                System.arraycopy(values, 0, newNode.values, 0, size);
            }
            return newNode;
        }


        public int getSize() { return keys.length; }

        final long getSum() { 
            long sum = 0;
            for(int i = 0; i < getSize(); i++)
                sum += ((Integer) keys[i]).intValue();
            return sum;
        }

        private boolean isFull(int maxSize) {
            return (getSize() == maxSize);
        }

        /**
            Performs a binary search of key in this node's array of keys.
            Precondition: key cannot be null.

            @param key  the key to search for
            @return     the index of key if it was found, otherwise -1
        */
        private final int containsKey(final Comparable key) {
            int i, a = 0, b = getSize()-1;
            if(b == -1) return -1;
            do {
                i = (a+b)/2;
                if (key.compareTo(keys[i]) == 0)
                    return i;
                else if (key.compareTo(keys[i]) < 0)
                    b = i-1;
                else
                    a = i+1;
            } while (a <= b);
            return -1;
        }

        /**
            Performs a binary search of key in this node's array of keys.
            Precondition: key cannot be null.

            @param key  the key to search for
            @return     value at the index of key if it was found, otherwise null
        */
        private final Object getValue(final Comparable key) {
            int i, a = 0, b = getSize()-1;
            if(b == -1) return null;
            do {
                i = (a+b)/2;
                if (key.compareTo(keys[i]) == 0)
                    return values[i];
                else if (key.compareTo(keys[i]) < 0)
                    b = i-1;
                else
                    a = i+1;
            } while (a <= b);
            return null;
        }

        // precondition: size > 0
        public final int lowerBound(final Comparable key) {
            int i, a = 0, b = getSize();
            while(a < b) {
                i = (a+b)/2;
                if (key.compareTo(keys[i]) == 0)
                    return i;
                else if (key.compareTo(keys[i]) < 0)
                    b = i;
                else
                    a = i+1;
            }
            return a;
        }

        // precondition: size > 0
        private final int upperBound(final Comparable key) {
            // if(key.compareTo(keys[size-1]) >= 0) return size - 1;
            int i, a = -1, b = getSize()-1;
            while(a < b) {
                i = (a+b+1)/2;
                if (key.compareTo(keys[i]) == 0)
                    return i;
                else if (key.compareTo(keys[i]) < 0)
                    b = i-1;
                else
                    a = i;
            }
            return a;
        }  

        /**
            Adds all keys of this node that belong in range [a,b] to ret.

            @param a    the lower limit of the range
            @param b    the upper limit of the range
            @param ret  the stack where keys are saved
        */
        private final void gatherKeys(final Comparable a, final Comparable b, final boolean leftOpen, final boolean rightOpen, final RangeScanResultHolder.Stack ret) {
            if(a.compareTo(b) == 0) {
                Object val = getValue(a);
                if(val != null)
                    ret.push(val); 
            } else {
                int startIndex = 0, endIndex = getSize()-1;
                if(leftOpen) startIndex = lowerBound(a);
                if(rightOpen) endIndex = upperBound(b);
                // Add all keys between them to the range query result
                for (int i = startIndex; i < getSize() && i <= endIndex; i++)
                    ret.push(values[i]);  
            }
        }

        /**
            Checks if key should be put in the left half of this node's array of keys.
            Preconditin: getSize() > 0
        */
        private boolean shouldBePutLeft(final Comparable key) {

            return (key.compareTo(keys[getSize()/2]) < 0);
        }
        /**
            Copies all keys of this node plus key in newNode.
        */

        private final LeafNode put(final Comparable key, final Object value) {
            int size = getSize();
            LeafNode newNode = new LeafNode(size+1, weight);
            if(size == 0) {
                newNode.keys[0] = key;
                newNode.values[0] = value;
            } else {
                int i, a = 0, b = size-1;
                do {
                    i = (a+b)/2;
                    if (key.compareTo(keys[i]) < 0)
                        b = i-1;
                    else
                        a = i+1;
                } while (a <= b);
                System.arraycopy(keys, 0, newNode.keys, 0, a);
                System.arraycopy(values, 0, newNode.values, 0, a);
                newNode.keys[a] = key;
                newNode.values[a] = value;

                System.arraycopy(keys, a, newNode.keys, a+1, size-a);
                System.arraycopy(values, a, newNode.values, a+1, size-a);
            }
            return newNode;
        }

        /**
            Copies all keys of this node except key in newNode.
        */
        private final LeafNode remove(final Comparable key) {
            int size = getSize();
            LeafNode newNode = new LeafNode(size-1, weight);
            if(size == 1) return newNode;
            int i, a = 0, b = size-1;
            do {
                i = (a+b)/2;
                if (key.compareTo(keys[i]) < 0)
                    b = i-1;
                else
                    a = i+1;
            } while (a <= b);
            System.arraycopy(keys, 0, newNode.keys, 0, b);
            System.arraycopy(values, 0, newNode.values, 0, b);
            System.arraycopy(keys, b+1, newNode.keys, b, size-b-1);
            System.arraycopy(values, b+1, newNode.values, b, size-b-1);
            return newNode;
        }

        /**
            Copies the left half of this node's array of keys plus key in newNode.
        */
        private final LeafNode splitLeftAndPut(final Comparable key, final Object value) {
            int newSize = (getSize()/2)+1;
            LeafNode newNode = new LeafNode(newSize, weight);
            int i, a = 0, b = newSize-1;
            do {
                i = (a+b)/2;
                if (key.compareTo(keys[i]) < 0)
                    b = i-1;
                else
                    a = i+1;
            } while (a <= b);
            System.arraycopy(keys, 0, newNode.keys, 0, a);
            System.arraycopy(values, 0, newNode.values, 0, a);
            newNode.keys[a] = key;
            newNode.values[a] = value;
            System.arraycopy(keys, a, newNode.keys, a+1, newSize-1-a);
            System.arraycopy(values, a, newNode.values, a+1, newSize-1-a);
            return newNode;
        }

        /**
            Copies the right half of this node's array of keys plus key in newNode.
        */
        private final LeafNode splitRightAndPut(final Comparable key, final Object value) {
            int size = getSize();
            int newSize = (size/2)+1;
            LeafNode newNode = new LeafNode(newSize, weight);
            int newStart = (size+1)/2;
            int i, a = newSize-1, b = size-1;
            do {
                i = (a+b)/2;
                if (key.compareTo(keys[i]) < 0)
                    b = i-1;
                else
                    a = i+1;
            } while (a <= b);
            System.arraycopy(this.keys, newStart, newNode.keys, 0, a-newStart);
            System.arraycopy(this.values, newStart, newNode.values, 0, a-newStart);
            newNode.keys[a-newStart] = key;
            newNode.values[a-newStart] = value;
            System.arraycopy(this.keys, a, newNode.keys, a-newStart+1, size-a);
            System.arraycopy(this.values, a, newNode.values, a-newStart+1, size-a);
            return newNode;
        }

        /**
            Copies the left half of this node's array of keys in newNode.
        */
        private final LeafNode splitLeft() {
            int newSize = (getSize()+1)/2;
            LeafNode newNode = new LeafNode(newSize, weight);
            System.arraycopy(this.keys, 0, newNode.keys, 0, newSize);
            System.arraycopy(this.values, 0, newNode.values, 0, newSize);
            return newNode;
        }

        /**
            Copies the right half of this node's array of keys in newNode.
        */
        private final LeafNode splitRight() {
            int size = getSize();
            int newSize = (size+1)/2;
            LeafNode newNode = new LeafNode(newSize, weight);
            System.arraycopy(this.keys, size-newSize, newNode.keys, 0, newSize);
            System.arraycopy(this.values, size-newSize, newNode.values, 0, newSize);
            return newNode;
        }
    }

    public static final class InternalNode extends Node {
        public final Comparable key;
        public volatile Node left, right;
        public volatile boolean marked;
        public volatile Operation op;

        public InternalNode(final Comparable key, final int weight, final Node left, final Node right) {
            super(weight);
            this.key = key;
            this.left = left;
            this.right = right;
            this.op = dummy;
        }

        public final boolean hasChild(final Node node) {
            return node == left || node == right;
        }

        public Node copy(final int weight) {
            return new InternalNode(key, weight, left, right);
        }
    }

    public static final class Operation {
        final static int STATE_INPROGRESS = 0;
        final static int STATE_ABORTED = 1;
        final static int STATE_COMMITTED = 2;

        volatile Node subtree;
        volatile Node[] nodes;
        volatile Operation[] ops;
        volatile int state;
        volatile boolean allFrozen;

        public Operation() {            // create an inactive operation (a no-op) [[ we do this to avoid the overhead of inheritance ]]
            nodes = null; ops = null; subtree = null;
            this.state = STATE_ABORTED;   // cheap trick to piggy-back on a pre-existing check for active operations
        }
        
        public Operation(final Node[] nodes, final Operation[] ops, final Node subtree) {
            this.nodes = nodes;
            this.ops = ops;
            this.subtree = subtree;
        }
    }
    
    /**
     * 
     * Code for non atomic multi-element queries
     *
     */

    // Reference to a thread local variable that is used by
    // RangeScan to return the result of a range query
    private final ThreadLocal<RangeScanResultHolder> rangeScanResult = new ThreadLocal<RangeScanResultHolder>() {
        @Override
        protected RangeScanResultHolder initialValue() {
            return new RangeScanResultHolder();
        }
    };

    /**
        Represents a storage space where the result of a range query operation is saved
        Each thread gets a copy of this variable
    */
    private static final class RangeScanResultHolder {
        private Stack rsResult;

        RangeScanResultHolder() {
            rsResult = new Stack();
        }

        private static final class Stack {
            private final int INIT_SIZE = 16;
            private Object[] stackArray;
            private int head = 0;

            Stack() {
                stackArray = new Object[INIT_SIZE];
            }

            final void clear() {
                head = 0;
            }

            final Object[] getStackArray() {
                return stackArray;
            }

            final int getEffectiveSize() {
                return head;
            }

            final void push(final Object x) {
                if (head == stackArray.length) {
                    final Object[] newStackArray = new Object[stackArray.length*4];
                    System.arraycopy(stackArray, 0, newStackArray, 0, head);
                    stackArray = newStackArray;
                }
                stackArray[head] = x;
                ++head;
            }
        }
    }

    /**
        Executes the tree traversal for rangeScan.
        Precondition: node.versionSeq is not greater than seq

        @param node    the current node of the traversal
        @param ts      the timestamp number of rangeScan operation
        @param a       the lower limit of the range
        @param b       the upper limit of the range
        @param ret     contains the rangeScan result, i.e. all values that correspond to keys
                       held by nodes in the version-seq part of the tree
    */
    private final void scanHelper(final Node node, final K a, final K b, final boolean leftOpen, final boolean rightOpen, RangeScanResultHolder.Stack ret) {
        if(node == null) return;
        if (node instanceof LeafNode) {    // node is a leaf
            ((LeafNode)node).gatherKeys(a, b, leftOpen, rightOpen, ret);
        }
        else {
            InternalNode n = (InternalNode) node;
            if(!leftOpen && !rightOpen) {
                scanHelper(n.left, a, b, false, false, ret);
                scanHelper(n.right, a, b, false, false, ret);             
            }
            else if (n.key != null && a.compareTo((K) n.key) >= 0)           // node's key is below the lower limit of [a,b]
                scanHelper(n.right, a, b, leftOpen, rightOpen, ret);  // traverse its right subtree
            else if (n.key == null || b.compareTo((K) n.key) < 0)       // node's key is above the upper limit of [a,b]
                scanHelper(n.left, a, b, leftOpen, rightOpen, ret);   // traverse its left subtree
            else {
                // node is in [a,b] - traverse both of its subtrees
                scanHelper(n.left, a, b, leftOpen, false, ret);
                scanHelper(n.right, a, b, false, rightOpen, ret);
            }
        }
    }

    /**
        Implements the RangeScan operation.
        <p>
        Preconditions:
        <ul>
            <li> a and b cannot be null
            <li> a is less than or equal to b
        <ul>

        @param a  the lower limit of the range
        @param b  the upper limit of the range
        @return   all values of mappings with keys in range [a,b]
    */
    public final Object[] rangeScan(final K a, final K b) {
        //System.out.println(ts);
        // Get and initialize rangeScanResultHolder before the start of the tree traversal
        RangeScanResultHolder rangeScanResultHolder = rangeScanResult.get();
        rangeScanResultHolder.rsResult.clear();

        // Start the tree traversal
        scanHelper(root, a, b, true, true, rangeScanResultHolder.rsResult);
        // Get stack and its number of elements
        Object[] stackArray = rangeScanResultHolder.rsResult.getStackArray();
        int stackSize = rangeScanResultHolder.rsResult.getEffectiveSize();

        // Make a copy of the stack and return it
        Object[] returnArray = new Object[stackSize];
        for (int i = 0; i < stackSize; i++)
            returnArray[i] = stackArray[i];
        return returnArray;
    }

    // returns number of successors found at subtree rooted at node
    private final int successorsHelper(final Node node, final K key, final int numSuccessors, Element<K,V>[] elements, final int index) {
        if (node == null) return index;    
        if (node instanceof LeafNode) {    // node is a leaf
            LeafNode n = (LeafNode) node;
            int nodeIndex = n.lowerBound(key);
            int arrayIndex = index;
            while(nodeIndex < n.getSize() && arrayIndex < numSuccessors) {
                elements[arrayIndex] = new Element<K, V>((K) n.keys[nodeIndex], (V) n.values[nodeIndex]);
                // values[arrayIndex] = this.values[nodeIndex];
                nodeIndex++;
                arrayIndex++;
            }
            return arrayIndex;
        }
        else {
            InternalNode n = (InternalNode) node;
            if (n.key != null && key.compareTo((K) n.key) >= 0)
                return successorsHelper(n.right, key, numSuccessors, elements, index);  // traverse its right subtree
            else {
                int newIndex = successorsHelper(n.left, key, numSuccessors, elements, index);  // traverse its right subtree
                if(newIndex < numSuccessors)
                    newIndex = successorsHelper(n.right, key, numSuccessors, elements, newIndex);   // traverse its left subtree
                return newIndex;
            }
        }
    }

    /**
        @param key 
        @param numSuccessors 
        @return The first 'numSuccessors' key-value pairs that compare greater than or equal to 'key'
    */
    public final Element<K,V>[] successors(final K key, int numSuccessors) {
        if(key == null) return null;
        Element<K,V>[] elements = (Element<K,V>[]) Array.newInstance(Element.class,numSuccessors);
        int size = successorsHelper(root, key, numSuccessors, elements, 0);
        Element<K,V>[] returnArray = (Element<K,V>[]) Array.newInstance(Element.class, size);
        for(int i = 0; i < size; i++)
            returnArray[i] = elements[i];
        return returnArray;
    }

    /**
        @param keys
        @return An array of values corresponding to the keys in 'keys'. If a key does not appear
                in the tree, then the corresponding array entry is null.
    */
    public final Object[] multiSearch(final K[] keys) {
        if(keys == null) return null;

        int numKeys = keys.length;
        Object[] returnArray = new Object[numKeys];

        for(int i = 0; i < numKeys; i++)
            returnArray[i] = get(keys[i]);
        return returnArray;
    }

    public Element<K,V> findIfHelper(Node node, K a, K b, Predicate<Element<K,V>> p) {
        if(node == null) return null;
        if(node instanceof LeafNode) {
            LeafNode n = (LeafNode) node;
            int startIndex = n.lowerBound(a);
            Element<K,V> e = new Element<K,V>();
            for(int i = startIndex; i < n.getSize() && n.keys[i].compareTo(b) <= 0; i++) {
                e.set((K) n.keys[i], (V) n.values[i]);
                if(p.test(e)) return e;
            }
            return null;
        } else {
            InternalNode n = (InternalNode) node;
            if (n.key != null && a.compareTo((K) n.key) >= 0)           // node's key is below the lower limit of [a,b]
                return findIfHelper(n.right, a, b, p);  // traverse its right subtree
            else if (n.key == null || b.compareTo((K) n.key) < 0)       // node's key is above the upper limit of [a,b]
                return findIfHelper(n.left, a, b, p);   // traverse its left subtree
            else {
                // node is in [a,b] - traverse both of its subtrees
                Element<K,V> e = findIfHelper(n.left, a, b, p);
                if(e != null) return e;
                return findIfHelper(n.right, a, b, p);
            }
        }
    }

    /**
        @param lo
        @param hi
        @param p
        @return returns the first key-value pair in the range [lo, hi] that statisfies p
    */
    public Element<K,V> findIf(K lo, K hi, Predicate<Element<K,V>> p) {
        Element<K,V> retValue = findIfHelper(root, lo, hi, p);
        return retValue;
    }

    /**
     *
     * Code for debugging
     *
     */
     
    private int countNodes(final Node node) {
        if (node == null) return 0;
        if (node instanceof LeafNode) return ((LeafNode)node).getSize();
        InternalNode n = (InternalNode) node;
        return 1 + countNodes(n.left) + countNodes(n.right);
    }

    public final int getNumberOfNodes() {
        return countNodes(root);
    }

    private int sumDepths(final Node node, final int depth) {
        if (node == null) return 0;
        if (node instanceof LeafNode) return 1;
        InternalNode n = (InternalNode) node;
        return sumDepths(n.left, depth+1) + sumDepths(n.right, depth+1);
    }

    public final int getSumOfDepths() {
        return sumDepths(root, 0);
    }
    
    private long getKeysum(final Node node) {
        if (node == null) return 0;
        if (node instanceof LeafNode) return ((LeafNode)node).getSum();
        return getKeysum(((InternalNode)node).left) + getKeysum(((InternalNode)node).right);
    }
    
    // Returns the sum of keys in the tree (the keys in leaves)
    public final long getKeysum() {
        return getKeysum(((InternalNode)((InternalNode)root).left).left);
    }
    
    /**
     *
     * Computer generated code
     *
     */
    
    private Operation createOverweightRightOp(final Node f,
                                             final Node fX,
                                             final Node fXX,
                                             final Node fXXR,
                                             final Operation opf,
                                             final Operation opfX,
                                             final Operation opfXX,
                                             final Operation opfXXR,
                                             final Node fXR,
                                             final Node fXL,
                                             final Node fXXL,
                                             final boolean fXXright) {
        if (fXXL.weight == 0) {
            if (fXX.weight == 0) {
                if (fXXright) {
                    if (fXL.weight == 0) {
                        final Operation opfXL = weakLLX(fXL);
                        if (opfXL == null) return null;
                        return createBlkOp(new Node[] {f, fX, fXL, fXX},
                                           new Operation[] {opf, opfX, opfXL, opfXX});
                    } else { // assert: fXL.weight > 0
                        final Operation opfXXL = weakLLX(fXXL);
                        if (opfXXL == null) return null;
                        return createRb2SymOp(new Node[] {f, fX, fXX, fXXL},
                                           new Operation[] {opf, opfX, opfXX, opfXXL});
                    }
                } else { // assert: fXX == fXL
                    if (fXR.weight == 0) {
                        final Operation opfXR = weakLLX(fXR);
                        if (opfXR == null) return null;
                        return createBlkOp(new Node[] {f, fX, fXX, fXR},
                                           new Operation[] {opf, opfX, opfXX, opfXR});
                    } else {
                        return createRb1Op(new Node[] {f, fX, fXX},
                                              new Operation[] {opf, opfX, opfXX});
                    }
                }
            } else { // assert: fXX.weight > 0
                final Operation opfXXL = weakLLX(fXXL);
                if (opfXXL == null) return null;
                
                final Node fXXLR = ((InternalNode)fXXL).right;
                final Operation opfXXLR = weakLLX(fXXLR);
                if (opfXXLR == null) return null;
                
                if (fXXLR.weight > 1) {
                    return createW1SymOp(new Node[] {fX, fXX, fXXL, fXXR, fXXLR},
                                      new Operation[] {opfX, opfXX, opfXXL, opfXXR, opfXXLR});
                } else if (fXXLR.weight == 0) {
                    return createRb2Op(new Node[] {fX, fXX, fXXL, fXXLR},
                                          new Operation[] {opfX, opfXX, opfXXL, opfXXLR});
                } else { // assert: fXXLR.weight == 1
                    final Node fXXLRL = ((InternalNode)fXXLR).left;
                    if (fXXLRL == null) return null;
                    if (fXXLRL.weight == 0) {
                        final Operation opfXXLRL = weakLLX(fXXLRL);
                        if (opfXXLRL == null) return null;
                        return createW4SymOp(new Node[] {fX, fXX, fXXL, fXXR, fXXLR, fXXLRL},
                                          new Operation[] {opfX, opfXX, opfXXL, opfXXR, opfXXLR, opfXXLRL});
                    } else { // assert: fXXLRL.weight > 0
                        final Node fXXLRR = ((InternalNode)fXXLR).right;
                        if (fXXLRR == null) return null;
                        if (fXXLRR.weight == 0) {
                            final Operation opfXXLRR = weakLLX(fXXLRR);
                            if (opfXXLRR == null) return null;
                            return createW3SymOp(new Node[] {fX, fXX, fXXL, fXXR, fXXLR, fXXLRR},
                                              new Operation[] {opfX, opfXX, opfXXL, opfXXR, opfXXLR, opfXXLRR});
                        } else { // assert: fXXLRR.weight > 0
                            return createW2SymOp(new Node[] {fX, fXX, fXXL, fXXR, fXXLR},
                                              new Operation[] {opfX, opfXX, opfXXL, opfXXR, opfXXLR});
                        }
                    }
                }
            }
        } else if (fXXL.weight == 1) {
            final Operation opfXXL = weakLLX(fXXL);
            if (opfXXL == null) return null;
            
            final Node fXXLR = ((InternalNode)fXXL).right;
            if (fXXLR == null) return null;
            final Node fXXLL = ((InternalNode)fXXL).left; // note: if fXXLL is null, then fXXLR is null, since tree is always a full binary tree, and children of leaves don't change
            if (fXXLL.weight == 0) {
                final Operation opfXXLL = weakLLX(fXXLL);
                if (opfXXLL == null) return null;
                return createW5SymOp(new Node[] {fX, fXX, fXXL, fXXR, fXXLL},
                                  new Operation[] {opfX, opfXX, opfXXL, opfXXR, opfXXLL});
            } else if (fXXLR.weight == 0) {
                final Operation opfXXLR = weakLLX(fXXLR);
                if (opfXXLR == null) return null;
                return createW6SymOp(new Node[] {fX, fXX, fXXL, fXXR, fXXLR},
                                  new Operation[] {opfX, opfXX, opfXXL, opfXXR, opfXXLR});
            } else {
                return createPushSymOp(new Node[] {fX, fXX, fXXL, fXXR},
                                    new Operation[] {opfX, opfXX, opfXXL, opfXXR});
            }
        } else {
            final Operation opfXXL = weakLLX(fXXL);
            if (opfXXL == null) return null;
            return createW7SymOp(new Node[] {fX, fXX, fXXL, fXXR},
                              new Operation[] {opfX, opfXX, opfXXL, opfXXR});
        }
    }
    
    private Operation createBlkOp(final Node[] nodes, final Operation[] ops) {
        final Node nodeXL = nodes[2].copy(1);
        final Node nodeXR = nodes[3].copy(1);
        final int weight = (isSentinel(nodes[1]) ? 1 : nodes[1].weight-1); // root of old subtree is a sentinel
        final Node nodeX = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, weight, nodeXL, nodeXR);
        return new Operation(nodes, ops, nodeX);
    }
    
    private Operation createRb1Op(final Node[] nodes, final Operation[] ops) {
        final Node nodeXR = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 0, ((InternalNode)nodes[2]).right, ((InternalNode)nodes[1]).right);
        final int weight = nodes[1].weight;
        final Node nodeX = new InternalNode((Comparable) ((InternalNode)nodes[2]).key, weight, ((InternalNode)nodes[2]).left, nodeXR);
        return new Operation(nodes, ops, nodeX);
    }
    
    private Operation createRb2Op(final Node[] nodes, final Operation[] ops) {
        final Node nodeXL = new InternalNode((Comparable) ((InternalNode)nodes[2]).key, 0, ((InternalNode)nodes[2]).left, ((InternalNode)nodes[3]).left);
        final Node nodeXR = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 0, ((InternalNode)nodes[3]).right, ((InternalNode)nodes[1]).right);
        final int weight = nodes[1].weight;
        final Node nodeX = new InternalNode((Comparable) ((InternalNode)nodes[3]).key, weight, nodeXL, nodeXR);
        return new Operation(nodes, ops, nodeX);
    }
    
    private Operation createPushOp(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXL = nodes[2].copy(nodes[2].weight-1);
        final Node nodeXXR = nodes[3].copy(0);
        final int weight = (isSentinel(nodes[1]) ? 1 : nodes[1].weight+1); // root of old subtree is a sentinel
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, weight, nodeXXL, nodeXXR);
        return new Operation(nodes, ops, nodeXX);
    }
    
    private Operation createW1Op(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXLL = nodes[2].copy(nodes[2].weight-1);
        final Node nodeXXLR = nodes[4].copy(nodes[4].weight-1);
        final Node nodeXXL = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 1, nodeXXLL, nodeXXLR);
        final int weight = nodes[1].weight;
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[3]).key, weight, nodeXXL, ((InternalNode)nodes[3]).right);
        return new Operation(nodes, ops, nodeXX);
    }
    
    private Operation createW2Op(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXLL = nodes[2].copy(nodes[2].weight-1);
        final Node nodeXXLR = nodes[4].copy(0);
        final Node nodeXXL = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 1, nodeXXLL, nodeXXLR);
        final int weight = nodes[1].weight;
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[3]).key, weight, nodeXXL, ((InternalNode)nodes[3]).right);
        return new Operation(nodes, ops, nodeXX);
    }
    
    private Operation createW3Op(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXLLL = nodes[2].copy(nodes[2].weight-1);
        final Node nodeXXLL = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 1, nodeXXLLL, ((InternalNode)nodes[5]).left);
        final Node nodeXXLR = new InternalNode((Comparable) ((InternalNode)nodes[4]).key, 1, ((InternalNode)nodes[5]).right, ((InternalNode)nodes[4]).right);
        final Node nodeXXL = new InternalNode((Comparable) ((InternalNode)nodes[5]).key, 0, nodeXXLL, nodeXXLR);
        final int weight = nodes[1].weight;
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[3]).key, weight, nodeXXL, ((InternalNode)nodes[3]).right);
        return new Operation(nodes, ops, nodeXX);
    }
    
    private Operation createW4Op(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXLL = nodes[2].copy(nodes[2].weight-1);
        final Node nodeXXL = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 1, nodeXXLL, ((InternalNode)nodes[4]).left);
        final Node nodeXXRL = nodes[5].copy(1);
        final Node nodeXXR = new InternalNode((Comparable) ((InternalNode)nodes[3]).key, 0, nodeXXRL, ((InternalNode)nodes[3]).right);
        final int weight = nodes[1].weight;
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[4]).key, weight, nodeXXL, nodeXXR);
        return new Operation(nodes, ops, nodeXX);
    }
    
    private Operation createW5Op(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXLL = nodes[2].copy(nodes[2].weight-1);
        final Node nodeXXL = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 1, nodeXXLL, ((InternalNode)nodes[3]).left);
        final Node nodeXXR = nodes[4].copy(1);
        final int weight = nodes[1].weight;
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[3]).key, weight, nodeXXL, nodeXXR);
        return new Operation(nodes, ops, nodeXX);
    }
    
    private Operation createW6Op(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXLL = nodes[2].copy(nodes[2].weight-1);
        final Node nodeXXL = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 1, nodeXXLL, ((InternalNode)nodes[4]).left);
        final Node nodeXXR = new InternalNode((Comparable) ((InternalNode)nodes[3]).key, 1, ((InternalNode)nodes[4]).right, ((InternalNode)nodes[3]).right);
        final int weight = nodes[1].weight;
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[4]).key, weight, nodeXXL, nodeXXR);
        return new Operation(nodes, ops, nodeXX);
    }
    
    private Operation createW7Op(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXL = nodes[2].copy(nodes[2].weight-1);
        final Node nodeXXR = nodes[3].copy(nodes[3].weight-1);
        final int weight = (isSentinel(nodes[1]) ? 1 : nodes[1].weight+1); // root of old subtree is a sentinel
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, weight, nodeXXL, nodeXXR);
        return new Operation(nodes, ops, nodeXX);
    }
    
    private Operation createRb1SymOp(final Node[] nodes, final Operation[] ops) {
        final Node nodeXL = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 0, ((InternalNode)nodes[1]).left, ((InternalNode)nodes[2]).left);
        final int weight = nodes[1].weight;
        final Node nodeX = new InternalNode((Comparable) ((InternalNode)nodes[2]).key, weight, nodeXL, ((InternalNode)nodes[2]).right);
        return new Operation(nodes, ops, nodeX);
    }
    
    private Operation createRb2SymOp(final Node[] nodes, final Operation[] ops) {
        final Node nodeXL = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 0, ((InternalNode)nodes[1]).left, ((InternalNode)nodes[3]).left);
        final Node nodeXR = new InternalNode((Comparable) ((InternalNode)nodes[2]).key, 0, ((InternalNode)nodes[3]).right, ((InternalNode)nodes[2]).right);
        final int weight = nodes[1].weight;
        final Node nodeX = new InternalNode((Comparable) ((InternalNode)nodes[3]).key, weight, nodeXL, nodeXR);
        return new Operation(nodes, ops, nodeX);
    }
    
    private Operation createPushSymOp(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXL = nodes[2].copy(0);
        final Node nodeXXR = nodes[3].copy(nodes[3].weight-1);
        final int weight = (isSentinel(nodes[1]) ? 1 : nodes[1].weight+1); // root of old subtree is a sentinel
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, weight, nodeXXL, nodeXXR);
        return new Operation(nodes, ops, nodeXX);
    }
    
    private Operation createW1SymOp(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXRL = nodes[4].copy(nodes[4].weight-1);
        final Node nodeXXRR = nodes[3].copy(nodes[3].weight-1);
        final Node nodeXXR = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 1, nodeXXRL, nodeXXRR);
        final int weight = nodes[1].weight;
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[2]).key, weight, ((InternalNode)nodes[2]).left, nodeXXR);
        return new Operation(nodes, ops, nodeXX);
    }
    
    private Operation createW2SymOp(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXRL = nodes[4].copy(0);
        final Node nodeXXRR = nodes[3].copy(nodes[3].weight-1);
        final Node nodeXXR = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 1, nodeXXRL, nodeXXRR);
        final int weight = nodes[1].weight;
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[2]).key, weight, ((InternalNode)nodes[2]).left, nodeXXR);
        return new Operation(nodes, ops, nodeXX);
    }
    
    private Operation createW3SymOp(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXRL = new InternalNode((Comparable) ((InternalNode)nodes[4]).key, 1, ((InternalNode)nodes[4]).left, ((InternalNode)nodes[5]).left);
        final Node nodeXXRRR = nodes[3].copy(nodes[3].weight-1);
        final Node nodeXXRR = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 1, ((InternalNode)nodes[5]).right, nodeXXRRR);
        final Node nodeXXR = new InternalNode((Comparable) ((InternalNode)nodes[5]).key, 0, nodeXXRL, nodeXXRR);
        final int weight = nodes[1].weight;
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[2]).key, weight, ((InternalNode)nodes[2]).left, nodeXXR);
        return new Operation(nodes, ops, nodeXX);
    }
    
    private Operation createW4SymOp(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXLR = nodes[5].copy(1);
        final Node nodeXXL = new InternalNode((Comparable) ((InternalNode)nodes[2]).key, 0, ((InternalNode)nodes[2]).left, nodeXXLR);
        final Node nodeXXRR = nodes[3].copy(nodes[3].weight-1);
        final Node nodeXXR = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 1, ((InternalNode)nodes[4]).right, nodeXXRR);
        final int weight = nodes[1].weight;
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[4]).key, weight, nodeXXL, nodeXXR);
        return new Operation(nodes, ops, nodeXX);
    }
    
    private Operation createW5SymOp(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXL = nodes[4].copy(1);
        final Node nodeXXRR = nodes[3].copy(nodes[3].weight-1);
        final Node nodeXXR = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 1, ((InternalNode)nodes[2]).right, nodeXXRR);
        final int weight = nodes[1].weight;
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[2]).key, weight, nodeXXL, nodeXXR);
        return new Operation(nodes, ops, nodeXX);
    }
    
    private Operation createW6SymOp(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXL = new InternalNode((Comparable) ((InternalNode)nodes[2]).key, 1, ((InternalNode)nodes[2]).left, ((InternalNode)nodes[4]).left);
        final Node nodeXXRR = nodes[3].copy(nodes[3].weight-1);
        final Node nodeXXR = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, 1, ((InternalNode)nodes[4]).right, nodeXXRR);
        final int weight = nodes[1].weight;
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[4]).key, weight, nodeXXL, nodeXXR);
        return new Operation(nodes, ops, nodeXX);
    }

    private Operation createW7SymOp(final Node[] nodes, final Operation[] ops) {
        final Node nodeXXL = nodes[2].copy(nodes[2].weight-1);
        final Node nodeXXR = nodes[3].copy(nodes[3].weight-1);
        final int weight = (isSentinel(nodes[1]) ? 1 : nodes[1].weight+1); // root of old subtree is a sentinel
        final Node nodeXX = new InternalNode((Comparable) ((InternalNode)nodes[1]).key, weight, nodeXXL, nodeXXR);
        return new Operation(nodes, ops, nodeXX);
    }

}