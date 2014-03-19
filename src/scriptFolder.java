package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringEscapeUtils;

import com.google.gson.Gson;

import net.pms.PMS;
import net.pms.dlna.DLNAResource;
import net.pms.dlna.DLNAMediaInfo;
import net.pms.dlna.DLNAMediaAudio;
import net.pms.dlna.DLNAMediaSubtitle;
import net.pms.dlna.VideosFeed;
import net.pms.dlna.AudiosFeed;
import net.pms.dlna.ImagesFeed;
import net.pms.dlna.RealFile;
import net.pms.dlna.FeedItem;
import net.pms.dlna.WebVideoStream;
import net.pms.dlna.WebAudioStream;
import net.pms.dlna.PlaylistFolder;
import net.pms.dlna.DVDISOFile;
import net.pms.formats.Format;
import net.pms.formats.FormatFactory;


public class scriptFolder extends xmbObject implements jumpyAPI {
	public String uri, basepath = null, syspath = null;
	public Map<String,String> env;
	private jumpy jumpy;
	private runner ex;
	public DLNAResource newItem;

	public boolean canBookmark = true;
	public boolean isBookmark = false;
	public boolean refreshOnce = true;
	public boolean refreshAlways = false;
	private int lastcount;

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
		this.newItem = null;
		this.refreshAlways = (jumpy.refresh == 0);
		this.lastmodified = 0;
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
		lastcount = 0;
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
		lastmodified = 0;
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
		boolean isneeded = (lastmodified != 0);
		lastmodified = 0;
		return isneeded;
	}

	@Override
	public boolean analyzeChildren(int count) {
		int size = getChildren().size();
		boolean ready = (ex != null && ex.running) ?
			count == -1 ? false : (size - lastcount >= count) : true;
		lastcount = size;
		return ready;
	}

	public Object addItem(int type, String filename, String uri, String thumbnail) {
		return addItem(type, filename, uri, thumbnail, null, null);
	}

	public Object addItem(int type, String filename, String uri, String thumbnail, Map mediainfo) {
		return addItem(type, filename, uri, thumbnail, mediainfo, null);
	}

	@Override
	public Object addItem(int type, String filename, String uri, String thumbnail, Map mediainfo, String data) {

		if (filename == null || uri == null) {
			return null;
		}

		DLNAMediaInfo mediaInfo = mediainfo != null ? getMediaInfo(mediainfo) : null;
		if (mediaInfo != null) {
			jumpy.log("mediaInfo: " + mediaInfo);
		}

		filename = StringEscapeUtils.unescapeXml(StringEscapeUtils.unescapeHtml(filename));
		String label = utils.unesc(FilenameUtils.getName(filename));
		String path = FilenameUtils.getFullPath(filename);
		DLNAResource folder = path == null ? this : utils.mkdirs(path, this);
		String thumb = jumpy.getResource(thumbnail);

		// see if target is a local file
		File f = null;
		if (type < FOLDER) {
			f = new File(uri.startsWith("file://") ? uri.substring(7) : uri);
			if (! f.exists()) {
				f = null;
			}
		}

		if (type == MEDIA && data == null) {
			Format format = FormatFactory.getAssociatedExtension("." + FilenameUtils.getExtension(uri));
			if (format == null) {
				return null;
			}
			int ftype = format.getType();
			if (ftype != Format.UNKNOWN) {
				type = ftype;
			}
		}

		int utype = 0;
		if ((type & UNRESOLVED) == UNRESOLVED) {
			utype = type ^ UNRESOLVED;
			type = UNRESOLVED;
		}

		String media = "unknown";
		newItem = null;

		switch (type) {
			case FOLDER:
				media = "folder";
				newItem = new scriptFolder(jumpy, label, uri, thumb, syspath, env);
				break;
			case BOOKMARK:
				media = "bookmark";
				jumpy.bookmarks.add(new scriptFolder(jumpy, filename, uri, thumb, syspath, env), false, true);
				break;
			case UNRESOLVED:
				media = "unresolved." + utype + " item";
				if (resolver.playback) {
					newItem = new resolver(jumpy, utype, label, uri, thumb, syspath, env);
				} else {
					newItem = new scriptFolder(jumpy, label, uri, thumb, syspath, env);
				}
				break;
			case ACTION:
				data = (data == null ? "" : data);
				media = data + " action";
				newItem = new xmbAction(label, "jump:" + data, uri, thumb, syspath, env);
				break;
			case MEDIA:
				mediaItem item = new mediaItem(label, data, uri, thumb);
				media = item.fmt + " item";
				newItem = item;
				break;
			case Format.VIDEO:
				media = (f == null ? "web " : "") + "video";
				newItem = (f == null ?
					new WebVideoStream(label, uri, thumb) {
						public boolean isURLResolved() {return true;}
					}
					: new RealFile(f, label));
				break;
			case Format.AUDIO:
				media = (f == null ? "web " : "") + "audio";
				newItem = (f == null ? new WebAudioStream(label, uri, thumb) : new RealFile(f, label));
				break;
			case Format.IMAGE:
				media = (f == null ? "web " : "") + "image";
				newItem = (f == null ? new FeedItem(label, uri, thumb, null, Format.IMAGE) : new RealFile(f, label));
				break;
			case Format.PLAYLIST:
				media = "playlist";
				if (f != null ) {
					newItem = new PlaylistFolder(f);
				}
				break;
			case Format.ISO:
				media = "iso";
				if (f != null ) {
					newItem = new DVDISOFile(f);
				}
				break;
			case IMAGEFEED:
				media = "imagefeed";
				newItem = new ImagesFeed(uri);
				break;
			case VIDEOFEED:
				media = "videofeed";
				newItem = new VideosFeed(uri);
				break;
			case AUDIOFEED:
				media = "audiofeed";
				newItem = new AudiosFeed(uri);
				break;
			case Format.UNKNOWN:
			default:
				if (f != null ) {
					newItem = new RealFile(f, label);
				}
		}
		if (newItem != null) {
			if (newItem instanceof xmbObject) {
				((xmbObject)newItem).tag = this.tag;
			}
			if (mediaInfo != null) {
				utils.setField(newItem, "media", mediaInfo);
			}
			folder.addChild(newItem);
			utils.touch(folder);
			jumpy.log("Adding " + media +  ": " + filename + ".");
		}
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

		if (action != LOG) {
			jumpy.log(apiName[action] + (arg1 == null ? "" : " " + arg1) + (arg2 == null ? "" : " " + arg2));
		}
		switch (action) {
			case VERSION:
				return jumpy.version;
			case HOME:
				return jumpy.home;
			case HOST_IP:
				return PMS.get().getServer().getHost();
			case PROFILEDIR:
				return new File(jumpy.jumpyconf).getParent();
			case LOGDIR:
				return new File(jumpy.jumpylog).getParent();
			case PLUGINJAR:
				try {
					return this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI().normalize().getPath();
				} catch(Exception e) {}
				break;
			case REFRESH:
				int level = Integer.parseInt(arg1);
				DLNAResource folder = this;
				while (folder != null && level-- > -1) {
					jumpy.log("refresh: " + folder.getName());
					if (folder instanceof scriptFolder) {
						((scriptFolder)folder).refresh();
					}
					folder.resolve();
					folder = folder.getParent();
				}
				break;
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
			case RUN:
				return Integer.toString(new runner(arg2, 0).run(this, arg1, syspath, env));
			case FOLDERNAME:
				return this.getName();
			case XMBPATH:
				return ("/" + utils.getXMBPath(this, jumpy.top.getParent()) + "/" + utils.unesc(this.name)).replace("//", "/");
			case MKDIRS:
				utils.mkdirs(arg1, this, arg2);
				break;
			case GETVAR:
				if (utils.properties.containsKey(arg1)) {
					return utils.properties.get(arg1);
				}
				break;
			case SETVAR:
				utils.properties.put(arg1, arg2);
				break;
			case GETPROPERTY:
				return utils.getCustomProperty(arg1, arg2);
			case SETPROPERTY:
				PMS.get().getConfiguration().setCustomProperty(arg1, arg2);
				break;
			case ICON:
				jumpy.setIcon(arg1, arg2);
				break;
			case INFO:
				Object section = tag;
				boolean defer = false;
				if (arg2 != null) {
					if (! userscripts.meta.containsKey(arg2)) {
						userscripts.meta.add(arg2);
					}
					section = userscripts.meta.get(arg2);
					defer = true;
				}
				userscripts.hideNonEdit(section);
				HashMap<String,List<String>> args = new HashMap();
				args = (HashMap<String,List<String>>) new Gson().fromJson(arg1, args.getClass());
//				jumpy.log(args.toString(), true);
				for (String key : args.keySet()) {
					if (defer && ((Map)section).containsKey(key)) {
						continue;
					}
					List<String> s = args.get(key);
					String val = s.get(0);
					String comment = s.size() > 1 ? s.get(1) : null;
					jumpy.userscripts._put(section, key, val, comment);
				}
				return new Gson().toJson((Map)section);
			case RESOURCE:
				return jumpy.getResource(arg1);
			case SETPMS:
				command.pms = arg1;
				break;
			case SUBTITLE:
				if (newItem != null) {
					DLNAResource parent = newItem.getParent();
					parent.getChildren().remove(newItem);
					utils.setMediaSubtitle(newItem, arg1, null);
					parent.addChild(newItem);
				}
				break;
			case LOG:
				boolean minimal = arg2.contains("v");
				if (arg2.contains("o")) {
					jumpy.logonce(arg1, arg1, minimal);
				} else {
					jumpy.log(arg1, minimal);
				}
				break;
		}
		return "";
	}

	@Override
	public int addPlayer(String name, String cmd, String supported, int mediatype, int purpose, String desc, String icon, String playback) {
		return jumpy.addPlayer(name, cmd, supported, mediatype, purpose, desc, icon, playback);
	}

	protected CountDownLatch ready = null;

	@Override
	public void register(Object obj) {
		if (obj == null) {
			try {
				ready = new CountDownLatch(1);
				ready.await(2000, TimeUnit.MILLISECONDS);
			} catch (Exception e) {e.printStackTrace();}
		} else if (ready != null) {
			ready.countDown();
			jumpy.log("\n");
		}
		ready = null;
	}

	public static DLNAMediaInfo getMediaInfo(Map mediainfo) {
		DLNAMediaInfo m = new DLNAMediaInfo();
		ArrayList<DLNAMediaAudio> audio = new ArrayList<DLNAMediaAudio>();
		ArrayList<DLNAMediaSubtitle> subs = new ArrayList<DLNAMediaSubtitle>();

		if (mediainfo.containsKey("duration")) {
			m.setDuration(utils.duration((String)mediainfo.get("duration")));
		} else if (mediainfo.containsKey("streams")) {
			for (Map<String,String> s : (List<Map<String,String>>)mediainfo.get("streams")) {
				try {
					String type = s.get("type").toLowerCase();
					if (type.equals("video")) {
						if (s.containsKey("codec")) {
							m.setCodecV(s.get("codec"));
						}
						if (s.containsKey("aspect")) {
							m.setAspect(s.get("aspect"));
						}
						if (s.containsKey("width")) {
							m.setWidth(Integer.parseInt(s.get("width")));
						}
						if (s.containsKey("height")) {
							m.setHeight(Integer.parseInt(s.get("height")));
						}
						if (s.containsKey("duration")) {
							m.setDuration(utils.duration(s.get("duration")));
						}
					} else if (type.equals("audio")) {
						DLNAMediaAudio a = new DLNAMediaAudio();
						if (s.containsKey("codec")) {
							a.setCodecA(s.get("codec"));
						}
						if (s.containsKey("language")) {
							a.setLang(s.get("language"));
						}
						if (s.containsKey("channels")) {
							a.getAudioProperties().setNumberOfChannels(Integer.parseInt(s.get("channels")));
						}
						audio.add(a);
					} else if (type.equals("subtitle")) {
						DLNAMediaSubtitle t = new DLNAMediaSubtitle();
						if (s.containsKey("language")) {
							t.setLang(s.get("language"));
						}
						subs.add(t);
					}
				} catch (Exception e) {
					net.pms.external.infidel.jumpy.jumpy.log("Error reading media info: " + e);
				}
			}
		}
		m.setAudioTracksList(audio);
		m.setSubtitleTracksList(subs);
		m.setMediaparsed(true);
		return m;
	}

	@Override
	public Object getTag() {
		return tag;
	}
}

