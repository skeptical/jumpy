package net.pms.external.infidel.jumpy;

import com.google.gson.Gson;
import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.pms.configuration.RendererConfiguration;
import net.pms.dlna.AudiosFeed;
import net.pms.dlna.DLNAMediaAudio;
import net.pms.dlna.DLNAMediaInfo;
import net.pms.dlna.DLNAMediaSubtitle;
import net.pms.dlna.DLNAResource;
import net.pms.dlna.DVDISOFile;
import net.pms.dlna.FeedItem;
import net.pms.dlna.ImagesFeed;
import net.pms.dlna.PlaylistFolder;
import net.pms.dlna.RealFile;
import net.pms.dlna.RootFolder;
import net.pms.dlna.VideosFeed;
import net.pms.dlna.WebAudioStream;
import net.pms.dlna.WebVideoStream;
import net.pms.formats.Format;
import net.pms.formats.FormatFactory;
import net.pms.network.UPNPHelper;
import net.pms.PMS;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringEscapeUtils;
import static net.pms.external.infidel.jumpy.jumpyAPI.*;

public final class xmb {

	public static DLNAResource fakeroot = new xmbObject("fakeroot", null, true);
	public static DLNAResource home = null;
	static ArrayList<RendererConfiguration> foundRenderers = null;
	public static boolean startup = true;

	public static DLNAResource pwd(xmbObject obj) {
		return obj.isFolder ? obj : obj.getParent() == null ? jumpy.top : obj.getParent();
	}

	public static String getPath(DLNAResource folder, DLNAResource ancestor) {
		DLNAResource p = folder;
		String xmbpath = "/";
		boolean root;
		while (true) {
			p = p.getParent();
			root = (p == null || p == fakeroot || p instanceof net.pms.dlna.RootFolder);
			if (root || p == ancestor) {
				break;
			}
			xmbpath = esc(p.getName().trim()) + "/" + xmbpath;
		}
		return (root ? "/" : "") + xmbpath.replace("//","").trim();
	}

	public static void touch(DLNAResource folder) {
		try {
			((xmbObject)folder).touch();
		} catch (Exception e) {
			utils.setField(folder, "lastmodified", 1);
		}
	}

	public static DLNAResource mkdirs(String path, DLNAResource pwd) {
		return mkdirs(path, pwd, null);
	}

	public static DLNAResource mkdirs(String path, DLNAResource pwd, String thumb) {
		boolean exists = true, atroot=path.startsWith("/"), rootchanged=false;
		DLNAResource child, parent = atroot ? fakeroot : path.startsWith("~/") ? home : pwd;
		String[] dir = path.split("/");
		Object tag = pwd instanceof xmbObject ? ((xmbObject)pwd).tag : null;
		int i;
		for (i=0; i<dir.length; i++) {
			dir[i] = unesc(dir[i]);
			if (dir[i].equals("") || dir[i].equals(".") || dir[i].equals("~")) continue;
			if (! (exists && (child = parent.searchByName(dir[i])) != null)) {
				if (atroot) {
					rootchanged = true;
					atroot = false;
				}
				child = new xmbObject(dir[i], i == dir.length-1 ? thumb : null, true);
				((xmbObject)child).tag = tag;
				parent.addChild(child);
				exists = false;
			}
			parent = child;
		}
		if (! startup && (rootchanged || atroot)) {
			refresh();
		}
		return parent;
	}

	public static void refresh() {
		if (foundRenderers == null) {
			foundRenderers = (ArrayList<RendererConfiguration>)
				utils.getField(PMS.get(), "foundRenderers");
		}
		for (RendererConfiguration r : foundRenderers) {
			RootFolder root = r.getRootFolder();
			root.getChildren().clear();
			utils.setField(root, "discovered", false);
		}
	}

	public static String esc(String name) {
		return name.replace("/", "^|");
	}

	public static String unesc(String name) {
		return name.replace("^|", "/");
	}

	public static void remove(DLNAResource obj) {
		DLNAResource parent = obj.getParent();
		parent.getChildren().remove(obj);
		touch(parent);
	}

	public static void add(DLNAResource parent, DLNAResource child) {
		parent.addChild(child);
		touch(parent);
	}

	public static Object add(xmbObject obj, int type, String filename, String uri, String thumbnail, Map mediainfo, String data) {
		if (filename == null || uri == null) {
			return null;
		}

		jumpy jumpy = obj.jumpy;

		DLNAMediaInfo mediaInfo = mediainfo != null ? getMediaInfo(mediainfo) : null;
		if (mediaInfo != null) {
			jumpy.log("mediaInfo: " + mediaInfo);
		}

		filename = StringEscapeUtils.unescapeXml(StringEscapeUtils.unescapeHtml(filename));
		String label = unesc(FilenameUtils.getName(filename));
		String path = FilenameUtils.getFullPath(filename);
		DLNAResource pwd = path == null ? pwd(obj) : mkdirs(path, obj);
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
			Format format = FormatFactory.getAssociatedFormat("." + FilenameUtils.getExtension(uri));
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
		obj.newItem = null;

		switch (type) {
			case FOLDER:
				media = "folder";
				obj.newItem = new scriptFolder(jumpy, label, uri, thumb, obj.syspath, obj.env);
				break;
			case BOOKMARK:
				media = "bookmark";
				jumpy.bookmarks.add(new scriptFolder(jumpy, filename, uri, thumb, obj.syspath, obj.env), false, true);
				break;
			case UNRESOLVED:
				media = "unresolved." + utype + " item";
				if (resolver.playback && utype != FOLDER) {
					obj.newItem = new resolver(jumpy, utype, label, uri, thumb, obj.syspath, obj.env);
				} else {
					obj.newItem = new scriptFolder(jumpy, label, uri, thumb, obj.syspath, obj.env);
				}
				break;
			case ACTION:
				data = (data == null ? "" : data);
				media = data + " action";
				obj.newItem = new xmbAction(label, "jump:" + data, uri, thumb, obj.syspath, obj.env);
				break;
			case MEDIA:
				mediaItem item = new mediaItem(label, data, uri, thumb);
				media = item.fmt + " item";
				obj.newItem = item;
				break;
			case Format.VIDEO:
				media = (f == null ? "web " : "") + "video";
				obj.newItem = (f == null ?
					new WebVideoStream(label, uri, thumb) {
						public boolean isURLResolved() {return true;}
					}
					: new RealFile(f, label));
				break;
			case Format.AUDIO:
				media = (f == null ? "web " : "") + "audio";
				obj.newItem = (f == null ? new WebAudioStream(label, uri, thumb) : new RealFile(f, label));
				break;
			case Format.IMAGE:
				media = (f == null ? "web " : "") + "image";
				obj.newItem = (f == null ? new FeedItem(label, uri, thumb, null, Format.IMAGE) : new RealFile(f, label));
				break;
			case Format.PLAYLIST:
				media = "playlist";
				if (f != null ) {
					obj.newItem = new PlaylistFolder(f);
				}
				break;
			case Format.ISO:
				media = "iso";
				if (f != null ) {
					obj.newItem = new DVDISOFile(f);
				}
				break;
			case IMAGEFEED:
				media = "imagefeed";
				obj.newItem = new ImagesFeed(uri);
				break;
			case VIDEOFEED:
				media = "videofeed";
				obj.newItem = new VideosFeed(uri);
				break;
			case AUDIOFEED:
				media = "audiofeed";
				obj.newItem = new AudiosFeed(uri);
				break;
			case Format.UNKNOWN:
			default:
				if (f != null ) {
					obj.newItem = new RealFile(f, label);
				}
		}
		if (obj.newItem != null) {
			if (obj.newItem instanceof xmbObject) {
				((xmbObject)obj.newItem).tag = obj.tag;
			}
			if (mediaInfo != null) {
				utils.setField(obj.newItem, "media", mediaInfo);
			}
			pwd.addChild(obj.newItem);
			touch(pwd);
			jumpy.log("Adding " + media +  ": " + filename + ".");
		}
		return (obj.ex != null && obj.ex.running) ? null : pwd.getChildren().get(pwd.getChildren().size()-1);
	}

	public static String util(xmbObject obj, int action, final String arg1, final String arg2) {

		final jumpy jumpy = obj.jumpy;
		String result = null;

		switch (action) {
			case VERSION:
				result = jumpy.version;
				break;
			case HOME:
				result = jumpy.home;
				break;
			case HOST_IP:
				result = PMS.get().getServer().getHost();
				break;
			case PROFILEDIR:
				result = jumpy.getProfileDirectory();
				break;
			case LOGDIR:
				result = new File(jumpy.jumpylog).getParent();
				break;
			case PLUGINJAR:
				try {
					result = obj.getClass().getProtectionDomain().getCodeSource().getLocation().toURI().normalize().getPath();
				} catch(Exception e) {}
				break;
			case REFRESH:
				int level = Integer.parseInt(arg1);
				DLNAResource folder = pwd(obj);
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
					obj.ex.shutdown();
					PMS.get().reset();
				} catch(Exception e) {e.printStackTrace();}
				break;
			case REBOOT:
				try {
					obj.ex.shutdown();
				} catch(Exception e) {e.printStackTrace();}
				utils.restart();
				break;
			case RUN:
				result = Integer.toString(new runner(arg2, 0).run(obj, arg1, obj.syspath, obj.env));
				break;
			case FOLDERNAME:
				result = pwd(obj).getName();
				break;
			case XMBPATH:
				result = ("/" + getPath(obj, jumpy.top.getParent()) + "/" + unesc(obj.name)).replace("//", "/");
				break;
			case MKDIRS:
				mkdirs(arg1, pwd(obj), arg2);
				break;
			case GETVAR:
				if (utils.properties.containsKey(arg1)) {
					result = utils.properties.get(arg1);
				}
				break;
			case SETVAR:
				utils.properties.put(arg1, arg2);
				break;
			case GETPROPERTY:
				result = utils.getCustomProperty(arg1, arg2);
				break;
			case SETPROPERTY:
				PMS.getConfiguration().setCustomProperty(arg1, arg2);
				break;
			case ICON:
				jumpy.setIcon(arg1, arg2);
				break;
			case INFO:
				Object section = obj.tag;
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
				result = new Gson().toJson((Map)section);
				break;
			case RESOURCE:
				result = jumpy.getResource(arg1);
				break;
			case SETPMS:
				command.pms = arg1;
				break;
			case SUBTITLE:
				if (obj.newItem != null) {
					DLNAResource parent = obj.newItem.getParent();
					parent.getChildren().remove(obj.newItem);
					utils.setMediaSubtitle(obj.newItem, arg1, null);
					parent.addChild(obj.newItem);
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
			case PLAY:
				new Thread(new Runnable() {
					public void run() {
						try {
							UPNPHelper.play(arg1, null, (net.pms.configuration.DeviceConfiguration)RendererConfiguration.getRendererConfigurationBySocketAddress(InetAddress.getByName(arg2)));
						} catch (Exception e) {
							jumpy.log("Error playing uri: " + e);
						}
					}
				}).start();
				break;
		}
		if (action != LOG) {
			jumpy.log(apiName[action] + " " + (arg1 == null ? "" : arg1) + (arg2 == null ? "" : " " + arg2) + (result == null ? "" : ": " + result));
		}
		return result != null ? result : "";
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
					jumpy.log("Error reading media info: " + e);
				}
			}
		}
		m.setAudioTracksList(audio);
		m.setSubtitleTracksList(subs);
		m.setMediaparsed(true);
		return m;
	}

}
