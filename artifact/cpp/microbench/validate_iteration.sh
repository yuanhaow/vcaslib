#!/bin/bash
# 
# File:   validate_iterations.sh
# Author: trbot
#
# Created on Nov 27, 2017, 8:44:33 PM
#

source ../config.mk
source ./supported.inc

nwork=`expr $maxthreads - 1`
args="-i 50 -d 50 -k 10000 -rq 0 -rqsize 10000 -t 1000 -p -nrq 1 -nwork $nwork"
machine=`hostname`

highest=0
curr=0

mkdir validation_output

## recompile with validation enabled
echo "Compiling with iteration validation..."
if [ "$#" -ne "1" ]; then
    make -j xargs="-DUSE_RQ_DEBUGGING -DRQ_VALIDATION" > /dev/null
fi
echo "Finished compiling with iteration validation."

for counting in 1 0 ; do
for ((trial=0;trial<1000;++trial)) ; do
for ds in $datastructures ; do
for alg in $rqtechniques ; do
    if [ "$alg" == "rlu" ] || [ "$alg" == "snapcollector" ] || [ "$alg" == "unsafe" ] ; then continue ; fi

    if ((counting==1)) ; then
        ((highest = highest+1))
        continue
    else
        ((curr = curr+1))
    fi

    fname="validation_output/validate_iteration.${ds}.rq_${alg}.$trial.txt"
    cmd="./${machine}.${ds}.rq_${alg}.out $args"
    echo "step $curr / $highest : $fname : LD_PRELOAD=../lib/libjemalloc.so $cmd"
    LD_PRELOAD=../lib/libjemalloc.so $cmd > $fname
    cat $fname | grep "RQ VALIDATION ERROR"
    #cat $fname | grep "RQ Validation OK"
done
done
done
done

grep "RQ VALIDATION ERROR" validation_output/validate_iteration*.txt

## recompile with validation disabled
echo "Recompiling without iteration validation..."
if [ "$#" -ne "1" ]; then
    make -j > /dev/null
fi
echo "Finished recompiling without iteration validation."

