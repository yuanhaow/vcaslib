
package main.support;

public class NodeStats {
  public Integer internalNodes;
  public Integer externalNodes;

  public NodeStats(Integer internalNodes, Integer externalNodes) {
    set(internalNodes, externalNodes);
  }

  public NodeStats() {
    this(0, 0);
  }

  public void set(Integer internalNodes, Integer externalNodes) {
    this.internalNodes = internalNodes;
    this.externalNodes = externalNodes;
  }

  public void add(NodeStats p) {
    this.internalNodes += p.internalNodes;
    this.externalNodes += p.externalNodes;
  }
}