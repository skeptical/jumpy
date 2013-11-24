package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

import net.pms.formats.Format;
import net.pms.formats.FormatFactory;
import net.pms.formats.WEB;
import net.pms.encoders.PlayerFactory;
import net.pms.dlna.Range;
import net.pms.dlna.DLNAMediaInfo;
import net.pms.configuration.RendererConfiguration;
import net.pms.util.PlayerUtil;

public class resolver extends xmbObject {
	public String uri0, uri = null, syspath = null;
	public Map<String,String> env;
	public boolean resolved = false, isweb, iscmdarray;
	public int type;

	public static final int XBMC = 1;
	public static final int URLRESOLVER = 2;
	public static final int YOUTUBE_DL = 4;

	public static int scrapers = 0;
	public static jumpy jumpy;
	public static command server;
	public static boolean enabled = false, playback = true;

	public resolver(jumpy jumpy, int type, String name, String uri, String thumb, String syspath, Map<String,String> env) {
		super(name, thumb);
		jumpy = jumpy;
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
		} else {
			f = FormatFactory.getAssociatedExtension(u);
			if (f == null) {
				f = FormatFactory.getAssociatedExtension(
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
			uri = resolve(uri0, syspath, env);
			if (uri != null) {
				resolved = true;
				reset(uri);
				setPlayer(PlayerFactory.getPlayer(this));
			}
		}
		return uri == null ? null : super.getInputStream(range, mediarenderer);
	}

	@Override
	public String getSystemName() {
		return uri != null ? uri : (isweb && iscmdarray) ? "http://unresolved" : uri0;
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
	public boolean isURLResolved() {
		return resolved;
	}

	// the actual resolving functionality:

	public static String resolve(String str) {
		return resolve(str, null, null);
	}

	public static String resolve(String str, String syspath, Map<String,String> env) {
		if (isCmdArray(str)) {
			// it's a script, run it and capture addItem() or stdout
			class result {String uri = null;}
			final result r = new result();
			scriptFolder s = new scriptFolder(jumpy, "Resolver", null, null) {
				@Override
				public Object addItem(int type, String filename, String uri, String thumbnail, String data) {
					r.uri = uri;
					return null;
				}
			};
			runner ex = new runner();
			ex.cache = true;
			jumpy.log("\n");
			int exitcode = ex.run(s, str, syspath, env);
			return r.uri != null ? r.uri :
				(exitcode == 0 && ex.output != null) ? ex.output.split("\n")[0] : null;

		} else if (scrapers != 0) {
			// it's a url, use the available scrapers
			if (pyResolver == null) {
				startPyServer();
			}
			return pyResolver.resolve(str);
		}
		return null;
	}

	public static boolean isCmdArray(String str) {
		return str.startsWith("[") && str.endsWith("]") &&
			! (str.contains(", plugin://") && ((scrapers & XBMC) != 0));
	}

	// py4j python callback server support:

	public interface Resolver {
		public String resolve(String url);
	}

	public static Resolver pyResolver = null;

	public static void startPyServer() {
		if (pyResolver == null) {
			scriptFolder registrar = new scriptFolder(jumpy, "resolver", null, null) {
				@Override
				public void register(Object obj) {
					if (obj == null) {
						try {
							ready = new CountDownLatch(1);
							ready.await();
						} catch (Exception e) {e.printStackTrace();}
					} else {
						jumpy.log("registering " + obj.getClass().getName(), true);
						pyResolver = (Resolver)obj;
						ready.countDown();
					}
				}
			};
			server = new command();
			server.init("[" + jumpy.home + "lib" + File.separatorChar + "resolver.py , start , &]",
				registrar.syspath, registrar.env);
			server.has_callback = true;
//			server.needs_listener = true;
//			server.py4jlog(true, registrar);
			jumpy.log("\n");
			new runner().run(registrar, server);
		}
	}

	public static boolean verify() {
		new runner(runner.QUIET).run(jumpy.top, "[" + jumpy.home + "lib" + File.separatorChar + "resolver.py , 'validate']", null, null);
		if (jumpy.top.env.containsKey("resolvers")) {
			String found = jumpy.top.env.get("resolvers");
			scrapers = (found.contains("xbmc") ? XBMC : 0) |
				(found.contains("urlresolver") ? URLRESOLVER : 0) |
				(found.contains("youtube-dl") ? YOUTUBE_DL : 0);
		}
		jumpy.top.env.clear();
		return scrapers != 0;
	}
}

