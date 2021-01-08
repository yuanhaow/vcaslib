#!/bin/bash
#
# Run script for the micro benchmark experiments.
#
# Author: Trevor Brown

source ../config.mk

trials=5

cols="%6s %12s %12s %12s %8s %6s %6s %8s %6s %6s %8s %12s %12s %12s %12s"
headers="step machine ds alg k u rq rqsize nrq nwork trial throughput rqs updates finds"
machine=`hostname`

echo "Generating 'experiment_list.txt' according to settings in '/config.mk'..."
./experiment_list_generate.sh

skip_steps_before=0
skip_steps_after=1000000

outdir=data
fsummary=$outdir/summary.txt

rm -r -f $outdir.old
mv -f $outdir $outdir.old
mkdir $outdir

rm -f warnings.txt

if [ "$#" -eq "1" ] ; then
    testingmode=1
    millis=1
    prefill_and_time="-t ${millis}"
else
    testingmode=0
    millis=3000
    prefill_and_time="-p -t ${millis}"
fi

cnt2=`cat experiment_list.txt | wc -l`
cnt2=`expr $cnt2 \* $trials`
echo "Performing $cnt2 trials..."

secs=`expr $millis / 1000`
estimated_secs=`expr $cnt2 \* $secs`
estimated_hours=`expr $estimated_secs / 3600`
estimated_mins=`expr $estimated_secs / 60`
estimated_mins=`expr $estimated_mins % 60`
echo "Estimated running time: ${estimated_hours}h${estimated_mins}m" > $fsummary

cnt1=10000
cnt2=`expr $cnt2 + 10000`

printf "${cols}\n" ${headers} >> $fsummary
cat $fsummary
    
while read u rq rqsize k nrq nwork ds alg ; do
    for ((trial=0;trial<$trials;++trial)) ; do
        cnt1=`expr $cnt1 + 1`
        if ((cnt1 < skip_steps_before)); then continue; fi
        if ((cnt1 > skip_steps_after)); then continue; fi

        fname="$outdir/step$cnt1.$machine.${ds}.${alg}.k$k.u$u.rq$rq.rqsize$rqsize.nrq$nrq.nwork$nwork.trial$trial.out"
        #echo "FNAME=$fname"
        cmd="./${machine}.${ds}.rq_${alg}.out -i $u -d $u -k $k -rq $rq -rqsize $rqsize ${prefill_and_time} -nrq $nrq -nwork $nwork ${pinning_policy}"
        echo "env LD_PRELOAD=../lib/libjemalloc.so TREE_MALLOC=../lib/libjemalloc.so $cmd" > $fname
        env LD_PRELOAD=../lib/libjemalloc.so TREE_MALLOC=../lib/libjemalloc.so $cmd >> $fname
        printf "${cols}" $cnt1 $machine $ds $alg $k $u $rq $rqsize $nrq $nwork $trial "`cat $fname | grep 'total throughput' | cut -d':' -f2`" "`cat $fname | grep 'total rq' | cut -d':' -f2`" "`cat $fname | grep 'total updates' | cut -d':' -f2`" "`cat $fname | grep 'total find' | cut -d':' -f2`" >> $fsummary
        tail -1 $fsummary
        echo
        printf "%120s          %s\n" "$fname" "`head -1 $fname`" >> $fsummary

        throughput=`cat $fname | grep "total throughput" | cut -d":" -f2 | tr -d " "`
        if [ "$throughput" == "" ] || [ "$throughput" -le "0" ] ; then echo "WARNING: thoughput $throughput in file $fname" >> warnings.txt ; cat warnings.txt | tail -1 ; fi
    done
done < experiment_list.txt

if [ "`cat warnings.txt | wc -l`" -ne 0 ]; then
    echo "NOTE: THERE WERE WARNINGS. PRINTING THEM..."
    cat warnings.txt
fi

mv results.db results.db.old
python create_db.py
