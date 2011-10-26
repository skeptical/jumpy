package net.pms.external.infidel;

import java.io.IOException;
import java.util.Properties;

import com.sun.jna.Platform;

import java.io.File;
import java.io.FilenameFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import net.pms.PMS;
import net.pms.util.PMSUtil;
import net.pms.dlna.DLNAResource;
import net.pms.dlna.virtual.VirtualFolder;
import net.pms.dlna.virtual.VirtualVideoAction;
import net.pms.formats.Format;
import net.pms.dlna.WebVideoStream;
import net.pms.dlna.WebAudioStream;

import net.pms.external.ExternalFactory;
import net.pms.external.ExternalListener;
import net.pms.external.AdditionalFolderAtRoot;
//import net.pms.external.widgets.*;

import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.JComponent;
//import net.pms.external.infidel.py_plugin_config;
import net.pms.external.infidel.py;
import net.pms.external.infidel.util.jumpyAPI;


public class pyFolder extends VirtualFolder implements jumpyAPI {
	public String cmd;
	public String basepath = null, path = null;
	
	public pyFolder(String name, String cmd, String thumbnailIcon) {
		this(name, cmd, thumbnailIcon, null);
	}
	
	public pyFolder(String name, String cmd, String thumbnailIcon, String path) {
		super(name, thumbnailIcon);
		this.cmd = cmd;
		this.basepath = this.path = path;
	}
	
	@Override
	public void discoverChildren() {
		discovered = false;
		py.path = path;
		py.run(cmd, this);
	}

	public boolean refreshChildren() {
		discovered = false;
		return false;
	}

// media formats (see net.pms.formats.Format)
//	public static final int ISO = 32;
//	public static final int PLAYLIST = 16;
//	public static final int UNKNOWN = 8;
//	public static final int VIDEO = 4;
//	public static final int AUDIO = 1;
//	public static final int IMAGE = 2;

	@Override
	public void addItem(int type, String name, String cmd, String thumb) {
		String media = "";
		switch (type) {
			case -1:
				media = "UNRESOLVED";
				addChild(new pyFolder(name, cmd, thumb, path));
				break;
			case  0:
				media = "FOLDER";
				addChild(new pyFolder(name, cmd, thumb, path));
				break;
			case Format.VIDEO:
				media = "VIDEO";
				addChild(new WebVideoStream(name, cmd, thumb));
				break;
			case Format.AUDIO:
				media = "AUDIO";
				addChild(new WebAudioStream(name, cmd, thumb));
				break;
			case Format.IMAGE:
				media = "IMAGE";
				addChild(new WebAudioStream(name, cmd, thumb));
				break;
		}
		System.out.printf("\n[%s] %s\n%s\n%s\n\n", media, name, cmd, thumb);
	}
	
	@Override
	public void setPath(String dir) {
		path = (dir == null || dir.equals("")) ? basepath : path + File.pathSeparator + dir;
	}

}
