#!/bin/bash
# 
# Run script for the macro benchmark (TPC-C benchmarks)
#
# Author: Trevor Brown
#
# Created on Jul 18, 2017, 10:56:28 PM
#

source ../config.mk
source ../microbench/supported.inc

ntrials=5
machine=`hostname`

outpath=data/rq_tpcc
fsummary=$outpath/summary.txt

mv $outpath $outpath.old
mkdir -p $outpath

cnt1=10000
cnt2=10000

## fix any \r line endings, which mess up the DBMS schema in TPC-C
dos2unix benchmarks/*.txt

for counting in 1 0 ; do
	if (($counting==0)); then
		echo "Total trials: $cnt1 ... $cnt2"
	fi
    
    for ((trial=0; trial < $ntrials; ++trial)); do
        for exepath in `ls ./bin/$machine/rundb*`; do
            if [ "${htm_error}" -ne "0" ]; then
                if [[ $exepath == *"HTM"* ]]; then continue ; fi
            fi

            if (($counting==1)); then
                cnt2=`expr $cnt2 + 1`
                if ((($cnt2 % 100) == 0)); then echo "Counting trials: $cnt2" ; fi
            else
                cnt1=`expr $cnt1 + 1`
                exeonly=`echo $exepath | cut -d"/" -f4`
                fname=$outpath/step$cnt1.trial$trial.$exeonly.txt
                workload=`echo $exeonly | cut -d"_" -f2 | cut -d"." -f1`
                datastructure=`echo $exeonly | cut -d"_" -f3 | cut -d"." -f1`
                rqalg=`echo $exeonly | cut -d"_" -f4- | cut -d"." -f1`

                #echo "RUNNING step $cnt1 / $cnt2 : trial $trial of $exeonly > $fname"
                
                echo -n "step=$cnt1, trial=$trial, workload=$workload, datastructure=$datastructure, rqalg=$rqalg," >> $fsummary

                args="-t$maxthreads -n$maxthreads"
                cmd="env LD_PRELOAD=../lib/libjemalloc.so TREE_MALLOC=../lib/libjemalloc.so $exepath $args"
                echo $fname > $fname
                echo $cmd >> $fname
                $cmd >> $fname
                cat $fname | grep "summary" | cut -d"]" -f2- >> $fsummary
                tail -1 $fsummary
            fi
        done
    done
done

./makecsv.sh
