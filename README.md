# TCPState

A simple application to monitor TCPDump output and determine the sender's TCP state (slow start or congestion avoidance)

Run it: java TcpState [TcpDump file] {Jprobe file}

[Presentation](http://bunkrapp.com/present/98za43/)


# Output without JProbe (CUBIC dataset):
```
00:50:11.450030 :192.168.1.1.35476:192.168.1.2.5001: Handshake [Initial SYN]
00:50:11.693338 :192.168.1.1.35476:192.168.1.2.5001: Slow Start [Completion ACK]
00:50:11.693338 :192.168.1.1.35476:192.168.1.2.5001: Handshake RTT: 243,263 microseconds
00:50:11.757824 :192.168.1.1.35476:192.168.1.2.5001: Estimated RTT: 57,304 microseconds
00:50:12.045537 :192.168.1.1.35476:192.168.1.2.5001: Congestion Avoidance [Window Stabilized]
00:50:12.136465 :192.168.1.1.35476:192.168.1.2.5001: Estimated RTT: 89,987 microseconds
00:50:12.750778 :192.168.1.1.35476:192.168.1.2.5001: Estimated RTT: 459,172 microseconds
00:50:12.807348 :192.168.1.1.35476:192.168.1.2.5001: Estimated RTT: 56,516 microseconds
00:50:13.166491 :192.168.1.1.35476:192.168.1.2.5001: Estimated RTT: 122,550 microseconds
00:50:13.206913 :192.168.1.1.35476:192.168.1.2.5001: Estimated RTT: 61,031 microseconds
00:50:13.286681 :192.168.1.1.35476:192.168.1.2.5001: Estimated RTT: 127,131 microseconds
00:50:13.644705 :192.168.1.1.35476:192.168.1.2.5001: Estimated RTT: 58,571 microseconds
00:50:18.045444 :192.168.1.1.35476:192.168.1.2.5001: Estimated RTT: 90,860 microseconds
00:50:42.469547 :192.168.1.1.35476:192.168.1.2.5001: Closing [Sender FIN]
00:50:42.474204 :192.168.1.1.35476:192.168.1.2.5001: Closed [Receiver FIN]
```


# Output with JProbe (Vegas Dataset) :
```
08:31:51.750462 :71.212.166.51.48325:192.3.171.8.5001: Handshake [Initial SYN]
08:31:51.806818 :71.212.166.51.48325:192.3.171.8.5001: Slow Start [Completion ACK]
08:31:51.869110--------tcp_slow_start (22:31:51.117582[124316])
08:31:51.869110--------tcp_slow_start (22:31:51.117582[125554])
08:31:51.869110--------tcp_vegas_cong_avoid (22:31:51.117582[125560])
08:31:51.984041 :71.212.166.51.48325:192.3.171.8.5001: Congestion Avoidance [Window Stabilized]
08:32:52.865751--------tcp_vegas_cong_avoid (22:32:52.117643[067571])
08:32:52.883311 :71.212.166.51.48325:192.3.171.8.5001: Closing [Sender FIN]
08:32:52.891987 :71.212.166.51.48325:192.3.171.8.5001: Closed [Receiver FIN]

-------------SUMMARY-------------
Match(Slow Start): 0 microseconds after sender state change [Completion ACK]
Match(Congestion Avoidance): 177,223 microseconds after sender state change [Window Stabilized]
Total microseconds in wrong state: 177,223 (Permitted: 3,059,840 microseconds of 61,196,814 total connection time)
Accuracy: 99.71%
```
