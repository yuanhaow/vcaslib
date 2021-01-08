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
public class Random {

    private int seed;

    public Random(int seed) {
        this.seed = seed;
    }

    /** returns pseudorandom x satisfying 0 <= x < n. **/
    public int nextNatural(int n) {
        seed ^= seed << 6;
        seed ^= seed >>> 21;
        seed ^= seed << 7;
        return (seed % n < 0 ? -(seed % n) : seed % n);
    }

    /** returns pseudorandom x satisfying 0 <= x < MAX_INT. **/
    public int nextNatural() {
        seed ^= seed << 6;
        seed ^= seed >>> 21;
        seed ^= seed << 7;
        return (seed < 0 ? -seed : seed);
    }

    /** returns pseudorandom x satisfying MIN_INT <= x <= MAX_INT. **/
    public int nextInt() {
        seed ^= seed << 6;
        seed ^= seed >>> 21;
        seed ^= seed << 7;
        return seed;
    }
}
