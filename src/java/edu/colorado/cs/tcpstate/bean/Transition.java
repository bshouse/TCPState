package edu.colorado.cs.tcpstate.bean;

public class Transition {

	public static final int TRANSITION_SWITCH = 0;
	public static final int TRANSITION_IN = 1;
	public static final int TRANSITION_OUT = 2;
	
	private int transition;
	private String key;
	private String time;
	private long microseconds;
	private int connectionState;
	private String transitionReason;
	
	public Transition(String connectionKey, String transitionTime, long transitionMicroseconds, int state, String reason) {
		transition = TRANSITION_SWITCH;
		key=connectionKey;
		time=transitionTime;
		microseconds=transitionMicroseconds;
		connectionState=state;
		transitionReason=reason;
 
	}
	public Transition(HostProgress hp, int state, String reason) {
		transition = TRANSITION_SWITCH;
		key=hp.getKey();
		time=hp.getTime();
		microseconds=hp.getMicroseconds();
		connectionState=state;
		transitionReason=reason;
	}
	public Transition(String connectionKey, JProbeLogEntry jple, int state, int transitionDirection, String reason) {
		transition = transitionDirection;
		key=connectionKey;
		time=jple.getTime();
		microseconds=jple.getMicroseconds();
		connectionState=state;
		transitionReason=reason;
	}
	public String toString() {
		return new String(time+" :"+key+" "+Connection.getStateString(connectionState)+" ["+transitionReason+"]");
	}
	public static int getTransitionSwitch() {
		return TRANSITION_SWITCH;
	}
	public static int getTransitionIn() {
		return TRANSITION_IN;
	}
	public static int getTransitionOut() {
		return TRANSITION_OUT;
	}
	public int getTransition() {
		return transition;
	}
	public String getKey() {
		return key;
	}
	public String getTime() {
		return time;
	}
	public long getMicroseconds() {
		return microseconds;
	}
	public int getConnectionState() {
		return connectionState;
	}
	public String getTransitionReason() {
		return transitionReason;
	}

}
