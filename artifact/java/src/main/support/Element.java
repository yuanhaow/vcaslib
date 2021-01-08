
package main.support;

public class Element<K, V> {
  public K key;
  public V value;

  public Element(K key, V value) {
    set(key, value);
  }

  public Element() {
    this(null, null);
  }

  public void set(K key, V value) {
    this.key = key;
    this.value = value;
  }
}