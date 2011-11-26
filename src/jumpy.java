package net.pms.external.infidel;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;

import java.util.Properties;

import javax.swing.JComponent;
//import javax.swing.JFrame;

import net.pms.PMS;
import net.pms.util.PMSUtil;
import net.pms.dlna.DLNAResource;
import net.pms.dlna.virtual.VirtualFolder;
import net.pms.external.AdditionalFolderAtRoot;
import net.pms.configuration.PmsConfiguration;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import net.pms.external.infidel.py;
import net.pms.external.infidel.jumpyRoot;
import net.pms.external.infidel.jumpyAPI;

public class jumpy implements AdditionalFolderAtRoot, jumpyAPI, jumpyRoot {

	public static final String appName = "jumpy";
	private static final String version = "0.1.2";
	private static final String msgtag = appName + ": ";
	private PMS pms;
	private PmsConfiguration configuration;
   private Properties conf = null;
	private String home;
	private boolean debug = false;
	private VirtualFolder top;
	private String basepath, pypath;
	private FileOutputStream logfile;
	private quickLog logger;
	private py python;
	
	public jumpy() {
		pms = PMS.get();
		home = System.getProperty("user.dir") + File.separatorChar
			 + "plugins" + File.separatorChar + appName + File.separatorChar;
		configuration = PMS.getConfiguration();
		readconf();
		
		try {
			logfile = new FileOutputStream(home + "jumpy.log");
		}catch(Exception e) {e.printStackTrace();}
		logger = new quickLog(logfile, "[jumpy] ");
		logger.stdout = debug;
		
		py.python = (String)configuration.getCustomProperty("python.path");
		py.out = logger;
		python = new py();
		basepath = pypath = home + "lib";
		
		log("initializing jumpy " + version, true);
		log("home=" + home, true);
		log("python=" + py.python, true);
		log("pypath=" + pypath, true);
	}
	
	@Override
	public DLNAResource getChild() {
		top = new VirtualFolder("Jumpy", null);
		log("adding root folder.", true);
		loadScripts();
		return top;
	}

	public void loadScripts() {
		File[] scripts = new File(home).listFiles(
			new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.endsWith(".py") && new File(dir.getPath() + File.separatorChar + name).isFile();
				}
			}
		);

		log("Found " + scripts.length + " scripts.", true);
		log("%n");

		for (File script:scripts) {
			log("loading " + script.getName() + ".", true);
			log("%n");
			setPath(null);
			python.run(this, script.getPath(), pypath);
		}
	}
	
	@Override
	public void addItem(int type, String name, String uri, String thumb) {
		if (type == FOLDER) {
			top.addChild(new pyFolder(this, name, uri, thumb, pypath));
		}
	}
	
	@Override
	public void log(String msg) {
		logger.log(msg);
	}
	
	public synchronized void log(String msg, boolean minimal) {
		if (minimal) {
			PMS.minimal(msgtag + msg);
		}
		logger.log(msg);
	}
	
	@Override
	public void setPath(String dir) {
		pypath = (dir == null ? basepath : pypath + File.pathSeparator + dir);
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
				FileInputStream conf_file = new FileInputStream(home + appName + ".conf");	
				conf.load(conf_file);
				conf_file.close();
			} catch (IOException e) {}
		}
		debug = Boolean.valueOf(conf.getProperty("debug", "false"));
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


