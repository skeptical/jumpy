package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import org.apache.commons.io.FilenameUtils;

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
import net.pms.dlna.FeedItem;
import net.pms.dlna.WebVideoStream;
import net.pms.dlna.WebAudioStream;
import net.pms.dlna.PlaylistFolder;
import net.pms.dlna.DVDISOFile;


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
		this.ex = null;
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
		jumpy.log("\n");
		jumpy.log("Opening folder: " + name + ".\n");
		ex = new runner();
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
				parent.addChild(new VirtualFolder(dir, null));
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

	public Object addItem(int type, String filename, String uri, String thumb) {
		return addItem(type, filename, uri, thumb, null);
	}

	@Override
	public Object addItem(int type, String filename, String uri, String thumb, String data) {

		DLNAResource folder = this;

		String name = filename;
		if (filename.contains("/")) {
			name = FilenameUtils.getName(filename);
			String path = FilenameUtils.getPath(filename);
			if (path != null) {
				folder = mkdirs(FilenameUtils.getPrefixLength(filename) == 0 ? this : jumpy.top, path);
			}
		}

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
				folder.addChild(new scriptFolder(jumpy, name, uri, thumb, syspath, env));
				break;
			case UNRESOLVED:
				media = "unresolved item";
				folder.addChild(new scriptFolder(jumpy, name, uri, thumb, syspath, env));
				break;
			case ACTION:
				data = (data == null ? "" : data);
				media = data + " action";
				folder.addChild(new mediaItem(name, "jump:" + data, uri, thumb));
				break;
			case MEDIA:
				media = data + " item";
				folder.addChild(new mediaItem(name, data, uri, thumb));
				break;
			case Format.VIDEO:
				media = "video";
				folder.addChild(f == null ? new WebVideoStream(name, uri, thumb) : new RealFile(f, name));
				break;
			case Format.AUDIO:
				media = "audio";
				folder.addChild(f == null ? new WebAudioStream(name, uri, thumb) : new RealFile(f, name));
				break;
			case Format.IMAGE:
				media = "image";
				folder.addChild(f == null ? new FeedItem(name, uri, thumb, null, Format.IMAGE) : new RealFile(f, name));
				break;
			case Format.PLAYLIST:
				media = "playlist";
				if (f != null ) {
					folder.addChild(new PlaylistFolder(f));
				}
				break;
			case Format.ISO:
				media = "iso";
				if (f != null ) {
					folder.addChild(new DVDISOFile(f));
				}
				break;
			case IMAGEFEED:
				media = "imagefeed";
				folder.addChild(new ImagesFeed(uri));
				break;
			case VIDEOFEED:
				media = "videofeed";
				folder.addChild(new VideosFeed(uri));
				break;
			case AUDIOFEED:
				media = "audiofeed";
				folder.addChild(new AudiosFeed(uri));
				break;
			case Format.UNKNOWN:
			default:
				if (f != null ) {
					folder.addChild(new RealFile(f, name));
				}
		}
		jumpy.log("Adding " + media +  ": " + filename + ".");
		return (ex != null && ex.running) ? null : folder.getChildren().get(folder.getChildren().size()-1);
	}

	@Override
	public void addPath(String path) {
		syspath = (path == null ? basepath : syspath + File.pathSeparator + path);
	}

	@Override
	public void setEnv(String name, String val) {
		if (name == null) {
			jumpy.log("setEnv: clear all.");
			env.clear();
		} else {
			jumpy.log("setEnv: " + name + "=" + val);
			env.put(name, val);
		}
	}

	@Override
	public String util(int action, String arg1, String arg2) {

		jumpy.log("util: " + apiName[action] + (arg1 == null ? "" : " " + arg1) + (arg2 == null ? "" : " " + arg2));
		switch (action) {
			case VERSION:
				return jumpy.version;
			case HOME:
				return jumpy.home;
			case PROFILEDIR:
				return new File(jumpy.jumpyconf).getParent();
			case LOGDIR:
				return new File(jumpy.jumpylog).getParent();
			case PLUGINJAR:
				try {
					return this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI().normalize().getPath();
				} catch(Exception e) {break;}
			case RESTART:
				try {
					ex.shutdown();
					PMS.get().reset();
				} catch(Exception e) {e.printStackTrace();}
				break;
			case REBOOT:
				try {
					ex.shutdown();
				} catch(Exception e) {e.printStackTrace();}
				utils.restart();
				break;
			case FOLDERNAME:
				return this.getName();
			case GETPROPERTY:
				Object obj = PMS.get().getConfiguration().getCustomProperty(arg1);
				// return last occurrence
				return (String)(obj instanceof ArrayList ? (((ArrayList)obj).get(((ArrayList)obj).size()-1)) : obj);
			case SETPROPERTY:
				PMS.get().getConfiguration().setCustomProperty(arg1, arg2);
				break;
			case SETPMS:
				command.pms = arg1;
				break;
		}
		return "";
	}

	@Override
	public int addPlayer(String name, String cmd, String supported, int mediatype, int purpose, String desc, String playback) {
		return jumpy.addPlayer(name, cmd, supported, mediatype, purpose, desc, playback);
	}
}

