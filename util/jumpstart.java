import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.net.URLDecoder;

import org.apache.commons.exec.util.StringUtils;

import net.pms.external.infidel.jumpy.command;
import net.pms.external.infidel.jumpy.runner;
import net.pms.external.infidel.jumpy.jumpyAPI;

public class jumpstart {

	public static item root = null;
	public static HashMap<String,String> vars = new HashMap<String,String>();

	public static void main(String[] argv) {

		if (argv.length == 0) usage();

		runner ex = new runner(new command());

		runner.version = "0.2.2";
		System.out.println("jumpstart " + runner.version);

		Console c = System.console();
		if (c == null) {
			System.err.println("No console.");
			System.exit(1);
		}

		ex.cmdline.scriptarg = 0;
		File script = new File(argv[ex.cmdline.scriptarg]);
		if (! script.exists()) {
			System.err.printf("'%s' not found.\n", script.getPath());
			usage();
		}

		String[] hist = new String[32];
		int level = 0, last = 0;
		File logfile = new File("jumpstart.log");
		if (logfile.exists()) {
			byte[] b = new byte[(int)logfile.length()];
			try {
				FileInputStream l = new FileInputStream(logfile);
				l.read(b);
				l.close();
			} catch (Exception e) {}
			String[] l = new String(b).trim().split(" +");
			last = l.length;
			for (int i=0; i<last; i++)
				hist[i] = l[i];
		}

		// get the current jar's location from a static context (isn't java lovely?)
		String lib = new File(new jumpstart().getClass().getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();
		try{
			lib = URLDecoder.decode(lib, "UTF-8");
		} catch (Exception e) {}

		// jumpy.py is always located alongside the jar
		command.pms = lib + File.separatorChar + "jumpy.py";
		command.basesubs = new HashMap<String,String>();
		command.basesubs.put("home", new File(lib).getParent().replace("\\","\\\\"));
		command.basesubs.put("PMS", "JUMPSTART");

		root = new item(-1, "root", "[" + StringUtils.toString(argv, " , ") + "]", "",
			lib, null);
		ex.run(runner.QUIET, root, "[" + command.pms + "]", lib, null);

		item current = root;

		while (true) {
			if (! current.discovered && current.type != 0) {
				if (current.children.size() != 0) current.children.clear();
				new runner().run(current, current.uri, current.syspath, current.env);
				current.discovered = true;
			}
			int size = current.children.size();

			c.printf("\n------------- MENU -------------\n");
			if (size != 0) {
				for (int i=0; i<size; i++) {
					item x = (item)current.get(i);
					String type = "";
					switch (x.type) {
						case    1: type = " (AUDIO)"; break;
						case    2: type = " (IMAGE)"; break;
						case    4: type = " (VIDEO)"; break;
						case   16: type = " (PLAYLIST)"; break;
						case   32: type = " (ISO)"; break;
						case 1025: type = " (MEDIA)"; break;
						case    0:
						case 1026: type = ""; break; // FOLDER
						case 1028: type = " (BOOKMARK)"; break;
						case 1032: type = " (ACTION)"; break;
						case 2048: type = " (UNRESOLVED)"; break;
						case 4096: type = " (FEED)"; break;
						case 4097: type = " (AUDIOFEED)"; break;
						case 4098: type = " (IMAGEFEED)"; break;
						case 4100: type = " (VIDEOFEED)"; break;
						case    8:
						default  : type = " (UNKNOWN)"; break;
					}
					c.printf("  [%d] %s%s\n", i+1, x.name, type);
				}
			} else c.printf("  [empty]\n");

			String sel = c.readLine("Choose an item, u=up h=home q=quit [" + (level < last ? hist[level] : "q") + "]: ");
			if (sel.equals("q")) break;
			if (sel.equals("u")){
				if (level > 0) {
					current = (item)current.parent;
					level--;
				}
				continue;
			}
			if (sel.equals("h")){
				current = root;
				level = 0;
				continue;
			}
			if (sel.equals("")) {
				if (level >= last) break;
				sel = hist[level++];
			} else {
				hist[level++] = sel;
				last = level;
			}

			try {
				int s = Integer.parseInt(sel)-1;
				assert s > 0 && s < size;
				current = (item)current.get(s);
				c.printf("--------------------------------\ntype : %d\nname : %s\nuri  : %s\nthumb: %s\n--------------------------------\n",
					current.type, current.name, current.uri, current.thumb);
				if (current.type < 1024 || current.type > 4095) break;
			} catch (Exception e) {
				System.err.printf("Invalid selection: %s\n", sel);
				break;
			}
		}
		try {
			String log = "";
			for (int i=0; i<last; i++)
				log = log + hist[i] + " ";
//			c.printf("log: %s\n", log);
			FileOutputStream l = new FileOutputStream(logfile);
			l.write(log.trim().getBytes());
			l.close();
		} catch (Exception e) {}

		if (runner.active.size() > 0) {
			for (runner r : runner.active) {
				runner.stop(r);
			}
		}
	}

	public static void usage() {
		System.err.printf("Usage: jumpstart <scriptfile>\n");
		System.exit(1);
	}
}

class node {
	public ArrayList<node> children = new ArrayList<node>();
	public node parent = null;
	public String name;
	public void add(node child) {
		child.parent = this;
		if (!children.contains(child))
			children.add(child);
	}
	private void remove(node child) {
		if (children.contains(child))
			children.remove(child);
	}
	public node get(int index) {
		return children.get(index);
	}
	public node get(String name) {
		for (node child : children)
			if (name.equals(child.name))
				return child;
		return null;
	}
}

class item extends node implements jumpyAPI {

	public int type;
	public String uri, thumb, syspath, basepath;
	public Map<String,String> env;
	public boolean discovered;

	item(int type, String name, String uri, String thumb, String syspath, Map<String,String> env) {
		this.type = type; this.name = name; this.uri = uri; this.thumb = thumb;
		this.basepath = this.syspath = syspath;
		this.env = new HashMap<String,String>();
		if (env != null && !env.isEmpty()) {
			this.env.putAll(env);
		}
		this.discovered = false;
	}

	public String getName() {
		return name;
	}
	public Object addItem(int type, String filename, String uri, String thumb, String data) {
		File f = new File(filename);
		String name = f.getName();
		String path = f.getParent();
		item folder = path == null ? this : mkdirs(path, this);
		item i = new item(type, name, uri, thumb, syspath, env);
		folder.add(i);
		return i;
	}
	public void addPath(String path) {
		syspath = (path == null ? basepath : syspath + File.pathSeparator + path);
	}
	public void setEnv(String name, String val) {
		if (name == null) env.clear();
		else env.put(name, val);
	}
	public String util(int action, String arg1, String arg2) {
		System.out.println("util: " + apiName[action] + (arg1 == null ? "" : " " + arg1) + (arg2 == null ? "" : " " + arg2));
		switch (action) {
			case VERSION:
				return runner.version;
			case PLUGINJAR:
				return new jumpstart().getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
			case FOLDERNAME:
				return this.name;
			case SETPMS:
				command.pms = arg1;
				break;
			case REFRESH:
				int level = Integer.parseInt(arg1);
				item folder = this;
				while (folder != null && level-- > -1) {
					System.out.println("refresh: " + folder.getName());
					folder.discovered = false;
					folder = (item)folder.parent;
				}
				break;
			case RUN:
				return Integer.toString(new runner(0).run(this, arg1, syspath, env));
			case GETVAR:
			case GETPROPERTY:
				if (jumpstart.vars.containsKey(arg1)) {
					return jumpstart.vars.get(arg1);
				}
				break;
			case SETVAR:
			case SETPROPERTY:
				jumpstart.vars.put(arg1, arg2);
				break;
			case HOME:
			case PROFILEDIR:
			case LOGDIR:
			case RESTART:
			case REBOOT:
			case XMBPATH: //TODO
			case MKDIRS:
			case ICON:
			case SUBTITLE:
			default:
				break;
		}
		return "";
	}
	public static item mkdirs(String path, item pwd) {
		item child, parent = path.startsWith("/") || path.startsWith("~/") ? jumpstart.root : pwd;
		boolean exists = true;
		for (String dir:path.replace("\\", "/").split("/")) {
			if (dir.equals("") || dir.equals("~")) {
				continue;
			}
			if (! (exists && (child = (item)parent.get(dir)) != null)) {
				child = new item(0, dir, "", "", "", parent.env);
				parent.add(child);
				parent.discovered = true;
				exists = false;
			}
			parent = child;
		}
		return parent;
	}
	public int addPlayer(String name, String cmdline, String supported, int type, int purpose, String desc, String icon, String playback) {
		return 0;
	}
	@Override
	public synchronized void register(Object obj) {}
}



