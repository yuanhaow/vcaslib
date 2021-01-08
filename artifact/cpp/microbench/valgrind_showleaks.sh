#!/bin/sh
# 
# File:   valgrind_showleaks.sh
# Author: trbot
#
# Created on Aug 17, 2017, 6:58:17 PM
#

skiplines="0 bytes"

if [ "$#" -eq "1" ]; then
#    echo "arg=$1"
    grep -E "definitely|possibly" leakcheck.$1.txt | grep -v "$skiplines"
else
    echo "Note: be sure to run valgrind_run.sh first"
    grep -E "definitely|possibly" leakcheck.* | grep -v "$skiplines"
fi
