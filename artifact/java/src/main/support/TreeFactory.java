
package main.support;

public abstract class TreeFactory<K> {
    public abstract SetInterface<K> newTree(final Object param);
    public abstract String getName();
}