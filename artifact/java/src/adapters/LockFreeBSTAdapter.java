
package adapters;

import algorithms.efrbbst.LockFreeBSTMap;
import main.support.SetInterface;
import main.support.KSTNode;
import main.support.OperationListener;
import main.support.Random;

public class LockFreeBSTAdapter<K extends Comparable<? super K>> extends AbstractAdapter<K> implements SetInterface<K> {
    LockFreeBSTMap<K,K> tree = new LockFreeBSTMap<K,K>();

    public boolean contains(K key) {
        return tree.containsKey(key);
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
		return null;
    }

    @Override
    public boolean remove(K key, Random rng, final int[] metrics) {
        return tree.remove(key) != null;
    }

    public boolean remove(K key, Random rng) {
        return remove(key, rng, null);
    }

    // @Override
    // public Object rangeQuery(K lo, K hi, int rangeSize, Random rng) {
    //     return tree.rangeScan(lo, hi);
    // }

    public void addListener(OperationListener l) {}

    public int size() {
        return sequentialSize();
    }

    public KSTNode<K> getRoot() {
        return null;
    }

    public long getSumOfKeys() {
        return tree.getSumOfKeys();
    }

    public boolean supportsKeysum() {
        return true;
    }

    public long getKeysum() {
        return tree.getSumOfKeys();
    }

    public int getSumOfDepths() {
        return tree.getSumOfDepths();
    }

    public int sequentialSize() {
        return tree.size();
    }

}
