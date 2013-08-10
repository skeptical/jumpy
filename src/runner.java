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

	public static final int DEV = 1;
	public static final int QUIET = 2;
	public int mode = 0;

	public static PrintStream out = System.out;
	public static String version = "";
	public command cmdline;
	private Process p = null;
	public boolean running = false;
	public boolean cache = false;
	public String output;
	public static ArrayList<runner> active = new ArrayList<runner>();
	public String name;

	public runner() {
	}

	public runner(String name) {
		this.name = name;
	}

	public runner(int mode) {
		this.mode = mode;
	}

	public runner(String name, int mode) {
		this.name = name;
		this.mode = mode;
	}

	public runner(command cmd) {
		cmdline = cmd;
	}

	public void log(String str) {
		if ((mode & QUIET) == 0) {
			out.println(str);
		}
	}

	public int run(int mode, jumpyAPI obj, String cmd, String syspath, Map<String,String> myenv) {
		int old = this.mode;
		this.mode |= mode;
		int r = run(obj, cmd, syspath, myenv);
		this.mode = old;
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
			name = (mode & DEV) == 0 ? obj.getName() : cmdstr;
		}
		log("running " + (cmdline.async ? "(async) " : "") + cmdstr + cmdline.envInfo());

		int exitValue = 0;
		running = true;

		try {
			ProcessBuilder pb = new ProcessBuilder(argv);
			pb.redirectErrorStream((mode & QUIET) == 0 ? true : false);
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
				new Thread(new outputlogger(), "outputlogger").start();
				obj.register(null);
				return 0;
			}

			new outputlogger().run();
			p.waitFor();
			exitValue = p.exitValue();
			shutdown();
		} catch(Exception e) {running = false; e.printStackTrace();}

		name = null;
		return exitValue;
	}

	class outputlogger implements Runnable {
		public void run() {
			String line;
			BufferedReader br = new BufferedReader (new InputStreamReader(
				(mode & QUIET) == 0 ? p.getInputStream() : p.getErrorStream()));
			output = "";
			try {
				while ((line = br.readLine()) != null) {
					if (cache) output += line + "\n";
					out.println(line);
				}
			} catch (Exception e) {
			} finally {
				try {
					br.close();
				} catch (Exception e) {}
			}
		}
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

