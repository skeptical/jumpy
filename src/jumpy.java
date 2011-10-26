package net.pms.external.infidel;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import java.io.IOException;
import java.util.StringTokenizer;

import javax.swing.JComponent;
import javax.swing.JFrame;

import net.pms.PMS;
import net.pms.util.PMSUtil;
import net.pms.dlna.DLNAResource;
import net.pms.dlna.virtual.VirtualFolder;
import net.pms.dlna.virtual.VirtualVideoAction;
import net.pms.dlna.WebVideoStream;
import net.pms.external.ExternalFactory;
import net.pms.external.ExternalListener;
import net.pms.external.AdditionalFolderAtRoot;
import net.pms.configuration.PmsConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.pms.external.infidel.py;
import net.pms.external.infidel.util.jumpyAPI;

//public class jumpy implements AdditionalFolderAtRoot/*ExternalListener*/, jumpyAPI {
public class jumpy implements AdditionalFolderAtRoot, jumpyAPI {

	public static final String appName = "jumpy";
	private static final String version = "0.1";
	private static final String msgtag = appName + ": ";
	private PMS pms;
	private PmsConfiguration configuration;
	private String home;
	private VirtualFolder top;
	private String basepath, path;
//	private static final Logger logger = LoggerFactory.getLogger(jumpy.class);
	
	public jumpy() {
		PMS.minimal("initializing jumpy " + version);
		pms = PMS.get();
		home = System.getProperty("user.dir") + File.separatorChar
			 + "plugins" + File.separatorChar + appName + File.separatorChar;
		configuration = PMS.getConfiguration();
		py.python = (String)configuration.getCustomProperty("python.path");
		basepath = path = py.path = home + "lib";
		
		PMS.minimal(msgtag + "home=" + home);	 
		PMS.minimal(msgtag + "python=" + py.python);	 
		PMS.minimal(msgtag + "python=" + (String)configuration.getCustomProperty("python.path"));
		PMS.minimal(msgtag + "path=" + py.path);	 
	}
	
	@Override
	public DLNAResource getChild() {
		top = new VirtualFolder("Jumpy", null);
		PMS.minimal(msgtag + "adding root folder.");
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

		PMS.minimal(msgtag + "Found " + scripts.length + " scripts.");

		for (File script:scripts) {
			setPath("");;
			py.run(script.getPath(), this);
		}
	}
	
	@Override
	public void addItem(int type, String name, String cmd, String thumb) {
		if(type == 0) {
			top.addChild(new pyFolder(name, cmd, thumb, path));
			System.out.printf("\n[FOLDER] %s\n%s\n%s\n\n", name, cmd, thumb);
		}
	}
	
	@Override
	public void setPath(String dir) {
		path = (dir == null || dir.equals("")) ? basepath : path + File.pathSeparator + dir;
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
	
}
