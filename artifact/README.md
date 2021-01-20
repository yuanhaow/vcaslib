
# Constant-Time Snapshots with Applications to Concurrent Data Structures

This artifact contains the source code and scripts to reproduce all the graphs in the following paper:

Constant-Time Snapshots with Applications to Concurrent Data Structures \
Yuanhao Wei, Naama Ben-David, Guy E. Blelloch, Panagiota Fatourou, Eric Ruppert, Yihan Sun \
PPoPP 2021

An up-to-date implementation of the techniques in this paper can be found here: https://github.com/yuanhaow/vcaslib.

## Software Requirements

Our artifact is expected to run correctly under a variety of Linux x86\_64 distributions. Our Java experiments require OpenJDK 11 and our C++ experiments were compiled with g++ 9.
For scalable memory allocation in C++, we used jemalloc 5.2.1.
Our scripts for running experiments and drawing graphs require a Python 3 installation with mathplotlib.
We used ```numactl -i all``` to evenly interleave memory across NUMA nodes.

#### Installing Dependancies (Ubuntu 20.04)
```
  sudo apt-get install -y g++-9
  sudo apt-get install -y openjdk-11-jdk
  sudo apt-get install -y python3-matplotlib
  sudo apt-get install -y numactl
```

To install jemalloc, download the following tar file and follow the instructions contained inside: https://github.com/jemalloc/jemalloc/releases/download/5.2.1/jemalloc-5.2.1.tar.bz2

## Hardware Requirements

A multicore machine with at least 60GB of main memory

## Compiling and running tests

```
    ./compile_all.sh    # compiles both Java and C++ benchmarks
    ./run_all_tests.sh  # tests both Java and C++ benchmarks
```
  - Expected output for ```compile_all.sh``` and ```run_all_tests.sh``` can be found in the ```expected_output``` directory

## Running experiments and generating graphs
  - To reproduce all the graphs in the paper, run ```./generate_graphs_from_paper.sh```
      - Note: these steps assume ```./compile_all.sh``` has already been executed
      - The output graphs will be stored in the ```graphs/``` directory
      - This command will take about 20 hours to run and requires a machine with 300G of main memory.
      - On a machine with less memory, you can edit ```generate_graphs_from_paper.sh``` by replacing '300G' with the memory size of your machine.
      - Each command in ```generate_graphs_from_paper.sh``` is responsible for generating a single graph. To generate only a specific graph, run the corresponding command from the file.
  - You can also run custom experiments (and generate graphs for them) using the following scripts: 
      - ```run_java_experiments_scalability.py```      :  scalability experiments (Figures 4a-f)
      - ```run_java_experiments_rqsize.py```           :  Java range query size experiments (Figures 4g-h)
      - ```run_cpp_experiments_rqsize.py```            :  C++ range query size experiments (Figures 5a-b)
      - ```run_java_experiments_sorted.py```           :  sorted-order insert experiments (Figure 4j)
      - ```run_java_experiments_overhead.py```         :  overhead VCAS (Figure 4k)
      - ```run_java_experiments_multipoint.py```       :  overhead of multipoint queries (Figure 4m)
      - ```run_cpp_experiments_memory_usage.py```      :  C++ memory usage (Figure 5c)
      - ```run_java_experiments_memory_usage.py```     :  Java memory usage (Figure 4i)
  - Use the ```-h``` option to see the parameters required by each script. 
  - Usage examples:
```
      python3 run_java_experiments_scalability.py -h
      python3 run_java_experiments_scalability.py [num_keys] [ins-del-find-rq-rqsize] [thread_list] [outputfile] [num_repeats] [runtime] [JVM memory size]
      python3 run_java_experiments_scalability.py  100000     30-20-49-1-1024         [1,4,16]       graph1.png   5             5         60G
```
  - See ```generate_graphs_from_paper.sh``` for more examples of how to use the python scripts.
  - Parameter descriptions: 
      - **JVM memory size** : We set Java's heap size to 300G in our experiments to avoid measuring GC time. 
        If your machine does not have 300G of main memory, we recommend setting this value as high as possible.
        At least 60G is recommended because one of the data structures we compare with, PNB-BST, does
        not allow for garbage collection and uses up to 30G of main memory in some experiments.
      - **num_repeats** : In the Java benchmarks, each experiment is repeated (num_repeats x 2) times where the
        first half is used to warm up the JVM.
      - **runtime** : measured in seconds
      - **thread_list** : make sure there are no spaces in the lists. i.e. [1,2,3] instead of [1, 2, 3]. 
        Similarly for rqsize_list.
      - **ins-del-find-rq-rqsize** : The input 30-20-49-1-1024 indicates a workload with 30% inserts, 20% deletes
        49% finds, and 1% range queries of size 1024.
      - **num_keys** : number of keys to prefill the data structure with. As described in our experiments section,
        the key range is chosen so that the size of the data structure remains stable throughout the experiment.
      - **rqsize** : range query size
      - **update_threads** : number of update threads, each performing 50% inserts or 50% deletes
      - **rq_threads** : number of range query threads
      - **query_threads** : number of threads performing multi-point queries
      - **threads** : number of worker threads

### Names
  - For some datastructures, the scripts/code and the paper/graphs use different names. A look up table is provided below.

| Paper         | Scripts/Code              | 
| ------------- |:-------------------------:| 
| CT-64         | ChromaticBatchBST64       | 
| VcasCT-64     | VcasChromaticBatchBSTGC64 | 
| PNB-BST       | BPBST64                   | 
| BST-64        | BatchBST64                | 
| VcasBST-64    | VcasBatchBSTGC64          | 
| KST           | KSTRQ                     | 
| VcasBST       | vcasbst                   | 
| BST           | bst.rq_unsafe             | 
| EpochBST      | bst.rq_lockfree           | 

## Sources
  - Java Benchmark Framework: https://github.com/elias-pap/concurrent-data-structures
      - main file: ```java/src/main/Main.java```
      - includes code for PNB-BST
  - C++ Benchmark Framework: https://bitbucket.org/trbot86/implementations/src/master/cpp/range_queries/
      - main file: ```cpp/microbench/main.cpp```
      - includes code for BST and EpochBST
  - LFCA: https://github.com/kjellwinblad/JavaRQBench
      - includes code for SnapTree, and KST
  - KIWI: https://github.com/sdimbsn/KiWi
  - Chromatic-Tree and EFRB-Tree: https://bitbucket.org/trbot86/implementations/src/master/java/src/algorithms/published/

## Our Implementations
  - Non-blocking **unbalanced** BST with Constant-Time Snapshotting **(VcasBST)** :
      - cpp/bst/vcas_bst.h
      - cpp/bst/vcas_bst_impl.h
      - cpp/bst/vcas_node.h
      - cpp/bst/vcas_scxrecord.h
      - cpp/rq/rq_vcas.h
  - Non-blocking **unbalanced** BST with Constant-Time Snapshotting and Batched Leaves **(VcasBST-64)** :
      - java/src/algorithms/vcas/Camera.java
      - java/src/algorithms/vcas/VcasBatchBSTMapGC.java
  - Non-blocking **balanced** BST with Constant-Time Snapshotting and Batched Leaves **(VcasCT-64)** :
      - java/src/algorithms/vcas/Camera.java
      - java/src/algorithms/vcas/VcasBatchChromaticMapGC.java
  - Non-blocking **unbalanced** BST with Batching **(BST-64)** : java/src/algorithms/efrbbst/LockFreeBatchBSTMap.java
  - Non-blocking **balanced** BST with Batching **(CT-64)** : java/src/algorithms/chromatic/LockFreeBatchChromaticMap.java
  - Epoch based memory reclamation **(EBR)** : java/src/main/support/Epoch.java

