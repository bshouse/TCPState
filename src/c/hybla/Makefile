obj-m := jprobe_hybla_cong_avoid.o
IDIR= /lib/modules/$(shell uname -r)/kernel/net/ipv4/
KDIR := /lib/modules/$(shell uname -r)/build
PWD := $(shell pwd)

default:
	$(MAKE) -C $(KDIR) SUBDIRS=$(PWD) modules

clean:
	rm -rf Module.markers modules.order Module.symvers jprobe_hybla_cong_avoid.ko jprobe_hybla_cong_avoid.mod.c jprobe_hybla_cong_avoid.mod.o jprobe_hybla_cong_avoid.o .jprobe_hybla_cong_avoid.ko.cmd .jprobe_hybla_cong_avoid.mod.o.cmd .jprobe_hybla_cong_avoid.o.cmd .tmp_versions
