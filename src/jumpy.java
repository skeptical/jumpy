package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.util.Properties;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JComponent;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.LoggerFactory;
import net.pms.PMS;
import net.pms.dlna.DLNAResource;
import net.pms.external.AdditionalFoldersAtRoot;
import net.pms.configuration.PmsConfiguration;
import net.pms.configuration.RendererConfiguration;
import net.pms.logging.LoggingConfigFileLoader;
import net.pms.formats.Format;
import net.pms.encoders.Player;
import net.pms.dlna.DLNAMediaInfo;
import net.pms.io.OutputParams;
import net.pms.io.ProcessWrapper;
import net.pms.external.URLResolver;
import net.pms.external.dbgpack;
import net.pms.external.DebugPacker;


public class jumpy implements AdditionalFoldersAtRoot, dbgpack, DebugPacker, URLResolver {

	public static final String appName = "jumpy";
	public static final String version = "0.3.6";
	private static final String msgtag = appName + ": ";
	private PMS pms;
	public PmsConfiguration configuration;
	private Properties conf = null;
	public static String home, jumpylog, jumpyconf, bookmarksini, scriptsini, metaini, host;
	public String lasturi;
	public boolean debug, check_update, showBookmarks, verboseBookmarks;
	public int refresh;
	private Timer timer;
	private FileOutputStream logfile;
	private static quickLog logger;
	private static HashSet<String> logspam = new HashSet<String>();
	public static scriptFolder top;
	public static xmbObject util;
	public bookmarker bookmarks;
	public userscripts userscripts;
	public List<player> players;
	public static Map<String,String> icons = new HashMap<String,String>();
	public static File cache;
	private static org.slf4j.Logger LOGGER = LoggerFactory.getLogger(jumpy.class);

	public jumpy() {
		pms = PMS.get();
		configuration = PMS.getConfiguration();
		utils.setField(xmb.fakeroot, "defaultRenderer", RendererConfiguration.getDefaultConf());
		host = new File(configuration.getProfilePath()).getName().split("\\.conf")[0];
		String plugins = configuration.getPluginDirectory();
		home = new File(plugins + File.separatorChar + appName)
			.getAbsolutePath();
		jumpyconf = getProfileDirectory() + File.separator + appName + ".conf";
		readconf();
		config.init(this);
		bookmarksini = getProfileDirectory() + File.separator + appName + "-bookmarks.ini";
		scriptsini = getProfileDirectory() + File.separator + appName + "-scripts.ini";
		metaini = getProfileDirectory() + File.separator + appName + "-meta.ini";
		resolver.jumpy = this;

		try {
			jumpylog = new File(LoggingConfigFileLoader.getLogFilePaths().get("debug.log"))
				.getParent() + File.separatorChar + "jumpy.log";
			logfile = new FileOutputStream(jumpylog);
		} catch(Exception e) {e.printStackTrace();}
		logger = new quickLog(logfile, "[jumpy] ");
		logger.stdout = debug;

		if (configuration.getCustomProperty("python.path") == null) {
			log("\n\n\nWARNING: No 'python.path' setting found in PMS.conf.\n\n\n");
		}

		try {
			cache = new File(PMS.getConfiguration().getTempFolder(), "jumpy");
			if (! cache.isDirectory()) {
				if (! cache.mkdir()) {
					log("Can't create cache: " + cache.getAbsolutePath());
				}
			}
		} catch(Exception e) {e.printStackTrace();}

		runner.out = logger;
		runner.version = version;

		home += File.separatorChar;

		log(new Date().toString());
		log("\n");
		log("initializing jumpy " + version, true);
//		config.checkLatest();
		config.create(new File(jumpyconf), false);
		config.create(new File(scriptsini), false);
		log("\n");

		log("using " + command.py4j_jar);

		String scriptexts = (String)configuration.getCustomProperty("script.filetypes");
		if (scriptexts != null) {
			for (String ext : scriptexts.split(",")) {
				String interpreter = (String)configuration.getCustomProperty(ext + ".interpreter");
				if (interpreter == null) {
					log("WARNING: add a '" + ext + ".interpreter' setting to PMS.conf if you want automatic '" + ext + "' script support.");
				} else {
					log("registering " + interpreter + " to interpret ." + ext + " scripts.");
				}
				command.interpreters.put(ext, interpreter);
			}
		}

		Object path;
		for (String interpreter : command.interpreters.values()) {
			if ((path = configuration.getCustomProperty(interpreter + ".path")) != null) {
				log("setting " + interpreter + " to " + (String)path);
				command.putexec(interpreter, (String)path);
			}
		}

		command.pms = home + "lib" + File.separatorChar + "jumpy.py";
		String bin = utils.getBinPaths(configuration, command.executables);
		command.basepath =
			home + "lib" + (bin == null ? "" : (File.pathSeparator + bin));
		command.basesubs = new HashMap<String,String>() {{
			put("home", home.substring(0, home.length() - 1).replace("\\","\\\\"));
			put("PMS", host);
		}};

		utils.checkFFmpeg();
		log(utils.properties.toString());

		log("\n");
		log("home=" + home, true);
		log("log=" + jumpylog, true);
		log("conf=" + jumpyconf, true);
		log("bookmarks=" + bookmarksini, true);
		log("userscripts=" + scriptsini, true);
		log("refresh=" + refresh, true);
		log("PATH=" + command.basepath);

		log("\n");
		for (Map.Entry<String,String> var : command.executables.entrySet()) {
			log(var.getKey() + "=" + var.getValue());
		}

		log("\n");
		log("Adding root folder.", true);
		top = new scriptFolder(this, "Jumpy", null, null);
		xmb.fakeroot.addChild(top);
		xmb.home = top;

		new runner(runner.QUIET).run(top, "[" + command.pms + "]", null, null);
		if (top.env.containsKey("imconvert")) {
			command.putexec("imconvert", top.env.get("imconvert"));
		}
		top.env.clear();
		log("\n");

		player.out = logger;
		players = new ArrayList<player>();
		players.add(new player(this,
			"@", "", "jump", "video/mpeg", Format.VIDEO, Player.MISC_PLAYER,
			"Jumpy Video Action Player", "#checkmark", 0, 1, 0) {
				public ProcessWrapper launchTranscode(DLNAResource dlna,
						DLNAMediaInfo media, OutputParams params) throws IOException {
					String filename = dlna.getSystemName();
					xmbAction action = (xmbAction)dlna;
					if (action.userdata != null && action.userdata.startsWith("CMD")) {
						finalize(filename, dlna);
						int exitcode = action.run(jumpy.top, cmdline);
						if (action.userdata.startsWith("CMD : ")) {
							String[] msg = action.userdata.split(" : ");
							filename = "[pms , " + (exitcode == 0 ? ("ok , " + msg[1]) : ("err , " + msg[2])) + "]";
						} else if (action.userdata.startsWith("CMDCONSOLE")) {
							filename = "[pms , vmsg , rate=0.25 , fill='white' , background='black' , pointsize=16 , gravity='NorthWest' , file='"
								+ action.ex.output.replace("\n", "\\n") + "']";
						}
						cmdline = null;
					}
					return launchTranscode(dlna, media, params, filename);
				}
			});

		userscripts = new userscripts(this);

		resolver.verify();
		log("\n");

		userscripts.autorun(true);

		if (showBookmarks) {
			bookmarks = new bookmarker(this);
		}

		if (refresh != 0) {
			util = new xmbObject("Util", "#wrench", true);
			top.addChild(util);
			final jumpy self = this;
			util.addChild(new xmbAction("Refresh",
					"jump+CMD : Marking folders for refresh :  ", null, "#refresh") {
				public int run(scriptFolder folder, command cmdline) {
					self.refresh(false);
					return 0;
				}
			});
			refresh(false);
		}

		userscripts.autoload();

		for (DLNAResource item : top.getChildren()) {
			if (item instanceof scriptFolder) {
				((scriptFolder)item).canBookmark = false;
			}
		}
		xmb.startup = false;
	}

	@Override
	public Iterator<DLNAResource> getChildren() {
		return xmb.fakeroot.getChildren().iterator();
	}

	public static void logonce(String msg, String id, boolean minimal) {
		if (! logspam.contains(id)) {
			log(msg, minimal);
			logspam.add(id);
		}
	}

	public static synchronized void log(String msg) {
		logger.log(msg);
	}

	public static synchronized void log(String msg, boolean minimal) {
		if (minimal) {
			LOGGER.info("{Jumpy} {}{}", msgtag, msg);
		}
		logger.log(msg);
	}

	@Override
	public JComponent config() {
		return config.mainPanel();
	}

	@Override
	public String name() {
		return "Jumpy";
	}

	@Override
	public void shutdown () {
		userscripts.autorun(false);

		if (runner.active.size() > 0) {
			for (runner r : runner.active.keySet()) {
				runner.stop(r);
			}
		}
		if (players.size() > 0) {
			boolean changed = false;
			for (player p : players) {
				changed = p.enable(false);
			}
			if (changed) pms.save();
		}
	}

	public static String getProfileDirectory() {
		return PMS.getConfiguration().getProfileDirectory() + File.separator + appName;
	}

	public void readconf() {
		if (conf == null) {
			conf = new Properties();
			try {
				FileInputStream conf_file = new FileInputStream(jumpyconf);
				conf.load(conf_file);
				conf_file.close();
			} catch (IOException e) {}
		}
		debug = Boolean.valueOf(conf.getProperty("debug", "false"));
		check_update = Boolean.valueOf(conf.getProperty("check_update", "true"));
		showBookmarks = Boolean.valueOf(conf.getProperty("bookmarks", "true"));
		verboseBookmarks = Boolean.valueOf(conf.getProperty("verbose_bookmarks", "true"));
		refresh = Integer.valueOf(conf.getProperty("refresh", "60"));
		resolver.enabled = Boolean.valueOf(conf.getProperty("url_resolver", "true"));
		resolver.playback = Boolean.valueOf(conf.getProperty("resolve_at_playback", "true"));
	}

	public boolean writeconf() {
		conf.setProperty("debug", String.valueOf(debug));
		conf.setProperty("check_update", String.valueOf(check_update));
		conf.setProperty("bookmarks", String.valueOf(showBookmarks));
		conf.setProperty("verbose_bookmarks", String.valueOf(verboseBookmarks));
		conf.setProperty("refresh", String.valueOf(refresh));
//		if (jumpy.host.equals("UMS")) {
			conf.setProperty("url_resolver", String.valueOf(resolver.enabled));
//		}
		if (! resolver.playback) {
			conf.setProperty("resolve_at_playback", String.valueOf(resolver.playback));
		}
		try {
			FileOutputStream conf_file = new FileOutputStream(jumpyconf);
			conf.store(conf_file, null);
			conf_file.close();
		} catch (IOException e) {return false;}
		return true;
	}

	public void refreshChildren(scriptFolder folder) {
		for (DLNAResource item : folder.getChildren()) {
			if (item instanceof scriptFolder) {
				((scriptFolder)item).refresh();
			}
		}
	}

	public void refresh(boolean timed) {
		refreshChildren(top);
		if (showBookmarks) {
			refreshChildren(bookmarks.Bookmarks);
		}
		if (timed) {
			log("Timed " + refresh + " minute refresh.");
		} else if (refresh > 0) {
			if (timer != null) {
				timer.cancel();
			}
			timer = new Timer(true);
			final jumpy me = this;
			timer.scheduleAtFixedRate(new TimerTask() {
				public void run() {
					me.refresh(true);
				}
			}, refresh * 60000, refresh * 60000);
			log("Refresh, resetting " + refresh + " minute timer.");
		} else {
			log("Refresh.");
		}
	}

	public void bookmark(scriptFolder folder) {
		if (!folder.isBookmark) {
			// if the renderer can't play the xmbAction it may send repeated requests
			if (folder.uri.equals(lasturi)) return;
			lasturi = folder.uri;
			bookmarks.add(folder);
		} else {
			bookmarks.remove(folder);
		}
	}

	public static String getResource(String name) {
		if (StringUtils.isBlank(name)) {
			return null;
		}
		String[] val = name.split("\\+", 2);
		String src = val[0];
		if (src != null && src.startsWith("#")) {
			src = home + "lib" + File.separatorChar + "resources" + File.separatorChar
				+ "icon" + File.separatorChar + src.substring(1) + ".png";
		}
		if (! src.contains("://") && ! new File(src).exists()) {
			jumpy.log("Resource not found: " + src);
			return name;
		}
		if (val.length > 1) {
			String dest = cache.getAbsolutePath() + File.separatorChar +
				FilenameUtils.getBaseName(src) + "+" + val[1] + "." + FilenameUtils.getExtension(src);
			File f = new File(dest);
			if (! f.exists()) {
				new runner().run(top, "[pms , imgfx , " + src + " , " + val[1] + " , " + cache + "]", null);
				if (! f.exists()) {
					jumpy.log("Failed to generate resource " + dest);
					return src;
				}
			}
			src = dest;
		}
		return src;
	}

	public static String getIcon(String fmt) {
		return icons.get(fmt);
	}

	public static void setIcon(String fmt, String img) {
		log("setting icon '" + img + "'  for " + fmt);
		for (String f : fmt.split("\\|")) {
			icons.put(f, img);
		}
	}

	public int addPlayer(String name, String cmd, String supported, int mediatype, int purpose, String desc, String icon, String playback, int priority) {
		players.add(new player(this, name, cmd, supported, mediatype, purpose, desc, icon, playback, priority));
		return players.size() - 1;
	}

	public Object dbgpack_cb() {
		return new String[] {jumpylog, jumpyconf, scriptsini, metaini};
	}

	@Override
	public URLResult urlResolve(String url) {
		if (resolver.enabled) {
			URLResult res = new URLResult();
			if((res.url = resolver.resolve(url)) != null) {
				return res;
			}
		}
		return null;
	}
}

class quickLog extends PrintStream {
	private static String tag;
	public static boolean stdout = false;
	String CRLF = System.getProperty("line.separator");

	public quickLog(FileOutputStream log, String tag) {
		super(log);
		this.tag = tag;
	}

	public synchronized void log(String msg) {
		if (utils.windows) {
			msg = msg.replaceAll("\n", CRLF);
		}
		print(msg.trim().equals("") ? msg : tag + msg + CRLF);
	}

	public synchronized void write(byte buf[], int off, int len) {
		try {
			super.write(buf, off, len);
			flush();
			if (stdout) {
				System.out.write(buf, off, len);
				System.out.flush();
			}
		} catch (Exception e) {}
	}
}


