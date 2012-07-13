package net.pms.external.infidel.jumpy;

import java.io.File;
import java.util.ArrayList;
import java.lang.System;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.lang.management.ManagementFactory;

import net.pms.PMS;

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
}
