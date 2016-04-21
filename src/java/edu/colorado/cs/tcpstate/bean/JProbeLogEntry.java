package edu.colorado.cs.tcpstate.bean;

import java.text.SimpleDateFormat;

public class JProbeLogEntry {
	public static final SimpleDateFormat TIME24 = new SimpleDateFormat("HH:mm:ss");
	public static final SimpleDateFormat TIME12 = new SimpleDateFormat("hh:mm:ss");
	public static final String NA = new String("-");
	protected String time;
	protected long microseconds;
	protected String message;
	protected boolean isCongestionControl=true;
	protected String entry;
	
	public JProbeLogEntry(String text) throws Exception {
		entry=text=text.trim();
		String[] chunks = text.split(" ");
		String mis;
		if(!chunks[5].equals("[")) {
			//No Leading space in time
			mis = chunks[5].replace(".", "").substring(1, 7);
			message = chunks[6];
			time = chunks[2]+"."+mis+"["+chunks[5].substring(8);
			
		} else {
			//Leading space in time
			mis = chunks[6].replace(".", "").substring(0, 6);
			message = chunks[7];
			time = chunks[2]+"."+mis+"["+chunks[6].substring(7);
		}
		microseconds = JProbeLogEntry.TIME24.parse(chunks[2]).getTime()*1000 + Long.parseLong(mis);
		if(message.endsWith("slow_start")) {
			isCongestionControl=false;
		}		
	}

	public String getTime() {
		return time;
	}

	public void setTime(String time) {
		this.time = time;
	}

	public long getMicroseconds() {
		return microseconds;
	}

	public void setMicroseconds(long microseconds) {
		this.microseconds = microseconds;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
	public String toString() {
		return time+": "+message +" ["+entry+"]";
	}

	public boolean isCongestionControl() {
		return isCongestionControl;
	}

	public String getEntry() {
		return entry;
	}

}
