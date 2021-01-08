import csv
import matplotlib as mpl
# mpl.use('Agg')
mpl.rcParams['grid.linestyle'] = ":"
mpl.rcParams.update({'font.size': 15})
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle
import numpy as np
import os
import statistics as st

columns = ['throughput', 'name', 'nthreads', 'ratio', 'maxkey', 'rqsize', 'time', 'ninstrue', 'ninsfalse', 'ndeltrue', 'ndelfalse', 'nrqtrue', 'nrqfalse', 'merged-experiment']

names = { 'BSTBST': 'BST',
          'BST': 'BST',
          'LFCA': 'LFCA',
          'KIWI': 'KIWI',
          'ChromaticBatchBST64': 'CT-64',
          'VcasChromaticBatchBSTGC64': 'VcasCT-64',
          'BPBST64': 'PNB-BST',
          'BatchBST64': 'BST-64',
          'VcasBatchBSTGC64': 'VcasBST-64',
          'KSTRQ' : 'KST',
          'SnapTree' : 'SnapTree',

          'vcasbst' : 'VcasBST',
          'bst.rq_unsafe' : 'BST (non-atomic RQs)',
          'bst.rq_lockfree' : 'EpochBST',}
colors = {
          'BPBST64': 'C8',
          'LFCA': 'C2', 
          'KIWI': 'C1',
          'KSTRQ' : 'C4',
          'SnapTree' : 'C9',
          'ChromaticBatchBST64': 'C6',
          'VcasChromaticBatchBSTGC64': 'C3',
          'BatchBST64': 'C0',
          'VcasBatchBSTGC64': 'C5',

          'vcasbst': 'C0',
          'bst.rq_unsafe': 'C1', 
          'bst.rq_lockfree': 'C2'               
}
linestyles = {
              'BPBST64': '--',
              'LFCA': '--', 
              'KIWI': '-',
              'KSTRQ' : '--',
              'SnapTree' : '-',
              'ChromaticBatchBST64': '-',
              'VcasChromaticBatchBSTGC64': '-',
              'BatchBST64': '--',
              'VcasBatchBSTGC64': '--',       

              'vcasbst': '--',
              'bst.rq_unsafe': '--', 
              'bst.rq_lockfree': '--',   
}
markers =    {
              'BPBST64': 'o',
              'LFCA': 'v', 
              'KIWI': '^',
              'KSTRQ' : 's',
              'SnapTree' : 'x',
              'ChromaticBatchBST64': '|',
              'VcasChromaticBatchBSTGC64': 'D',
              'BatchBST64': '<',
              'VcasBatchBSTGC64': '>',    

              'vcasbst': 'o',
              'bst.rq_unsafe': 'x', 
              'bst.rq_lockfree': '>',       
}

algList = ['KIWI', 'SnapTree', 'KSTRQ', 'BPBST64', 'LFCA', 'VcasBatchBSTGC64', 'VcasChromaticBatchBSTGC64']

threadList = [1, 36, 72, 140]
RQsizes = [8, 64, 256, 1024, 8192, 65536]
# threadList = [1, 8, 32, 48, 64]
# legendPrinted = False

benchmarkList = ['166666k-3i-2d-0rq-10s', '166666k-30i-20d-0rq-10s', '166666k-30i-20d-1rq-1024s', '166666666k-3i-2d-0rq-10s', '166666666k-30i-20d-0rq-10s', '166666666k-30i-20d-1rq-1024s']

benchmarkRQ = ['72t-200000k', '36t-200000k', '72t-200000000k', '36t-200000000k']

def toStringBench(maxkey, ratio, rqsize):
  return str(maxkey) + 'k-' + ratio + '-' + str(rqsize) + 's'

def toRatio(insert, delete, rq):
  return str(insert) + 'i-' + str(delete) + 'd-' + str(rq) + 'rq'

def toString(algname, nthreads, ratio, maxkey, rqsize):
  return algname + '-' + str(nthreads) + 't-' + str(maxkey) + 'k-' + ratio + '-' + str(rqsize) + 's' 

def toString2(algname, nthreads, insert, delete, rq, maxkey, rqsize):
  return toString(algname, nthreads, toRatio(insert, delete, rq), maxkey, rqsize)

def toString3(algname, nthreads, benchmark):
  return algname + '-' + str(nthreads) + 't-' + benchmark

def toString4(algname, threadkey, ratio):
  return algname + '-' + threadkey + '-' + ratio

def toStringRQ(algname, nthreads, maxkey, rqsize):
  return "RQ-" + algname + '-' + str(nthreads) + 't-' + str(maxkey) + 'k-' + str(rqsize) + 's'

def toStringRQ3(algname, rqsize, benchmark, optype):
  return "RQ-" + algname + '-' + benchmark + '-' + str(rqsize) + 's' + '-' + optype

def toStringRQcomplex3(algname, benchmark, querytype, optype):
  if(querytype == 'range'):
    return toStringRQ3(algname, rqsize, benchmark, optype)
  return "RQ-" + algname + '-' + benchmark + '-' + querytype + '-' + optype

def div1Mlist(numlist):
  newList = []
  for num in numlist:
    newList.append(div1M(num))
  return newList

def div1M(num):
  return round(num/1000000.0, 3)

def avg(numlist):
  # if len(numlist) == 1:
  #   return numlist[0]
  total = 0.0
  length = 0
  for num in numlist:
    length=length+1
    total += float(num)
  if length > 0:
    return 1.0*total/length
  else:
    return -1;

def readCppResultsFileMemory(filename, results, stddev, rqsizes, algs):
  resultsRaw = {}
  alg = ""
  rqsize = ""
  allocated = 0
  deallocated = 0
  nodesize = 0

  # read csv into resultsRaw
  file = open(filename, 'r')
  for line in file.readlines():
    if line.find('vcasbst') != -1:
      alg = 'vcasbst'
    elif line.find('bst.rq_unsafe') != -1:
      alg = 'bst.rq_unsafe'
    elif line.find('bst.rq_lockfree') != -1:
      alg = 'bst.rq_lockfree'
    elif line.find('RQSIZE') != -1:
      rqsize = int(line.split("=")[1])
      if rqsize not in rqsizes:
        rqsizes.append(rqsize)
    elif 'allocated   :' in line:
      allocated = int(line.split()[2])
    elif 'deallocated :' in line:
      deallocated = int(line.split()[2])
      if alg not in algs:
        algs.append(alg)
      key = alg + " " + str(rqsize)
      memory = (allocated - deallocated)*nodesize
      if key not in resultsRaw:
        resultsRaw[key] = []
      resultsRaw[key].append(memory)
    elif 'recmgr status for objects of size' in line:
      nodesize = int(line.split()[6])

  for key in resultsRaw:
    resultsTmp = resultsRaw[key]
    # print(results)
    for i in range(len(resultsTmp)):
      resultsTmp[i] = resultsTmp[i]/1000000.0
    avgResult = avg(resultsTmp)
    results[key] = avgResult
    stddev[key] = st.pstdev(resultsTmp)
    # print(avgResult)

def readCppResultsFile(filename, throughput, stddev, threads, ratios, maxkeys, rqsizes, algs):
  throughputRaw = {}
  threads.append("")
  ratios.append("")
  maxkeys.append("")
  alg = ""
  rqsize = ""

  # read csv into throughputRaw
  file = open(filename, 'r')
  for line in file.readlines():
    if line.find('vcasbst') != -1:
      alg = 'vcasbst'
    elif line.find('bst.rq_unsafe') != -1:
      alg = 'bst.rq_unsafe'
    elif line.find('bst.rq_lockfree') != -1:
      alg = 'bst.rq_lockfree'
    elif line.find('RQSIZE') != -1:
      rqsize = int(line.split("=")[1])
      if rqsize not in rqsizes:
        rqsizes.append(rqsize)
    elif line.find('update throughput') != -1:
      if alg not in algs:
        algs.append(alg)
      key = toStringRQ(alg, threads[0], maxkeys[0], rqsize)+'-updates'
      # print(key)
      if key not in throughputRaw:
        throughputRaw[key] = []
      throughputRaw[key].append(int(line.split(' ')[-1]))
    elif line.find('rq throughput') != -1:
      if alg not in algs:
        algs.append(alg)
      key = toStringRQ(alg, threads[0], maxkeys[0], rqsize)+'-rqs'
      # print(key)
      if key not in throughputRaw:
        throughputRaw[key] = []
      throughputRaw[key].append(int(line.split(' ')[-1]))

  # print(throughputRaw)
  # Average througputRaw into throughput

  for key in throughputRaw:
    results = throughputRaw[key]
    # print(results)
    for i in range(len(results)):
      results[i] = results[i]/1000000.0
    avgResult = avg(results)
    throughput[key] = avgResult
    stddev[key] = st.pstdev(results)
    # print(avgResult)

def readJavaResultsFileMemory(filename, results, stddev, rqsizes, algs):
  resultsRaw = {}
  times = {}
  # results = {}
  # stddev = {}
  alg = ""
  rqsize = 0

  # read csv into resultsRaw
  file = open(filename, 'r')
  for line in file.readlines():
    if 'rqsize' in line:
      for word in line.split():
        if 'rqsize' in word:
          rqsize = int(word.replace('-rqsize', ''))
          if rqsize not in rqsizes:
            rqsizes.append(rqsize)
    if 'rqoverlapRandom' in line:
      alg = line.split("-")[0]
      if alg not in algs:
        algs.append(alg)
    elif line.find('Memory Usage After Benchmark') != -1:
      key = alg + " " + str(rqsize)
      # print(key)
      if key not in resultsRaw:
        resultsRaw[key] = []
      resultsRaw[key].append(int(line.split(' ')[-2]))

  # print(resultsRaw)
  # Average througputRaw into results

  for key in resultsRaw:
    resultsAll = resultsRaw[key]
    warmupRuns = int(len(resultsAll)/2)
    # print(warmupRuns)
    resultsHalf = resultsAll[warmupRuns:]
    for i in range(len(resultsHalf)):
      resultsHalf[i] = resultsHalf[i]/1000000.0
    avgResult = avg(resultsHalf)
    results[key] = avgResult
    stddev[key] = st.pstdev(resultsHalf)
    # print(avgResult)


def readJavaResultsFile(filename, throughput, stddev, threads, ratios, maxkeys, rqsizes, algs):
  columnIndex = {}
  throughputRaw = {}
  times = {}
  # throughput = {}
  # stddev = {}

  # read csv into throughputRaw
  with open(filename, newline='') as csvfile:
    csvreader = csv.reader(csvfile, delimiter=',', quotechar='|')
    for row in csvreader:
      if not bool(columnIndex): # columnIndex dictionary is empty
        for col in columns:
          columnIndex[col] = row.index(col)
      if row[columnIndex[columns[0]]] == columns[0]:  # row contains column titles
        continue
      values = {}
      for col in columns:
        values[col] = row[columnIndex[col]]
      numUpdates = int(values['ninstrue']) + int(values['ninsfalse']) + int(values['ndeltrue']) + int(values['ndelfalse']) 
      numRQs = int(values['nrqtrue']) + int(values['nrqfalse'])
      if values['ratio'] == '50i-50d-0rq' and numRQs > 0:
        key = toStringRQ(values['name'], values['nthreads'], values['maxkey'], int(float(values['rqsize'])))
        if int(values['nthreads']) not in threads:
          threads.append(int(values['nthreads']))
        if int(values['maxkey']) not in maxkeys:
          maxkeys.append(int(values['maxkey']))
        if int(float(values['rqsize'])) not in rqsizes:
          rqsizes.append(int(float(values['rqsize'])))
        if values['name'] not in algs:
          algs.append(values['name'])
        time = float(values['time'])
        if values['merged-experiment'].find('findif') != -1:
          key += '-findif'
        elif values['merged-experiment'].find('succ') != -1:
          key += '-succ'
        elif values['merged-experiment'].find('multisearch-nonatomic') != -1:
          key += '-multisearch-nonatomic'
        elif values['merged-experiment'].find('multisearch') != -1:
          key += '-multisearch'
        if (key+'-updates') not in throughputRaw:
          throughputRaw[key+'-updates'] = []
          times[key+'-updates'] = []
        if (key+'-rqs') not in throughputRaw:
          throughputRaw[key+'-rqs'] = []
          times[key+'-rqs'] = []
        throughputRaw[key+'-updates'].append(numUpdates/time)
        throughputRaw[key+'-rqs'].append(numRQs/time)
        times[key+'-updates'].append(time)
        times[key+'-rqs'].append(time)
      else:
        key = toString(values['name'], values['nthreads'], values['ratio'], values['maxkey'], int(float(values['rqsize'])))
        if int(values['nthreads']) not in threads:
          threads.append(int(values['nthreads']))
        if int(values['maxkey']) not in maxkeys:
          maxkeys.append(int(values['maxkey']))
        if values['ratio'] not in ratios:
          ratios.append(values['ratio'])
        if int(float(values['rqsize'])) not in rqsizes:
          rqsizes.append(int(float(values['rqsize'])))
        if values['name'] not in algs:
          algs.append(values['name'])
        if key not in throughputRaw:
          throughputRaw[key] = []
          times[key] = []
        throughputRaw[key].append(float(values['throughput']))
        times[key].append(float(values['time']))

  for key in throughputRaw:
    resultsAll = throughputRaw[key]
    warmupRuns = int(len(resultsAll)/2)
    # print(warmupRuns)
    results = resultsAll[warmupRuns:]
    for i in range(len(results)):
      results[i] = results[i]/1000000.0
    if avg(results) == 0:
      continue
    avgResult = avg(results)
    throughput[key] = avgResult
    stddev[key] = st.pstdev(results)
    # print(avgResult)

  # return throughput, stddev

def plot_java_scalability_graphs(inputFile, outputFile, graphtitle):
  throughput = {}
  stddev = {}
  threads = []
  ratios = []
  maxkeys = []
  rqsizes = []
  algs = []

  readJavaResultsFile(inputFile, throughput, stddev, threads, ratios, maxkeys, rqsizes, algs)
  # print(throughput)
  threads.sort()
  rqsizes.sort()
  maxkeys.sort()

  bench = toStringBench(maxkeys[0], ratios[0], rqsizes[0])

  str_threads = []
  for th in threads:
    str_threads.append(str(th))

  # print(graphtitle)
  ymax = 0
  series = {}
  error = {}
  for alg in algs:
    # if (alg == 'BatchBST64' or alg == 'ChromaticBatchBST64') and (bench.find('-0rq') == -1 or bench.find('2000000000') != -1):
    #   continue
    # if toString3(alg, 1, bench) not in results:
    #   continue
    series[alg] = []
    error[alg] = []
    for th in threads:
      if toString3(alg, th, bench) not in throughput:
        del series[alg]
        del error[alg]
        break
      series[alg].append(throughput[toString3(alg, th, bench)])
      error[alg].append(stddev[toString3(alg, th, bench)])
  
  fig, axs = plt.subplots()
  # fig = plt.figure()
  opacity = 0.8
  rects = {}
  
  for alg in series:
    ymax = max(ymax, max(series[alg]))
    rects[alg] = axs.plot(threads, series[alg],
      alpha=opacity,
      color=colors[alg],
      #hatch=hatch[ds],
      linestyle=linestyles[alg],
      marker=markers[alg],
      markersize=8,
      label=names[alg])

  axs.set_ylim(bottom=-0.02*ymax)
  # plt.xticks(threads, threads)
  axs.set_xlabel('Number of threads')
  axs.set_ylabel('Throughput (Mop/s)')
  axs.set(xlabel='Number of threads', ylabel='Total throughput (Mop/s)')
  legend_x = 1
  legend_y = 0.5 
  # if this_file_name == 'Update_heavy_with_RQ_-_100K_Keys':
  #   plt.legend(loc='center left', bbox_to_anchor=(legend_x, legend_y))

  # plt.legend(framealpha=0.0)
  plt.legend(loc='center left', bbox_to_anchor=(legend_x, legend_y))
  plt.grid()
  plt.title(graphtitle)
  plt.savefig(outputFile, bbox_inches='tight')
  plt.close('all')

def plot_rqsize_graphs(inputFile, outputFile, graphtitle, throughput, stddev, threads, ratios, maxkeys, rqsizes, algs):
  threads.sort()
  rqsizes.sort()
  maxkeys.sort()

  bench = str(threads[0]) + 't-' + str(maxkeys[0]) + 'k'

  ax = {}
  fig, (ax['rqs'], ax['updates']) = plt.subplots(1, 2, figsize=(15,5))

  for opType in ('rqs', 'updates'):
    series = {}
    error = {}
    ymax = 0
    for alg in algs:
      # if (alg == 'BatchBST64' or alg == 'ChromaticBatchBST64') and bench.find('-0rq') == -1:
      #   continue
      series[alg] = []
      error[alg] = []
      for size in rqsizes:
        if toStringRQ3(alg, size, bench, opType) not in throughput:
          del series[alg]
          del error[alg]
          break
        series[alg].append(throughput[toStringRQ3(alg, size, bench, opType)])
        error[alg].append(stddev[toStringRQ3(alg, size, bench, opType)])
    # create plot
    
    opacity = 0.8
    rects = {}
     
    for alg in series:
      if max(series[alg]) > ymax:
        ymax = max(series[alg])
      # print(alg)
      rects[alg] = ax[opType].plot(rqsizes, series[alg],
        alpha=opacity,
        color=colors[alg],
        #hatch=hatch[ds],
        linestyle=linestyles[alg],
        marker=markers[alg],
        markersize=8,
        label=names[alg])
    ax[opType].set_xscale('log', basex=2)
    # rqlabels=(8, 64, 258, '1K', '8K', '64K')
    # plt.xticks(rqsizes, rqlabels)
    if opType == 'rqs':
      ax[opType].set_yscale('log')
    else:
      ax[opType].set_ylim(bottom=-0.02*ymax)
    ax[opType].set(xlabel='Range query size', ylabel=opType.replace('rqs', 'RQ').replace('updates', 'Update') + ' throughput' + ' (Mop/s)')
    ax[opType].grid()
    ax[opType].title.set_text(graphtitle+" "+opType.replace('rqs', 'RQ').replace('updates', 'update') + ' throughput')

  legend_x = 1
  legend_y = 0.5 
  plt.legend(loc='center left', bbox_to_anchor=(legend_x, legend_y))
  plt.savefig(outputFile, bbox_inches='tight')
  plt.close('all')

def plot_java_rqsize_graphs(inputFile, outputFile, graphtitle):
  throughput = {}
  stddev = {}
  threads = []
  ratios = []
  maxkeys = []
  rqsizes = []
  algs = []

  readJavaResultsFile(inputFile, throughput, stddev, threads, ratios, maxkeys, rqsizes, algs)
  plot_rqsize_graphs(inputFile, outputFile, graphtitle, throughput, stddev, threads, ratios, maxkeys, rqsizes, algs)

def plot_cpp_rqsize_graphs(inputFile, outputFile, graphtitle):
  throughput = {}
  stddev = {}
  threads = []
  ratios = []
  maxkeys = []
  rqsizes = []
  algs = []

  readCppResultsFile(inputFile, throughput, stddev, threads, ratios, maxkeys, rqsizes, algs)
  plot_rqsize_graphs(inputFile, outputFile, graphtitle, throughput, stddev, threads, ratios, maxkeys, rqsizes, algs)

def plot_memory_graphs(inputFile, outputFile, graphtitle, results, stddev, rqsizes, algs):
  # for key in results:
  #   print(key)
  rqsizes.sort()

  fig, axs = plt.subplots()

  series = {}
  error = {}
  ymax = 0
  for alg in algs:
    # if (alg == 'BatchBST64' or alg == 'ChromaticBatchBST64') and bench.find('-0rq') == -1:
    #   continue
    series[alg] = []
    error[alg] = []
    for size in rqsizes:
      key = alg + " " + str(size)
      if key not in results:
        del series[alg]
        del error[alg]
        break
      series[alg].append(results[key])
      error[alg].append(stddev[key])
  # create plot
  
  opacity = 0.8
  rects = {}
   
  for alg in series:
    if max(series[alg]) > ymax:
      ymax = max(series[alg])
    # print(alg)
    rects[alg] = axs.plot(rqsizes, series[alg],
      alpha=opacity,
      color=colors[alg],
      #hatch=hatch[ds],
      linestyle=linestyles[alg],
      marker=markers[alg],
      markersize=8,
      label="CT-64 (non-atmoic RQs)" if names[alg] == "CT-64" else names[alg])
  axs.set_xscale('log', basex=2)
  # rqlabels=(8, 64, 258, '1K', '8K', '64K')
  # plt.xticks(rqsizes, rqlabels)
  axs.set_ylim(bottom=-0.02*ymax)
  axs.set(xlabel='Range query size', ylabel='Memory Usage (MB)')
  axs.grid()
  axs.title.set_text(graphtitle)

  legend_x = 1
  legend_y = 0.5 
  plt.legend(loc='center left', bbox_to_anchor=(legend_x, legend_y))
  plt.savefig(outputFile, bbox_inches='tight')
  plt.close('all')

def plot_java_memory_usage_graphs(inputFile, outputFile, graphtitle):
  results = {}
  stddev = {}
  rqsizes = []
  algs = []
  readJavaResultsFileMemory(inputFile, results, stddev, rqsizes, algs)
  rqsizes.sort()
  # for key in results:
  #   print(key)
  plot_memory_graphs(inputFile, outputFile, graphtitle, results, stddev, rqsizes, algs)

def plot_cpp_memory_usage_graphs(inputFile, outputFile, graphtitle):
  results = {}
  stddev = {}
  rqsizes = []
  algs = []
  readCppResultsFileMemory(inputFile, results, stddev, rqsizes, algs)
  rqsizes.sort()
  # for key in results:
  #   print(key)
  plot_memory_graphs(inputFile, outputFile, graphtitle, results, stddev, rqsizes, algs)

def plot_java_overhead_graphs(inputFile, outputFile, graphtitle):
  throughput = {}
  stddev = {}
  threads = []
  ratios = []
  maxkeys = []
  rqsizes = []
  algs = []

  readJavaResultsFile(inputFile, throughput, stddev, threads, ratios, maxkeys, rqsizes, algs)
  maxkeys.sort()

  # for key in throughput:
  #   print(key)

  th = threads[0]

  benchmarkList = []
  for size in maxkeys:
    for ratio in ratios:
      benchmarkList.append(str(size) + "k-" + ratio + "-" + str(rqsizes[0]) + 's')
  # print(benchmarkList)

  # benchmarkList = ['166666k-3i-2d-0rq-10s', '166666k-30i-20d-0rq-10s', '166666666k-3i-2d-0rq-10s', '166666666k-30i-20d-0rq-10s']
  algs = ['BatchBST64', 'VcasBatchBSTGC64', 'ChromaticBatchBST64', 'VcasChromaticBatchBSTGC64']
  xlabels = ['lookup\nheavy\nsmall', 'update\nheavy\nsmall', 'lookup\nheavy\nlarge', 'update\nheavy\nlarge']

  hatches = {
    'BatchBST64' : '/', 
    'VcasBatchBSTGC64' : 'x', 
    'ChromaticBatchBST64' : '.', 
    'VcasChromaticBatchBSTGC64' : 'o',
  }

  x = np.arange(len(xlabels))*2
  width = 0.3  # the width of the bars

  series = {}

  for alg in algs:
    series[alg] = []
    for bench in benchmarkList:
      key = toString3(alg, th, bench)
      if alg.find("Chromatic") != -1:
        keyNorm = toString3('ChromaticBatchBST64', th, bench)
      else:
        keyNorm = toString3('BatchBST64', th, bench)
      if key in throughput:
        series[alg].append(throughput[key]/throughput[keyNorm])

  fig, axs = plt.subplots(figsize=(6.5, 3.8))
  opacity = 0.8
  rects = {}
   
  # offset = width/len(queryTypes)
  curpos = x - len(series)/2.0*width
  i = 0
  for alg in series:
    if len(series[alg]) == 0:
      continue
    # print(alg)
    rects[alg] = axs.bar(curpos, series[alg], width, label=names[alg], hatch=hatches[alg])
    curpos += width
    i+=1
    if i == 2:
      curpos += 0.3
  
  axs.set_xticks(x)
  axs.set_xticklabels(xlabels)
  axs.set_ylim(bottom=0)
  axs.set(xlabel='Workload', ylabel='Normalized throughput')

  # legend_x = -0.2
  # legend_y = 1.14 
  legend_x = 1
  legend_y = 0.5 
  # plt.legend()
  plt.legend(loc='center left', bbox_to_anchor=(legend_x, legend_y))
  plt.grid()
  plt.axhline(y=1,linewidth=1, color='k', linestyle='--')
  plt.title(graphtitle)
  plt.savefig(outputFile, bbox_inches='tight')
  plt.close('all')

def plot_java_multipoint_graphs(inputFile, outputFile, graphtitle):
  throughput = {}
  stddev = {}
  threads = []
  ratios = []
  maxkeys = []
  rqsizes = []
  algs = []

  readJavaResultsFile(inputFile, throughput, stddev, threads, ratios, maxkeys, rqsizes, algs)
  threads.sort()
  # for key in throughput:
  #   print(key)

  rqsize = 256
  size = maxkeys[0]
  algs = ['ChromaticBatchBST64', 'VcasChromaticBatchBSTGC64']
  # queryTypes = ['range', 'range-nonatomic', 'findif', 'findif-nonatomic', 'succ', 'succ-nonatomic', 'multisearch', 'multisearch-nonatomic']
  queryTypes = ['1s-succ', '128s-succ', '256s', '128s-findif', '4s-multisearch']
  xlabels = ['succ\n1', ' succ\n128', 'range\n256', 'findif\n128', 'multi-\nsearch4']
  x = np.arange(len(xlabels))*2
  width = 0.35  # the width of the bars

  hatches = ('/', 'x', '.', 'o')

  opType = 'rqs'
  series = {}

  for th in threads:
    for alg in algs:
      if th == threads[0]:
        skey = names[alg] + ', 0 update threads'
      else:
        skey = names[alg] + ', ' + str(int(threads[1]) - int(threads[0])) + ' update threads'
      # if alg == 'ChromaticBatchBST64':
      #   skey += '\n   (Queries Non-Atomic)'
      series[skey] = []
      bench = str(th) + 't-' + str(size) + 'k'
      benchNorm = str(threads[0]) + 't-' + str(size) + 'k'
      benchNorm2 = str(th) + 't-' + str(size) + 'k'
      for qt in queryTypes:
        key = toStringRQcomplex3(alg, bench, qt, opType)
        keyNorm = toStringRQcomplex3(algs[0], benchNorm, qt, opType)
        keyNorm2 = toStringRQcomplex3(algs[0], benchNorm2, qt, opType)
        if key in throughput:
          series[skey].append(throughput[key]/throughput[keyNorm])
          # print(key + ': ' + str(100-100.0*results[key]/results[keyNorm2]))
  
  # if small:
  #   fig, axs = plt.subplots(figsize=(6.5, 3.0))
  # else:
  fig, axs = plt.subplots()
  opacity = 0.8
  rects = []
   
  # offset = width/len(queryTypes)
  i = 0
  curpos = x - len(series)/2.0*width
  for skey in series:
    if len(series[skey]) == 0:
      continue
    # print(alg)
    rects.append(axs.bar(curpos, series[skey], width, label=skey, hatch=hatches[i]))
    curpos += width
    i+=1
  
  axs.set_xticks(x)
  axs.set_xticklabels(xlabels)
  axs.set_ylim(bottom=0)
  axs.set(xlabel='Query type', ylabel='Query throughput (normalized)')

  legend_x = 0.95
  legend_y = 1.36
  # if small:
  #   legend_x = 0.07
  #   legend_y = 1.0
  # legend_x = 1
  # legend_y = 0.5 
  #plt.legend(framealpha=0.0)

  # create blank rectangle
  extra = Rectangle((0, 0), 1, 1, fc="w", fill=False, edgecolor='none', linewidth=0)

  #Create organized list containing all handles for table. Extra represent empty space
  legend_handle = [extra, extra, extra,
                   extra, rects[0], rects[1],
                   extra, rects[2], rects[3],]

  #Define the labels
  label_row_1 = [r"# of Update Threads", r"CT-64", r"VcasCT-64"]
  label_j_1 = [r"0"]
  label_j_2 = [r"36    "]
  label_empty = [""]

  #organize labels for table construction
  legend_labels = np.concatenate([label_row_1, label_j_1, label_empty * 2, label_j_2, label_empty * 2])

  #Create legend
  plt.legend(legend_handle, legend_labels, 
            loc='center left', bbox_to_anchor=(1, 0.5), ncol = 3, shadow = True, handletextpad = -2)

  # plt.legend(loc='center left', bbox_to_anchor=(legend_x, legend_y))
  plt.grid()
  plt.axhline(y=1,linewidth=1, color='k', linestyle='--')
  plt.title(graphtitle)
  plt.savefig(outputFile, bbox_inches='tight')
  plt.close('all')

