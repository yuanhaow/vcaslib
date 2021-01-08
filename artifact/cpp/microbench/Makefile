include ../config.mk
PLAF = -DLOGICAL_PROCESSORS=$(maxthreads_powerof2) -DMAX_TID_POW2=$(maxthreads_powerof2) -DCPU_FREQ_GHZ=$(cpu_freq_ghz) -DCPU_FREQ=$(cpu_freq_ghz)

GPP = g++-9
FLAGS = -std=c++11 -mcx16 -O3
FLAGS += -g
FLAGS += -DNDEBUG
FLAGS += $(PLAF)
FLAGS += -DUSE_GSTATS
#FLAGS += -DNO_FREE
#FLAGS += -DUSE_STL_HASHLIST
FLAGS += -DUSE_SIMPLIFIED_HASHLIST
#FLAGS += -DRAPID_RECLAMATION
#FLAGS += -DRWLOCK_PTHREADS
#FLAGS += -DRWLOCK_FAVOR_WRITERS
FLAGS += -DRWLOCK_FAVOR_READERS
#FLAGS += -DRWLOCK_COHORT_FAVOR_WRITERS
#FLAGS += -DSNAPCOLLECTOR_PRINT_RQS
#FLAGS += -DUSE_RQ_DEBUGGING -DRQ_VALIDATION
#FLAGS += -DRQ_VISITED_IN_BAGS_HISTOGRAM
#FLAGS += -DRQ_HISTOGRAM
#FLAGS += -DADD_DELAY_BEFORE_DTIME
#FLAGS += -DRQ_LOCKFREE_WAITS_FOR_DTIME
#FLAGS += -DUSE_DEBUGCOUNTERS
#FLAGS += -DBLOCKBAG_ITERATOR_COUNT_BLOCKS_TRAVERSED
#FLAGS += -DDEBUG=if\(1\) -DDEBUG1=if\(1\) -DDEBUG2=if\(1\)
#FLAGS += -DDEBUG=if\(1\) -DDEBUG1=if\(0\) -DDEBUG2=if\(0\)
FLAGS += -DDEBUG=if\(0\) -DDEBUG1=if\(0\) -DDEBUG2=if\(0\)
FLAGS += -DMEMORY_STATS=if\(1\) -DMEMORY_STATS2=if\(1\)
#FLAGS += -DMEMORY_STATS=if\(0\) -DMEMORY_STATS2=if\(0\)
FLAGS += -DINSERT_FUNC=insertIfAbsent
#FLAGS += -DUSE_PAPI

#FLAGS += -DDEBRA_DISABLE_READONLY_OPT
#FLAGS += -DEERPRCU_DISABLE_RDTSC_TTAS
#FLAGS += -DSKIPLIST_DEBUGGING_FLATTEN_MAX_LEVEL

LDFLAGS = -L../lib
LDFLAGS += -I../
LDFLAGS += -I../bst
LDFLAGS += -I../bslack_reuse
LDFLAGS += -I../citrus
LDFLAGS += -I../common
LDFLAGS += -I../dcss_plus
LDFLAGS += -I../descriptors
LDFLAGS += -I../include
LDFLAGS += -I../lazylist
LDFLAGS += -I../lockfree_list
LDFLAGS += -I../lockfree_skiplist
LDFLAGS += -I../skiplist_lock
LDFLAGS += -I../microbench
LDFLAGS += -I../recordmgr
LDFLAGS += -I../rlu
LDFLAGS += -I../rlu_citrus
LDFLAGS += -I../rlu_linked_list
LDFLAGS += -I../rq
LDFLAGS += -I../rq/snapcollector

LDFLAGS += -lpthread
LDFLAGS += -ldl
# LDFLAGS += -lnuma

# LDFLAGS += -I`jemalloc-config --includedir` \
# -L`jemalloc-config --libdir` -Wl,-rpath,`jemalloc-config --libdir` \
# -ljemalloc `jemalloc-config --libs`

# LDFLAGS += -lpapi

machine=$(shell hostname)

all: abtree bslack bst lazylist lflist citrus rlu skiplistlock vcasbst

thispath=

.PHONY: abtree abtree.rq_lockfree abtree.rq_rwlock abtree.rq_htm_rwlock abtree.rq_unsafe
abtree: abtree.rq_lockfree abtree.rq_rwlock abtree.rq_htm_rwlock abtree.rq_unsafe
abtree.rq_lockfree:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DABTREE -DRQ_LOCKFREE $(pinning) $(thispath)main.cpp $(LDFLAGS)
abtree.rq_rwlock:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DABTREE -DRQ_RWLOCK $(pinning) $(thispath)main.cpp $(LDFLAGS)
abtree.rq_htm_rwlock:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DABTREE -DRQ_HTM_RWLOCK $(pinning) $(thispath)main.cpp $(LDFLAGS)
abtree.rq_unsafe:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DABTREE -DRQ_UNSAFE $(pinning) $(thispath)main.cpp $(LDFLAGS)

.PHONY: bslack bslack.rq_lockfree bslack.rq_rwlock bslack.rq_htm_rwlock bslack.rq_unsafe
bslack: bslack.rq_lockfree bslack.rq_rwlock bslack.rq_htm_rwlock bslack.rq_unsafe
bslack.rq_lockfree:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DBSLACK -DRQ_LOCKFREE $(pinning) $(thispath)main.cpp $(LDFLAGS)
bslack.rq_rwlock:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DBSLACK -DRQ_RWLOCK $(pinning) $(thispath)main.cpp $(LDFLAGS)
bslack.rq_htm_rwlock:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DBSLACK -DRQ_HTM_RWLOCK $(pinning) $(thispath)main.cpp $(LDFLAGS)
bslack.rq_unsafe:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DBSLACK -DRQ_UNSAFE $(pinning) $(thispath)main.cpp $(LDFLAGS)

.PHONY: lazylist lazylist.rq_lockfree lazylist.rq_rwlock lazylist.rq_htm_rwlock lazylist.rq_unsafe
lazylist: lazylist.rq_lockfree lazylist.rq_rwlock lazylist.rq_htm_rwlock lazylist.rq_unsafe
lazylist.rq_lockfree:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DLAZYLIST -DRQ_LOCKFREE $(pinning) $(thispath)main.cpp $(LDFLAGS)
lazylist.rq_rwlock:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DLAZYLIST -DRQ_RWLOCK $(pinning) $(thispath)main.cpp $(LDFLAGS)
lazylist.rq_htm_rwlock:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DLAZYLIST -DRQ_HTM_RWLOCK $(pinning) $(thispath)main.cpp $(LDFLAGS)
lazylist.rq_unsafe:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DLAZYLIST -DRQ_UNSAFE $(pinning) $(thispath)main.cpp $(LDFLAGS)

.PHONY: lflist lflist.rq_lockfree lflist.rq_rwlock lflist.rq_htm_rwlock lflist.rq_unsafe lflist.rq_snapcollector
lflist: lflist.rq_lockfree lflist.rq_rwlock lflist.rq_htm_rwlock lflist.rq_unsafe lflist.rq_snapcollector
lflist.rq_lockfree:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DLFLIST -DRQ_LOCKFREE $(pinning) $(thispath)main.cpp $(LDFLAGS)
lflist.rq_rwlock:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DLFLIST -DRQ_RWLOCK $(pinning) $(thispath)main.cpp $(LDFLAGS)
lflist.rq_htm_rwlock:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DLFLIST -DRQ_HTM_RWLOCK $(pinning) $(thispath)main.cpp $(LDFLAGS)
lflist.rq_unsafe:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DLFLIST -DRQ_UNSAFE $(pinning) $(thispath)main.cpp $(LDFLAGS)
lflist.rq_snapcollector:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DLFLIST -DRQ_SNAPCOLLECTOR $(pinning) $(thispath)main.cpp $(LDFLAGS)

.PHONY: skiplistlock skiplistlock.rq_lockfree skiplistlock.rq_rwlock skiplistlock.rq_htm_rwlock skiplistlock.rq_unsafe skiplistlock.rq_snapcollector
skiplistlock: skiplistlock.rq_lockfree skiplistlock.rq_rwlock skiplistlock.rq_htm_rwlock skiplistlock.rq_unsafe skiplistlock.rq_snapcollector
skiplistlock.rq_lockfree:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DSKIPLISTLOCK -DRQ_LOCKFREE $(pinning) $(thispath)main.cpp $(LDFLAGS)
skiplistlock.rq_rwlock:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DSKIPLISTLOCK -DRQ_RWLOCK $(pinning) $(thispath)main.cpp $(LDFLAGS)
skiplistlock.rq_htm_rwlock:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DSKIPLISTLOCK -DRQ_HTM_RWLOCK $(pinning) $(thispath)main.cpp $(LDFLAGS)
skiplistlock.rq_unsafe:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DSKIPLISTLOCK -DRQ_UNSAFE $(pinning) $(thispath)main.cpp $(LDFLAGS)
skiplistlock.rq_snapcollector:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DSKIPLISTLOCK -DRQ_SNAPCOLLECTOR $(pinning) $(thispath)main.cpp $(LDFLAGS)

.PHONY: bst bst.rq_lockfree bst.rq_rwlock bst.rq_htm_rwlock bst.rq_unsafe bst.rq_vcas
bst: bst.rq_lockfree bst.rq_rwlock bst.rq_htm_rwlock bst.rq_unsafe bst.rq_vcas
bst.rq_lockfree:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DBST -DRQ_LOCKFREE $(pinning) $(thispath)main.cpp $(LDFLAGS)
bst.rq_rwlock:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DBST -DRQ_RWLOCK $(pinning) $(thispath)main.cpp $(LDFLAGS)
bst.rq_htm_rwlock:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DBST -DRQ_HTM_RWLOCK $(pinning) $(thispath)main.cpp $(LDFLAGS)
bst.rq_unsafe:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DBST -DRQ_UNSAFE $(pinning) $(thispath)main.cpp $(LDFLAGS)

.PHONY: vcasbst
vcasbst:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DVCASBST -DRQ_VCAS $(pinning) $(thispath)main.cpp $(LDFLAGS)

.PHONY: citrus citrus.rq_lockfree citrus.rq_rwlock citrus.rq_htm_rwlock citrus.rq_unsafe
citrus: citrus.rq_lockfree citrus.rq_rwlock citrus.rq_htm_rwlock citrus.rq_unsafe
citrus.rq_lockfree:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DCITRUS -DRQ_LOCKFREE $(pinning) $(thispath)main.cpp $(LDFLAGS)
citrus.rq_rwlock:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DCITRUS -DRQ_RWLOCK $(pinning) $(thispath)main.cpp $(LDFLAGS)
citrus.rq_htm_rwlock:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DCITRUS -DRQ_HTM_RWLOCK $(pinning) $(thispath)main.cpp $(LDFLAGS)
citrus.rq_unsafe:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DCITRUS -DRQ_UNSAFE $(pinning) $(thispath)main.cpp $(LDFLAGS)

.PHONY: rlu lazylist.rq_rlu citrus.rq_rlu
rlu: lazylist.rq_rlu citrus.rq_rlu
lazylist.rq_rlu:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DRLU_LIST $(pinning) $(thispath)main.cpp $(thispath)../rlu/rlu.cpp $(LDFLAGS)
citrus.rq_rlu:
	$(GPP) $(FLAGS) -o $(thispath)$(machine).$@$(filesuffix).out $(xargs) -DRLU_CITRUS $(pinning) $(thispath)main.cpp $(thispath)../rlu/rlu.cpp $(LDFLAGS)
