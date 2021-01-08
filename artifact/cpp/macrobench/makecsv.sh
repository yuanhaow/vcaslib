#!/bin/bash

#first=1
#inprefix=data/rq_tpcc/step
#outfile=rq_tpcc.csv
#
#for f in ${inprefix}* ; do
#	#data/rq_rpcc/step10002.trial0.rundb_TPCC_ABTREE_RQ_HTM_RWLOCK.txt
#	ff=`echo $f | cut -d"/" -f3`
#	step=`echo $ff | cut -d"." -f1 | cut -d"p" -f2`
#	trial=`echo $ff | cut -d"." -f2 | cut -d"l" -f2`
#	workload=`echo $ff | cut -d"." -f3 | cut -d"_" -f2`
#	ds=`echo $ff | cut -d"." -f3 | cut -d"_" -f3`
#	rqalg=`echo $ff | cut -d"." -f3 | cut -d"_" -f5-`
#
#	cmd=`cat $f | head -2 | tail -1`
#	summary=`cat $f | grep "summary" | cut -d" " -f2-`
#
#	line="step=$step, trial=$trial, workload=$workload, ds=$ds, rqalg=$rqalg, $summary, filename=$f"
#	#echo LINE: $line
#	if [ $first -eq 1 ]; then
#		first=0
#		header=`echo $line | tr "," "\n" | tr -d " " | cut -d"=" -f1 | tr "\n" "," | sed "s/,$//"`,cmd
#		echo $header > $outfile
#	fi
#	csvline=`echo $line | tr "," "\n" | tr -d " " | cut -d"=" -f2 | tr "\n" "," | sed "s/,$//"`,$cmd
#	echo $csvline >> $outfile
#done

cat data/rq_tpcc/summary.txt | tail -1 | tr "," "\n" | tr -d " " | cut -d"=" -f1 | tr "\n" "," ; echo ; cat data/rq_tpcc/summary.txt | grep "datastructure" | while read line ; do echo $line | tr "," "\n" | tr -d " " | cut -d"=" -f2 | tr "\n" "," ; echo ; done
