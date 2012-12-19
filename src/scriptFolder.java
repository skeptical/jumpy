package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringEscapeUtils;

import net.pms.PMS;
import net.pms.util.PMSUtil;
import net.pms.dlna.DLNAResource;
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


public class scriptFolder extends xmbObject implements jumpyAPI {
	public String uri, basepath = null, syspath = null;
	public Map<String,String> env;
	private jumpy jumpy;
	private runner ex;

	public boolean canBookmark = true;
	public boolean isBookmark = false;
	public boolean refreshOnce = true;
	public boolean refreshAlways = false;

	public scriptFolder(jumpy jumpy, String name, String uri, String thumb) {
		this(jumpy, name, uri, thumb, null, null);
	}

	public scriptFolder(jumpy jumpy, String name, String uri, String thumb, String syspath) {
		this(jumpy, name, uri, thumb, syspath, null);
	}

	public scriptFolder(scriptFolder other) {
		this(other.jumpy, other.name, other.uri, other.thumbnail, other.syspath, other.env);
	}

	public scriptFolder(jumpy jumpy, String name, Map<String,String> m) {
		this(jumpy, name, m.remove("uri"), m.remove("thumbnail"), m.remove("syspath"), m);
	}

	public scriptFolder(jumpy jumpy, String name, String uri, String thumb, String syspath, Map<String,String> env) {
		super(name, thumb);
		this.jumpy = jumpy;
		this.uri = uri;
		this.basepath = this.syspath = syspath;
		this.env = new HashMap<String,String>();
		if (env != null && !env.isEmpty()) {
			this.env.putAll(env);
		}
		this.ex = null;
		this.refreshAlways = (jumpy.refresh == 0);
	}

	@Override
	public boolean isFolder() {
		return true;
	}

	@Override
	public long length() {
		return 0;
	}

	@Override
	public void discoverChildren() {
		if (uri == null || uri.equals("")) {
			return;
		}
		List<DLNAResource> children = getChildren();
		children.clear();
		jumpy.log("\n");
		jumpy.log("Opening folder: " + name + ".\n");
		ex = new runner();
		ex.run(this, uri, syspath, env);
		if (jumpy.showBookmarks && canBookmark && (children.size() > 0 || isBookmark)) {
			final scriptFolder self = this;
			addChild(new xmbAction((isBookmark ? "Delete" : "Add") + " bookmark",
					"jump+CMD : Bookmark " + (isBookmark ? "deleted" : "added") + " :  ", null,
					isBookmark ? "#x" : "#plus") {
				public int run(scriptFolder folder, command cmdline) {
					jumpy.bookmark(self);
					return 0;
				}
			});
			// put the action first (note: this breaks id=index correspondence in the children)
			children.add(0, children.remove(children.size() - 1));
		}
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

	public Object addItem(int type, String filename, String uri, String thumbnail) {
		return addItem(type, filename, uri, thumbnail, null);
	}

	@Override
	public Object addItem(int type, String filename, String uri, String thumbnail, String data) {

		String path = StringEscapeUtils.unescapeXml(StringEscapeUtils.unescapeHtml(filename));
		String label = FilenameUtils.getName(path);
		path = FilenameUtils.getFullPath(path);
		DLNAResource folder = path == null ? this : utils.mkdirs(path, this);

		String thumb = jumpy.getResource(thumbnail);

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
				folder.addChild(new scriptFolder(jumpy, label, uri, thumb, syspath, env));
				break;
			case UNRESOLVED:
				media = "unresolved item";
				folder.addChild(new scriptFolder(jumpy, label, uri, thumb, syspath, env));
				break;
			case ACTION:
				data = (data == null ? "" : data);
				media = data + " action";
				folder.addChild(new xmbAction(label, "jump:" + data, uri, thumb));
				break;
			case MEDIA:
				media = data + " item";
				folder.addChild(new mediaItem(label, data, uri, thumb));
				break;
			case Format.VIDEO:
				media = "video";
				folder.addChild(f == null ? new WebVideoStream(label, uri, thumb) : new RealFile(f, label));
				break;
			case Format.AUDIO:
				media = "audio";
				folder.addChild(f == null ? new WebAudioStream(label, uri, thumb) : new RealFile(f, label));
				break;
			case Format.IMAGE:
				media = "image";
				folder.addChild(f == null ? new FeedItem(label, uri, thumb, null, Format.IMAGE) : new RealFile(f, label));
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
					folder.addChild(new RealFile(f, label));
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
		if (name == null || name.isEmpty()) {
			jumpy.log("setEnv: clear all.");
			env.clear();
		} else if (val == null || val.isEmpty()) {
			jumpy.log("setEnv: remove '" + name + "'");
			env.remove(name);
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
			case XMBPATH:
				return ("/" + utils.getXMBPath(this, jumpy.top.getParent()) + "/" + this.name).replace("//", "/");
			case GETPROPERTY:
				Object obj = PMS.get().getConfiguration().getCustomProperty(arg1);
				// return last occurrence
				return (String)(obj instanceof ArrayList ? (((ArrayList)obj).get(((ArrayList)obj).size()-1)) : obj);
			case SETPROPERTY:
				PMS.get().getConfiguration().setCustomProperty(arg1, arg2);
				break;
			case ICON:
				jumpy.setIcon(arg1, arg2);
				break;
			case RESOURCE:
				return jumpy.getResource(arg1);
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

