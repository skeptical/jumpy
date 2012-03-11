package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.IOException;

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


public class pyFolder extends VirtualFolder implements jumpyAPI {
	public String uri, thumbnail;
	public String basepath = null, pypath = null;
	private jumpyRoot jumpy;
	private py python;
	
	public boolean isBookmark = false;
	public boolean refreshOnce = true;
	public boolean refreshAlways = false;
	
	public pyFolder(jumpyRoot jumpy, String name, String uri, String thumbnailIcon) {
		this(jumpy, name, uri, thumbnailIcon, null);
	}
	
	public pyFolder(pyFolder other) {
		this(other.jumpy, other.name, other.uri, other.thumbnailIcon, other.pypath);
	}
	
	public pyFolder(jumpyRoot jumpy, String name, String uri, String thumbnailIcon, String pypath) {
		super(name, thumbnailIcon);
		this.jumpy = jumpy;
		this.thumbnail = thumbnailIcon;
		this.uri = uri;
		this.basepath = this.pypath = pypath;
		this.python = new py();
		this.refreshAlways = (((jumpy)jumpy).refresh == 0);
	}
	
	@Override
	public void discoverChildren() {
		if (uri == null || uri.equals("")) {
			return;
		}
		getChildren().clear();
		if (((jumpy)jumpy).showBookmarks) {
			final pyFolder me = this;
			addChild(new VirtualVideoAction((isBookmark ? "Delete" : "Add") + " bookmark", true) {
				public boolean enable() {
					jumpy.bookmark(me);
					return true;
				}
			});
		}
		jumpy.log("%n");
		jumpy.log("Opening folder: " + name + ".%n");
		python.run(this, uri, pypath);
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
				addChild(new pyFolder(jumpy, name, uri, thumb, pypath));
				break;
			case UNRESOLVED:
				media = "unresolved item";
				addChild(new pyFolder(jumpy, name, uri, thumb, pypath));
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
		pypath = (dir == null ? basepath : pypath + File.pathSeparator + dir);
	}

}
