/**
 * Java test harness for throughput experiments on concurrent data structures.
 * Copyright (C) 2012 Trevor Brown
 * Copyright (C) 2019 Elias Papavasileiou
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

import adapters.*;
import main.support.*;

import java.io.*;
import java.lang.management.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public class Tests {

	/*static class MyInsertThread extends Thread {
		private LockFreePBSTAdapter<Integer> tree;

		public MyInsertThread(LockFreePBSTAdapter<Integer> tree) {
			this.tree = tree;
		}

		public void run() {
			tree.add(10, null);
		}
	}

	static class MyDeleteThread extends Thread {
		private LockFreePBSTAdapter<Integer> tree;

		public MyDeleteThread(LockFreePBSTAdapter<Integer> tree) {
			this.tree = tree;
		}

		public void run() {
			for (int i=0; i<3; i++) {
				tree.remove(10, null);
			}
		}
	}*/

    static void InsertDeleteOneKey(AbstractAdapter<Integer> tree) {
        assert tree.add(10, null);
        assert !tree.add(10, null);
        assert tree.remove(10, null);
        assert !tree.remove(10, null);
        System.out.println(new Object(){}.getClass().getEnclosingMethod().getName() + ": OK");
    }

    static void InsertDeleteTwoKeys(AbstractAdapter<Integer> tree) {
        assert tree.add(10, null);
        assert tree.add(15, null);
        assert !tree.add(10, null);
        assert !tree.add(15, null);
        assert tree.remove(10, null);
        assert !tree.remove(10, null);
        assert tree.remove(15, null);
        assert !tree.remove(15, null);
        System.out.println(new Object(){}.getClass().getEnclosingMethod().getName() + ": OK");
    }

    static void InsertDeleteQuery(AbstractAdapter<Integer> tree) {
        try {
            tree.rangeQuery(1, 100, 0, null);
        } catch(UnsupportedOperationException e) {
            System.out.println("range query not supported");
            return;
        }
        int keysInTree = 0;
        int key1 = 11, key2 = 20, key3 = 30;
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        assert !tree.remove(key1, null);
        assert tree.add(key1, null);
        keysInTree++;
        assert tree.add(key3, null);
        keysInTree++;
        assert tree.add(key2, null);
        keysInTree++;
        // System.out.println("RQResult length: " + ((Object[]) tree.rangeQuery(1, 100, 0, null)).length);
        // System.out.println("keysInTree: " + keysInTree);
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        //System.out.println("RQResult length: " + ((Object[]) tree.rangeQuery(1, 100, 0, null)).length);
        //assert tree.remove(key1, null);
        //assert !tree.contains(key1);
        assert tree.remove(key1, null);
        keysInTree--;
        assert !tree.contains(key1);
        // Object[] keys = (Object[]) tree.rangeQuery(1, 100, 0, null);
        // System.out.println("RQResult length: " + keys.length);
        // System.out.println("keysInTree: " + keysInTree);
        // for(int i = 0; i < keys.length; i++)
        //     System.out.print((Integer) keys[i] + ", ");
        // System.out.println();
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        assert !tree.contains(key1);
        assert tree.contains(key2);
        assert tree.contains(key3);
        assert !tree.remove(key1, null);
        assert tree.contains(key2);
        assert tree.contains(key3);
        assert ((Object[]) tree.rangeQuery(key2, key3-1, 0, null)).length == keysInTree-1;
        assert ((Object[]) tree.rangeQuery(key2, key3, 0, null)).length == keysInTree;
        assert ((Object[]) tree.rangeQuery(key2, key3+1, 0, null)).length == keysInTree;
        assert tree.remove(key2, null);
        keysInTree--;
        assert tree.remove(key3, null);
        keysInTree--;
        System.out.println(new Object(){}.getClass().getEnclosingMethod().getName() + ": OK");
    }

    static void InsertDeleteQuery2(AbstractAdapter<Integer> tree) {
        try {
            tree.rangeQuery(1, 100, 0, null);
        } catch(UnsupportedOperationException e) {
            System.out.println("range query not supported");
            return;
        }
        int keysInTree = 0;
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        assert !tree.contains(10);
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        assert tree.add(10, null);
        keysInTree++;
        assert tree.add(15, null);
        keysInTree++;
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        assert tree.remove(15, null);
        keysInTree--;
        assert !tree.remove(15, null);
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        assert !tree.contains(15);
		assert ((Object[]) tree.rangeQuery(1, 1000000, 0, null)).length == keysInTree;
        assert tree.add(5, null);
        keysInTree++;
        assert tree.remove(5, null);
        keysInTree--;
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
		assert tree.add(20, null);
        keysInTree++;
		assert tree.add(15, null);
        keysInTree++;
		assert tree.add(25, null);
        keysInTree++;
		assert !tree.remove(5, null);
		assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
		assert tree.remove(25, null);
        keysInTree--;
		assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        assert ((Object[]) tree.rangeQuery(1, 10, 0, null)).length == keysInTree-2;
        assert ((Object[]) tree.rangeQuery(1, 14, 0, null)).length == keysInTree-2;
        assert ((Object[]) tree.rangeQuery(1, 15, 0, null)).length == keysInTree-1;
        assert ((Object[]) tree.rangeQuery(1, 16, 0, null)).length == keysInTree-1;
        assert ((Object[]) tree.rangeQuery(9, 16, 0, null)).length == keysInTree-1;
        assert ((Object[]) tree.rangeQuery(10, 16, 0, null)).length == keysInTree-1;
        assert ((Object[]) tree.rangeQuery(11, 16, 0, null)).length == keysInTree-2;
        System.out.println(new Object(){}.getClass().getEnclosingMethod().getName() + ": OK");
    }

    static void InsertDeleteOneKeyKiwi(AbstractAdapter<Integer> tree) {
        tree.add(10, null);
        assert tree.contains(10);
        tree.add(10, null);
        assert tree.contains(10);
        assert !tree.contains(11);
        tree.remove(10, null);
        assert !tree.contains(10);
        tree.remove(10, null);
        assert !tree.contains(10);
        System.out.println(new Object(){}.getClass().getEnclosingMethod().getName() + ": OK");
    }

    static void InsertDeleteTwoKeysKiwi(AbstractAdapter<Integer> tree) {
        tree.add(10, null);
        assert tree.contains(10);
        tree.add(15, null);
        assert tree.contains(15);
        tree.add(10, null);
        tree.add(15, null);
        tree.remove(10, null);
        assert !tree.contains(10);
        assert tree.contains(15);
        tree.remove(10, null);
        tree.remove(15, null);
        assert !tree.contains(15);
        tree.remove(15, null);
        System.out.println(new Object(){}.getClass().getEnclosingMethod().getName() + ": OK");
    }

    static void InsertDeleteQueryKiwi(AbstractAdapter<Integer> tree) {
        try {
            tree.rangeQuery(1, 100, 0, null);
        } catch(UnsupportedOperationException e) {
            System.out.println("range query not supported");
            return;
        }
        int keysInTree = 0;
        int key1 = 11, key2 = 20, key3 = 30;
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        tree.remove(key1, null);
        assert !tree.contains(key1);
        tree.add(key1, null);
        assert tree.contains(key1);
        keysInTree++;
        tree.add(key3, null);
        assert tree.contains(key3);
        keysInTree++;
        tree.add(key2, null);
        assert tree.contains(key2);
        keysInTree++;
        // System.out.println("RQResult length: " + ((Object[]) tree.rangeQuery(1, 100, 0, null)).length);
        // System.out.println("keysInTree: " + keysInTree);
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        //System.out.println("RQResult length: " + ((Object[]) tree.rangeQuery(1, 100, 0, null)).length);
        //assert tree.remove(key1, null);
        //assert !tree.contains(key1);
        tree.remove(key1, null);
        keysInTree--;
        assert !tree.contains(key1);
        //System.out.println("RQResult length: " + ((Object[]) tree.rangeQuery(1, 100, 0, null)).length);
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        assert !tree.contains(key1);
        assert tree.contains(key2);
        assert tree.contains(key3);
        tree.remove(key1, null);
        assert tree.contains(key2);
        assert tree.contains(key3);
        assert ((Object[]) tree.rangeQuery(key2, key3-1, 0, null)).length == keysInTree-1;
        assert ((Object[]) tree.rangeQuery(key2, key3, 0, null)).length == keysInTree;
        assert ((Object[]) tree.rangeQuery(key2, key3+1, 0, null)).length == keysInTree;
        tree.remove(key2, null);
        assert !tree.contains(key2);
        keysInTree--;
        tree.remove(key3, null);
        assert !tree.contains(key3);
        keysInTree--;
        System.out.println(new Object(){}.getClass().getEnclosingMethod().getName() + ": OK");
    }

    static void InsertDeleteQuery2Kiwi(AbstractAdapter<Integer> tree) {
        try {
            tree.rangeQuery(1, 100, 0, null);
        } catch(UnsupportedOperationException e) {
            System.out.println("range query not supported");
            return;
        }
        int keysInTree = 0;
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        assert !tree.contains(10);
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        tree.add(10, null);
        keysInTree++;
        tree.add(15, null);
        keysInTree++;
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        tree.remove(15, null);
        keysInTree--;
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        assert !tree.contains(15);
        assert ((Object[]) tree.rangeQuery(1, 1000000, 0, null)).length == keysInTree;
        tree.add(5, null);
        keysInTree++;
        tree.remove(5, null);
        keysInTree--;
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        tree.add(20, null);
        keysInTree++;
        tree.add(15, null);
        keysInTree++;
        tree.add(25, null);
        keysInTree++;
        tree.remove(5, null);
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        tree.remove(25, null);
        keysInTree--;
        assert ((Object[]) tree.rangeQuery(1, 100, 0, null)).length == keysInTree;
        assert ((Object[]) tree.rangeQuery(1, 10, 0, null)).length == keysInTree-2;
        assert ((Object[]) tree.rangeQuery(1, 14, 0, null)).length == keysInTree-2;
        assert ((Object[]) tree.rangeQuery(1, 15, 0, null)).length == keysInTree-1;
        assert ((Object[]) tree.rangeQuery(1, 16, 0, null)).length == keysInTree-1;
        assert ((Object[]) tree.rangeQuery(9, 16, 0, null)).length == keysInTree-1;
        assert ((Object[]) tree.rangeQuery(10, 16, 0, null)).length == keysInTree-1;
        assert ((Object[]) tree.rangeQuery(11, 16, 0, null)).length == keysInTree-2;
        System.out.println(new Object(){}.getClass().getEnclosingMethod().getName() + ": OK");
    }

    static void getSuccessorsTest(AbstractAdapter<Integer> tree) {
        Element<Integer,Integer>[] output = tree.successors(3, 20);
        assert output.length == 0;

        tree.add(2, null);
        output = tree.successors(3, 20);
        assert output.length == 0;

        tree.add(30, null);
        output = tree.successors(3, 20);
        assert output.length == 1;

        tree.add(3, null);
        output = tree.successors(3, 20);
        assert output.length == 2;
        output = tree.successors(3, 1);
        assert output.length == 1;

        tree.add(15, null);
        tree.add(5, null);
        tree.add(10, null);
        output = tree.successors(3, 20);
        assert output.length == 5;
        assert output[0].key.equals(3);
        assert output[1].key.equals(5);
        assert output[2].key.equals(10);
        assert output[3].key.equals(15);
        assert output[4].key.equals(30);
        
        tree.remove(2, null);
        tree.remove(30, null);
        tree.remove(3, null);
        tree.remove(15, null);
        tree.remove(5, null);
        tree.remove(10, null);
        System.out.println(new Object(){}.getClass().getEnclosingMethod().getName() + ": OK");
    }

    static void multiSearchTest(AbstractAdapter<Integer> tree) {
        Integer[] keys = {5, 10, 15};
        Object[] output = tree.multiSearch(keys);
        assert output.length == 3;
        // System.out.println(output[0] + ", " + output[1] + ", " + output[2]);
        assert output[0] == null;
        assert output[1] == null;
        assert output[2] == null;

        tree.add(7, null);
        output = tree.multiSearch(keys);
        assert output.length == 3;
        assert output[0] == null;
        assert output[1] == null;
        assert output[2] == null;

        tree.add(5, null);
        tree.add(15, null);
        output = tree.multiSearch(keys);
        assert output.length == 3;
        assert output[0].equals(5);
        assert output[1] == null;
        assert output[2].equals(15);

        tree.add(10, null);
        tree.add(12, null);
        tree.add(20, null);
        output = tree.multiSearch(keys);
        assert output.length == 3;
        assert output[0].equals(5);
        assert output[1].equals(10);
        assert output[2].equals(15);

        tree.remove(7, null);
        tree.remove(5, null);
        tree.remove(15, null);
        tree.remove(10, null);
        tree.remove(12, null);
        tree.remove(20, null);
        System.out.println(new Object(){}.getClass().getEnclosingMethod().getName() + ": OK");
    }

    static void findIfTest(AbstractAdapter<Integer> tree) {
        Predicate<Element<Integer,Integer>> even = (e) -> { return e.value%2 == 0; };
        assert tree.findIf(3, 20, even) == null;

        tree.add(2, null);
        assert tree.findIf(3, 20, even) == null;
        assert tree.findIf(2, 20, even).key.equals(2);
        assert tree.findIf(2, 2, even).key.equals(2);
        assert tree.findIf(0, 1, even) == null;

        tree.add(30, null);
        tree.add(3, null);
        tree.add(15, null);
        tree.add(5, null);
        tree.add(10, null);

        assert tree.findIf(3, 20, even).key.equals(10);
        assert tree.findIf(11, 30, even).key.equals(30);
        assert tree.findIf(11, 29, even) == null;
        assert tree.findIf(2, 30, even).key.equals(2);
        assert tree.findIf(-100, 2020, even).key.equals(2);
        
        tree.remove(2, null);
        tree.remove(30, null);
        tree.remove(3, null);
        tree.remove(15, null);
        tree.remove(5, null);
        tree.remove(10, null);
        assert tree.findIf(-100, 2020, even) == null;
        System.out.println(new Object(){}.getClass().getEnclosingMethod().getName() + ": OK");
    }

    private static void runTests(AbstractAdapter<Integer> tree) {
        InsertDeleteOneKey(tree);
        InsertDeleteTwoKeys(tree);
        InsertDeleteQuery(tree);
        InsertDeleteQuery2(tree);
    }

    private static void runKiwiTests(AbstractAdapter<Integer> tree) {
        InsertDeleteOneKeyKiwi(tree);
        InsertDeleteTwoKeysKiwi(tree);
        InsertDeleteQueryKiwi(tree);
        InsertDeleteQuery2Kiwi(tree);
    }

    private static void runComplexQueryTests(AbstractAdapter<Integer> tree) {
        getSuccessorsTest(tree);
        multiSearchTest(tree);
        findIfTest(tree);
    }

    public static void runTests() {
        int[] treeParam = {2, 16, 64};
        ThreadID.threadID.set(0);

        // Run tests
        for (TreeFactory<Integer> tree : Factories.factories) {
            if(tree.getName() == "KIWI") {
                System.out.println("[*] Testing " + tree.getName() + " ...");
                AbstractAdapter<Integer> treeAdapter = (AbstractAdapter<Integer>) tree.newTree(null);
                runKiwiTests(treeAdapter);
                System.out.println();
            }
            else if(tree.getName().indexOf("Batch") != -1 || tree.getName().indexOf("BPBST") != -1) {
                for(int i = 0; i < treeParam.length; i++) {
                    System.out.println("[*] Testing " + tree.getName() + " Batch Size " + treeParam[i] + " ...");
                    AbstractAdapter<Integer> treeAdapter = (AbstractAdapter<Integer>) tree.newTree((Object) treeParam[i]);
                    runTests(treeAdapter);   

                    if(tree.getName() == "VcasChromaticBatchBSTGC" || tree.getName() == "ChromaticBatchBST") {
                        //System.out.println("[*] Testing " + tree.getName() + " ...");
                        treeAdapter = (AbstractAdapter<Integer>) tree.newTree((Object) treeParam[i]);
                        runComplexQueryTests(treeAdapter);
                    }
                    System.out.println();
                }
            }
            else {
                System.out.println("[*] Testing " + tree.getName() + " ...");
                AbstractAdapter<Integer> treeAdapter = (AbstractAdapter<Integer>) tree.newTree(null);
                runTests(treeAdapter);
                System.out.println();
            }          
        }        
    }
}
