
import sys 
import os

sys.path.insert(1, 'internals/')

import create_graphs as graph

if len(sys.argv) == 1 or sys.argv[1] == '-h':
  print("Usage: python3 run_java_experiments_multipoint.py [query_threads] [update_threads] [num_keys] [outputfile] [num_repeats] [runtime] [JVM memory size]")
  print("For example: python3 run_java_experiments_multipoint.py 4 4 100000 graph.png 1 1 1G")
  exit(0)

query_threads = sys.argv[1]
update_threads = [0, int(sys.argv[2])]
num_keys = sys.argv[3]

graphfile = sys.argv[4]
repeats = int(sys.argv[5])*2
runtime = sys.argv[6]
JVM_mem_size = sys.argv[7]
key_range = int(num_keys)*2

graphtitle = "multipoint: " + str(query_threads) + "qth-" + str(update_threads[1]) + "uth-" + str(num_keys) + "keys"

benchmark_name = graphtitle.replace(':','-').replace(' ', '-')
results_file_name = "java/results/" + benchmark_name + ".csv"

datastructures = ["VcasChromaticBatchBSTGC -param-64", "ChromaticBatchBST -param-64"] 
query_types = [" -findif -rqsize128 ", " -succ -rqsize128 ", " -succ -rqsize1 ", " -multisearch -rqsize4 ", " -rqsize256 "]

# graph.plot_java_multipoint_graphs(results_file_name, graphfile, graphtitle)

numactl = "numactl -i all "
cmdbase = "java -server -Xms" + JVM_mem_size + " -Xmx" + JVM_mem_size + " -Xbootclasspath/a:'java/lib/scala-library.jar:java/lib/deuceAgent.jar' -jar java/build/experiments_instr.jar "

# delete previous results
os.system("rm -rf java/build/*.csv")
os.system("rm -rf java/build/*.csv_stdout")

i = 0
for ds in datastructures:
  for up_th in update_threads:
    for querytype in query_types:
      i = i+1
      th = str(int(up_th) + int(query_threads))
      cmd = numactl + cmdbase + th + " " + str(repeats) + " " + runtime + " " + ds + " -ins50 -del50 -rq0 " + " -rqers" + query_threads + querytype + " -keys" + str(key_range) + " -prefill -file-java/build/data-trials" + str(i) + ".csv"
      print(cmd)
      if os.system(cmd) != 0:
        print("")
        exit(1)

os.system("cat java/build/data-*.csv > " + results_file_name)

graph.plot_java_multipoint_graphs(results_file_name, graphfile, graphtitle)
