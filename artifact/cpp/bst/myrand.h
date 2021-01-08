#ifndef UTILS_H
#define UTILS_H

namespace myrand {
  thread_local static unsigned long x=123456789, y=362436069, z=521288629;

  unsigned long get_rand(void) {          //period 2^96-1
    unsigned long t;
    //x += utils::my_id;
    x ^= x << 16;
    x ^= x >> 5;
    x ^= x << 1;

    t = x;
    x = y;
    y = z;
    z = t ^ x ^ y;

    return z;
  }
}
#endif /* UTILS_H */
