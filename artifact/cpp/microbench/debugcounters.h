/* 
 * File:   debugcounters.h
 * Author: trbot
 *
 * Created on January 26, 2015, 2:22 PM
 */

#ifndef DEBUGCOUNTERS_H
#define	DEBUGCOUNTERS_H

#include <string>
#include <sstream>
#include "globals_extern.h"
#include "recordmgr/debugcounter.h"
using namespace std;

class debugCounters {
public:
    const int NUM_PROCESSES;
    debugCounter * const insertSuccess;
    debugCounter * const insertFail;
    debugCounter * const eraseSuccess;
    debugCounter * const eraseFail;
    debugCounter * const findSuccess;
    debugCounter * const findFail;
    debugCounter * const rqSuccess;
    debugCounter * const rqFail;
    debugCounter * garbage;
    void clear() {
        insertSuccess->clear();
        insertFail->clear();
        eraseSuccess->clear();
        eraseFail->clear();
        findSuccess->clear();
        findFail->clear();
        rqSuccess->clear();
        rqFail->clear();
        garbage->clear();
    }
    debugCounters(const int numProcesses) :
            NUM_PROCESSES(numProcesses),
            insertSuccess(new debugCounter(numProcesses)),
            insertFail(new debugCounter(numProcesses)),
            eraseSuccess(new debugCounter(numProcesses)),
            eraseFail(new debugCounter(numProcesses)),
            findSuccess(new debugCounter(numProcesses)),
            findFail(new debugCounter(numProcesses)),
            rqSuccess(new debugCounter(numProcesses)),
            rqFail(new debugCounter(numProcesses)),
            garbage(new debugCounter(numProcesses)) {
    }
    ~debugCounters() {
        delete insertSuccess;
        delete insertFail;
        delete eraseSuccess;
        delete eraseFail;
        delete findSuccess;
        delete findFail;
        delete rqSuccess;
        delete rqFail;
        delete garbage;
    }
};

#endif	/* DEBUGCOUNTERS_H */

