#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/kprobes.h>
#include <net/tcp.h>

static void j_tcp_reno_cong_avoid(struct sock *sk, u32 ack, u32 acked)
{
	pr_info("tcp_reno_cong_avoid\n");

	jprobe_return();
}

static struct jprobe tcp_reno_cong_avoid_jprobe = {
	.entry			= j_tcp_reno_cong_avoid,
	.kp = {
		.symbol_name	= "tcp_reno_cong_avoid",
	},
};

static int __init tcp_reno_cong_avoid_jprobe_init(void)
{
	int ret;

	ret = register_jprobe(&tcp_reno_cong_avoid_jprobe);
	if (ret < 0) {
		printk(KERN_INFO "register_jprobe tcp_reno_cong_avoid failed, returned %d\n", ret);
		return -1;
	}
	printk(KERN_INFO "Planted tcp_reno_cong_avoid_jprobe at %p, handler addr %p\n",
	       tcp_reno_cong_avoid_jprobe.kp.addr, tcp_reno_cong_avoid_jprobe.entry);
	return 0;
}

static void __exit tcp_reno_cong_avoid_jprobe_exit(void)
{
	unregister_jprobe(&tcp_reno_cong_avoid_jprobe);
	printk(KERN_INFO "tcp_reno_cong_avoid_jprobe at %p unregistered\n", tcp_reno_cong_avoid_jprobe.kp.addr);
}

module_init(tcp_reno_cong_avoid_jprobe_init)
module_exit(tcp_reno_cong_avoid_jprobe_exit)
MODULE_LICENSE("GPL");
