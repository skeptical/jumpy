package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import net.pms.dlna.DLNAResource;
import net.pms.network.HTTPResource;

public class xmbObject extends DLNAResource implements jumpyAPI {
	public Object tag = null;
	public String name, thumbnail, thumbtype;
	public boolean valid, isFolder;

	public String uri, basepath = null, syspath = null;
	public Map<String,String> env;
	public jumpy jumpy;
	public runner ex;
	public DLNAResource newItem;
	public boolean isBookmark = false;

	public xmbObject(String name, String thumb) {
		this(name, thumb, false);
	}

	public xmbObject(String name, String thumb, boolean isFolder) {
		this.name = name;
		setThumbnail(thumb);
		this.valid = true;
		this.isFolder = isFolder;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setThumbnail(String thumb) {
		thumbnail = jumpy.getResource(thumb);
		thumbtype = (thumbnail != null && thumbnail.toLowerCase().endsWith(".png") ?
			HTTPResource.PNG_TYPEMIME : HTTPResource.JPEG_TYPEMIME);
	}

	public void touch() {
		lastmodified = 1;
	}

	// DLNAResource

	@Override
	public InputStream getInputStream() throws IOException {
		return null;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getSystemName() {
		return name;
	}

	@Override
	public boolean isValid() {
		return valid;
	}

	@Override
	public boolean isFolder() {
		return isFolder;
	}

	@Override
	public long length() {
		return 0;
	}

	@Override
	public InputStream getThumbnailInputStream() throws IOException {
		if (thumbnail != null) {
			try {
				return new FileInputStream(thumbnail);
			} catch (Exception e) {}
			try {
				return downloadAndSend(thumbnail, true);
			} catch (Exception e) {}
			jumpy.logonce("can't get thumbnail: " + thumbnail, thumbnail, false);
		}
		return null;
	}

	@Override
	public String getThumbnailContentType() {
		return thumbtype;
	}

	// jumpyAPI

	public Object addItem(int type, String filename, String uri, String thumbnail) {
		return addItem(type, filename, uri, thumbnail, null, null);
	}

	public Object addItem(int type, String filename, String uri, String thumbnail, Map details) {
		return addItem(type, filename, uri, thumbnail, details, null);
	}

	@Override
	public Object addItem(int type, String filename, String uri, String thumbnail, Map details, String data) {
		return xmb.add(this, type, filename, uri, thumbnail, details, data);
	}

	@Override
	public String util(int action, String arg1, String arg2) {
		return xmb.util(this, action, arg1, arg2);
	}

	@Override
	public void addPath(String path) {
		syspath = path == null ? basepath : (syspath == null ? "" : syspath + File.pathSeparator) + path;
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
	public int addPlayer(String name, String cmd, String supported, int mediatype, int purpose, String desc, String icon, String playback, int priority) {
		return jumpy.addPlayer(name, cmd, supported, mediatype, purpose, desc, icon, playback, priority);
	}

	public /*volatile*/ Object registeredObject;
	protected CountDownLatch ready = null;

	@Override
	public void register(Object obj) {
		if (obj == null && ready == null) {
			try {
				ready = new CountDownLatch(1);
				ready.await(2000, TimeUnit.MILLISECONDS);
				ready = null;
			} catch (Exception e) {e.printStackTrace();}
		} else if (ready != null) {
			registeredObject = obj;
			String className = obj.getClass().getName();
			jumpy.log("[" + name + "] registering " + (className.contains("$Proxy") ? className : obj), true);
			jumpy.log("\n");
			ready.countDown();
		}
	}

	@Override
	public Object getTag() {
		return tag;
	}
}

