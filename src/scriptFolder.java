package net.pms.external.infidel.jumpy;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import net.pms.dlna.DLNAResource;


public class scriptFolder extends xmbObject {

	public boolean canBookmark = true;
	public boolean refreshOnce = true;
	public boolean refreshAlways = false;
	private int lastcount;

	public scriptFolder(jumpy jumpy, String name, String uri, String thumb) {
		this(jumpy, name, uri, thumb, null, null);
	}

	public scriptFolder(jumpy jumpy, String name, String uri, String thumb, String syspath) {
		this(jumpy, name, uri, thumb, syspath, null);
	}

	public scriptFolder(scriptFolder other) {
		this(other.jumpy, other.name, other.uri, other.thumbnail, other.syspath, other.env);
	}

	public scriptFolder(jumpy jumpy, String name, Map<String,String> m) {
		this(jumpy, name, m.remove("uri"), m.remove("thumbnail"), m.remove("syspath"), m);
	}

	public scriptFolder(jumpy jumpy, String name, String uri, String thumb, String syspath, Map<String,String> env) {
		super(name, thumb);
		this.jumpy = jumpy;
		this.uri = uri;
		this.basepath = this.syspath = syspath;
		this.env = new HashMap<String,String>();
		if (env != null && !env.isEmpty()) {
			this.env.putAll(env);
		}
		this.ex = null;
		this.newItem = null;
		this.refreshAlways = (jumpy.refresh == 0);
		setLastModified(0);
		this.isFolder = true;
	}

	@Override
	public boolean isFolder() {
		return true;
	}

	@Override
	public long length() {
		return 0;
	}

	@Override
	public void discoverChildren() {
		if (uri == null || uri.equals("")) {
			return;
		}
		List<DLNAResource> children = getChildren();
		children.clear();
		lastcount = 0;
		jumpy.log("\n");
		jumpy.log("Opening folder: " + name + ".\n");
		ex = new runner();
		ex.run(this, uri, syspath, env);
		if (jumpy.showBookmarks && canBookmark && (children.size() > 0 || isBookmark)) {
			final scriptFolder self = this;
			addChild(new xmbAction((isBookmark ? "Delete" : "Add") + " bookmark",
					"jump+CMD : Bookmark " + (isBookmark ? "deleted" : "added") + " :  ", null,
					isBookmark ? "#x" : "#plus") {
				public int run(scriptFolder folder, command cmdline) {
					self.jumpy.bookmark(self);
					return 0;
				}
			});
			// put the action first (note: this breaks id=index correspondence in the children)
			children.add(0, children.remove(children.size() - 1));
		}
		refreshOnce = false;
		setLastModified(0);
	}

	public void refresh() {
		refreshOnce = true;
	}

	@Override
	public void resolve() {
		setDiscovered(!(refreshOnce || refreshAlways));
	}

	@Override
	public boolean isRefreshNeeded() {
		boolean isneeded = (getLastModified() != 0);
		setLastModified(0);
		return isneeded;
	}

	@Override
	public boolean analyzeChildren(int count) {
		int size = getChildren().size();
		boolean ready = (ex != null && ex.running) ?
			count == -1 ? false : (size - lastcount >= count) : true;
		lastcount = size;
		return ready;
	}
}

