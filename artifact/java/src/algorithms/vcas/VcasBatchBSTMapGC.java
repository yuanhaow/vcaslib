package algorithms.vcas;

/*
Concurrent, Non-blocking Binary Search Tree with Constant Time Snapshotting and Batched Leaves

This is an implementation of the VcasBST-64 algorithm described in the paper
    "Constant-Time Snapshots with Applications to Concurrent Data Structures"
    Yuanhao Wei, Naama Ben-David, Guy E. Blelloch, Panagiota Fatourou, Eric Ruppert, Yihan Sun
    PPoPP 2021

The data structure supports linearizable get(), containsKey(), putIfAbsent(), remove(), and 
rangeScan(). All operations are lock-free and rangeScan() is also wait-free.

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

import algorithms.vcas.Camera;
import main.support.Epoch;
import main.support.Reclaimable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

// import algorithms.vcas.VcasAtomicReferenceFieldUpdater;

public class VcasBatchBSTMapGC<K extends Comparable<? super K>, V> {

    private final int BATCHING_DEGREE;
    public static final Epoch<Node> epoch = new Epoch();

    public VcasBatchBSTMapGC(final int BATCHING_DEGREE) {
        this.BATCHING_DEGREE = BATCHING_DEGREE;
        System.out.println("BATCHING DEGREE: " + BATCHING_DEGREE);
        root = new InternalNode(null, new InternalNode(null, new LeafNode(0), new LeafNode(0)), new LeafNode(0));
    }

    public VcasBatchBSTMapGC() {
        this(16);
    }

    public static abstract class Node extends Reclaimable {
        public volatile long ts;
        private volatile Node nextv;

        public static final long TBD = -1;
        public static final AtomicLongFieldUpdater<Node> tsUpdater = AtomicLongFieldUpdater.newUpdater(Node.class, "ts");
        public static final AtomicReferenceFieldUpdater<Node, Node> nextvUpdater = AtomicReferenceFieldUpdater.newUpdater(Node.class, Node.class, "nextv");
        public static final Node dummyNextv = new InternalNode(null, null, null);

        public Node() {
            this.nextv = dummyNextv;
            this.ts = TBD;
        }

        public void init() {
            if(nextv == dummyNextv) {
                initTS();
                nextv = null;
            }
        }

        public void initTS() {
          if(ts == TBD) {
            long curTS = Camera.getTimestamp();
            tsUpdater.compareAndSet(this, TBD, curTS);
          }
        }

        public void reclaim() {
            nextv = null;
        }

        public abstract Node copy();
    }

    public static final class LeafNode extends Node {
        public Comparable[] keys;
        public Object[] values;

        LeafNode(final int size) {
            super();
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
        
        public static final AtomicReferenceFieldUpdater<InternalNode, Node> updateLeft = AtomicReferenceFieldUpdater.newUpdater(InternalNode.class, Node.class, "left");
        public static final AtomicReferenceFieldUpdater<InternalNode, Node> updateRight = AtomicReferenceFieldUpdater.newUpdater(InternalNode.class, Node.class, "right");

        public InternalNode(final Comparable key, final Node left, final Node right) {
            super();
            this.key = key;
            this.left = left;
            this.right = right;
            this.info = null;
            if(left != null) left.init();
            if(right != null) right.init();
        }

        Node getLeft() {
            Node head = left;
            if(head == null) return null;
            head.initTS();
            return head;
        }

        Node getRight() {
            Node head = right;
            if(head == null) return null;
            head.initTS();
            return head;
        }

        Node getLeft(long ts) {
            Node node = left;
            if(node == null) return null;
            node.initTS();
            //System.out.println("ts = " + ts + ", node.ts = " + node.getTS() + ", node.nextv = " + node.getNext());
            while(node != null && node.ts > ts) {
                //System.out.println("ts = " + ts + ", node.ts = " + node.getTS() + ", node.nextv = " + node.getNext());
                node = node.nextv;
            }
            return node;
        }

        Node getRight(long ts) {
            Node node = right;
            if(node == null) return null;
            node.initTS();
            //System.out.println("ts = " + ts + ", node.ts = " + node.getTS() + ", node.nextv = " + node.getNext());
            while(node != null && node.ts > ts) {
                //System.out.println("ts = " + ts + ", node.ts = " + node.getTS() + ", node.nextv = " + node.getNext());
                node = node.nextv;
            }
            return node;
        }

        boolean compareAndSetLeft(final Node oldV, Node newV) {
            Node head = left; // head cannot be null
            if(head != null) {
                head.initTS();
                Node headNext = head.nextv;
                if(headNext != null && head.ts == headNext.ts)
                    head.nextv = headNext.nextv;
            }
            if(head != oldV) return false;
            if(newV == oldV) return true;
            nextvUpdater.compareAndSet(newV, dummyNextv, oldV);
            // newV.nextv = oldV;
            // newV.ts = TBD;
            
            if(updateLeft.compareAndSet(this, head, newV)) {
                newV.initTS();
                if(head != null && newV.ts == head.ts)
                    newV.nextv = head.nextv;
                if(newV.nextv != null) {
                    epoch.retire(newV);
                }
                return true;
            } else {
                head = left;
                head.initTS();
                return false;
            }
        }

        boolean compareAndSetRight(final Node oldV, Node newV) {
            Node head = right; // head cannot be null
            if(head != null) {
                head.initTS();
                Node headNext = head.nextv;
                if(headNext != null && head.ts == headNext.ts)
                    head.nextv = headNext.nextv;
            }
            if(head != oldV) return false;
            if(newV == oldV) return true;
            nextvUpdater.compareAndSet(newV, dummyNextv, oldV);
            // newV.nextv = oldV;
            // newV.ts = TBD;
            
            if(updateRight.compareAndSet(this, head, newV)) {
                newV.initTS();
                if(head != null && newV.ts == head.ts)
                    newV.nextv = head.nextv;
                if(newV.nextv != null) {
                    epoch.retire(newV);
                }
                return true;
            } else {
                head = right;
                head.initTS();
                return false;
            }
        }

        public final boolean hasChild(final Node node) {
            return node == getLeft() || node == getRight();
        }

        public Node copy() {
            return new InternalNode(key, getLeft(), getRight());
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

    protected final static class Flag extends Info {
        final DInfo dinfo;

        Flag(final DInfo dinfo) {
            this.dinfo = dinfo;
        }
    }

    protected final static class Clean extends Info {}

//--------------------------------------------------------------------------------
// DICTIONARY
//--------------------------------------------------------------------------------
    private static final AtomicReferenceFieldUpdater<InternalNode, Node> leftUpdater = AtomicReferenceFieldUpdater.newUpdater(InternalNode.class, Node.class, "left");
    private static final AtomicReferenceFieldUpdater<InternalNode, Node> rightUpdater = AtomicReferenceFieldUpdater.newUpdater(InternalNode.class, Node.class, "right");
    private static final AtomicReferenceFieldUpdater<InternalNode, Info> infoUpdater = AtomicReferenceFieldUpdater.newUpdater(InternalNode.class, Info.class, "info");

    final InternalNode root;

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
        InternalNode p = (InternalNode) root.getLeft();
        while(true) {
            Node l = (p.key == null || key.compareTo((K) p.key) < 0) ? p.getLeft() : p.getRight();
            if(l instanceof LeafNode) return (V) ((LeafNode)l).getValue(key);
            p = (InternalNode) l;
        }
    }

    // /** PRECONDITION: k CANNOT BE NULL **/
    // public final V get(final K key) {
    //     if (key == null) throw new NullPointerException();
    //     Node l = root.getLeft();
    //     while (l.getLeft() != null) {
    //         l = (l.key == null || key.compareTo(l.key) < 0) ? l.getLeft() : l.getRight();
    //     }
    //     return (l.key != null && key.compareTo(l.key) == 0) ? l.value : null;
    // }

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
            n = p.getLeft();
            while(n instanceof InternalNode) {
                p = (InternalNode) n;
                n = (p.key == null || key.compareTo((K) p.key) < 0) ? p.getLeft() : p.getRight();
            }
            l = (LeafNode) n;
            pinfo = p.info;                             // read pinfo once instead of every iteration
            if (l != p.getLeft() && l != p.getRight()) continue;  // then confirm the child link to l is valid
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
        //int counter = 0;
        while (true) {
            //counter++;
            //if(counter > 1000) System.out.println("remove loop: " + counter);

            /** SEARCH **/
            gp = null;
            gpinfo = null;
            p = root;
            pinfo = p.info;
            n = p.getLeft();
            while(n instanceof InternalNode) {
                gp = p;
                p = (InternalNode) n;
                n = (p.key == null || key.compareTo((K) p.key) < 0) ? p.getLeft() : p.getRight();
            }
            l = (LeafNode) n;

            if (gp != null) {
                gpinfo = gp.info;                               // - read gpinfo once instead of every iteration
                if (p != gp.getLeft() && p != gp.getRight()) continue;    //   then confirm the child link to p is valid
                pinfo = p.info;                                 //   (just as if we'd read gp's info field before the reference to p)
                if (l != p.getLeft() && l != p.getRight()) continue;      // - do the same for pinfo and l

            }
            /** END SEARCH **/
            
            V ret = (V) l.getValue(key);
            if (ret == null) {
                return null;
            }
            if (!(gpinfo == null || gpinfo.getClass() == Clean.class)) {
               //if(counter > 1000) System.out.println("remove help 1");
                help(gpinfo);
            } else if (!(pinfo == null || pinfo.getClass() == Clean.class)) {
                help(pinfo);
            } else {
                // try to DFlag grandparent
                //if(counter > 1000) System.out.println("try dflag");
                final DInfo newGPInfo = new DInfo(l, p, gp, pinfo, key);

                if (infoUpdater.compareAndSet(gp, gpinfo, newGPInfo)) {
                    if (helpDelete(newGPInfo)) {
                        return ret;
                    }
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
        if(info.p.getLeft() == info.l) {
            info.p.compareAndSetLeft(info.l, info.newInternal);
        } else {
            info.p.compareAndSetRight(info.l, info.newInternal);
        }
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
        else if(info.getClass() == Flag.class)  helpFlagged(((Flag)info).dinfo);
    }

    private void helpMarked(final DInfo info){
        if(info.l.getSize() > 1 || info.l == ((InternalNode) root.getLeft()).getLeft()) {
            Node newLeft, newRight;
            if(info.p.getLeft() == info.l) {
                newLeft = info.l.remove(info.keyToDelete);
                newRight = info.p.getRight();
            } else {
                newLeft = info.p.getLeft();
                newRight = info.l.remove(info.keyToDelete);
            }
            InternalNode newNode = new InternalNode(info.p.key, newLeft, newRight);
            if(info.gp.getLeft() == info.p)
                info.gp.compareAndSetLeft(info.p, newNode);
            else
                info.gp.compareAndSetRight(info.p, newNode);
            infoUpdater.compareAndSet(info.gp, info, new Clean());
        } else {
            final boolean result;
            final Node other = (info.p.getRight() == info.l) ? info.p.getLeft() : info.p.getRight();

            if(other instanceof LeafNode) { // leaf node
              helpFlagged(info, other);
            } else {
              final InternalNode otherIn = (InternalNode) other;
              Info otherinfo;
              //int counter = 0;
              while(true) {
                //counter++;
                //if(counter > 1000) System.out.println("help marked loop: " + counter);
                otherinfo = otherIn.info;
                if(otherinfo == null || otherinfo.getClass() == Clean.class) {
                  Flag flag = new Flag(info);
                  if(infoUpdater.compareAndSet(otherIn, otherinfo, flag)) {
                    helpFlagged(info, otherIn);
                    return;
                  }              
                } else if(otherinfo.getClass() == Flag.class && ((Flag) otherinfo).dinfo == info) {
                  helpFlagged(info, otherIn);
                  return;
                } else
                  help(otherinfo);
              }
            }            
        }
    }

    private void helpFlagged(final DInfo info) {
      final Node other = (info.p.getRight() == info.l) ? info.p.getLeft() : info.p.getRight();
      helpFlagged(info, other);
    }

    private void helpFlagged(final DInfo info, final Node other) {
        Node newOther = other.copy();
        if(info.gp.getLeft() == info.p) {
            info.gp.compareAndSetLeft(info.p, newOther);
        } else {
            info.gp.compareAndSetRight(info.p, newOther);
        }
        infoUpdater.compareAndSet(info.gp, info, new Clean());
    }

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
            private final int INIT_SIZE = 128;
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
    private final void scanHelper(final Node node, final long ts, final K a, final K b, final boolean leftOpen, final boolean rightOpen, RangeScanResultHolder.Stack ret) {
        if (node == null) return;
        if (node instanceof LeafNode) {    // node is a leaf
            ((LeafNode)node).gatherKeys(a, b, leftOpen, rightOpen, ret);
        }
        else {
            InternalNode n = (InternalNode) node;
            if(!leftOpen && !rightOpen) {
                scanHelper(n.getLeft(ts), ts, a, b, false, false, ret);
                scanHelper(n.getRight(ts), ts, a, b, false, false, ret);             
            }
            else if (n.key != null && a.compareTo((K) n.key) >= 0)           // node's key is below the lower limit of [a,b]
                scanHelper(n.getRight(ts), ts, a, b, leftOpen, rightOpen, ret);  // traverse its right subtree
            else if (n.key == null || b.compareTo((K) n.key) < 0)       // node's key is above the upper limit of [a,b]
                scanHelper(n.getLeft(ts), ts, a, b, leftOpen, rightOpen, ret);   // traverse its left subtree
            else {
                // node is in [a,b] - traverse both of its subtrees
                scanHelper(n.getLeft(ts), ts, a, b, leftOpen, false, ret);
                scanHelper(n.getRight(ts), ts, a, b, false, rightOpen, ret);
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
        epoch.announce();
        long ts = Camera.takeSnapshot();
        //System.out.println(ts);
        // Get and initialize rangeScanResultHolder before the start of the tree traversal
        RangeScanResultHolder rangeScanResultHolder = rangeScanResult.get();
        rangeScanResultHolder.rsResult.clear();

        // Start the tree traversal
        scanHelper(root, ts, a, b, true, true, rangeScanResultHolder.rsResult);
        epoch.unannounce();
        // Get stack and its number of elements
        Object[] stackArray = rangeScanResultHolder.rsResult.getStackArray();
        int stackSize = rangeScanResultHolder.rsResult.getEffectiveSize();

        // Make a copy of the stack and return it
        Object[] returnArray = new Object[stackSize];
        for (int i = 0; i < stackSize; i++)
            returnArray[i] = stackArray[i];
        return returnArray;
    }

    // public final long rangeSumHelper(Node node, long ts, final K a, final K b) {
    //     if (node.getLeft(ts) == null) {    // node is a leaf
    //         return node.sumValues(a, b);
    //     }
    //     else {
    //         if (node.key != null && a.compareTo(node.key) >= 0)           // node's key is below the lower limit of [a,b]
    //             return rangeSumHelper(node.getRight(ts), ts, a, b);
    //         else if (node.key == null || b.compareTo(node.key) < 0)       // node's key is above the upper limit of [a,b]
    //             return rangeSumHelper(node.getLeft(ts), ts, a, b);   // traverse its left subtree
    //         else {
    //             // node is in [a,b] - traverse both of its subtrees
    //             return rangeSumHelper(node.getRight(ts), ts, a, b) + rangeSumHelper(node.getLeft(ts), ts, a, b);
    //         }
    //     }
    // }

    // sum all values of keys between a and b
    public final long rangeSum(final K a, final K b) {
        return 0;
    }

    /**
     *
     * DEBUG CODE (FOR TESTBED)
     *
     */

    // private int sumDepths(Node node, int depth) {
    //     if (node == null) return 0;
    //     if (node.getLeft() == null && node.key != null) return depth;
    //     return sumDepths(node.getLeft(), depth+1) + sumDepths(node.getRight(), depth+1);
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
        return getKeysum(((InternalNode)node).getLeft()) + getKeysum(((InternalNode)node).getRight());
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
        return sequentialSize(n.getLeft()) + sequentialSize(n.getRight());
    }
}