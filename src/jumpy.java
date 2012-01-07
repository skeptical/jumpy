package net.pms.external.infidel;

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

import javax.swing.JComponent;

import org.apache.commons.lang.StringUtils;
import net.pms.PMS;
import net.pms.util.PMSUtil;
import net.pms.dlna.DLNAResource;
import net.pms.dlna.virtual.VirtualFolder;
import net.pms.external.AdditionalFolderAtRoot;
import net.pms.configuration.PmsConfiguration;
import net.pms.logging.LoggingConfigFileLoader;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import net.pms.external.infidel.py;
import net.pms.external.infidel.jumpyRoot;
import net.pms.external.infidel.jumpyAPI;

public class jumpy implements AdditionalFolderAtRoot, jumpyRoot {

	public static final String appName = "jumpy";
	public static final String version = "0.1.4";
	private static final String msgtag = appName + ": ";
	private PMS pms;
	private PmsConfiguration configuration;
   private Properties conf = null;
	public String home, jumpylog, jumpyconf;
	public boolean debug = false;
	private String pypath;
	private FileOutputStream logfile;
	private quickLog logger;
	private py python;
	private File[] scripts;
	private pyFolder top;
	
	public jumpy() {
		pms = PMS.get();
		configuration = PMS.getConfiguration();
		home = new File(configuration.getPluginDirectory() + File.separatorChar + appName)
			.getAbsolutePath() + File.separatorChar;
		jumpyconf = configuration.getProfileDirectory() + File.separator + appName + ".conf";
		readconf();
		
		try {
			jumpylog = new File(LoggingConfigFileLoader.getLogFilePaths().get("debug.log"))
				.getParent() + File.separatorChar + "jumpy.log";
			logfile = new FileOutputStream(jumpylog);
		} catch(Exception e) {e.printStackTrace();}
		logger = new quickLog(logfile, "[jumpy] ");
		logger.stdout = debug;

		py.python = (String)configuration.getCustomProperty("python.path");
		py.out = logger;
		python = new py();
		pypath = home + "lib";
		
		log(new Date().toString());
		log("initializing jumpy " + version, true);
		log("home=" + home, true);
		log("log=" + jumpylog, true);
		log("conf=" + jumpyconf, true);
		log("python=" + py.python, true);
		log("pypath=" + pypath, true);

		scripts = new File(home).listFiles(
			new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.endsWith(".py") && new File(dir.getPath() + File.separatorChar + name).isFile();
				}
			}
		);

		log("Found " + scripts.length + " scripts.", true);
		log("%n");

		top = new pyFolder(this, "Jumpy", null, null, pypath);
		log("adding root folder.", true);
		for (File script:scripts) {
			log("%n");
			log("loading " + script.getName() + ".", true);
			log("%n");
			python.run(top, script.getPath(), pypath);
		}
		dbgpack_register();
	}
	
	@Override
	public DLNAResource getChild() {
		return top;
	}

	@Override
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
   }

   public void writeconf() {
		conf.setProperty("debug", String.valueOf(debug));
		try {
			FileOutputStream conf_file = new FileOutputStream(jumpyconf);	
			conf.store(conf_file, name());
			conf_file.close();
		} catch (IOException e) {}
   }

   public void dbgpack_register() {
		String files = (String)configuration.getCustomProperty("dbgpack");
		HashSet dbgpk = StringUtils.isBlank(files) ? new HashSet() :
			new HashSet(Arrays.asList(files.split(",")));
		dbgpk.add(jumpylog);
		dbgpk.add(jumpyconf);
		dbgpk.add(new File(jumpylog).getParent() + File.separatorChar + "pmsencoder.log");
		files = StringUtils.join(dbgpk, ',');
		configuration.setCustomProperty("dbgpack", files);
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


