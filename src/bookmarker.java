package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.IOException;

import java.util.Map;

import org.ini4j.Wini;
import org.ini4j.Profile.Section;

import net.pms.dlna.DLNAResource;


public class bookmarker {
	private jumpy jumpy;
	public pyFolder bookmarks;
	public boolean verbose = true;
	private String bookmarksini;
	
	public bookmarker(jumpy jumpy) {
		this.jumpy = jumpy;
		bookmarks = new pyFolder(jumpy, "Bookmarks", null, null, jumpy.pypath);
		bookmarks.refreshAlways = true;
		jumpy.top.addChild(bookmarks);
		bookmarksini = jumpy.bookmarksini;
		verbose = jumpy.verboseBookmarks;
		load();
	}
	
	public void add(pyFolder folder) {
		String name = folder.getName();
		pyFolder bookmark = new pyFolder(folder);
		bookmark.isBookmark = true;
		if (verbose) {
			name += (" :" + topName(folder));
			bookmark.setName(name);
		}
		bookmarks.addChild(bookmark);
		jumpy.log("Added bookmark: " + name);
		store();
	}
	
	public void remove(pyFolder folder) {
		String name = folder.getName();
		bookmarks.getChildren().remove(folder);
		jumpy.log("Removed bookmark: " + name);
		store();
	}
   
	public void load() {
		Wini ini = new Wini();
		ini.getConfig().setMultiSection(true);
		ini.setFile(new File(bookmarksini));
   	try {
			ini.load();
		} catch (IOException e) {} catch (Exception e) {e.printStackTrace();}
		for (Section section : ini.values()) {
			jumpy.log("section: " + section.getName());
			pyFolder bookmark = new pyFolder(jumpy, section.getName(), section);
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
				pyFolder bookmark = (pyFolder)item;
				String name = bookmark.getName();
				Section section = ini.add(name);
				section.put("uri", bookmark.uri);
				section.put("thumbnail", bookmark.thumbnail);
				section.put("pypath", bookmark.pypath);
				if (bookmark.env != null && !bookmark.env.isEmpty()) {
					for (Map.Entry<String,String> var : bookmark.env.entrySet()) {
						section.put(var.getKey(), var.getValue());
					}
				}
			}
			ini.store();
		} catch (IOException e) {} catch (Exception e) {e.printStackTrace();}
   }
   
   public String topName(pyFolder folder) {
		return folder.getXMBPath(folder, jumpy.top).split("/")[0].replace("[xbmc]","").trim();
   }
}

