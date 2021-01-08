/*
 *  Copyright 2018 Kjell Winblad (kjellwinblad@gmail.com, http://winsh.me)
 *
 *  This file is part of JavaRQBench
 *
 *  catrees is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  catrees is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with catrees.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * 
 */
package algorithms.lfca;

import java.io.File;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import algorithms.lfca.ImmutableTreapMap.ImmutableTreapValue;

/**
 * @author kjell
 *
 */
public class ImmutableTreapMap {

	final static int DEGREE = 64;

	public static <K> int compare(K key1, K key2, Comparator<? super K> comparator) {
		if (comparator == null) {
			@SuppressWarnings("unchecked")
			Comparable<? super K> keyComp = (Comparable<? super K>) key1;
			return keyComp.compareTo(key2);
		} else {
			return comparator.compare(key1, key2);
		}
	}

	static public interface ImmutableTreapValue<K, V> {
	}

	static public interface Node<K, V> extends ImmutableTreapValue {
		public Node<K, V> createClone();

		public long getWeight();

		public K minKey();

		public String nodeKeysToString();
	}

	static public class InternalNode<K, V> implements Node<K, V> {
		private K key;
		private Node<K, V> left = null;
		private Node<K, V> right = null;
		private long weight;

		public K getKey() {
			return key;
		}

		public Node<K, V> getLeft() {
			return left;
		}

		public Node<K, V> getRight() {
			return right;
		}

		public long getWeight() {
			return weight;
		}

		public InternalNode(K key, Node<K, V> left, Node<K, V> right) {
			super();
			this.key = key;
			this.left = left;
			this.right = right;
			this.weight = ThreadLocalRandom.current().nextLong(1152921504606846976L - 1) + 1;
		}

		public InternalNode(K key, Node<K, V> left, Node<K, V> right, long weight) {
			super();
			this.key = key;
			this.left = left;
			this.right = right;
			this.weight = weight;
		}

		public Node<K, V> createClone() {
			return new InternalNode<>(key, left, right, weight);
		}

		@Override
		public K minKey() {
			return key;
		}

		@Override
		public String toString() {
			StringBuffer buff = new StringBuffer();
			buff.append("{");
			traverseAllItems(this, (k, v) -> buff.append(k + "=" + v + ","));
			buff.append("}");
			return buff.toString();
		}

		@Override
		public String nodeKeysToString() {
			return key.toString();
		}

	}

	static public class ExternalNode<K, V> implements Node<K, V> {
		private K maxKey = null;
		private Object[] keys;
		private Object[] values;

		@Override
		public String toString() {
			StringBuffer buff = new StringBuffer();
			buff.append("{");
			traverseAllItems(this, (k, v) -> buff.append(k + "=" + v + ","));
			buff.append("}");
			return buff.toString();
		}

		public final boolean firstNodeTraverseRange(K lo, K hi, Consumer<K> consumer, Comparator<? super K> comparator) {
		        if(maxKey() == null){
		        	return false;
		        }
		        // (Investigate if find index is smart)
			int i = 0;
			K currentKey = (K) keys[i];
			while (compare(hi, currentKey, comparator) >= 0) {
				if (compare(lo, currentKey, comparator) <= 0) {
					consumer.accept(currentKey);
				}
				i = i + 1;

				if (i >= size()) {
					return false;
				}

				currentKey = (K) keys[i];
			}
			return true;
		}

		public final boolean notFirstNodeTraverseRange(K lo, K hi, Consumer<K> consumer, Comparator<? super K> comparator) {
		    if (compare(hi, maxKey(), comparator) <= 0) {
				// We only need to look at this node
				int i = 0;
				K currentKey = (K) keys[i];
				while (compare(hi, currentKey, comparator) >= 0) {
					consumer.accept(currentKey);
					//returnStack.push(currentKey);
					i = i + 1;
					
					if(i >= size()){
						break;
					}
					
					currentKey = (K) keys[i];
				}
				return true;
			} else {
				// All keys in this node need to be included
				for (int i = 0; i < size(); i++) {
					//returnStack.push((K) currentNode.getKeys()[i]);
					consumer.accept((K)keys[i]);
				}
				return false;
			}
		}
		
		public Node<K, V> createClone() {
			ExternalNode<K, V> clone = new ExternalNode<>();
			clone.keys = new Object[this.keys.length];
			clone.values = new Object[this.keys.length];
			for (int i = 0; i < keys.length; i++) {
				clone.keys[i] = keys[i];
				clone.values[i] = values[i];
			}
			clone.maxKey = (K) clone.keys[this.keys.length - 1];
			return clone;
		}

		public boolean isFull() {
			return DEGREE == keys.length;
		}

		@SuppressWarnings("unchecked")
		public K minKey() {
			if (keys.length == 0)
				return null;
			return (K) keys[0];
		}

		@SuppressWarnings("unchecked")
		public K maxKey() {
			return maxKey;
		}

		public ExternalNode<K, V> splitLeft() {
			ExternalNode<K, V> newNode = new ExternalNode<K, V>();
			int splitUntilIndex = keys.length / 2;
			newNode.keys = new Object[splitUntilIndex];
			newNode.values = new Object[splitUntilIndex];
			for (int i = 0; i < splitUntilIndex; i++) {
				newNode.keys[i] = keys[i];
				newNode.values[i] = values[i];
			}
			newNode.maxKey = (K) newNode.keys[newNode.keys.length - 1];
			return newNode;
		}

		public ExternalNode<K, V> splitRight() {
			ExternalNode<K, V> newNode = new ExternalNode<K, V>();
			int splitFromIndex = keys.length / 2;
			newNode.keys = new Object[keys.length - splitFromIndex];
			newNode.values = new Object[keys.length - splitFromIndex];
			for (int i = 0; i < (keys.length - splitFromIndex); i++) {
				newNode.keys[i] = keys[splitFromIndex + i];
				newNode.values[i] = values[splitFromIndex + i];
			}
			newNode.maxKey = (K) newNode.keys[newNode.keys.length - 1];
			return newNode;
		}

		@SuppressWarnings("unchecked")
		public ExternalNode<K, V> splitLeftAndInsert(K key, V value, Comparator<? super K> comparator) {
			ExternalNode<K, V> newNode = new ExternalNode<K, V>();
			int splitUntilIndex = keys.length / 2;
			newNode.keys = new Object[splitUntilIndex + 1];
			newNode.values = new Object[splitUntilIndex + 1];
			boolean inserted = false;
			for (int i = 0; i < (splitUntilIndex + 1); i++) {
				if (!inserted && i == splitUntilIndex) {
					newNode.keys[i] = key;
					newNode.values[i] = value;
					break;
				} else if (inserted && i == splitUntilIndex) {
					break;
				} else if (!inserted && compare(key, (K) keys[i], comparator) < 0) {
					newNode.keys[i] = key;
					newNode.values[i] = value;
					inserted = true;
					newNode.keys[i + 1] = keys[i];
					newNode.values[i + 1] = values[i];
				} else if (!inserted) {
					newNode.keys[i] = keys[i];
					newNode.values[i] = values[i];
				} else {
					newNode.keys[i + 1] = keys[i];
					newNode.values[i + 1] = values[i];
				}
			}
			newNode.maxKey = (K) newNode.keys[newNode.keys.length - 1];
			return newNode;
		}

		@SuppressWarnings("unchecked")
		public ExternalNode<K, V> splitRightAndInsert(K key, V value, Comparator<? super K> comparator) {
			ExternalNode<K, V> newNode = new ExternalNode<K, V>();
			int splitFromIndex = keys.length / 2;
			newNode.keys = new Object[keys.length - splitFromIndex + 1];
			newNode.values = new Object[keys.length - splitFromIndex + 1];
			boolean inserted = false;
			for (int i = 0; i < (keys.length - splitFromIndex + 1); i++) {
				if (!inserted && i == (keys.length - splitFromIndex)) {
					newNode.keys[i] = key;
					newNode.values[i] = value;
					break;
				} else if (inserted && i == (keys.length - splitFromIndex)) {
					break;
				} else if (!inserted && compare(key, (K) keys[splitFromIndex + i], comparator) < 0) {
					newNode.keys[i] = key;
					newNode.values[i] = value;
					inserted = true;
					newNode.keys[i + 1] = keys[splitFromIndex + i];
					newNode.values[i + 1] = values[splitFromIndex + i];
				} else if (!inserted) {
					newNode.keys[i] = keys[splitFromIndex + i];
					newNode.values[i] = values[splitFromIndex + i];
				} else {
					newNode.keys[i + 1] = keys[splitFromIndex + i];
					newNode.values[i + 1] = values[splitFromIndex + i];
				}
			}
			newNode.maxKey = (K) newNode.keys[newNode.keys.length - 1];
			return newNode;
		}

		// Based on open JDK 8 code Arrays.binarySearch
		@SuppressWarnings("unchecked")
		public int indexOfKeyInNode(K key, Comparator<? super K> comparator) {
			if (keys.length == 0) {
				return -1;
			}
			int low = 0;
			int high = keys.length - 1;

			while (low <= high) {
				int mid = (low + high) >>> 1;
				Object midVal = keys[mid];
				int cmp = compare(key, (K) midVal, comparator);
				if (cmp > 0)
					low = mid + 1;
				else if (cmp < 0)
					high = mid - 1;
				else
					return mid; // key found
			}
			return -(low + 1); // key not found.
		}

		@SuppressWarnings("unchecked")
		public V get(K key, Comparator<? super K> comparator) {
			int index = indexOfKeyInNode(key, comparator);
			if (index < 0) {
				return null;
			} else {
				return (V) values[index];
			}
		}

		public ExternalNode<K, V> replaceValueAtPos(int pos, V value) {
			ExternalNode<K, V> newNode = new ExternalNode<K, V>();
			newNode.keys = new Object[keys.length];
			newNode.values = new Object[keys.length];
			for (int i = 0; i < keys.length; i++) {
				newNode.keys[i] = keys[i];
				newNode.values[i] = values[i];
			}
			newNode.values[pos] = value;
			newNode.maxKey = (K) newNode.keys[newNode.keys.length - 1];
			return newNode;
		}

		public ExternalNode<K, V> removeItemAtPos(int pos) {
			ExternalNode<K, V> newNode = new ExternalNode<K, V>();
			newNode.keys = new Object[keys.length - 1];
			newNode.values = new Object[keys.length - 1];
			int newNodeI = 0;
			for (int i = 0; i < keys.length; i++) {
				if (i == pos) {
					continue;
				}
				newNode.keys[newNodeI] = keys[i];
				newNode.values[newNodeI] = values[i];
				newNodeI = newNodeI + 1;
			}
			if (pos == (keys.length - 1)) {
				newNode.maxKey = (K) newNode.keys[newNode.keys.length - 1];
			}
			newNode.maxKey = (K) newNode.keys[newNode.keys.length - 1];
			return newNode;
		}

		public ExternalNode<K, V> addAtPos(int insertPos, K key, V val) {
			ExternalNode<K, V> newNode = new ExternalNode<K, V>();
			newNode.keys = new Object[keys.length + 1];
			newNode.values = new Object[keys.length + 1];
			int newNodeI = 0;
			for (int i = 0; i < (keys.length + 1); i++) {
				if (i == insertPos) {
					newNode.keys[newNodeI] = key;
					newNode.values[newNodeI] = val;
					newNodeI = newNodeI + 1;
				}
				if (i != keys.length) {
					newNode.keys[newNodeI] = keys[i];
					newNode.values[newNodeI] = values[i];
				}
				newNodeI = newNodeI + 1;
			}
			if (insertPos == (keys.length)) {
				newNode.maxKey = (K) newNode.keys[newNode.keys.length - 1];
			}
			newNode.maxKey = (K) newNode.keys[newNode.keys.length - 1];
			return newNode;
		}

		public int size() {
			return keys.length;
		}

		public Object[] getKeys() {
			return keys;
		}

		public Object[] getValues() {
			return values;
		}

		@Override
		public long getWeight() {
			return 0;
		}

		@Override
		public String nodeKeysToString() {
			StringBuffer buff = new StringBuffer();
			traverseAllItems(this, (k, v) -> buff.append(k + ","));
			return buff.toString();
		}
	}

	public static Object getPrevValue() {
		return threadLocalBuffers.get().getPrevValue();
	}

	private final static class ThreadLocalBuffers {
		public static int sizeCounter;

		@SuppressWarnings("rawtypes")
		public Stack<Node> getStack() {
			stack.resetStack();
			return stack;
		}

		private Object prevValue = null;
		private final Stack<Node> stack = new Stack<Node>();

		public void setPrevValue(Object object) {
			this.prevValue = object;

		}

		public Object getPrevValue() {
			return prevValue;
		}
	}

	private final static ThreadLocal<ThreadLocalBuffers> threadLocalBuffers = new ThreadLocal<ThreadLocalBuffers>() {

		@Override
		protected ThreadLocalBuffers initialValue() {
			return new ThreadLocalBuffers();
		}

	};

	final static private <K, V> InternalNode<K, V> getInternalNodeUsingComparator(Node<K, V> root, K key,
			Comparator<? super K> comparator) {
		Node<K, V> currentNode = root;
		Comparator<? super K> cpr = comparator;
		InternalNode<K, V> currentNodeInt = null;
		while (currentNode instanceof InternalNode) {
			currentNodeInt = (InternalNode<K, V>) currentNode;
			K nodeKey = currentNodeInt.getKey();
			int compareValue = cpr.compare(key, nodeKey);
			if (compareValue < 0) {
				currentNode = currentNodeInt.getLeft();
			} else {
				currentNode = currentNodeInt.getRight();
			}
		}
		return currentNodeInt;
	}

	final static private <K, V> InternalNode<K, V> getInternalNodeUsingComparator(Node<K, V> root, K key,
			Comparator<? super K> comparator, Stack<Node> stack) {
		Node<K, V> currentNode = root;
		Comparator<? super K> cpr = comparator;
		InternalNode<K, V> currentNodeInt = null;
		while (currentNode instanceof InternalNode) {
			stack.push(currentNode);
			currentNodeInt = (InternalNode<K, V>) currentNode;
			K nodeKey = currentNodeInt.getKey();
			int compareValue = cpr.compare(key, nodeKey);
			if (compareValue < 0) {
				currentNode = currentNodeInt.getLeft();
			} else {
				currentNode = currentNodeInt.getRight();
			}
		}
		return currentNodeInt;
	}

	final static private <K, V> InternalNode<K, V> getInternalNode(Node<K, V> root, K keyParam,
			Comparator<? super K> comparator) {
		if (comparator != null) {
			return getInternalNodeUsingComparator(root, keyParam, comparator);
		} else {
			@SuppressWarnings("unchecked")
			Comparable<? super K> key = (Comparable<? super K>) keyParam;
			Node<K, V> currentNode = root;
			InternalNode<K, V> currentNodeInt = null;
			while (currentNode instanceof InternalNode) {
				currentNodeInt = (InternalNode<K, V>) currentNode;
				K nodeKey = currentNodeInt.getKey();
				int compareValue = key.compareTo(nodeKey);
				if (compareValue < 0) {
					currentNode = currentNodeInt.getLeft();
				} else {
					currentNode = currentNodeInt.getRight();
				}
			}
			return currentNodeInt;
		}
	}

	final static private <K, V> InternalNode<K, V> getInternalNode(Node<K, V> root, K keyParam,
			Comparator<? super K> comparator, @SuppressWarnings("rawtypes") Stack<Node> stack) {
		if (comparator != null) {
			return getInternalNodeUsingComparator(root, keyParam, comparator, stack);
		} else {
			@SuppressWarnings("unchecked")
			Comparable<? super K> key = (Comparable<? super K>) keyParam;
			Node<K, V> currentNode = root;
			InternalNode<K, V> currentNodeInt = null;
			while (currentNode instanceof InternalNode) {
				stack.push(currentNode);
				currentNodeInt = (InternalNode<K, V>) currentNode;
				K nodeKey = currentNodeInt.getKey();
				int compareValue = key.compareTo(nodeKey);
				if (compareValue < 0) {
					currentNode = currentNodeInt.getLeft();
				} else {
					currentNode = currentNodeInt.getRight();
				}
			}
			return currentNodeInt;
		}
	}

	final static public <K, V> K minKey(ImmutableTreapValue<K, V> root) {
		Node<K, V> currentNode = (Node<K, V>) root;
		InternalNode<K, V> currentNodeInt = null;
		while (currentNode instanceof InternalNode) {
			currentNodeInt = (InternalNode<K, V>) currentNode;
			currentNode = currentNodeInt.getLeft();
		}
		return ((ExternalNode<K, V>) currentNode).minKey();
	}

	final static private <K, V> ExternalNode<K, V> getExternalNode(Node<K, V> root, K key,
			Comparator<? super K> comparator) {
		InternalNode<K, V> internalParent = getInternalNode(root, key, comparator);
		if (internalParent == null) {
			return (ExternalNode<K, V>) root;
		} else if (compare(key, internalParent.getKey(), comparator) < 0) {
			return (ExternalNode<K, V>) internalParent.getLeft();
		} else {
			return (ExternalNode<K, V>) internalParent.getRight();
		}
	}

	@SuppressWarnings("unchecked")
	public static <K, V> V get(ImmutableTreapValue<K, V> root, K key, Comparator<? super K> comparator) {
		ExternalNode<K, V> node = getExternalNode((Node<K, V>) root, key, comparator);
		int index = node.indexOfKeyInNode(key, comparator);
		if (index < 0) {
			return null;
		} else {
			return (V) node.getValues()[index];
		}

	}

	// public static int i;

	public static <K, V> ImmutableTreapValue<K, V> put(ImmutableTreapValue<K, V> root, K key, V value,
			Comparator<? super K> comparator) {
		return put((Node<K, V>) root, key, value, true, comparator);
	}

	public static <K, V> ImmutableTreapValue<K, V> putIfAbsent(ImmutableTreapValue<K, V> root, K key, V value,
			Comparator<? super K> comparator) {
		return put((Node<K, V>) root, key, value, false, comparator);
	}

	private static final <K, V> Node<K, V> put(Node<K, V> root, K key, V value, boolean replace,
			Comparator<? super K> comparator) {
		@SuppressWarnings("rawtypes")
		Stack<Node> stack = threadLocalBuffers.get().getStack();
		InternalNode<K, V> internalParent = getInternalNode(root, key, comparator, stack);
		ExternalNode<K, V> externalNode;
		Node<K, V> topCopied;
		Node<K, V> prevTopNotCopied;
		InternalNode<K, V> topNotCopied;
		// boolean leftChild = false;
		if (internalParent == null) {
			externalNode = (ExternalNode<K, V>) root;
		} else if (compare(key, internalParent.getKey(), comparator) < 0) {
			externalNode = (ExternalNode<K, V>) internalParent.getLeft();
			// leftChild = true;
		} else {
			externalNode = (ExternalNode<K, V>) internalParent.getRight();
		}
		ExternalNode<K, V> newExternalNode = null;
		int index = externalNode.indexOfKeyInNode(key, comparator);
		if (index >= 0) {
			threadLocalBuffers.get().setPrevValue(externalNode.getValues()[index]);
			if (!replace) {
				return root;
			}
			newExternalNode = externalNode.replaceValueAtPos(index, value);
			topCopied = newExternalNode;
			prevTopNotCopied = externalNode;
			topNotCopied = internalParent;
		} else if (externalNode.isFull()) {
			ExternalNode<K, V> left = null;
			ExternalNode<K, V> right = null;
			int insertionPoint = (-1) * (index + 1);
			if (insertionPoint >= DEGREE / 2) {
				left = externalNode.splitLeft();
				right = externalNode.splitRightAndInsert(key, value, comparator);
			} else {
				left = externalNode.splitLeftAndInsert(key, value, comparator);
				right = externalNode.splitRight();
			}
			// System.out.println("SPLIT");
			InternalNode<K, V> newInternalNode = new InternalNode<K, V>(right.minKey(), left, right);
			stack.push(externalNode);
			// printDot(root, "/home/kjell/before_dot" + i);
			Node<K, V> res = handleInsertedInternalNode(stack, newInternalNode);
			// printDot(res, "/home/kjell/after_dot" + i);
			// i++;
			threadLocalBuffers.get().setPrevValue(null);
			return res;
			// prevTopNotCopied = externalNode;
			// topNotCopied = internalParent;
		} else {
			int insertionPoint = (-1) * (index + 1);
			newExternalNode = externalNode.addAtPos(insertionPoint, key, value);
			topCopied = newExternalNode;
			prevTopNotCopied = externalNode;
			topNotCopied = internalParent;
			threadLocalBuffers.get().setPrevValue(null);
		}
		// Copy down to the root
		stack.pop();
		while (topNotCopied != null) {
			Node<K, V> prevTopCopied = topCopied;
			if (topNotCopied.getLeft() == prevTopNotCopied) {
				topCopied = new InternalNode<K, V>(topNotCopied.getKey(), topCopied, topNotCopied.getRight(),
						topNotCopied.getWeight());
			} else {
				topCopied = new InternalNode<K, V>(topNotCopied.getKey(), topNotCopied.getLeft(), topCopied,
						topNotCopied.getWeight());
			}
			prevTopNotCopied = topNotCopied;
			topNotCopied = (InternalNode<K, V>) stack.pop();
		}
		return topCopied;
	}

	public static <K, V> ImmutableTreapValue<K, V> remove(ImmutableTreapValue<K, V> root, K key,
			Comparator<? super K> comparator) {
		Stack<Node> stack = threadLocalBuffers.get().getStack();
		InternalNode<K, V> internalParent = getInternalNode((Node<K, V>) root, key, comparator, stack);
		stack.pop();
		ExternalNode<K, V> externalNode;
		Node<K, V> topCopied;
		Node<K, V> prevTopNotCopied;
		InternalNode<K, V> topNotCopied;
		boolean leftChild = false;
		if (internalParent == null) {
			externalNode = (ExternalNode<K, V>) root;
		} else if (compare(key, internalParent.getKey(), comparator) < 0) {
			externalNode = (ExternalNode<K, V>) internalParent.getLeft();
			leftChild = true;
		} else {
			externalNode = (ExternalNode<K, V>) internalParent.getRight();
		}
		ExternalNode<K, V> newExternalNode = null;
		int index = externalNode.indexOfKeyInNode(key, comparator);
		if (index < 0) {
			threadLocalBuffers.get().setPrevValue(null);
			return root;
		}
		if (externalNode.size() > 1) {
			threadLocalBuffers.get().setPrevValue(externalNode.getValues()[index]);
			newExternalNode = externalNode.removeItemAtPos(index);
			topCopied = newExternalNode;
			prevTopNotCopied = externalNode;
			topNotCopied = internalParent;
		} else {
			threadLocalBuffers.get().setPrevValue(externalNode.getValues()[index]);
			if (internalParent == null) {
				return createEmpty();
			}
			if (leftChild) {
				topCopied = internalParent.getRight().createClone();
			} else {
				topCopied = internalParent.getLeft().createClone();
			}
			prevTopNotCopied = internalParent;
			topNotCopied = (InternalNode<K, V>) stack.pop();
		}
		// Copy down to the root

		while (topNotCopied != null) {
			Node<K, V> prevTopCopied = topCopied;
			if (topNotCopied.getLeft() == prevTopNotCopied) {
				topCopied = new InternalNode<K, V>(topNotCopied.getKey(), topCopied, topNotCopied.getRight(),
						topNotCopied.getWeight());
			} else {
				topCopied = new InternalNode<K, V>(topNotCopied.getKey(), topNotCopied.getLeft(), topCopied,
						topNotCopied.getWeight());
			}
			prevTopNotCopied = topNotCopied;
			topNotCopied = (InternalNode<K, V>) stack.pop();
		}
		return topCopied;
	}

	public static <K, V> ImmutableTreapValue<K, V> createEmpty() {
		ExternalNode<K, V> node = new ExternalNode<>();
		node.keys = new Object[0];
		node.values = new Object[0];
		return node;
	}

	public static <K, V> ImmutableTreapValue<K, V> splitLeft(ImmutableTreapValue<K, V> root) {
		if (root instanceof ExternalNode) {
			return ((ExternalNode<K, V>) root).splitLeft();
		} else {
			return ((InternalNode<K, V>) root).getLeft();
		}
	}

	public static <K, V> ImmutableTreapValue<K, V> splitRight(ImmutableTreapValue<K, V> root) {
		if (root instanceof ExternalNode) {
			return ((ExternalNode<K, V>) root).splitRight();
		} else {
			return ((InternalNode<K, V>) root).getRight();
		}
	}

	/*
	 * Stack should be empty when calling this function After the call the top
	 * of the stack will contain the root of a join of the two input trees (also
	 * returned as the return value). The returned stack contains the path to
	 * the returned node in the tree which has two external nodes as children
	 * and might need to be rotated to the right place.
	 */
	private static <K, V> InternalNode<K, V> joinHelper(Node<K, V> left, Node<K, V> right, Stack<Node> stack) {

		if (left instanceof ExternalNode && right instanceof ExternalNode) {
			Node<K, V> newLeft = left.createClone();
			ExternalNode<K, V> newRight = (ExternalNode<K, V>) right.createClone();
			InternalNode<K, V> root = new InternalNode<>(newRight.minKey(), newLeft, newRight);
			stack.push(root);
			return root;
		} else if (left.getWeight() > right.getWeight()) {
			InternalNode<K, V> eleft = (InternalNode<K, V>) left;
			Node<K, V> recNode = joinHelper(eleft.getRight(), right, stack);
			InternalNode<K, V> newTree = new InternalNode<K, V>(eleft.getKey(), eleft.getLeft(), recNode,
					eleft.getWeight());
			stack.push(newTree);
			return newTree;
		} else {
			InternalNode<K, V> eright = (InternalNode<K, V>) right;
			Node<K, V> recNode = joinHelper(left, eright.getLeft(), stack);
			InternalNode<K, V> newTree = new InternalNode<K, V>(eright.getKey(), recNode, eright.getRight(),
					eright.getWeight());
			stack.push(newTree);
			return newTree;
		}
	}

	public static <K, V> ImmutableTreapValue<K, V> join(ImmutableTreapValue<K, V> left,
			ImmutableTreapValue<K, V> right) {
		if (left instanceof ExternalNode) {
			ExternalNode<K, V> e = (ExternalNode<K, V>) left;
			if (e.size() == 0) {
				return right;
			}
		}
		if (right instanceof ExternalNode) {
			ExternalNode<K, V> e = (ExternalNode<K, V>) right;
			if (e.size() == 0) {
				return left;
			}
		}
		Stack<Node> stack = threadLocalBuffers.get().getStack();
		joinHelper((Node<K, V>) left, (Node<K, V>) right, stack);
		stack.reverseStack();
		return handleInsertedInternalNode(stack, (InternalNode<K, V>) stack.top());
	}

    public static <K, V> ImmutableTreapValue<K, V> cheapJoin(ImmutableTreapValue<K, V> left,
                                                             ImmutableTreapValue<K, V> right) {
        if (left instanceof ExternalNode) {
            ExternalNode<K, V> e = (ExternalNode<K, V>) left;
            if (e.size() == 0) {
                return right;
            }
        }
        if (right instanceof ExternalNode) {
            ExternalNode<K, V> e = (ExternalNode<K, V>) right;
            if (e.size() == 0) {
                return left;
            }
        }
        Node<K, V> leftN = (Node<K, V>) left;
        Node<K, V> rightN = (Node<K, V>) right;
        return new InternalNode<K, V>(minKey(right), leftN, rightN, Long.MAX_VALUE);
        //return join(left, right);
    }
	
	private static <K, V> Node<K, V> handleInsertedInternalNode(Stack<Node> stack, InternalNode<K, V> nodeClone) {
		Node node = stack.pop();
		InternalNode<K, V> parent = (InternalNode<K, V>) stack.pop();
		while (parent != null) {
			InternalNode<K, V> parentClone = (InternalNode<K, V>) parent.createClone();
			if (parent.getLeft() == node) {
				parentClone.left = nodeClone;
				if (nodeClone.getWeight() > parent.getWeight()) {
					// do right rotation
					parentClone.left = nodeClone.right;
					nodeClone.right = parentClone;
				} else {
					nodeClone = parentClone;
				}
			} else {
				parentClone.right = nodeClone;
				if (nodeClone.getWeight() > parent.getWeight()) {
					// do left rotation
					parentClone.right = nodeClone.left;
					nodeClone.left = parentClone;
				} else {
					nodeClone = parentClone;
				}
			}
			node = parent;
			parent = (InternalNode<K, V>) stack.pop();
		}
		return nodeClone;
	}

	public static <K, V> void printDotHelper(Node<K, V> node, PrintStream writeTo) {
		Random rand = new Random();
		try {
			if (node instanceof InternalNode) {
				InternalNode<K, V> nodeI = (InternalNode<K, V>) node;
				if (nodeI.getLeft() != null) {
					writeTo.print("\"" + nodeI.nodeKeysToString() + ", " + node.getWeight() + "\"");
					writeTo.print(" -> ");
					writeTo.print(
							"\"" + nodeI.getLeft().nodeKeysToString() + ", " + nodeI.getLeft().getWeight() + "\"");
					writeTo.println(";");
				}
				if (nodeI.getRight() != null) {
					writeTo.print("\"" + nodeI.getKey() + ", " + node.getWeight() + "\"");
					writeTo.print(" -> ");
					writeTo.print(
							"\"" + nodeI.getRight().nodeKeysToString() + ", " + nodeI.getRight().getWeight() + "\"");
					writeTo.println(";");
				}
				printDotHelper(nodeI.getLeft(), writeTo);
				printDotHelper(nodeI.getRight(), writeTo);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static <K, V> void printDot(ImmutableTreapValue<K, V> node, String fileName) {
		try {
			Process p = new ProcessBuilder("dot", "-Tpng")
					.redirectOutput(ProcessBuilder.Redirect.to(new File(fileName + ".png"))).start();
			PrintStream writeTo = new PrintStream(p.getOutputStream());
			writeTo.print("digraph G{\n");
			writeTo.print("  graph [ordering=\"out\"];\n");
			if (node instanceof ExternalNode) {
				writeTo.print("\"" + ((Node) node).nodeKeysToString() + ", " + ((Node) node).getWeight() + "\"");
				writeTo.print(" -> ");
				writeTo.print("\"nothing\"");
			} else {
				printDotHelper((Node<K, V>) node, writeTo);
			}
			writeTo.print("}\n");
			writeTo.close();
			p.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static final <K, V> void traverseKeysInRange(ImmutableTreapValue<K, V> root, K lo, K hi, Consumer<K> consumer,
			Comparator<? super K> comparator) {
	    traverseKeysInRangeStack1(root, lo, hi, consumer, comparator);

	}

    private static final <K, V> void traverseKeysInRangeStack2(ImmutableTreapValue<K, V> root, K lo, K hi, Consumer<K> consumer, Comparator<? super K> comparator) {
	Stack<InternalNode<K,V>> stack = new Stack<InternalNode<K,V>>();//threadLocalBuffers.get().getStack();
    	Node<K,V> node = (Node<K, V>) root;
    	boolean firstExternalNode = true;
    	while(stack.size() != 0 || firstExternalNode ){
	    if(node instanceof InternalNode){
		InternalNode<K,V> nodeI = (InternalNode<K, V>) node;
		if(compare(hi, nodeI.getKey(), comparator) >= 0){
		    stack.push(nodeI);
		}
		if(compare(lo, nodeI.getKey(), comparator) < 0){
		    node = nodeI.getLeft();
		}else{
		    nodeI = stack.pop();
		    if(nodeI == null) return;
		    node = nodeI.getRight();
		}
	    }else{
		ExternalNode<K,V> nodeE = (ExternalNode<K, V>) node;
		if(firstExternalNode){
		    if(nodeE.firstNodeTraverseRange(lo, hi, consumer, comparator)){
			return;
		    }
		    firstExternalNode = false;
		}else{
		    if(nodeE.notFirstNodeTraverseRange(lo, hi, consumer, comparator)){
			return;
		    }
		}
            	InternalNode<K,V> nodeI = stack.pop();
		if(nodeI == null) return;
		node = nodeI.getRight();
	    }
        }

    }

	private static final <K, V> void traverseKeysInRangeStack1(ImmutableTreapValue<K, V> root, K lo, K hi,
			Consumer<K> consumer, Comparator<? super K> comparator) {
		Stack<Node> stack = threadLocalBuffers.get().getStack();
		Node<K, V> node = (Node<K, V>) root;
		boolean firstExternalNode = true;
		while (stack.size() != 0 || node != null) {
			if (node instanceof InternalNode) {
				InternalNode<K, V> nodeI = (InternalNode<K, V>) node;
				if (compare(hi, nodeI.getKey(),
						comparator) >= 0 /* greaterThanEqual(hi, node.key) */) {
					stack.push(node);
				}
				if (compare(lo, nodeI.getKey(),
						comparator) <= 0 /* lessThanEqual(lo, node.key) */) {
					node = nodeI.getLeft();
				} else {
					node = null;
				}
			} else {
				ExternalNode<K, V> nodeE = (ExternalNode<K, V>) node;
				if (node != null && nodeE.size() != 0) {
					int startPos = 0;
					if (firstExternalNode) {
						int index = nodeE.indexOfKeyInNode(lo, comparator);
						if (index >= 0) {
							startPos = index;
						} else {
							startPos = (-1) * (index + 1);
						}
						firstExternalNode = false;
					}
					if (compare(nodeE.maxKey(), hi, comparator) <= 0) {
						for (int i = startPos; i < nodeE.getKeys().length; i++) {
							consumer.accept((K) nodeE.getKeys()[i]);
						}
					} else {
						int endPos = 0;
						int index = nodeE.indexOfKeyInNode(hi, comparator);
						if (index >= 0) {
							endPos = index + 1;
						} else {
							endPos = (-1) * (index + 1);
						}
						for (int i = startPos; i < endPos; i++) {
							consumer.accept((K) nodeE.getKeys()[i]);
						}
					}
				}
				node = stack.pop();
				InternalNode<K, V> nodeI = (InternalNode<K, V>) node;
				if (nodeI != null && compare(hi, nodeI.getKey(),
						comparator) >= 0 /* greaterThanEqual(hi, node.key) */) {
					node = nodeI.getRight();
				} else {
					node = null;
				}
			}
		}
	}

	public static <K, V> void traverseAllItems(ImmutableTreapValue<K, V> root, BiConsumer<K, V> consumer) {
		Stack<Node> stack = threadLocalBuffers.get().getStack();
		Node<K, V> node = (Node<K, V>) root;
		while (stack.size() != 0 || node != null) {
			if (node instanceof InternalNode) {
				stack.push(node);
				node = ((InternalNode) node).getLeft();
			} else {
				ExternalNode<K, V> nodeE = (ExternalNode<K, V>) node;
				for (int i = 0; i < nodeE.size(); i++) {
					consumer.accept((K) nodeE.getKeys()[i], (V) nodeE.getValues()[i]);
				}
				node = stack.pop();
				if (node != null) {
					InternalNode nodeI = ((InternalNode) node);
					node = nodeI.getRight();
				}
			}
		}
	}

	public static <K, V> int size(ImmutableTreapValue<K, V> root) {
		threadLocalBuffers.get().sizeCounter = 0;
		traverseAllItems(root, (k, v) -> threadLocalBuffers.get().sizeCounter++);
		return threadLocalBuffers.get().sizeCounter;
	}

	public static <K, V> boolean isEmpty(ImmutableTreapValue<K, V> root) {
		if (root instanceof ExternalNode) {
			ExternalNode<K, V> n = (ExternalNode<K, V>) root;
			return n.getKeys().length == 0;
		}
		return false;
	}

	public static <K, V> boolean lessThanTwoElements(ImmutableTreapValue<K, V> root) {
		if (root instanceof ExternalNode) {
			ExternalNode<K, V> n = (ExternalNode<K, V>) root;
			return n.size() < 2;
		}
		return false;
	}

	public static <K, V> K maxKey(ImmutableTreapValue<K, V> root) {
		Node<K, V> currentNode = (Node<K, V>) root;
		InternalNode<K, V> currentNodeInt = null;
		while (currentNode instanceof InternalNode) {
			currentNodeInt = (InternalNode<K, V>) currentNode;
			currentNode = currentNodeInt.getRight();
		}
		return ((ExternalNode<K, V>) currentNode).maxKey();
	}

	private static <V> ImmutableTreapValue<Integer, V> putIfAbsentTest(TreeMap<Integer, V> model,
			ImmutableTreapValue<Integer, V> treap, Integer k, V v) {
		ImmutableTreapValue<Integer, V> oldTreap = treap;
		ImmutableTreapValue<Integer, V> newTreap = putIfAbsent(treap, k, v, null);
		checkEqualAndOrder(model, oldTreap);
		V res = model.putIfAbsent(k, v);
		assert (res.equals(getPrevValue()));
		checkEqualAndOrder(model, newTreap);
		return newTreap;
	}

	private static <V> ImmutableTreapValue<Integer, V> putTest(TreeMap<Integer, V> model,
			ImmutableTreapValue<Integer, V> treap, Integer k, V v) {
		ImmutableTreapValue<Integer, V> oldTreap = treap;
		ImmutableTreapValue<Integer, V> newTreap = put(treap, k, v, null);
		checkEqualAndOrder(model, oldTreap);
		V res = model.put(k, v);
		assert (res.equals(getPrevValue()));
		checkEqualAndOrder(model, newTreap);
		return newTreap;
	}

	private static <V> ImmutableTreapValue<Integer, V> removeTest(TreeMap<Integer, V> model,
			ImmutableTreapValue<Integer, V> treap, Integer k) {
		ImmutableTreapValue<Integer, V> oldTreap = treap;
		ImmutableTreapValue<Integer, V> newTreap = remove(treap, k, null);
		checkEqualAndOrder(model, oldTreap);
		V res = model.remove(k);
		assert (res.equals(getPrevValue()));
		checkEqualAndOrder(model, newTreap);
		return newTreap;
	}

	private static <V> V getTest(TreeMap<Integer, V> model, ImmutableTreapValue<Integer, V> treap, Integer k) {
		V res1 = get(treap, k, null);
		V res2 = model.get(k);
		assert (res1.equals(res2));
		return res1;
	}

	private static int prevKey;

	private static <V> void checkEqualAndOrder(TreeMap<Integer, V> model, ImmutableTreapValue<Integer, V> oldTreap) {
		assert (size(oldTreap) == model.size());
		prevKey = Integer.MIN_VALUE;
		traverseAllItems(oldTreap, (key, value) -> {
			assert (prevKey < key);
			prevKey = key;
			assert (model.get(key).equals(value));
		});
		for (Integer key : model.keySet()) {
			get(oldTreap, key, null).equals(key);

		}
	}

	private static void checkHeapProperty(ImmutableTreapValue<Integer, Integer> treap, long parentWeight) {
		if (treap instanceof ExternalNode) {
			ExternalNode<Integer, Integer> t = (ExternalNode<Integer, Integer>) treap;
			assert (t.getWeight() == 0);
		} else {
			InternalNode<Integer, Integer> t = (InternalNode<Integer, Integer>) treap;
			assert (t.weight <= parentWeight);
			checkHeapProperty(t.getLeft(), t.getWeight());
			checkHeapProperty(t.getRight(), t.getWeight());
		}

	}

	public static void main(String[] args) {
		{
			int size = 100;
			ImmutableTreapValue<Integer, Integer> node = createEmpty();
			for (int i = 0; i < size; i++) {
				System.out.println("i: " + i);
				node = put(node, i, i, null);
			}
			// printDot(node, "/home/kjell/dot");
			ImmutableTreapValue<Integer, Integer> nodeLeft = splitLeft(node);
			ImmutableTreapValue<Integer, Integer> nodeRight = splitRight(node);
			printDot(nodeLeft, "/home/kjell/dot_left");
			printDot(nodeRight, "/home/kjell/dot_right");
			ImmutableTreapValue<Integer, Integer> nodeJoin = cheapJoin(nodeLeft, nodeRight);
			printDot(nodeJoin, "/home/kjell/dot_join");
			// printDot(nodeLeft, "/home/kjell/dot_left2");
			// printDot(nodeRight, "/home/kjell/dot_right2");
			System.out.println("TRAVERSE ALL");
			traverseAllItems(node, (k, v) -> System.out.print("(" + k + "," + v + "),"));
			int rangeStart = -10;
			int rangeEnd = 5;
System.out.println("==============================================================================================================================================");
			System.out.println("Print range " + rangeStart + " " + rangeEnd);
			traverseKeysInRange(node, rangeStart, rangeEnd, (k) -> System.out.print(k + ","), null);
			System.out.println("");
			rangeStart = -10;
			rangeEnd = 50;
			System.out.println("Print range " + rangeStart + " " + rangeEnd);
			traverseKeysInRange(node, rangeStart, rangeEnd, (k) -> System.out.print(k + ","), null);
			System.out.println("");
			rangeStart = 50;
			rangeEnd = 98;
			System.out.println("Print range " + rangeStart + " " + rangeEnd);
			traverseKeysInRange(node, rangeStart, rangeEnd, (k) -> System.out.print(k + ","), null);
			System.out.println("");
			rangeStart = 99;
			rangeEnd = 100;
			System.out.println("Print range " + rangeStart + " " + rangeEnd);
			traverseKeysInRange(node, rangeStart, rangeEnd, (k) -> System.out.print(k + ","), null);
			System.out.println("");
			rangeStart = -10;
			rangeEnd = 1;
			System.out.println("Print range " + rangeStart + " " + rangeEnd);
			traverseKeysInRange(node, rangeStart, rangeEnd, (k) -> System.out.print(k + ","), null);
			System.out.println("");
			for (int i = 0; i < size; i++) {
				int got = get(node, i, null);
				assert (i == got);
				System.out.println(got);
			}
			for (int i = size; i < size + 10; i++) {
				Integer got = get(node, i, null);
				assert (null == got);
				System.out.println(got);
			}
			for (int i = 0; i < 40; i++) {
				System.out.println("r: " + i);
				node = remove(node, i, null);
			}
			for (int i = 0; i < size; i++) {
				Integer got = get(node, i, null);
				System.out.println(got);
			}
		}

		// Random test
//		System.out.println("RANDOM TEST");
//		TreeMap<Integer, Integer> model = new TreeMap<>();
//		ImmutableTreapValue<Integer, Integer> treap = createEmpty();
//		Random r = new Random();
//		int rangeSize = 1000;
//		int iterations = 100000;
//		for (int i = 0; i < iterations; i++) {
//			if (i % 10000 == 0) {
//				System.out.println("Random it " + i);
//			}
//			Integer item = r.nextInt(rangeSize);
//			treap = putIfAbsentTest(model, treap, item, item);
//			item = r.nextInt(rangeSize);
//			treap = putTest(model, treap, item, item);
//			item = r.nextInt(rangeSize);
//			treap = removeTest(model, treap, item);
//			item = r.nextInt(rangeSize);
//			getTest(model, treap, item);
//		}
//		checkHeapProperty(treap, Long.MAX_VALUE);

//		printDot(treap, "/home/kjell/treap");
	}

}
