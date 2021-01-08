#!/bin/bash
# 
# File:   errorchecks.sh
# Author: trbot
#
# Created on Aug 17, 2017, 6:54:47 PM
#

skiplines="HEAP SUMMARY|LEAK SUMMARY|definitely lost|still reachable|possibly lost|Reachable blocks|To see them|For counts of detected|to see where uninitialised values come|Memcheck|Copyright|Using Valgrind|Command:|ERROR SUMMARY|noted but unhandled ioctl|set address range perms|Thread"

if [ "$#" -eq "1" ]; then
#    echo "arg=$1"
    grep -E "== [^ ]" leakcheck.$1.txt | grep -vE "$skiplines"
else
    echo "Note: be sure to run valgrind_run.sh first"
    grep -E "== [^ ]" leakcheck.* | grep -vE "$skiplines"
fi
