## Note: as a convenience hack, this file is included in Makefiles,
##       AND in bash scripts, to set some variables.
##       So, don't write anything that isn't valid in both make AND bash.

## Set the desired maximum thread count (maxthreads),
## an upper bound on the maximum thread count that is a power of 2 (maxthreads_powerof2),
## the maximum range query thread count (maxrqthreads) for use in experiments,
## the number of threads to increment by in the graphs produced by experiments (threadincrement),
## and the CPU frequency in GHz (cpu_freq_ghz) used for timing measurements with RDTSC.

maxthreads=72
maxthreads_powerof2=128
maxrqthreads=36
threadincrement=1
cpu_freq_ghz=2.4

## Configure the thread pinning/binding policy (see README.txt)
## Blank means no thread pinning. (Threads can run wherever they want.)
pinning_policy=""

## The policy commented out below is what we used on our 48 thread 2-socket
## Intel machine (where we pinned threads to alternating sockets).
#pinning_policy="-bind 0,24,12,36,1,25,13,37,2,26,14,38,3,27,15,39,4,28,16,40,5,29,17,41,6,30,18,42,7,31,19,43,8,32,20,44,9,33,21,45,10,34,22,46,11,35,23,47"
#LD_PRELOAD=../lib/libjemalloc.so ./`hostname`.vcasbst.out -i 50 -d 50 -k 200000 -rq 0 -rqsize 1000 -t 5000 -p -nrq 36 -nwork 36
