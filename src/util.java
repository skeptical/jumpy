package net.pms.external.infidel.jumpy;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.lang.System;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.lang.management.ManagementFactory;

import org.apache.commons.lang.StringUtils;

import net.pms.PMS;
import net.pms.configuration.PmsConfiguration;
import net.pms.util.PropertiesUtil;

public class util {

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

}
