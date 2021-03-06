package edu.colorado.cs.tcpstate.bean;

import java.text.NumberFormat;
import java.util.Locale;

public class TransitionResult {

	private static final NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
	private Transition tcpdump;
	private Transition jprobeStart;
	private Transition jprobeEnd;
	
	public TransitionResult(Transition JProbeStart, Transition JProbeEnd, Transition TcpDump) {
		jprobeStart = JProbeStart;
		jprobeEnd = JProbeEnd;
		tcpdump=TcpDump;
	}
	
	public Transition getTcpdump() {
		return tcpdump;
	}
	public void setTcpdump(Transition tcpdump) {
		this.tcpdump = tcpdump;
	}
	public Transition getJprobeStart() {
		return jprobeStart;
	}
	public void setJprobeStart(Transition jprobeStart) {
		this.jprobeStart = jprobeStart;
	}
	public Transition getJprobeEnd() {
		return jprobeEnd;
	}
	public void setJprobeEnd(Transition jprobeEnd) {
		this.jprobeEnd = jprobeEnd;
	}
	
	public String getResult() {

		if(jprobeStart == null) {
			if(jprobeEnd == null) {
				return ("Overboard: TcpDump("+Connection.getStateString(tcpdump.getConnectionState())+") - "+tcpdump.getTransitionReason());
			} else {
				jprobeEnd = jprobeStart;
			}
		} else if(jprobeEnd == null) {
			jprobeEnd = jprobeStart;
		}
		

		int connectionState = jprobeStart.getConnectionState();
		if(jprobeStart.getConnectionState() != jprobeEnd.getConnectionState()) {
			return new String("Mismatch: JProbe Start("+Connection.getStateString(connectionState)+") End("+Connection.getStateString(jprobeEnd.getConnectionState())+")");
		}
		if(tcpdump == null) {
			return new String("Missed("+Connection.getStateString(connectionState)+"): "+jprobeStart.getTime()+" - "+jprobeEnd.getTime()+ " = "+nf.format(getMicrosecondsInTransition())+" microseconds");
		}
		if(connectionState != tcpdump.getConnectionState()) {
			return new String("Mismatch: JProbe ("+Connection.getStateString(connectionState)+") TcpDump ("+Connection.getStateString(tcpdump.getConnectionState())+")");
		}
		
		return new String("Match("+Connection.getStateString(connectionState)+"): "+nf.format(tcpdump.getMicroseconds() - jprobeStart.getMicroseconds())+" microseconds after sender state change ["+tcpdump.getTransitionReason()+"]");
	}
	public long getMicrosecondsInTransition() {
		return jprobeEnd.getMicroseconds() - jprobeStart.getMicroseconds();
	}
}
