package net.pms.external.infidel.jumpy;

import java.io.PrintStream;
import java.io.IOException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import javax.swing.JComponent;
import javax.swing.AbstractButton;
import javax.swing.Icon;

import java.lang.reflect.Method;

import net.pms.PMS;
import net.pms.configuration.PmsConfiguration;
import net.pms.configuration.FormatConfiguration;
import net.pms.dlna.DLNAMediaInfo;
import net.pms.dlna.DLNAResource;
import net.pms.io.OutputParams;
import net.pms.io.PipeProcess;
import net.pms.io.ProcessWrapper;
import net.pms.io.ProcessWrapperImpl;
import net.pms.io.ProcessWrapperLiteImpl;
import net.pms.encoders.Player;
import net.pms.encoders.PlayerFactory;
import net.pms.encoders.PlayerPurpose;
import net.pms.formats.Format;
import net.pms.formats.FormatFactory;
import net.pms.newgui.LooksFrame;

public class player extends Player {

	public String name;
	public command cmdline;
	public boolean isnative = false;
	public boolean dynamic, isMediaitem = false;
	public String id;
	public int priority = 0;
	public int type = Format.UNKNOWN;
	public int purpose = MISC_PLAYER;
	public Format format = null;
	public String[] args;
	public String mimetype;
	public String executable = "";
	private FormatConfiguration supported;
	public String desc, supportStr, cmdStr;
	private ProcessWrapperImpl pw;
	public static PrintStream out = System.out;
	public int delay, buffersize;
	public static Method Format_setIcon = utils.getFormatSetIconMethod();

	public jumpy jumpy;

	public player(jumpy jumpy, String name, String cmdline, String fmt, String mimetype, int type,
			int purpose, String desc, String icon, int delay, int buffersize, int priority) {
		isnative = true;
		init(jumpy, name, cmdline, fmt, mimetype, "f:" + fmt + " m:" + mimetype, type, purpose, desc, icon, null, priority);
		this.delay = delay;
		this.buffersize = buffersize;
	}

	public player(jumpy jumpy, String name, String cmdline, String supported, int type,
			int purpose, String desc, String icon, String playback, int priority) {
		String fmt = null, mimetype = null;
		if (supported.matches(".*f:\\w+.*")) {
			fmt = supported.split("f:")[1].split("\\s")[0];
		} else {
			jumpy.log("ERROR: Invalid player. Missing format (f:) in '" + supported + "'");
			return;
//			throw new IllegalArgumentException("Missing format (f:) in '" + supported + "'");
		}
		if (supported.matches(".*m:\\w+.*")) {
			mimetype = supported.split("m:")[1].split("\\s")[0];
		}
		init(jumpy, name, cmdline, fmt, mimetype, supported, type, purpose, desc, icon, playback, priority);
	}

	private void init(jumpy jumpy, String name, String cmdline, String fmt, String mimetype,
			String supported, int type, int purpose, String desc, String icon, String playback, int priority) {

		this.jumpy = jumpy;
		this.name = name;
		this.id = name;
		this.priority = priority;
		this.mimetype = mimetype;
		this.type = type;
		this.purpose = purpose;
		if (!(cmdline == null || "".equals(cmdline))) {
			this.cmdline = new command(cmdline, null);
			this.dynamic = false;
		} else {
			this.dynamic = true;
		}
		final String[] fmts = fmt.split("\\|");
		this.format = FormatFactory.getAssociatedFormat("." + fmts[0]);

		// create the format if it doesn't exist
		if (this.format == null) {
			jumpy.log("adding new format " + fmt);
			final player self = this;
			this.format = new Format() {
				@Override
				public String[] getSupportedExtensions() {
					return fmts;
				}
				@Override
				public boolean transcodable() {
					return true;
				}
				@Override
				public boolean ps3compatible() {
					return true;
				}
				@Override
				public Identifier getIdentifier() {
					return Identifier.CUSTOM;
				}
				@Override
				public String toString() {
					return self.id;
				}
			};
			this.format.setType(this.type);
			FormatFactory.getSupportedFormats().add(0, this.format/*.duplicate()*/);
		}
		if (this.mimetype == null) {
			this.mimetype = this.format.mimeType();
		}

		this.supported = new FormatConfiguration(Arrays.asList(supported.split(",")));
		this.supportStr = supported;
		this.desc = desc;
		this.cmdStr = cmdline;
		String[] playvars = (playback != null ? playback.split(":") : new String[0]);
		this.delay = playvars.length > 0 ? Integer.valueOf(playvars[0]) : -1;
		this.buffersize = playvars.length > 1 ? Integer.valueOf(playvars[1]) : -1;

		int last = PlayerFactory.getAllPlayers().size();
		PlayerFactory.getAllPlayers().add(priority > -1 && priority < last ? priority : last, this);
		last = PlayerFactory.getPlayers().size();
		PlayerFactory.getPlayers().add(priority > -1 && priority < last ? priority : last, this);
		if (icon != null) {
			if (Format_setIcon != null) {
				try {
					Format_setIcon.invoke(this.format, jumpy.getResource(icon));
				} catch (Exception e) {jumpy.log(e.toString());}
			}
			jumpy.setIcon(fmt, icon);
		}
		enable(true);
	}

	@Override
	public boolean excludeFormat(Format extension) {
		return ! isCompatible(extension);
	}

	@Override
	public boolean isCompatible(DLNAResource resource) {
		return (isCompatible(resource.getFormat()) || isCompatible(resource.getMedia()));
	}

	public boolean isCompatible(DLNAMediaInfo mediaInfo) {
		return (mediaInfo != null && mediaInfo.getContainer() != null && supported.match(mediaInfo) != null);
	}

	public boolean isCompatible(Format format) {
		return (format != null && supported.match(format.getMatchedExtension(), null, null) != null);
	}

	public ProcessWrapper launchTranscode(DLNAResource dlna,
			DLNAMediaInfo media, OutputParams params) throws IOException {
		return launchTranscode(dlna, media, params, dlna.getSystemName());
	}

	public ProcessWrapper launchTranscode(DLNAResource dlna,
			DLNAMediaInfo media, OutputParams params, String filename) throws IOException {

		if (! finalize(filename, dlna)) {
			return null;
		}

		int delay = isMediaitem && ((mediaItem)dlna).delay != -1 ?
			((mediaItem)dlna).delay : this.delay;
		int buffersize = isMediaitem && ((mediaItem)dlna).buffersize > 0 ?
			((mediaItem)dlna).buffersize : this.buffersize;
		if (delay > -1) {
			params.waitbeforestart = delay * 1000;
		}
		if (buffersize > 0) {
			params.minBufferSize = buffersize;
		}

		PipeProcess pipe = new PipeProcess(System.currentTimeMillis() + id);
		cmdline.substitutions.put("outfile", pipe.getInputPipe());

		DLNAResource parent = dlna.getParent();

		jumpy.log("\n");

		if (! cmdline.startAPI(parent instanceof jumpyAPI ? (jumpyAPI)parent : jumpy.top)) {
			return null;
		}

		String[] argv = cmdline.toStrings();

		params.workDir = cmdline.startdir;
		params.env = cmdline.env;
		params.env = new HashMap<String,String>();
		params.env.putAll(cmdline.env);
		params.env.put("OUTFILE", pipe.getInputPipe());
		if (cmdline.syspath != null ) {
			params.env.put("PATH", cmdline.syspath);
		}

		jumpy.log("starting " + name() + " player (" + (params.waitbeforestart/1000)
			+ "s " + (int)params.minBufferSize
			+ "mb).\n\nrunning " + Arrays.toString(argv)
			+ cmdline.envInfo());

//		final player self = this;
//		pw = new ProcessWrapperImpl(argv, params, false, true) {
//			public void stopProcess() {
//				super.stopProcess();
//				self.stop();
//			}
//		};
		pw = new ProcessWrapperImpl(argv, params, false, true);
		params.maxBufferSize = 100;
		params.input_pipes[0] = pipe;
		params.stdin = null;
		ProcessWrapper pipe_process = pipe.getPipeProcess();
		pw.attachProcess(pipe_process);
		pipe_process.runInNewThread();
		final player self = this;
		pw.attachProcess(new ProcessWrapperLiteImpl(null) {
			public void stopProcess() {
				self.stop();
			}
		});
		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {}
		pipe.deleteLater();

		pw.runInNewThread();
		return pw;
	}

	public void stop() {
		if (! pw.isDestroyed()) {
			cmdline.stopAPI();
			List<String> results;
			if ((results = pw.getResults()) != null) {
				for (String line : results) {
					out.println(line);
				}
			}
		}
	}

	public void setCommand(String cmd) {
		this.cmdline = new command(cmd, null);
	}

	public boolean finalize(String filename, DLNAResource dlna) {
		isMediaitem = dlna instanceof mediaItem;
		if (! (filename == null || filename.isEmpty())) {
			if (dynamic || cmdline == null) {
				cmdline = new command(filename,
					dlna instanceof xmbAction ? ((xmbAction)dlna).syspath : null,
					dlna instanceof xmbAction ? ((xmbAction)dlna).env : null);
			}
			String fmt = null;
			if (isMediaitem) {
				fmt = ((mediaItem)dlna).fmt;
			} else {
				String[] fmts = dlna.getFormat().getSupportedExtensions();
				fmt = fmts != null ? fmts[0] : utils.getExtension(filename);
			}
			cmdline.substitutions.put("format", fmt);
			cmdline.substitutions.put("filename", filename.replace("\\","\\\\"));
			cmdline.substitutions.put("userdata", isMediaitem && ((mediaItem)dlna).userdata != null ?
				((mediaItem)dlna).userdata : "");
			return true;
		}
		return false;
	}

	public boolean enable(boolean on) {
		PmsConfiguration configuration = PMS.getConfiguration();
		List<String> engines = configuration.getEnginesAsList(PMS.get().getRegistry());
		int index = engines.indexOf(id);
		boolean remove = (!on && index > -1) || (on && index > 0);
		boolean add = (on && index != 0);
		if (remove) {
			engines.removeAll(Arrays.asList(id));
			configuration.setEnginesAsList(new ArrayList(engines));
		}
		if (add) {
			int last = engines.size();
			engines.add(priority > -1 && priority < last ? priority : last, id);
			// store the gui's reload button state
			boolean isGui = (PMS.get().getFrame() instanceof LooksFrame);
			AbstractButton reload = isGui ? ((LooksFrame)PMS.get().getFrame()).getReload() : null;
			Icon icon = isGui ? reload.getIcon() : null;
			String tooltip = isGui ? reload.getToolTipText() : null;
			// modify the engine list
			configuration.setEnginesAsList(new ArrayList(engines));
			if (isGui) {
				// restore the reload button state
				reload.setIcon(icon);
				reload.setToolTipText(tooltip);
			}
		}
		if (add || remove) {
			jumpy.log((on ? "en" : "dis") + "abling " + id + " player.", true);
			return true;
		}
		return false;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public int type() {
		return type;
	}

	@Override
	public int purpose() {
		return purpose;
	}

	public PlayerPurpose getPurpose() {
		return purpose == VIDEO_SIMPLEFILE_PLAYER ? PlayerPurpose.VIDEO_FILE_PLAYER :
			purpose == AUDIO_SIMPLEFILE_PLAYER ? PlayerPurpose.AUDIO_FILE_PLAYER :
			purpose == VIDEO_WEBSTREAM_PLAYER ? PlayerPurpose.VIDEO_WEB_STREAM_PLAYER :
			purpose == AUDIO_WEBSTREAM_PLAYER ? PlayerPurpose.AUDIO_WEB_STREAM_PLAYER :
			purpose == MISC_PLAYER ? PlayerPurpose.MISC_PLAYER : null;
	}

	@Override
	public String[] args() {
		return args;
	}

	@Override
	public String mimeType() {
		return mimetype;
	}

	@Override
	public String executable() {
		return executable;
	}

	@Override
	public JComponent config() {
		return config.playerPanel(this, true);
	}
}

