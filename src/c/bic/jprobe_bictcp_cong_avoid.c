#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/kprobes.h>
#include <net/tcp.h>

static void j_bictcp_cong_avoid(struct sock *sk, u32 ack, u32 acked)
{
	pr_info("bictcp_cong_avoid\n");

	jprobe_return();
}

static struct jprobe bictcp_cong_avoid_jprobe = {
	.entry			= j_bictcp_cong_avoid,
	.kp = {
		.symbol_name	= "bictcp_cong_avoid",
	},
};

static int __init bictcp_cong_avoid_jprobe_init(void)
{
	int ret;

	ret = register_jprobe(&bictcp_cong_avoid_jprobe);
	if (ret < 0) {
		printk(KERN_INFO "register_jprobe bictcp_cong_avoid failed, returned %d\n", ret);
		return -1;
	}
	printk(KERN_INFO "Planted bictcp_cong_avoid_jprobe at %p, handler addr %p\n",
	       bictcp_cong_avoid_jprobe.kp.addr, bictcp_cong_avoid_jprobe.entry);
	return 0;
}

static void __exit bictcp_cong_avoid_jprobe_exit(void)
{
	unregister_jprobe(&bictcp_cong_avoid_jprobe);
	printk(KERN_INFO "bictcp_cong_avoid_jprobe at %p unregistered\n", bictcp_cong_avoid_jprobe.kp.addr);
}

module_init(bictcp_cong_avoid_jprobe_init)
module_exit(bictcp_cong_avoid_jprobe_exit)
MODULE_LICENSE("GPL");
