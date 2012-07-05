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
//import org.apache.commons.io.FilenameUtils;
//import org.apache.commons.collections.bidimap.DualHashBidiMap;

import java.net.InetAddress;
import py4j.GatewayServer;
import py4j.Py4JNetworkException;

public class runner {

	public static String pms = "lib/jumpy.py";
	private static boolean pmsok = false;
	private static boolean quiet = false;
	public static PrintStream out = System.out;
	public static String version = "";
	public static boolean windows = System.getProperty("os.name").startsWith("Windows");
	public static boolean mac = System.getProperty("os.name").contains("OS X");
	private Process p = null;
	private GatewayServer server = null;
	protected jumpyAPI jumpy;
	public int scriptarg = 0;
	private boolean delims = false;

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

//	public static String unquote(String s) {
//		if (StringUtils.isQuoted(s)) {
//			return s.substring(1, s.length() - 1);
//		}
//		return s;
//	}

	public static String getexec(String interpreter) {
		String executable = executables.containsKey(interpreter) ? executables.get(interpreter) : null;
		return executable == null ? interpreter : executable;
	}

	public static void putexec(String interpreter, String exec) {
		executables.put(interpreter, absolute(exec).getPath());
	}

	public static File absolute(String file) {
		return new File(file).getAbsoluteFile();
	}

//	public static File canonical(String file) {
//		File f = new File(file);
//		try {
//			return f.getCanonicalFile();
//		} catch (IOException e) {
//			return f;
//		}
//	}

	public static String getpms()  {
		if (!pmsok) {
			pms = StringUtils.quoteArgument(getexec("python")) + " " + StringUtils.quoteArgument(pms);
//			pms = new CommandLine(getexec("python")).addArgument(absolute(pms).getPath()).toString();
			pmsok = true;
		}
		return pms;
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
		cmd = " , " + cmd;
		return Arrays.asList((delims ? cmd.split(" , ") : CommandLine.parse(cmd).toStrings()));
	}

	public List<String> fixArgs(List<String> argv) {
		for (int i=0; i<argv.size(); i++) {
			String arg = argv.get(i).trim();
			if (StringUtils.isQuoted(arg)) {
				arg = arg.substring(1, arg.length()-1);
			}
			if (delims) arg = arg.replace(" ,, ", " , ");
			if (windows) arg = arg.replace("\"", "\\\"");
			argv.set(i, arg);
		}

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
		}

		// use the executable if we have one
		arg1 = getexec(arg1);
		scriptarg = 0;
		return argv.subList(1, argv.size());
	}

	public boolean prepare(String syspath, Map<String,String> myenv) {
		server = start(jumpy);
		if (server == null) {
			return false;
		}
		try {
			myenv.put("JGATEWAY", InetAddress.getLocalHost().getHostAddress() + ":" + server.getListeningPort());
		} catch(Exception e) {return false;}
		myenv.put("PYTHONPATH", syspath);
		myenv.put("pms", getpms());
		return true;
	}

	public GatewayServer start(jumpyAPI obj) {
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
		return run(obj, split(cmd), syspath, null);
	}

	public int run(jumpyAPI obj, String cmd, String syspath, Map<String,String> myenv) {
		return run(obj, split(cmd), syspath, myenv);
	}

//	public int run(jumpyAPI obj, String[] argv, String syspath, Map<String,String> myenv) {
//		return run(obj, Arrays.asList(argv), syspath, myenv);
//	}

	private int run(jumpyAPI obj, List<String> argv, String syspath, Map<String,String> myenv) {
		jumpy = obj;
		int exitValue = 0;
		argv = fixArgs(argv);
		if (myenv == null) {
			myenv = new HashMap<String,String>();
		}
		if (!prepare(syspath, myenv)) {
			return -1;
		}

		File startdir = absolute(argv.get(scriptarg)).getParentFile().getAbsoluteFile();
		log("Running " + Arrays.toString(argv.toArray(new String[0])));
		log("in directory '" + startdir.getAbsolutePath() + "'");

		try {
			ProcessBuilder pb = new ProcessBuilder(argv);
			pb.redirectErrorStream(true);
			pb.directory(startdir);
			Map<String,String> env = pb.environment();
			if (syspath != null ) {
				env.put("PATH", syspath + File.pathSeparator + env.get("PATH"));
			}
			if (myenv != null && !myenv.isEmpty()) {
				for (Map.Entry<String,String> var : myenv.entrySet()) {
					log(var.getKey() + "=" + var.getValue());
				}
				env.putAll(myenv);
			}
			out.println("");

			p = pb.start();

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
		} catch(Exception e) {e.printStackTrace();}

		return exitValue;
	}

	public int shutdown() {
		try {
			if (p != null) {
				p.destroy();
			}
			if (server != null) {
				server.shutdown();
			}
		} catch(Exception e) {e.printStackTrace();}
		return 0;
	}
}

