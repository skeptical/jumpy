package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;

import java.util.Properties;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JComponent;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import net.pms.PMS;
import net.pms.util.PMSUtil;
import net.pms.dlna.DLNAResource;
import net.pms.dlna.virtual.VirtualFolder;
import net.pms.dlna.virtual.VirtualVideoAction;
import net.pms.external.AdditionalFolderAtRoot;
import net.pms.configuration.PmsConfiguration;
import net.pms.logging.LoggingConfigFileLoader;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import com.sharkhunter.dbgpack.dbgpack;


public class jumpy implements AdditionalFolderAtRoot, dbgpack {

	public static final String appName = "jumpy";
	public static final String version = "0.2.0";
	private static final String msgtag = appName + ": ";
	private PMS pms;
	public PmsConfiguration configuration;
	private Properties conf = null;
	public String home, jumpylog, jumpyconf, bookmarksini, scriptsini, lasturi;
	public boolean debug, showBookmarks, verboseBookmarks;
	public int refresh;
	private Timer timer;
	public String syspath;
	private FileOutputStream logfile;
	private quickLog logger;
	private File[] scripts;
	public scriptFolder top, util;
	private bookmarker bookmarks;
	private userscripts userscripts;

	public jumpy() {
		pms = PMS.get();
		configuration = PMS.getConfiguration();
		String plugins = configuration.getPluginDirectory();
		home = new File(plugins + File.separatorChar + appName)
			.getAbsolutePath() + File.separatorChar;
		jumpyconf = configuration.getProfileDirectory() + File.separator + appName + ".conf";
		readconf();
		bookmarksini = configuration.getProfileDirectory() + File.separator + appName + "-bookmarks.ini";
		scriptsini = configuration.getProfileDirectory() + File.separator + appName + "-scripts.ini";

		try {
			jumpylog = new File(LoggingConfigFileLoader.getLogFilePaths().get("debug.log"))
				.getParent() + File.separatorChar + "jumpy.log";
			logfile = new FileOutputStream(jumpylog);
		} catch(Exception e) {e.printStackTrace();}
		logger = new quickLog(logfile, "[jumpy] ");
		logger.stdout = debug;

		if (configuration.getCustomProperty("python.path") == null) {
			log("%n%n%nWARNING: No 'python.path' setting found in PMS.conf.%n%n%n");
		}

		runner.pms = home + "lib" + File.separatorChar + "jumpy.py";
		runner.out = logger;
		runner.version = version;
		syspath = home + "lib";

		log(new Date().toString());
		log("%n");
		log("initializing jumpy " + version, true);
		log("%n");

		String scriptexts = (String)configuration.getCustomProperty("script.filetypes");
		if (scriptexts != null) {
			for (String ext : scriptexts.split(",")) {
				String interpreter = (String)configuration.getCustomProperty(ext + ".interpreter");
				if (interpreter == null) {
					log("WARNING: add a '" + ext + ".interpreter' setting to PMS.conf if you want automatic '" + ext + "' script support.");
				} else {
					log("registering " + interpreter + " to interpret ." + ext + " scripts.");
				}
				runner.interpreters.put(ext, interpreter);
			}
		}

		Object path;
		for (String interpreter : runner.interpreters.values()) {
			if ((path = configuration.getCustomProperty(interpreter + ".path")) != null) {
				log("setting " + interpreter + " to " + (String)path);
				runner.putexec(interpreter, (String)path);
			}
		}

		log("%n");
		log("home=" + home, true);
		log("log=" + jumpylog, true);
		log("conf=" + jumpyconf, true);
		log("bookmarks=" + bookmarksini, true);
		log("userscripts=" + bookmarksini, true);
		log("refresh=" + refresh, true);
		log("python=" + runner.getexec("python"), true);
		log("pypath=" + syspath, true);

		log("Adding root folder.", true);
		top = new scriptFolder(this, "Jumpy", null, null, syspath);

		runner ex = new runner();
		ex.quiet(top, "[" + runner.pms + "]", syspath, null);
		log("%n");

		scripts = new File(home).listFiles(
			new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.endsWith(".py") && new File(dir.getPath() + File.separatorChar + name).isFile();
				}
			}
		);

		if (showBookmarks) {
			bookmarks = new bookmarker(this);
		}


		if (refresh != 0) {
			util = new scriptFolder(this, "Util", null, null, syspath);
			top.addChild(util);
			final jumpy me = this;
			util.addChild(new VirtualVideoAction("Refresh", true) {
				public boolean enable() {
					me.refresh(false);
					return true;
				}
			});
			refresh(false);
		}

		userscripts = new userscripts(this);
		userscripts.autorun(true);

		log("%n");
		log("Found " + scripts.length + " python scripts.", true);

		for (File script:scripts) {
			log("%n");
			log("starting " + script.getName() + ".", true);
			log("%n");
			ex.run(top, "[" + script.getPath() + "]", syspath);
			top.env.clear();
		}

		for (DLNAResource item : top.getChildren()) {
			if (item instanceof scriptFolder) {
				((scriptFolder)item).canBookmark = false;
			}
		}
	}

	@Override
	public DLNAResource getChild() {
		return top;
	}

	public synchronized  void log(String msg) {
		logger.log(msg);
	}

	public synchronized void log(String msg, boolean minimal) {
		if (minimal) {
			PMS.minimal(msgtag + msg);
		}
		logger.log(msg);
	}

	@Override
	public JComponent config() {
		return null;
	}

	@Override
	public String name() {
		return "Jumpy";
	}

	@Override
	public void shutdown () {
		userscripts.autorun(false);
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
		showBookmarks = Boolean.valueOf(conf.getProperty("bookmarks", "true"));
		verboseBookmarks = Boolean.valueOf(conf.getProperty("verbose_bookmarks", "true"));
		refresh = Integer.valueOf(conf.getProperty("refresh", "60"));
	}

	public void writeconf() {
		conf.setProperty("debug", String.valueOf(debug));
		conf.setProperty("bookmarks", String.valueOf(showBookmarks));
		conf.setProperty("verbose_bookmarks", String.valueOf(verboseBookmarks));
		conf.setProperty("refresh", String.valueOf(refresh));
		try {
			FileOutputStream conf_file = new FileOutputStream(jumpyconf);
			conf.store(conf_file, null);
			conf_file.close();
		} catch (IOException e) {}
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
			refreshChildren(bookmarks.bookmarks);
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
			// if the renderer can't play the VirtualVideoAction it may send repeated requests
			if (folder.uri.equals(lasturi)) return;
			lasturi = folder.uri;
			bookmarks.add(folder);
		} else {
			bookmarks.remove(folder);
		}
	}

	public Object dbgpack_cb() {
		return new String[] {jumpylog, jumpyconf};
	}

}

class quickLog extends PrintStream {
	private static String tag;
	public static boolean stdout = false;

	public quickLog(FileOutputStream log, String tag) {
		super(log);
		this.tag = tag;
	}

	public synchronized void log(String msg) {
		printf(msg.trim().replaceAll("%n","").equals("") ? msg : tag + msg + "%n");
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


