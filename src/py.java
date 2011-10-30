package net.pms.external.infidel;

import java.io.*;
import java.util.Arrays;
import java.util.Map;

import java.lang.Process;
import java.lang.ProcessBuilder;

import java.net.InetAddress;
import py4j.GatewayServer;
import py4j.Py4JNetworkException;

import net.pms.external.infidel.jumpyAPI;

public class py {

	public static String python = "python";
	public static PrintStream out = System.out;
	
	public static GatewayServer start(jumpyAPI obj) {
		for (int i=0; i<32; i++) {
			try {
				GatewayServer server = new GatewayServer(obj, GatewayServer.DEFAULT_PORT + i);
				server.start();
				return server;
			}
			catch(Py4JNetworkException e) {
				// socket is in use
				continue;
			}
			catch(Exception e) {e.printStackTrace(); continue;}
		}
		return null;
	}
	
	public static int run(jumpyAPI obj, String cmd, String pypath) {
		int exitValue = 0;
		GatewayServer server = start(obj);
		if (server == null) {
			return -1;
		}
		try {
			
			String[] argv = (python + " | " + cmd).split(" \\| ");
			boolean windows = System.getProperty("os.name").startsWith("Windows");
			for (int i=0; i<argv.length; i++) {
				argv[i] = argv[i].trim().replace("||", "|");
				if (windows) {
					argv[i] = argv[i].replace("\"", "\\\"");
				}
			}
			out.println("Running "+Arrays.toString(argv));

			ProcessBuilder pb = new ProcessBuilder(argv);
			pb.redirectErrorStream(true);
			pb.directory(new File(argv[1]).getParentFile());
			Map<String,String> env = pb.environment();
			if (pypath != null ) {
				env.put("PYTHONPATH", pypath);
			}
			String addr = InetAddress.getLocalHost().getHostAddress() + ":" + server.getListeningPort();
			env.put("JGATEWAY", addr);
			out.println("PYTHONPATH=" + pypath + "\nJGATEWAY=" + addr + "\n");
			
			Process p = pb.start();
			
			String line;
			BufferedReader br;
			br = new BufferedReader (new InputStreamReader(p.getInputStream()));
			while ((line = br.readLine()) != null) {
				out.println(line);
			}
			p.waitFor();
			exitValue = p.exitValue();

			br.close();
			server.shutdown();
		} catch(Exception e) {e.printStackTrace();}
		
		return exitValue;
	}
}

