import csv
import matplotlib as mpl
# mpl.use('Agg')
mpl.rcParams['grid.linestyle'] = ":"
mpl.rcParams.update({'font.size': 17})
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle
import numpy as np
import os
import statistics as st
from brokenaxes import brokenaxes

outdir='graphs/'
columns = ['throughput', 'name', 'nthreads', 'ratio', 'maxkey', 'rqsize', 'time', 'ninstrue', 'ninsfalse', 'ndeltrue', 'ndelfalse', 'nrqtrue', 'nrqfalse', 'merged-experiment']

names = { 'BSTBST': 'BST',
          'BST': 'BST',
          'VcasBST': 'Vcas-BST (non-GCable)',
          'VcasBSTGC': 'Vcas-BST',
          'PBST': 'PNB-BST',
          'BPBST16': 'PNB-BST-16',
          'BatchBST16': 'BST-16',
          'LFCA': 'LFCA',
          'KIWI': 'KIWI',
          'ChromaticBST': 'ChromaticTree',
          'VcasChromaticBSTGC': 'Vcas-ChromaticTree',
          'ChromaticBatchBST16': 'ChromaticTree-16',
          'ChromaticBatchBST64': 'CT-64',
          'VcasChromaticBatchBSTGC16': 'Vcas-ChromaticTree-16',
          'VcasChromaticBatchBSTGC64': 'VcasCT-64',
          'VcasChromaticBatchBST16': 'Vcas-ChromaticTree-16 (non-GCable)',
          'VcasChromaticBatchBST': 'Vcas-ChromaticTree (non-GCable)',
          'VcasChromaticBatchBST64': 'Vcas-ChromaticTree-64 (non-GCable)',
          'VcasBatchBST16': 'Vcas-BST-16 (non-GCable)',
          'VcasBatchBST64': 'Vcas-BST-64 (non-GCable)',
          'BPBST64': 'PNB-BST',
          'BatchBST64': 'BST-64',
          'VcasBatchBSTGC64': 'VcasBST-64',
          'BPBSTGC64': 'PNB-BST-64',
          'VcasBatchBSTGC16': 'Vcas-BST-16',
          'BPBSTGC16': 'PNB-BST-16',
          'KSTRQ' : 'KST',
          'SnapTree' : 'SnapTree',}
colors = {
          # 'BPBST16': 'C6',
          'BPBST64': 'C8',
          'LFCA': 'C2', 
          'KIWI': 'C1',
          'KSTRQ' : 'C4',
          'SnapTree' : 'C9',
          # 'ChromaticBatchBST16': 'C0',
          'ChromaticBatchBST64': 'C6',
          # 'VcasChromaticBatchBSTGC16': 'C0',
          'VcasChromaticBatchBSTGC64': 'C3',
          # 'VcasChromaticBatchBST16': 'C0',
          # 'VcasChromaticBatchBST64': 'C3',
          'BatchBST64': 'C0',
          'VcasBatchBSTGC64': 'C5',               
}
linestyles = {
              # 'BPBST16': '--',
              'BPBST64': '--',
              'LFCA': '--', 
              'KIWI': '-',
              'KSTRQ' : '--',
              'SnapTree' : '-',
              # 'ChromaticBatchBST16': '-',
              'ChromaticBatchBST64': '-',
              # 'VcasChromaticBatchBSTGC16': '-',
              'VcasChromaticBatchBSTGC64': '-',
              # 'VcasChromaticBatchBST16': ':',
              # 'VcasChromaticBatchBST64': ':',
              'BatchBST64': '--',
              'VcasBatchBSTGC64': '--',         
}
markers =    {
              # 'BPBST16': '--',
              'BPBST64': 'o',
              'LFCA': 'v', 
              'KIWI': '^',
              'KSTRQ' : 's',
              'SnapTree' : 'x',
              # 'ChromaticBatchBST16': '-',
              'ChromaticBatchBST64': '|',
              # 'VcasChromaticBatchBSTGC16': '-',
              'VcasChromaticBatchBSTGC64': 'D',
              # 'VcasChromaticBatchBST16': ':',
              # 'VcasChromaticBatchBST64': ':',
              'BatchBST64': '<',
              'VcasBatchBSTGC64': '>',         
}
# markers =    {
#               # 'BPBST16': '--',
#               'BPBST64': '.',
#               'LFCA': '1', 
#               'KIWI': '2',
#               'KSTRQ' : '3',
#               'SnapTree' : '+',
#               # 'ChromaticBatchBST16': '-',
#               'ChromaticBatchBST64': 'x',
#               # 'VcasChromaticBatchBSTGC16': '-',
#               'VcasChromaticBatchBSTGC64': '|',
#               # 'VcasChromaticBatchBST16': ':',
#               # 'VcasChromaticBatchBST64': ':',
#               'BatchBST64': 'd',
#               'VcasBatchBSTGC64': '4',         
# }
benchmarkNames = {'2000000k-0i-0d-0rq-10s' : 'Lookup only',
               '2000000k-25i-25d-0rq-10s' : 'Update heavy, no RQ',
               '2000000k-10i-10d-0rq-10s': 'Lookup heavy, no RQ',
               '2000000k-25i-25d-25rq-10s': 'Update heavy with RQ',
               '2000000k-10i-10d-10rq-10s': 'Lookup heavy with RQ',
               '2000000k-10i-10d-80rq-1s': 'Using snapshots for lookup',
               '2000000k-25i-25d-25rq-10000s': 'Large RQs with updates',
               '2000000k-0i-0d-100rq-10s': 'RQ only',
               '10000000k-0i-0d-0rq-10s': '10M keys, Lookup only',
               '200000000k-0i-0d-0rq-10s' : '100M keys, Lookup only',
               '200000000k-50i-50d-0rq-10s' : '100M keys, Update only',
               '200000000k-10i-10d-0rq-10s': '100M keys, Lookup heavy, no RQ',
               '200000000k-25i-25d-25rq-10s': '100M keys, Update heavy with RQ',
               '200000000k-10i-10d-10rq-10s': '100M keys, Lookup heavy with RQ',
               '200000000k-10i-10d-80rq-1s': '100M keys, Using snapshots for lookup',
               '200000000k-25i-25d-25rq-10000s': '100M keys, Large RQs with updates',
               '200000000k-0i-0d-100rq-8000s': '100M keys, RQ only, RQ size 8K',
               '20000k-0i-0d-0rq-10s' : '10K keys, Lookup only',
               '20000k-50i-50d-0rq-10s' : '10K keys, Update only',
               '20000k-10i-10d-0rq-10s': '10K keys, Lookup heavy, no RQ',
               '20000k-25i-25d-25rq-10s': '10K keys, Update heavy with RQ',
               '20000k-10i-10d-10rq-10s': '10K keys, Lookup heavy with RQ',
               '20000k-10i-10d-80rq-1s': '10K keys, Using snapshots for lookup',
               '20000k-25i-25d-25rq-10000s': '10K keys, Large RQs with updates',
               '20000k-0i-0d-100rq-8000s': '10K keys, RQ only, RQ size 8K',
               '200000k-0i-0d-0rq-10s' : '100K keys, Lookup only',
               '200000k-50i-50d-0rq-10s' : '100K keys, Update only',
               '200000k-10i-10d-0rq-10s': '100K keys, Lookup heavy, no RQ',
               '200000k-25i-25d-25rq-10s': '100K keys, Update heavy with RQ',
               '200000k-10i-10d-10rq-10s': '100K keys, Lookup heavy with RQ',
               '200000k-10i-10d-80rq-1s': '100K keys, Using snapshots for lookup',
               '200000k-25i-25d-25rq-10000s': '100K keys, Large RQs with updates',
               '200000k-0i-0d-100rq-8000s': '100K keys, RQ only, RQ size 8K',
               '2000000k-0i-0d-0rq-10s' : '1M keys, Lookup only',
               '2000000k-50i-50d-0rq-10s' : '1M keys, Update only',
               '2000000k-10i-10d-0rq-10s': '1M keys, Lookup heavy, no RQ',
               '2000000k-25i-25d-25rq-10s': '1M keys, Update heavy with RQ',
               '2000000k-10i-10d-10rq-10s': '1M keys, Lookup heavy with RQ',
               '2000000k-10i-10d-80rq-1s': '1M keys, Using snapshots for lookup',
               '2000000k-25i-25d-25rq-10000s': '1M keys, Large RQs with updates',
               '2000000k-0i-0d-100rq-8000s': '1M keys, RQ only, RQ size 8K',
               '10000000k-0i-0d-0rq-10s': '10M keys, Lookup only',

               '2000000000k-100i-0d-0rq-10s' : 'Insert Only, Sorted Sequence',

               '166666k-3i-2d-0rq-10s' : 'Lookup heavy - 100K Keys:\n3% insert, 2% delete, 95% lookup',
               '166666k-30i-20d-0rq-10s' : 'Update heavy - 100K Keys:\n30% insert, 20% delete, 50% lookup',
               '166666k-30i-20d-1rq-1024s' : 'Update heavy with RQ - 100K Keys:\n30% insert, 20% delete, 49% lookup, 1% RQ of size 1024',
               '166666666k-3i-2d-0rq-10s' : 'Lookup heavy - 100M Keys:\n3% insert, 2% delete, 95% lookup',
               '166666666k-30i-20d-0rq-10s' : 'Update heavy - 100M Keys:\n30% insert, 20% delete, 50% lookup',
               '166666666k-30i-20d-1rq-1024s' : 'Update heavy with RQ - 100M Keys:\n30% insert, 20% delete, 49% lookup, 1% RQ of size 1024',
               # '72t-200000k' : 'Concurrent Updates and RQs - 100K Keys:\n36 update threads, 36 RQ threads',
               # '36t-200000k' : 'RQ only - 100K Keys:\n36 RQ threads',
               # '72t-200000000k' : 'Concurrent Updates and RQs - 100M Keys:\n36 update threads, 36 RQ threads',
               # '36t-200000000k' : 'RQ only - 100M Keys:\n36 RQ threads',

               '72t-200000k-rqs' : 'RQ Throughput with Concurrent Updates - 100K Keys:\n36 update threads, 36 RQ threads',
               '36t-200000k-rqs' : 'RQ only - 100K Keys:\n36 RQ threads',
               '72t-200000000k-rqs' : 'RQ Throughput with Concurrent Updates - 100M Keys:\n36 update threads, 36 RQ threads',
               '36t-200000000k-rqs' : 'RQ only - 100M Keys:\n36 RQ threads',

               '72t-200000k-updates' : 'Update Throughput with Concurrent RQs - 100K Keys:\n36 update threads, 36 RQ threads',
               '72t-200000000k-updates' : 'Update Throughput with Concurrent RQs - 100M Keys:\n36 update threads, 36 RQ threads',
}

# algListWithBase = ['VcasChromaticBatchBSTGC16', 'VcasChromaticBatchBSTGC64', 'VcasChromaticBatchBST16', 'VcasChromaticBatchBST64', 'ChromaticBatchBST16', 'ChromaticBatchBST64', 'BPBST16', 'BPBST64', 'LFCA', 'KIWI', 'KSTRQ', 'SnapTree']
# algListWithBase = ['BPBST16', 'BPBST64', 'KIWI', 'KSTRQ', 'SnapTree', 'LFCA', 'ChromaticBatchBST16', 'VcasChromaticBatchBSTGC16', 'ChromaticBatchBST64', 'VcasChromaticBatchBSTGC64', ]
# algList = ['BPBST16', 'BPBST64', 'KIWI', 'KSTRQ', 'SnapTree', 'LFCA', 'VcasChromaticBatchBSTGC16', 'VcasChromaticBatchBSTGC64', ]

# algListWithBase = ['KIWI', 'KSTRQ', 'SnapTree', 'BPBST64',  'LFCA', 'ChromaticBatchBST64', 'VcasChromaticBatchBSTGC64', ]
algList = ['KIWI', 'SnapTree', 'KSTRQ', 'BPBST64', 'LFCA', 'VcasBatchBSTGC64', 'VcasChromaticBatchBSTGC64']

threadList = [1, 36, 72, 140]
RQsizes = [8, 64, 256, 1024, 8192, 65536]
# threadList = [1, 8, 32, 48, 64]
# legendPrinted = False

# benchmarkList = ['200000000k-0i-0d-0rq-10s', '200000000k-50i-50d-0rq-10s', '200000000k-0i-0d-100rq-8000s', '2000000k-0i-0d-0rq-10s', '2000000k-50i-50d-0rq-10s', '2000000k-0i-0d-100rq-8000s']
benchmarkList = ['166666k-3i-2d-0rq-10s', '166666k-30i-20d-0rq-10s', '166666k-30i-20d-1rq-1024s', '166666666k-3i-2d-0rq-10s', '166666666k-30i-20d-0rq-10s', '166666666k-30i-20d-1rq-1024s']

# benchmarkRQ = ['72t-200000000k', '36t-200000000k', '72t-2000000k', '36t-2000000k']
benchmarkRQ = ['72t-200000k', '36t-200000k', '72t-200000000k', '36t-200000000k']

resultsFiles = ['2020-07-31-10..70', # original file
                '2020-08-05-18..43', # run-experiments-rq with backoff
                '2020-08-05-19..06', # run-experiments-complex with backoff
                '2020-08-05-19..37', # run-experiments-complex with backoff
                '2020-08-05-19..45', # run-experiments-rq with backoff
                ]

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

def toStringRQcomplex3(algname, rqsize, benchmark, querytype, optype):
  if(querytype == 'range'):
    return toStringRQ3(algname, rqsize, benchmark, optype)
  return "RQ-" + algname + '-' + benchmark + '-' + str(rqsize) + 's' + '-' + querytype + '-' + optype

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

def export_legend(legend, filename="legend.png", expand=[-5,-5,5,5]):
    fig  = legend.figure
    fig.canvas.draw()
    bbox  = legend.get_window_extent()
    bbox = bbox.from_extents(*(bbox.extents + np.array(expand)))
    bbox = bbox.transformed(fig.dpi_scale_trans.inverted())
    fig.savefig(filename, dpi="figure", bbox_inches=bbox)

def create_graph(results, stddev, benchmarks, algs, threads, graph_title, folder, printLegend='', errorbar=False):
  graphsDir = outdir+resultsFile+'/'+folder+'/'
  if not os.path.exists(outdir+resultsFile):
    os.mkdir(outdir+resultsFile)
  if not os.path.exists(graphsDir):
    os.mkdir(graphsDir)

  str_threads = []
  for th in threads:
    str_threads.append(str(th))

  for bench in benchmarks:
    this_file_name = graph_title + ' ' +benchmarkNames[bench][0:benchmarkNames[bench].find(':')]
    this_graph_title = graph_title + ' ' + benchmarkNames[bench]
    this_file_name = this_file_name.strip().replace(' ', '_')
    this_graph_title = this_graph_title.strip()
    print()
    print(this_graph_title)
    ymax = 0
    series = {}
    error = {}
    for alg in algs:
      if printLegend == 'balanced' and linestyles[alg] == '--':
        continue
      if printLegend == 'unbalanced' and linestyles[alg] == '-':
        continue      
      if (alg == 'BatchBST64' or alg == 'ChromaticBatchBST64') and (bench.find('-0rq') == -1 or bench.find('2000000000') != -1):
        continue
      if toString3(alg, 1, bench) not in results:
        continue
      series[alg] = []
      error[alg] = []
      throughput1 = results[toString3(alg, 1, bench)]
      throughputmax = results[toString3(alg, threads[-2], bench)]
      # print(alg + ' speed up on ' + str(threads[-2]) + ' cores: ' + str(throughputmax/throughput1))
      for th in threads:
        series[alg].append(results[toString3(alg, th, bench)])
        error[alg].append(stddev[toString3(alg, th, bench)])
    # create plot
    
    fig, axs = plt.subplots()
    # fig = plt.figure()
    # axs = brokenaxes(xlims=((0, 75), (135, 145)))
    opacity = 0.8
    rects = {}
    
    for alg in algs:
      if alg not in series:
        continue
      if max(series[alg]) > ymax:
        ymax = max(series[alg])
      if errorbar:
        rects[alg] = axs.errorbar(threads, series[alg], 
          error[alg], capsize=3, 
          alpha=opacity,
          color=colors[alg],
          #hatch=hatch[ds],
          linestyle=linestyles[alg],
          marker='o',
          label=names[alg])
      else:
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
    # axs.set_xlabel('Number of threads')
    # axs.set_ylabel('Throughput (Mop/s)')
    if benchmarks[0] == '2000000000k-100i-0d-0rq-10s':
      axs.set(xlabel='Number of threads', ylabel='Insert throughput (Mop/s)')
    else:
      axs.set(xlabel='Number of threads', ylabel='Total throughput (Mop/s)')
    legend_x = 1
    legend_y = 0.5 
    # if this_file_name == 'Update_heavy_with_RQ_-_100K_Keys':
    #   plt.legend(loc='center left', bbox_to_anchor=(legend_x, legend_y))
    if printLegend != '':
      legend = plt.legend(loc='center left', bbox_to_anchor=(legend_x, legend_y), ncol=7, framealpha=0.0)
      export_legend(legend, graphsDir+printLegend+'_legend.png')
    else:
      # plt.legend(framealpha=0.0)
      plt.grid()
      #plt.title(this_graph_title)
      plt.savefig(graphsDir+this_file_name+'.png', bbox_inches='tight')
      plt.close('all')

def create_graph_legend(algs, printLegend='', folder=''):
  graphsDir = outdir+resultsFile+'/'+folder+'/'
  if not os.path.exists(outdir+resultsFile):
    os.mkdir(outdir+resultsFile)
  if not os.path.exists(graphsDir):
    os.mkdir(graphsDir)

  print()
  print('creating legend')
  x = [0, 1]
  series = {}
  for alg in algs:  
    series[alg] = [0, 1]
  # create plot
  
  fig, axs = plt.subplots()
  # fig = plt.figure()
  # axs = brokenaxes(xlims=((0, 75), (135, 145)))
  opacity = 0.8
  rects = {}
  
  for alg in algs:
    rects[alg] = axs.plot(x, series[alg],
      alpha=opacity,
      color=colors[alg],
      #hatch=hatch[ds],
      linestyle=linestyles[alg],
      marker=markers[alg],
      markersize=8,
      label=names[alg])

  legend_x = 1
  legend_y = 0.5 

  # create blank rectangle
  extra = Rectangle((0, 0), 1, 1, fc="w", fill=False, edgecolor='none', linewidth=0)

  #Create organized list containing all handles for table. Extra represent empty space
  legend_handle = [extra]

  #Define the labels
  label_row_1 = [r""+printLegend+" data structures:"]

  #organize labels for table construction
  legend_labels = np.concatenate([label_row_1])

  #Create legend
  legend = plt.legend(legend_handle, legend_labels, 
            # bbox_to_anchor=(legend_x, legend_y), 
            ncol = 1, handletextpad = -2,
            framealpha=0.0)
  export_legend(legend, graphsDir+printLegend+'.png')
  plt.close('all')

def create_graph_rq(results, stddev, benchmarks, algs, rqsizes, graph_title, folder, errorbar=False):
  graphsDir = outdir+resultsFile+'/'+folder+'/'
  if not os.path.exists(outdir+resultsFile):
    os.mkdir(outdir+resultsFile)
  if not os.path.exists(graphsDir):
    os.mkdir(graphsDir)
  for bench in benchmarks:
    for graphType in ('log', 'normal', 'percent', 'scaled'):
      for opType in ('rqs', 'updates'):
        if graphType == 'percent' and (bench.find("72") == -1):
          continue
        if graphType == 'scaled' and opType == 'updates':
          continue
        if graphType == 'log' and opType == 'updates':
          continue
        series = {}
        error = {}
        ymax = 0
        for alg in algs:
          if (alg == 'BatchBST64' or alg == 'ChromaticBatchBST64') and bench.find('-0rq') == -1:
            continue
          if toStringRQ3(alg, rqsizes[0], bench, opType) not in results:
            continue
          series[alg] = []
          error[alg] = []
          for size in rqsizes:
            if graphType == 'percent':
              if opType == 'updates':
                key = toString4(alg, bench.replace('72', '36'), '50i-50d-0rq-10s')
                if key not in results:
                  continue
                series[alg].append(100*results[toStringRQ3(alg, size, bench, opType)]/results[toString4(alg, bench.replace('72', '36'), '50i-50d-0rq-10s')])
              else:
                series[alg].append(100*results[toStringRQ3(alg, size, bench, opType)]/results[toStringRQ3(alg, size, bench.replace('72', '36'), opType)])
            elif graphType == 'scaled':
              series[alg].append(results[toStringRQ3(alg, size, bench, opType)]*int(size))
            else:
              series[alg].append(results[toStringRQ3(alg, size, bench, opType)])
              error[alg].append(stddev[toStringRQ3(alg, size, bench, opType)])
            # if opType == 'rqs':
            #   series[alg].append(int(size) * results[toStringRQ3(alg, size, bench, opType)])
            # else:
            #   series[alg].append(results[toStringRQ3(alg, size, bench, opType)])
          if len(error[alg]) == 0:
            error[alg] = None
        if len(series) == 0:
          continue
        # create plot
        
        fig, axs = plt.subplots()
        opacity = 0.8
        rects = {}
         
        offset = 0
        for alg in algs:
          if alg not in series:
            continue
          if len(series[alg]) == 0:
            continue
          if max(series[alg]) > ymax:
            ymax = max(series[alg])
          # print(alg)
          if errorbar:
            rects[alg] = axs.errorbar(rqsizes, series[alg], 
              error[alg], capsize=3,
              alpha=opacity,
              color=colors[alg],
              #hatch=hatch[ds],
              linestyle=linestyles[alg],
              marker='o',
              label=names[alg])
          else:
            rects[alg] = axs.plot(rqsizes, series[alg],
              alpha=opacity,
              color=colors[alg],
              #hatch=hatch[ds],
              linestyle=linestyles[alg],
              marker=markers[alg],
              markersize=8,
              label=names[alg])
        
        benchKey = bench + '-' + opType
        if benchKey in benchmarkNames:
          this_file_name = graph_title + ' ' + benchmarkNames[benchKey][0:benchmarkNames[benchKey].find(':')]
          this_graph_title = graph_title + ' ' + benchmarkNames[benchKey]
        else:
          this_file_name = graph_title + '-' + benchKey
          this_graph_title = graph_title + ': ' + benchKey
        this_file_name = this_file_name.strip().replace(' ', '_')
        this_graph_title = this_graph_title.strip()
        print()
        print(this_graph_title)

        axs.set_xscale('log', basex=2)
        rqlabels=(8, 64, 258, '1K', '8K', '64K')
        plt.xticks(rqsizes, rqlabels)
        if graphType == 'log':
          axs.set_yscale('log')
          ylim = axs.get_ylim()
          # if ylim[0] < 0.002:
            # axs.set_ylim((0.001, ylim[1]))
        else:
          axs.set_ylim(bottom=-0.02*ymax)
        if graphType == 'percent':
          axs.set(xlabel='Range query size', ylabel='RQ throughput with updates / RQ Throughput without updates (%)')
        elif graphType == 'scaled':
          axs.set(xlabel='Range query size', ylabel='Keys scanned (Mop/s)')
        else:
          axs.set(xlabel='Range query size', ylabel=opType.replace('rqs', 'RQ').replace('updates', 'Update') + ' throughput' + ' (Mop/s)')
        legend_x = 1
        legend_y = 0.5 
        # plt.legend(loc='center left', bbox_to_anchor=(legend_x, legend_y))
        #plt.legend(framealpha=0.0)
        # legend.get_frame().set_alpha(0.3)
        plt.grid()
        #plt.title(this_graph_title)
        if not os.path.exists(graphsDir+graphType):
          os.mkdir(graphsDir+graphType)
        plt.savefig(graphsDir+graphType+'/'+this_file_name + '.png', bbox_inches='tight')
        plt.close('all')

def create_graph_vary_size(results, stddev):
  numThreads = '72'
  rqsizes = [1000, 10000, 100000, 1000000]
  algs = ['LFCA', 'VcasChromaticBSTGC', 'VcasChromaticBatchBSTGC64']

  graphsDir = outdir+resultsFile+'/'
  this_file_name = 'Full table scan, vary size'.replace(' ', '_')
  this_graph_title = 'Full table scan, vary size'
  print()
  print(this_graph_title)
  if not os.path.exists(graphsDir):
    os.mkdir(graphsDir)

  for opType in ('rqs', 'updates'):
    series = {}
    error = {}
    ymax = 0
    for alg in algs:
      # if toStringRQ3(alg, rqsizes[0], bench, opType) not in results:
      #   continue
      series[alg] = []
      error[alg] = []
      for size in rqsizes:
        bench = numThreads + 't-' + str(2*size) + 'k'
        key = toStringRQ3(alg, size, bench, opType)
        series[alg].append(results[key])
        error[alg].append(stddev[key])
        # if opType == 'rqs':
        #   series[alg].append(int(size) * results[toStringRQ3(alg, size, bench, opType)])
        # else:
        #   series[alg].append(results[toStringRQ3(alg, size, bench, opType)])
    if len(series) == 0:
      continue
    # create plot
    
    fig, axs = plt.subplots()
    opacity = 0.8
    rects = {}
     
    offset = 0
    for alg in algs:
      if alg not in series:
        continue
      if len(series[alg]) == 0:
        continue
      if max(series[alg]) > ymax:
        ymax = max(series[alg])
      # print(alg)
      rects[alg] = axs.errorbar(rqsizes, series[alg], 
        error[alg], capsize=3,
        alpha=opacity,
        color=colors[alg],
        linestyle=linestyles[alg],
        #hatch=hatch[ds],
        marker=markers[alg],
        markersize=8,
        label=names[alg])
    
    axs.set_xscale('log')
    if opType == 'rqs':
      axs.set_yscale('log')
    else:
      axs.set_ylim(bottom=-0.02*ymax)
    axs.set(xlabel='Range query size', ylabel='Throughput of ' + opType + ' (Mop/s)')
    legend_x = 1
    legend_y = 0.5 
    #plt.legend(loc='center left', bbox_to_anchor=(legend_x, legend_y))
    plt.grid()
    #plt.title(this_graph_title + ', ' + opType + ' throughput')
    plt.savefig(graphsDir+this_file_name+'-'+opType+'.png', bbox_inches='tight')
    plt.close('all')

def create_graph_complex(results, stddev, small=False):
  threads = ['36', '72']
  rqsize = 256
  size = 200000000
  algs = ['ChromaticBatchBST64', 'VcasChromaticBatchBSTGC64']
  # queryTypes = ['range', 'range-nonatomic', 'findif', 'findif-nonatomic', 'succ', 'succ-nonatomic', 'multisearch', 'multisearch-nonatomic']
  queryTypes = ['succ1', 'succ', 'range', 'findif', 'multisearch']
  xlabels = ['succ\n1', ' succ\n128', 'range\n256', 'findif\n128', 'multi-\nsearch4']
  x = np.arange(len(xlabels))*2
  width = 0.35  # the width of the bars

  hatches = ('/', 'x', '.', 'o')

  graphsDir = outdir+resultsFile+'/'
  this_file_name = 'Complex Queries'.replace(' ', '_')
  this_graph_title = 'Throughput of Complex Queries on Vcas-ChromaticTree'
  print()
  print(this_graph_title)
  if not os.path.exists(graphsDir):
    os.mkdir(graphsDir)

  opType = 'rqs'
  series = {}

  for th in threads:
    for alg in algs:
      if th == '36':
        skey = names[alg] + ', 0 update threads'
      else:
        skey = names[alg] + ', 36 update threads'
      # if alg == 'ChromaticBatchBST64':
      #   skey += '\n   (Queries Non-Atomic)'
      series[skey] = []
      bench = th + 't-' + str(size) + 'k'
      benchNorm = threads[0] + 't-' + str(size) + 'k'
      benchNorm2 = th + 't-' + str(size) + 'k'
      for qt in queryTypes:
        if qt == 'succ1':
          key = toStringRQcomplex3(alg, 2, bench, 'succ', opType)
          keyNorm = toStringRQcomplex3(algs[0], 2, benchNorm, 'succ', opType)
          keyNorm2 = toStringRQcomplex3(algs[0], 2, benchNorm2, 'succ', opType)
        else:
          key = toStringRQcomplex3(alg, rqsize, bench, qt, opType)
          keyNorm = toStringRQcomplex3(algs[0], rqsize, benchNorm, qt, opType)
          keyNorm2 = toStringRQcomplex3(algs[0], rqsize, benchNorm2, qt, opType)
        if key in results:
          series[skey].append(results[key]/results[keyNorm])
          print(key + ': ' + str(100-100.0*results[key]/results[keyNorm2]))
  
  if small:
    fig, axs = plt.subplots(figsize=(6.5, 3.0))
  else:
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
  if small:
    legend_x = 0.07
    legend_y = 1.0
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
            bbox_to_anchor=(legend_x, legend_y), ncol = 3, shadow = True, handletextpad = -2)

  # plt.legend(loc='center left', bbox_to_anchor=(legend_x, legend_y))
  plt.grid()
  plt.axhline(y=1,linewidth=1, color='k', linestyle='--')
  #plt.title(this_graph_title)
  plt.savefig(graphsDir+this_file_name +'.png', bbox_inches='tight')
  plt.close('all')

def create_graph_overhead(results, stddev):
  th = '140'
  benchmarkList = ['166666k-3i-2d-0rq-10s', '166666k-30i-20d-0rq-10s', '166666666k-3i-2d-0rq-10s', '166666666k-30i-20d-0rq-10s']
  algs = ['BatchBST64', 'VcasBatchBSTGC64', 'ChromaticBatchBST64', 'VcasChromaticBatchBSTGC64']
  xlabels = ['lookup\nheavy\n100K', 'update\nheavy\n100K', 'lookup\nheavy\n100M', 'update\nheavy\n100M']

  hatches = {
    'BatchBST64' : '/', 
    'VcasBatchBSTGC64' : 'x', 
    'ChromaticBatchBST64' : '.', 
    'VcasChromaticBatchBSTGC64' : 'o',
  }

  x = np.arange(len(xlabels))*2
  width = 0.3  # the width of the bars

  graphsDir = outdir+resultsFile+'/'
  this_file_name = 'Overhead'.replace(' ', '_')
  this_graph_title = 'Overhead'
  print()
  print(this_graph_title)
  if not os.path.exists(graphsDir):
    os.mkdir(graphsDir)

  series = {}

  for alg in algs:
    series[alg] = []
    for bench in benchmarkList:
      key = toString3(alg, th, bench)
      if alg.find("Chromatic") != -1:
        keyNorm = toString3('ChromaticBatchBST64', th, bench)
      else:
        keyNorm = toString3('BatchBST64', th, bench)
      if key in results:
        series[alg].append(results[key]/results[keyNorm])

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

  legend_x = -0.2
  legend_y = 1.14 
  # legend_x = 1
  # legend_y = 0.5 
  # plt.legend()
  plt.legend(loc='center left', bbox_to_anchor=(legend_x, legend_y), ncol=2, framealpha=0.0)
  plt.grid()
  plt.axhline(y=1,linewidth=1, color='k', linestyle='--')
  #plt.title(this_graph_title)
  plt.savefig(graphsDir+this_file_name +'.png', bbox_inches='tight')
  plt.close('all')

def readFile(filename, throughput, stddev):
  columnIndex = {}
  throughputRaw = {}
  times = {}
  # throughput = {}
  # stddev = {}

  # read csv into throughputRaw
  with open('results/'+filename+'.csv', newline='') as csvfile:
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
        if key not in throughputRaw:
          throughputRaw[key] = []
          times[key] = []
        throughputRaw[key].append(float(values['throughput']))
        times[key].append(float(values['time']))

  # print(throughputRaw)
  # Average througputRaw into throughput

  print("Experiments ran for too long:")
  for key in throughputRaw:
    time = times[key]
    warmupRuns = int(len(time)/2)
    count = 0
    for t in time[warmupRuns:]:
      if t > 5.5:
        count+=1
    if count > 0:
      print(key + ': ' + str(count))
      # print('\t' + str(time[warmupRuns:]))
  print()

  print("incorrect number of trials:")
  for key in throughputRaw:
    resultsAll = throughputRaw[key]
    if key.find('2000000000') == -1 and len(resultsAll) != 10:
      print(key + ': ' + str(len(resultsAll)))
      # print('\t' + str(resultsAll))
    if key.find('2000000000') != -1 and len(resultsAll) != 20:
      print(key + ': ' + str(len(resultsAll)))
  print()

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

def print_overheads(throughput):
  workloads = ['166666k-3i-2d-0rq-10s', '166666k-30i-20d-0rq-10s', '166666666k-3i-2d-0rq-10s', '166666666k-30i-20d-0rq-10s']
  for bench in workloads:
    print()
    print(bench + ' Overhead:')
    for th in threadList:
      resultbst = throughput[toString3('BatchBST64', th, bench)]
      resultct = throughput[toString3('ChromaticBatchBST64', th, bench)]
      resultvbst = throughput[toString3('VcasBatchBSTGC64', th, bench)]
      resultvct = throughput[toString3('VcasChromaticBatchBSTGC64', th, bench)]
      print('\t' + str(th) + ' thread (bst): ' + str(100.0*(resultbst-resultvbst)/resultbst) + '%')
      print('\t' + str(th) + ' thread (ct): ' + str(100.0*(resultct-resultvct)/resultct) + '%')

# benchmarkList = ['2000000k-25i-25d-25rq-10s', '2000000k-10i-10d-10rq-10s', '2000000k-10i-10d-80rq-1s', '2000000k-25i-25d-25rq-10000s']
# benchmarkList = ['2000000k-0i-0d-100rq-10s']
# benchmarkList = ['2000000k-0i-0d-0rq-10s', '2000000k-25i-25d-0rq-10s', '2000000k-10i-10d-0rq-10s']

throughput = {}
stddev = {}
for resultsFile in resultsFiles:
  readFile(resultsFile, throughput, stddev)

for key in throughput:
    print(key)
    print('\t\t\t' + str(throughput[key]))
# stddev_ratio = {}
# for key in throughput:
#   stddev_ratio = 

# sorted_stddev = []
# sorted_stddev = [v for k, v in sorted(x.items(), key=lambda item: item[1])]
# index95 = int(95.0/100*len(sorted_stddev))


experiments_to_print = ['RQ-BPBST64-72t-200000k-8s-updates',
                        'RQ-BPBST64-72t-200000k-64s-updates',
                        'RQ-BPBST64-72t-200000k-256s-updates',
                        'RQ-BPBST64-72t-200000k-1024s-updates',
                        'RQ-LFCA-72t-200000k-8s-rqs',
                        'RQ-VcasChromaticBatchBSTGC64-72t-200000k-8s-rqs',
                        # 'RQ-BPBST64-72t-200000k-8s-updates',
]

for exp in experiments_to_print:
  print(exp + '\n\t' + str(throughput[exp]))

print_overheads(throughput)

# print()
# print("72 thread throughputs:")
# for key in throughput:
#   if key.find('72') != -1:
#     print(key + ': ' + str(throughput[key]))
# print(throughput)
# create_graph(throughput, benchmarkList, algList, threadList, 'BST')


# print(throughput['RQ-VcasChromaticBatchBSTGC64-36t-200000000k-256s-rqs'])

create_graph_legend(algList, 'Balanced')
create_graph_legend(algList, 'Unbalanced')
create_graph(throughput, stddev, ['2000000000k-100i-0d-0rq-10s'], algList, threadList, '', '', 'balanced')
create_graph(throughput, stddev, ['2000000000k-100i-0d-0rq-10s'], algList, threadList, '', '', 'unbalanced')
create_graph_overhead(throughput, stddev)
create_graph_complex(throughput, stddev, True)
create_graph(throughput, stddev, ['2000000000k-100i-0d-0rq-10s'], algList, threadList, '', '')

create_graph(throughput, stddev, benchmarkList, algList, threadList, '', '')
create_graph_rq(throughput, stddev, benchmarkRQ, algList, RQsizes, '', 'RQ-and-Updates')