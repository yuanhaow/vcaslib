
g++ ../common/test_htm_support.cpp -o ../common/test_htm_support > /dev/null
../common/test_htm_support &> /dev/null
htm_error=$?

datastructures="abtree bst lflist lazylist citrus skiplistlock"
rqtechniques="snapcollector lockfree rwlock unsafe rlu"

if [ "$htm_error" -eq "0" ]; then
    echo "Note: HTM support detected; including HTM experiments."
    rqtechniques="$rqtechniques htm_rwlock"
else
    echo "Warning: HTM does not appear to be supported; skipping HTM experiments."
fi

check_ds_technique() {
    ## args: ds alg
    if [ "$2" == "snapcollector" ] && [ "$1" != "lflist" ] && [ "$1" != "skiplistlock" ] ; then return -1 ; fi
    if [ "$2" == "rlu" ] && [ "$1" != "lazylist" ] && [ "$1" != "citrus" ] ; then return -1 ; fi
    return 0
}
export -f check_ds_technique

check_ds_size() {
    ## args: ds k
    if [ "$2" == "1000000" ] && [ "$1" != "abtree" ] ; then return -1 ; fi
    if [ "$2" == "100000" ] && [ "$1" != "bst" ] && [ "$1" != "citrus" ] && [ "$1" != "skiplistlock" ]  ; then return -1 ; fi
    if [ "$2" == "10000" ] && [ "$1" != "lflist" ] && [ "$1" != "lazylist" ] ; then return -1 ; fi
    return 0
}
export -f check_ds_size
