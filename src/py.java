// javac -d classes -cp .:\* xbmc.java && if [ $?=0 ] ; then java -cp classes:\* xbmc ; fi
// javac -d classes -cp .:./path/py4j0.7.jar:\* src/xbmc.java
// java -cp /f/git/pms-plugins/xbmc_plugin/classes:/f/git/pms-plugins/xbmc_plugin/path/py4j0.7.jar xbmc 0 ?
package net.pms.external.infidel;

import java.io.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.io.Console;

import java.net.InetAddress;
import py4j.GatewayServer;
import py4j.Py4JNetworkException;

import net.pms.external.infidel.util.jumpyAPI;

public class py {

	public static String python = "python";
	public static String path, addr;
	private static jumpyAPI api;
	private static GatewayServer server = null;
	private static Process p = null;
	
	public static boolean start() {
		for (int i=0; i<32; i++) {
			try {
				server = new GatewayServer(api, GatewayServer.DEFAULT_PORT + i);
				server.start();
				addr = InetAddress.getLocalHost().getHostAddress() + ":" + server.getListeningPort();
				return true;
			}
			catch(Py4JNetworkException e) {
				// socket is in use
				continue;
			}
			catch(Exception e) {e.printStackTrace(); continue;}
		}
		return false;
	}
	
	public static int run(String cmd, jumpyAPI obj) {
		int exitValue = 0;
		api = obj;
		start();
		try {
			String[] argv = (python + "|" + cmd).split("\\|");
			for (int i=0; i<argv.length; i++) {
				argv[i] = argv[i].trim();
			}
			System.out.println("Running "+Arrays.toString(argv));

			ProcessBuilder pb = new ProcessBuilder(argv);
			pb.redirectErrorStream(true);
			pb.directory(new File(argv[1]).getParentFile());
			Map<String,String> env = pb.environment();
			env.put("PYTHONPATH", path);
			env.put("JGATEWAY", addr);
			System.out.println("PYTHONPATH=" + path + "\nJGATEWAY=" + addr + "\n");
			
			p = pb.start();
			
			String line;
			BufferedReader br;
			br = new BufferedReader (new InputStreamReader(p.getInputStream()));
			while ((line = br.readLine()) != null) {
				System.out.println(line);
			}
			p.waitFor();
			exitValue = p.exitValue();

			br.close();
			server.shutdown();
		} catch(Exception e) {e.printStackTrace();}
		
		return exitValue;
	}
}

