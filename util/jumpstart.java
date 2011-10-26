// javac -d classes -cp .:\* xbmc.java && if [ $?=0 ] ; then java -cp classes:\* xbmc ; fi
// javac -d classes -cp .:./path/py4j0.7.jar:\* src/xbmc.java
// java -cp /f/git/pms-plugins/xbmc_plugin/classes:/f/git/pms-plugins/xbmc_plugin/path/py4j0.7.jar xbmc 0 ?
//package net.pms.external.infidel;

import java.io.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.io.Console;

import net.pms.external.infidel.py;
import net.pms.external.infidel.util.jumpyAPI;

public class jumpstart {
	public static void main(String[] argv) {
		
		class item {
			public int type;
			public String name, cmd, thumb, path;
			item(int type, String name, String cmd, String thumb, String path) {
				this.type = type; this.name = name; this.cmd = cmd; this.thumb = thumb; this.path = path;
			}
		}
		
		class apiobj implements jumpyAPI {
			public ArrayList<item> items;
			public String basepath, path;
			apiobj(String p) {
				basepath = path = p; items = new ArrayList<item>();
			}
			public void addItem(int type, String name, String url, String thumb) {
				items.add(new item(type, name, url, thumb, path));
			}
			public void setPath(String dir) {
				path = (dir == null || dir.equals("")) ? basepath : path + File.pathSeparator + dir;
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
		
		apiobj obj = new apiobj(home + File.separatorChar + ".." + File.separatorChar + "src" + File.separatorChar + "lib");
		String cmd = script.getAbsolutePath();
		py.path = obj.path;
		
		while (true) {
			obj.items.clear();
			py.run(cmd, obj);
			int size = obj.items.size();
			if (size == 0) break;
			
			c.printf("\n------------- MENU -------------\n");
			for (int i=0; i<size; i++) {
				item x = obj.items.get(i);
				String type = "";
				switch (x.type) {
					case -1: type = " (UNRESOLVED)"; break;
					case  0: type = ""; break;
					case  1: type = " (AUDIO)"; break;
					case  2: type = " (IMAGE)"; break;
					case  4: type = " (VIDEO)"; break;
					case  8: type = " (UNKNOWN)"; break;
					case 16: type = " (PLAYLIST)"; break;
					case 32: type = " (ISO)"; break;
				}
				c.printf("  [%d] %s%s\n", i+1, x.name, type);
			}
			String sel = c.readLine("Choose an item or 'enter' to quit: ");
			if (sel.equals("q") || sel.equals("")) break;
			
			try {
				int s = Integer.parseInt(sel)-1;
				assert s > 0 && s < size;
				item i = obj.items.get(s);
				c.printf("--------------------------------\ntype : %d\nname : %s\ncmd  : %s\nthumb: %s\n--------------------------------\n",
					i.type, i.name, i.cmd, i.thumb);
			
				cmd = i.cmd;
				py.path = obj.path = i.path;
			} catch (Exception e) {
				System.err.printf("Invalid selection: %s\n", sel);
				break;
			}
		}
	}
}

