package net.pms.external.infidel.jumpy;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

import java.lang.Process;
import java.lang.ProcessBuilder;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.util.StringUtils;

import java.net.InetAddress;
import py4j.GatewayServer;
import py4j.Py4JNetworkException;

//import net.pms.PMS;
//import net.pms.io.WinUtils;

public class command {

	public List<String> argv;
	public static String pms = "lib/jumpy.py";
	public static String jumpypy = pms;
	public static String basepath = null;
	public String syspath = null;
	private static boolean pmsok = false;
	public static boolean windows = System.getProperty("os.name").startsWith("Windows");
	public static boolean mac = System.getProperty("os.name").contains("OS X");
	public File startdir;
	public Map<String,String> env = null;
	public Map substitutions = null;
	private GatewayServer server = null;
	public int scriptarg = 0, arg0 = 0;
	private boolean delims = false;
	public boolean async = false;

	public static HashMap<String,String> interpreters = new HashMap<String,String>() {{
		put("py", "python");
		put("sh", "sh");
		put("pl", "perl");
		put("rb", "ruby");
		put("php", "php");
		put("groovy", "groovy");
		put("vbs", windows ? "cscript" : null);
		put("js", windows ? "cscript" : mac ? "jsc" : "rhino");
	}};
	public static HashMap<String,String> executables = new HashMap<String,String>();

	public static String getexec(String interpreter) {
		String executable = executables.containsKey(interpreter) ? executables.get(interpreter) : null;
		return executable == null ? interpreter : executable;
	}

	public static void putexec(String interpreter, String exec) {
		executables.put(interpreter, absolute(exec).getPath());
	}

	public static String getpms()  {
		if (!pmsok) {
			jumpypy = pms;
//			if (windows) {
//				WinUtils win = (WinUtils)(PMS.get().getRegistry());
//				pms = win.getShortPathNameW(getexec("python")) + " " + win.getShortPathNameW(pms);
//			} else {
				pms = StringUtils.quoteArgument(getexec("python")) + " " + StringUtils.quoteArgument(pms);
//			}
			pmsok = true;
		}
		return pms;
	}

//	public static String unquote(String s) {
//		if (StringUtils.isQuoted(s)) {
//			return s.substring(1, s.length() - 1);
//		}
//		return s;
//	}

//	public static File canonical(String file) {
//		File f = new File(file);
//		try {
//			return f.getCanonicalFile();
//		} catch (IOException e) {
//			return f;
//		}
//	}

	public static File absolute(String file) {
		return new File(file).getAbsoluteFile();
	}

	public static boolean isOuterQuoted(String str)  {
		return (StringUtils.isQuoted(str) &&
			! str.matches("\\\".*\\\"\\s+\\\".*\\\"|'.*'\\s+'.*'"));
	}

	public List<String> split(String cmd) {
		cmd = cmd.trim();
		delims = (cmd.startsWith("[") || isOuterQuoted(cmd));
		if (delims) {
			cmd = cmd.substring(1, cmd.length()-1);
		}
		// Arrays.asList() is fixed-size, so allocate an extra interpreter slot now
		// TODO: check if arg1 is an interpreter first
		cmd = " , " + cmd;
		arg0 = 1;
		return Arrays.asList((delims ? cmd.split(" , ") : CommandLine.parse(cmd).toStrings()));
	}

	public List<String> fixArgs(List<String> argv) {

		if (async = argv.get(argv.size()-1).equals("&")) {
			argv = argv.subList(0, argv.size()-1);
		} else if (windows) {
			async = argv.get(arg0).toLowerCase().startsWith("cmd") &&
				argv.get(arg0 + 1).toLowerCase().equals("/c") &&
				argv.get(arg0 + 2).toLowerCase().equals("start");
		}

		for (int i=0; i<argv.size(); i++) {
			String arg = argv.get(i).trim();
			if (StringUtils.isQuoted(arg)) {
				arg = arg.substring(1, arg.length()-1);
			}
			if (delims) arg = arg.replace(" ,, ", " , ");
			if (windows) arg = arg.replace("\"", "\\\"");
			argv.set(i, arg);
		}

		arg0 = 0;
		String arg1 = argv.get(1);
		String filename = new File(arg1).getName();
		int i = filename.lastIndexOf('.') + 1;
		if (i > 0) {
			String ext = filename.substring(i).toLowerCase();
			if (interpreters.containsKey(ext)) {
				String interpreter = interpreters.get(ext);
				if (interpreter != null) {
					argv.set(0, getexec(interpreter));
					scriptarg = 1;
					return argv;
				}
			}
		} else if (arg1.equals("pms")) {
			argv.set(0, getexec("python"));
			argv.set(1, jumpypy);
			scriptarg = 1;
			return argv;
		}

		// use the executable if we have one
		argv.set(1, getexec(arg1));
		scriptarg = 0;
		return argv.subList(1, argv.size());
	}

//	public void addPath(String path) {
//		env.put("PATH", path + File.pathSeparator + env.get("PATH"));
//	}

	public void addPath(String path) {
		syspath = (path == null ? basepath : (syspath != null ? syspath + File.pathSeparator + path : path));
	}

	public command() {
	}

	public command(String cmd, String syspath) {
		init(split(cmd), syspath, null);
	}

	public command(String cmd, String syspath, Map<String,String> myenv) {
		init(split(cmd), syspath, myenv);
	}

	public command(List<String> argv, String syspath, Map<String,String> myenv) {
		init(argv, syspath, myenv);
	}

	public void init(String cmd, String syspath, Map<String,String> myenv) {
		init(split(cmd), syspath, myenv);
	}

	public void init(List<String> argv, String syspath, Map<String,String> myenv) {
		this.argv = fixArgs(argv);
		if (env == null) {
			env = new HashMap<String,String>();
		} else {
			env.clear();
		}
		if (myenv != null) {
			env.putAll(myenv);
		}
		addPath(null);
		addPath(syspath);
		if (this.syspath != null) {
			env.put("PYTHONPATH", this.syspath);
		}
		env.put("pms", getpms());
		if (executables.containsKey("imconvert")) {
			env.put("imconvert", executables.get("imconvert"));
		}
	}

	public boolean startAPI(jumpyAPI obj) {
		for (int i=0; i<32; i++) {
			try {
				server = new GatewayServer(obj, GatewayServer.DEFAULT_PORT + i);
				server.start();
				try {
					env.put("JGATEWAY", InetAddress.getLocalHost().getHostAddress() + ":" + server.getListeningPort());
				} catch(Exception e) {
					stopAPI();
					e.printStackTrace();
					System.err.println("Error: failed to start API.");
					return false;
				}
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

	public void stopAPI() {
		if (server != null) {
			server.shutdown();
			server = null;
		}
	}

	private String expand(final String arg) {
		return StringUtils.stringSubstitution(arg, substitutions, true).toString();
	}

	public String[] toStrings() {
		final String[] args = new String[argv.size()];
		for(int i=0; i<args.length; i++) {
			args[i] = expand(argv.get(i));
		}
		args[scriptarg] = StringUtils.fixFileSeparatorChar(args[scriptarg]);
		startdir = absolute(args[scriptarg]).getParentFile().getAbsoluteFile();
		return args;
	}

	public String envInfo() {
		String e = "";
		for (Map.Entry<String,String> var : env.entrySet()) {
			e += (var.getKey() + "=" + var.getValue() + "\n");
		}
		return "\nin directory '" + startdir.getAbsolutePath() + "'\n"
			+ (syspath != null ? ("PATH=" + syspath + "\n") : "")
			+ e;
	}
}

