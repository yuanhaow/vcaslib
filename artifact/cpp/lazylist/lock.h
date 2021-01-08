/* 
 * File:   lock.h
 * Author: trbot
 *
 * Created on February 17, 2016, 7:28 PM
 */

#ifndef LOCK_H
#define	LOCK_H

#include <pthread.h>
typedef pthread_mutex_t lock_type;
#define INIT_LOCK(lock)				pthread_mutex_init((pthread_mutex_t *) lock, NULL);
#define DESTROY_LOCK(lock)			pthread_mutex_destroy((pthread_mutex_t *) lock)
#define LOCK(lock)					pthread_mutex_lock((pthread_mutex_t *) lock)
#define UNLOCK(lock)					pthread_mutex_unlock((pthread_mutex_t *) lock)

#endif	/* LOCK_H */

