#!/bin/bash

#Run this passing the server IP on the command line
#Ouput defaults to /tmp/

for cc in `cat /proc/sys/net/ipv4/tcp_available_congestion_control`
do 

	for t in 30 60 600
	do
		echo $cc for $t
		tail -n 0 -F /var/log/kern.log > /tmp/$cc.$t.$1.`date +%Y%m%d.%H%M%S`.log &
		TPID=$!
		iperf -t $t -Z $cc -c $1
		sleep 30
		kill $TPID

	done
done
