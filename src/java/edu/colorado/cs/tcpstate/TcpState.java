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

	private Map<String, Connection> connectionMap = new HashMap<String, Connection>();
	private boolean debug = false;

	private JProbeDistiller jpd;
	private List<JProbeLogEntry> jprobe = new ArrayList<JProbeLogEntry>();
	private int jprobePos = 0;
	private long timeOffset = 0;

	public TcpState() {
	}

	public TcpState(InputStream tcpDump) {
		process(tcpDump);
	}

	public TcpState(InputStream tcpDump, InputStream jprobeLog) {
		// debug=true;
		jpd = new JProbeDistiller();
		jprobe = jpd.load(jprobeLog);
		process(tcpDump);
	}

	public void process(InputStream tcpDump) {
		try {

			Scanner br = new Scanner(tcpDump);
			String line;
			Connection c;
			HostProgress hp = null;
			while (br.hasNext()) {
				line = br.nextLine();
				if (line.length() < 5 || line.indexOf(" IP ") < 0) {
					continue;
				}
				hp = new HostProgress(line);
				String key = hp.getKey();
				if (hp.getFlags().equals("[S]")) {
					c = new Connection();
					c.addProgress(hp);
					c.setDebug(debug);
					connectionMap.put(key, c);
				} else {
					c = connectionMap.get(key);
					c.addProgress(hp);
					if (hp.getFlags().equals("[.]") && c.getState() == Connection.STATE_CLOSED) {
						connectionMap.remove(c);
						if (debug) {
							// Print the last one / Make sure nothing was missed
							while (jprobePos < jprobe.size()) {
								JProbeLogEntry jle = jprobe.get(jprobePos);
								System.out.println(hp.getTime()+"--------"+jle.getMessage()+" ("+jle.getTime()+")"+" ["+hp.getMicroseconds()+" >= "+jle.getMicroseconds()+"]");								jprobePos++;
							}
						}

						if (jprobe.size() > 0) {

							compareTransitions(jpd.createTransitionList(c.getKey(), jprobe), c.getTransitions());
						}
					}
				}

				if (timeOffset != 0 && jprobePos < jprobe.size()&& hp.getMicroseconds() >= jprobe.get(jprobePos).getMicroseconds()) {
					do {
						JProbeLogEntry jle = jprobe.get(jprobePos);
						System.out.println(hp.getTime()+"--------"+jle.getMessage()+" ("+jle.getTime()+")"+" ["+hp.getMicroseconds()+" >= "+jle.getMicroseconds()+"]");
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

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public List<TransitionResult> compareTransitions(List<Transition> jprobe, List<Transition> tcpdump) {

		System.out.println("\n\n-------------SUMMARY-------------");
		
		int jprobePos = 0;

		if (jprobe.size() != tcpdump.size() * 2) {
			System.out.println("Transition mismatch jprobe(" + jprobe.size() + ") and tcpdump(" + (tcpdump.size()*2) + ")");
		}
		for (Transition t : tcpdump) {
			TransitionResult tr;
			if (jprobePos + 2 <= jprobe.size()) {
				tr = new TransitionResult(jprobe.get(jprobePos++), jprobe.get(jprobePos++), t);
			} else {
				tr = new TransitionResult(null, null, t);
			}
			System.out.println(tr.getResult());
		}
		while (jprobePos < jprobe.size()) {
			TransitionResult tr = new TransitionResult(jprobe.get(jprobePos++), jprobe.get(jprobePos++), null);
			System.out.println(tr.getResult());
		}

		return null;
	}

	public static void main(String[] args) throws Exception {
		if (args.length == 1) {
			FileInputStream tfis = new FileInputStream(new File(args[0]));
			new TcpState(tfis);
			tfis.close();

		} else if (args.length == 2) {
			FileInputStream jfis = new FileInputStream(new File(args[1]));
			FileInputStream tfis = new FileInputStream(new File(args[0]));
			new TcpState(tfis, jfis);
			jfis.close();
			tfis.close();

		} else {
			System.out.println("Syntax: java TcpState [TcpDumpFile] {JProbeFile}");

		}
	}
}
