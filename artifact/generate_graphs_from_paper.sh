
# Figure 4a
python3 run_java_experiments_scalability.py 100000 3-2-95-0-0 [1,35,70,140] graphs/figure4a.png 5 5 300G

# Figure 4b
python3 run_java_experiments_scalability.py 100000 30-20-50-0-0 [1,35,70,140] graphs/figure4b.png 5 5 300G

# Figure 4c
python3 run_java_experiments_scalability.py 100000 30-20-50-1-1024 [1,35,70,140] graphs/figure4c.png 5 5 300G

# Figure 4d
python3 run_java_experiments_scalability.py 100000000 3-2-95-0-0 [1,35,70,140] graphs/figure4d.png 5 5 300G

# Figure 4e
python3 run_java_experiments_scalability.py 100000000 30-20-50-0-0 [1,35,70,140] graphs/figure4e.png 5 5 300G

# Figure 4f
python3 run_java_experiments_scalability.py 100000000 30-20-50-1-1024 [1,35,70,140] graphs/figure4f.png 5 5 300G

# Figures 4g and 4h
python3 run_java_experiments_rqsize.py 100000 36 36 [8,64,256,1024,8192,65536] graphs/figure4g4h.png 5 5 300G

# Figures 5a and 5b
python3 run_cpp_experiments_rqsize.py 100000 36 36 [8,64,256,1024,8192,65536] graphs/figure5a5b.png 5 5

# Figure 4j
python3 run_java_experiments_sorted.py [1,35,70,140] graphs/figure4j.png 10 5 300G

# Figure 4k
python3 run_java_experiments_overhead.py 140 100000 100000000 graphs/figure4k.png 5 5 300G

# Figure 4m
python3 run_java_experiments_multipoint.py 36 36 100000000 graphs/figure4m.png 5 5 300G

# Figure 5c
python3 run_cpp_experiments_memory_usage.py 100000 36 36 [8,64,256,1024,8192,65536] graphs/figure5c.png 5 5

# Figure 4i
python3 run_java_experiments_memory_usage.py 100000 36 36 [8,64,256,1024,8192,65536] graphs/figure4i.png 5 5 300G
