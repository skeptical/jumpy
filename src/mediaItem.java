package net.pms.external.infidel.jumpy;

import java.io.IOException;
import java.io.InputStream;

import net.pms.network.HTTPResource;
import net.pms.dlna.DLNAResource;
import net.pms.formats.FormatFactory;

public class mediaItem extends DLNAResource {
	public String name, fmt, uri, thumbnail, thumbtype;

	public mediaItem(String name, String format, String uri, String thumb) {
		this.name = name;
		this.fmt = format;
		this.uri = uri;
		this.thumbnail = thumb;
		this.thumbtype = (thumb != null && thumb.toLowerCase().endsWith(".png") ?
			HTTPResource.PNG_TYPEMIME : HTTPResource.JPEG_TYPEMIME);
		setFormat(FormatFactory.getAssociatedExtension("." + fmt));
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
		return true;
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

