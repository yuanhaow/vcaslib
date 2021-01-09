
import sys 
import os

sys.path.insert(1, 'internals/')

import create_graphs as graph

if len(sys.argv) == 1 or sys.argv[1] == '-h':
  print("Usage: python3 run_java_experiments_overhead.py [threads] [num_keys1] [num_keys2] [outputfile] [num_repeats] [runtime] [JVM memory size]")
  print("For example: python3 run_java_experiments_overhead.py 4 100000 1000000 graph.png 1 1 1G")
  exit(0)

th = sys.argv[1]
num_keys = [sys.argv[2], sys.argv[3]]

workloads = [" -ins3 -del2 -rq0 -rqsize0 ", " -ins30 -del20 -rq0 -rqsize0 "]

graphfile = sys.argv[4]
repeats = int(sys.argv[5])*2
runtime = sys.argv[6]
JVM_mem_size = sys.argv[7]
key_range = [0, 1]
key_range[0] = int(int(num_keys[0]) * (int(3) + int(2)) / int(3))
key_range[1] = int(int(num_keys[1]) * (int(3) + int(2)) / int(3))


graphtitle = "overhead: " + str(num_keys[0]) + "-" + str(num_keys[1]) + "keys-" + str(th) + "th"

benchmark_name = graphtitle.replace(':','-').replace(' ', '-')
results_file_name = "java/results/" + benchmark_name + ".csv"

datastructures = ["VcasChromaticBatchBSTGC -param-64", "VcasBatchBSTGC -param-64", "ChromaticBatchBST -param-64", "BatchBST -param-64"] 

# "ChromaticBatchBST -param-64", "BatchBST -param-64"

numactl = "numactl -i all "
cmdbase = "java -server -Xms" + JVM_mem_size + " -Xmx" + JVM_mem_size + " -Xbootclasspath/a:'java/lib/scala-library.jar:java/lib/deuceAgent.jar' -jar java/build/experiments_instr.jar "

# delete previous results
os.system("rm -rf java/build/*.csv")
os.system("rm -rf java/build/*.csv_stdout")

i = 0
for ds in datastructures:
  for size in key_range:
    for workload in workloads:
      i = i+1
      cmd = numactl + cmdbase + th + " " + str(repeats) + " " + runtime + " " + ds + workload + " -rqers0 -keys" + str(size) + " -prefill -file-java/build/data-trials" + str(i) + ".csv"
      print(cmd)
      if os.system(cmd) != 0:
        print("")
        exit(1)

os.system("cat java/build/data-*.csv > " + results_file_name)

graph.plot_java_overhead_graphs(results_file_name, graphfile, graphtitle)
