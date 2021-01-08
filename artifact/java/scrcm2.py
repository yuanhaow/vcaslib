#!/usr/bin/python3
import csv, time
from os import system, listdir
from sys import argv

if len(argv) < 3:
	print('Usage: ' + argv[0] + ' keys_mix num_of_x_values')
	exit()

EXP_NAME = str(argv[1])
NUM_OF_X_VALUES = str(argv[2])

files = []
for file in listdir('build'):
	if file.startswith('perflog') and file.endswith(EXP_NAME + '.txt'):
		files.append(file)
numOfRows = int(NUM_OF_X_VALUES)+1
numOfCols = len(files)+1
algs = []
algsToFiles = {}
filesToAlgs = {}
for i,file in enumerate(files):
	alg = files[i].split('-')[1]
	algs.append(alg)
	algsToFiles[alg] = file
	filesToAlgs[file] = alg
algs.sort()

with open('perflog_temp.tsv','w') as f2:

	# Expand spreadsheet
	w = csv.writer(f2, delimiter='\t')
	emptyLine = [' ']*numOfCols
	for i in range(numOfRows):
		w.writerow(emptyLine.copy())

with open('perflog_temp.tsv','r') as f2:

	# Load empty spreadsheet
	r2 = csv.reader(f2, delimiter='\t')
	lines2 = list(r2)

with open('perflog_temp.tsv','w') as f2:

	# Write title
	w = csv.writer(f2, delimiter='\t')
	lines2[0][0] = '#Total cache misses'
	for j,alg in enumerate(algs):
		lines2[0][j+1] = alg

	# Get threads
	threads = []
	with open('./build/'+files[0]) as f:
		lines = f.readlines()
		for line in lines:
			if line.startswith(' Performance counter stats'):
				alg = filesToAlgs[files[0]]
				lineList = line.split(' ')
				if alg not in lineList:
					alg = alg.split('_')[0]
				threads.append(lineList[lineList.index(alg)-3])

	# Create threads column
	for i in range(numOfRows-1):
		lines2[i+1][0] = threads[i]

	# Write columns
	for j,alg in enumerate(algs):
		with open('./build/'+algsToFiles[alg]) as f:
			lines = f.readlines()
			outIndex = 1
			for line in lines:
				if line.strip().endswith('cache-misses:u'):
					lines2[outIndex][j+1] = line.strip().split(' ')[0]
					outIndex += 1
	w.writerows(lines2)

# Archive
DATETIME = time.strftime('%Y-%m-%d-%H..%M')
system('mv perflog_temp.tsv experiments/expcm' + '_' + DATETIME + '_' + EXP_NAME + '.tsv')
