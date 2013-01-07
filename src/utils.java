package net.pms.external.infidel.jumpy;

import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;

import java.net.URI;
import java.net.URL;
import java.net.URLConnection ;
import java.net.ConnectException;

import java.lang.System;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;

import org.apache.commons.lang.StringUtils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import net.pms.PMS;
import net.pms.configuration.PmsConfiguration;
import net.pms.configuration.RendererConfiguration;
import net.pms.util.PropertiesUtil;
import net.pms.dlna.DLNAResource;
import net.pms.dlna.RootFolder;
import net.pms.dlna.virtual.VirtualFolder;

public final class utils {

	public static boolean windows = System.getProperty("os.name").startsWith("Windows");
	public static boolean mac = System.getProperty("os.name").contains("OS X");
	public static DLNAResource fakeroot = new VirtualFolder("fakeroot", null);
	public static DLNAResource home = null;
	static ArrayList<RendererConfiguration> foundRenderers = null;
	public static boolean startup = true;

	//http://stackoverflow.com/questions/4159802/how-can-i-restart-a-java-application
	//http://stackoverflow.com/questions/1518213/read-java-jvm-startup-parameters-eg-xmx

	public static ArrayList<String> PMS_cmd() {
		ArrayList<String> restart = new ArrayList<String>();
		restart.add(org.apache.commons.exec.util.StringUtils.quoteArgument(
			 System.getProperty("java.home") + File.separator + "bin" + File.separator +
			 ((windows && System.console() == null) ? "javaw" : "java")));
		for (String jvmArg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
			restart.add(org.apache.commons.exec.util.StringUtils.quoteArgument(jvmArg));
		}
		restart.add("-cp");
		restart.add(ManagementFactory.getRuntimeMXBean().getClassPath());
		// could also use generic main discovery instead:
		// see http://stackoverflow.com/questions/41894/0-program-name-in-java-discover-main-class
		restart.add(PMS.class.getName());
		return restart;
	}

	public static void restart() {
		restart(null, null, null);
	}

	public static void restart(ArrayList<String> cmd, Map<String,String> env, String startdir) {
		final ArrayList<String> restart = PMS_cmd();
		if (cmd == null) {
			cmd = restart;
		} else {
			if (env == null) {
				env = new HashMap<String,String>();
			}
			env.put("RESTART", StringUtils.join(restart, " "));
			env.put("RESTARTDIR", System.getProperty("user.dir"));
		}
		if (startdir == null) {
			startdir = System.getProperty("user.dir");
		}

		System.out.println("starting: " + StringUtils.join(cmd, " "));

		final ProcessBuilder pb = new ProcessBuilder(cmd);
		if (env != null) {
			pb.environment().putAll(env);
		}
		pb.directory(new File(startdir));
		System.out.println("in directory: " + pb.directory());
		try {
			pb.start();
		} catch (Exception e) { e.printStackTrace(); return; }
		System.exit(0);
	}

	public static ArrayList<RendererConfiguration> getFoundRenderers() {
		// reflect upon (private final) PMS.foundRenderers
		try {
			PMS instance = PMS.get();
			Field field = instance.getClass().getDeclaredField("foundRenderers");
			field.setAccessible(true);
			return (ArrayList<RendererConfiguration>)field.get(instance);
		} catch (Exception e) {
			// shouldn't happen
			PMS.debug(e.toString());
			return null;
		}
	}

	public static void refreshRoot() {
		if (foundRenderers == null) {
			foundRenderers = getFoundRenderers();
		}
		for (RendererConfiguration r : foundRenderers) {
			RootFolder root = r.getRootFolder();
			root.getChildren().clear();
			root.reset();
		}
	}

	public static DLNAResource mkdirs(String path, DLNAResource pwd) {
		boolean exists = true, atroot=path.startsWith("/"), rootchanged=false;
		DLNAResource child, parent = atroot ? fakeroot : path.startsWith("~/") ? home : pwd;
		for (String dir : path.split("/")) {
			if (dir.equals("") || dir.equals("~")) continue;
			if (! (exists && (child = parent.searchByName(dir)) != null)) {
				if (atroot) {
					rootchanged = true;
					atroot = false;
				}
				child = new VirtualFolder(dir, null);
				parent.addChild(child);
				exists = false;
			}
			parent = child;
		}
		if (! startup && (rootchanged || atroot)) {
			refreshRoot();
		}
		return parent;
	}

	public static String getXMBPath(DLNAResource folder, DLNAResource ancestor) {
		DLNAResource p = folder;
		String xmbpath = "/";
		while (true) {
			p = p.getParent();
			if (p == null || p == ancestor) {
				break;
			}
			xmbpath = p.getName().trim() + "/" + xmbpath;
		}
		return (p == null ? "/" : "") + xmbpath.replace("//","").trim();
	}

	public static File download(String url, String destdir) {
		System.out.println("downloading: " + url);
		File dest=null, temp=null;
		try {
			URL src = new URL(url);
			dest = new File(destdir, new File(src.getPath()).getName());
			temp = new File(destdir, dest.getName() + ".part");
			FileUtils.copyURLToFile(src, temp, 30000, 30000);
			if (temp.exists()) {
				temp.renameTo(dest);
				return dest;
			}
		} catch (Exception e) {
			if (temp != null && temp.exists()) temp.delete();
			e.printStackTrace();
		}
		return null;
	}

	public static String gettext(String url) {
		try {
			URLConnection c = new URL(url).openConnection();
			c.setConnectTimeout(10000);
			c.setReadTimeout(10000);
			return IOUtils.toString(c.getInputStream());
		} catch (Exception e) { System.out.println(e.toString()); }
		return null;
	}

	public static boolean isNewer(String v1, String v0) {
		// expand and compare gnu-style major.minor.revision[suffix] version strings
		String x1 = String.format("%3s%3s%3s%s",
			(Object[])(v1.replaceFirst("([\\d.]+)","$1. ").split("\\.")));
		String x0 = String.format("%3s%3s%3s%s",
			(Object[])(v0.replaceFirst("([\\d.]+)","$1. ").split("\\.")));
		return (x0.compareTo(x1) < 0);
	}

	public static boolean update(String url) {
		File installer = download(url, "plugins");
		if (installer != null) {
			ArrayList<String> cmd = new ArrayList<String>();
			cmd.add(installer.getAbsolutePath());
			if (url.endsWith(".run")) {
				cmd.add("--");
			}
			cmd.add("--selfupdate");
			Map<String,String> env = new HashMap<String,String>();
			env.put("PROFILE_PATH", PMS.getConfiguration().getProfilePath());
			restart(cmd, env, installer.getParent());
		}
		return false;
	}

	public static String getBinPaths(PmsConfiguration configuration, final Map<String,String> executables) {
		class pathHash extends HashSet<String> {
			public List syspath = Arrays.asList(
				System.getenv(windows ? "Path" : "PATH").split(File.pathSeparator));
			@Override
			public boolean add(String s) {
				return add(new File(s));
			}
			public boolean addParent(String s) {
				return add(new File(s).getParentFile());
			}
			public boolean add(File f) {
				return (f != null && f.exists() && f.isDirectory() && ! syspath.contains(f.getAbsolutePath()) ?
					super.add(f.getAbsolutePath()) : false);
			}
			public boolean addExec(String n, String s) {
				if (s != null) {
					executables.put(n, s);
					return addParent(s);
				}
				return false;
			}
		}
		pathHash paths = new pathHash();
		String path;
		if ((path = (String)configuration.getCustomProperty("bin.path")) != null) {
			for (String p : path.split(",|" + File.pathSeparator)) {
				paths.add(p.trim());
			}
		}
		for (String s : executables.values()) {
			paths.addParent(s);
		}
		paths.addExec("ffmpeg", configuration.getFfmpegPath());
		paths.addExec("mplayer", configuration.getMplayerPath());
		paths.addExec("vlc", configuration.getVlcPath());
		paths.addExec("mencoder", configuration.getMencoderPath());
		paths.addExec("tsmuxer", configuration.getTsmuxerPath());
		paths.addExec("flac", configuration.getFlacPath());
		paths.addExec("eac3to", configuration.getEac3toPath());
		paths.addExec("dcraw", configuration.getDCRawPath());
		paths.addExec("convert", configuration.getIMConvertPath());
		paths.add(PropertiesUtil.getProjectProperties().get("project.binaries.dir"));
		paths.add(windows ? "win32" : mac ? "osx" : "linux");
//		paths.remove("");
//		paths.remove(null);
		String s = StringUtils.join(paths, File.pathSeparator);
		return "".equals(s) ? null : s;
	}

	public static void textedit(String file) {
		// see if we have a user-specified editor
		String[] cmd;
		String editor;
		if ((editor = (String)PMS.get().getConfiguration().getCustomProperty("text.editor")) != null) {
			cmd = new String[] {editor, file};
			try {
				Runtime.getRuntime().exec(cmd);
				PMS.debug("Opening editor: " + Arrays.toString(cmd));
				return;
			} catch (Exception e) {
				PMS.debug("Failed to open editor: " + Arrays.toString(cmd));
			}
		}
		// try a default desktop edit and failing that an explicit system cmd
		if (! edit(file)) {
			cmd = windows ? new String[] {"Notepad", file} :
					mac ? new String[] {"open", "-t", file} :
					new String[] {"xdg-open", file};
			try {
				Runtime.getRuntime().exec(cmd);
				PMS.debug("Opening editor: " + Arrays.toString(cmd));
			} catch (Exception e) {
				PMS.debug("Failed to open editor: " + Arrays.toString(cmd));
			}
		}
	}

	public static boolean browse(String uri) {
		try {
			if(uri.startsWith("file://") && windows) {
				return open(uri.substring(7));
			} else {
				java.awt.Desktop.getDesktop().browse(new URI(uri));
				PMS.debug("Opening default browser: " + uri);
			}
			return true;
		} catch (Exception e) {
			PMS.debug("Failed to open default browser: " + uri);
			return false;
		}
	}

	public static boolean edit(String file) {
		File f = new File(file);
		try {
			java.awt.Desktop.getDesktop().edit(f);
			PMS.debug("Opening default editor: " + f);
			return true;
		} catch (Exception e) {
			PMS.debug("Failed to open default editor: " + f);
			return false;
		}
	}

	public static boolean open(String file) {
		File f = new File(file);
		try {
			java.awt.Desktop.getDesktop().open(f);
			PMS.debug("Opening default viewer: " + f);
			return true;
		} catch (Exception e) {
			PMS.debug("Failed to open default viewer: " + f);
			return false;
		}
	}
}
