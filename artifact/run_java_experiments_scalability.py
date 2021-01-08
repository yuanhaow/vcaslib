
import sys 
import os

sys.path.insert(1, 'internals/')

import create_graphs as graph

if len(sys.argv) == 1 or sys.argv[1] == '-h':
  print("Usage: python3 run_java_experiments_scalability.py [num_keys] [ins-del-find-rq-rqsize] [thread_list] [outputfile] [num_repeats] [runtime] [JVM memory size]")
  print("For example: python3 run_java_experiments_scalability.py 10000 3-2-95-0-0 [1,4] graph.png 1 1 1G")
  exit(0)

num_keys = sys.argv[1]
workload = sys.argv[2].split('-')
ins = workload[0]
rmv = workload[1]
find = workload[2]
rq = workload[3]
rqsize = workload[4]
threads = sys.argv[3][1:-1].split(',')
graphfile = sys.argv[4]
repeats = int(sys.argv[5])*2
runtime = sys.argv[6]
JVM_mem_size = sys.argv[7]
key_range = int(int(num_keys) * (int(ins) + int(rmv)) / int(ins))

graphtitle = "scalability: " + str(num_keys) + "keys-" + str(ins) + "ins-" + str(rmv) + "del-" + str(rq) + "rq-" + str(rqsize) + "rqsize"

benchmark_name = graphtitle.replace(':','-').replace(' ', '-')
results_file_name = "java/results/" + benchmark_name + ".csv"
# print(results_file_name)

# graph.plot_java_scalability_graphs(results_file_name, graphfile, graphtitle)

datastructures = ["BPBST -param-64", "KIWI",
      "VcasChromaticBatchBSTGC -param-64", "LFCA", 
      "SnapTree", "KSTRQ", "VcasBatchBSTGC -param-64"] 

# "ChromaticBatchBST -param-64", "BatchBST -param-64"

numactl = "numactl -i all "
numactl = ""
cmdbase = "java -server -Xms" + JVM_mem_size + " -Xmx" + JVM_mem_size + " -Xbootclasspath/a:'java/lib/scala-library.jar:java/lib/deuceAgent.jar' -jar java/build/experiments_instr.jar "

# delete previous results
os.system("rm -rf java/build/*.csv")
os.system("rm -rf java/build/*.csv_stdout")

i = 0
for ds in datastructures:
  for th in threads:
    i = i+1
    cmd = numactl + cmdbase + th + " " + str(repeats) + " " + runtime + " " + ds + " -ins" + ins + " -del" + rmv + " -rq" + rq + " -rqsize" + rqsize + " -rqers0 -keys" + str(key_range) + " -prefill -file-java/build/data-trials" + str(i) + ".csv"
    print(cmd)
    if os.system(cmd) != 0:
      print("")
      exit(1)

os.system("cat java/build/data-*.csv > " + results_file_name)

graph.plot_java_scalability_graphs(results_file_name, graphfile, graphtitle)
