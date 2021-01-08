
package adapters;

import algorithms.lfca.LockFreeImmTreapCATreeMapSTDR;

import main.support.SetInterface;
import main.support.KSTNode;
import main.support.OperationListener;
import main.support.Random;

public class LFCAAdapter<K extends Comparable<? super K>> extends AbstractAdapter<K> implements SetInterface<K> {
    LockFreeImmTreapCATreeMapSTDR<K,K> tree = new LockFreeImmTreapCATreeMapSTDR<K,K>();

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

    @Override
    public Object rangeQuery(K lo, K hi, int rangeSize, Random rng) {
        return tree.subSet(lo, hi);
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
        return tree.getSumOfKeys();
    }

    public boolean supportsKeysum() {
        return true;
    }

    public long getKeysum() {
        return tree.getSumOfKeys();
    }

    public int getSumOfDepths() {
        return 0;
    }

    public int sequentialSize() {
        return tree.size();
    }

}
