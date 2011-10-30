package net.pms.external.infidel;

import java.io.IOException;

import java.io.File;

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

import net.pms.external.infidel.py;
import net.pms.external.infidel.jumpyRoot;
import net.pms.external.infidel.jumpyAPI;


public class pyFolder extends VirtualFolder implements jumpyAPI {
	public String uri;
	public String basepath = null, pypath = null;
	private jumpyRoot jumpy;
	
	public pyFolder(jumpyRoot jumpy, String name, String uri, String thumbnailIcon) {
		this(jumpy, name, uri, thumbnailIcon, null);
	}
	
	public pyFolder(jumpyRoot jumpy, String name, String uri, String thumbnailIcon, String pypath) {
		super(name, thumbnailIcon);
		this.jumpy = jumpy;
		this.uri = uri;
		this.basepath = this.pypath = pypath;
	}
	
	@Override
	public void discoverChildren() {
		discovered = false;
		jumpy.log("%n");
		jumpy.log("Opening folder: " + name + ".%n");
		py.run(this, uri, pypath);
	}

	public boolean refreshChildren() {
		discovered = false;
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
