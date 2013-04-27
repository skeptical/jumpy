package net.pms.external.infidel.jumpy;


public interface jumpyAPI {

	public String getName();

	// constants from net.pms.formats.Format:
	//	public static final int ISO = 32;
	//	public static final int PLAYLIST = 16;
	//	public static final int UNKNOWN = 8;
	//	public static final int VIDEO = 4;
	//	public static final int AUDIO = 1;
	//	public static final int IMAGE = 2;

	// new constants:
	public static final int MEDIA = 1025;
	public static final int FOLDER = 1026;
	public static final int ACTION = 1028;
	public static final int UNRESOLVED = 2048;
	public static final int FEED = 4096;
	public static final int VIDEOFEED = 4100; // Format.VIDEO|FEED
	public static final int AUDIOFEED = 4097; // Format.AUDIO|FEED
	public static final int IMAGEFEED = 4098; // Format.IMAGE|FEED

	public Object addItem(int type, String filename, String uri, String thumbnail, String data);
	public void addPath(String path);
	public void setEnv(String name, String val);

	public static final String[] apiName = {"",
		"VERSION", "HOME", "PROFILEDIR", "LOGDIR", "PLUGINJAR", "RESTART", "FOLDERNAME",
		"GETPROPERTY", "SETPROPERTY", "SETPMS", "REBOOT", "XMBPATH", "ICON", "RESOURCE",
		"REFRESH", "RUN", "SUBTITLE", "GETVAR", "LOG"};
	public static final int VERSION = 1;
	public static final int HOME = 2;
	public static final int PROFILEDIR = 3;
	public static final int LOGDIR = 4;
	public static final int PLUGINJAR = 5;
	public static final int RESTART = 6;
	public static final int FOLDERNAME = 7;
	public static final int GETPROPERTY = 8;
	public static final int SETPROPERTY = 9;
	public static final int SETPMS = 10;
	public static final int REBOOT = 11;
	public static final int XMBPATH = 12;
	public static final int ICON = 13;
	public static final int RESOURCE = 14;
	public static final int REFRESH = 15;
	public static final int RUN = 16;
	public static final int SUBTITLE = 17;
	public static final int GETVAR = 18;
	public static final int LOG = 19;

	public String util(int action, String arg1, String arg2);

	// constants from net.pms.encoders.Player:
	// public static final int VIDEO_SIMPLEFILE_PLAYER = 0;
	// public static final int AUDIO_SIMPLEFILE_PLAYER = 1;
	// public static final int VIDEO_WEBSTREAM_PLAYER = 2;
	// public static final int AUDIO_WEBSTREAM_PLAYER = 3;
	// public static final int MISC_PLAYER = 4;
	// public static final String NATIVE = "NATIVE";
	public int addPlayer(String name, String cmd, String supported, int mediatype, int purpose, String desc, String icon, String playback);
	public void register(Object obj);
}


