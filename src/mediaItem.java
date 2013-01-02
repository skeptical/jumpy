package net.pms.external.infidel.jumpy;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.io.FilenameUtils;

import net.pms.formats.FormatFactory;

public class mediaItem extends xmbObject {
	public String fmt, uri, userdata;
	public int delay, buffersize;

	public mediaItem(String name, String format, String uri, String thumb) {
		super(name, thumb);
		this.uri = uri;
		if (! StringUtils.isBlank(format)) {
			String[] settings = format.split("\\+", 2);
			this.userdata = settings.length > 1 ? settings[1] : null;
			settings = settings[0].split(":");
			this.fmt = settings[0];
			this.delay = settings.length > 1 ? Integer.valueOf(settings[1]) : -1;
			this.buffersize = settings.length > 2 ? Integer.valueOf(settings[2]) : -1;
		} else {
			this.fmt = FilenameUtils.getExtension(uri);
			this.delay = this.buffersize = -1;
			this.userdata = null;
		}
		setFormat(FormatFactory.getAssociatedExtension("." + this.fmt));
		if (this.thumbnail == null) {
			setThumbnail(jumpy.getIcon(this.fmt));
		}
	}

	@Override
	public boolean isFolder() {
		return false;
	}

	@Override
	public long length() {
		return -1; //DLNAMediaInfo.TRANS_SIZE;
	}

	@Override
	public boolean isTranscodeFolderAvailable() {
		return false;
	}

	@Override
	public String getSystemName() {
		return uri;
	}
}

