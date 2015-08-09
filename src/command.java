package net.pms.external.infidel.jumpy;

import java.io.*;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.Map;
//import net.pms.io.WinUtils;
//import net.pms.PMS;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.util.StringUtils;
import py4j.CallbackClient;
import py4j.DefaultGatewayServerListener;
import py4j.GatewayServer;
import py4j.Py4JNetworkException;

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
	public static Map<String,String> basesubs = null;
	public Map substitutions = null;
	private GatewayServer server = null;
	private CallbackClient client = null;
	private Logger logger = null;
	private DefaultGatewayServerListener listener = null;
	public int scriptarg = 0, arg0 = 0;
	private boolean delims = false;
	public boolean async = false;
	public boolean has_callback = false, needs_listener = false, serverError = false;
	public static String py4j_jar = new File(py4j.GatewayServer.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getName();
	private static String localhost = getLocalhost();

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
		File f = new File(file);
		return f.exists() ? f.getAbsoluteFile() : f;
	}

	public static boolean isOuterQuoted(String str)  {
		return (StringUtils.isQuoted(str) &&
			! str.matches("\\\".*\\\".+\\\".*\\\"|'.*'.+'.*'"));
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
		substitutions = new HashMap<String,String>();
		if (basesubs != null && ! basesubs.isEmpty()) {
			substitutions.putAll(basesubs);
		}
	}

	public static String getLocalhost() {
		String addr = GatewayServer.DEFAULT_ADDRESS;
		if (windows) {
			try {
				// py4j python side connects to localhost in later versions
				// but only to host ip in v0.7 (release)
				if (py4j_jar.equals("py4j0.7.jar")) {
					addr = InetAddress.getLocalHost().getHostAddress();
				}
			} catch (Exception e) {}
		}
		return addr;
	}

	public static boolean avail(int port) {
		try {
			new Socket("localhost", port).close();
			// connection accepted = in use
			return false;
		} catch (IOException e) {
			// connection refused = not in use
			return true;
		}
	}

	public void py4jlog(boolean on, final jumpyAPI obj) {
		if (on) {
			if (logger == null) {
				logger = Logger.getLogger("py4j");
				logger.setLevel(Level.ALL);
				Handler handler = new Handler() {
					public void publish(LogRecord record) {
						String msg = getFormatter().format(record).trim();
						obj.util(jumpyAPI.LOG, msg, null);
					}
					public void flush() {}
					public void close() throws SecurityException {}
				};
				handler.setFormatter(new SimpleFormatter());
				logger.addHandler(handler);
			}
			GatewayServer.turnLoggingOn();
		} else {
			GatewayServer.turnLoggingOff();
		}
	}

	public boolean startAPI(jumpyAPI obj) {
		boolean needs_client = has_callback;
		for (int port=GatewayServer.DEFAULT_PORT; port<GatewayServer.DEFAULT_PORT+32; port++) {
			try {
				if (needs_client) {
					if (avail(port)) {
						client = new CallbackClient(port);
						env.put("JCLIENT", Integer.toString(client.getPort()));
						needs_client = false;
					}
				} else {
					server = has_callback ?
						new GatewayServer(obj, port, GatewayServer.DEFAULT_CONNECT_TIMEOUT,
							GatewayServer.DEFAULT_READ_TIMEOUT, null, client) :
						new GatewayServer(obj, port);
					server.start();
					env.put("JGATEWAY", localhost + ":" + server.getListeningPort());
					return true;
				}
			}
			catch(Py4JNetworkException e) {
				// socket is in use
				continue;
			}
			catch(Exception e) {e.printStackTrace(); continue;}
		}
		if (needs_listener) {
			final command self = this;
			final jumpyAPI api = obj;
			listener = new DefaultGatewayServerListener() {
				@Override
				public void serverError(Exception e) {
					api.util(jumpyAPI.LOG, "serverError: " + e.getMessage(), null);
					self.serverError = true;
				}
			};
		}
		return false;
	}

	public void stopAPI() {
		if (client != null) {
			client.shutdown();
			client = null;
		}
		if (server != null) {
			server.shutdown();
			server = null;
		}
	}

	private String expand(final String arg) {
		return expand(arg, substitutions);
	}

	public static String expand(String arg, Map m) {
		return arg == null ? null :
			StringUtils.stringSubstitution(arg, m != null ? m : basesubs, true).toString();
	}

	public String getScript(boolean editableonly) {
		String s = expand(argv.get(scriptarg));
		if (editableonly && (jumpypy.equals(s) ||
				executables.containsKey(s) || executables.containsValue(s) ||
				(! new File(s).exists()))) {
			s = null;
		}
		return s;
	}

	public String[] toStrings() {
		final String[] args = new String[argv.size()];
		for(int i=0; i<args.length; i++) {
			args[i] = expand(argv.get(i));
		}
		args[scriptarg] = StringUtils.fixFileSeparatorChar(args[scriptarg]);
		startdir = absolute(args[scriptarg]).getParentFile();
		startdir = (startdir == null ? new File("") : startdir).getAbsoluteFile();
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

