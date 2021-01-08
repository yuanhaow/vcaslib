
package adapters;

import algorithms.snaptree.SnapTreeMap;

import main.support.SetInterface;
import main.support.KSTNode;
import main.support.OperationListener;
import main.support.Random;

import java.util.NavigableSet;
import java.util.Set;
// import se.uu.collection.Stack;
import java.util.concurrent.ConcurrentNavigableMap;

public class SnapTreeAdapter<K extends Comparable<? super K>> extends AbstractAdapter<K> implements SetInterface<K> {
    SnapTreeMap<K, K> tree = new SnapTreeMap<K, K>();

    public boolean contains(K key) {
        return tree.get(key) != null;
    }

    @Override
    public boolean add(K key, Random rng, final int[] metrics) {
        return tree.putIfAbsent(key, key) == null;
//        return tree.put(key, key) == null;
    }

    public boolean add(K key, Random rng) {
        return add(key, rng, null);
    }

    public K get(K key) {
        //return tree.get(key);
        return tree.get(key);
    }

    @Override
    public boolean remove(K key, Random rng, final int[] metrics) {
        return tree.remove(key) != null;
    }

    public boolean remove(K key, Random rng) {
        return remove(key, rng, null);
    }

    private final ThreadLocal<Integer[]> scanResult = new ThreadLocal<Integer[]>() {
        @Override
        protected Integer[] initialValue() {
            return new Integer[1000];
        }
    };
    
    public Object[] subSet(K lo, K hi) {
        //lock.readLock();
        SnapTreeMap<K,K> clone = tree.clone();
        //  lock.readUnlock();
        ConcurrentNavigableMap<K, K> rangeMap = clone.subMap(lo, true, hi, true);
        NavigableSet<K> keySet = rangeMap.keySet();

        if( (Integer) hi - (Integer) lo + 64 > scanResult.get().length) {
            scanResult.set(new Integer[(Integer) hi - (Integer) lo + 64]);
        }
        Object[] resultArray = scanResult.get();

        int idx = 0;
        for(K key : keySet){
            resultArray[idx] = key;
            idx++;
            // returnStack.push(key);
            //System.out.println(key);
        }
        Object[] returnArray = new Object[idx];
        for(int i = 0; i < idx; i++){
            returnArray[i] = resultArray[i];
        }
        return returnArray;
    }

    @Override
    public Object rangeQuery(K lo, K hi, int rangeSize, Random rng) {
        return subSet(lo, hi);
    }

    // @Override
    // public long rangeSum(K lo, K hi, int rangeSize, Random rng) {
    //     return tree.rangeSum(lo, hi);
    // }

    public void addListener(OperationListener l) {}

    public int size() {
        return sequentialSize();
    }

    public KSTNode<K> getRoot() {
        return null;
    }

    public long getSumOfKeys() {
        return getKeysum();
    }

    public boolean supportsKeysum() {
        return true;
    }

    public long getKeysum() {
        SnapTreeMap<K,K> clone = tree.clone();
        //  lock.readUnlock();
        ConcurrentNavigableMap<K, K> rangeMap = clone.subMap((K) (Integer) 0, true, (K) (Integer) (Integer.MAX_VALUE-10), true);
        NavigableSet<K> keySet = rangeMap.keySet();

        long sum = 0;
        for(K key : keySet){
            sum += (int) (Integer) key;
            // returnStack.push(key);
            //System.out.println(key);
        }
        return sum;
    }

    public int getSumOfDepths() {
        return 0;
    }

    public int sequentialSize() {
        return tree.size();
    }

}
