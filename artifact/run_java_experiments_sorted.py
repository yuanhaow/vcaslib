
import sys 
import os

sys.path.insert(1, 'internals/')

import create_graphs as graph

if len(sys.argv) == 1 or sys.argv[1] == '-h':
  print("Usage: python3 run_java_experiments_sorted.py [thread_list] [outputfile] [num_repeats] [runtime] [JVM memory size]")
  print("For example: python3 run_java_experiments_sorted.py [1,4] graph.png 1 1 1G")
  exit(0)

ins = 100
rmv = 0
find = 0
rq = 0
rqsize = 0
threads = sys.argv[1][1:-1].split(',')
graphfile = sys.argv[2]
repeats = int(sys.argv[3])*2
runtime = sys.argv[4]
JVM_mem_size = sys.argv[5]
key_range = 2000000000

graphtitle = "insert-only,sorted-order"

benchmark_name = graphtitle.replace(':','-').replace(' ', '-')
results_file_name = "java/results/" + benchmark_name + ".csv"
# print(results_file_name)

datastructures = ["BPBST -param-64", "KIWI",
      "VcasChromaticBatchBSTGC -param-64", "LFCA", 
      "SnapTree", "KSTRQ", "VcasBatchBSTGC -param-64"
      ] 

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
    cmd = numactl + cmdbase + th + " " + str(repeats) + " " + runtime + " " + ds + " -ins" + str(ins) + " -del" + str(rmv) + " -rq" + str(rq) + " -rqsize" + str(rqsize) + " -rqers0 -keys" + str(key_range) + " -seq -file-java/build/data-trials" + str(i) + ".csv"
    print(cmd)
    if os.system(cmd) != 0:
      print("")
      exit(1)

os.system("cat java/build/data-*.csv > " + results_file_name)

graph.plot_java_scalability_graphs(results_file_name, graphfile, graphtitle)
