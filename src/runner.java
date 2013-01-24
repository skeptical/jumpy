package net.pms.external.infidel.jumpy;

import java.io.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import java.lang.Process;
import java.lang.ProcessBuilder;

import org.jvnet.winp.WinProcess;

public class runner {

	private static boolean quiet = false;
	public static PrintStream out = System.out;
	public static String version = "";
	public command cmdline;
	private Process p = null;
	public boolean running = false;
	public boolean cache = false;
	public String output = null;
	public static ArrayList<runner> active = new ArrayList<runner>();
	public String name;
	public int mode = 1;

	public runner() {
	}

	public runner(int mode) {
		this.mode = mode;
	}

	public runner(command cmd) {
		cmdline = cmd;
	}

	public static void log(String str) {
		if (! quiet) {
			out.println(str);
		}
	}

	public int quiet(jumpyAPI obj, String cmd, String syspath, Map<String,String> myenv) {
		quiet = true;
		int r = run(obj, cmd, syspath, myenv);
		quiet = false;
		return r;
	}

	public int run(jumpyAPI obj, String cmd, String syspath) {
		return run(obj, cmd, syspath, null);
	}

	public int run(jumpyAPI obj, String cmd, String syspath, Map<String,String> myenv) {
		if (cmdline == null) {
			cmdline = new command();
		}
		cmdline.init(cmd, syspath, myenv);
		return run(obj);
	}

	public int run(jumpyAPI obj, command cmd) {
		cmdline = cmd;
		return run(obj);
	}

//	public int run(jumpyAPI obj, String[] argv, String syspath, Map<String,String> myenv) {
//		return run(obj, Arrays.asList(argv), syspath, myenv);
//	}

	private int run(jumpyAPI obj) {

		if (! cmdline.startAPI(obj)) {
			return -1;
		}

		String[] argv = cmdline.toStrings();
		String cmdstr = Arrays.toString(argv);
		if (name == null) {
			name = mode == 1 ? obj.getName() : cmdstr;
		}
		log("running " + (cmdline.async ? "(async) " : "") + cmdstr + cmdline.envInfo());

		int exitValue = 0;
		running = true;

		try {
			ProcessBuilder pb = new ProcessBuilder(argv);
			pb.redirectErrorStream(true);
			pb.directory(cmdline.startdir);
			Map<String,String> env = pb.environment();
			if (cmdline.syspath != null ) {
				String sysPathKey = cmdline.windows ? "Path" : "PATH";
				env.put(sysPathKey, cmdline.syspath + File.pathSeparator + env.get(sysPathKey));
			}
			if (cmdline.env != null && !cmdline.env.isEmpty()) {
				env.putAll(cmdline.env);
			}

			p = pb.start();

			if (cmdline.async) {
				active.add(this);
				return 0;
			}

			String line;
			BufferedReader br;
			output = "";
			br = new BufferedReader (new InputStreamReader(p.getInputStream()));
			while ((line = br.readLine()) != null) {
				if (cache) output += line + "\n";
				out.println(line);
			}
			p.waitFor();
			exitValue = p.exitValue();

			br.close();
			shutdown();
		} catch(Exception e) {running = false; e.printStackTrace();}

		name = null;
		return exitValue;
	}

	public static void stop (runner r) {
		if (r.running) {
			r.log("stopping '" + r.name + "'");
			r.shutdown();
		}
	}

	public int shutdown() {
		try {
			if (cmdline != null) {
				cmdline.stopAPI();
			}
			if (p != null) {
				if (cmdline.windows) {
//					log("pid="+new WinProcess(p).getPid());
					new WinProcess(p).killRecursively();
				} else {
					p.destroy();
				}
				p = null;
			}
			running = false;
		} catch(Exception e) {e.printStackTrace();}
		return 0;
	}
}

