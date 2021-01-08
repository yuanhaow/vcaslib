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

package main.support;

import org.deuce.transform.Exclude;

@Exclude
public interface SetInterface<K> {
    public boolean contains(final K key);
    public boolean add(final K key, final Random rng);//, final int[] metrics);
    //public K get(final K key);
    public boolean remove(final K key, final Random rng);//, final int[] metrics);
    public void addListener(final OperationListener l);
    public int size();
    public KSTNode<K> getRoot();
    public int getSumOfDepths();
    public int sequentialSize();
    public double getRebalanceProbability();
}
