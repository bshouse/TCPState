package edu.colorado.cs.tcpstate.bean;

public class TcpTimestamp {
	
	private long tcpTimestamp=0L;
	private long microseconds=0L;
	
	public TcpTimestamp(long eventMicroseconds, long tcpTimestamp) {
		this.microseconds=eventMicroseconds;
		this.tcpTimestamp=tcpTimestamp;
	}

	public long getTcpTimestamp() {
		return tcpTimestamp;
	}
	public long getMicroseconds() {
		return microseconds;
	}
	
}
