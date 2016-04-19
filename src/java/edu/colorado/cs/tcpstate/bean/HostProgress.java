package edu.colorado.cs.tcpstate.bean;

import java.text.ParseException;

public class HostProgress extends TcpDumpEntry {
	public HostProgress(String text) throws ParseException {
		super(text);
	}
	
	public String getKey() {

		if(source.compareTo(destination) > 0) {
			return source+destination;
		}
		return destination+source;
	}

	
}
