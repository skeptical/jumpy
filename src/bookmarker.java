package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.IOException;

import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

import org.apache.commons.io.FilenameUtils;

import org.ini4j.Wini;
import org.ini4j.Profile.Section;

import net.pms.dlna.DLNAResource;


public class bookmarker {
	private jumpy jumpy;
	public scriptFolder Bookmarks;
	public List<scriptFolder> bookmarks;
	public boolean verbose = true;
	private String bookmarksini;

	static List<String> temporal = Arrays.asList(new String[]{"pms", "PYTHONPATH", "JGATEWAY", "JCLIENT"});

	public bookmarker(jumpy jumpy) {
		this.jumpy = jumpy;
		Bookmarks = new scriptFolder(jumpy, "Bookmarks", null, "#book");
		Bookmarks.refreshAlways = true;
		jumpy.top.addChild(Bookmarks);
		bookmarks = new ArrayList<scriptFolder>();
		bookmarksini = jumpy.bookmarksini;
		verbose = jumpy.verboseBookmarks;
		load();
	}

	public void add(scriptFolder folder) {
		add(folder, true, true);
	}

	public void add(scriptFolder folder, boolean copy, boolean save) {
		String path = folder.getName();
		String label = utils.unesc(FilenameUtils.getName(path));
		path = FilenameUtils.getFullPath(path);
		DLNAResource parent = path == null ? Bookmarks : utils.mkdirs(path, Bookmarks);

		scriptFolder bookmark = copy ? new scriptFolder(folder) : folder;
		bookmark.isBookmark = true;
		if (parent == Bookmarks && copy && verbose) {
			label += (" :" + topName(folder));
		}
		bookmark.setName(label);
		parent.addChild(bookmark);
		bookmarks.add(bookmark);
		jumpy.log("Adding bookmark: " + label);
		if (save) {
			store();
		}
	}

	public void remove(scriptFolder folder) {
		String name = folder.getName();
		folder.getParent().getChildren().remove(folder);
		bookmarks.remove(folder);
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
			add(new scriptFolder(jumpy, section.getName(), section), false, false);
		}
	}

	public void store() {
		try {
			Wini ini = new Wini();
			ini.getConfig().setMultiSection(true);
			ini.setFile(new File(bookmarksini));
			for (scriptFolder bookmark : bookmarks) {
				String name =
					(bookmark.getParent() == Bookmarks ? "" : (utils.getXMBPath(bookmark, null) + "/")) +
					utils.esc(bookmark.getName());
				Section section = ini.add(name);
				section.put("uri", bookmark.uri);
				if (! (bookmark.thumbnail == null || bookmark.thumbnail.isEmpty()))
					section.put("thumbnail", bookmark.thumbnail);
				if (! (bookmark.syspath == null || bookmark.syspath.isEmpty()))
					section.put("syspath", bookmark.syspath);
				if (bookmark.env != null && !bookmark.env.isEmpty()) {
					for (Map.Entry<String,String> var : bookmark.env.entrySet()) {
						String key = var.getKey();
						String val = var.getValue();
						if (! temporal.contains(key) && ! (val == null || val.isEmpty())) {
							section.put(key, var.getValue());
						}
					}
				}
			}
			ini.store();
		} catch (IOException e) {} catch (Exception e) {e.printStackTrace();}
	}

	public String topName(scriptFolder folder) {
		String[] dirs = utils.getXMBPath(folder, jumpy.top.getParent()).split("/");
		return dirs[dirs.length > 2 ? 2 : 1].replace("[xbmc]","").trim();
	}
}

