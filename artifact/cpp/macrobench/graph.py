import os.path
from os.path import isdir
from os import mkdir
from subprocess import check_output,CalledProcessError
import numpy as np
import matplotlib as mpl
mpl.use('Agg')

import pandas as pd
import matplotlib.pyplot as plt
plt.style.use('ggplot')
import sys

clean = 0
if "-clean" in sys.argv:
    clean = 1

print "Generating graphs using Matplotlib version " + mpl.__version__ + "and Pandas version " + pd.__version__

if clean: 
    font = {'weight' : 'normal',
            'size'   : 26}
    mpl.rc('font', **font)

    
from matplotlib import rcParams
rcParams.update({'figure.autolayout': True})

CSV_FILENAME = "dbx.csv"

if not os.path.isfile(CSV_FILENAME):
    print "Database file '" + CSV_FILENAME + "' does not exist!"
    print "Run ./makecsv.sh > dbx.csv"
    exit()

#check if HTM is enabled
try: 
    check_output('g++ ../common/test_htm_support.cpp -o ../common/test_htm_support > /dev/null; ../common/test_htm_support',shell=True)
    htm_enabled = True
except CalledProcessError:
    htm_enabled = False


if htm_enabled:
    rqAlgorithms = ["RQ_RWLOCK", "RQ_HTM_RWLOCK", "RQ_LOCKFREE", "RQ_UNSAFE", "RQ_RLU"]
else:
    rqAlgorithms = ["RQ_RWLOCK", "RQ_LOCKFREE", "RQ_UNSAFE", "RQ_RLU"]

if not isdir("graphs"):
    mkdir("graphs")
outdir = "graphs/"


def do_dbx_graph():
    df = pd.read_csv('dbx.csv')
    if pd.__version__ < '0.14.0':
        df = pd.pivot_table(df, values='throughput', rows='datastructure', cols='rqalg', aggfunc=np.mean)
    else:
        df = pd.pivot_table(df, values='throughput', index='datastructure', columns='rqalg', aggfunc=np.mean)
    df = df.reindex(columns=rqAlgorithms)
    plt.figure()
    if clean:
        hatches = ["", '.', '/', 'O', 'X','-']
        ax = df.plot(kind='bar', legend=False, color='white', edgecolor='black', linewidth=2)
        # Loop over the bars
        for i,thisbar in enumerate(ax.patches):
            # Set a different hatch for each bar (add +1 for when using unsafe)
            hatch = hatches[i/(len(df.index.values))]
            thisbar.set_hatch(hatch*hatches_mult)
        plt.xticks(rotation=0, fontsize = 14)
        plt.yticks(fontsize = 12)
        ax.set_aspect(1.5)
        ymin,ymax = ax.get_ylim()
        plt.yticks(np.arange(ymin, ymax, 0.2))
        ax.xaxis.grid(which='major', linewidth=0)
    else: 
        ax = df.plot(kind='bar', legend=True, linewidth=2)
        ax.legend(ax.get_lines(), df.columns, loc='best') 
        plt.xticks(rotation=0)
        ax.set_ylabel("txn/sec")
    plt.xlabel("")
    ax.grid(color='grey', linestyle='-')    
    if not clean:
      ax.legend(loc='center left', bbox_to_anchor=(1, 0.5),handleheight=2, handlelength=2)
      plt.savefig(outdir+"dbx.png",bbox_extra_artists=(ax.get_legend(),),bbox_inches='tight')
    else:
      plt.savefig(outdir+"dbx.png",bbox_inches='tight')
    plt.close('all')


print "generating DBX graphs"
do_dbx_graph()
