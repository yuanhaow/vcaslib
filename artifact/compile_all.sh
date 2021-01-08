
echo "Compiling Java benchmarks..."
cd java/
./compile
cd ..
echo "Finished compiling Java benchmarks"

echo "Compiling C++ benchmarks..."
cd cpp/microbench
make vcasbst
make bst.rq_unsafe
make bst.rq_lockfree
echo "Finished compiling C++ benchmarks"