package edu.colorado.cs.tcpstate.bean;

import java.util.ArrayList;
import java.util.List;

public class Connection {

	//TCP States
	public static final int STATE_HANDSHAKE = 0;
	public static final int STATE_SLOW_START = 1;
	public static final int STATE_CONGESTION_AVOIDANCE = 2;
	public static final int STATE_FAST_RETRANSMIT = 3;
	public static final int STATE_FAST_RECOVERY = 4;
	public static final int STATE_CLOSING = 5;
	public static final int STATE_CLOSED = 6;

	//Common tracking variables
	protected boolean debug = false;
	protected boolean includeFastRetransmit = false;
	//protected List<HostProgress> sequence = new ArrayList<HostProgress>(10000);
	//protected List<HostProgress> sender = new ArrayList<HostProgress>(5000); 
	//protected List<HostProgress> receiver = new ArrayList<HostProgress>(5000);
	protected List<Transition> transitions = new ArrayList<Transition>(100);
	protected int state = STATE_CLOSED;
	protected String senderIP;
	protected String key=null;

	/*
	 * 
	 * Basic shared TcpDump calculations
	 * 
	 */
	//Time interval between TCP dump time stamps
	private long timestampInterval=0; 
	//Time stamp of last packet in RTT
	private long lastRtTimestamp=0;

	
	//Estimated RTT: Time between SYN-ACK & ACK in handshake
	private int estimatedRtt=0;
	
	//The time stamp of the previous TCP dump entry
	private long lastTime = 0;
	
	//Time stamp of the last TcpDump entry of the sender
	private long lastSenderTime = 0;
	//Timestamp of the last TcpDump entry of the receiver
	private long lastReceiverTime = 0;
	//Time between send/receive
	private long interactionPeriod=0; 
	//Previous Time between send/receive
	private long lastInteractionPeriod=0; 



	
	/*
	 * 
	 * Window size calculations
	 * 
	 */
	//Packets in the round-trip time
	private int packetsPerRtt=0;
	//Packets in the previous round-trip time
	private int lastPacketsPerRtt=0;
	

	/*
	 * 
	 * RTT Calculations
	 * 
	 */
	private int effectiveRtt=0;

	/*
	 * 
	 * Fast retransmit calculations
	 * 
	 */
	//Time the last fast retransmit was completed
	private long fastRetransmitTimestamp=0;
	//Sequence number of the last fast retransmit
	private long fastRetransmitSequence = 0;
	//Time when the last fast retransmit was received
	private long fastRetransmitRttTime=0;
	//Number of duplicate ACKs sent for a sequence number
	private long fastRetransmitRttDupAckCount = 0;
	//The last ACK sent (used to identify potential fast retransmit sequence number) 
	private long lastAck;
	//Number of duplicate ACKs sent
	private int dupAckCount = 0;

	
	public void addProgress(HostProgress hp) {
		//sequence.add(hp);
		if(key == null) {
			key = hp.getKey();
		}
		
		boolean isSender=true;
		if(state != STATE_CLOSED) {
			if(hp.getSource().equals(senderIP)) {
				//sender.add(hp);
			} else {
				//receiver.add(hp);
				isSender=false;
			}
		} else {
			senderIP = hp.getSource();
			//sender.add(hp);
		}
		
		//Watch for the explicit notifications
		String flags = hp.getFlags();
		if(state == STATE_HANDSHAKE && flags.equals("[.]")) {
			//3-way handshake complete
			estimatedRtt = (int)(hp.getMicroseconds() - lastRtTimestamp);
			lastRtTimestamp = hp.getMicroseconds(); 
			
			setState(STATE_SLOW_START,"Completion ACK",hp);
			
			if(debug) {
				System.out.println("\nEstimated RTT: "+estimatedRtt+" microseconds");
			}
			
		} else if(state == STATE_HANDSHAKE && flags.equals("[S.]")) { //Handshake SYN-ACK
			
			//Prepare to estimated RTT
			lastRtTimestamp = hp.getMicroseconds();
			
		} else if(flags.equals("[S]")) { //Initial SYN
			setState(STATE_HANDSHAKE,"Initial SYN",hp);
			lastRtTimestamp = hp.getMicroseconds();
			
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

				//Time between send & receive
				interactionPeriod = lastSenderTime - lastReceiverTime;
				
				if(hp.sequenceContains(fastRetransmitSequence)) {
					long recoveryTime = hp.getMicroseconds()-fastRetransmitTimestamp;
					fastRetransmitRttDupAckCount+=dupAckCount;
					fastRetransmitRttTime+=recoveryTime;
					if(includeFastRetransmit) {
						System.out.println("\t"+hp.getTime()+" Fast Retransmitted:  "+lastAck+" Time: "+recoveryTime+ " Count: "+dupAckCount);
					}
				}

				
												
			} else {
				lastReceiverTime = hp.getMicroseconds();
				
				//Watch for 3x Duplicate ACK
				if(hp.getAck() == lastAck) {
					dupAckCount++;
					if(dupAckCount == 3) {
						fastRetransmitSequence=lastAck;
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
				 if(false) {
					 System.out.println(hp.getTime()+" Timestamp Interval: "+timestampInterval+" Window Size: "+hp.getSequenceSize());
				 }
			 } else {
				 lastTime = hp.getMicroseconds();
			 }
						
			//Estimated RTTs (Once per RTT)
			if( (lastRtTimestamp + estimatedRtt) <= hp.getMicroseconds()) {				

				//Effective Rtt
				effectiveRtt = (int)(hp.getMicroseconds()-lastRtTimestamp); 
				
				if(this.includeFastRetransmit && fastRetransmitRttDupAckCount > 0) {
					System.out.println("\t"+hp.getTime()+" Fast Retransmitted Total Time: "+fastRetransmitRttTime+ " Count: "+fastRetransmitRttDupAckCount+ " Ratio: "+(fastRetransmitRttTime/fastRetransmitRttDupAckCount));
				}

				
				//Throughput
				if(lastRtTimestamp != 0) {
					
					if(debug) {
						System.out.println("\n");
						System.out.println("Packets per RTT("+getStateString(state)+"): "+packetsPerRtt+ " Interaction Period: "+interactionPeriod);
						System.out.println("Effective RTT: "+effectiveRtt+" Effective-Estimated RTT: "+ (effectiveRtt - estimatedRtt)+" SeqLen: "+packetsPerRtt+" < "+lastPacketsPerRtt);
						//System.out.println("SeqLen: "+packetsPerRtt+" < "+lastPacketsPerRtt+"\n");			
					}
					
					Transition windowRecommendedTransition = getWindowRecommendation(hp);
					Transition rttRecommendedTransition = getRttRecommendation(hp);
					if(windowRecommendedTransition.getTransitionReason() != null) {
						if(windowRecommendedTransition.getTransitionReason().startsWith("CWND Reduction @ ")) {
							setState(rttRecommendedTransition);
						} else {
							setState(windowRecommendedTransition);
						}
					}
					//Update Window calculation variables
					lastPacketsPerRtt=packetsPerRtt;
					packetsPerRtt=0;					
				}

				
				lastRtTimestamp = hp.getMicroseconds();
				lastInteractionPeriod=interactionPeriod;

				//Reset fast retransmit counts at the end of the Estimated RTT 
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
	private Transition getRttRecommendation(HostProgress hp) {
		if(state == STATE_SLOW_START) {
			return new Transition(hp,state,null);
		} else if(state == STATE_CONGESTION_AVOIDANCE) {
			return new Transition(hp,state,null);
		}
		return new Transition(hp,state,null);
	}
	private Transition getWindowRecommendation(HostProgress hp) {
		if(state == STATE_SLOW_START) {
			if(packetsPerRtt < lastPacketsPerRtt) {
				if(packetsPerRtt < lastPacketsPerRtt * .4) {
					return new Transition(hp,STATE_SLOW_START,"Tiemout");
				} else {
					return new Transition(hp,STATE_CONGESTION_AVOIDANCE,"Window Stabilized");
				}
			
			}
			
		} else if(state == STATE_CONGESTION_AVOIDANCE) {
			if(packetsPerRtt < 11 && lastPacketsPerRtt > 10) {
				return new Transition(hp,STATE_SLOW_START,"CWND Reset");
				
			//} else if(packetsPerRtt < lastPacketsPerRtt * .20) {
			} else if( (((packetsPerRtt - lastPacketsPerRtt) / (lastPacketsPerRtt * 1.0f) )*100) < -80 ) { //-81
				return new Transition(hp,STATE_SLOW_START,"CWND Reduction @ "+(int)(((packetsPerRtt - lastPacketsPerRtt) / (lastPacketsPerRtt * 1.0f) )*-100)+"%");
			}
		} 
		return new Transition(hp,state,null);
	}
	
	
	private void setState(int newState, String reason, HostProgress hp) {
		Transition t = new Transition(hp,newState,reason);
		setState(t);
	}
	private void setState(Transition t) {
		
		int newState = t.getConnectionState();
		if(state == newState) {
			if(t.getTransitionReason() == null) {
				return;
			} else if(newState == STATE_SLOW_START){
				//Add missed congestion avoidance
				Transition tMissed = new Transition(t.getKey(),t.getTime(),t.getMicroseconds(),STATE_CONGESTION_AVOIDANCE,t.getTransitionReason());
				transitions.add(tMissed);
				System.out.println(t);
			} else if(newState == STATE_CONGESTION_AVOIDANCE) {
				Transition tMissed = new Transition(t.getKey(),t.getTime(),t.getMicroseconds(),STATE_SLOW_START,t.getTransitionReason());
				transitions.add(tMissed);
				System.out.println(t);
			}
		} 
		
		if(newState == STATE_SLOW_START || newState == STATE_CONGESTION_AVOIDANCE) {
			transitions.add(t);
		}
		
		System.out.println(t);
		
		state=newState;

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
	
	
	public String getKey() {
		return key;
	}

	public int getState() {
		return state;
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

}
