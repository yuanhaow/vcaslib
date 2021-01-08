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

package main;

import org.deuce.transform.Exclude;

/**
 *
 * @author trev
 */
@Exclude
public class Globals {
    public static final int DEFAULT_SEED = (int) System.currentTimeMillis(); //581968107;//11720571;
    public static final int DEFAULT_KEYRANGE = 1000000;

    public static final int DEFAULT_CHAIN_SIZE = 10000;
    public static final int DEFAULT_RQ_SIZE = 1000;

    public static final int QUERY_TYPE_RQ = 0;
    public static final int QUERY_TYPE_FINDIF = 1;
    public static final int QUERY_TYPE_SUCC = 2;
    public static final int QUERY_TYPE_MULTISEARCH = 3;
    public static final int QUERY_TYPE_MULTISEARCH_NONATOMIC = 4;

    public static final int GENERATOR_TYPE_DEFAULT = 0;
    public static final int GENERATOR_TYPE_CHAINS = 1;
    public static final int GENERATOR_TYPE_SEQUENTIAL = 2;
//    public static final int GENERATOR_TYPE_BIASED = 2;
}
