/* 
 * File:   error.h
 * Author: trbot
 *
 * Created on May 28, 2017, 3:11 PM
 */

#ifndef ERROR_H
#define ERROR_H

#include <iostream>
#include <unistd.h>
using namespace std;

#define error(x) { cout<<"ERROR: "<<(x)<<endl; exit(-1); }

#endif /* ERROR_H */

