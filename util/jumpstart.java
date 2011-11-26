import java.io.File;
import java.io.Console;
import java.util.ArrayList;
import java.net.URLDecoder;


import net.pms.external.infidel.py;
import net.pms.external.infidel.jumpyAPI;

public class jumpstart {
	public static void main(String[] argv) {
		
		py py = new py();
		class item {
			public int type;
			public String name, uri, thumb, path;
			item(int type, String name, String uri, String thumb, String path) {
				this.type = type; this.name = name; this.uri = uri; this.thumb = thumb; this.path = path;
			}
		}
		
		class apiobj implements jumpyAPI {
			public ArrayList<item> items;
			public String basepath, path;
			apiobj(String p) {
				basepath = path = p; items = new ArrayList<item>();
			}
			public void addItem(int type, String name, String uri, String thumb) {
				items.add(new item(type, name, uri, thumb, path));
			}
			public void setPath(String dir) {
				path = (dir == null ? basepath : path + File.pathSeparator + dir);
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
				
		// get the current jar's location from a static context (isn't java lovely?)
		String home = new File(new jumpstart().getClass().getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();
		try{
		home = URLDecoder.decode(home, "UTF-8");
		} catch (Exception e) {}
		
//		apiobj obj = new apiobj(home + File.separatorChar + ".." + File.separatorChar + "src" + File.separatorChar + "lib");
		// jumpy.py is always located alongside the jar
		apiobj obj = new apiobj(home);
		String uri = script.getAbsolutePath();
		
		while (true) {
			obj.items.clear();
			py.run(obj, uri, obj.path);
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
			String sel = c.readLine("Choose an item or 'enter' to quit: ");
			if (sel.equals("q") || sel.equals("")) break;
			
			try {
				int s = Integer.parseInt(sel)-1;
				assert s > 0 && s < size;
				item i = obj.items.get(s);
				c.printf("--------------------------------\ntype : %d\nname : %s\nuri  : %s\nthumb: %s\n--------------------------------\n",
					i.type, i.name, i.uri, i.thumb);
				if (i.type > 0) break;
				
				uri = i.uri;
				obj.path = i.path;
			} catch (Exception e) {
				System.err.printf("Invalid selection: %s\n", sel);
				break;
			}
		}
	}
}

