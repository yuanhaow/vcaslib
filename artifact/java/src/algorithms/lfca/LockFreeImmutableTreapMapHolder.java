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

package algorithms.lfca;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import algorithms.lfca.ImmutableTreapMap.ImmutableTreapValue;

public abstract class LockFreeImmutableTreapMapHolder<K, V> {
	 static final int STAT_LOCK_HIGH_CONTENTION_LIMIT = 1000;
	 static final int STAT_LOCK_LOW_CONTENTION_LIMIT = -1000;
	 static final int STAT_LOCK_FAILURE_CONTRIB = 250;
	 static final int STAT_LOCK_SUCCESS_CONTRIB = 1;
	 static final int RANGE_MORE_THAN_ONE_BASE_STAT_REDUCTION = 100;
	// Number of elements per node (should be 3 or greater)
	final ImmutableTreapValue<K, V> root;
	final Object parent;
	final int statistics;
	final Comparator<? super K> comparator;

	
	// === Public functions and helper functions ===

	
	public abstract int newStatistics();
	public abstract NormalLockFreeImmutableTreapMapHolder<K,V> normalize(boolean contended);
	public abstract NormalLockFreeImmutableTreapMapHolder<K,V> normalize();
	
	public K anyKey() {
		return ImmutableTreapMap.minKey(root);
	}

	public int getHighContentionLimit() {
		return STAT_LOCK_HIGH_CONTENTION_LIMIT;
	}

	public int getLowContentionLimit() {
		return STAT_LOCK_LOW_CONTENTION_LIMIT;
	}

	public boolean isHighContentionLimitReached() {
		return getHighContentionLimit() < statistics; 
	}

	public boolean isLowContentionLimitReached() {
		return getLowContentionLimit() > statistics;
	}

	final protected void addAllToList(LinkedList<Map.Entry<K, V>> list) {
		ImmutableTreapMap.traverseAllItems(root, (k, v) -> list.add(new AbstractMap.SimpleImmutableEntry<K, V>(k, v) {
				private static final long serialVersionUID = 1L;
			public int hashCode() {
				return k.hashCode();
			}
		}));
	}

	// END =======================================

	
	public NormalLockFreeImmutableTreapMapHolder<K,V> setParent(Object newParent) {
		return new NormalLockFreeImmutableTreapMapHolder<>(comparator, 
				newStatistics(),
				root, newParent);

	}
	
	@SuppressWarnings("unused")
	private int compare(K key1, K key2) {
		if (comparator == null) {
			@SuppressWarnings("unchecked")
			Comparable<? super K> keyComp = (Comparable<? super K>) key1;
			return keyComp.compareTo(key2);
		} else {
			return comparator.compare(key1, key2);
		}
	}



	public LockFreeImmutableTreapMapHolder() {
		this(null, 0, ImmutableTreapMap.createEmpty(), null);
	}
	
	public LockFreeImmutableTreapMapHolder(Comparator<? super K> comparator) {
		this(comparator, 0, ImmutableTreapMap.createEmpty(), null);
	}

	public LockFreeImmutableTreapMapHolder(Comparator<? super K> comparator, int statistics, ImmutableTreapMap.ImmutableTreapValue<K, V> root, Object parent) {
		this.statistics = statistics;
		this.comparator = comparator;
		this.root = root;
		this.parent = parent;
	}

	public int size() {
		return computeActualSize();
	}

	public boolean isEmpty() {
		return ImmutableTreapMap.isEmpty(root);
	}

	@SuppressWarnings("unchecked")
	public boolean containsKey(Object key) {
		return ImmutableTreapMap.get(root, (K) key, comparator) != null;
	}

	@SuppressWarnings("unchecked")
	public V get(Object key) {
		return ImmutableTreapMap.get(root, (K) key, comparator);
	}


	public NormalLockFreeImmutableTreapMapHolder<K, V> put(K key, V value, Object parent, boolean contended) {
		return new NormalLockFreeImmutableTreapMapHolder<K, V>(comparator, 
				contended ? newStatistics() + STAT_LOCK_FAILURE_CONTRIB : newStatistics() - STAT_LOCK_SUCCESS_CONTRIB, 
						ImmutableTreapMap.put(this.root, key, value, comparator), parent);
	}

	public NormalLockFreeImmutableTreapMapHolder<K,V> putIfAbsent(K key, V value, Object parent,  boolean contended) {
		ImmutableTreapValue<K, V> newRoot = ImmutableTreapMap.putIfAbsent(this.root, key, value, comparator);
		if(newRoot == root) {
			return null;
		}else {
			return new NormalLockFreeImmutableTreapMapHolder<>(comparator, 
							contended ? newStatistics() + STAT_LOCK_FAILURE_CONTRIB : newStatistics() - STAT_LOCK_SUCCESS_CONTRIB, 
							newRoot, parent);
		}

	}

	@SuppressWarnings("unchecked")
	public NormalLockFreeImmutableTreapMapHolder<K,V> remove(Object key, Object parent, boolean contended) {
		ImmutableTreapValue<K, V> newRoot = ImmutableTreapMap.remove(root, (K) key, comparator);
		if(newRoot == root) {
			return null;
		}else {
			return new NormalLockFreeImmutableTreapMapHolder<>(comparator, 
					contended ? newStatistics() + STAT_LOCK_FAILURE_CONTRIB : newStatistics() - STAT_LOCK_SUCCESS_CONTRIB
							,  newRoot , parent);
		}
	}


	public LockFreeImmutableTreapMapHolder<K, V> join(LockFreeImmutableTreapMapHolder<K, V> right, Object parent) {
		return new NormalLockFreeImmutableTreapMapHolder<K,V>(comparator, 0, ImmutableTreapMap.join(this.root, right.root), parent);
	}

	public LockFreeImmutableTreapMapHolder<K, V> split(Object[] splitKeyWriteBack,
			LockFreeImmutableTreapMapHolder<K, V>[] rightTreeWriteBack, Object parent) {
		ImmutableTreapValue<K, V> newLeftPart = ImmutableTreapMap.splitLeft(root);
		ImmutableTreapValue<K, V> newRightPart = ImmutableTreapMap.splitRight(root);
		splitKeyWriteBack[0] = ImmutableTreapMap.minKey(newRightPart);
		rightTreeWriteBack[0] = new NormalLockFreeImmutableTreapMapHolder<>(comparator, 0, newRightPart, parent);
		return  new NormalLockFreeImmutableTreapMapHolder<>(comparator, 0, newLeftPart, parent);
	}

	public Set<java.util.Map.Entry<K, V>> entrySet() {
		TreeMap<K, V> entrySet;
		if (comparator == null) {
			entrySet = new TreeMap<K, V>();
		} else {
			entrySet = new TreeMap<K, V>(comparator);
		}
		ImmutableTreapMap.traverseAllItems(root, (k, v) -> entrySet.put(k, v));
		return entrySet.entrySet();
	}

	public static void main(String[] args) {
//		LockFreeImmutableTreapMapHolder<Integer, Integer> map = new LockFreeImmutableTreapMapHolder<Integer, Integer>();
//		for (int i = 10; i >= 0; i--) {
//			System.out.println("INSERT: " + i);
//			map.put(i, i);
//		}
//		for (Integer e : map.keySet()) {
//			System.out.println(e);
//		}
//		for (int i = 10; i >= 0; i--) {
//			System.out.println("GET: " + i);
//			System.out.println(map.get(i));
//		}
//		for (int i = 10; i >= 0; i--) {
//			System.out.println("Remove: " + i);
//			map.remove(i, i);
//		}
//		// for(Integer e: map.keySet()){
//		// System.out.println(e);
//		// }
//		for (int i = 0; i < 100000; i++) {
//			// System.out.println("INSERT: " + i);
//			map.put(i, i);
//		}
//		System.out.println(map.get(50000));
	}

	public boolean hasLessThanTwoElements() {
		return ImmutableTreapMap.lessThanTwoElements(root);
	}

	public void traverseKeysInRange(K lo, K hi, Consumer<K> consumer) {
		ImmutableTreapMap.traverseKeysInRange(root, lo, hi, consumer, comparator);
	}

	public void performOperationToValuesInRange(K lo, K hi, BiFunction<K, V, V> operation) {
		throw new RuntimeException("Not yet defined");
	}

	int counter = 0;

	private int computeActualSize() {
		counter = 0;
		ImmutableTreapMap.traverseAllItems(root, (k, v) -> counter++);
		return counter;
	}

	public ImmutableTreapValue<K, V> getRoot() {
		return root;
	}

	public Object getParent() {
		return parent;
	}

	public LockFreeImmutableTreapMapHolder<K, V> resetStatistics() {
		return new NormalLockFreeImmutableTreapMapHolder<K, V>(comparator, 0, root, parent);
	}
	public K maxKey() {
		return ImmutableTreapMap.maxKey(root);
	}

}

final class NormalLockFreeImmutableTreapMapHolder<K, V> extends LockFreeImmutableTreapMapHolder<K, V>{

	public NormalLockFreeImmutableTreapMapHolder(Comparator<? super K> comparator, int statistics,
			ImmutableTreapValue<K, V> root, Object parent) {
		super(comparator, statistics, root, parent);
	}

	public NormalLockFreeImmutableTreapMapHolder(Comparator<? super K> comparator) {
		super(comparator);
	}

	@Override
	public NormalLockFreeImmutableTreapMapHolder<K,V> normalize(boolean contended) {
		throw new RuntimeException("It does not make sense to normalize a normal node");
	}

	@Override
	public NormalLockFreeImmutableTreapMapHolder<K,V> normalize() {
		throw new RuntimeException("It does not make sense to normalize a normal node");
	}

	@Override
	public int newStatistics() {
		return statistics;
	}
}

final class CollectAllLockFreeImmutableTreapMapHolder<K, V> extends LockFreeImmutableTreapMapHolder<K, V>{


	public CollectAllLockFreeImmutableTreapMapHolder(NormalLockFreeImmutableTreapMapHolder<K,V> base, AtomicStampedReference<ImmutableTreapValue<K, V>> resultStorage) {
		super(base.comparator, base.newStatistics(), base.root, base.parent);
		this.allNodes = resultStorage;
	}
	
	private final AtomicStampedReference<ImmutableTreapValue<K, V>> allNodes;
	public boolean isValid() {
		return allNodes.getStamp() == 0;
	}
	public ImmutableTreapValue<K, V> getResult(){
		return allNodes.getReference();
	}
	
	public AtomicStampedReference<ImmutableTreapValue<K, V>> getResultStorage(){
		return allNodes;
	}
	@Override
	public NormalLockFreeImmutableTreapMapHolder<K,V> normalize(boolean contended) {
			return new NormalLockFreeImmutableTreapMapHolder<>(comparator, 
					newStatistics(),
					root, parent);
	}
	@Override
	public NormalLockFreeImmutableTreapMapHolder<K,V> normalize() {
			return new NormalLockFreeImmutableTreapMapHolder<>(comparator, 
					newStatistics(),
					root, parent);
	}
	@Override
	public int newStatistics() {
		if(allNodes.getStamp() == 2) {
			return statistics - RANGE_MORE_THAN_ONE_BASE_STAT_REDUCTION;
		}else {
			return statistics;
		}
	}
}


final class CollectRangeQueryImmutableTreapMapHolder<K, V> extends LockFreeImmutableTreapMapHolder<K, V>{

	public CollectRangeQueryImmutableTreapMapHolder(K lo, K hi, LockFreeImmutableTreapMapHolder<K,V> base, AtomicStampedReference<ImmutableTreapValue<K, V>> resultStorage) {
		super(base.comparator, base.newStatistics(), base.root, base.parent);
		this.allNodes = resultStorage;
		this.lo = lo;
		this.hi = hi;
	}
	

	private final K lo;
	private final K hi;
	
	private final AtomicStampedReference<ImmutableTreapValue<K, V>> allNodes;
	public boolean isValid() {
		return allNodes.getStamp() == 0;
	}
	public ImmutableTreapValue<K, V> getResult(){
		return allNodes.getReference();
	}
	
	public AtomicStampedReference<ImmutableTreapValue<K, V>> getResultStorage(){
		return allNodes;
	}
	@Override
	public NormalLockFreeImmutableTreapMapHolder<K,V> normalize(boolean contended) {
			return new NormalLockFreeImmutableTreapMapHolder<>(comparator, 
					newStatistics(),
					root, parent);
	}
	@Override
	public NormalLockFreeImmutableTreapMapHolder<K,V> normalize() {
			return new NormalLockFreeImmutableTreapMapHolder<>(comparator, 
					newStatistics(),
					root, parent);

	}
	@Override
	public int newStatistics() {
		if(allNodes.getStamp() == 2) {
			return statistics - RANGE_MORE_THAN_ONE_BASE_STAT_REDUCTION;
		}else {
			return statistics;
		}
	}
	
	public K getLo() {
		return lo;
	}
	public K getHi() {
		return hi;
	}
}


final class JoinNeighborImmutableTreapMapHolder<K, V> extends LockFreeImmutableTreapMapHolder<K, V>{

	
	private final boolean isJoined;
	final private JoinMainImmutableTreapMapHolder<K,V> mainBase;
	
	public static <K> int compare(K key1, K key2, Comparator<? super K> comparator) {
		if (comparator == null) {
			@SuppressWarnings("unchecked")
			Comparable<? super K> keyComp = (Comparable<? super K>) key1;
			return keyComp.compareTo(key2);
		} else {
			return comparator.compare(key1, key2);
		}
	}
	
	private static <K,V> ImmutableTreapValue<K, V> createJoin(ImmutableTreapValue<K, V> root,
			ImmutableTreapValue<K, V> otherRoot, Comparator<? super K> comparator) {
		if(ImmutableTreapMap.isEmpty(otherRoot)) {
			return root;
		}else if(ImmutableTreapMap.isEmpty(root)) {
			return otherRoot;
		}else if(compare(ImmutableTreapMap.maxKey(otherRoot), ImmutableTreapMap.maxKey(root), comparator) < 0) {
			return ImmutableTreapMap.join(otherRoot, root);
		}else {
			return ImmutableTreapMap.join(root, otherRoot);
		}
	}
	
	public JoinNeighborImmutableTreapMapHolder(LockFreeImmutableTreapMapHolder<K, V> neighborBase, JoinMainImmutableTreapMapHolder<K,V> main) {
		super(neighborBase.comparator, 0, neighborBase.root, neighborBase.parent);
		isJoined = false;
		mainBase = main;
	}
	
	public JoinNeighborImmutableTreapMapHolder(JoinNeighborImmutableTreapMapHolder<K, V> neighborBase, JoinMainImmutableTreapMapHolder<K,V> main) {
		super(neighborBase.comparator, 0, createJoin(main.root, neighborBase.root, main.comparator), neighborBase.parent);
		isJoined = true;
		mainBase = main;
	}

	@Override
	public NormalLockFreeImmutableTreapMapHolder<K,V> normalize(boolean contended) {
		return new NormalLockFreeImmutableTreapMapHolder<>(comparator, 
				newStatistics(),
				root, parent);
	}
	@Override
	public NormalLockFreeImmutableTreapMapHolder<K,V> normalize() {
		return new NormalLockFreeImmutableTreapMapHolder<>(comparator, 
				newStatistics(),
				root, parent);
	}

	public boolean isJoined() {
		return isJoined;
	}

	public JoinMainImmutableTreapMapHolder<K,V> getMainBase() {
		return mainBase;
	}

	@Override
	public int newStatistics() {
		return 0;
	}
	
}

final class JoinMainImmutableTreapMapHolder<K, V> extends LockFreeImmutableTreapMapHolder<K, V>{
	private JoinNeighborImmutableTreapMapHolder<K,V> fistNeighborBase;
	private volatile JoinNeighborImmutableTreapMapHolder<K,V> secondNeighborBase;
	public JoinNeighborImmutableTreapMapHolder<K, V> getSecondNeighborBase() {
		return secondNeighborBase;
	}

	private Object parentOtherBranch = null;
	private Object grandParent = null; //grandParent == this means unset
	@SuppressWarnings("unused")
	private volatile int killSwitch;
	
	
	@SuppressWarnings("rawtypes")
	private final static AtomicReferenceFieldUpdater<JoinMainImmutableTreapMapHolder, JoinNeighborImmutableTreapMapHolder> secondNeighborBaseUpdater = 
			AtomicReferenceFieldUpdater.newUpdater(JoinMainImmutableTreapMapHolder.class, JoinNeighborImmutableTreapMapHolder.class, "secondNeighborBase");
	@SuppressWarnings("rawtypes")
	private final static AtomicIntegerFieldUpdater<JoinMainImmutableTreapMapHolder> killSwitchUpdater = AtomicIntegerFieldUpdater.newUpdater(JoinMainImmutableTreapMapHolder.class, "killSwitch");
	
	public JoinNeighborImmutableTreapMapHolder<K, V> trySetSecondNeighborBase(JoinNeighborImmutableTreapMapHolder<K, V> newNewNeighborBaseP) {
		secondNeighborBaseUpdater.compareAndSet(this, null, newNewNeighborBaseP);
		return secondNeighborBase;
	}
	
	public JoinMainImmutableTreapMapHolder(LockFreeImmutableTreapMapHolder<K,V> base) {
		super(base.comparator, 0, base.root, base.parent);
		this.fistNeighborBase = null;
		this.killSwitch = 0;
	}
	
	public boolean needsHelp() {
		return killSwitchUpdater.get(this) == 2;
	}
	
	public boolean isKilled() {
		return killSwitchUpdater.get(this) == 1;
	}
	
	public boolean makeUnkillable(JoinNeighborImmutableTreapMapHolder<K, V> neighborBase, Object gparent, Object parentOtherBranch) {//
		grandParent = gparent;
		//System.out.println("nei base " + neighborBase);
		this.fistNeighborBase = neighborBase;
		this.parentOtherBranch = parentOtherBranch;
		return killSwitchUpdater.compareAndSet(this,0, 2);
	}
	
	public boolean tryKill() {
		int value = killSwitchUpdater.get(this);
		if(value >= 2) {
			return false;
		}else if(value == 1){
			return true;			
		}else {
			if(killSwitchUpdater.compareAndSet(this, 0, 1)) {
				return true;
			}
			value = killSwitchUpdater.get(this);
			if(value >= 2) {
				return false;
			}else {
				return value == 1;			
			}
		}
	}

	@Override
	public NormalLockFreeImmutableTreapMapHolder<K,V> normalize(boolean contended) {
		return new NormalLockFreeImmutableTreapMapHolder<>(comparator, 
				contended ? newStatistics() + STAT_LOCK_FAILURE_CONTRIB: newStatistics() - STAT_LOCK_SUCCESS_CONTRIB,
				root, parent);
	}
	@Override
	public NormalLockFreeImmutableTreapMapHolder<K,V> normalize() {
		return new NormalLockFreeImmutableTreapMapHolder<>(comparator, 
				newStatistics(),
				root, parent);
	}


	public JoinNeighborImmutableTreapMapHolder<K, V> getFistNeighborBase() {
		return fistNeighborBase;
	}

	public Object getParentOtherBrach() {
		return parentOtherBranch;
	}

	public Object getGrandParent() {
		return grandParent;
	}

	public void kill() {
		killSwitchUpdater.set(this,1);
		
	}

	@Override
	public int newStatistics() {
		return statistics;
	}

}




