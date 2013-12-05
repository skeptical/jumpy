package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Writer;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.commons.exec.util.StringUtils;

import org.ini4j.Wini;
import org.ini4j.Profile.Section;
import org.ini4j.InvalidFileFormatException;
import org.ini4j.spi.IniHandler;
import org.ini4j.BasicProfile;
import org.ini4j.Options;
import org.ini4j.OptionMap;
import org.ini4j.Persistable;

import net.pms.PMS;
import net.pms.dlna.DLNAResource;
import net.pms.configuration.PmsConfiguration;


public class userscripts {
	private static jumpy jumpy;
	private String scriptsini;
	private File[] autoscripts;
	public ArrayList<Ini> inis;
	public boolean changed;
	public static metaIni meta;

	public userscripts(jumpy jumpy) {
		this.jumpy = jumpy;
		inis = new ArrayList<Ini>();
		meta = new metaIni(jumpy.scriptsini.replace("-scripts.ini", "-meta.ini"));
		inis.add(meta);
		scriptsini = jumpy.scriptsini;
		load(scriptsini);
	}

	public void autoload() {
		autoscripts = new File(jumpy.home).listFiles(
			new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.endsWith(".py") && new File(dir.getPath() + File.separatorChar + name).isFile();
				}
			}
		);

		jumpy.log("\n");
		jumpy.log("Found " + autoscripts.length + " python scripts.", true);

		for (File script : autoscripts) {
			String name = script.getName();
			if (meta.containsKey("#" + name)) {
				continue;
			}
			Section section = meta.add(name);
			String cmd = StringUtils.quoteArgument(script.getPath());
			section.put("cmd", cmd);
			jumpy.log("\n");
			jumpy.log("starting " + name + ".", true);
			jumpy.log("\n");
			jumpy.top.tag = section;
			new runner().run(jumpy.top, cmd, null);
			jumpy.top.env.clear();
			jumpy.top.tag = null;
		}
	}

	public void load(String inifile) {
		jumpy.log("loading " + inifile);
		Ini ini = new Ini(inifile);
		for (Section section : ini.values()) {
			String name = section.getName();
			if (name.startsWith("#")) {
				continue;
			}
			jumpy.log("Adding user script: " + name);

			if (section.containsKey("ini")) {
				hide(section);
				load(section.get("ini"));
			} else if (! (name.startsWith("+") || name.startsWith("-"))) {
				String cmd = section.get("cmd").split("\n")[0];
				String thumb = section.get("thumb");
				jumpy.top.tag = section;
				scriptFolder folder =
					(scriptFolder)jumpy.top.addItem(jumpyAPI.FOLDER, name, cmd, thumb);
				folder.env.putAll(new env(section));
				jumpy.top.tag = null;
			}
		}
		inis.add(ini);
	}

	public void autorun(boolean startup) {
		String flag = (startup ? "+" : "-");
		String context = (startup ? "starting" : "finishing");
		for (Ini ini : inis) {
			for (Section section : ini.values()) {
				if (section.getName().startsWith(flag)) {
					run(section, context);
				}
			}
		}
	}

	public static void run (Section section, String context) {
		String name = section.getName();
		jumpy.log("\n");
		jumpy.log(context + " " + name + ".", true);
		jumpy.log("\n");
		String cmd = section.get("cmd").split("\n")[0];
		jumpy.top.tag = section;
		new runner(name).run(jumpy.top, cmd, null, new env(section));
		jumpy.top.env.clear();
		jumpy.top.tag = null;
	}

	static class env extends HashMap<String,String> {
		Map<String,String> map;
		public env(Map<String,String> map) {
			this.map = map;
		}
		public int 	size() {
			return map.size();
		}
		public Set entrySet() {
			HashSet<Map.Entry<String,String>> items = new HashSet();
			for (Map.Entry<String,String> e : map.entrySet()) {
				if (! isMeta(e.getKey())) {
					items.add(e);
				}
			}
			return items;
		}
	}

	static final String reserved = "_title|_desc|_icon|_link|_version|_conf";
	static final Matcher metavar = Pattern.compile("@.*|$.*|cmd|thumb|" + reserved).matcher("");
	static final Matcher nonedit = Pattern.compile("@.*|" + reserved).matcher("");

	public static boolean isMeta(String key) {
		return metavar.reset(key).matches();
	}

	public static boolean isEditable(String key) {
		return ! nonedit.reset(key).matches();
	}

	public static String getName(Section section) {
		String name = section.getName();
		return name.startsWith("#") ? name.substring(1) : name;
	}

	public static boolean isEnabled(Section section) {
		return ! section.getName().startsWith("#");
	}

	public static boolean isMetaSection(Section section) {
		return isHiddenSection(section) || section.getName().startsWith(".");
	}

	public static boolean isHiddenSection(Section section) {
		return section.getName().startsWith("!") || section.containsKey("@hide");
	}

	public static boolean isHideNonEdit(Section section) {
		return section.containsKey("@hide_nonedit");
	}

	public static void hide(Object section) {
		_put(section, "@hide", "true", null);
	}

	public static void hideNonEdit(Object section) {
		_put(section, "@hide_nonedit", "true", null);
	}

	public static void enable(Section section, boolean enable) {
		String name = section.getName();
		String newname = (enable && name.startsWith("#")) ? name.substring(1) :
			(! enable && ! name.startsWith("#")) ? ("#" + name) : null;
		if (newname != null) {
			utils.setField(section, "_name", newname);
		}
	}

	public static boolean canDisable(OptionMap s) {
		return s.containsKey("cmd");
	}

	public static String _get(OptionMap s, String key, String fallback) {
		return s.containsKey(key) ? s.get(key) :
			s.containsKey("@" + key) ? s.get("@" + key) : fallback;
	}

	public static void _put(Object map, String key, String val, String comment) {
		((OptionMap)map).put(key, val);
		((OptionMap)map).putComment(key, comment);
	}

	public static void restart(Section script) {
		remove(script);
//		inis.remove(script);
//		load(script.getFile());
		if (script.containsKey("cmd")) {
			run(script, "starting");
		}
	}

	public static void remove(Section script) {
		shutdown(script);
		clear(script, utils.fakeroot);
	}

	public static void shutdown(Section script) {
		for (Iterator<runner> it = runner.active.keySet().iterator(); it.hasNext();) {
			runner r = it.next();
			if (runner.active.get(r) == script) {
				runner.stop(r);
				it.remove();
			}
		}
	}

	public static void clear(Section script, DLNAResource folder) {
		for (Iterator<DLNAResource> it = folder.getChildren().iterator(); it.hasNext();) {
			DLNAResource child = it.next();
			if (child instanceof xmbObject && ((xmbObject)child).tag == script) {
				it.remove();
			} else if (child.isFolder()) {
				clear(script, child);
			}
		}
	}


	static class metaIni extends Ini {
		boolean disabled;

		public metaIni (String inifile) {
			super(inifile);
		}

		@Override
		public int getType(String option) {
			return disabled ? 0 : isEditable(option) ? super.getType(option) : SESSION;
		}

		@Override
		protected void store(IniHandler formatter, Section s)
		{
			disabled = ! isEnabled(s);
			if (disabled || isMetaSection(s)) {
				super.store(formatter, s);
			} else if (s == changed_sec) {
				PmsConfiguration configuration = PMS.get().getConfiguration();
				for (String option : s.keySet()) {
					if (super.getType(option) == PMSPROPERTY) {
						configuration.setCustomProperty(option.substring(1), s.get(option));
						pms_save = true;
					}
				}
			}
		}
	}

	interface Doc extends Persistable {
		public void store(OptionMap changed) throws IOException;
	}

	static class Ini extends org.ini4j.Wini implements Doc {

		protected static final int SESSION = 1;
		protected static final int PMSPROPERTY = 2;
		protected static final int NONEDIT = 3;

		protected boolean pms_save = false;
		protected Section changed_sec = null;

		public Ini(String inifile) {
			super();
			getConfig().setMultiSection(true);
			getConfig().setMultiOption(true);
			File f = new File(inifile);
			setFile(f);
			if (f.exists()) {
				try {
					load();
				} catch (Exception e) {e.printStackTrace();}
			}
		}

		@Override
		public void load() throws IOException, InvalidFileFormatException {
			super.load();
			for (Section section : values()) {
				glob(section);
			}
		}

		public static void glob(Section section) {
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

		public int getType (String option) {
			return option.startsWith("@") ? SESSION :
				option.startsWith("$") ? PMSPROPERTY :
				isEditable(option) ? 0 : NONEDIT;
		}

		@Override
		public void store(OptionMap changed) throws IOException {
			changed_sec = (Section)changed;
			store();
			if (pms_save) {
				PMS.get().save();
			}
			changed_sec = null;
			pms_save = false;
		}

//		@Override
//		protected void store (IniHandler formatter, Section s) {
////			Section s = new
//			super.store(formatter, s);
//		}

		@Override
		protected void store(IniHandler formatter, Section section, String option, int index) {
			String val = section.get(option);
			int type = getType(option);
			if (val == null || type == SESSION || (isHideNonEdit(section) && type == NONEDIT)) {
				// skip
			} else if (type == PMSPROPERTY) {
				if (isEnabled(section)) {
					PMS.get().getConfiguration().setCustomProperty(option.substring(1), val);
					pms_save = true;
				}
				super.store(formatter, section, option, index);
			} else if (val.contains("\n")) {
				// unglob
				int i = 0;
				Section s = new BasicProfile().add(section.getName());
				for (String line : val.split("\n")) {
					s.add(option, line);
					super.store(formatter, s, option, i++);
				}
			} else {
				// regular option
				super.store(formatter, section, option, index);
			}
		}
	}


	static class Options extends org.ini4j.Options {
		File usercopy;
		Writer writer;
		static String newline = System.getProperty("line.separator", "\n");

		public Options(File f) throws IOException, InvalidFileFormatException {
			super(f);
		}

		@Override
		public void load() throws IOException {
			super.load();
			if (usercopy == null) {
				usercopy = new File(jumpy.getProfileDirectory(), getFile().getName());
				getConfig().setMultiSection(false);
				getConfig().setMultiOption(false);
			}
			if (usercopy.exists()) {
				load(usercopy);
			}
		}

		@Override
		public void store() throws IOException {
			store(usercopy);
		}

		@Override
		public void store(Writer output) throws IOException {
			writer = output;
			super.store(output);
			writer = null;
		}

		@Override
		public String getComment(Object key) {
			try {
				if (writer != null) writer.write(newline);
			} catch (Exception e) {}
			return super.getComment(key);
		}
	}
}
