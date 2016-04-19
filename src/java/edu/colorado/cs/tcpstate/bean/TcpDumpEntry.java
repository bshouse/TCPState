package edu.colorado.cs.tcpstate.bean;

import java.text.ParseException;
import java.text.SimpleDateFormat;

public class TcpDumpEntry {

	public static final SimpleDateFormat TIME24 = new SimpleDateFormat("HH:mm:ss");
	public static final SimpleDateFormat TIME12 = new SimpleDateFormat("hh:mm:ss");
	public static final String NA = new String("-");
	protected String time;
	protected long microseconds;
	protected String source;
	protected String destination;
	protected String flags;
	protected String sequence = NA;
	protected long sequenceStart=0;
	protected long sequenceEnd=0;
	protected long ack = -1;
	protected int sequenceLength = 0;
	protected int windowSize = 0;
	 
	
	//String[] chunks;
	//String[] event;
	
	public TcpDumpEntry(String text) throws ParseException {
		String[] chunks = text.trim().split(", ");
		String[] event = chunks[0].split(" ");
		time = event[0];
		String[] timeMicro = event[0].split("\\.");
		microseconds = (TcpDumpEntry.TIME24.parse(timeMicro[0]).getTime()*1000) + Long.parseLong(timeMicro[1]);
		source = event[4];
		destination = event[2]+":";
		flags = event[6];
		for(int x = 1; x < 5; x++) {
			if(chunks[x].startsWith("seq ")) {
				sequence = chunks[x].substring(4);
				
			} else if(chunks[x].startsWith("ack ")) {
				ack = Long.parseLong(chunks[x].substring(4));
				
			} else if(chunks[x].startsWith("win ")) {
				windowSize = Integer.parseInt(chunks[x].substring(4));
				break;
			}
		}
		if(sequence != NA) {
			String[] nums = sequence.split(":");
			if(nums.length == 2) {
				sequenceEnd=Long.parseLong(nums[1]);
				sequenceStart=Long.parseLong(nums[0]);
			} else {
				sequenceEnd=sequenceStart=Long.parseLong(nums[0]);
			}
		}

		/*
		for(int x = 0; x < chunks.length; x++) {
			System.out.println("chunks["+x+"]="+chunks[x]);
		}

		for(int x = 0; x < event.length; x++) {
			System.out.println("event["+x+"]="+event[x]);
		}

		 
		 */

				
	}
	public String getTime() {
		return time;
	}
	public int getWindowSize() {
		return windowSize;	
	}
	public long getAck() {
		return ack;
	}
	public String getSequence() {
		return sequence;
	}
	public long getSequenceSize() {
		if(sequence != NA) {
			if(sequenceLength == 0) {
				sequenceLength= (int) (sequenceEnd - sequenceStart - 1);
			}
			return sequenceLength;
		}
		return 0;
	}
	public boolean sequenceContains(long sequenceNumber) {
		if(sequenceNumber >= sequenceStart && sequenceNumber < sequenceEnd) {
			return true;
		}
		return false;
	}
	public String getSource() {
		return source;
	}
	public String getDestination() {
		return destination;
	}
	
	public String getFlags() {
		return flags;
	}
	public long getMicroseconds() {
		return microseconds;
	}
	public void setTimestamp(long timestamp) {
		this.microseconds = timestamp;
	}	
}
