
package main.support;

import org.deuce.transform.Exclude;

import adapters.*;
import main.support.*;
import java.util.ArrayList;

public class Factories {
      // central list of factory classes for all supported data structures

    public static final ArrayList<TreeFactory<Integer>> factories =
            new ArrayList<TreeFactory<Integer>>();
    static {
        factories.add(new VcasBatchBSTGCFactory<Integer>());
        factories.add(new LockFreeBSTFactory<Integer>());
        factories.add(new LockFreeChromaticFactory<Integer>());
        factories.add(new LockFreeBatchBSTFactory<Integer>());
        factories.add(new LockFreePBSTFactory<Integer>());
        factories.add(new LockFreeBPBSTFactory<Integer>());
        factories.add(new LFCAFactory<Integer>());
        factories.add(new LockFreeBatchChromaticFactory<Integer>());
        factories.add(new VcasBatchChromaticGCFactory<Integer>());
        factories.add(new KiwiFactory<Integer>());
        factories.add(new SnapTreeFactory<Integer>());
        factories.add(new LockFreeKSTRQFactory<Integer>());
    }

    // factory classes for each supported data structure

    @Exclude
    protected static class LockFreePBSTFactory<K> extends TreeFactory<K> {
        public SetInterface<K> newTree(final Object param) {
            return new LockFreePBSTAdapter();
        }
        public String getName() { return "PBST"; }
    }

    @Exclude
    protected static class KiwiFactory<K> extends TreeFactory<K> {
        public SetInterface<K> newTree(final Object param) {
            return new KiwiAdapter();
        }
        public String getName() { return "KIWI"; }
    }

    @Exclude
    protected static class LFCAFactory<K> extends TreeFactory<K> {
        public SetInterface<K> newTree(final Object param) {
            return new LFCAAdapter();
        }
        public String getName() { return "LFCA"; }
    }

    @Exclude
    protected static class LockFreeBSTFactory<K> extends TreeFactory<K> {
        public SetInterface<K> newTree(final Object param) {
            return new LockFreeBSTAdapter();
        }
        public String getName() { return "BST"; }
    }

    @Exclude
    protected static class LockFreeChromaticFactory<K> extends TreeFactory<K> {
        public SetInterface<K> newTree(final Object param) {
            return new LockFreeChromaticAdapter();
        }
        public String getName() { return "ChromaticBST"; }
    }

    @Exclude
    protected static class SnapTreeFactory<K> extends TreeFactory<K> {
        public SetInterface<K> newTree(final Object param) {
            return new SnapTreeAdapter();
        }
        public String getName() { return "SnapTree"; }
    }

    @Exclude
    protected static class LockFreeKSTRQFactory<K> extends TreeFactory<K> {
        public SetInterface<K> newTree(final Object param) {
            return new LockFreeKSTRQAdapter();
        }
        public String getName() { return "KSTRQ"; }
    }

    @Exclude
    protected static class LockFreeBatchChromaticFactory<K> extends TreeFactory<K> {
        Object param;
        public SetInterface<K> newTree(final Object param) {
            this.param = param;
            return param.toString().isEmpty() ? new LockFreeBatchChromaticAdapter()
                                              : new LockFreeBatchChromaticAdapter(Integer.parseInt(param.toString()));
        }
        public String getName() { return "ChromaticBatchBST"; }
    }

    @Exclude
    protected static class VcasBatchChromaticGCFactory<K> extends TreeFactory<K> {
        Object param;
        public SetInterface<K> newTree(final Object param) {
            this.param = param;
            return param.toString().isEmpty() ? new VcasBatchChromaticGCAdapter()
                                              : new VcasBatchChromaticGCAdapter(Integer.parseInt(param.toString()));
        }
        public String getName() { return "VcasChromaticBatchBSTGC"; }
    }

    @Exclude
    protected static class LockFreeBatchBSTFactory<K> extends TreeFactory<K> {
        Object param;
        public SetInterface<K> newTree(final Object param) {
            this.param = param;
            return param.toString().isEmpty() ? new LockFreeBatchBSTAdapter()
                                              : new LockFreeBatchBSTAdapter(Integer.parseInt(param.toString()));
        }
        public String getName() { return "BatchBST"; }
    }

    @Exclude
    protected static class VcasBatchBSTGCFactory<K> extends TreeFactory<K> {
        Object param;
        public SetInterface<K> newTree(final Object param) {
            this.param = param;
            return param.toString().isEmpty() ? new VcasBatchBSTGCAdapter()
                                              : new VcasBatchBSTGCAdapter(Integer.parseInt(param.toString()));
        }
        public String getName() { return "VcasBatchBSTGC"; }
    }

    @Exclude
    protected static class LockFreeBPBSTFactory<K> extends TreeFactory<K> {
        Object param;
        public SetInterface<K> newTree(final Object param) {
            this.param = param;
            return param.toString().isEmpty() ? new LockFreeBPBSTAdapter()
                                              : new LockFreeBPBSTAdapter(Integer.parseInt(param.toString()));
        }
        public String getName() { return "BPBST"; }
    }

}