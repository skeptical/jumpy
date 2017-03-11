package net.pms.external.infidel.jumpy;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.System;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection ;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.pms.configuration.PmsConfiguration;
import net.pms.dlna.DLNAMediaSubtitle;
import net.pms.dlna.DLNAResource;
import net.pms.formats.FormatFactory;
import net.pms.formats.v2.SubtitleType;
import net.pms.formats.WEB;
import net.pms.PMS;
import net.pms.util.PropertiesUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

public final class utils {

	public static boolean windows = System.getProperty("os.name").startsWith("Windows");
	public static boolean mac = System.getProperty("os.name").contains("OS X");
	public static HashMap<String,String> properties = new HashMap<String,String>();

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

	public static Field _getField(Class c, String name) throws NoSuchFieldException {
		Class clazz = c;
		while (true) {
			try {
				Field field = clazz.getDeclaredField(name);
				field.setAccessible(true);
				if ((field.getModifiers() & Modifier.FINAL) == Modifier.FINAL) {
					Field modifiers = Field.class.getDeclaredField("modifiers");
					modifiers.setAccessible(true);
					modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
				}
				return field;
			} catch (NoSuchFieldException n) {
				clazz = clazz.getSuperclass();
				if (clazz == null) {
					throw n;
				}
			} catch (Exception e) {
				return null;
			}
		}
	}

	public static Object getField(Object obj, String name) {
		return getField(obj, name, false);
	}

	public static Object getField(Object obj, String name, boolean quiet) {
		try {
			boolean isClass = obj instanceof Class;
			return _getField(isClass ? (Class)obj : obj.getClass(), name).get(isClass ? null : obj);
		} catch (Exception e) {
			if (! quiet) e.printStackTrace();
		}
		return null;
	}

	public static void setField(Object obj, String name, Object val) {
		try {
			_getField(obj.getClass(), name).set(obj, val);
		} catch (Exception e) {e.printStackTrace();}
	}

	public static Method getFormatSetIconMethod() {
		try {
			// Format.setIcon() is only available under UMS > 2.2.5
			return Class.forName("net.pms.formats.Format")
				.getMethod("setIcon", String.class);
		} catch (Exception e) {}
		return null;
	}

	public static void setMediaSubtitle(DLNAResource d, String path, String lang) {
		DLNAMediaSubtitle sub = new DLNAMediaSubtitle();
		try {
			sub.setId(100); // fake id, not used
			sub.setLang(lang);
			sub.setType(SubtitleType.valueOfFileExtension(FilenameUtils.getExtension(path)));
			sub.setExternalFile(new File(path), null);
			try {
				d.setMediaSubtitle(sub); // ums
			} catch (Throwable t) {
				setField(d, "media_subtitle", sub); // pms
			}
			setField(d, "srtFile", true); // protected
		} catch (Exception e) {e.printStackTrace();}
	}

	public static String run(String... cmd) {
		try {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			Process p = pb.start();
			InputStream is = p.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
			StringBuilder output = new StringBuilder();
			while ((line = br.readLine()) != null) {
				output.append(line).append("\n");
			}
			return output.toString();
		} catch (Exception e) {e.printStackTrace();}
		return "";
	}

	public static String getCustomProperty(String key) {
		return getCustomProperty(key, "");
	}

	public static String getCustomProperty(String key, String fallback) {
		Object obj;
		if ((obj = PMS.getConfiguration().getCustomProperty(key)) != null) {
			// return last occurrence
			return (String)(obj instanceof ArrayList ? (((ArrayList)obj).get(((ArrayList)obj).size()-1)) : obj);
		}
		return fallback;
	}

	public static void checkFFmpeg() {
		String ffmpeg_hdr = run(PMS.getConfiguration().getFfmpegPath());
		if (ffmpeg_hdr.contains("--enable-librtmp")) {
			properties.put("librtmp", "true");
			if (FormatFactory.getAssociatedFormat("rtmp://?") == null) {
				player.addFormat(new WEB() {
					@Override
					public String[] getSupportedExtensions() {
						return (new String[] {"rtmp", "rtmpt", "rtmps", "rtmpe", "rtmfp", "rtmpte", "rtmpts"});
					}
					@Override
					public String toString() {
						return "RTMP";
					}
				});
			}
			if (! getCustomProperty("rtmpdump.force").equals("true")) {
				properties.put("using_librtmp", "true");
			}
		}
		if (ffmpeg_hdr.contains("--enable-libass")) {
			properties.put("libass", "true");
		}
	}

	public static File download(String url, String destdir) {
		System.out.println("downloading: " + url);
		File dest=null, temp=null;
		try {
			URL src = new URL(url);
			dest = new File(destdir, new File(src.getPath()).getName());
			temp = new File(destdir, dest.getName() + ".part");
			if (temp.exists()) temp.delete();
			FileUtils.copyURLToFile(src, temp, 30000, 30000);
			if (temp.exists()) {
				if (dest.exists()) dest.delete();
				temp.renameTo(dest);
				return dest;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (temp != null && temp.exists()) temp.delete();
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
			try {
				installer.setExecutable(true);
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
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
		paths.addExec("dcraw", configuration.getDCRawPath());
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
		if ((editor = (String)PMS.getConfiguration().getCustomProperty("text.editor")) != null) {
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

	// parse duration strings in hh:mm:ss, mm:ss, or ss format
	public static double duration(String d) {
		String[] n = ("0:0:" + d.replace(",", ".")).split(":");
		ArrayUtils.reverse(n);
		try {
			return Double.parseDouble(n[0]) + Double.parseDouble(n[1])*60 + Double.parseDouble(n[2])*3600;
		} catch (Exception e) {
			PMS.debug("Error parsing duration " + d + ": " + e.toString());
		}
		return 0;
	}

	public static String getExtension(String uri) {
		// Omit the query string, if any
		String p = StringUtils.substringBefore(uri, "?");
		// And check for protocol, if any
		int i = p.indexOf("://");
		return FilenameUtils.getExtension(i == -1 ? p : p.substring(i + 3));
	}

}
