package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.IOException;

import java.util.Map;

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
		ini.getConfig().setMultiOption(true);
		ini.setFile(new File(scriptsini));
		try {
			ini.load();
		} catch (IOException e) {} catch (Exception e) {e.printStackTrace();}
		for (Section section : ini.values()) {
			aggregate(section);
			String name = section.getName();
			jumpy.log("Adding user script: " + name);

			if (! (name.startsWith("+") || name.startsWith("-") || name.startsWith("#"))) {
				scriptFolder folder =
					(scriptFolder)jumpy.top.addItem(jumpyAPI.FOLDER, name, section.remove("cmd").split("\n")[0], section.remove("thumb"));
				folder.env.putAll(section);
			}
		}
	}

	public void autorun(boolean startup) {
		String flag = (startup ? "+" : "-");
		String context = (startup ? "starting " : "finishing ");
		for (Section section : ini.values()) {
			String name = section.getName();
			if (name.startsWith(flag)) {
				jumpy.log("\n");
				jumpy.log(context + name + ".", true);
				jumpy.log("\n");
				new runner().run(jumpy.top, section.remove("cmd").split("\n")[0], null, section);
				jumpy.top.env.clear();
			}
		}
	}

	public void aggregate(Section section) {
		for (String key : section.keySet()) {
			String[] values = section.getAll(key, String[].class);
			if (values.length > 1) {
				String val = values[0].trim();
				int i;
				for (i=1; i < values.length; i++) {
					val += ("\n" + section.remove(key, 1).trim());
				}
				section.put(key, val, 0);
			}
		}
	}
}

