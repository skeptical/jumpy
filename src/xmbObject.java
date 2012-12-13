package net.pms.external.infidel.jumpy;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;

import net.pms.network.HTTPResource;
import net.pms.dlna.DLNAResource;

public abstract class xmbObject extends DLNAResource {
	public String name, thumbnail, thumbtype;
	public boolean valid;

	public xmbObject(String name, String thumb) {
		this.name = name;
		setThumbnail(thumb);
		this.valid = true;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setThumbnail(String thumb) {
		thumbnail = jumpy.getResource(thumb);
		thumbtype = (thumbnail != null && thumbnail.toLowerCase().endsWith(".png") ?
			HTTPResource.PNG_TYPEMIME : HTTPResource.JPEG_TYPEMIME);
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
	public InputStream getThumbnailInputStream() {
		if (thumbnail != null) {
			try {
				return new FileInputStream(thumbnail);
			} catch (Exception e) {}
			try {
				return downloadAndSend(thumbnail, true);
			} catch (Exception e) {}
			jumpy.logonce("can't get thumbnail: " + thumbnail, thumbnail);
		}
		return null;
	}

	@Override
	public String getThumbnailContentType() {
		return thumbtype;
	}
}

