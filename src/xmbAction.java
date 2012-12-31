package net.pms.external.infidel.jumpy;

import java.util.Map;
import java.util.HashMap;
import net.pms.dlna.DLNAResource;

public class xmbAction extends mediaItem {
	public runner ex;
	public xmbAction alt;
	public String syspath;
	public Map<String,String> env;

	public xmbAction(String name, String format, String uri, String thumb) {
		this(name, format, uri, thumb, null, null);
	}

	public xmbAction(String name, String format, String uri, String thumb, String syspath, Map<String,String> env) {
		super(name, format, uri, thumb == null ? "#checkmark" : thumb);
		this.syspath = syspath;
		if (env != null && !env.isEmpty()) {
			this.env = new HashMap<String,String>();
			this.env.putAll(env);
		} else {
			this.env = null;
		}
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
					"jump+CMD : Stopping Process : Error Stopping Process", null, "#x");
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

