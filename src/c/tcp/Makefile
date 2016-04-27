obj-m := jprobe_tcp_slow_start.o jprobe_tcp_cong_avoid_ai.o
IDIR= /lib/modules/$(shell uname -r)/kernel/net/ipv4/
KDIR := /lib/modules/$(shell uname -r)/build
PWD := $(shell pwd)

default:
	$(MAKE) -C $(KDIR) SUBDIRS=$(PWD) modules

clean:
	rm -rf Module.markers modules.order Module.symvers jprobe_tcp_cong_avoid_ai.ko jprobe_tcp_cong_avoid_ai.mod.c jprobe_tcp_cong_avoid_ai.mod.o jprobe_tcp_cong_avoid_ai.o .jprobe_tcp_cong_avoid_ai.ko.cmd .jprobe_tcp_cong_avoid_ai.mod.o.cmd .jprobe_tcp_cong_avoid_ai.o.cmd jprobe_tcp_slow_start.ko jprobe_tcp_slow_start.mod.c jprobe_tcp_slow_start.mod.o jprobe_tcp_slow_start.o .jprobe_tcp_slow_start.ko.cmd .jprobe_tcp_slow_start.mod.o.cmd .jprobe_tcp_slow_start.o.cmd .tmp_versions
