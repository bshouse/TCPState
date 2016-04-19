package edu.colorado.cs.tcpstate.bean;

import java.util.ArrayList;
import java.util.List;

public class Connection {
	
	public static final int STATE_HANDSHAKE = 0;
	public static final int STATE_SLOW_START = 1;
	public static final int STATE_CONGESTION_AVOIDANCE = 2;
	public static final int STATE_FAST_RETRANSMIT = 3;
	public static final int STATE_FAST_RECOVERY = 4;
	public static final int STATE_CLOSING = 5;
	public static final int STATE_CLOSED = 6;

	private boolean debug = false;
	private boolean verbose = false;
	private boolean includeFastRetransmit=false;
	
	private long timestampInterval=0; 
	private long lastTimestamp;
	private int estimatedRtt=0;
	private long lastRtTimestamp=0;
	
	private int packetsPerRtt=0;
	private int lastPacketsPerRtt=0;
	private int lastEffectivePacketsPerRtt=0;
	
	private int flatLineCount=0;
	
	private long fastRetransmitTimestamp=0;
	private long fastRetransmitSequence = 0;
	private long fastRetransmitRttTime=0;
	private long fastRetransmitRttDupAckCount = 0;
	private long lastAck;
	private int dupAckCount = 0;

	
	private long lastTime = 0;
	
	private long interactionPeriod=0; //Time between send/receive
	private long lastSenderTime = 0;
	private long lastReceiverTime = 0;
	
	
	private List<HostProgress> sequence = new ArrayList<HostProgress>(10000);
	private List<HostProgress> sender = new ArrayList<HostProgress>(5000); 
	private List<HostProgress> receiver = new ArrayList<HostProgress>(5000);
	private List<Transition> transitions = new ArrayList<Transition>(100);
	private int state = STATE_CLOSED;
	private String senderIP;
	
	
	public void addProgress(HostProgress hp) {
		sequence.add(hp);

		boolean isSender=true;
		if(state != STATE_CLOSED) {
			if(hp.getSource().equals(senderIP)) {
				sender.add(hp);
			} else {
				receiver.add(hp);
				isSender=false;
			}
		} else {
			senderIP = hp.getSource();
			sender.add(hp);
		}
		
		//Watch for the explicit notifications
		String flags = hp.getFlags();
		if(state == STATE_HANDSHAKE && flags.equals("[.]")) {
			//3-way handshake complete
			estimatedRtt = (int)(hp.getMicroseconds() - lastTimestamp);
			lastTimestamp = hp.getMicroseconds(); 
			
			setState(STATE_SLOW_START,"Completion ACK",hp);
			lastRtTimestamp = hp.getMicroseconds();
			
			if(debug) {
				System.out.println("\nEstimated RTT: "+estimatedRtt+" microseconds");
			}
			
		} else if(state == STATE_HANDSHAKE && flags.equals("[S.]")) { //Handshake SYN-ACK
			
			//Prepare to estimated RTT
			lastTimestamp = hp.getMicroseconds();
			
		} else if(flags.equals("[S]")) { //Initial SYN
			setState(STATE_HANDSHAKE,"Initial SYN",hp);
			lastTimestamp = hp.getMicroseconds();
			
		} else if(flags.startsWith("[F")) {
			if(state != STATE_CLOSING) {
				setState(STATE_CLOSING,"Sender FIN",hp);				
			} else {
				setState(STATE_CLOSED,"Receiver FIN",hp);
			}
			
		} else if(state > STATE_HANDSHAKE && state < STATE_CLOSING) {

			//
			//Sender/Receiver Checks
			//
			if(isSender) {
				lastSenderTime = hp.getMicroseconds();

				//Throughput sum
				packetsPerRtt += hp.getSequenceSize();

				
				
				if(hp.sequenceContains(fastRetransmitSequence)) {
					long recoveryTime = hp.getMicroseconds()-fastRetransmitTimestamp;
					fastRetransmitRttDupAckCount+=dupAckCount;
					fastRetransmitRttTime+=recoveryTime;
					if(includeFastRetransmit) {
						System.out.println("\t"+hp.getTime()+" Fast Retransmitted:  "+lastAck+" Time: "+recoveryTime+ " Count: "+dupAckCount);
					}
				}
				
				
				interactionPeriod = lastSenderTime - lastReceiverTime;
				
												
			} else {
				lastReceiverTime = hp.getMicroseconds();
				
				//Watch for 3x Duplicate ACK
					if(hp.getAck() == lastAck) {
						dupAckCount++;
						if(dupAckCount == 3) {
							fastRetransmitSequence=lastAck;
							//setState(STATE_FAST_RETRANSMIT,hp);
						}
					} else {
						fastRetransmitTimestamp=hp.getMicroseconds();
						lastAck=hp.getAck();
						dupAckCount=1;
					}
				
				
				//System.out.println(hp.getTime()+" Receiver WIN: "+hp.getWindowSize()+"\tACK: "+hp.getAck()+"\tSEQ: "+hp.getSequence()+"("+hp.getSequenceSize()+")");
			}
			
			
			//
			//State Shift
			//
			
			//Timestamp interval
			if(lastTime != 0) {
				 //diff > 10x estimatedRTT during cubic switch to slow start
				 timestampInterval = hp.getMicroseconds() - lastTime;
				 lastTime = hp.getMicroseconds();
				 if(verbose) {
					 System.out.println(hp.getTime()+" Timestamp Interval: "+timestampInterval+" Window Size: "+hp.getSequenceSize());
				 }
			 } else {
				 lastTime = hp.getMicroseconds();
			 }
						
			//Estimated RTTs (Once per RTT)
			if( (lastTimestamp + estimatedRtt) <= hp.getMicroseconds()) {
				int effectiveRtt = (int)(hp.getMicroseconds()-lastTimestamp); //Effective Rtt				
				
				if(this.includeFastRetransmit && fastRetransmitRttDupAckCount > 0) {
					System.out.println("\t"+hp.getTime()+" Fast Retransmitted Total Time: "+fastRetransmitRttTime+ " Count: "+fastRetransmitRttDupAckCount+ " Ratio: "+(fastRetransmitRttTime/fastRetransmitRttDupAckCount));
				}

				
				//Throughput
				if(lastRtTimestamp != 0) {
					int effectivePacketsPerRtt = packetsPerRtt / (effectiveRtt / estimatedRtt);
					
					if(debug) {
						System.out.println("\n");
						System.out.println("Packets per RTT("+getStateString(state)+"): "+effectivePacketsPerRtt+" Actual: "+packetsPerRtt+ " Interaction Period: "+interactionPeriod);
						System.out.println("RTT: "+effectiveRtt+" Effective-Estimated RTT: "+ (effectiveRtt - estimatedRtt)+" SeqLen: "+effectivePacketsPerRtt+" < "+lastEffectivePacketsPerRtt);
					}
					if(state == STATE_SLOW_START && lastEffectivePacketsPerRtt > 0 && effectivePacketsPerRtt < lastEffectivePacketsPerRtt) {
						setState(STATE_CONGESTION_AVOIDANCE,"CWND Shrink",hp);
						
					} else if(state == STATE_CONGESTION_AVOIDANCE) {
						/*
						if(lastPacketsPerRtt == packetsPerRtt) {
							flatLineCount++;
							if(flatLineCount == 5) {
								if(debug) {
									System.out.println("Flat Line Slow Start @ a rate of: "+effectivePacketsPerRtt);
								}
								setState(STATE_SLOW_START,"Flat Line",hp);
							}
						} else */
						if(effectivePacketsPerRtt < lastEffectivePacketsPerRtt/2) {
							if(fastRetransmitRttDupAckCount < 1) {
								setState(STATE_SLOW_START,"CWND Halved",hp);
							} else if(fastRetransmitRttTime/fastRetransmitRttDupAckCount > 1000){
								setState(STATE_SLOW_START,"CWND Halved FastRT",hp);
							}
							if(debug) {
								System.out.println("eppRTT: "+effectivePacketsPerRtt+" vs "+lastEffectivePacketsPerRtt);
							}
							
						} else if(interactionPeriod > estimatedRtt*2) {
							if(debug) {
								System.out.println("Long Interaction Period: "+interactionPeriod);
							}
							setState(STATE_SLOW_START,"Long Delay (Timeout)",hp);
						}

					} /*else {
						flatLineCount=0;
					}*/
					lastEffectivePacketsPerRtt=effectivePacketsPerRtt;
					effectivePacketsPerRtt=0;
					
					lastPacketsPerRtt = packetsPerRtt;
					packetsPerRtt=0;
				}

				
				lastTimestamp = lastRtTimestamp = hp.getMicroseconds();
				fastRetransmitRttTime=0;
				fastRetransmitRttDupAckCount=0;
	
				if(debug) {
					System.out.println("\n");
				}
			} 
			

		} else if (state == STATE_CLOSING || state == STATE_CLOSED) {
		} else {
			System.out.println(hp.getTime()+" State["+state+"]: "+getStateString(state)+" Flags: "+hp.getFlags());
			
		}
	}
	public void setState(int newState, String reason, HostProgress hp) {

		Transition t = new Transition(hp,newState,reason);
		
		if(newState == STATE_SLOW_START || newState == STATE_CONGESTION_AVOIDANCE) {
			transitions.add(t);
		}
		
		//if(debug) {
			System.out.println(t);
		//}
		state=newState;
	}
	
	public int getState() {
		return state;
	}
	public static String getStateString(int cState) {
		switch(cState) {
			case STATE_FAST_RETRANSMIT:
				return "Fast Retransmission";
			case STATE_FAST_RECOVERY:
				return "Fast Recovery";
			case STATE_CONGESTION_AVOIDANCE:
				return "Congestion Avoidance";
			case STATE_SLOW_START:
				return "Slow Start";
			case STATE_CLOSING:
				return "Closing";
			case STATE_CLOSED:
				return "Closed";
			case STATE_HANDSHAKE:
				return "Handshake";
			default:
				return "UNDEFINED STATE ["+cState+"]";
		}
	}
	public int getEstimatedRtt() {
		return estimatedRtt;
	}
	public List<Transition> getTransitions() {
		return transitions;
	}
	public boolean isDebug() {
		return debug;
	}
	public void setDebug(boolean debug) {
		this.debug = debug;
	}
	public String getKey() {
		if(sequence.size() > 0) {
			return sequence.get(0).getKey();
		}
		return null;
	}
}
