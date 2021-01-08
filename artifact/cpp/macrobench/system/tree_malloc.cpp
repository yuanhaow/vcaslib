#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <pthread.h>

#include "tree_malloc.h"
// Infrastructure for separating index-related allocations for the rest
// of the system allocation.

void* (*tree_malloc::allocnodefn)(size_t size);
void* (*tree_malloc::allocfn)(size_t size);
void  (*tree_malloc::freefn)(void *ptr);

void *tree_malloc::allocnode(size_t size)
{
	return allocnodefn ? allocnodefn(size) : malloc(size);
}

void *tree_malloc::alloc(size_t size)
{
	return allocfn ? allocfn(size) : malloc(size);
}

void tree_malloc::free(void *ptr)
{
	if (freefn)
		freefn(ptr);
	else
		::free(ptr);
}

static void* dummy_thr(void *p)
{
	return 0;
}

void tree_malloc::init(void)
{
#ifdef SEGREGATE_MALLOC
	char *lib = getenv("TREE_MALLOC");

	if (!lib) {
		printf("no TREE_MALLOC defined: using default!\n");
		return;
	}

	void *h = dlopen(lib, RTLD_NOW | RTLD_LOCAL);
	if (!h) {
		fprintf(stderr, "unable to load '%s': %s\n", lib, dlerror());
		exit(1);
	}

	// If the allocator exports pthread_create(), we assume it does so to detect
	// multi-threading (through interposition on pthread_create()) and so call
	// this function (since it might not be called otherwise, if the standard
	// allocator does a similar trick).
	int (*pthread_create)(pthread_t *, const pthread_attr_t *, void *(*) (void *), void *);
	pthread_create = (__typeof(pthread_create)) dlsym(h, "pthread_create");
	if (pthread_create) {
		pthread_t thr;
		pthread_create(&thr, NULL, dummy_thr, NULL);
		pthread_join(thr, NULL);
	}

	allocnodefn = (__typeof(allocnodefn)) dlsym(h, "malloc");
	if (!allocnodefn) {
		fprintf(stderr, "unable to resolve malloc\n");
		exit(1);
	}

	if (!getenv("TREE_MALLOC_NODES"))
		allocfn = allocnodefn;

	freefn = (__typeof(freefn)) dlsym(h, "free");
	if (!freefn) {
		fprintf(stderr, "unable to resolve free\n");
		exit(1);
	}
#endif
}

