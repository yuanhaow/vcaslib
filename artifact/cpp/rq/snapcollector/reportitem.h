/* 
 * This is an implementation of the linearizable Snap Collector object,
 * which was introduced by Petrank and Timnat at DISC 2015 in the paper
 * "Lock-free Data Structure Iterators".
 * 
 * Author: Trevor Brown
 * 
 * This C++ implementation is based on the Java implementation by Shahar Timnat.
 * Of course, this version has to perform memory reclamation manually,
 * whereas the original used automatic garbage collection.
 * (This was particularly tricky when it came to reclaiming reports added to
 *  the Snap Collector after report "blocker" objects.)
 * Memory is reclaimed using DEBRA: distributed epoch-based reclamation.
 *
 * Created on June 21, 2017, 4:47 PM
 */

#ifndef REPORTITEM_H
#define REPORTITEM_H

enum ReportType {Add, Remove};

static int getOrdinalForReportType(ReportType t) {
    return (t == ReportType::Add);
}

class ReportItem {
public:
    void * node;
    ReportType t;
    ReportItem * volatile next;
    int key;
    int id;
    
    ReportItem() {}
    void init(void * node, ReportType t, int key) {
        this->node = node;
        this->t = t;
        next = NULL;
        this->key = key;
        id = 0;
    }
};

class CompactReportItem {
public:
    void * node;
    ReportType t;
    int key;
    int id;
    
    CompactReportItem() {}
    void init(void * node, ReportType t, int key) {
        this->node = node;
        this->t = t;
        this->key = key;
        id = 0;
    }
};

struct {
    bool operator()(CompactReportItem * a, CompactReportItem * b) const {
        if (a->key != b->key) return a->key < b->key;
        if (a->node != b->node) return (long long) a->node < (long long) b->node;
        return getOrdinalForReportType(a->t) < getOrdinalForReportType(b->t);
    }
} compareCRI;

#endif /* REPORTITEM_H */

