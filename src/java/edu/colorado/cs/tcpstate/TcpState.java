package edu.colorado.cs.tcpstate;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
							result = compareTransitions(jpd.createTransitionList(c.getKey(), jprobe), c.getTransitions());
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
					result = compareTransitions(jpd.createTransitionList(c.getKey(), jprobe), c.getTransitions());
				} else { 
					System.out.println("\n\n\n");
				}
				System.out.println("Input Error: TCPDUMP Incomplete - FIN missing");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	public boolean compareTransitions(List<Transition> jprobe, List<Transition> tcpdump) {

		boolean clean=true;
		System.out.println("-------------SUMMARY-------------");
		
		int jprobePos = 0;

		if (jprobe.size() != tcpdump.size() * 2) {
			System.out.println("Transition mismatch jprobe(" + jprobe.size() + ") and tcpdump(" + (tcpdump.size()*2) + ")");
			clean=false;
		}
		for (Transition t : tcpdump) {
			TransitionResult tr;
			if (jprobePos + 2 <= jprobe.size()) {
				tr = new TransitionResult(jprobe.get(jprobePos++), jprobe.get(jprobePos++), t);
			} else {
				tr = new TransitionResult(null, null, t);
			}
			System.out.println(tr.getResult());
			if(clean && !tr.getResult().startsWith("Match")) {
				clean=false;
			}
		}
		while (jprobePos < jprobe.size()) {
			TransitionResult tr = new TransitionResult(jprobe.get(jprobePos++), jprobe.get(jprobePos++), null);
			System.out.println(tr.getResult());
		}
		System.out.println("\n\n\n");
		return clean;
	}

	public static void main(String[] args) throws Exception {
		if(args.length == 0) {
			String[] jprobes = new String[] {"iperf.inet.kern.log","iperf.vpn.kern.log","cubic.30.192.3.171.8.log", "cubic.600.192.3.171.8.log", "cubic.60.192.3.171.8.log", "cubic.30.192.3.171.8.20160420.232755.log","cubic.600.192.3.171.8.20160420.233026.log","cubic.60.192.3.171.8.20160420.232855.log","vegas.30.192.3.171.8.20160420.235356.log","vegas.600.192.3.171.8.20160420.235627.log","vegas.60.192.3.171.8.20160420.235457.log"}; 

			String[] tcpdumps = new String[] {"iperf.inet.tcpdump","iperf.vpn.tcpdump","cubic.30.192.3.171.8.tcpdump.log", "cubic.600.192.3.171.8.tcpdump.log", "cubic.60.192.3.171.8.tcpdump.log", "cubic.30.192.3.171.8.20160420.232755.tcpdump.log","cubic.600.192.3.171.8.20160420.233026.tcpdump.log","cubic.60.192.3.171.8.20160420.232855.tcpdump.log","vegas.30.192.3.171.8.20160420.235356.tcpdump.log","vegas.600.192.3.171.8.20160420.235627.tcpdump.log","vegas.60.192.3.171.8.20160420.235457.tcpdump.log"};
			//for(int x = 0; x < jprobes.length; x++) {
			for(int x = 5; x < 6; x++) {
				//#0-3:  100% with window recommended transitions only
				//#
				System.out.println("-------------------------"+jprobes[x]+"-------------------------"+x);
				FileInputStream jfis = new FileInputStream(new File("/opt/UCBVM/ReceiverDetectSender/"+jprobes[x]));
				FileInputStream tfis = new FileInputStream(new File("/opt/UCBVM/ReceiverDetect/"+tcpdumps[x]));
				TcpState ts = new TcpState();
				if(!ts.process(tfis, jfis)) {
					break;
				}
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
