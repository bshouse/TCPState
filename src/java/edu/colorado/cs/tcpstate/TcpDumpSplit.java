package edu.colorado.cs.tcpstate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.Scanner;

public class TcpDumpSplit {

	public static void tcpDumpSplit(InputStream tcpdump) {
		try {
			FileWriter fw = null;
			String lastPort = null;
			String line = null;
			int pos = 0;
			Scanner s = new Scanner(tcpdump);
			while(s.hasNext()) {
				line = s.nextLine();
				if( line.indexOf("[S]") > -1 && (pos = line.indexOf(">")) > -1) {
					int start = line.lastIndexOf(".", pos)+1;
					String port = line.substring(start, pos).trim();
					if(!port.equals(lastPort)) {
						if(fw != null) {
							fw.close();
						}
						lastPort=port;
						System.out.println("Parsing TCPDUMP for port# "+port);
						fw = new FileWriter(new File(port+".log"));
					}
				}
				if(fw!=null && line.indexOf(lastPort) > -1) {
					fw.write(line+"\n");
				}
			}
			fw.close();
			s.close();
			
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	public static void main(String[] args) throws Exception {
		if(args.length == 1) {
			TcpDumpSplit.tcpDumpSplit(new FileInputStream(args[0]));
		} else {
			System.out.println("java TcpDumpSplit [tcpDumpJoined]");
		}
	}
}
