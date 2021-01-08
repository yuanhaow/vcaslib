#!/usr/bin/python3
import csv, time
from os import system, listdir
from sys import argv

if len(argv) < 2:
	print('Usage: ' + argv[0] + ' keys_mix')
	exit()

EXP_NAME = str(argv[1])

filesToNumOfLines = {}
for file in listdir('build'):
	if file.startswith('memrec') and file.endswith(EXP_NAME + '.csv'):
		with open('./build/'+file) as f:
			r = csv.reader(f)
			lines = list(r)
			filesToNumOfLines[file] = len(lines)
maxLines = max(filesToNumOfLines.values())
numOfFiles = len(filesToNumOfLines)
numOfRows = maxLines+1
numOfCols = numOfFiles+1
files = list(filesToNumOfLines.keys())
algs = []
algsToFiles = {}
for i,file in enumerate(files):
	alg = files[i].split('-')[1]
	algs.append(alg)
	algsToFiles[alg] = file
algs.sort()

with open('memrec_temp.tsv','w') as f2:

	# Expand spreadsheet
	w = csv.writer(f2)
	emptyLine = [' ']*numOfCols
	for i in range(numOfRows):
		w.writerow(emptyLine.copy())

with open('memrec_temp.tsv','r') as f2:

	# Load empty spreadsheet
	r2 = csv.reader(f2)
	lines2 = list(r2)

with open('memrec_temp.tsv','w') as f2:

	# Write title
	w = csv.writer(f2)
	lines2[0][0] = '#TIME (sec)'
	for j,alg in enumerate(algs):
		lines2[0][j+1] = alg+' (GB)'

	# Create time column
	for i in range(numOfRows-1):
		lines2[i+1][0] = i

	# Write columns
	for j,alg in enumerate(algs):
		with open('./build/'+algsToFiles[alg]) as f:
			r = csv.reader(f)
			lines = list(r)
			outIndex = 1

			for i in range(1,len(lines)):
				# Skip empty lines
				if lines[i][1] == '':
					continue
				# Convert in GB and write memory value
				lines2[outIndex][j+1] = round(int(lines[i][1])/1000000,2)
				outIndex += 1

			# Pad with zeroes
			for i in range(outIndex,numOfRows):
				lines2[outIndex][j+1] = 0
				outIndex += 1
	w.writerows(lines2)

# Archive
DATETIME = time.strftime('%Y-%m-%d-%H..%M')
system('mv memrec_temp.tsv experiments/expmem' + '_' + DATETIME + '_' + EXP_NAME + '.tsv')
