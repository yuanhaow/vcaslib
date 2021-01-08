/**
 * Java test harness for throughput experiments on concurrent data structures.
 * Copyright (C) 2012 Trevor Brown
 * Contact (me [at] tbrown [dot] pro) with any questions or comments.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package adapters;

import main.support.Random;
import main.support.Element;
import org.deuce.transform.Exclude;
import java.util.function.Predicate;

@Exclude
public abstract class AbstractAdapter<K> {

    public double getRebalanceProbability() {
        // meaningless for most adapters
        // (but used as a signal that existing violations is okay in BBST series)
        return 1;
    }
    public abstract boolean add(final K key, final Random rng);
    public boolean add(final K key, final Random rng, final int[] metrics) {
        return add(key, rng);
    }
    public abstract boolean remove(final K key, final Random rng);
    public boolean remove(final K key, final Random rng, final int[] metrics) {
        return remove(key, rng);
    }
    public abstract boolean contains(final K key);

    public static final int greaterPow(int x) {
        assert x >= 0;
        int shift = 0;
        while (x > 0) {
            x /= 2;
            shift++;
        }
        return 1<<shift;
    }

    public Object rangeQuery(final K lo, final K hi, final int rangeSize, final Random rng) {
        throw new UnsupportedOperationException("not overloaded");
    }
    public long rangeSum(final K lo, final K hi, final int rangeSize, final Random rng) {
        throw new UnsupportedOperationException("not overloaded");
    }
    public Object partialSnapshot(final int size, final Random rng) {
        throw new UnsupportedOperationException("not overloaded");
    }
    
    public Element<K,K>[] successors(K key, int numSuccessors) {
        throw new UnsupportedOperationException("not overloaded");
    }

    public Object[] multiSearch(K[] keys) {
        throw new UnsupportedOperationException("not overloaded");
    }

    public Element<K,K> findIf(K lo, K hi, Predicate<Element<K,K>> p) {
        throw new UnsupportedOperationException("not overloaded");
    }    

    public boolean supportsKeysum() {
        return false;
    }
    public long getKeysum() {
        return 0;
    }

    public abstract int size();

    public void debugPrint(){ 
        System.out.println("NOTICE: debugPrint() is not implemented for " + getClass().getName());
    }
}
