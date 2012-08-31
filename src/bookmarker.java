package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.IOException;

import java.util.Map;
import java.util.List;
import java.util.Arrays;

import org.ini4j.Wini;
import org.ini4j.Profile.Section;

import net.pms.dlna.DLNAResource;


public class bookmarker {
	private jumpy jumpy;
	public scriptFolder bookmarks;
	public boolean verbose = true;
	private String bookmarksini;

	static List<String> temporal = Arrays.asList(new String[]{"pms", "PYTHONPATH", "JGATEWAY"});

	public bookmarker(jumpy jumpy) {
		this.jumpy = jumpy;
		bookmarks = new scriptFolder(jumpy, "Bookmarks", null, null);
		bookmarks.refreshAlways = true;
		jumpy.top.addChild(bookmarks);
		bookmarksini = jumpy.bookmarksini;
		verbose = jumpy.verboseBookmarks;
		load();
	}

	public void add(scriptFolder folder) {
		String name = folder.getName();
		scriptFolder bookmark = new scriptFolder(folder);
		bookmark.isBookmark = true;
		if (verbose) {
			name += (" :" + topName(folder));
			bookmark.setName(name);
		}
		bookmarks.addChild(bookmark);
		jumpy.log("Adding bookmark: " + name);
		store();
	}

	public void remove(scriptFolder folder) {
		String name = folder.getName();
		bookmarks.getChildren().remove(folder);
		jumpy.log("Removing bookmark: " + name);
		store();
	}

	public void load() {
		Wini ini = new Wini();
		ini.getConfig().setMultiSection(true);
		ini.setFile(new File(bookmarksini));
		boolean oldstyle = false;
		try {
			ini.load();
		} catch (IOException e) {} catch (Exception e) {e.printStackTrace();}
		for (Section section : ini.values()) {
			jumpy.log("Reading bookmark: " + section.getName());

			scriptFolder bookmark = new scriptFolder(jumpy, section.getName(), section);
			bookmark.isBookmark = true;
			bookmarks.addChild(bookmark);
		}
	}

	public void store() {
		try {
			Wini ini = new Wini();
			ini.getConfig().setMultiSection(true);
			ini.setFile(new File(bookmarksini));
			for (DLNAResource item : bookmarks.getChildren()) {
				scriptFolder bookmark = (scriptFolder)item;
				String name = bookmark.getName();
				Section section = ini.add(name);
				section.put("uri", bookmark.uri);
				section.put("thumbnail", bookmark.thumbnail);
				section.put("syspath", bookmark.syspath);
				if (bookmark.env != null && !bookmark.env.isEmpty()) {
					for (Map.Entry<String,String> var : bookmark.env.entrySet()) {
						String key = var.getKey();
						if (! temporal.contains(key)) {
							section.put(key, var.getValue());
						}
					}
				}
			}
			ini.store();
		} catch (IOException e) {} catch (Exception e) {e.printStackTrace();}
	}

	public String topName(scriptFolder folder) {
		return folder.getXMBPath(folder, jumpy.top).split("/")[0].replace("[xbmc]","").trim();
	}
}

