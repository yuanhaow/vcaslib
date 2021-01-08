
package adapters;

import main.support.SetInterface;
import main.support.KSTNode;
import main.support.OperationListener;
import main.support.Random;
import algorithms.chromatic.LockFreeChromaticMap;

public class LockFreeChromaticAdapter<K extends Comparable<? super K>> extends AbstractAdapter<K> implements SetInterface<K> {
    public LockFreeChromaticMap<K,K> tree;

    public LockFreeChromaticAdapter() {
        tree = new LockFreeChromaticMap();
    }

    public LockFreeChromaticAdapter(final int allowedViolations) {
        tree = new LockFreeChromaticMap(allowedViolations);
    }

    public boolean contains(K key) {
        return tree.containsKey(key);
    }
    
    public boolean add(K key, Random rng) {
        return tree.putIfAbsent(key, key) == null;
        //return tree.put(key, key) == null;
    }

    public K get(K key) {
        return tree.get(key);
    }

    public boolean remove(K key, Random rng) {
        return tree.remove(key) != null;
    }

    public void addListener(OperationListener l) {

    }

    public int size() {
        return sequentialSize();
    }

    public KSTNode<K> getRoot() {
        return null;
    }
    
    public double getAverageDepth() {
        return tree.getSumOfDepths() / (double) tree.getNumberOfNodes();
    }

    public int getSumOfDepths() {
        return tree.getSumOfDepths();
    }

    public int sequentialSize() {
        return tree.size();
    }
    
    public boolean supportsKeysum() {
        return true;
    }
    public long getKeysum() {
        return tree.getKeysum();
    }

    public double getRebalanceProbability() {
        return -1;
    }

    @Override
    public String toString() {
        return tree.toString();
    }
    
    public void disableRotations() {

    }

    public void enableRotations() {

    }

}