#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/kprobes.h>
#include <net/tcp.h>

static u32 j_tcp_slow_start(struct sock *sk, u32 ack, u32 acked)
{
	pr_info("tcp_slow_start\n");

	jprobe_return();
	return 0;
}

static struct jprobe tcp_slow_start_jprobe = {
	.entry			= j_tcp_slow_start,
	.kp = {
		.symbol_name	= "tcp_slow_start",
	},
};

static int __init tcp_slow_start_jprobe_init(void)
{
	int ret;

	ret = register_jprobe(&tcp_slow_start_jprobe);
	if (ret < 0) {
		printk(KERN_INFO "register_jprobe tcp_slow_start failed, returned %d\n", ret);
		return -1;
	}
	printk(KERN_INFO "Planted tcp_slow_start_jprobe at %p, handler addr %p\n",
	       tcp_slow_start_jprobe.kp.addr, tcp_slow_start_jprobe.entry);
	return 0;
}

static void __exit tcp_slow_start_jprobe_exit(void)
{
	unregister_jprobe(&tcp_slow_start_jprobe);
	printk(KERN_INFO "tcp_slow_start_jprobe at %p unregistered\n", tcp_slow_start_jprobe.kp.addr);
}

module_init(tcp_slow_start_jprobe_init)
module_exit(tcp_slow_start_jprobe_exit)
MODULE_LICENSE("GPL");
