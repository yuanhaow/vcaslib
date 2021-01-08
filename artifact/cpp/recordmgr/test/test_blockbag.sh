#!/bin/bash
# 
# File:   test_blockbag.sh
# Author: trbot
#
# Created on Apr 24, 2017, 3:41:41 PM
#

for ((x=0;x<=1024;++x)) ; do
    y=`./test_blockbag.out $x | wc -w`
    if [ "$x" -ne "$y" ]; then
        echo "ERROR: x=$x does not match y=$y"
        exit 1
    fi
done
echo "All tests passed."
