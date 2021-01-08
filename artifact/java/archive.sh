#!/bin/bash
archiveName=`date +"%Y-%m-%d-%H..%M"`
archivePath="./results"
mkdir -p "$archivePath"
cat ./build/data-*.csv > "$archivePath/$archiveName".csv
#cp ./build/*.csv ./build/*.txt $archivePath
