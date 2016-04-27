#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/kprobes.h>
#include <net/tcp.h>

static void j_hstcp_cong_avoid(struct sock *sk, u32 ack, u32 acked)
{
	pr_info("hstcp_cong_avoid\n");

	jprobe_return();
}

static struct jprobe hstcp_cong_avoid_jprobe = {
	.entry			= j_hstcp_cong_avoid,
	.kp = {
		.symbol_name	= "hstcp_cong_avoid",
	},
};

static int __init hstcp_cong_avoid_jprobe_init(void)
{
	int ret;

	ret = register_jprobe(&hstcp_cong_avoid_jprobe);
	if (ret < 0) {
		printk(KERN_INFO "register_jprobe hstcp_cong_avoid failed, returned %d\n", ret);
		return -1;
	}
	printk(KERN_INFO "Planted hstcp_cong_avoid_jprobe at %p, handler addr %p\n",
	       hstcp_cong_avoid_jprobe.kp.addr, hstcp_cong_avoid_jprobe.entry);
	return 0;
}

static void __exit hstcp_cong_avoid_jprobe_exit(void)
{
	unregister_jprobe(&hstcp_cong_avoid_jprobe);
	printk(KERN_INFO "hstcp_cong_avoid_jprobe at %p unregistered\n", hstcp_cong_avoid_jprobe.kp.addr);
}

module_init(hstcp_cong_avoid_jprobe_init)
module_exit(hstcp_cong_avoid_jprobe_exit)
MODULE_LICENSE("GPL");
