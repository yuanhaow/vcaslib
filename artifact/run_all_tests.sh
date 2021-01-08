
printf "Testing Java implementations...\n"
cd java/build
java -server -ea -Xms1G -Xmx1G -Xbootclasspath/a:'../lib/scala-library.jar:../lib/deuceAgent.jar' -jar experiments_instr.jar test
cd ../..
printf "Finished testing Java implementations\n"


printf "Testing C++ implementations...\n"
cd cpp/microbench
printf "\nTesting VcasBST:\n"
./`hostname`.vcasbst.out -i 50 -d 50 -k 200000 -rq 0 -rqsize 65536 -t 500 -p -nrq 36 -nwork 36 | grep -i validation
printf "\nTesting EpochBST:\n"
./`hostname`.bst.rq_lockfree.out -i 50 -d 50 -k 200000 -rq 0 -rqsize 65536 -t 500 -p -nrq 36 -nwork 36 | grep -i validation
printf "\nTesting BST:\n"
./`hostname`.bst.rq_unsafe.out -i 50 -d 50 -k 200000 -rq 0 -rqsize 65536 -t 500 -p -nrq 36 -nwork 36 | grep -i validation
cd ../..
printf "\nFinished testing C++ implementations\n"