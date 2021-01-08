#!/bin/bash
# 
# File:   valgrind_run.sh
# Author: trbot
#
# Created on Aug 17, 2017, 6:26:03 PM
#

source ../config.mk
source ./supported.inc

nwork=`expr $maxthreads - 1`
args="-i 10 -d 10 -k 1000 -rq 2 -rqsize 100 -t 1000 -nrq 1 -nwork $nwork"
machine=`hostname`

highest=0
curr=0

if [ "$#" -eq "1" ]; then
#    echo "arg=$1"
    valgrind --fair-sched=yes --tool=memcheck --leak-check=yes --read-inline-info=yes --read-var-info=yes ./$machine.$1.out $args > leakcheck.$1.txt 2>&1
    ./valgrind_showerrors.sh $1
    ./valgrind_showleaks.sh $1
    exit
fi

make -j

for counting in 1 0 ; do
for ds in $datastructures ; do
for alg in $rqtechniques ; do
    check_ds_technique $ds $alg ; if [ "$?" -ne 0 ]; then continue ; fi
    
    if ((counting==1)) ; then
        ((highest = highest+1))
        continue
    else
        ((curr = curr+1))
    fi
    
    fname="leakcheck.${ds}.rq_${alg}.txt"
    echo "step $curr / $highest : $fname"
    valgrind --fair-sched=yes --tool=memcheck --leak-check=yes --read-inline-info=yes --read-var-info=yes ./$machine.${ds}.rq_${alg}.out $args > $fname 2>&1
done
done
done
