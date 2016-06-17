package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.HashMap;
import java.util.Map;
import net.pms.configuration.RendererConfiguration;
import net.pms.dlna.DLNAMediaInfo;
import net.pms.dlna.Range;
import net.pms.encoders.FFmpegWebVideo;
import net.pms.encoders.PlayerFactory;
import net.pms.formats.Format;
import net.pms.formats.FormatFactory;
import net.pms.formats.WEB;

public class resolver extends xmbObject {
	public String uri0, uri = null, syspath = null;
	public boolean resolved = false, isweb, iscmdarray;
	public int type;

	public static final int XBMC = 1;
	public static final int URLRESOLVER = 2;
	public static final int YOUTUBE_DL = 4;

	public static final String UNRESOLVED = "http://jumpy.unresolved/";

	public static int scrapers = 0;
	public static jumpy jumpy;
	public static boolean enabled = false, playback = true;

	public resolver(jumpy jumpy, int type, String name, String uri, String thumb, String syspath, Map<String,String> env) {
		super(name, thumb);
		resolver.jumpy = jumpy;
		this.uri0 = uri;
		this.syspath = syspath;
		this.env = new HashMap<String,String>();
		if (env != null && !env.isEmpty()) {
			this.env.putAll(env);
		}
		this.type = (type == 0 ? Format.VIDEO : type);
		setSpecificType(type);
		setMedia(new DLNAMediaInfo());
		reset(uri0);
	}

	public void reset(String u) {
		Format f;
		isweb = u.matches(".*\\S+://.*");
		iscmdarray = u.startsWith("[");
		if (isweb) {
			f = new WEB();
			// avoid URI.getScheme() parsing (in pms) by setting protocol explicitly,
			// otherwise psuedo-urls (eg librtmp-style urls with spaces) will fail
			f.setMatchedExtension(iscmdarray ? "http" : u.split("://")[0].toLowerCase());
			if (type == Format.UNKNOWN) {
				// infer type from extension or default to video
				Format ext = iscmdarray ? null : FormatFactory.getAssociatedFormat(uri.split("://")[1]);
				type = ext == null ? Format.VIDEO : ext.getType();
			}
			setSpecificType(type);
		} else {
			f = FormatFactory.getAssociatedFormat(u);
			if (f == null) {
				f = FormatFactory.getAssociatedFormat(
					type == Format.IMAGE ? ".jpg" : type == Format.AUDIO ? ".mp3" : ".mpg");
			}
		}
		setFormat(f);
		resolveFormat();
		getMedia().setContainer(f.getMatchedExtension());
	}

	@Override // (h/t SharkHunter)
	public InputStream getInputStream(Range range, RendererConfiguration mediarenderer) throws IOException {
		if (! resolved) {
			if (pyResolver == null) {
				startPyServer();
			}
			root.newItem = this; //TODO: synchronization issues?
			Map<String, Object> r = resolve(uri0, syspath, env);
			if (r != null) {
				uri = (String)r.get("uri");
				if (uri != null) {
					resolved = true;
					reset(uri);
					setPlayer(PlayerFactory.getPlayer(this));
				}
				if (r.containsKey("details")) {
					new xmb.mediaDetails((Map)r.get("details")).update(this);
				}
			} else {
				jumpy.log("failed to resolve " + uri0);
			}
		}
		return uri == null ? null : super.getInputStream(range, mediarenderer);
	}

	@Override
	public String getSystemName() {
		return uri != null ? uri : (isweb && iscmdarray) ? (UNRESOLVED + uri0) : uri0;
	}

	@Override
	public boolean isFolder() {
		return false;
	}

	@Override
	public long length() {
		long size = getMedia().getSize();
		return size > 0 ? size : DLNAMediaInfo.TRANS_SIZE;
	}

	@Override
	public boolean isTranscodeFolderAvailable() {
		return (media.getCodecV() != null || media.getFirstAudioTrack() != null);
	}

	@Override
	public boolean isURLResolved() {
		return resolved;
	}

	// the actual resolving functionality:

	public static Map resolve(String str) {
		return resolve(str, null, null);
	}

	public static Map resolve(String str, String syspath, Map<String,String> env) {
		if (str.startsWith(UNRESOLVED)) {
			str = str.substring(UNRESOLVED.length());
		}
		if (isCmdArray(str)) {
			// it's a script, run it and capture addItem() or stdout
			final HashMap<String,Object> r = new HashMap<String,Object>();
			scriptFolder s = new scriptFolder(jumpy, "Resolver", null, null) {
				@Override
				public Object addItem(int type, String filename, String uri, String thumbnail, Map details, String data) {
					r.put("uri", uri);
					r.put("thumbnail", thumbnail);
					r.put("details", details);
					r.put("data", data);
					return null;
				}
			};
			runner ex = new runner();
			ex.cache = true;
			jumpy.log("\n");
			int exitcode = ex.run(s, str, syspath, env);
			if (r.isEmpty() && exitcode == 0 && ex.output != null) {
				r.put("uri", ex.output.split("\n")[0]);
			}
			return r;

		} else if (scrapers != 0) {
			// it's a url, use the available scrapers
			if (pyResolver == null) {
				startPyServer();
			}
			return pyResolver.resolve(str, env != null ? env.get("USERDATA") : null);
		}
		return null;
	}

	public static boolean isCmdArray(String str) {
		return str.startsWith("[") && str.endsWith("]") &&
			! (str.contains(", plugin://") && ((scrapers & XBMC) != 0));
	}

	// py4j python callback server support:

	public interface Resolver {
		public Map<String,Object> resolve(String url, String userdata);
	}

	public static Resolver pyResolver = null;
	public static scriptFolder root;

	public static void startPyServer() {
		if (pyResolver == null) {
			root = new scriptFolder(jumpy, "resolver", null, null);
			command cmd = new command();
			cmd.init("[" + jumpy.home + "lib" + File.separatorChar + "resolver.py , start , &]",
				root.syspath, root.env);
			cmd.has_callback = true;
//			cmd.needs_listener = true;
//			cmd.py4jlog(true, registrar);
			jumpy.log("\n");
			new runner().run(root, cmd);
			pyResolver = (Resolver)root.registeredObject;
		}
	}

	public static boolean verify() {
		new runner(runner.QUIET).run(jumpy.top, "[" + jumpy.home + "lib" + File.separatorChar + "resolver.py , 'validate']", null, null);
		if (utils.properties.containsKey("_resolvers")) {
			String found = utils.properties.get("_resolvers");
			scrapers = (found.contains("xbmc") ? XBMC : 0) |
				(found.contains("urlresolver") ? URLRESOLVER : 0) |
				(found.contains("youtube-dl") ? YOUTUBE_DL : 0);
		}
		jumpy.top.env.clear();
		return scrapers != 0;
	}
}

