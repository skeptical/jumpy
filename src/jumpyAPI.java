package net.pms.external.infidel.util;


// media formats (see net.pms.formats.Format)
//	public static final int ISO = 32;
//	public static final int PLAYLIST = 16;
//	public static final int UNKNOWN = 8;
//	public static final int VIDEO = 4;
//	public static final int AUDIO = 1;
//	public static final int IMAGE = 2;

public interface jumpyAPI {
	public void addItem(int type, String name, String url, String thumb);
	public void setPath(String dir);
}


