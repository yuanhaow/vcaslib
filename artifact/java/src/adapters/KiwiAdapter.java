
package adapters;

import algorithms.kiwi.KiWiMap;
import main.support.SetInterface;
import main.support.KSTNode;
import main.support.OperationListener;
import main.support.Random;

public class KiwiAdapter<K extends Comparable<? super K>> extends AbstractAdapter<K> implements SetInterface<K> {
    KiWiMap tree = new KiWiMap();

    private final ThreadLocal<Integer[]> scanResult = new ThreadLocal<Integer[]>() {
        @Override
        protected Integer[] initialValue() {
            return new Integer[1000];
        }
    };

    public boolean contains(K key) {
        return tree.containsKey(key);
    }

    @Override
    public boolean add(K key, Random rng, final int[] metrics) {
        return tree.putIfAbsent((Integer) key, (Integer) key) == null;
//        return tree.put(key, key) == null;
    }

    public boolean add(K key, Random rng) {
        return add(key, rng, null);
    }

    public K get(K key) {
        return (K) tree.get((Integer) key);
    // return null;
    }

    @Override
    public boolean remove(K key, Random rng, final int[] metrics) {
        return tree.remove((Integer) key) != null; // Kiwi's remove() always returns null
    }

    public boolean remove(K key, Random rng) {
        return remove(key, rng, null);
    }

    @Override
    public Object rangeQuery(K lo, K hi, int rangeSize, Random rng) {
        if( (Integer) hi - (Integer) lo + 64 > scanResult.get().length) {
            scanResult.set(new Integer[(Integer) hi - (Integer) lo + 64]);
        }
        Integer[] resultArray = scanResult.get();
        int size = tree.getRange(resultArray, (Integer) lo, (Integer) hi);
        Object[] returnArray = new Object[size];
        for(int i = 0; i < size; i++) returnArray[i] = resultArray[i];
        return returnArray;
    }

    @Override
    public long rangeSum(K lo, K hi, int rangeSize, Random rng) {
        return 0;
    }

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
        return false;
    }

    public long getKeysum() {
        int size = 2000000;
        //System.out.println("SIZE: " + size);
        Integer[] result = new Integer[size];
        size = tree.getRange(result, 0, 2000000);
        //for(int i = 0; i < size; i++)
        //    System.out.print(result[i].intValue() + ", ");
        //System.out.println();
        long sum = 0;
        for(int i = 0; i < size; i++)
            sum += result[i].intValue();
        //System.out.println(sum);
        return sum;
    }

    public int getSumOfDepths() {
        return 0;
    }

    public int sequentialSize() {
        Integer[] result = new Integer[20000000];
        return tree.getRange(result, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

}
