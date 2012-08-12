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
		for (Section section : ini.values()) {
			String name = section.getName();
			jumpy.log("Adding user script: " + name);

			if (! (name.startsWith("+") || name.startsWith("-"))) {
				jumpy.top.addItem(jumpyAPI.FOLDER, name, section.remove("cmd"), section.remove("thumb")/*, section*/);
			}
		}
	}

	public void autorun(boolean startup) {
		runner ex = new runner();
		String flag = (startup ? "+" : "-");
		String context = (startup ? "starting " : "finishing ");
		for (Section section : ini.values()) {
			String name = section.getName();
			if (name.startsWith(flag)) {
				jumpy.log("%n");
				jumpy.log(context + name + ".", true);
				jumpy.log("%n");
				ex.run(jumpy.top, section.remove("cmd"), jumpy.syspath, section);
				jumpy.top.env.clear();
			}
		}
	}
}

