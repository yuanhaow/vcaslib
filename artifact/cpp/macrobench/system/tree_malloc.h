#pragma once 

// Infrastructure for separating index-related allocations for the rest
// of the system allocation.

class tree_malloc {
	static void* (*allocnodefn)(size_t size);
	static void* (*allocfn)(size_t size);
	static void (*freefn)(void *ptr);

public:
	static void* allocnode(size_t size);
	static void* alloc(size_t size);
	static void free(void *ptr);
	static void init();

};


