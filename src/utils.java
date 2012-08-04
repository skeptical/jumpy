package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.IOException;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;

import java.net.URI;

import java.lang.System;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.lang.management.ManagementFactory;

import org.apache.commons.lang.StringUtils;

import net.pms.PMS;
import net.pms.configuration.PmsConfiguration;
import net.pms.util.PropertiesUtil;

public class utils {

	public static boolean windows = System.getProperty("os.name").startsWith("Windows");
	public static boolean mac = System.getProperty("os.name").contains("OS X");

	//http://stackoverflow.com/questions/4159802/how-can-i-restart-a-java-application
	//http://stackoverflow.com/questions/1518213/read-java-jvm-startup-parameters-eg-xmx

	public static void restart() {
		final ArrayList<String> command = new ArrayList<String>();
		command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
		for (String jvmArg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
			command.add(jvmArg);
		}
		command.add("-cp");
		command.add(ManagementFactory.getRuntimeMXBean().getClassPath());
		// can also use http://stackoverflow.com/questions/41894/0-program-name-in-java-discover-main-class
		command.add(PMS.class.getName());
		command.add("&");
		String cmdString = "";
		for (String s : command) {
			cmdString += s + " ";
		}
		System.out.println("Launching: " + cmdString);

		final ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(new File(System.getProperty("user.dir")));
		System.out.println(pb.directory());
		try {
			pb.start();
		} catch (Exception e) { e.printStackTrace(); }
		System.exit(0);
	}

	public static String getBinPaths(PmsConfiguration configuration) {
		class pathHash extends HashSet<String> {
			@Override
			public boolean add(String s) {
				return add(new File(s));
			}
			public boolean addParent(String s) {
				return add(new File(s).getParentFile());
			}
			public boolean add(File f) {
				return (f != null && f.exists() && f.isDirectory() ? super.add(f.getAbsolutePath()) : false);
			}
		}
		pathHash paths = new pathHash();
		String path;
		if ((path = (String)configuration.getCustomProperty("bin.path")) != null) {
			for (String p : path.split(",")) {
				paths.add(p.trim());
			}
		}
		paths.addParent(configuration.getFfmpegPath());
		paths.addParent(configuration.getMplayerPath());
		paths.addParent(configuration.getVlcPath());
		paths.addParent(configuration.getMencoderPath());
		paths.addParent(configuration.getTsmuxerPath());
		paths.addParent(configuration.getFlacPath());
		paths.addParent(configuration.getEac3toPath());
		paths.addParent(configuration.getDCRawPath());
		paths.addParent(configuration.getIMConvertPath());
		paths.add(PropertiesUtil.getProjectProperties().get("project.binaries.dir"));
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
