package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.IOException;

import java.util.Map;
//import java.util.List;
//import java.util.Arrays;

import org.ini4j.Wini;
import org.ini4j.Profile.Section;

import net.pms.dlna.DLNAResource;
import net.pms.dlna.virtual.VirtualFolder;


public class userscripts {
	private jumpy jumpy;
	private String scriptsini;
	public Wini ini;

	public userscripts(jumpy jumpy) {
		this.jumpy = jumpy;
		scriptsini = jumpy.scriptsini;
		load();
	}

	public void load() {
		ini = new Wini();
		ini.getConfig().setMultiSection(true);
		ini.setFile(new File(scriptsini));
		try {
			ini.load();
		} catch (IOException e) {} catch (Exception e) {e.printStackTrace();}
		String path, alt;
		for (Section section : ini.values()) {
			jumpy.log("Adding user script: " + section.getName());

			if ((alt = section.get("cmd")) != null && alt.startsWith("pms ")) {
				section.put("cmd", runner.getpms() + alt.substring(3));
			}

			if ((path = section.remove("folder")) != null) {
				DLNAResource folder = scriptFolder.mkdirs(jumpy.top, path);
				section.remove("autostart");
				scriptFolder script = new scriptFolder(jumpy, section.getName(), section.remove("cmd"), section.remove("thumb"), jumpy.syspath, section);
				folder.addChild(script);
			}
		}
	}

	public void autostart() {
		runner ex = new runner();
		for (Section section : ini.values()) {
			boolean start = Boolean.valueOf(section.remove("autostart"));
			if (start) {
				jumpy.log("%n");
				jumpy.log("starting " + section.getName() + ".", true);
				jumpy.log("%n");
				ex.run(jumpy.top, section.remove("cmd"), jumpy.syspath, section);
				jumpy.top.env.clear();
			}
		}
	}
}

