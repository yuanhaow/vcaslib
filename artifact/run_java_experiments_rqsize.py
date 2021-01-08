
import sys 
import os

sys.path.insert(1, 'internals/')

import create_graphs as graph

if len(sys.argv) == 1 or sys.argv[1] == '-h':
  print("Usage: python3 run_java_experiments_rqsize.py [num_keys] [update_threads] [rq_threads] [rqsizelist] [outputfile] [num_repeats] [runtime] [JVM memory size]")
  print("For example: python3 run_java_experiments_rqsize.py 10000 4 4 [8,256] graph.png 1 1 1G")
  exit(0)

num_keys = sys.argv[1]
update_threads = sys.argv[2]
rq_threads = sys.argv[3]
rqsizes = sys.argv[4][1:-1].split(',')
graphfile = sys.argv[5]
repeats = int(sys.argv[6])*2
runtime = sys.argv[7]
JVM_mem_size = sys.argv[8]

ins=50
rmv=50
th = int(update_threads)+int(rq_threads)

key_range = int(int(num_keys) * (int(ins) + int(rmv)) / int(ins))

graphtitle = "rqsize: " + str(num_keys) + "keys-" + str(update_threads) + "up-" + str(rq_threads) + "rq"

benchmark_name = graphtitle.replace(':','-').replace(' ', '-')
results_file_name = "java/results/" + benchmark_name + ".csv"
# print(results_file_name)

# graph.plot_java_scalability_graphs(results_file_name, graphfile, graphtitle)

datastructures = ["BPBST -param-64", "KIWI",
      "VcasChromaticBatchBSTGC -param-64", "LFCA", 
      "SnapTree", "KSTRQ", "VcasBatchBSTGC -param-64"
      ] 

# "ChromaticBatchBST -param-64", "BatchBST -param-64"

numactl = "numactl -i all "
numactl = ""
cmdbase = "java -server -Xms" + JVM_mem_size + " -Xmx" + JVM_mem_size + " -Xbootclasspath/a:'java/lib/scala-library.jar:java/lib/deuceAgent.jar' -jar java/build/experiments_instr.jar "

# delete previous results
os.system("rm -rf java/build/*.csv")
os.system("rm -rf java/build/*.csv_stdout")

i = 0
for ds in datastructures:
  for rqsize in rqsizes:
    i = i+1
    cmd = numactl + cmdbase + str(th) + " " + str(repeats) + " " + runtime + " " + ds + " -ins" + str(ins) + " -del" + str(rmv) + " -rq0 -rqsize" + str(rqsize) + " -rqers" + rq_threads + " -keys" + str(key_range) + " -prefill -file-java/build/data-trials" + str(i) + ".csv"
    print(cmd)
    if os.system(cmd) != 0:
      print("")
      exit(1)

os.system("cat java/build/data-*.csv > " + results_file_name)

graph.plot_java_rqsize_graphs(results_file_name, graphfile, graphtitle)
