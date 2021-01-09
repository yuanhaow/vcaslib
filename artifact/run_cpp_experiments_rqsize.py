
import sys 
import os
import socket

sys.path.insert(1, 'internals/')

import create_graphs as graph

if len(sys.argv) == 1 or sys.argv[1] == '-h':
  print("Usage: python3 run_cpp_experiments_rqsize.py [num_keys] [update_threads] [rq_threads] [rqsizelist] [outputfile] [num_repeats] [runtime]")
  print("For example: python3 run_cpp_experiments_rqsize.py 10000 4 4 [8,256] graph.png 1 1")
  exit(0)

num_keys = sys.argv[1]
update_threads = sys.argv[2]
rq_threads = sys.argv[3]
rqsizes = sys.argv[4][1:-1].split(',')
graphfile = sys.argv[5]
repeats = int(sys.argv[6])
runtime = sys.argv[7]
key_range = int(num_keys)*2

ins=50
rmv=50

graphtitle = "rqsize: " + str(num_keys) + "keys-" + str(update_threads) + "up-" + str(rq_threads) + "rq"

benchmark_name = graphtitle.replace(':','-').replace(' ', '-')
results_file_name = "cpp/results/" + benchmark_name + ".txt"
# print(results_file_name)

datastructures = ["vcasbst.out", "bst.rq_lockfree.out", "bst.rq_unsafe.out"] 


jemalloc = "LD_PRELOAD=`jemalloc-config --libdir`/libjemalloc.so.`jemalloc-config --revision` "
numactl = "numactl -i all "
cmdbase = "./cpp/microbench/" + socket.gethostname() + "."

os.system("rm -rf cpp/microbench/results/*.out")

i = 0
for ds in datastructures:
  for rqsize in rqsizes:
    for j in range(int(repeats)):
      i = i+1
      tmp_results_file = "cpp/microbench/results/data-trials" + str(i) + ".out"
      cmd = jemalloc + numactl + cmdbase + ds + " -i " + str(ins) + " -d  " + str(rmv) + " -k " + str(key_range) + " -rq 0 -rqsize " + rqsize + " -t " + str(int(runtime)*1000) + " -p -nrq " + str(rq_threads) + " -nwork " + str(update_threads)
      print(cmd)
      f = open(tmp_results_file, "w")
      f.write(cmd + '\n')
      f.close()
      if os.system(cmd + " >> " + tmp_results_file) != 0:
        print("")
        exit(1)

os.system("cat cpp/microbench/results/data-*.out > " + results_file_name)

graph.plot_cpp_rqsize_graphs(results_file_name, graphfile, graphtitle)
