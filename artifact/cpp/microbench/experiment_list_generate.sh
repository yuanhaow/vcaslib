#!/bin/bash

source ../config.mk
rm -f experiment_list.txt > /dev/null

source ./supported.inc

ksizes="10000 100000 1000000"

echo "Preparing experiment 1: IMPACT OF INCREASING UPDATE THREAD COUNT ON RQs (rq size 100)"
rq=0
rqsize=100
nrq=1
for u in 10 50 ; do
for k in $ksizes ; do
for ds in $datastructures ; do
for alg in $rqtechniques ; do
    remaining_threads="`expr $maxthreads - $nrq`"
    nworks="0"
    if [ "$threadincrement" -ne "1" ]; then nworks="$nworks 1" ; fi
    for ((x=$threadincrement; x<${remaining_threads}; x+=$threadincrement)); do nworks="$nworks $x" ; done
    nworks="$nworks $remaining_threads"
    for nwork in $nworks ; do
        check_ds_technique $ds $alg ; if [ "$?" -ne 0 ]; then continue ; fi
        check_ds_size $ds $k ; if [ "$?" -ne 0 ]; then continue ; fi
        echo $u $rq $rqsize $k $nrq $nwork $ds $alg >> experiment_list.txt
    done
done
done
done
done

echo "Preparing experiment 2: IMPACT OF INCREASING RQ THREAD COUNT ON UPDATE THREADS (rq size 100)"
rq=0
rqsize=100
u=50
nwork=`expr $maxthreads - $maxrqthreads`
for k in $ksizes ; do
for ds in $datastructures ; do
for alg in $rqtechniques ; do
for ((nrq=0;nrq<=$maxrqthreads;++nrq)); do
    check_ds_technique $ds $alg ; if [ "$?" -ne 0 ]; then continue ; fi
    check_ds_size $ds $k ; if [ "$?" -ne 0 ]; then continue ; fi
    echo $u $rq $rqsize $k $nrq $nwork $ds $alg >> experiment_list.txt
done
done
done
done

echo "Preparing experiment 3: SMALL RQ (k/1000) VS BIG RQ (k/10) (VS ITERATION (rqsize=k); for latency graphs, and for comparison with iterators)"
u=10
rq=0
nrq=1
nwork="`expr $maxthreads - $nrq`"
for k in $ksizes ; do
    kdiv10=`expr $k / 10`
    kdiv100=`expr $k / 100`
    kdiv1000=`expr $k / 1000`
    for rqsize in $k $kdiv10 $kdiv100 $kdiv1000 ; do
    for ds in $datastructures ; do
    for alg in $rqtechniques ; do
        check_ds_technique $ds $alg ; if [ "$?" -ne 0 ]; then continue ; fi
        check_ds_size $ds $k ; if [ "$?" -ne 0 ]; then continue ; fi
        echo $u $rq $rqsize $k $nrq $nwork $ds $alg >> experiment_list.txt
    done
    done
    done
done

echo "Preparing experiment 4: MIXED WORKLOAD WHERE ALL THREADS DO UPDATES AND RQs (rqsize=100)"
u=10
rq=2
rqsize=100
nrq=0
nwork="`expr $maxthreads - $nrq`"
for k in $ksizes ; do
for ds in $datastructures ; do
for alg in $rqtechniques ; do
    nwork="`expr $maxthreads - $nrq`"
    check_ds_technique $ds $alg ; if [ "$?" -ne 0 ]; then continue ; fi
    check_ds_size $ds $k ; if [ "$?" -ne 0 ]; then continue ; fi
    echo $u $rq $rqsize $k $nrq $nwork $ds $alg >> experiment_list.txt
done
done
done

echo "Total experiment lines generated:" `cat experiment_list.txt | wc -l`
