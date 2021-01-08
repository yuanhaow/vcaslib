package algorithms.efrbbst;

/*
Concurrent, Non-blocking Binary Search Tree with Batched Leaves

This is an implementation of the BST-64 algorithm mentioned in the paper
    "Constant-Time Snapshots with Applications to Concurrent Data Structures"
    Yuanhao Wei, Naama Ben-David, Guy E. Blelloch, Panagiota Fatourou, Eric Ruppert, Yihan Sun
    PPoPP 2021

The data structure supports linearizable get(), containsKey(), putIfAbsent(), remove(). 
All operations are lock-free.

Copyright (C) 2021 Yuanhao Wei

This implementation based on the following non-blocking BST implementation by Trevor Brown:
https://bitbucket.org/trbot86/implementations/src/master/java/src/algorithms/published/LockFreeBSTMap.java

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class LockFreeBatchBSTMap<K extends Comparable<? super K>, V> {

    private final int BATCHING_DEGREE;

    //--------------------------------------------------------------------------------
    // Class: Node
    //--------------------------------------------------------------------------------
    public static abstract class Node {
    }

    public static final class LeafNode extends Node {
        public Comparable[] keys;
        public Object[] values;

        LeafNode(final int size) {
            this.keys = new Comparable[size];
            this.values = new Object[size];
        } 

        public Node copy() {
            int size = getSize();
            LeafNode newNode = new LeafNode(size);
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
            LeafNode newNode = new LeafNode(size+1);
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
            LeafNode newNode = new LeafNode(size-1);
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
            LeafNode newNode = new LeafNode(newSize);
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
            LeafNode newNode = new LeafNode(newSize);
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
            LeafNode newNode = new LeafNode(newSize);
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
            LeafNode newNode = new LeafNode(newSize);
            System.arraycopy(this.keys, size-newSize, newNode.keys, 0, newSize);
            System.arraycopy(this.values, size-newSize, newNode.values, 0, newSize);
            return newNode;
        }
    }

    public static final class InternalNode extends Node {
        public final Comparable key;
        public volatile Node left, right;
        volatile Info info;

        public InternalNode(final Comparable key, final Node left, final Node right) {
            this.key = key;
            this.left = left;
            this.right = right;
            this.info = null;
        }

        public final boolean hasChild(final Node node) {
            return node == left || node == right;
        }

        public Node copy() {
            return new InternalNode(key, left, right);
        }
    }


    //--------------------------------------------------------------------------------
    // Class: Info, DInfo, IInfo, Mark, Clean
    // May 25th: trying to make CAS to update field static
    // instead of using <state, Info>, we extends Info to all 4 states
    // to see a state of a node, see what kind of Info class it has
    //--------------------------------------------------------------------------------
    protected static abstract class Info {
    }

    protected final static class DInfo extends Info {
        final InternalNode p;
        final LeafNode l;
        final InternalNode gp;
        final Info pinfo;
        final Comparable keyToDelete;

        DInfo(final LeafNode leaf, final InternalNode parent, final InternalNode grandparent, final Info pinfo, final Comparable key) {
            this.p = parent;
            this.l = leaf;
            this.gp = grandparent;
            this.pinfo = pinfo;
            this.keyToDelete = key;
        }
    }

    protected final static class IInfo extends Info {
        final InternalNode p;
        final LeafNode l;
        final Node newInternal;

        IInfo(final LeafNode leaf, final InternalNode parent, final Node newInternal){
            this.p = parent;
            this.l = leaf;
            this.newInternal = newInternal;
        }
    }

    protected final static class Mark extends Info {
        final DInfo dinfo;

        Mark(final DInfo dinfo) {
            this.dinfo = dinfo;
        }
    }

    protected final static class Clean extends Info {}

//--------------------------------------------------------------------------------
// DICTIONARY
//--------------------------------------------------------------------------------
    public static final AtomicReferenceFieldUpdater<InternalNode, Node> leftUpdater = AtomicReferenceFieldUpdater.newUpdater(InternalNode.class, Node.class, "left");
    public static final AtomicReferenceFieldUpdater<InternalNode, Node> rightUpdater = AtomicReferenceFieldUpdater.newUpdater(InternalNode.class, Node.class, "right");
    private static final AtomicReferenceFieldUpdater<InternalNode, Info> infoUpdater = AtomicReferenceFieldUpdater.newUpdater(InternalNode.class, Info.class, "info");

    final InternalNode root;

    public LockFreeBatchBSTMap(final int BATCHING_DEGREE) {
        System.out.println("BATCHING DEGREE: " + BATCHING_DEGREE);
        this.BATCHING_DEGREE = BATCHING_DEGREE;
        // to avoid handling special case when <= 2 nodes,
        // create 2 dummy nodes, both contain key null
        // All real keys inside BST are required to be non-null
        root = new InternalNode(null, new InternalNode(null, new LeafNode(0), new LeafNode(0)), new LeafNode(0));
    }

    public LockFreeBatchBSTMap() {
        this(16);
    }

//--------------------------------------------------------------------------------
// PUBLIC METHODS:
// - find   : boolean
// - insert : boolean
// - delete : boolean
//--------------------------------------------------------------------------------

    /** PRECONDITION: k CANNOT BE NULL **/
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

    // Insert key to dictionary, returns the previous value associated with the specified key,
    // or null if there was no mapping for the key
    /** PRECONDITION: k CANNOT BE NULL **/
    public final V putIfAbsent(final K key, final V value){
        Node newInternal;
        LeafNode newLeft, newRight;

        /** SEARCH VARIABLES **/
        InternalNode p;
        Info pinfo;
        LeafNode l;
        Node n;
        /** END SEARCH VARIABLES **/

        //int counter = 0;
        while (true) {
            //counter++;
            //if(counter > 1000) System.out.println("insert loop: " + counter);
            /** SEARCH **/
            p = root;
            n = p.left;
            while(n instanceof InternalNode) {
                p = (InternalNode) n;
                n = (p.key == null || key.compareTo((K) p.key) < 0) ? p.left : p.right;
            }
            l = (LeafNode) n;
            pinfo = p.info;                             // read pinfo once instead of every iteration
            if (l != p.left && l != p.right) continue;  // then confirm the child link to l is valid
                                                        // (just as if we'd read p's info field before the reference to l)
            /** END SEARCH **/

            V ret = (V) l.getValue(key);
            if (ret != null) {
                return ret; // key already in the tree, no duplicate allowed
            } else if (!(pinfo == null || pinfo.getClass() == Clean.class)) {
                //if(counter > 1000) System.out.println("insert help1");
                help(pinfo);
            } else {
                if(!l.isFull(BATCHING_DEGREE)) {
                    newInternal = l.put(key, value);
                } else {
                    if(l.shouldBePutLeft(key)) {
                        newLeft = l.splitLeftAndPut(key, value);
                        newRight = l.splitRight();
                    } else {
                        newLeft = l.splitLeft();
                        newRight = l.splitRightAndPut(key, value);                        
                    }
                    newInternal = new InternalNode(newRight.keys[0], newLeft, newRight);
                }

                final IInfo newPInfo = new IInfo(l, p, newInternal);

                // try to IFlag parent
                if (infoUpdater.compareAndSet(p, pinfo, newPInfo)) {
                    helpInsert(newPInfo);
                    return null;
                } else {
                    // if fails, help the current operation
                    // [CHECK]
                    // need to get the latest p.info since CAS doesnt return current value
                    //if(counter > 1000) System.out.println("insert help2");
                    help(p.info);
                }
            }
        }
    }

    // Delete key from dictionary, return the associated value when successful, null otherwise
    /** PRECONDITION: k CANNOT BE NULL **/
    public final V remove(final K key){
        /** SEARCH VARIABLES **/
        InternalNode gp;
        Info gpinfo;
        InternalNode p;
        Info pinfo;
        LeafNode l;
        Node n;
        /** END SEARCH VARIABLES **/
        
        while (true) {

            /** SEARCH **/
            gp = null;
            gpinfo = null;
            p = root;
            pinfo = p.info;
            n = p.left;
            while(n instanceof InternalNode) {
                gp = p;
                p = (InternalNode) n;
                n = (p.key == null || key.compareTo((K) p.key) < 0) ? p.left : p.right;
            }
            l = (LeafNode) n;

            // note: gp can be null here, because clearly the root.left.left == null
            //       when the tree is empty. however, in this case, l.key will be null,
            //       and the function will return null, so this does not pose a problem.
            if (gp != null) {
                gpinfo = gp.info;                               // - read gpinfo once instead of every iteration
                if (p != gp.left && p != gp.right) continue;    //   then confirm the child link to p is valid
                pinfo = p.info;                                 //   (just as if we'd read gp's info field before the reference to p)
                if (l != p.left && l != p.right) continue;      // - do the same for pinfo and l
            }
            /** END SEARCH **/

            V ret = (V) l.getValue(key);
            if (ret == null) {
                return null;
            }
            if (!(gpinfo == null || gpinfo.getClass() == Clean.class)) {
                help(gpinfo);
            } else if (!(pinfo == null || pinfo.getClass() == Clean.class)) {
                help(pinfo);
            } else {
                // try to DFlag grandparent
                final DInfo newGPInfo = new DInfo(l, p, gp, pinfo, key);
                
                if (infoUpdater.compareAndSet(gp, gpinfo, newGPInfo)) {
                    if (helpDelete(newGPInfo)) return ret;
                } else {
                    // if fails, help grandparent with its latest info value
                    help(gp.info);
                }
            }
        }
    }

//--------------------------------------------------------------------------------
// PRIVATE METHODS
// - helpInsert
// - helpDelete
//--------------------------------------------------------------------------------

    private void helpInsert(final IInfo info){
        (info.p.left == info.l ? leftUpdater : rightUpdater).compareAndSet(info.p, info.l, info.newInternal);
        infoUpdater.compareAndSet(info.p, info, new Clean());
    }

    private boolean helpDelete(final DInfo info){
        final boolean result;

        result = infoUpdater.compareAndSet(info.p, info.pinfo, new Mark(info));
        final Info currentPInfo = info.p.info;
        // if  CAS succeed or somebody else already suceed helping, the helpMarked
        if (result || (currentPInfo.getClass() == Mark.class && ((Mark) currentPInfo).dinfo == info)) {
            helpMarked(info);
            return true;
        } else {
            help(currentPInfo);
            infoUpdater.compareAndSet(info.gp, info, new Clean());
            return false;
        }
    }

    private void help(final Info info) {
        if (info.getClass() == IInfo.class)     helpInsert((IInfo) info);
        else if(info.getClass() == DInfo.class) helpDelete((DInfo) info);
        else if(info.getClass() == Mark.class)  helpMarked(((Mark)info).dinfo);
    }

    private void helpMarked(final DInfo info) {
        final Node newNode;

        if(info.l.getSize() == 1 && info.l != ((InternalNode)root.left).left) {
            newNode = (info.p.right == info.l) ? info.p.left : info.p.right;
        } else {
            Node newLeft, newRight;
            if(info.p.left == info.l) {
                newLeft = info.l.remove(info.keyToDelete);
                newRight = info.p.right;
            } else {
                newLeft = info.p.left;
                newRight = info.l.remove(info.keyToDelete);
            }
            newNode = new InternalNode(info.p.key, newLeft, newRight);
        }
        (info.gp.left == info.p ? leftUpdater : rightUpdater).compareAndSet(info.gp, info.p, newNode);
        infoUpdater.compareAndSet(info.gp, info, new Clean());   
    }

    /**
     *
     * DEBUG CODE (FOR TESTBED)
     *
     */

    // private int sumDepths(Node node, int depth) {
    //     if (node == null) return 0;
    //     if (node.left == null && node.key != null) return depth;
    //     return sumDepths(node.left, depth+1) + sumDepths(node.right, depth+1);
    // }

    // public final int getSumOfDepths() {
    //     return sumDepths(root, 0);
    // }

    public long getSumOfKeys() {
        long result = getKeysum(root);
        return result;
    }

    private long getKeysum(final Node node) {
        if (node == null) return 0;
        if (node instanceof LeafNode) return ((LeafNode)node).getSum();
        return getKeysum(((InternalNode)node).left) + getKeysum(((InternalNode)node).right);
    }

    /**
     * size() is NOT a constant time method, and the result is only guaranteed to
     * be consistent if no concurrent updates occur.
     * Note: linearizable size() and iterators can be implemented, so contact
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

}