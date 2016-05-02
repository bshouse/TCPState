package edu.colorado.cs.tcpstate;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import edu.colorado.cs.tcpstate.bean.Connection;
import edu.colorado.cs.tcpstate.bean.JProbeLogEntry;
import edu.colorado.cs.tcpstate.bean.Transition;

public class JProbeDistiller {
	
	private static final int MODE_BEGIN = -1;
	private static final int MODE_CONGESTION_AVOIDANCE = 0;
	private static final int MODE_SLOW_START = 1;
	
	protected boolean debug = false;

	public JProbeDistiller() {
	}

	
	public List<JProbeLogEntry> load(InputStream jprobeLog) {
		// Load a list of JProbeLogEntries
		// The 1st and Last of each group
		
		List<JProbeLogEntry> jprobeEntries = new ArrayList<JProbeLogEntry>();

		try {
			Scanner br = new Scanner(jprobeLog);
			String line;
			int lineCount = 0;
			int mode = MODE_BEGIN;
			JProbeLogEntry previous1 = null, previous2=null, firstCongAvoid = null;
			while( br.hasNextLine()) {
				line = br.nextLine();
				lineCount++;
				JProbeLogEntry jle = new JProbeLogEntry(line);
				if (lineCount % 2 == 0) {
					if (mode == MODE_CONGESTION_AVOIDANCE) {
						if (!jle.isCongestionControl()) {
							if(jprobeEntries.get(jprobeEntries.size()-1).getMicroseconds() > previous1.getMicroseconds()) {
								System.out.println("JProbe - ERROR MCA1("+lineCount+"): "+previous1);
								break;
							}
							jprobeEntries.add(previous1);
							if(previous1.getMicroseconds() > jle.getMicroseconds()) {
								System.out.println("JProbe - MCA2: "+previous1);
							}
							jprobeEntries.add(jle);
							mode = MODE_SLOW_START;
						}
					} else if (mode == MODE_SLOW_START) {
						if (jle.isCongestionControl()) {
							if(jprobeEntries.get(jprobeEntries.size()-1).getMicroseconds() > previous2.getMicroseconds()) {
								System.out.println("JProbe - MSS1: "+previous2);
							}
							jprobeEntries.add(previous2);
							if(previous2.getMicroseconds() > previous1.getMicroseconds()) {
								System.out.println("JProbe - MSS2: "+previous1);
							}
							jprobeEntries.add(previous1);
							mode = MODE_CONGESTION_AVOIDANCE;
						}

					} else if (mode == MODE_BEGIN) {
						if (!jle.isCongestionControl()) {
							mode = MODE_SLOW_START;
							if(previous1 != null && !previous1.isCongestionControl()) {
								//BIC doesn't use the pair (cong avoid & slow start) calling. Just a stream of slow_start
								jprobeEntries.add(previous1);
								lineCount++;
							} else {
								//Pair-wise grouping (cong avoid & slow start): add the final call
								jprobeEntries.add(jle);
							}
						} else {
							//TCP shouldn't go into Congestion Avoidance right away
							//Capture this entry in case of weirdness
							lineCount--;
							if(firstCongAvoid == null) {
								firstCongAvoid = jle;
							}
						}
					}

				}
				previous2 = previous1;
				previous1 = jle;
			}
			if(previous1 != null) { //Blank JProbe file / No slow start
				jprobeEntries.add(previous1);
			}
			if(jprobeEntries.size() == 1 && jprobeEntries.get(0).isCongestionControl()) {
				//Some Congestion Control never enter slow start
				//e.g. DCTCP
				jprobeEntries.add(0, firstCongAvoid);
			}
			
			if(debug) {
				for(JProbeLogEntry jple: jprobeEntries) {
					System.out.println("JProbleDistiller: "+jple);
				}
			}

			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}	
		
		//Sanity Checks
		if(jprobeEntries.size() % 2 != 0) {
			System.out.println("ERROR: Odd number of JProbe Log Entries");
		}
		for(int x = 0; x < jprobeEntries.size(); x+=2) {
			if(jprobeEntries.get(x).isCongestionControl() != jprobeEntries.get(x+1).isCongestionControl()) {
				System.out.println("ERROR: Mismatched JProbe Log Entries");
				break;
			}
		}
		
		return jprobeEntries;
	}

	public List<Transition> createTransitionList(String connectionKey, List<JProbeLogEntry> jpel) throws Exception {
		int size = jpel.size();
		if (size == 0 || size % 2 != 0) {
			throw new Exception("Incomplete JProbe set");
		}

		List<Transition> transitionList = new ArrayList<Transition>();
		boolean transitionIn = true;
		Transition t;
		int state = 0;
		for (int x = 0; x < size; x++) {
			JProbeLogEntry jple = jpel.get(x);

			if (jple.getMessage().indexOf("slow_start") > -1) {
				state = Connection.STATE_SLOW_START;
			} else {
				state = Connection.STATE_CONGESTION_AVOIDANCE;
			}

			if (transitionIn) {
				t = new Transition(connectionKey, jple, state, Transition.TRANSITION_IN, "JPROBE");
			} else {
				t = new Transition(connectionKey, jple, state, Transition.TRANSITION_OUT, "JPROBE");
			}
			transitionList.add(t);
			transitionIn = !transitionIn;
		}
		
		return transitionList;
	}


	public boolean isDebug() {
		return debug;
	}


	public void setDebug(boolean debug) {
		this.debug = debug;
	}

}
