package net.pms.external.infidel.jumpy;


public interface jumpyAPI {
	
	// new constants:
	public static final int UNRESOLVED = -2;
	public static final int FOLDER = -1;
	
	// constants from net.pms.formats.Format:
	//	public static final int ISO = 32;
	//	public static final int PLAYLIST = 16;
	//	public static final int UNKNOWN = 8;
	//	public static final int VIDEO = 4;
	//	public static final int AUDIO = 1;
	//	public static final int IMAGE = 2;
	
	// new constants (net.pms.formats.Format|FEED)
	public static final int FEED = 4096;
	public static final int VIDEOFEED = 4100; // Format.VIDEO|FEED
	public static final int AUDIOFEED = 4097; // Format.AUDIO|FEED;
	public static final int IMAGEFEED = 4098; // Format.IMAGE|FEED;

	public void addItem(int type, String name, String uri, String thumb);
	public void setPath(String dir);
}


