# Concurrent Data Structures

## Original Implementations
### Persistent Non-Blocking Binary Search Tree ([PNB-BST](https://github.com/elias-pap/concurrent-data-structures/blob/master/java/src/algorithms/published/LockFreePBSTMap.java))
This is an implementation of PNB-BST algorithm described in the paper
"Persistent Non-Blocking Binary Search Trees Supporting Wait-Free Range Queries"
of Panagiota Fatourou, Elias Papavasileiou and Eric Ruppert.

### Batched Persistent Non-Blocking Binary Search Tree ([BPNB-BST](https://github.com/elias-pap/concurrent-data-structures/blob/master/java/src/algorithms/published/LockFreeBPBSTMap.java))
This is an optimized version of PNB-BST algorithm that supports key batching in the leafs.

# Benchmark suite

## Workflow
To launch an experiment:
1. `./compile`
1. `./run-experiments`

After the experiment:
1. `./scrarchive.sh`
1. `./scrmerge.sh`
1. `./scr.py`
1. Open the generated .csv file in "experiments" folder, and copy the generated table in "toplot.txt"
1. Tweak the appropriate plot\_script file (e.g. `nano plot_script_1.gp`)
1. Launch gnuplot and give the appropriate command (e.g. `load "plot_script_1.gp"`) 


## Contents
Many different versions of the same file are included in some cases. Usually, the last modified is the one currently used. Using `git diff -w --no-index file1 file2`, one can see the changes between versions.

  - src
  Java source code files.

  - adapters
  Adapters for concurrent data structures.

  - algorithms/published
  Contains all java source code files of our algorithms used in the experiments of the paper "Persistent Non-Blocking Binary Search Trees Supporting Wait-Free Range Queries".

  - main
  Main function and Globals file.

  - compile
  Compiles every .java source file.

  - merge
  Merges .csv files.

  - plot_script_*.gp
  Gnuplot scripts used to generate the graphs of the experiments.

  - plot_script_cm.gp
  Gnuplot script that plots the cache misses per operation.
  
  - plot_script_mem.gp
  Gnuplot script that plots the memory usage over time.

  - README.txt
  Useful README file provided by Trevor Brown.

  - run-experiments*
  Scripts used to run the experiments. Memory usage and cache misses are measured with these scripts as well.

  - scr.py
  Constructs a new .csv file containing the throughput results of the experiment in a table format.

  - scrarchive.sh
  Makes a backup of all .csv and .txt files contained in build/ folder.

  - scrcm.py
  Constructs a new .csv file containing the cache-misses results of the experiment in a table format.

  - scrmem.py
    Constructs a new .csv file containing the memory usage results of the experiment in a table format.

  - scrmerge.sh
  Merges .csv files. Wrapper of ./merge.

  - toplot.txt
  A .txt file used as input to gnuplot, to generate a plot.

## Acknowledgements
[Trevor Brown](https://bitbucket.org/trbot86/) provided the implementation of [Non-Blocking Binary Search Tree](https://bitbucket.org/trbot86/implementations/src/6dbe554bbea072bdd5ec344a2653d93cd502d5a8/java/src/algorithms/published/LockFreeBSTMap.java) (NB-BST) on which the implementation of PNB-BST was based on. The benchmark suite is also based on his [benchmark code](https://bitbucket.org/trbot86/implementations/src/master/java/).


From Trevor's Paper
================================================================================

This is a complete Java test harness capable of running all experiments for the
paper "A general technique for non-blocking trees." Shell scripts to compile
and run the experiments are included for Linux / Solaris.

COMPILING AND RUNNING:

To make the scripts executable, execute:
  chmod 755 compile, merge, run-experiments

Before you can use them, you must edit "compile" and "run-experiments" to
reflect the appropriate path to your "java", "javac" and "jar" binaries.

To compile, execute:
  ./compile

This will show some warnings about the deprecated Java Unsafe API.
This API is needed by the SkipTree data structure.

To run a quick test, execute:
  java -server -d64 -Xms3G -Xmx3G -Xbootclasspath/p:'./lib/scala-library.jar' -jar build/experiments.jar 8 5 2 Chromatic -param-6 -ins50 -del50 -keys1048576 -prefill -file-data-temp.csv

To run an STM-based algorithm, you must include deuceAgent.jar in the boot classpath and run the JAR that is instrumented for transactions, instead:
  java -server -d64 -Xms3G -Xmx3G -Xbootclasspath/p:'./lib/scala-library.jar:./lib/deuceAgent.jar' -jar build/experiments_instr.jar 8 5 2 RBSTM -param-norec -ins50 -del50 -keys16384 -prefill -file-data-temp.csv

The output of this run will appear in a file called data-temp.csv in the
root directory for the project.

To run experiments, execute:
  ./run-experiments

Output CSV files appear in the directory "build".
One CSV file is created per experiment (workload * key range * algorithm).
These CSV files can be merged into one file "data.csv" by executing:
  ./merge build/*.csv

INCLUDED DATA STRUCTURES:

BST             An implementation of the non-blocking BST of Ellen, Fatourou,
                Ruppert and val Breugel.
KST             A non-blocking k-ary search tree described in the paper
                "Non-blocking k-ary search trees" by Brown and Helga.
                This data structure requires a command line parameter, e.g.,
                "-param-4" or "-param-8" to specify the degree, k, of nodes.
4-ST            An implementation of the KST optimized for k=4.
Chromatic       A non-blocking chromatic tree described in the paper "A general
                technique for non-blocking trees" by Brown, Ellen and Ruppert.
                This data structure accepts a command line parameter.
                With "-param-0", after a thread performs an insert or delete,
                it performs rebalancing steps to fix any imbalance it created.
                With "-param-6", after a thread performs an insert or delete,
                it performs rebalancing steps only if it saw more than six
                "violations" (which indicate imbalance) as it traversed the
                tree to reach node where it inserted or deleted a key.
                This parameter accepts any integer value.
                The experiments in the paper use parameter values 0 and 6.
LockFreeAVL     A non-blocking relaxed AVL tree briefly mentioned in the same
                paper as Chromatic. Like Chromatic, this accepts a parameter
                (which serves the same purpose). Experiments showed 15 to be
                a good choice for this parameter.
AVL             The leading lock-based AVL tree, which is described in the
                paper "A practical concurrent binary search tree" by Bronson,
                Casper, Chafi and Olukotun.
Snap            A slightly slower variant of AVL that supports clone().
SkipList        The non-blocking skip list of the Java library. This is the
                class "java.util.concurrent.ConcurrentSkipListMap".
SkipTree        A non-blocking multiway search tree that combines elements of
                B-trees and skip lists. It is described in the paper
                "Lock-free multiway search trees" by Spiegel.
RBUnsync        This is a red black tree developed by Herlihy and Oracle to
                demonstrate the performance of software transactional memory.
                This particular version has no synchronization whatsoever.
                It is a sequential data structure and is NOT thread safe.
RBLock          Same as RBUnsync, but uses Java synchronized blocks to
                guarantee thread safety.
RBSTM           Same as RBLock, but uses DeuceSTM 1.3 instead of locks. At time
                of writing, DeuceSTM is the fastest Java STM that does not
                require modifications to the JVM.
                This algorithm (and all others that use DeuceSTM) require the
                user to specify the STM algorithm to use as a parameter on the
                command line by adding "-param-X" where X is an algorithm in
                the set {tl2, lsa, tl2cm, lsacm, norec}.
SkipListSTM     A skip list implemented using DeuceSTM 1.3.
Ctrie           A non-blocking concurrent hash trie, which is described in the
                paper "Concurrent tries with efficient non-blocking snapshots"
                by Prokopec, Bronson, Bagwell and Odersky.
                This uses hashing, and implements an unordered dictionary.
ConcurrentHMAP  java.util.concurrent.ConcurrentHashMap
SyncTMAP        java.util.TreeMap wrapped in synchronized blocks
TMAP            java.util.TreeMap
HMAP            java.util.HashMap

ADDING YOUR OWN DATA STRUCTURE(S):
  
It is easy to extend the test harness to run experiments using your own data
structure(s). To add a new data structure, there are three simple steps:
1. Copy an adapter class in "src/adapters" (such as "OptTreeAdapter.java") and
   implement the method stubs using your data structure.
   add() and remove() should return true if they changed the data structure,
   and false otherwise.
   get() should return the key it found, or null if none was found.
   contains() should return true if the key is in the data structure, and
   false otherwise.
   size() and sequentialSize() must both return the number of keys in the data
   structure. You can assume no concurrent operations occur when they are
   executing. There is no difference between these methods in this package.
   addListener() should be left empty, and getRoot() should simply return null.
   They both provide functionality that is not included in this package.
   If your data structure is not a tree, or you do not care about its depth,
   then getSumOfDepths() should simply return 0.
   Otherwise, it should return the sum, over every key k, of the depth of the
   node containing k (that would be found by a contains() or get() operation).
2. Copy one of the private factory classes in "src/main/Main.java" and
   implement it using your data structure.
   The newTree() method should return a new instance of the adapter for your
   data structure.
   The getName() method should return a string (with no spaces) that represents
   your data structure. This string is what you will provide to the test
   harness in "run-experiments" so that it uses your data structure.
3. Add an instance of your factory to the "factories" ArrayList near the top of
   "src/main/Main.java".

It should be easy to edit "run-experiments" to include your data structure.

Finally, a word about experimental methodology in Java:

* Experiments of this sort in Java should be performed with a number of timed
  trials, each running for a reasonable length of time, such as five seconds.
  This length of time should be long enough that running substantially longer
  trials (e.g., 10x longer) does not change your results.
* The amount of memory allocated to the JVM should be chosen such that memory
  will be exhausted, and garbage collection performed, several times per trial.
  If garbage collection is never triggered, then a crucial piece of your data
  structure's performance is being ignored, and one cannot gauge how your
  algorithm will perform on a real system. However, if memory pressure is
  too severe, you will be measuring the performance of a stressed garbage
  collector, and not your algorithm. Try to choose the smallest memory limit
  that does not grossly inflate the standard deviation of your throughput
  measurements over all trials for an experiment. I typically find 3GB to be
  reasonable for five second trials on 128 thread servers. I also recommend
  setting a minimum and maximum heap size, since Java is very eager in resizing
  the heap.
* Each trial should start with trees that are prefilled to their expected size
  in the steady state, so that you are measuring steady state performance, and
  not inconsistent performance as the size of the tree is changing. If your
  experiment consists of random operations in the proportions i% insertions,
  d% deletions, and s% searches on keys drawn uniformly randomly from a key range
  of size r, then the expected size of the tree in the steady state will be
  ri/(i+d). For example, in an experiment where 20% of operations are insertions,
  10% are deletions, and 70% are searches, the expected size of the tree in the
  steady state is (2/3)r.
* When you work in Java, compilation happens on the fly, during the first few
  seconds of your experiments. For this reason, you should throw away the first
  few trials of each experiment. It should be obvious how many need to be
  discarded when you look at the measured throughput of each trial. This is
  important because some algorithms take longer than others to compile, and an
  algorithm that takes longer may be stuck in its slow, interpreted state for
  much longer than another. This can skew throughput measurements in favour of
  the algorithm that compiles faster.
* Remember to use a 64-bit JVM on a 64-bit machine, and to use the -d64 and
  -server JVM flags. These flags can drastically change performance measurements.
* Experiments should run in separate invocations of the JVM, since they will not
  be statistically independent if they run in the same VM. I've seen this change
  results dramatically. For more information on this and other best practices
  for Java experiments, see the paper "Statistically rigorous java performance
  evaluation" by Georges et al.
