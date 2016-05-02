package edu.colorado.cs.tcpstate;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

import edu.colorado.cs.tcpstate.bean.Connection;
import edu.colorado.cs.tcpstate.bean.HostProgress;
import edu.colorado.cs.tcpstate.bean.JProbeLogEntry;
import edu.colorado.cs.tcpstate.bean.Transition;
import edu.colorado.cs.tcpstate.bean.TransitionResult;

public class TcpState {

	//private Map<String,Connection> connectionMap = new HashMap<String,Connection>();
	private Connection c = null;
	private boolean debug = false;

	private static final NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
	private float PASS_ACCURACY_LEVEL = 0.05f;
	private JProbeDistiller jpd;
	private List<JProbeLogEntry> jprobe = new ArrayList<JProbeLogEntry>();
	private int jprobePos = 0;
	private long timeOffset = 0;



	public TcpState() {
	}
	public boolean process(InputStream tcpDump, InputStream jprobeLog) {
		// debug=true;
		jpd = new JProbeDistiller();
		//jpd.setDebug(true);
		jprobe = jpd.load(jprobeLog);
		return process(tcpDump);
	}

	public boolean process(InputStream tcpDump) {
		boolean result = true;
		try {

			Scanner br = new Scanner(tcpDump);
			String line;
			HostProgress hp = null;
			while (br.hasNext()) {
				line = br.nextLine();
				if (line.length() < 5 || line.indexOf(" IP ") < 0) {
					continue;
				}
				hp = new HostProgress(line);
				if(c == null && !hp.getFlags().equals("[S]")) {
					continue;
				}
				
				if (hp.getFlags().equals("[S]")) {
					c = new Connection();
					c.addProgress(hp);
					c.setDebug(debug);
				} else {
					c.addProgress(hp);
					if (hp.getFlags().equals("[.]") && c.getState() == Connection.STATE_CLOSED) {
						// Print the last one / Make sure nothing was missed
						while (jprobePos < jprobe.size()) {
							JProbeLogEntry jle = jprobe.get(jprobePos);
							System.out.println(hp.getTime()+"--------"+jle.getMessage()+" ("+jle.getTime()+")");
							jprobePos++;
						}

						if (jprobe.size() > 0) {
							result = compareTransitions(jpd.createTransitionList(c.getKey(), jprobe), c.getTransitions(), c.getConnectionDuration());
						} else { 
							System.out.println("\n\n\n");
						}
					}
				}

				if (timeOffset != 0 && jprobePos < jprobe.size()&& hp.getMicroseconds() >= jprobe.get(jprobePos).getMicroseconds()+c.getEstimatedRtt()) {
					do {
						JProbeLogEntry jle = jprobe.get(jprobePos);
						System.out.println(hp.getTime()+"--------"+jle.getMessage()+" ("+jle.getTime()+")");
						jprobePos++;
					} while (jprobePos < jprobe.size() && jprobe.get(jprobePos).getMicroseconds() <= hp.getMicroseconds());

				} else if (timeOffset == 0 && jprobe.size() > 0 && c.getState() == Connection.STATE_SLOW_START) {
					// Adjust for clock differences
					timeOffset = hp.getMicroseconds() - jprobe.get(0).getMicroseconds();
					// System.out.println("Timestamp offset: "+timeOffset+"\n");
					for (JProbeLogEntry jpe : jprobe) {
						jpe.setMicroseconds(jpe.getMicroseconds() + timeOffset);
					}

				}

			}
			br.close();
			tcpDump.close();
			if(c.getState() != Connection.STATE_CLOSED) {
				if (jprobe.size() > 0) {
					result = compareTransitions(jpd.createTransitionList(c.getKey(), jprobe), c.getTransitions(), c.getConnectionDuration());
				} else { 
					System.out.println("\n\n\n");
				}
				System.out.println("Input Error: TCPDUMP Incomplete - FIN missing");
				result=true;
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	public boolean compareTransitions(List<Transition> jprobe, List<Transition> tcpdump, long duration) {

		long missedMicroseconds = 0;
		long overboardMicroseconds = 0;
		long lateMicroseconds = 0;
		TransitionResult lastAccurateTcpDumpTransitionResult=null, lastOverboardTcpDumpTransitionResult=null;
		boolean pass=true;
		int currentState = Connection.STATE_SLOW_START;
		System.out.println("-------------SUMMARY-------------");
		int jprobePos = 0;

		for (int x = 0; x < tcpdump.size(); x++) {
			Transition t = tcpdump.get(x);
			TransitionResult tr;
			if (jprobePos + 2 <= jprobe.size()) {
				tr = new TransitionResult(jprobe.get(jprobePos++), jprobe.get(jprobePos++), t);
			} else {
				tr = new TransitionResult(null, null, t);
			}
			String result = tr.getResult();
			
			if(result.startsWith("Match")) {
				lastAccurateTcpDumpTransitionResult = tr;
				currentState = t.getConnectionState();
				long late = tr.getTcpdump().getMicroseconds() - tr.getJprobeStart().getMicroseconds();
				if(late < 0) {
					late*=-1;
				}
				lateMicroseconds += late;
			} else if(result.startsWith("Overboard")) {
				if(lastOverboardTcpDumpTransitionResult != null) {
					//At least two overboard results
					if(lastOverboardTcpDumpTransitionResult.getTcpdump().getConnectionState() != currentState) {
						overboardMicroseconds += tr.getTcpdump().getMicroseconds() - lastOverboardTcpDumpTransitionResult.getTcpdump().getMicroseconds();
					}
				} 
				lastOverboardTcpDumpTransitionResult=tr;
			} else if(result.startsWith("Mismatch")) {
				if (tcpdump.size() > x+1) {
					//watch for No Slow Start
					TransitionResult trForward = new TransitionResult(tr.getJprobeStart(), tr.getJprobeEnd(), tcpdump.get(x+1));
					if(trForward.getResult().startsWith("Match")) {
						System.out.println(result);
						result=trForward.getResult();
						long late = trForward.getTcpdump().getMicroseconds() - tr.getJprobeStart().getMicroseconds();
						if(late < 0) {
							late*=-1;
						}
						lateMicroseconds += late;
						x++;
					}
				}
			}
			System.out.println(result);
		}
		if(lastAccurateTcpDumpTransitionResult != null && lastOverboardTcpDumpTransitionResult != null && overboardMicroseconds == 0) {
			overboardMicroseconds = lastAccurateTcpDumpTransitionResult.getJprobeEnd().getMicroseconds() - lastOverboardTcpDumpTransitionResult.getTcpdump().getMicroseconds();
		}
		
		//Report Missed Transitions
		while (jprobePos < jprobe.size()) {
			TransitionResult tr = new TransitionResult(jprobe.get(jprobePos++), jprobe.get(jprobePos++), null);
			System.out.println(tr.getResult());
			if(tr.getJprobeStart().getConnectionState() != currentState) {
				missedMicroseconds+=tr.getMicrosecondsInTransition();
			}
					
		}
		if(missedMicroseconds > 0 || overboardMicroseconds > 0 || lateMicroseconds > 0) {
			long total = missedMicroseconds + overboardMicroseconds + lateMicroseconds;
			System.out.print("Total microseconds in wrong state: "+nf.format(total));
			if(total > duration*PASS_ACCURACY_LEVEL) {
				pass=false;
			}
			System.out.println(" (Permitted: "+nf.format((long)(duration*PASS_ACCURACY_LEVEL))+" microseconds of "+nf.format(duration)+" total connection time)");
		}
		System.out.println("Accuracy: "+ nf.format((((duration-(overboardMicroseconds + missedMicroseconds + lateMicroseconds))*100.0f)/duration))+"%");
		System.out.println("\n\n\n");
		return pass;
	}
	public void setDebug(boolean tf) {
		debug=tf;
	}
	public static void main(String[] args) throws Exception {
		if(args.length == 0) {
			/*
			String[] jprobes = new String[] {}; 
			String[] tcpdumps = new String[] {};
			*/
			
			
			String[] jprobes = new String[] {"iperf.inet.kern.log","iperf.vpn.kern.log","cubic.30.192.3.171.8.log", "cubic.600.192.3.171.8.log", "cubic.60.192.3.171.8.log","cubic.600.192.3.171.8.20160420.233026.log","cubic.60.192.3.171.8.20160420.232855.log","vegas.30.192.3.171.8.20160420.235356.log","vegas.60.192.3.171.8.20160420.235457.log","bic.30.192.3.171.8.20160429.205940.log","bic.600.192.3.171.8.20160429.210211.log","bic.60.192.3.171.8.20160429.210040.log","cubic.30.192.3.171.8.20160429.203337.log","cubic.600.192.3.171.8.20160429.203608.log","cubic.60.192.3.171.8.20160429.203438.log","dctcp.30.192.3.171.8.20160429.211241.log","dctcp.600.192.3.171.8.20160429.211512.log","dctcp.60.192.3.171.8.20160429.211342.log","highspeed.30.192.3.171.8.20160429.212543.log","highspeed.600.192.3.171.8.20160429.212814.log","highspeed.60.192.3.171.8.20160429.212643.log","htcp.30.192.3.171.8.20160429.213844.log","htcp.600.192.3.171.8.20160429.214115.log","htcp.60.192.3.171.8.20160429.213945.log","hybla.30.192.3.171.8.20160429.215145.log","hybla.600.192.3.171.8.20160429.215417.log","hybla.60.192.3.171.8.20160429.215246.log","illinois.30.192.3.171.8.20160429.220447.log","illinois.600.192.3.171.8.20160429.220718.log","illinois.60.192.3.171.8.20160429.220548.log","reno.30.192.3.171.8.20160429.204638.log","reno.600.192.3.171.8.20160429.204909.log","reno.60.192.3.171.8.20160429.204739.log","scalable.30.192.3.171.8.20160429.221749.log","scalable.600.192.3.171.8.20160429.222020.log","scalable.60.192.3.171.8.20160429.221849.log","vegas.30.192.3.171.8.20160429.223050.log","vegas.600.192.3.171.8.20160429.223321.log","vegas.60.192.3.171.8.20160429.223151.log","veno.30.192.3.171.8.20160429.224352.log","veno.600.192.3.171.8.20160429.224623.log","veno.60.192.3.171.8.20160429.224452.log","westwood.30.192.3.171.8.20160429.225653.log","westwood.600.192.3.171.8.20160429.225924.log","westwood.60.192.3.171.8.20160429.225754.log","yeah.30.192.3.171.8.20160429.230955.log","yeah.600.192.3.171.8.20160429.231226.log","yeah.60.192.3.171.8.20160429.231055.log"};
			String[] tcpdumps = new String[] {"iperf.inet.tcpdump","iperf.vpn.tcpdump","cubic.30.192.3.171.8.tcpdump.log", "cubic.600.192.3.171.8.tcpdump.log", "cubic.60.192.3.171.8.tcpdump.log","cubic.600.192.3.171.8.20160420.233026.tcpdump.log","cubic.60.192.3.171.8.20160420.232855.tcpdump.log","vegas.30.192.3.171.8.20160420.235356.tcpdump.log","vegas.60.192.3.171.8.20160420.235457.tcpdump.log","bic.30.192.3.171.8.20160429.205940.tcpdump.log","bic.600.192.3.171.8.20160429.210211.tcpdump.log","bic.60.192.3.171.8.20160429.210040.tcpdump.log","cubic.30.192.3.171.8.20160429.203337.tcpdump.log","cubic.600.192.3.171.8.20160429.203608.tcpdump.log","cubic.60.192.3.171.8.20160429.203438.tcpdump.log","dctcp.30.192.3.171.8.20160429.211241.tcpdump.log","dctcp.600.192.3.171.8.20160429.211512.tcpdump.log","dctcp.60.192.3.171.8.20160429.211342.tcpdump.log","highspeed.30.192.3.171.8.20160429.212543.tcpdump.log","highspeed.600.192.3.171.8.20160429.212814.tcpdump.log","highspeed.60.192.3.171.8.20160429.212643.tcpdump.log","htcp.30.192.3.171.8.20160429.213844.tcpdump.log","htcp.600.192.3.171.8.20160429.214115.tcpdump.log","htcp.60.192.3.171.8.20160429.213945.tcpdump.log","hybla.30.192.3.171.8.20160429.215145.tcpdump.log","hybla.600.192.3.171.8.20160429.215417.tcpdump.log","hybla.60.192.3.171.8.20160429.215246.tcpdump.log","illinois.30.192.3.171.8.20160429.220447.tcpdump.log","illinois.600.192.3.171.8.20160429.220718.tcpdump.log","illinois.60.192.3.171.8.20160429.220548.tcpdump.log","reno.30.192.3.171.8.20160429.204638.tcpdump.log","reno.600.192.3.171.8.20160429.204909.tcpdump.log","reno.60.192.3.171.8.20160429.204739.tcpdump.log","scalable.30.192.3.171.8.20160429.221749.tcpdump.log","scalable.600.192.3.171.8.20160429.222020.tcpdump.log","scalable.60.192.3.171.8.20160429.221849.tcpdump.log","vegas.30.192.3.171.8.20160429.223050.tcpdump.log","vegas.600.192.3.171.8.20160429.223321.tcpdump.log","vegas.60.192.3.171.8.20160429.223151.tcpdump.log","veno.30.192.3.171.8.20160429.224352.tcpdump.log","veno.600.192.3.171.8.20160429.224623.tcpdump.log","veno.60.192.3.171.8.20160429.224452.tcpdump.log","westwood.30.192.3.171.8.20160429.225653.tcpdump.log","westwood.600.192.3.171.8.20160429.225924.tcpdump.log","westwood.60.192.3.171.8.20160429.225754.tcpdump.log","yeah.30.192.3.171.8.20160429.230955.tcpdump.log","yeah.600.192.3.171.8.20160429.231226.tcpdump.log","yeah.60.192.3.171.8.20160429.231055.tcpdump.log"};
			
			for(int x = 0; x < jprobes.length; x++) {
			//for(int x = 1; x < 2; x++) {
				TcpState ts = new TcpState();
				//ts.setDebug(true);
				System.out.println(x+"]-------------------------"+jprobes[x]+"-------------------------"+tcpdumps[x]);
				FileInputStream jfis = new FileInputStream(new File("/opt/UCBVM/ReceiverDetectSender/"+jprobes[x]));
				FileInputStream tfis = new FileInputStream(new File("/opt/UCBVM/ReceiverDetect/"+tcpdumps[x]));
				if(!ts.process(tfis, jfis)) {
					System.out.println(x+"]-------------------------"+jprobes[x]+"-------------------------"+tcpdumps[x]);
					break;
				}
				System.out.println(x+"]-------------------------"+jprobes[x]+"-------------------------"+tcpdumps[x]);
				jfis.close();
				tfis.close();
				
			}
		} else if (args.length == 1) {
			FileInputStream tfis = new FileInputStream(new File(args[0]));
			(new TcpState()).process(tfis);
			tfis.close();

		} else if (args.length == 2) {
			FileInputStream jfis = new FileInputStream(new File(args[1]));
			FileInputStream tfis = new FileInputStream(new File(args[0]));
			(new TcpState()).process(tfis, jfis);
			jfis.close();
			tfis.close();

		} else {
			System.out.println("Syntax: java TcpState [TcpDumpFile] {JProbeFile}");

		}
	}
}
