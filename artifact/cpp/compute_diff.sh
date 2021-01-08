#!/bin/bash

diff bst/node.h vcasbst/vcas_node.h | grep "^>" | wc -l
diff bst/bst.h vcasbst/vcas_bst.h | grep "^>" | wc -l
diff bst/bst_impl.h vcasbst/vcas_bst_impl.h | grep "^>" | wc -l
diff bst/scxrecord.h vcasbst/vcas_scxrecord.h | grep "^>" | wc -l

diff rq/rq_unsafe.h rq/rq_vcas.h | grep "^>" | wc -l
