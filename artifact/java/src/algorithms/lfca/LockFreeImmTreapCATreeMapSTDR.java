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

import java.io.File;
import java.io.PrintStream;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

// import main.Main;

import algorithms.lfca.ImmutableTreapMap.ImmutableTreapValue;

public class LockFreeImmTreapCATreeMapSTDR<K, V> extends AbstractMap<K, V>
		implements RangeQueryMap<K, V>, RangeUpdateMap<K, V> {
	private static final int MAX_BASE_NODES_OPTIMISTIC_RANGE_QUERY = 50;
	private volatile Object root;
	private final Comparator<? super K> comparator;
	// ====== FOR DEBUGING ======
	@SuppressWarnings("unused")
	private final static boolean DEBUG = false;
	private final static boolean PROFILE = false;
	// ==========================

	@SuppressWarnings("unchecked")
	private final int compare(K k1, K k2) {
		if (comparator != null) {
			return comparator.compare(k1, k2);
		} else {
			return ((Comparable<K>) k1).compareTo(k2);
		}
	}

	private final static class RouteNode {
		volatile Object left;
		volatile Object right;
		Object key;
		volatile boolean valid = true;
		@SuppressWarnings({ "rawtypes", "unused" })
		volatile JoinMainImmutableTreapMapHolder helpInfo = null;

		public RouteNode(Object key, Object left, Object right) {
			this.key = key;
			this.left = left;
			this.right = right;
		}

		public String toString() {
			return "R(" + key + ")";
		}

		private static final AtomicReferenceFieldUpdater<RouteNode, Object> leftUpdater = AtomicReferenceFieldUpdater
				.newUpdater(RouteNode.class, Object.class, "left");
		private static final AtomicReferenceFieldUpdater<RouteNode, Object> rightUpdater = AtomicReferenceFieldUpdater
				.newUpdater(RouteNode.class, Object.class, "right");
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<RouteNode, JoinMainImmutableTreapMapHolder> helpInfoUpdater = AtomicReferenceFieldUpdater
				.newUpdater(RouteNode.class, JoinMainImmutableTreapMapHolder.class, "helpInfo");

		public boolean compareAndSetLeft(Object expect, Object update) {
			return leftUpdater.compareAndSet(this, expect, update);
		}

		public boolean compareAndSetRight(Object expect, Object update) {
			return rightUpdater.compareAndSet(this, expect, update);
		}

		@SuppressWarnings("rawtypes")
		public boolean compareAndSetHelpInfo(JoinMainImmutableTreapMapHolder expect,
				JoinMainImmutableTreapMapHolder update) {
			return helpInfoUpdater.compareAndSet(this, expect, update);
		}

	}

	private final RouteNode doesNotExistRouteNode = new RouteNode(null, null, null);

	@SuppressWarnings("rawtypes")
	private static final AtomicReferenceFieldUpdater<LockFreeImmTreapCATreeMapSTDR, Object> rootUpdater = AtomicReferenceFieldUpdater
			.newUpdater(LockFreeImmTreapCATreeMapSTDR.class, Object.class, "root");

	// ==== Functions for debuging and testing

	void printDotHelper(Object n, PrintStream writeTo, int level) {
		try {
			if (n instanceof RouteNode) {
				RouteNode node = (RouteNode) n;
				// LEFT
				writeTo.print("\"" + node + level + " \"");
				writeTo.print(" -> ");
				writeTo.print("\"" + node.left + (level + 1) + " \"");
				writeTo.println(";");
				// RIGHT
				writeTo.print("\"" + node + level + " \"");
				writeTo.print(" -> ");
				writeTo.print("\"" + node.right + (level + 1) + " \"");
				writeTo.println(";");

				printDotHelper(node.left, writeTo, level + 1);
				printDotHelper(node.right, writeTo, level + 1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	void printDot(Object node, String fileName) {
		try {
			// lockAll();
			Process p = new ProcessBuilder("dot", "-Tpng")
					.redirectOutput(ProcessBuilder.Redirect.to(new File(fileName + ".png"))).start();
			PrintStream writeTo = new PrintStream(p.getOutputStream());
			writeTo.print("digraph G{\n");
			writeTo.print("  graph [ordering=\"out\"];\n");
			printDotHelper(node, writeTo, 0);
			writeTo.print("}\n");
			writeTo.close();
			p.waitFor();
			// unlockAll();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void printDot(String fileName) {
		printDot(root, fileName);
	}
	// === End of debug functions ==================

	// === Constructors ============================

	public LockFreeImmTreapCATreeMapSTDR() {
		comparator = null;
		root = new NormalLockFreeImmutableTreapMapHolder<K, V>(comparator);
	}

	public LockFreeImmTreapCATreeMapSTDR(Comparator<? super K> comparator) {
		this.comparator = comparator;
		root = new NormalLockFreeImmutableTreapMapHolder<K, V>(comparator);
	}

	private int numberOfRouteNodes(Object currentNode) {
		if (currentNode == null) {
			return 0;
		} else {
			if (currentNode instanceof RouteNode) {
				RouteNode r = (RouteNode) currentNode;
				int sizeSoFar = numberOfRouteNodes(r.left);
				return 1 + sizeSoFar + numberOfRouteNodes(r.right);
			} else {
				return 0;
			}
		}
	}

	/*
	 * public long getTraversedNodes(){ return
	 * threadLocalBuffers.get().getTraversedNodes(); }
	 */
	public int numberOfRouteNodes() {
		// System.err.println("RANGE_QUERY_TRAVERSED_NODES " +
		// threadLocalBuffers.get().getTraversedNodes());
		// System.err.println("NUMBER OF RANGE QUERIES " +
		// threadLocalBuffers.get().getRangeQueries());
		// System.err.println("TRAVERSED NODE PER QUERY " +
		// ((double)threadLocalBuffers.get().getTraversedNodes())/((double)threadLocalBuffers.get().getRangeQueries()));
		return numberOfRouteNodes(root);
	}

	// === Public functions and helper functions ===

	// === Sorted Set Functions ====================

	public boolean isEmpty() {
		return size() == 0;
	}

	public boolean containsKey(Object key) {
		return get(key) != null;
	}

	@SuppressWarnings("unchecked")
	final private LockFreeImmutableTreapMapHolder<K, V> getBaseNodeUsingComparator(Object keyParam) {
		// int nodesTravsered = 0;
		Object currNode = root;
		K key = (K) keyParam;
		while (currNode instanceof RouteNode) {
			// nodesTravsered++;
			RouteNode currNodeR = (RouteNode) currNode;
			K routeKey = (K) (currNodeR.key);
			if (comparator.compare(key, routeKey) < 0) {
				currNode = currNodeR.left;
			} else {
				currNode = currNodeR.right;
			}
		}
		// if(Main.getBaseNodeCalls.get() != null)
		// 	Main.routeNodesTraversed.set(Main.routeNodesTraversed.get() + nodesTravsered);
		return (LockFreeImmutableTreapMapHolder<K, V>)currNode;
	}


	@SuppressWarnings("unchecked")
	final private LockFreeImmutableTreapMapHolder<K, V> getBaseNode(Object keyParam) {
		// int nodesTravsered = 0;	
		// if(Main.getBaseNodeCalls.get() != null)
		// 	Main.getBaseNodeCalls.set(Main.getBaseNodeCalls.get()+1);

		Object currNode = root;
		if (comparator != null) {
			return getBaseNodeUsingComparator(keyParam);
		} else {
			Comparable<? super K> key = (Comparable<? super K>) keyParam;
			while (currNode instanceof RouteNode) {
				// nodesTravsered++;
				RouteNode currNodeR = (RouteNode) currNode;
				K routeKey = (K) (currNodeR.key);
				if (key.compareTo(routeKey) < 0) {
					currNode = currNodeR.left;
				} else {
					currNode = currNodeR.right;
				}
			}
			// if(Main.getBaseNodeCalls.get() != null)
			// 	Main.routeNodesTraversed.set(Main.routeNodesTraversed.get() + nodesTravsered);
			return (LockFreeImmutableTreapMapHolder<K, V>)currNode;
		}
	}

	final private void highContentionSplit(LockFreeImmutableTreapMapHolder<K, V> baseNode) {
		if(!isReplaceable(baseNode)) {
		    //System.err.println("SPLIT FAILURE 2\n");
                  return;
		}
		RouteNode parent = (RouteNode) baseNode.getParent();
		if (baseNode.hasLessThanTwoElements()) {
			tryResetStatistics(baseNode, parent);
			return;
		}
		Object[] writeBackSplitKey = new Object[1];
		@SuppressWarnings("unchecked")
		LockFreeImmutableTreapMapHolder<K, V>[] writeBackRightTree = new LockFreeImmutableTreapMapHolder[1];
		RouteNode newRoute = new RouteNode(null, null, null);
		LockFreeImmutableTreapMapHolder<K, V> leftTree = (LockFreeImmutableTreapMapHolder<K, V>) baseNode
				.split(writeBackSplitKey, writeBackRightTree, newRoute);
		@SuppressWarnings("unchecked")
		K splitKey = (K) writeBackSplitKey[0];
		LockFreeImmutableTreapMapHolder<K, V> rightTree = (LockFreeImmutableTreapMapHolder<K, V>) writeBackRightTree[0];
		newRoute.key = splitKey;
		newRoute.left = leftTree;
		newRoute.right = rightTree;
		boolean splitSuccess = false;
		if (parent == null) {
			splitSuccess = rootUpdater.compareAndSet(this, baseNode, newRoute);
		} else {
			if (parent.left == baseNode) {
				splitSuccess = parent.compareAndSetLeft(baseNode, newRoute);
			} else {
				splitSuccess = parent.compareAndSetRight(baseNode, newRoute);
			}
		}
		if(splitSuccess) {
                     if(PROFILE) threadLocalBuffers.get().increaseNrOfSplits();
                }else{
		    //System.err.println("SPLIT FAILURE\n");
                }
	}

	private void tryResetStatistics(LockFreeImmutableTreapMapHolder<K, V> baseNode, RouteNode parent) {
		LockFreeImmutableTreapMapHolder<K, V> newBase = baseNode.resetStatistics();// Fast path out if nrOfElem <= 1
		if (parent == null) {
			rootUpdater.compareAndSet(this, baseNode, newBase);
		} else {
			if (parent.left == baseNode) {
				parent.compareAndSetLeft(baseNode, newBase);
			} else {
				parent.compareAndSetRight(baseNode, newBase);
			}
		}
	}

	final private LockFreeImmutableTreapMapHolder<K, V> leftmostBaseNode(Object node) {
		Object currentNode = node;
		while (currentNode instanceof RouteNode) {
			RouteNode r = (RouteNode) currentNode;
			currentNode = r.left;
		}
		@SuppressWarnings("unchecked")
		LockFreeImmutableTreapMapHolder<K, V> toReturn = (LockFreeImmutableTreapMapHolder<K, V>) currentNode;
		return toReturn;
	}

	final private LockFreeImmutableTreapMapHolder<K, V> rightmostBaseNode(Object node) {
		Object currentNode = node;
		while (currentNode instanceof RouteNode) {
			RouteNode r = (RouteNode) currentNode;
			currentNode = r.right;
		}
		@SuppressWarnings("unchecked")
		LockFreeImmutableTreapMapHolder<K, V> toReturn = (LockFreeImmutableTreapMapHolder<K, V>) currentNode;
		return toReturn;
	}

	final private RouteNode parentOfUsingComparator(RouteNode node) {
		@SuppressWarnings("unchecked")
		K key = (K) node.key;
		Object prevNode = null;
		Object currNode = root;

		while (currNode != node && currNode instanceof RouteNode) {
			RouteNode currNodeR = (RouteNode) currNode;
			@SuppressWarnings("unchecked")
			K routeKey = (K) (currNodeR.key);
			prevNode = currNode;
			if (comparator.compare(key, routeKey) < 0) {
				currNode = currNodeR.left;
			} else {
				currNode = currNodeR.right;
			}
		}
		if (!(currNode instanceof RouteNode)) {
			return doesNotExistRouteNode;
		}
		return (RouteNode) prevNode;

	}

	final private RouteNode parentOf(RouteNode node) {
		if (comparator != null) {
			return parentOfUsingComparator(node);
		} else {
			@SuppressWarnings("unchecked")
			Comparable<? super K> key = (Comparable<? super K>) node.key;
			Object prevNode = null;
			Object currNode = root;
			while (currNode != node && currNode instanceof RouteNode) {
				RouteNode currNodeR = (RouteNode) currNode;
				@SuppressWarnings("unchecked")
				K routeKey = (K) (currNodeR.key);
				prevNode = currNode;
				if (key.compareTo(routeKey) < 0) {
					currNode = currNodeR.left;
				} else {
					currNode = currNodeR.right;
				}
			}
			if (!(currNode instanceof RouteNode)) {
				return doesNotExistRouteNode;
			}
			return (RouteNode) prevNode;
		}
	}

	final private void lowContentionJoin(LockFreeImmutableTreapMapHolder<K, V> baseNode) {
		// System.out.println("JOIN");
		if(!isReplaceable(baseNode)) {
			return;
		}
		RouteNode parent = (RouteNode) baseNode.getParent();
		if (parent == null) {
			return;
		} else if (parent.left == baseNode) {
			LockFreeImmutableTreapMapHolder<K, V> neighborBase = leftmostBaseNode(parent.right);
			if (isReplaceable(neighborBase)) {
				JoinMainImmutableTreapMapHolder<K, V> newTempBase = 
						new JoinMainImmutableTreapMapHolder<K, V>(baseNode);
				if (parent.compareAndSetLeft(baseNode, newTempBase)) {
					RouteNode neighborBaseParent = (RouteNode) neighborBase.getParent();
					JoinNeighborImmutableTreapMapHolder<K, V> newTempNeighborBase =
							new JoinNeighborImmutableTreapMapHolder<K, V>(
							neighborBase, newTempBase); 
					boolean successLinkInNewNeighbor = false;
					if(neighborBaseParent == parent) {
						successLinkInNewNeighbor =
								neighborBaseParent.compareAndSetRight(neighborBase, newTempNeighborBase);
					}else {
						successLinkInNewNeighbor =
								neighborBaseParent.compareAndSetLeft(neighborBase, newTempNeighborBase);
					}
					if (successLinkInNewNeighbor) {
						if (lockParentAndGrandParent(newTempBase, newTempNeighborBase)) {
							completeJoin(newTempBase);
							if(PROFILE) threadLocalBuffers.get().increaseNrOfJoins();							
						}
					}
				}
			}
		} else if (parent.right == baseNode) {
			LockFreeImmutableTreapMapHolder<K, V> neighborBase = rightmostBaseNode(parent.left);//ff
			if (isReplaceable(neighborBase)) {//ff
				JoinMainImmutableTreapMapHolder<K, V> newTempBase =//ff 
						new JoinMainImmutableTreapMapHolder<K, V>(baseNode);//ff
				if (parent.compareAndSetRight(baseNode, newTempBase)) {//ff
					RouteNode neighborBaseParent = (RouteNode) neighborBase.getParent();//ff
					JoinNeighborImmutableTreapMapHolder<K, V> newTempNeighborBase =//ff
							new JoinNeighborImmutableTreapMapHolder<K, V>(//ff
							neighborBase, newTempBase); //ff
					boolean successLinkInNewNeighbor = false;//ff
					if(neighborBaseParent == parent) {//ff
						successLinkInNewNeighbor =//ff
								neighborBaseParent.compareAndSetLeft(neighborBase, newTempNeighborBase);//ff
					}else {//ff
						successLinkInNewNeighbor =//ff
								neighborBaseParent.compareAndSetRight(neighborBase, newTempNeighborBase);//ff
					}//ff
					if (successLinkInNewNeighbor) {//ff
						if (lockParentAndGrandParent(newTempBase, newTempNeighborBase)) {//ff
							completeJoin(newTempBase);//ff
							if(PROFILE)threadLocalBuffers.get().increaseNrOfJoins();//ff
						}//ff
					}//ff
				}
			}
		}

	}

	private boolean isReplaceable(LockFreeImmutableTreapMapHolder<K, V> baseNode) {
		return baseNode instanceof NormalLockFreeImmutableTreapMapHolder || 
			  (baseNode instanceof CollectRangeQueryImmutableTreapMapHolder && 
						!((CollectRangeQueryImmutableTreapMapHolder<K,V>)baseNode).isValid()); /*||
			  /*
			   * Need to think about if this could work
			   * (baseNode instanceof JoinMainImmutableTreapMapHolder && 
						((JoinMainImmutableTreapMapHolder<K,V>)baseNode).isKilled()) ||
			  (baseNode instanceof JoinNeighborImmutableTreapMapHolder && 
						((JoinNeighborImmutableTreapMapHolder<K,V>)baseNode).getMainBase().isKilled())*/
	}

	private boolean lockParentAndGrandParent(JoinMainImmutableTreapMapHolder<K, V> baseNode,
			JoinNeighborImmutableTreapMapHolder<K, V> neighborBase) {
		if (baseNode.isKilled()) {
			return false;
		} else {
			RouteNode parent = (RouteNode) baseNode.getParent();
			if (!parent.compareAndSetHelpInfo(null, baseNode)) {
				baseNode.kill();
				return false;
			} else if (baseNode.isKilled()) {
				parent.compareAndSetHelpInfo(baseNode, null);
				return false;
			} else {
				//System.out.println("SET parent help " + baseNode);
				RouteNode gparent = null;
				gparent = parentOf(parent);
				if (gparent == doesNotExistRouteNode) {
					baseNode.kill();
					parent.compareAndSetHelpInfo(baseNode, null);
					return false;
				} else if (gparent == null) {
					if (baseNode.makeUnkillable(neighborBase, gparent,
							parent.left == baseNode ? parent.right : parent.left)) {
						return true;
					} else {
						//baseNode.tryKill();
						parent.compareAndSetHelpInfo(baseNode, null);
						return false;
					}
				} else {
					if (!gparent.compareAndSetHelpInfo(null, baseNode)) {
						baseNode.kill();
						parent.compareAndSetHelpInfo(baseNode, null);
						return false;
					} else if (gparent.valid == false) {
						baseNode.kill();
						parent.compareAndSetHelpInfo(baseNode, null);
						gparent.compareAndSetHelpInfo(baseNode, null);
						return false;
					} else if (baseNode.makeUnkillable(neighborBase, gparent,
							parent.left == baseNode ? parent.right : parent.left)) { //
						return true;
					} else {
						//baseNode.tryKill();
						parent.compareAndSetHelpInfo(baseNode, null);
						gparent.compareAndSetHelpInfo(baseNode, null);
						return false;
					}
				}
			}
		}

	}

	private void completeJoin(JoinMainImmutableTreapMapHolder<K, V> baseNode) {
		JoinNeighborImmutableTreapMapHolder<K, V> neighborBase = baseNode.getSecondNeighborBase();
		if (neighborBase == null) {
			// System.out.println("What is this: " + baseNode.getFistNeighborBase());
			JoinNeighborImmutableTreapMapHolder<K, V> newNeighborBase = new JoinNeighborImmutableTreapMapHolder<K, V>(
					baseNode.getFistNeighborBase(), baseNode);
			neighborBase = baseNode.trySetSecondNeighborBase(newNeighborBase);
		}
		// System.out.println("NEIGHBOR BASE " + neighborBase);
		JoinNeighborImmutableTreapMapHolder<K, V> firstNeighborBase = baseNode.getFistNeighborBase();
		RouteNode firstNeighborBaseParent = (RouteNode) firstNeighborBase.getParent();
		while (true) {
			if (firstNeighborBaseParent.left == neighborBase) {
				break;
			} else if (firstNeighborBaseParent.right == neighborBase) {
				break;
			} else if (firstNeighborBaseParent.left == firstNeighborBase) {
				if (firstNeighborBaseParent.compareAndSetLeft(firstNeighborBase, neighborBase)) {
					break;
				}
			} else if (firstNeighborBaseParent.right == firstNeighborBase) {
				if (firstNeighborBaseParent.compareAndSetRight(firstNeighborBase, neighborBase)) {
					break;
				}
			} else {
				return;
			}
		}
		// System.out.println("DONE SETTING SECOND");
		RouteNode parent = (RouteNode) baseNode.getParent();
		parent.valid = false;
		RouteNode gparent = (RouteNode) baseNode.getGrandParent();
		if (baseNode.getGrandParent() == null) {
			if (baseNode.getParentOtherBrach() == baseNode.getFistNeighborBase()) {
				NormalLockFreeImmutableTreapMapHolder<K, V> newNeighborBase = neighborBase.setParent(null);
				rootUpdater.compareAndSet(this, parent, newNeighborBase);//wrong parent (Fixed)!
			} else {
				rootUpdater.compareAndSet(this, parent, baseNode.getParentOtherBrach());
			}
			parent.compareAndSetHelpInfo(baseNode, null);
			baseNode.kill();
			return;
		} else if (gparent.right == parent) {
			if (baseNode.getParentOtherBrach() == baseNode.getFistNeighborBase()) {
				//System.out.println("WRONG PARENT 1!");
				NormalLockFreeImmutableTreapMapHolder<K, V> newNeighborBase = 
						neighborBase.setParent(gparent);
				gparent.compareAndSetRight(parent, newNeighborBase);// wrong parent! (Fixed!)
			} else {
				gparent.compareAndSetRight(parent, baseNode.getParentOtherBrach());
			}
			parent.compareAndSetHelpInfo(baseNode, null);
			gparent.compareAndSetHelpInfo(baseNode, null);
			baseNode.kill();
			return;
		} else if (gparent.left == parent) {
			if (baseNode.getParentOtherBrach() == baseNode.getFistNeighborBase()) {
			//	System.out.println("WRONG PARENT 2!");
				NormalLockFreeImmutableTreapMapHolder<K, V> newNeighborBase = 
						neighborBase.setParent(gparent);
				gparent.compareAndSetLeft(parent, newNeighborBase);// wrong parent
			} else {
				gparent.compareAndSetLeft(parent, baseNode.getParentOtherBrach());
			}
			parent.compareAndSetHelpInfo(baseNode, null);
			gparent.compareAndSetHelpInfo(baseNode, null);
			baseNode.kill();
			return;
		} else {
			baseNode.kill();
		}

	}

	private final void adaptIfNeeded(LockFreeImmutableTreapMapHolder<K, V> baseNode) {
		if (baseNode.isHighContentionLimitReached()) {
			highContentionSplit(baseNode);
		} else if (baseNode.isLowContentionLimitReached()) {
			lowContentionJoin(baseNode);
		}
	}

	@SuppressWarnings("unchecked")
	public V get(Object key) {
		LockFreeImmutableTreapMapHolder<K, V> baseNode = (LockFreeImmutableTreapMapHolder<K, V>) getBaseNode(key);
		ImmutableTreapValue<K, V> root = baseNode.getRoot();
		return ImmutableTreapMap.get(root, (K) key, comparator);
	}

	/*
	 * Return if contended
	 */
	public boolean helpIfNeeded(Object baseNode) {
		if (baseNode instanceof CollectAllLockFreeImmutableTreapMapHolder) {
			@SuppressWarnings("unchecked")
			CollectAllLockFreeImmutableTreapMapHolder<K, V> base = (CollectAllLockFreeImmutableTreapMapHolder<K, V>) baseNode;
			if (base.isValid()) {
				getAll(base.getResultStorage());
				return true;
			} else {
				baseNode = tryNormalize(base);
				if (baseNode == null) {
					return true;
				}
			}
		} else if (baseNode instanceof CollectRangeQueryImmutableTreapMapHolder) {
			@SuppressWarnings("unchecked")
			CollectRangeQueryImmutableTreapMapHolder<K, V> base = (CollectRangeQueryImmutableTreapMapHolder<K, V>) baseNode;
			if (base.isValid()) {
				getAllInRange(base.getLo(), base.getHi(), base.getResultStorage(), false);
				return true;
			} else {
				//baseNode = tryNormalize(base);
				//if (baseNode == null) {
				//	return true;
				//}
				return true;
			}
		} else if (baseNode instanceof JoinNeighborImmutableTreapMapHolder) {
			@SuppressWarnings("unchecked")
			JoinNeighborImmutableTreapMapHolder<K, V> base = (JoinNeighborImmutableTreapMapHolder<K, V>) baseNode;
			if (base.getMainBase().tryKill()) {
				NormalLockFreeImmutableTreapMapHolder<K, V> newBase = base.normalize();
				RouteNode parent = (RouteNode) base.getParent();
				if (parent.left == base) {
					parent.compareAndSetLeft(baseNode, newBase);
					return false;
				} else if (parent.right == base) {
					parent.compareAndSetRight(baseNode, newBase);
					return false;
				} else {
					return false;
				}
			} else {
				completeJoin(base.getMainBase());
				return false;
			}
		} else if (baseNode instanceof JoinMainImmutableTreapMapHolder) {
			@SuppressWarnings("unchecked")
			JoinMainImmutableTreapMapHolder<K, V> base = (JoinMainImmutableTreapMapHolder<K, V>) baseNode;
			if (base.tryKill()) {
				NormalLockFreeImmutableTreapMapHolder<K, V> newBase = base.normalize();
				RouteNode parent = (RouteNode) base.getParent();
				if (parent.left == base) {
					parent.compareAndSetLeft(baseNode, newBase);
					return false;
				} else if (parent.right == base) {
					parent.compareAndSetRight(baseNode, newBase);
					return false;
				} else {
					return false;
				}
			} else {
				completeJoin(base);
				return false;
			}
		}
		return false;

	}

	private boolean tryDoReplace(LockFreeImmutableTreapMapHolder<K, V> base, RouteNode parent,
			LockFreeImmutableTreapMapHolder<K, V> newBase) {
		boolean success = true;
		if (parent == null) {
			if (!rootUpdater.compareAndSet(this, base, newBase)) {
				success = false;
			}
			// Update success
		} else if (parent.left == base) {
			if (!parent.compareAndSetLeft(base, newBase)) {
				success = false;
			}
			// Update success
		} else if (parent.right == base) {
			if (!parent.compareAndSetRight(base, newBase)) {
				success = false;
			}
			// Update success
		} else {
			success = false;
		}
		return success;
	}

	@SuppressWarnings("unchecked")
	public V put(K key, V value) {
		boolean addContention = false;
		while (true) {
			LockFreeImmutableTreapMapHolder<K, V> base = getBaseNode(key);
			if (isReplaceable(base)) {
				RouteNode parent = (RouteNode) base.getParent();
				NormalLockFreeImmutableTreapMapHolder<K, V> newBase = base.put(key, value, parent, addContention);
				if (tryDoReplace(base, parent, newBase)) {
					adaptIfNeeded(newBase);
					return ((V) ImmutableTreapMap.getPrevValue());
				} else {
					addContention = true;
					continue;
				}
			}
			if (helpIfNeeded(base)) {
				addContention = true;
			}
		}
	}

	@SuppressWarnings("unchecked")
	public V putIfAbsent(K key, V value) {
		boolean addContention = false;
		while (true) {
			LockFreeImmutableTreapMapHolder<K, V> base = getBaseNode(key);
			if (isReplaceable(base)) {
				RouteNode parent = (RouteNode) base.getParent();
				NormalLockFreeImmutableTreapMapHolder<K, V> newBase = base.putIfAbsent(key, value, parent,
						addContention);
				if(newBase == null) {
					return ((V) ImmutableTreapMap.getPrevValue());
				}else if (tryDoReplace(base, parent, newBase)) {
					adaptIfNeeded(newBase);
					return ((V) ImmutableTreapMap.getPrevValue());
				} else {
					addContention = true;
					continue;
				}
			}
			if (helpIfNeeded(base)) {
				addContention = true;
			}
		}
	}

	@SuppressWarnings("unchecked")
	public V remove(Object key) {
		boolean addContention = false;
		while (true) {
			LockFreeImmutableTreapMapHolder<K, V> base = getBaseNode(key);
			if (isReplaceable(base)) {
				RouteNode parent = (RouteNode) base.getParent();
				NormalLockFreeImmutableTreapMapHolder<K, V> newBase = base.remove(key, parent, addContention);
				if(newBase == null) {
					return ((V) ImmutableTreapMap.getPrevValue());
				}else if (tryDoReplace(base, parent, newBase)) {
					adaptIfNeeded(newBase);
					return ((V) ImmutableTreapMap.getPrevValue());
				} else {
					addContention = true;
					continue;
				}
			}
			if(base.get(key) == null) {
				return null;
			}
			if (helpIfNeeded(base)) {
				addContention = true;
			}
		}
	}

	public void clear() {
		throw new RuntimeException("Not yet implemented");
		// root = new NormalLockFreeImmutableTreapMapHolder<K,V>(comparator, 0,
		// ImmutableTreapMap.createEmpty(), null);
	}

	public Set<Map.Entry<K, V>> entrySet() {
		LinkedList<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>();
		ImmutableTreapMap.traverseAllItems(getAll(), (k, v) -> list.add(new SimpleImmutableEntry<K, V>(k, v)));
		return new HashSet<Map.Entry<K, V>>(list);
	}

	final private class Counter<L, U> implements BiConsumer<K, V> {
		int count = 0;

		@Override
		public void accept(K t, V u) {
			count++;
		}
	}

	public int size() {
		Counter<K, V> consume = new Counter<K, V>();
		ImmutableTreapMap.traverseAllItems(getAll(), consume);
		return consume.count;
	}

	final private class Aggregator<L, U> implements BiConsumer<K, V> {
		long sum = 0;

		@Override
		public void accept(K t, V u) {
			sum += ((Integer) t).intValue();
		}
	}

	public long getSumOfKeys() {
		Aggregator<K, V> consume = new Aggregator<K, V>();
		ImmutableTreapMap.traverseAllItems(getAll(), consume);
		return consume.sum;
	}

	final private Object getBaseNodeAndStackUsingComparator(Object keyParam, Stack<RouteNode> stack) {
		Object currNode = root;
		@SuppressWarnings("unchecked")
		K key = (K) keyParam;
		while (currNode instanceof RouteNode) {
			RouteNode currNodeR = (RouteNode) currNode;
			stack.push(currNodeR);
			@SuppressWarnings("unchecked")
			K routeKey = (K) (currNodeR.key);
			if (comparator.compare(key, routeKey) < 0) {
				currNode = currNodeR.left;
			} else {
				currNode = currNodeR.right;
			}
		}
		return currNode;
	}

	@SuppressWarnings("unchecked")
	final private LockFreeImmutableTreapMapHolder<K, V> getBaseNodeAndStack(Object keyParam, Stack<RouteNode> stack) {
		Object currNode = root;
		if (comparator != null) {
			return (LockFreeImmutableTreapMapHolder<K, V>) getBaseNodeAndStackUsingComparator(keyParam, stack);
		} else {
			Comparable<? super K> key = (Comparable<? super K>) keyParam;
			while (currNode instanceof RouteNode) {
				RouteNode currNodeR = (RouteNode) currNode;
				stack.push(currNodeR);
				K routeKey = (K) (currNodeR.key);
				if (key.compareTo(routeKey) < 0) {
					currNode = currNodeR.left;
				} else {
					currNode = currNodeR.right;
				}
			}
			return (LockFreeImmutableTreapMapHolder<K, V>) currNode;
		}
	}

	@SuppressWarnings("unchecked")
	private LockFreeImmutableTreapMapHolder<K, V> getFirstBaseNodeAndStack(Stack<RouteNode> stack) {
		Object currNode = root;
		while (currNode instanceof RouteNode) {
			RouteNode currNodeR = (RouteNode) currNode;
			stack.push(currNodeR);
			currNode = currNodeR.left;
		}
		return (LockFreeImmutableTreapMapHolder<K, V>) currNode;
	}

	final private LockFreeImmutableTreapMapHolder<K, V> leftmostBaseNodeAndStack(Object node, Stack<RouteNode> stack) {
		Object currentNode = node;
		while (currentNode instanceof RouteNode) {
			RouteNode r = (RouteNode) currentNode;
			stack.push(r);
			currentNode = r.left;
		}
		@SuppressWarnings("unchecked")
		LockFreeImmutableTreapMapHolder<K, V> toReturn = (LockFreeImmutableTreapMapHolder<K, V>) currentNode;
		return toReturn;
	}

	@SuppressWarnings("unchecked")
	final private LockFreeImmutableTreapMapHolder<K, V> getNextBaseNodeAndStack(Object baseNode,
			Stack<RouteNode> stack) {
		RouteNode top = stack.top();
		if (top == null) {
			return null;
		}
		if (top.left == baseNode) {
			return leftmostBaseNodeAndStack(top.right, stack);
		}
		K keyToBeGreaterThan = (K) top.key;
		while (top != null) {
			if (top.valid && lessThan(keyToBeGreaterThan, (K) top.key)) {
				return leftmostBaseNodeAndStack(top.right, stack);
			} else {
				stack.pop();
				top = stack.top();
			}
		}
		return null;
	}
	
	private boolean lessThan(K key1, K key2) {
		if (comparator != null) {
			return comparator.compare(key1, key2) < 0;
		} else {
			@SuppressWarnings("unchecked")
			Comparable<? super K> keyComp = (Comparable<? super K>) key1;
			return keyComp.compareTo(key2) < 0;
		}
	}

	static final int RANGE_QUERY_MODE = 1;
	// 0 = write lock directly
	// 1 = read lock directly

	public final Object[] subSet(final K lo, final K hi) {
		Stack<Object> returnStack = threadLocalBuffers.get().getKeyReturnStack();
		subSet(lo, hi, (k) -> returnStack.push(k));
		int returnSize = returnStack.size();
		Object[] returnArray = new Object[returnSize];
		Object[] returnStackArray = returnStack.getStackArray();
		for (int i = 0; i < returnSize; i++) {
			returnArray[i] = returnStackArray[i];
		}
		return returnArray;

	}

	public void subSet(final K lo, final K hi, Consumer<K> consumer) {
		threadLocalBuffers.get().increaseRangeQueries();
		ImmutableTreapValue<K, V> returnValue = optimisticSubSet(lo, hi);
		if (null == returnValue) {
			ImmutableTreapMap.traverseKeysInRange(getAllInRange(lo, hi), lo, hi, consumer, comparator);
		} else {
			ImmutableTreapMap.traverseKeysInRange(returnValue, lo, hi, consumer, comparator);
		}
	}

	@SuppressWarnings("unchecked")
	private ImmutableTreapValue<K, V> optimisticSubSet(K lo, K hi) {
		ThreadLocalBuffers tlbs = threadLocalBuffers.get();
		Object[] array1 = tlbs.getBaseNodeArray1();
		int array1size = optimisticGetBaseNodesInRange(lo, hi, array1);
		if(array1size == 0) {
			return null;
		}else if(array1size == -1){
			LockFreeImmutableTreapMapHolder<K, V> holder = (LockFreeImmutableTreapMapHolder<K, V>)array1[0];
			tlbs.increaseTraversedNodes();
			return holder.getRoot();
		}else {
			Object[] array2 = tlbs.getBaseNodeArray2();
			int array2size = optimisticGetBaseNodesInRange(lo, hi, array2);
			if(array2size == 0) {
				return null;
			}else if(array1size != array2size) {
				return null;
			}else {
				for(int i = 0; i < array1size; i++) {
					if(array1[i] != array2[i]) {
						return null;
					}
				}
				LockFreeImmutableTreapMapHolder<K, V> holder = (LockFreeImmutableTreapMapHolder<K, V>)array1[0];
				ImmutableTreapValue<K, V> root = holder.getRoot();
				for(int i = 1; i < array1size; i++) {
					tlbs.increaseTraversedNodes();
					holder = (LockFreeImmutableTreapMapHolder<K, V>)array1[i];
					root = ImmutableTreapMap.cheapJoin(root, holder.getRoot());
				}
				return root;
			}
		}
	}

	private int optimisticGetBaseNodesInRange(K lo, K hi, Object[] returnArray) {
		ThreadLocalBuffers tlbs = threadLocalBuffers.get();
		Stack<RouteNode> stack = tlbs.getStack();
		LockFreeImmutableTreapMapHolder<K, V> baseNode;
		// Lock all base nodes that might contain keys in the range
		baseNode = getBaseNodeAndStack(lo, stack);
		int returnArrayPos = 0;
		// First base node successfully locked
		while (true) {
			if (baseNode instanceof JoinNeighborImmutableTreapMapHolder ||
				baseNode instanceof JoinMainImmutableTreapMapHolder) {
				return 0; // Fail
			}
			// Add the successfully locked base node to the completed list
			returnArray[returnArrayPos] = baseNode;
			returnArrayPos++;
			// Check if it is the end of our search
			K baseNodeMaxKey = baseNode.maxKey();
			if (baseNodeMaxKey != null && lessThan(hi, baseNodeMaxKey)) {
				//System.out.println("RET1");
				if(returnArrayPos == 1) {
					return -1;
				}else {
				    return returnArrayPos; // We have locked all base nodes that we need!
				}
			}
			// There might be more base nodes in the range, continue
			baseNode = getNextBaseNodeAndStack(baseNode, stack);
			if (baseNode == null) {
				//System.out.println("RET2");
				return returnArrayPos;
			}else if(returnArrayPos == MAX_BASE_NODES_OPTIMISTIC_RANGE_QUERY) {
				return 0;
			}
		}
	}
	
	public void subSetForce(final K lo, final K hi, Consumer<K> consumer) {
		ImmutableTreapMap.traverseKeysInRange(getAllInRange(lo, hi), lo, hi, consumer, comparator);
	}

	/**/
	private final class ThreadLocalBuffers {
		

		public Stack<RouteNode> getStack() {
			stack.resetStack();
			return stack;
		}

		public Object[] getBaseNodeArray2() {
			return baseNodeArray2;
		}

		public Object[] getBaseNodeArray1() {

			return baseNodeArray1;
		}

		long[] statistics = new long[35];

		public void increaseTraversedNodes() {
			statistics[16]++;
		}

		public void increaseRangeQueries() {
			statistics[17]++;
		}
		
		public void increaseNrOfJoins() {
			statistics[18]++;
		}
		
		public void increaseNrOfSplits() {
			statistics[19]++;
		}


		public long getTraversedNodes() {
			return statistics[16];
		}

		@SuppressWarnings("unused")
		public long getRangeQueries() {
			return statistics[17];
		}
		
		@SuppressWarnings("unused")
		public long getNrOfJoins() {
			return statistics[18];
		}
		
		@SuppressWarnings("unused")
		public long getNrOfSplits() {
			return statistics[19];
		}

		public Stack<RouteNode> getNextStack() {
			nextStack.resetStack();
			return nextStack;
		}

		public Stack<LockFreeImmutableTreapMapHolder<K, V>> getLockedBaseNodesStack() {
			lockedBaseNodesStack.resetStack();
			return lockedBaseNodesStack;
		}

		@SuppressWarnings("unused")
		public Stack<STDAVLNode<K, V>> getTraverseStack() {
			traverseStack.resetStack();
			return traverseStack;
		}

		public Stack<Object> getKeyReturnStack() {
			keyReturnStack.resetStack();
			return keyReturnStack;
		}

		@SuppressWarnings("unused")
		public Stack<ImmutableTreapMap.ImmutableTreapValue<K, V>> getReturnStack() {
			returnStack.resetStack();
			return returnStack;
		}

		@SuppressWarnings("unused")
		public LongStack getReadTokenStack() {
			readTokenStack.resetStack();
			return readTokenStack;
		}

		@SuppressWarnings("unused")
		public Stack<Object> getReturnStack2() {
			returnStack2.resetStack();
			return returnStack2;
		}

		@SuppressWarnings("unused")
		public void setReturnStack2(Stack<Object> returnStack2) {
			this.returnStack2 = returnStack2;
		}

		@SuppressWarnings("unused")
		public Stack<K> getOptimisticReturnStack() {
			optimisticReturnStack.resetStack();
			return optimisticReturnStack;
		}

		@SuppressWarnings("unused")
		public void setOptimisticReturnStack(Stack<K> optimisticReturnStack) {
			this.optimisticReturnStack = optimisticReturnStack;
		}

		private Object[] baseNodeArray1 = new Object[MAX_BASE_NODES_OPTIMISTIC_RANGE_QUERY];
		private Object[] baseNodeArray2 = new Object[MAX_BASE_NODES_OPTIMISTIC_RANGE_QUERY];
		private Stack<RouteNode> stack = new Stack<RouteNode>();
		private Stack<RouteNode> nextStack = new Stack<RouteNode>();
		private Stack<LockFreeImmutableTreapMapHolder<K, V>> lockedBaseNodesStack = new Stack<LockFreeImmutableTreapMapHolder<K, V>>();
		private Stack<ImmutableTreapMap.ImmutableTreapValue<K, V>> returnStack = new Stack<ImmutableTreapMap.ImmutableTreapValue<K, V>>(
				16);
		private Stack<Object> keyReturnStack = new Stack<Object>(16);

		private Stack<STDAVLNode<K, V>> traverseStack = new Stack<STDAVLNode<K, V>>();
		private Stack<Object> returnStack2 = new Stack<Object>(16);
		private Stack<K> optimisticReturnStack = new Stack<K>(16);

		private LongStack readTokenStack = new LongStack();
	}

	private ThreadLocal<ThreadLocalBuffers> threadLocalBuffers = new ThreadLocal<ThreadLocalBuffers>() {

		@Override
		protected LockFreeImmTreapCATreeMapSTDR<K, V>.ThreadLocalBuffers initialValue() {
			return new ThreadLocalBuffers();
		}

	};

	public final ImmutableTreapValue<K, V> getAll() {
		return getAll(null);
	}

	@SuppressWarnings("unchecked")
	private final ImmutableTreapValue<K, V> getAll(AtomicStampedReference<ImmutableTreapValue<K, V>> onlyHelpThis) {
		ThreadLocalBuffers tlbs = threadLocalBuffers.get();
		Stack<RouteNode> stack;
		Stack<RouteNode> nextStack;
		Stack<LockFreeImmutableTreapMapHolder<K, V>> lockedBaseNodesStack;
		if(onlyHelpThis == null) {
			stack = tlbs.getStack();
			nextStack = tlbs.getNextStack();
			lockedBaseNodesStack = tlbs.getLockedBaseNodesStack();
		}else {
			stack = new Stack<>();
			nextStack = new Stack<>();
			lockedBaseNodesStack = new Stack<>();
		}
		LockFreeImmutableTreapMapHolder<K, V> baseNode;
		boolean tryAgain;
		// Lock all base nodes that might contain keys in the range
		AtomicStampedReference<ImmutableTreapValue<K, V>> storage = null;
		do {
			tryAgain = false;
			baseNode = getFirstBaseNodeAndStack(stack);
			if (baseNode instanceof CollectAllLockFreeImmutableTreapMapHolder) {
				CollectAllLockFreeImmutableTreapMapHolder<K, V> base = (CollectAllLockFreeImmutableTreapMapHolder<K, V>) baseNode;
				if (base.isValid()) {
					ImmutableTreapValue<K, V> res = base.getResult();
					if (res != null) {
						return res;
					} else {
						storage = base.getResultStorage();
						if (onlyHelpThis != null && storage != onlyHelpThis) {
							return null;
						}
					}
				} else {
					if (onlyHelpThis != null) {
						return null;
					}
					tryNormalize(base);
					tryAgain = true;
				}
			} else if (baseNode instanceof CollectRangeQueryImmutableTreapMapHolder) {
				CollectRangeQueryImmutableTreapMapHolder<K, V> base = (CollectRangeQueryImmutableTreapMapHolder<K, V>) baseNode;
				if (base.isValid()) {
					getAllInRange(base.getLo(), base.getHi(), base.getResultStorage(), true);
					tryAgain = true;
				} else {
					baseNode = tryNormalize(base);
					tryAgain = true;
				}
			} else if (baseNode instanceof NormalLockFreeImmutableTreapMapHolder) {
				if (onlyHelpThis != null) {
					return null;
				}
				NormalLockFreeImmutableTreapMapHolder<K, V> base = (NormalLockFreeImmutableTreapMapHolder<K, V>) baseNode;
				storage = new AtomicStampedReference<ImmutableTreapMap.ImmutableTreapValue<K, V>>(null, 0);
				CollectAllLockFreeImmutableTreapMapHolder<K, V> newBase = new CollectAllLockFreeImmutableTreapMapHolder<K, V>(
						base, storage);
				tryAgain = tryReplace(base, newBase);
				if (!tryAgain) {
					baseNode = newBase;
				}
			} else if (baseNode instanceof JoinNeighborImmutableTreapMapHolder) {
				// System.out.println("RANGE JOIN NEI");
				JoinNeighborImmutableTreapMapHolder<K, V> base = (JoinNeighborImmutableTreapMapHolder<K, V>) baseNode;
				if (base.getMainBase().tryKill()) {
					baseNode = tryNormalize(base);
					tryAgain = true;
				} else {
					completeJoin(base.getMainBase());
					tryAgain = true;
				}
			} else if (baseNode instanceof JoinMainImmutableTreapMapHolder) {
				// System.out.println("RANGE JOIN MAIN");
				JoinMainImmutableTreapMapHolder<K, V> base = (JoinMainImmutableTreapMapHolder<K, V>) baseNode;
				if (base.tryKill()) {
					baseNode = tryNormalize(base);
					tryAgain = true;
				} else {
					completeJoin(base);
					tryAgain = true;
				}
			}
			if (tryAgain) {
				stack.resetStack();
			}
		} while (tryAgain);
		// First base node successfully locked
		outer: while (true) {
			tryAgain = false;
			// Add the successfully locked base node to the completed list
			lockedBaseNodesStack.push(baseNode);
			// There might be more base nodes in the range, continue
			LockFreeImmutableTreapMapHolder<K, V> lastLockedBaseNode = baseNode;
			nextStack.copyStateFrom(stack); // Save the current position so we
											// can try again
			do {
				baseNode = getNextBaseNodeAndStack(lastLockedBaseNode, stack);
				if (baseNode == null) {
					break outer;// The last base node is locked
				}

				if (baseNode instanceof CollectAllLockFreeImmutableTreapMapHolder) {
					CollectAllLockFreeImmutableTreapMapHolder<K, V> base = (CollectAllLockFreeImmutableTreapMapHolder<K, V>) baseNode;
					if (base.getResultStorage() != storage) {
						tryNormalize(base);
						tryAgain = true;
					}
				} else if (baseNode instanceof CollectRangeQueryImmutableTreapMapHolder) {
					CollectRangeQueryImmutableTreapMapHolder<K, V> base = (CollectRangeQueryImmutableTreapMapHolder<K, V>) baseNode;
					if (base.isValid()) {
						getAllInRange(base.getLo(), base.getHi(), base.getResultStorage(), true);
						tryAgain = true;
					} else {
						baseNode = tryNormalize(base);
						tryAgain = true;
					}
				} else if (baseNode instanceof NormalLockFreeImmutableTreapMapHolder) {
					NormalLockFreeImmutableTreapMapHolder<K, V> base = (NormalLockFreeImmutableTreapMapHolder<K, V>) baseNode;
					CollectAllLockFreeImmutableTreapMapHolder<K, V> newBase = new CollectAllLockFreeImmutableTreapMapHolder<K, V>(
							base, storage);
					tryAgain = tryReplace(base, newBase);
					if (!tryAgain) {
						baseNode = newBase;
					}
				} else if (baseNode instanceof JoinNeighborImmutableTreapMapHolder) {
					JoinNeighborImmutableTreapMapHolder<K, V> base = (JoinNeighborImmutableTreapMapHolder<K, V>) baseNode;
					if (base.getMainBase().tryKill()) {
						baseNode = tryNormalize(base);
						tryAgain = true;
					} else {
						completeJoin(base.getMainBase());
						tryAgain = true;
					}
				} else if (baseNode instanceof JoinMainImmutableTreapMapHolder) {
					JoinMainImmutableTreapMapHolder<K, V> base = (JoinMainImmutableTreapMapHolder<K, V>) baseNode;
					if (base.tryKill()) {
						baseNode = tryNormalize(base);
						tryAgain = true;
					} else {
						completeJoin(base);
						tryAgain = true;
					}
				}

				if (storage.getReference() != null) {
					return storage.getReference();
				}

				if (tryAgain) {
					// unlockBaseNode(mode, baseNode);
					// Reset stack
					stack.copyStateFrom(nextStack);
				}
			} while (tryAgain);
		}
		// We have successfully locked all the base nodes that we need
		// Time to construct the results from the contents of the base nodes
		// The linearization point is just before the first lock is unlocked
		// Stack<ImmutableTreapValue<K, V>> returnStack = tlbs.getReturnStack();
		ImmutableTreapValue<K, V> root = null;
		Object[] lockedBaseNodeArray = lockedBaseNodesStack.getStackArray();
		if (lockedBaseNodesStack.size() == 1) {
			threadLocalBuffers.get().increaseTraversedNodes();
			CollectAllLockFreeImmutableTreapMapHolder<K, V> map = (CollectAllLockFreeImmutableTreapMapHolder<K, V>) (lockedBaseNodeArray[0]);
			// returnStack.push(map.getRoot());
			root = map.getRoot();
			storage.compareAndSet(null, root, 0, 1);
		} else {
			root = ImmutableTreapMap.createEmpty();
			for (int i = 0; i < lockedBaseNodesStack.size(); i++) {
				threadLocalBuffers.get().increaseTraversedNodes();
				CollectAllLockFreeImmutableTreapMapHolder<K, V> map = (CollectAllLockFreeImmutableTreapMapHolder<K, V>) (lockedBaseNodeArray[i]);
				// returnStack.push(map.getRoot());
				root = ImmutableTreapMap.cheapJoin(root, map.getRoot());
			}
			storage.compareAndSet(null, root, 0, 2);
		}
		return storage.getReference();
	}

	private ImmutableTreapValue<K, V> getAllInRange(K lo, K hi) {
		return getAllInRange(lo, hi, null, false);
	}

	@SuppressWarnings("unchecked")
	private final ImmutableTreapValue<K, V> getAllInRange(K lo, K hi,
			AtomicStampedReference<ImmutableTreapValue<K, V>> onlyHelpThis, boolean recursive) {
		ThreadLocalBuffers tlbs = threadLocalBuffers.get();
		Stack<RouteNode> stack;
		Stack<RouteNode> nextStack;
		Stack<LockFreeImmutableTreapMapHolder<K, V>> lockedBaseNodesStack;
		if(!recursive) {
			stack = tlbs.getStack();
			nextStack = tlbs.getNextStack();
			lockedBaseNodesStack = tlbs.getLockedBaseNodesStack();
		}else {
			stack = new Stack<>();
			nextStack = new Stack<>();
			lockedBaseNodesStack = new Stack<>();
		}
		LockFreeImmutableTreapMapHolder<K, V> baseNode;
		boolean tryAgain;
		// Lock all base nodes that might contain keys in the range
		AtomicStampedReference<ImmutableTreapValue<K, V>> storage = null;
		K actualLo = null;
		K actualHi = null;
		do {
			tryAgain = false;
			baseNode = getBaseNodeAndStack(lo, stack);
			if (isReplaceable(baseNode)) {
				if (onlyHelpThis != null) {
					return null;
				}
				LockFreeImmutableTreapMapHolder<K, V> base = baseNode;
				storage = new AtomicStampedReference<ImmutableTreapMap.ImmutableTreapValue<K, V>>(null, 0);
				CollectRangeQueryImmutableTreapMapHolder<K, V> newBase = new CollectRangeQueryImmutableTreapMapHolder<K, V>(
						lo, hi, base, storage);
				tryAgain = tryReplace(base, newBase);
				if (!tryAgain) {
					actualLo = lo;
					actualHi = hi;
					baseNode = newBase;
				}
			} else if (baseNode instanceof CollectRangeQueryImmutableTreapMapHolder) {
				CollectRangeQueryImmutableTreapMapHolder<K, V> base = (CollectRangeQueryImmutableTreapMapHolder<K, V>) baseNode;
				if (base.isValid()) {
					if (compare(hi, base.getHi()) <= 0) {
						// We can use the current one
						ImmutableTreapValue<K, V> res = base.getResult();
						if (res != null) {
							return res;
						} else {
							storage = base.getResultStorage();
							if (onlyHelpThis != null && storage != onlyHelpThis) {
								return null;
							}
							actualLo = base.getLo();
							actualHi = base.getHi();
							if (compare(lo, base.getLo()) != 0) {
								getAllInRange(base.getLo(), base.getHi(), base.getResultStorage(), false);
								return storage.getReference();
							}
						}
					} else {
						getAllInRange(base.getLo(), base.getHi(), base.getResultStorage(), true);
						tryAgain = true;
					}
				} else {
					if (onlyHelpThis != null) {
						return null;
					}
					//tryNormalize(base);
					tryAgain = true;
				}
			} else if (baseNode instanceof CollectAllLockFreeImmutableTreapMapHolder) {
				CollectAllLockFreeImmutableTreapMapHolder<K, V> base = (CollectAllLockFreeImmutableTreapMapHolder<K, V>) baseNode;
				if (base.isValid()) {
					getAll(base.getResultStorage());
					tryAgain = true;
				} else {
					baseNode = tryNormalize(base);
					tryAgain = true;
				}
			} else if (baseNode instanceof JoinNeighborImmutableTreapMapHolder) {
				// System.out.println("RANGE JOIN NEI");
				JoinNeighborImmutableTreapMapHolder<K, V> base = (JoinNeighborImmutableTreapMapHolder<K, V>) baseNode;
				if (base.getMainBase().tryKill()) {
					baseNode = tryNormalize(base);
					tryAgain = true;
				} else {
					completeJoin(base.getMainBase());
					tryAgain = true;
				}
			} else if (baseNode instanceof JoinMainImmutableTreapMapHolder) {
				// System.out.println("RANGE JOIN MAIN");
				JoinMainImmutableTreapMapHolder<K, V> base = (JoinMainImmutableTreapMapHolder<K, V>) baseNode;
				if (base.tryKill()) {
					baseNode = tryNormalize(base);
					tryAgain = true;
				} else {
					completeJoin(base);
					tryAgain = true;
				}
			}
			if (tryAgain) {
				stack.resetStack();
			}
		} while (tryAgain);
		// First base node successfully locked
		ImmutableTreapValue<K, V> ending = null;
		LockFreeImmutableTreapMapHolder<K, V> lastLockedBaseNode = null;
		outer: while (true) {
			tryAgain = false;
			// Add the successfully locked base node to the completed list
			lockedBaseNodesStack.push(baseNode);

			K baseNodeMaxKey = baseNode.maxKey();
			if (baseNodeMaxKey != null && lessThan(actualHi, baseNodeMaxKey)) {
				break; // We have locked all base nodes that we need!
			}

			// There might be more base nodes in the range, continue
			lastLockedBaseNode = baseNode;
			nextStack.copyStateFrom(stack); // Save the current position so we
											// can try again
			do {
				baseNode = getNextBaseNodeAndStack(lastLockedBaseNode, stack);
				if (baseNode == null) {
					break outer;// The last base node is locked
				}

				if (isReplaceable(baseNode)) {
					LockFreeImmutableTreapMapHolder<K, V> base = baseNode;
					CollectRangeQueryImmutableTreapMapHolder<K, V> newBase = new CollectRangeQueryImmutableTreapMapHolder<K, V>(
							actualLo, actualHi, base, storage);
					tryAgain = tryReplace(base, newBase);
					if (!tryAgain) {
						baseNode = newBase;
					}
				} else if (baseNode instanceof CollectAllLockFreeImmutableTreapMapHolder) {
					CollectAllLockFreeImmutableTreapMapHolder<K, V> base = (CollectAllLockFreeImmutableTreapMapHolder<K, V>) baseNode;
					if (base.isValid()) {
						getAll(base.getResultStorage());
						tryAgain = true;
					} else {
						tryNormalize(base);
						tryAgain = true;
					}
				} else if (baseNode instanceof CollectRangeQueryImmutableTreapMapHolder) {
					CollectRangeQueryImmutableTreapMapHolder<K, V> base = (CollectRangeQueryImmutableTreapMapHolder<K, V>) baseNode;
					if (base.getResultStorage() != storage) {
						if (base.isValid()) {
///*BUG*/							if (compare(actualHi, base.getHi()) <= 0) {
///*BUG*/								getAllInRange(base.getLo(), base.getHi(), base.getResultStorage(), true);
///*BUG*/								ending = base.getResult();
///*BUG*/								break outer;
///*BUG*/							}else {
								getAllInRange(base.getLo(), base.getHi(), base.getResultStorage(), true);
								tryAgain = true;
//							}
						} else {
							//tryNormalize(base);
							tryAgain = true;
						}
					}
				} else if (baseNode instanceof JoinNeighborImmutableTreapMapHolder) {
					JoinNeighborImmutableTreapMapHolder<K, V> base = (JoinNeighborImmutableTreapMapHolder<K, V>) baseNode;
					if (base.getMainBase().tryKill()) {
						baseNode = tryNormalize(base);
						tryAgain = true;
					} else {
						completeJoin(base.getMainBase());
						tryAgain = true;
					}
				} else if (baseNode instanceof JoinMainImmutableTreapMapHolder) {
					JoinMainImmutableTreapMapHolder<K, V> base = (JoinMainImmutableTreapMapHolder<K, V>) baseNode;
					if (base.tryKill()) {
						baseNode = tryNormalize(base);
						tryAgain = true;
					} else {
						completeJoin(base);
						tryAgain = true;
					}
				}

				if (storage.getReference() != null) {
					// System.out.println("RANGE: " + lo + " " + hi + " RET 2");
					return storage.getReference();
				}

				if (tryAgain) {
					// unlockBaseNode(mode, baseNode);
					// Reset stack
					stack.copyStateFrom(nextStack);
				}
			} while (tryAgain);
		}
		// We have successfully locked all the base nodes that we need
		// Time to construct the results from the contents of the base nodes
		// The linearization point is just before the first lock is unlocked
		// Stack<ImmutableTreapValue<K, V>> returnStack = tlbs.getReturnStack();
		ImmutableTreapValue<K, V> root = null;
		Object[] lockedBaseNodeArray = lockedBaseNodesStack.getStackArray();
		if (lockedBaseNodesStack.size() == 1) {
			threadLocalBuffers.get().increaseTraversedNodes();
			CollectRangeQueryImmutableTreapMapHolder<K, V> map = (CollectRangeQueryImmutableTreapMapHolder<K, V>) (lockedBaseNodeArray[0]);
			// returnStack.push(map.getRoot());
			root = map.getRoot();
			if(ending != null) {
				root = ImmutableTreapMap.cheapJoin(root, ending);
			}
			storage.compareAndSet(null, root, 0, 1);
			adaptIfNeeded(map);
		} else {
			root = ImmutableTreapMap.createEmpty();
			CollectRangeQueryImmutableTreapMapHolder<K, V> map = null;
			for (int i = 0; i < lockedBaseNodesStack.size(); i++) {
				threadLocalBuffers.get().increaseTraversedNodes();
				map = (CollectRangeQueryImmutableTreapMapHolder<K, V>) (lockedBaseNodeArray[i]);
				// returnStack.push(map.getRoot());
				// assert(compare(ImmutableTreapMap.maxKey(root), ImmutableTreapMap.maxKey(
				// map.getRoot())) < 0);
				// System.out.println("RANGE: " + lo + " " + hi +" actual " + actualLo + " " +
				// actualHi + " ADD " + ImmutableTreapMap.maxKey( map.getRoot()) + " id:" +
				// Thread.currentThread().getId());
				root = ImmutableTreapMap.cheapJoin(root, map.getRoot());
			}
			// CollectRangeQueryImmutableTreapMapHolder<K, V> map =
			// (CollectRangeQueryImmutableTreapMapHolder<K, V>) (lockedBaseNodeArray[0]);
			if(ending != null) {
				root = ImmutableTreapMap.cheapJoin(root, ending);
			}
			storage.compareAndSet(null, root, 0, 2);
			int toAdapt = ThreadLocalRandom.current().nextInt(lockedBaseNodesStack.size());
			map = (CollectRangeQueryImmutableTreapMapHolder<K, V>) (lockedBaseNodeArray[toAdapt]);
			adaptIfNeeded(map);
		}
		// Object[] returnStackArray = returnStack.getStackArray();
		// for (int i = 0; i < returnStack.size(); i++) {
		// ImmutableTreapValue<K, V> tree = (ImmutableTreapValue<K, V>)
		// returnStackArray[i];
		// System.out.println("RANGE: " + lo + " " + hi + " RET 3 " +
		// lockedBaseNodesStack.size());
		return storage.getReference();
		// }
	}

	private boolean tryReplace(LockFreeImmutableTreapMapHolder<K, V> base,
			LockFreeImmutableTreapMapHolder<K, V> newBase) {
		boolean tryAgain = false;
		RouteNode parent = (RouteNode) base.getParent();
		if (parent == null) {
			if (!rootUpdater.compareAndSet(this, base, newBase)) {
				tryAgain = true;
			}
		} else if (parent.left == base) {
			if (!parent.compareAndSetLeft(base, newBase)) {
				tryAgain = true;
			}
		} else if (parent.right == base) {
			if (!parent.compareAndSetRight(base, newBase)) {
				tryAgain = true;
			}
		} else {
			tryAgain = true;
		}
		return tryAgain;
	}

	private NormalLockFreeImmutableTreapMapHolder<K, V> tryNormalize(LockFreeImmutableTreapMapHolder<K, V> base) {
		NormalLockFreeImmutableTreapMapHolder<K, V> newBase = base.normalize();
		RouteNode parent = (RouteNode) base.getParent();
		if (parent == null) {
			if (rootUpdater.compareAndSet(this, base, newBase)) {
				return newBase;
			}
		} else if (parent.left == base) {
			if (parent.compareAndSetLeft(base, newBase)) {
				return newBase;
			}
		} else if (parent.right == base) {
			if (parent.compareAndSetRight(base, newBase)) {
				return newBase;
			}
		}
		return null;
	}

	public final void rangeUpdate(final K lo, final K hi, BiFunction<K, V, V> operation) {
		throw new RuntimeException("Not yet implemented");
	}

	public long getTraversedNodes() {
		return threadLocalBuffers.get().getTraversedNodes();
	}

	public long getNrOfJoins() {
		return threadLocalBuffers.get().getNrOfJoins();
	}
	
	public long getNrOfSplits() {
		return threadLocalBuffers.get().getNrOfSplits();
	}
	
	public static void main(String[] args) {
		{
			System.out.println("Simple TEST");
			LockFreeImmTreapCATreeMapSTDR<Integer, Integer> set = new LockFreeImmTreapCATreeMapSTDR<Integer, Integer>();
			// Insert elements
			for (int i = 0; i < 100; i++) {
				set.put(i, 1000000);
			}
			// Test subSet
			Object[] array = set.subSet(0, 2);
			System.out.println("SUBSET SIZE = " + array.length);
			for (int i = 0; i < array.length; i++) {
				System.out.println(array[i]);
			}
			// Test get
			System.out.println("set.get(7) = " + set.get(7));
			System.out.println("Advanced TEST");
		}
		{
			LockFreeImmTreapCATreeMapSTDR<Integer, Integer> set = new LockFreeImmTreapCATreeMapSTDR<Integer, Integer>();
			// Insert elements
			for (int i = 0; i < 100; i = i + 2) {
				set.put(i, 1000000);
			}
			{
				LockFreeImmutableTreapMapHolder<Integer, Integer> baseNode = (LockFreeImmutableTreapMapHolder<Integer, Integer>) set
						.getBaseNode(50);
				set.highContentionSplit(baseNode);
			}
			{
				LockFreeImmutableTreapMapHolder<Integer, Integer> baseNode = (LockFreeImmutableTreapMapHolder<Integer, Integer>) set
						.getBaseNode(25);
				set.highContentionSplit(baseNode);
			}
			{
				LockFreeImmutableTreapMapHolder<Integer, Integer> baseNode = (LockFreeImmutableTreapMapHolder<Integer, Integer>) set
						.getBaseNode(75);
				set.highContentionSplit(baseNode);
			}
			{
				LockFreeImmutableTreapMapHolder<Integer, Integer> baseNode = (LockFreeImmutableTreapMapHolder<Integer, Integer>) set
						.getBaseNode(1);
				set.highContentionSplit(baseNode);
			}
			// Test subSet
			{
				Object[] array = set.subSet(-30, 50);
				System.out.println("SUBSET SIZE = " + array.length);
				for (int i = 0; i < array.length; i++) {
					System.out.println(array[i]);
				}
			}
			{
				Object[] array = set.subSet(10, 45);
				System.out.println("SUBSET SIZE = " + array.length);
				for (int i = 0; i < array.length; i++) {
					System.out.println(array[i]);
				}
			}
			{
				Object[] array = set.subSet(99, 105);
				System.out.println("SUBSET SIZE = " + array.length);
				for (int i = 0; i < array.length; i++) {
					System.out.println(array[i]);
				}
			}
			{
				Object[] array = set.subSet(-30, 130);
				System.out.println("SUBSET SIZE = " + array.length);
				for (int i = 0; i < array.length; i++) {
					System.out.println(array[i]);
				}
			}
			{
				Object[] array = set.subSet(50, 50);
				System.out.println("SUBSET SIZE = " + array.length);
				for (int i = 0; i < array.length; i++) {
					System.out.println(array[i]);
				}
			}
			{
				Object[] array = set.subSet(12, 34);
				System.out.println("SUBSET SIZE = " + array.length);
				for (int i = 0; i < array.length; i++) {
					System.out.println(array[i]);
				}
			}
			for (int i = 1; i < 100; i = i + 2) {
				set.put(i, 1000000);
			}
			{
				Object[] array = set.subSet(12, 34);
				System.out.println("SUBSET SIZE = " + array.length);
				for (int i = 0; i < array.length; i++) {
					System.out.println(array[i]);
				}
			}
			// Test get
			System.out.println("set.get(7) = " + set.get(7));
		}
	}



}
