package net.pms.external.infidel.jumpy;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;

import net.pms.network.HTTPResource;
import net.pms.dlna.DLNAResource;

public class xmbObject extends DLNAResource {
	public String name, thumbnail, thumbtype;
	public boolean valid, isFolder;

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
}

