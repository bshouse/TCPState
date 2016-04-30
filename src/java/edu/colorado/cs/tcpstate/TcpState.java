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
						if (debug) {
							// Print the last one / Make sure nothing was missed
							while (jprobePos < jprobe.size()) {
								JProbeLogEntry jle = jprobe.get(jprobePos);
								System.out.println(hp.getTime()+"--------"+jle.getMessage()+" ("+jle.getTime()+")"+" ["+hp.getMicroseconds()+" >= "+jle.getMicroseconds()+"]");								jprobePos++;
							}
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
			} else if(result.startsWith("Mismatch") && tcpdump.size() > x+1 && tcpdump.get(x+1).getConnectionState() == tr.getJprobeEnd().getConnectionState()) {
				//Check for skipped transition
				//Vegas will start in congestion congtrol instead of slow start
				System.out.println("Vegas Correction");
				tr = new TransitionResult(jprobe.get(jprobePos-2), jprobe.get(jprobePos-1), null);
				result = tr.getResult();
				missedMicroseconds+=tr.getMicrosecondsInTransition();
				x--;

			} 
			System.out.println(result);
		}
		if(lastOverboardTcpDumpTransitionResult != null && overboardMicroseconds == 0) {
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
			
			String[] jprobes = new String[] {"iperf.inet.kern.log","iperf.vpn.kern.log","cubic.30.192.3.171.8.log", "cubic.600.192.3.171.8.log", "cubic.60.192.3.171.8.log","cubic.600.192.3.171.8.20160420.233026.log","cubic.60.192.3.171.8.20160420.232855.log","vegas.30.192.3.171.8.20160420.235356.log","vegas.60.192.3.171.8.20160420.235457.log"}; 
			String[] tcpdumps = new String[] {"iperf.inet.tcpdump","iperf.vpn.tcpdump","cubic.30.192.3.171.8.tcpdump.log", "cubic.600.192.3.171.8.tcpdump.log", "cubic.60.192.3.171.8.tcpdump.log","cubic.600.192.3.171.8.20160420.233026.tcpdump.log","cubic.60.192.3.171.8.20160420.232855.tcpdump.log","vegas.30.192.3.171.8.20160420.235356.tcpdump.log","vegas.60.192.3.171.8.20160420.235457.tcpdump.log"};
			
			
			/*
			String[] jprobes = new String[] {};
			String[] tcpdumps = new String[] {};
			*/
			//for(int x = 0; x < jprobes.length; x++) {
			for(int x = 1; x < 2; x++) {
				TcpState ts = new TcpState();
				ts.setDebug(true);
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
			(new TcpState()).process(tfis);;
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
