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


# Output with JProbe (BIC Dataset) :
```
19:49:40.657956 :192.168.1.1.41962:192.168.1.2.5001: Handshake [Initial SYN]
19:49:40.718236 :192.168.1.1.41962:192.168.1.2.5001: Slow Start [Completion ACK]
19:49:40.718236 :192.168.1.1.41962:192.168.1.2.5001: Handshake RTT: 60,231 microseconds
19:49:40.781933--------tcp_slow_start (09:49:40.398704[08482])
19:49:40.781933--------tcp_slow_start (09:49:40.398705[84034])
19:49:40.781933--------bictcp_cong_avoid (09:49:40.398705[84208])
19:49:40.963454 :192.168.1.1.41962:192.168.1.2.5001: Congestion Avoidance [Window Stabilized]
19:49:41.298996 :192.168.1.1.41962:192.168.1.2.5001: Estimated RTT: 90,428 microseconds
19:50:11.093473 :192.168.1.1.41962:192.168.1.2.5001: Closing [Sender FIN]
19:50:11.098121 :192.168.1.1.41962:192.168.1.2.5001: Closed [Receiver FIN]
19:50:11.154345--------tcp_cong_avoid_ai (09:50:11.399007[76566])
-------------SUMMARY-------------
Match(Slow Start): 0 microseconds after sender state change [Completion ACK]
Match(Congestion Avoidance): 245,217 microseconds after sender state change [Window Stabilized]
Total microseconds in wrong state: 245,217 (Permitted: 1,524,819 microseconds of 30,496,389 total connection time)
Accuracy: 99.196%
```
