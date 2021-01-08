#!/bin/bash

if [ "$#" != "3" ]; then
        echo "USAGE: $0 exp_serial_number num_of_x_values num_of_algs"
        exit 1
fi
dataPoints=$(($2*$3))
startSeqIndex=$((($1-1)*dataPoints+1))
endSeqIndex=$(($1*dataPoints))
seqCommand="`seq $startSeqIndex $endSeqIndex | xargs $xargsArguments -n1 printf './build/data-trials%1.f.csv '`"
./merge $seqCommand
