package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.IOException;

import java.util.Map;
import java.util.HashMap;

import net.pms.PMS;
import net.pms.util.PMSUtil;
import net.pms.dlna.DLNAResource;
import net.pms.dlna.virtual.VirtualFolder;
import net.pms.dlna.virtual.VirtualVideoAction;
import net.pms.dlna.VideosFeed;
import net.pms.dlna.AudiosFeed;
import net.pms.dlna.ImagesFeed;
import net.pms.formats.Format;
import net.pms.dlna.RealFile;
import net.pms.dlna.WebVideoStream;
import net.pms.dlna.WebAudioStream;


public class scriptFolder extends VirtualFolder implements jumpyAPI {
	public String uri, thumbnail;
	public String basepath = null, syspath = null;
	public Map<String,String> env;
	private jumpy jumpy;
	private runner ex;

	public boolean canBookmark = true;
	public boolean isBookmark = false;
	public boolean refreshOnce = true;
	public boolean refreshAlways = false;

	public scriptFolder(jumpy jumpy, String name, String uri, String thumbnailIcon) {
		this(jumpy, name, uri, thumbnailIcon, null, null);
	}

	public scriptFolder(jumpy jumpy, String name, String uri, String thumbnailIcon, String syspath) {
		this(jumpy, name, uri, thumbnailIcon, syspath, null);
	}

	public scriptFolder(scriptFolder other) {
		this(other.jumpy, other.name, other.uri, other.thumbnailIcon, other.syspath, other.env);
	}

	public scriptFolder(jumpy jumpy, String name, Map<String,String> m) {
		this(jumpy, name, m.remove("uri"), m.remove("thumbnail"), m.remove("syspath"), m);
	}

	public scriptFolder(jumpy jumpy, String name, String uri, String thumbnailIcon, String syspath, Map<String,String> env) {
		super(name, thumbnailIcon);
		this.jumpy = jumpy;
		this.thumbnail = thumbnailIcon;
		this.uri = uri;
		this.basepath = this.syspath = syspath;
		this.env = new HashMap<String,String>();
		if (env != null && !env.isEmpty()) {
			this.env.putAll(env);
		}
		this.ex = new runner();
		this.refreshAlways = (jumpy.refresh == 0);
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void discoverChildren() {
		if (uri == null || uri.equals("")) {
			return;
		}
		getChildren().clear();
		if (jumpy.showBookmarks && canBookmark) {
			final scriptFolder me = this;
			addChild(new VirtualVideoAction((isBookmark ? "Delete" : "Add") + " bookmark", true) {
				public boolean enable() {
					jumpy.bookmark(me);
					return true;
				}
			});
		}
		jumpy.log("%n");
		jumpy.log("Opening folder: " + name + ".%n");
		ex.run(this, uri, syspath, env);
		refreshOnce = false;
	}

	public void refresh() {
		refreshOnce = true;
	}

	@Override
	public void resolve() {
		discovered = !(refreshOnce || refreshAlways);
	}

	@Override
	public boolean isRefreshNeeded() {
		return true;
	}

	public static DLNAResource mkdirs(DLNAResource root, String path) {
		DLNAResource parent = root, child;
		boolean exists = true;
		for (String dir:path.split("/")) {
			if (exists && (child = parent.searchByName(dir)) != null) {
				parent = child;
			} else {
//				jumpy.log("adding folder: " + dir);
				parent.addChild(new VirtualFolder(dir, null));
//				parent.addChild(new scriptFolder(jumpy, dir, null));
				parent = parent.searchByName(dir);
				exists = false;
			}
		}
		return parent;
	}

	public static String getXMBPath(DLNAResource folder, DLNAResource ancestor) {
		DLNAResource p = folder;
		String xmbpath = "/";
		while (true) {
			p = p.getParent();
			if (p == null || p == ancestor) {
				break;
			}
			xmbpath = p.getName().trim() + "/" + xmbpath;
		}
		return (p == null ? "/" : "") + xmbpath.replace("//","").trim();
	}

	@Override
	public void addItem(int type, String name, String uri, String thumb) {

		// see if target is a local file
		File f = null;
		if (type > FOLDER) {
			f = new File(uri.startsWith("file://") ? uri.substring(7) : uri);
			if (! f.exists()) {
				f = null;
			}
		}

		String media = "unknown";

		switch (type) {
			case FOLDER:
				media = "folder";
				addChild(new scriptFolder(jumpy, name, uri, thumb, syspath, env));
				break;
			case UNRESOLVED:
				media = "unresolved item";
				addChild(new scriptFolder(jumpy, name, uri, thumb, syspath, env));
				break;
			case Format.VIDEO:
				media = "video";
				addChild(f == null ? new WebVideoStream(name, uri, thumb) : new RealFile(f, name));
				break;
			case Format.AUDIO:
				media = "audio";
				addChild(f == null ? new WebAudioStream(name, uri, thumb) : new RealFile(f, name));
				break;
			case Format.IMAGE:
				media = "image";
//				addChild(f == null ? new WebAudioStream(name, uri, thumb) : new RealFile(f, name));
				break;
			case Format.PLAYLIST:
				media = "playlist";
//				addChild(new WebAudioStream(name, uri, thumb));
				break;
			case Format.ISO:
				media = "iso";
//				addChild(new WebAudioStream(name, uri, thumb));
				break;
			case IMAGEFEED:
				media = "imagefeed";
				addChild(new ImagesFeed(uri));
				break;
			case VIDEOFEED:
				media = "videofeed";
				addChild(new VideosFeed(uri));
				break;
			case AUDIOFEED:
				media = "audiofeed";
				addChild(new AudiosFeed(uri));
				break;
			case Format.UNKNOWN:
			default:
				if (f != null ) {
					addChild(new RealFile(f, name));
				}
		}
		jumpy.log("Adding " + media +  ": " + name + ".");
	}

	@Override
	public void setPath(String dir) {
		syspath = (dir == null ? basepath : syspath + File.pathSeparator + dir);
	}

	@Override
	public void setEnv(String name, String val) {
		if (name == null && val == null ) {
			jumpy.log("setEnv: clear all.");
			env.clear();
		} else {
			jumpy.log("setEnv: " + name + "=" + val);
			env.put(name, val);
		}
	}

	@Override
	public String util(int action, String data) {

		jumpy.log("util: " + apiName[action] +  ", " + data);
		switch (action) {
			case VERSION:
				return ex.version;
			case HOME:
				return jumpy.home;
			case PROFILEDIR:
				return new File(jumpy.jumpyconf).getParent();
			case LOGDIR:
				return new File(jumpy.jumpylog).getParent();
			case PLUGINJAR:
				return this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
			case RESTART:
				try {
					ex.shutdown();
					PMS.get().reset();
				} catch(Exception e) {e.printStackTrace();}
				break;
			case FOLDERNAME:
				return this.getName();
		}
		return "";
	}
}
