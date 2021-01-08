#include "thread_pinning.h"

namespace thread_pinning {

    using namespace std;

    cpu_set_t ** cpusets;
    int * customBinding;
    int numCustomBindings;

    void configurePolicy(const int numProcessors, string policy) {
        cpusets = new cpu_set_t * [numProcessors];
        customBinding = new int[numProcessors];
        parseCustom(policy);
        if (numCustomBindings > 0) {
            // create cpu sets for binding threads to cores
            int size = CPU_ALLOC_SIZE(numProcessors);
            for (int i=0;i<numProcessors;++i) {
                cpusets[i] = CPU_ALLOC(numProcessors);
                CPU_ZERO_S(size, cpusets[i]);
                CPU_SET_S(customBinding[i%numCustomBindings], size, cpusets[i]);
            }
        }
    }

    void bindThread(const int tid, const int nprocessors) {
        if (numCustomBindings > 0) {
            doBindThread(tid, nprocessors);
        }
    }

    int getActualBinding(const int tid, const int nprocessors) {
        int result = -1;
        if (numCustomBindings == 0) {
            return result;
        }
        unsigned bindings = 0;
        for (int i=0;i<nprocessors;++i) {
            if (CPU_ISSET_S(i, CPU_ALLOC_SIZE(nprocessors), cpusets[tid%nprocessors])) {
                result = i;
                ++bindings;
            }
        }
        if (bindings > 1) {
            cout<<"ERROR: "<<bindings<<" processor bindings for thread "<<tid<<endl;
            exit(-1);
        }
        return result;
    }

    bool isInjectiveMapping(const int nthreads, const int nprocessors) {
        if (numCustomBindings == 0) {
            return true;
        }
        bool covered[nprocessors];
        for (int i=0;i<nprocessors;++i) covered[i] = 0;
        for (int i=0;i<nthreads;++i) {
            int ix = getActualBinding(i, nprocessors);
            if (covered[ix]) return false;
            covered[ix] = 1;
        }
        return true;
    }
    
}