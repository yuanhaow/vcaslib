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

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import org.deuce.transform.Exclude;

/**
 *
 * @author trev
 */
@Exclude
public class KSTNode<K> {

    public static final boolean DEBUG = true;
    public final ArrayList<K> keys;
    public final int keyCount;
    public final long weight;
    public ArrayList<KSTNode<K>> children;
    public String address;

    public KSTNode(final K key,
                   final long weight,
                   final String address,
                   final KSTNode<K>... children) {
        this.keyCount = 0;
        this.keys = new ArrayList<K>();
        keys.add(key);
        this.weight = weight;
        this.address = address;
        this.children = new ArrayList<KSTNode<K>>(children.length);
        this.children.addAll(Arrays.asList(children));
    }

    public KSTNode(final ArrayList<K> keys,
                   final int keyCount,
                   final long weight,
                   final String address,
                   final KSTNode<K>... children) {
        this.keyCount = keyCount;
        this.keys = (ArrayList<K>) keys.clone();
        this.weight = weight;
        this.address = address;
        this.children = new ArrayList<KSTNode<K>>(children.length);
        this.children.addAll(Arrays.asList(children));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof KSTNode)) return false;
        KSTNode<Integer> node = (KSTNode<Integer>) o;
        if (node.keyCount != keyCount) return false;
        if ((node.keys == null) != (keys == null)) return false;
        if ((keys != null) && (!keys.equals(node.keys))) return false;
        if (node.weight != weight) return false;
        if ((node.address == null) != (address == null)) return false;
        if ((address != null) && (!address.equals(node.address))) return false;
        for (int i=0;i<children.size();i++) {
            if ((node.children.get(i) == null) != (children.get(i) == null)) {
                return false;
            }
            if ((node.children.get(i) != null)
                    && (!children.get(i).equals(node.children.get(i)))) {
                return false;
            }
        }
        return true;
    }

    public static KSTNode<Integer> readKST(final String str) {
        char c = str.charAt(0);
        if (c == '-') {
            return null;
        } else {
            assert(c == '(');

            //System.out.println("readings fields...");
            ArrayList<String> fields = getFields(str, "(", ",)");
            //System.out.println("done readings fields...");
            //for (int i=0;i<fields.size();i++) {
            //    System.out.println("fields.get(" + i + ") = " + fields.get(i));
            //}
            Integer key = (fields.get(0).equals("-") ? null
                           : Integer.parseInt(fields.get(0)));

            String weightString = fields.get(1);
            int weight = Integer.parseInt(weightString);
            if (DEBUG && Math.random() < 0.01) System.out.println("read node with key " + key + " and weight " + weight);

            ArrayList<KSTNode<Integer>> children = new ArrayList<KSTNode<Integer>>();
            for (int i=2;i<fields.size();i++) {
                children.add(readKST(fields.get(i)));
            }
            KSTNode<Integer>[] childrenArray = (KSTNode<Integer>[])
                    Array.newInstance(KSTNode.class, children.size());
            for (int i=0;i<children.size();i++) {
                childrenArray[i] = children.get(i);
            }
            return new KSTNode<Integer>(key, weight, null, childrenArray);
        }
    }

    // assumes hierarchical structure using parentheses (key,weight[,[child],...])
    private static ArrayList<String> getFields(final String str,
                                               final String openFields,
                                               final String closeFields) {
        ArrayList<String> result = new ArrayList<String>();
        int open = 0;       // number of open parentheses (depth)
        int last = 0;       // start of last field
        int ix = 0;

        for (; ix < str.length(); ix++) {
            char c = str.charAt(ix);
            if (c == '(') {
                open++;
            } else if (c == ')') {
                open--;
            }

            if (open == 1 && openFields.indexOf(c) != -1) {
                last = ix + 1;
            } else if (open == 1 && closeFields.indexOf(c) != -1) {
                if (last < ix) result.add(str.substring(last, ix));
                last = ix + 1;
            }
            if (open == 0) {
                break;
            }
        }
        if (last < ix) result.add(str.substring(last, ix));

        return result;
    }

    private static String getField(final String str, final int index) {
        int ix = index;
        while (str.charAt(ix) != ',' && str.charAt(ix) != ')') {
            ix++;
        }
        return str.substring(index, ix);
    }

    public void printToBufferedStream(final BufferedWriter bw)
            throws IOException {
        bw.write("(");
        bw.write(keys == null ? "-" : keys.toString());
        bw.write(",");
        bw.write(String.valueOf(weight));
        for (int i = 0; i < children.size(); i++) {
            bw.write(",");
            if (children.get(i) == null) {
                bw.write("-");
            } else {
                children.get(i).printToBufferedStream(bw);
            }
        }
        bw.write(")");
    }
}
