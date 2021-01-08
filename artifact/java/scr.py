#!/usr/bin/python3
import csv, time
from os import system
from sys import argv

### THIS IS A HACKY SCRIPT THAT NEEDS TO BE REWRITTEN ###

if len(argv) < 5:
	print('Usage: ' + argv[0] + ' exp_type num_of_x_values num_of_algorithms exp_name')
	exit()

# Parse arguments
EXP_TYPE = str(argv[1])
NUM_OF_X_VALUES = int(argv[2])
NUM_OF_ALGORITHMS = int(argv[3])
EXP_NAME = str(argv[4])

# Store some useful spreadsheet columns
ALG_NAME_COL = 0
TRIAL_NUMBER_COL = 1
NUM_OF_THREADS_COL = 2
OPS_COL = 3
TIME_COL = 7
INS_TRUE_COL = 8
RQ_SIZE_COL = 18
THROUGHPUT_COL = 20

NUM_OF_TRIALS = 10
NUM_OF_DELETED_TRIALS = 5
NUM_OF_USEFUL_TRIALS = NUM_OF_TRIALS - NUM_OF_DELETED_TRIALS
DATA_POINTS = NUM_OF_X_VALUES * NUM_OF_ALGORITHMS
ORIG_LAST_ROW = NUM_OF_TRIALS * DATA_POINTS
LAST_ROW = NUM_OF_USEFUL_TRIALS * DATA_POINTS
RESULTS_ROW = LAST_ROW + 4

# Stores the total ops for each experiment (data point), i.e. for every algorithm and number of threads
totalOps = []
# Stores the Updates and Find throughput for each experiment
totalThroughputUF = []
# Stores the RangeQuery throughput for each experiment
totalThroughputRQ = []

with open('data.csv') as f, open('data_temp.csv','w+') as f2:
	r = csv.reader(f)
	w = csv.writer(f2)
	lines = list(r)

	# Compute total ops for each experiment
	for i in range(1,ORIG_LAST_ROW,NUM_OF_TRIALS):
		opsSum = 0
		for j in range(NUM_OF_TRIALS):
			opsSum += int(lines[i+j][OPS_COL])
		totalOps.append(opsSum)

	assert (len(totalOps) == DATA_POINTS)

	# Throw away the first NUM_OF_DELETED_TRIALS trials
	w.writerow(lines[0])
	for i in range(1,ORIG_LAST_ROW,NUM_OF_TRIALS):
		for j in range(NUM_OF_DELETED_TRIALS, NUM_OF_TRIALS):
			w.writerow(lines[i+j])


with open('data_temp.csv') as f, open('data_fin.csv','w') as f2:
	r = csv.reader(f)
	w = csv.writer(f2)
	lines = list(r)
	numOfColumns = len(lines[0])

	# Expand spreadsheet
	emptyLine = [' ']*numOfColumns
	for i in range(40):
		lines.append(emptyLine.copy())

	# Write total ops for each experiment
	lines[RESULTS_ROW-1][OPS_COL-1] = '#Total ops'
	for i in range(DATA_POINTS):
		k = int(i/NUM_OF_X_VALUES)
		lines[RESULTS_ROW-1][OPS_COL+k] = lines[(i+1)*NUM_OF_USEFUL_TRIALS][ALG_NAME_COL]
		lines[RESULTS_ROW+i%NUM_OF_X_VALUES][OPS_COL-1] = lines[(i+1)*NUM_OF_USEFUL_TRIALS][NUM_OF_THREADS_COL]
		lines[RESULTS_ROW+i%NUM_OF_X_VALUES][OPS_COL+k] = totalOps[i]

	# Compute throughput averages in millions
	totalThroughputUFTrials = []
	totalThroughputRQTrials = []
	for i in range(1,LAST_ROW+1):
		ufOps = 0
		for j in range(6):
			ufOps += int(lines[i][INS_TRUE_COL+j])
		rqOps = int(lines[i][INS_TRUE_COL+6]) + int(lines[i][INS_TRUE_COL+7])
		totalThroughputUFTrials.append(ufOps/float(lines[i][TIME_COL]))
		totalThroughputRQTrials.append(rqOps/float(lines[i][TIME_COL]))

	for i in range(1,LAST_ROW+1,NUM_OF_USEFUL_TRIALS):
		throughputUFSum = 0
		throughputRQSum = 0
		for j in range(NUM_OF_USEFUL_TRIALS):
			throughputUFSum += totalThroughputUFTrials[i-1+j]
			throughputRQSum += totalThroughputRQTrials[i-1+j]
		totalThroughputUF.append(throughputUFSum/(NUM_OF_USEFUL_TRIALS*1000000))
		totalThroughputRQ.append(throughputRQSum/(NUM_OF_USEFUL_TRIALS*1000000))

	lines[RESULTS_ROW-1][THROUGHPUT_COL-1] = '#Throughput (million ops/sec)'
	for i in range(1,LAST_ROW,NUM_OF_USEFUL_TRIALS):
		k = int(i/(NUM_OF_X_VALUES*NUM_OF_USEFUL_TRIALS))
		lines[RESULTS_ROW-1][THROUGHPUT_COL+k] = lines[i][ALG_NAME_COL]
		lines[RESULTS_ROW+int((i-1)/NUM_OF_USEFUL_TRIALS)%NUM_OF_X_VALUES][THROUGHPUT_COL-1] = lines[i][NUM_OF_THREADS_COL]
		throughputSum = 0
		for j in range(NUM_OF_USEFUL_TRIALS):
			throughputSum += int(lines[i+j][THROUGHPUT_COL])
		lines[RESULTS_ROW+int((i-1)/NUM_OF_USEFUL_TRIALS)%NUM_OF_X_VALUES][THROUGHPUT_COL+k] = round(throughputSum/(NUM_OF_USEFUL_TRIALS*1000000),2)

	SECOND_RESULTS_ROW = RESULTS_ROW + NUM_OF_X_VALUES + 2
	lines[SECOND_RESULTS_ROW-1][THROUGHPUT_COL-1] = '#Throughput of Updaters (million ops/sec)'
	for i in range(1,LAST_ROW,NUM_OF_USEFUL_TRIALS):
		k = int(i/(NUM_OF_X_VALUES*NUM_OF_USEFUL_TRIALS))
		lines[SECOND_RESULTS_ROW-1][THROUGHPUT_COL+k] = lines[i][ALG_NAME_COL]
		lines[SECOND_RESULTS_ROW+int((i-1)/NUM_OF_USEFUL_TRIALS)%NUM_OF_X_VALUES][THROUGHPUT_COL-1] = lines[i][RQ_SIZE_COL]
		lines[SECOND_RESULTS_ROW+int((i-1)/NUM_OF_USEFUL_TRIALS)%NUM_OF_X_VALUES][THROUGHPUT_COL+k] = totalThroughputUF[int(i/NUM_OF_USEFUL_TRIALS)]

	THIRD_RESULTS_ROW = SECOND_RESULTS_ROW + NUM_OF_X_VALUES + 2
	lines[THIRD_RESULTS_ROW-1][THROUGHPUT_COL-1] = '#Throughput of RQers (million ops/sec)'
	for i in range(1,LAST_ROW,NUM_OF_USEFUL_TRIALS):
		k = int(i/(NUM_OF_X_VALUES*NUM_OF_USEFUL_TRIALS))
		lines[THIRD_RESULTS_ROW-1][THROUGHPUT_COL+k] = lines[i][ALG_NAME_COL]
		lines[THIRD_RESULTS_ROW+int((i-1)/NUM_OF_USEFUL_TRIALS)%NUM_OF_X_VALUES][THROUGHPUT_COL-1] = lines[i][RQ_SIZE_COL]
		lines[THIRD_RESULTS_ROW+int((i-1)/NUM_OF_USEFUL_TRIALS)%NUM_OF_X_VALUES][THROUGHPUT_COL+k] = totalThroughputRQ[int(i/NUM_OF_USEFUL_TRIALS)]

	# Write to file
	w.writerows(lines)

# Cleanup
system('rm data_temp.csv')
system('rm data.csv')

# Archive
DATETIME = time.strftime('%Y-%m-%d-%H..%M')
system('mv data_fin.csv experiments/exp' + EXP_TYPE + '_' + DATETIME + '_' + EXP_NAME + '.csv')
