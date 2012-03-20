import java.io.*;
import java.util.ArrayList;
import java.util.Map;
import java.net.URLDecoder;

import net.pms.external.infidel.jumpy.py;
import net.pms.external.infidel.jumpy.jumpyAPI;

public class jumpstart {

	static py py;

	public static void main(String[] argv) {
		
		py = new py();
		py.version = "0.1.3";
		class item {
			public int type;
			public String name, uri, thumb, path;
			item(int type, String name, String uri, String thumb, String path) {
				this.type = type; this.name = name; this.uri = uri; this.thumb = thumb; this.path = path;
			}
		}
		
		class apiobj implements jumpyAPI {
			public ArrayList<item> items;
			public String basepath, path, name = "Item";
			private Map<String,String> env;
			apiobj(String p) {
				basepath = path = p; items = new ArrayList<item>();
			}
			public void addItem(int type, String name, String uri, String thumb) {
				items.add(new item(type, name, uri, thumb, path));
			}
			public void setPath(String dir) {
				path = (dir == null ? basepath : path + File.pathSeparator + dir);
			}
			public void setEnv(String name, String val) {
				if (name == null && val == null ) env.clear();
				else env.put(name, val);
			}
			public String util(int action, String data) {
				System.out.println("util: " + apiName[action] +  ", " + data);
				switch (action) {
					case VERSION:
						return jumpstart.py.version;
					case PLUGINJAR:
						return new jumpstart().getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
					case FOLDERNAME:
						return this.name;
					case HOME:
					case PROFILEDIR:
					case LOGDIR:
					case RESTART:
						break;
				}
				return "";
			}
		}

		Console c = System.console();
		if (c == null) {
			System.err.println("No console.");
			System.exit(1);
		}
		
		File script = new File(argv.length > 0 ? argv[0] : "default.py");
		if (! script.exists()) {
			System.err.printf("'%s' not found.\nUsage: jumpstart <scriptfile>\n", script.getPath());
			System.exit(1);
		}
				
		File logfile = new File("jumpstart.log");
		int r = 0;
		String[] hist = new String[0];
		if (logfile.exists()) {
			byte[] b = new byte[(int)logfile.length()];
			try {
				FileInputStream l = new FileInputStream(logfile);
				l.read(b);
				l.close();
			} catch (Exception e) {}
			hist = new String(b).split(" ");
		}

		// get the current jar's location from a static context (isn't java lovely?)
		String home = new File(new jumpstart().getClass().getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();
		try{
			home = URLDecoder.decode(home, "UTF-8");
		} catch (Exception e) {}
		
//		apiobj obj = new apiobj(home + File.separatorChar + ".." + File.separatorChar + "src" + File.separatorChar + "lib");
		// jumpy.py is always located alongside the jar
		apiobj obj = new apiobj(home);
		String uri = script.getAbsolutePath();
		String log = "";
		
		while (true) {
			obj.items.clear();
			py.run(obj, uri, obj.path, obj.env);
			int size = obj.items.size();
			if (size == 0) break;
			
			c.printf("\n------------- MENU -------------\n");
			for (int i=0; i<size; i++) {
				item x = obj.items.get(i);
				String type = "";
				switch (x.type) {
					case   -2: type = " (UNRESOLVED)"; break;
					case   -1: type = ""; break;
					case    1: type = " (AUDIO)"; break;
					case    2: type = " (IMAGE)"; break;
					case    4: type = " (VIDEO)"; break;
					case   16: type = " (PLAYLIST)"; break;
					case   32: type = " (ISO)"; break;
					case 4096: type = " (FEED)"; break;
					case 4097: type = " (AUDIOFEED)"; break;
					case 4098: type = " (IMAGEFEED)"; break;
					case 4100: type = " (VIDEOFEED)"; break;
					case    0:
					case    8:
					default  : type = " (UNKNOWN)"; break;
				}
				c.printf("  [%d] %s%s\n", i+1, x.name, type);
			}
			boolean def = (hist.length > 0 && r < hist.length);
			String sel = c.readLine("Choose an item or 'q' to quit [" + (def ? hist[r] : "q") + "]: ");
			if (sel.equals("q")) break;
			if (sel.equals("")) {
				if (!def) break;
				sel = hist[r++];
			} else {
				hist = new String[0];
			}
			log = log + sel + " ";
			
			try {
				int s = Integer.parseInt(sel)-1;
				assert s > 0 && s < size;
				item i = obj.items.get(s);
				c.printf("--------------------------------\ntype : %d\nname : %s\nuri  : %s\nthumb: %s\n--------------------------------\n",
					i.type, i.name, i.uri, i.thumb);
				if (i.type > 0) break;
				
				uri = i.uri;
				obj.path = i.path;
				obj.name = i.name;
			} catch (Exception e) {
				System.err.printf("Invalid selection: %s\n", sel);
				break;
			}
		}
//		c.printf("log: %s\n", log);
		try {
			FileOutputStream l = new FileOutputStream(logfile);
			l.write(log.getBytes());
			l.close();
		} catch (Exception e) {}
	}
}

