include ../config.mk
PLAF = -DLOGICAL_PROCESSORS=$(maxthreads_powerof2) -DMAX_TID_POW2=$(maxthreads_powerof2) -DCPU_FREQ_GHZ=$(cpu_freq_ghz) -DCPU_FREQ=$(cpu_freq_ghz)

dict=CITRUS_SPIN
workload=TPCC

CC=g++
CFLAGS=-g -std=c++0x
#CFLAGS += -Wall
#CFLAGS += -DNDEBUG

.SUFFIXES: .o .cpp .h

ifeq ($(readonly),)
    workload2=$(workload)
else
    workload2=$(workload)_readonly
endif

machine=$(shell hostname)
bindir=bin/$(machine)
odir=$(bindir)/OBJS_$(workload2)_$(dict)

SRC_DIRS = ./ ./benchmarks/ ./concurrency_control/ ./storage/ ./storage/index/ ./system/
SRC_DIRS += ./rlu/
INCLUDE = -I. -I./include -I./benchmarks -I./concurrency_control -I./storage -I./system -I./storage/index

######################## DEFINES FOR RQ_DATA STRUCTURES ########################
#CFLAGS += -Winline
CFLAGS += -DNDEBUG
#CFLAGS += -DNO_FREE
#CFLAGS += -DUSE_STL_HASHLIST
CFLAGS += -DUSE_SIMPLIFIED_HASHLIST
#CFLAGS += -DRAPID_RECLAMATION
#CFLAGS += -DRWLOCK_PTHREADS
#CFLAGS += -DRWLOCK_FAVOR_WRITERS
CFLAGS += -DRWLOCK_FAVOR_READERS
#CFLAGS += -DSNAPCOLLECTOR_PRINT_RQS
#CFLAGS += -DRQ_VALIDATION
#CFLAGS += -DRQ_VISITED_IN_BAGS_HISTOGRAM
#CFLAGS += -DRQ_HISTOGRAM
#CFLAGS += -DADD_DELAY_BEFORE_DTIME
#CFLAGS += -DUSE_DEBUGCOUNTERS
#CFLAGS += -DRANDOMIZED_ALLOCATIONS
#CFLAGS += -DEXTRA_CACHE_MISSES_PER_OP
#CFLAGS += -DBLOCKBAG_ITERATOR_COUNT_BLOCKS_TRAVERSED
CFLAGS += $(PLAF)
#CFLAGS += -DDEBUG=if\(1\) -DDEBUG1=if\(1\) -DDEBUG2=if\(1\)
#CFLAGS += -DDEBUG=if\(1\) -DDEBUG1=if\(0\) -DDEBUG2=if\(0\)
CFLAGS += -DDEBUG=if\(0\) -DDEBUG1=if\(0\) -DDEBUG2=if\(0\)
CFLAGS += -DMEMORY_STATS=if\(1\) -DMEMORY_STATS2=if\(1\)
#CFLAGS += -DMEMORY_STATS=if\(0\) -DMEMORY_STATS2=if\(0\)

INCLUDE += -I../bst
INCLUDE += -I../bslack_reuse
INCLUDE += -I../citrus
INCLUDE += -I../common
INCLUDE += -I../dcss_plus
INCLUDE += -I../descriptors
INCLUDE += -I../lazylist
INCLUDE += -I../lockfree_list
INCLUDE += -I../recordmgr
INCLUDE += -I../rlu
INCLUDE += -I../rlu_citrus
INCLUDE += -I../rlu_linked_list
INCLUDE += -I../skiplist_lock
INCLUDE += -I../rq
INCLUDE += -I../rq/snapcollector

#CFLAGS += -DSKIP_PERMUTATIONS
CFLAGS += $(INCLUDE) -DNOGRAPHITE=1 -O3 -DINDEX_STRUCT=IDX_$(dict) -DWORKLOAD=$(workload) #-Werror
CFLAGS += -DSEGREGATE_MALLOC
CFLAGS += $(readonly)
#CFLAGS += -DREAD_ONLY
#CFLAGS += -DVERBOSE_1
CFLAGS += -DHASH_PRIMARY_KEYS
CFLAGS += -DALIGNED_ALLOCATIONS
#CFLAGS += -DINDEX_NO_RECLAMATION
LDFLAGS = -L. -L./libs -pthread -g -lrt -std=c++0x -O3 -ldl
LDFLAGS += $(CFLAGS)

CPPS = $(foreach dir, $(SRC_DIRS), $(wildcard $(dir)*.cpp))
OBJS = $(foreach obj, $(CPPS:.cpp=.o), $(odir)/$(obj))

dir_guard=@mkdir -p $(@D)

.PHONY: all clean
all: $(bindir)/rundb_$(workload2)_$(dict).out

$(bindir)/rundb_$(workload2)_$(dict).out: $(OBJS)
	$(dir_guard)
	$(CC) -o $@ $^ $(LDFLAGS)

$(odir)/%.o: %.cpp
	$(dir_guard)
	$(CC) -c $(CFLAGS) -o $@ $<

clean:
	@rm -f $(bindir)/rundb_$(workload2)_$(dict).out
	@rm -r -f $(odir)
