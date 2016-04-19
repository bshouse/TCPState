#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/kprobes.h>
#include <net/tcp.h>

static void j_tcp_cong_avoid_ai(struct tcp_sock *tp, u32 w, u32 acked)
{
	pr_info("tcp_cong_avoid_ai\n");

	jprobe_return();
}

static struct jprobe tcp_cong_avoid_ai_jprobe = {
	.entry			= j_tcp_cong_avoid_ai,
	.kp = {
		.symbol_name	= "tcp_cong_avoid_ai",
	},
};

static int __init tcp_cong_avoid_ai_jprobe_init(void)
{
	int ret;

	ret = register_jprobe(&tcp_cong_avoid_ai_jprobe);
	if (ret < 0) {
		printk(KERN_INFO "register_jprobe tcp_cong_avoid_ai failed, returned %d\n", ret);
		return -1;
	}
	printk(KERN_INFO "Planted tcp_cong_avoid_ai_jprobe at %p, handler addr %p\n",
	       tcp_cong_avoid_ai_jprobe.kp.addr, tcp_cong_avoid_ai_jprobe.entry);
	return 0;
}

static void __exit tcp_cong_avoid_ai_jprobe_exit(void)
{
	unregister_jprobe(&tcp_cong_avoid_ai_jprobe);
	printk(KERN_INFO "tcp_cong_avoid_ai_jprobe at %p unregistered\n", tcp_cong_avoid_ai_jprobe.kp.addr);
}

module_init(tcp_cong_avoid_ai_jprobe_init)
module_exit(tcp_cong_avoid_ai_jprobe_exit)
MODULE_LICENSE("GPL");
