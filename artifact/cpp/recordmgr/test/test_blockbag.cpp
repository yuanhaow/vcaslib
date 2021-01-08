/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/* 
 * File:   test_blockbag.cpp
 * Author: trbot
 *
 * Created on April 24, 2017, 3:25 PM
 */

#include <cstdlib>
#include <iostream>
#include "blockbag.h"
#include "blockpool.h"

using namespace std;

/*
 * 
 */
int main(int argc, char** argv) {
    if (argc != 2) {
        cout<<"USAGE: "<<argv[0]<<" NUMBER_OF_DATA_ITEMS_TO_TEST"<<endl;
        exit(-1);
    }
    int n = atoi(argv[1]);
    
    blockpool<int> bpool;
    blockbag<int> bag (&bpool);
    
    int * data = new int[n];
    for (int i=0;i<n;++i) {
        data[i] = i;
        bag.add(&data[i]);
    }
    
    for (blockbag_iterator<int> it = bag.begin(); it != bag.end(); it++) {
        cout<<*(*it)<<" ";
    }
    cout<<endl;
    
    delete[] data;
    return 0;
}

