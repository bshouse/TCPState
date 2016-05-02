package edu.colorado.cs.tcpstate.bean;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Connection {

	private static final NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
	
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
	protected boolean TCP_OPTION_TIMESTAMP = false;
	protected float RTT_VARIANCE_TRIGGER_LEVEL = 0.5f;
	protected List<Transition> transitions = new ArrayList<Transition>(100);
	protected int state = STATE_CLOSED;
	protected String senderIP;
	protected String key=null;
	protected HostProgress first = null;
	protected HostProgress last = null;

	/*
	 * 
	 * Basic shared TcpDump calculations
	 * 
	 */
	//Handshake Estimated RTT: Time between SYN-ACK & ACK in handshake
	private int estimatedRttHandshake=0;
	private long lastRttTimestamp=0;

	
	/*
	 * 
	 * Window size calculations
	 * 
	 */
	//Packets in the round-trip time
	private long sequenceCountPerRtt=0;
	//Packets in the previous round-trip time
	private long lastSequenceCountPerRtt=0;
	//Number of TCP dump entries
	private int senderPacketsPerRtt=0;
	private int lastSenderPacketsPerRtt=0;
	//Receiver Window Size
	private int receiverWindowSize=0;
	private int lastReceiverWindowSize=0;
	

	/*
	 * 
	 * Duplicate ACK tracking
	 * 
	 */
	//Time the last Duplicate ACK sequence was received
	private long duplicateAckRetransmittedTimestamp=0;
	//Sequence number of the last DUP ACK
	private long duplicateAckSequenceNumber = 0;
	//Time when the last DUP ACk was received
	private long duplicateAckRttTime=0;
	//Number of duplicate ACKs sent for a sequence number
	private long duplicateAckRttCount = 0;
	//The last ACK sent (used to identify potential fast retransmit sequence number) 
	private long lastAck;
	//Number of duplicate ACKs sent
	private int dupAckCount = 0;
	
	/*
	 * 
	 * Real-time RTT Estimation
	 * 
	 */
	private List<TcpTimestamp> tcpOptionTimestampSender = new ArrayList<TcpTimestamp>(100);
	private long tcpOptionTimestampRtt=0L;

	
	
	public void addProgress(HostProgress hp) {
		//sequence.add(hp);
		if(key == null) {
			key = hp.getKey();
			first = hp;
		}
		last=hp;
		
		boolean isSender=true;
		if(state != STATE_CLOSED) {
			if(!hp.getSource().equals(senderIP)) {
				isSender=false;
			}
		} else {
			senderIP = hp.getSource();
		}
		
		//Watch for the explicit notifications
		String flags = hp.getFlags();
		if(state == STATE_HANDSHAKE && flags.equals("[.]")) { //3-way handshake complete
			
			//Estimate RTT
			estimatedRttHandshake = (int)(hp.getMicroseconds() - lastRttTimestamp);
			//Record last RTT Time stamp 
			lastRttTimestamp = hp.getMicroseconds();
			//Move state to Slow Start
			setState(STATE_SLOW_START,"Completion ACK",hp);
			tcpOptionTimestampRtt=estimatedRttHandshake;
			System.out.println(hp.getTime()+" :"+hp.getKey()+" Handshake RTT: "+nf.format(estimatedRttHandshake)+" microseconds");
			
		} else if(state == STATE_HANDSHAKE && flags.equals("[S.]")) { //Handshake SYN-ACK
			//Prepare start time for estimated RTT
			lastRttTimestamp = hp.getMicroseconds();
			
		} else if(flags.equals("[S]")) { //Initial SYN
			setState(STATE_HANDSHAKE,"Initial SYN",hp);
			lastRttTimestamp = hp.getMicroseconds();
			if(hp.getTcpOptionTsVal() > -1L) {
				TCP_OPTION_TIMESTAMP=true;
			}
			
		} else if(flags.startsWith("[F")) { //Closing FIN
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

				//Throughput sum
				sequenceCountPerRtt += hp.getSequenceSize();
				
				//Sender packet count
				senderPacketsPerRtt++;

				
				if(hp.sequenceContains(duplicateAckSequenceNumber)) {
					long recoveryTime = hp.getMicroseconds()-duplicateAckRetransmittedTimestamp;
					duplicateAckRttCount+=dupAckCount;
					duplicateAckRttTime+=recoveryTime;
					//System.out.println("\t"+hp.getTime()+" Fast Retransmitted:  "+lastAck+" Time: "+recoveryTime+ " Count: "+dupAckCount);
				}
				
				
				//Real-time RTT estimation
				if(TCP_OPTION_TIMESTAMP) {
					int tsSize = tcpOptionTimestampSender.size(); 
					if(tsSize > 0) {
						for(int x = 0; x < tsSize; x++) {
							if(hp.getTcpOptionEcr() == tcpOptionTimestampSender.get(x).getTcpTimestamp()) {
								TcpTimestamp tt = tcpOptionTimestampSender.remove(x);
								long tRtt = hp.getMicroseconds()-tt.getMicroseconds();
								int variance = (int) (tcpOptionTimestampRtt * RTT_VARIANCE_TRIGGER_LEVEL); 
								if(tRtt > tcpOptionTimestampRtt+variance || tRtt < tcpOptionTimestampRtt-variance) {
									System.out.println(hp.getTime()+" :"+hp.getKey()+" Estimated RTT: "+nf.format(tRtt)+" microseconds");
									tcpOptionTimestampRtt=tRtt;
								}
								//Remove previous unused time stamps
								for(int p = 0; p < x; p++) {
									tcpOptionTimestampSender.remove(0);
								}
								//Remove subsequent identical timestamps
								while(tcpOptionTimestampSender.size() > 0 && tcpOptionTimestampSender.get(0).getTcpTimestamp() == hp.getTcpOptionEcr()) {
									tcpOptionTimestampSender.remove(0);
								}
								break;
							}
						}
					}
				}
												
			} else { //Receiver
				if(hp.getWindowSize() != receiverWindowSize) {
					receiverWindowSize = hp.getWindowSize();
				}

				//Real-time RTT estimation
				tcpOptionTimestampSender.add(new TcpTimestamp(hp.getMicroseconds(),hp.getTcpOptionTsVal()));
				
				
				//Watch for 3x Duplicate ACK
				if(hp.getAck() == lastAck) {
					dupAckCount++;
					if(dupAckCount == 3) {
						duplicateAckSequenceNumber=lastAck;
					}
				} else {
					duplicateAckRetransmittedTimestamp=hp.getMicroseconds();
					lastAck=hp.getAck();
					dupAckCount=1;
				}
								
			}
						
			//			
			//Once per RTT processing
			//
			if( (lastRttTimestamp + tcpOptionTimestampRtt) <= hp.getMicroseconds()) {				

				
				
				//Throughput
				if(lastRttTimestamp != 0) {
					
					if(debug) {
						System.out.println("\n");
						System.out.println("Current State: ["+getStateString(state)+"] RTT: ["+nf.format(tcpOptionTimestampRtt)+"]");
						System.out.println("Sequence Length: ["+sequenceCountPerRtt+"/"+lastSequenceCountPerRtt+"]");
						System.out.println("Packets- Per RTT: ["+senderPacketsPerRtt+"/"+lastSenderPacketsPerRtt+"]");
						System.out.println("Receiver Window: ["+receiverWindowSize+"/"+lastReceiverWindowSize+"]");
						if(duplicateAckRttCount > 0) {
							System.out.println("Duplicate ACK: ["+duplicateAckRttTime+"] Count: ["+duplicateAckRttCount+"] Ratio: ["+(duplicateAckRttTime/duplicateAckRttCount)+"]");
						}
					}
					
					Transition windowRecommendedTransition = getWindowRecommendation(hp);
					setState(windowRecommendedTransition);

					
					//Update Window calculation variables
					lastSequenceCountPerRtt=sequenceCountPerRtt;
					sequenceCountPerRtt=0;
					lastSenderPacketsPerRtt=senderPacketsPerRtt;
					senderPacketsPerRtt=0;
					lastReceiverWindowSize=receiverWindowSize;
					receiverWindowSize=0;
					
					//Reset Duplicate ACK counts at the end of the Estimated RTT 
					duplicateAckRttTime=0;
					duplicateAckRttCount=0;
				}

				
				lastRttTimestamp = hp.getMicroseconds();
				if(debug) {
					System.out.println("\n");
				}
			} 
			

		} else if (state == STATE_CLOSING || state == STATE_CLOSED) {
		} else {
			System.out.println(hp.getTime()+" State["+state+"]: "+getStateString(state)+" Flags: "+hp.getFlags());
			
		}
	}
	private Transition getWindowRecommendation(HostProgress hp) {
		if(state == STATE_SLOW_START) {
			if(senderPacketsPerRtt <= lastSenderPacketsPerRtt) {
				return new Transition(hp,STATE_CONGESTION_AVOIDANCE,"Window Stabilized");
			}
			
		} else if(state == STATE_CONGESTION_AVOIDANCE) {			
			if(sequenceCountPerRtt > lastSequenceCountPerRtt * lastSequenceCountPerRtt) {
				return new Transition(hp,STATE_SLOW_START,"Exponential Growth ("+sequenceCountPerRtt+" > "+(lastSequenceCountPerRtt * lastSequenceCountPerRtt)+")");
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
				//No transition recommended
				return;
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
		return estimatedRttHandshake;
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
	public long getConnectionDuration() {
		return last.getMicroseconds() - first.getMicroseconds();
	}

}
