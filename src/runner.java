package net.pms.external.infidel.jumpy;

import java.io.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import java.lang.Process;
import java.lang.ProcessBuilder;

public class runner {

	private static boolean quiet = false;
	public static PrintStream out = System.out;
	public static String version = "";
	public static String home = null;
	public command cmdline;
	private Process p = null;
	public boolean running = false;
	public static ArrayList<runner> active = new ArrayList<runner>();
	public String name;

	public runner() {
		cmdline = new command();
	}

	public void log(String str) {
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
		cmdline.init(cmd, syspath, myenv);
		return run(obj);
	}

//	public int run(jumpyAPI obj, String[] argv, String syspath, Map<String,String> myenv) {
//		return run(obj, Arrays.asList(argv), syspath, myenv);
//	}

	private int run(jumpyAPI obj) {

		if (name == null) {
			name = obj.getName();
		}

		if (! cmdline.startAPI(obj)) {
			return -1;
		}

		HashMap<String,String> vars = new HashMap<String,String>();
		if (home != null ) {
			vars.put("home", home.replace("\\","\\\\"));
		}
		cmdline.substitutions = vars;

		String[] argv = cmdline.toStrings();
		int exitValue = 0;
		running = true;
		log("running " + (cmdline.async ? "(async) " : "") + Arrays.toString(argv) + cmdline.envInfo());

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
			br = new BufferedReader (new InputStreamReader(p.getInputStream()));
			while ((line = br.readLine()) != null) {
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
		r.log("stopping '" + r.name + "'");
		r.shutdown();
	}

	public int shutdown() {
		running = false;
		try {
			if (p != null) {
				p.destroy();
			}
			if (cmdline != null) {
				cmdline.stopAPI();
			}
		} catch(Exception e) {e.printStackTrace();}
		return 0;
	}
}

