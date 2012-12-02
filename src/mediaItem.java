package net.pms.external.infidel.jumpy;

import java.io.IOException;
import java.io.InputStream;

import net.pms.network.HTTPResource;
import net.pms.dlna.DLNAResource;
import net.pms.formats.FormatFactory;

public class mediaItem extends DLNAResource {
	public String name, fmt, uri, thumbnail, thumbtype, userdata;
	public int delay, buffersize;
	public boolean valid;

	public mediaItem(String name, String format, String uri, String thumb) {
		this.name = name;
		this.uri = uri;
		this.thumbnail = thumb;
		this.thumbtype = (thumb != null && thumb.toLowerCase().endsWith(".png") ?
			HTTPResource.PNG_TYPEMIME : HTTPResource.JPEG_TYPEMIME);
		String[] settings = format.split("\\+");
		this.userdata = settings.length > 1 ? settings[1] : null;
		settings = settings[0].split(":");
		this.fmt = settings[0];
		this.delay = settings.length > 1 ? Integer.valueOf(settings[1]) : -1;
		this.buffersize = settings.length > 2 ? Integer.valueOf(settings[2]) : -1;
		setFormat(FormatFactory.getAssociatedExtension("." + this.fmt));
		this.valid = true;
	}

	@Override
	public boolean isTranscodeFolderAvailable() {
		return false;
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
	public boolean isFolder() {
		return false;
	}

	@Override
	public long length() {
		return -1; //DLNAMediaInfo.TRANS_SIZE;
	}

	public long lastModified() {
		return 0;
	}

	@Override
	public String getSystemName() {
		return uri;
	}

	@Override
	public boolean isValid() {
		return valid;
	}

	@Override
	public InputStream getThumbnailInputStream() {
		return getResourceInputStream(thumbnail);
	}

	@Override
	public String getThumbnailContentType() {
		return thumbtype;
	}
}

