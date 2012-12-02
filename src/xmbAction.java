package net.pms.external.infidel.jumpy;

import net.pms.dlna.DLNAResource;

public class xmbAction extends mediaItem {
	public runner ex;
	public xmbAction alt;

	public xmbAction(String name, String format, String uri, String thumb) {
		super(name, format, uri, thumb);
		this.ex = null;
		this.alt = null;
	}

	public int run(scriptFolder folder, command cmdline) {
		int exitcode = 0;
		if (alt == null) {
			ex = new runner();
			ex.cache = true;
			ex.name = getName();
			exitcode = ex.run(folder, cmdline);
			if (ex.running) {
				alt = new xmbAction("[STOP] " + name,
					"jump:-1+CMD : Stopping Process : Error Stopping Process", null, this.thumbnail);
				alt.alt = this;
			}
		} else {
			runner.stop(alt.ex);
			alt.alt = null;
		}
		if (alt != null) {
			DLNAResource parent = getParent();
			parent.addChild(alt);
			parent.getChildren().remove(this);
			parent.refreshChildren();
		}
		return exitcode;
	}
}

