package net.pms.external.infidel.jumpy;

import java.io.File;
import java.io.IOException;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.Border;
import javax.swing.plaf.metal.MetalIconFactory;
import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;

import org.ini4j.Wini;
import org.ini4j.Options;
import org.ini4j.Persistable;
import org.ini4j.OptionMap;
import org.ini4j.Profile.Section;

import net.pms.PMS;
import net.pms.io.SystemUtils;
import net.pms.io.BasicSystemUtils;
import net.pms.io.MacSystemUtils;

// convenience class to combine interfaces for inlining
abstract class multiListener implements ItemListener, ChangeListener, ActionListener, DocumentListener {}

public class config {

	static final String updateurl = "http://skeptical.github.com/jumpy/";
	static final String websiteurl = "https://github.com/skeptical/jumpy";
	static final String forumurl = "http://www.universalmediaserver.com/forum/viewtopic.php?f=6&t=288";
	static final String xbmcurl = "http://xbmc.org";
	static final String pythonuri = "http://www.python.org/download/releases/2.7.3";
	static final String py4juri = "http://py4j.sourceforge.net";

	static final Insets border = new Insets(10,10,10,10);
	static final Insets items = new Insets(5,5,5,5);
	static final Insets none = new Insets(0,0,0,0);
	static final Insets stack = new Insets(0,5,0,5);

	public static jumpy jumpy;
	public static String latest, latesturl;
	static JPanel statusbar;

	public static multiListener listener = new multiListener() {
		public void stateChanged(ChangeEvent e) {
			setopt(((JComponent)e.getSource()), 0);
		}
		public void itemStateChanged(ItemEvent e) {
			setopt(((JComponent)e.getItem()), e.getStateChange());
		}
		public void setopt(JComponent c, int state) {
			String opt = c.getToolTipText();
			PMS.debug("setopt " + opt);
			if (opt.equals("bookmarks")) {
				jumpy.showBookmarks = (state == ItemEvent.DESELECTED ? false : true);
			} else if (opt.equals("verbose_bookmarks")) {
				jumpy.verboseBookmarks = (state == ItemEvent.DESELECTED ? false : true);
			} else if (opt.equals("refresh")) {
				jumpy.refresh = (Integer)(((JSpinner)c).getValue());
			} else if (opt.equals("check_update")) {
				jumpy.check_update = (state == ItemEvent.DESELECTED ? false : true);
				checkLatest();
			} else if (opt.equals("debug")) {
				jumpy.debug = (state == ItemEvent.DESELECTED ? false : true);
			} else if (opt.equals("url_resolver")) {
				resolver.enabled = (state == ItemEvent.DESELECTED ? false : true);
			}
		}
		public void actionPerformed(ActionEvent e) {
			String cmd = e.getActionCommand();
			if (cmd.equals("Save")) {
				PMS.debug("Save");
				jumpy.writeconf();
			} else if (cmd.equals("Revert")) {
				PMS.debug("Revert");
				jumpy.readconf();
				rebuild((JComponent)e.getSource());
			} else if (cmd.equals("Update")) {
				PMS.debug("Update");
				update();
			}
		}
		public void insertUpdate(DocumentEvent e) {}
		public void removeUpdate(DocumentEvent e) {}
		public void changedUpdate(DocumentEvent e) {}
	};

	public static void init(final jumpy obj) {
		jumpy = obj;
	}

	public static JComponent mainPanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0; c.gridy = 0;
		c.fill = GridBagConstraints.BOTH;
		c.gridwidth = GridBagConstraints.REMAINDER;
		JPanel top = new JPanel();
		top.add(new JLabel("<html><center>"
			+ "<font size=+1 color=blue>Jumpy <font color=#ffa050>" + jumpy.version + "</font></font>"
			+ "<center></html>"));
		c.insets = none;
		panel.add(top, c);

		JToolBar files = new JToolBar();
		files.setLayout(new GridBagLayout());
		files.setFloatable(false);
		files.setRollover(true);
		c.insets = items;
		c.gridwidth = 1;
		c.gridx = 0; c.gridy = 0;
		files.add(editButton(jumpy.scriptsini), c);
		c.gridx = 1;
		files.add(editButton(jumpy.bookmarksini), c);
		c.gridx = 2;
		files.add(editButton(jumpy.jumpyconf), c);
		c.gridx = 3;
		files.add(editButton(jumpy.jumpylog), c);
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.gridx = 0; c.gridy = 2;
		c.insets = none;
		panel.add(files, c);

		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Options", options());

		tabs.addTab("Scripts", scripts());

		for (player p : jumpy.players.subList(1, jumpy.players.size())) {
			tabs.addTab(p.name() + " player", playerPanel(p, false));
		}
		tabs.addTab("About", about());
		c.gridx = 0; c.gridy = 1;
		c.insets = none;
		panel.add(tabs, c);

		// check for updates every time the panel appears
		final JPanel main = panel;
		panel.addHierarchyListener( new HierarchyListener() {
			public void hierarchyChanged(HierarchyEvent e) {
				if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == HierarchyEvent.SHOWING_CHANGED) {
					if (main.isShowing()) {
						defaultLAF(true);
						checkLatest();
					} else {
						defaultLAF(false);
					}
				}
			}
		});

		return panel;
	}

	public static JPanel options() {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0; c.gridy = -1;
		c.fill = GridBagConstraints.BOTH;
		c.gridwidth = GridBagConstraints.REMAINDER;

		c.insets = items;

		c.gridx = 0; c.gridy++;
		statusbar = new JPanel(new GridLayout(0,1)) {
			@Override
			public Component add(Component comp) {
				super.removeAll();
				comp = super.add(comp);
				((Window)getTopLevelAncestor()).pack();
				return comp;
			}
			@Override
			public void removeAll() {
				if (getComponents().length > 0) {
					super.removeAll();
					((Window)getTopLevelAncestor()).pack();
				}
			}
		};
		panel.add(statusbar, c);

		c.gridx = 0; c.gridy++;
		panel.add(checkbox("Enable bookmarks", jumpy.showBookmarks, "bookmarks"), c);
		c.gridx = 0; c.gridy++;
		panel.add(checkbox("Qualify bookmark names (e.g. 'Live :ESPN' instead of just 'Live')", jumpy.verboseBookmarks, "verbose_bookmarks"), c);
		c.gridx = 0; c.gridy++;
		c.gridwidth = GridBagConstraints.RELATIVE;
		c.weightx = 1;
		panel.add(new JLabel("Refresh folder content every (minutes) :", SwingConstants.LEFT), c);
		c.gridx = 1;
		c.weightx = 0;
		c.gridwidth = GridBagConstraints.REMAINDER;
		panel.add(numberBox(Integer.valueOf(jumpy.refresh), 0, 1000, 1, "refresh"), c);
		c.gridx = 0; c.gridy++;
		panel.add(checkbox("Check for updates", jumpy.check_update, "check_update"), c);
		c.gridx = 0; c.gridy++;
		panel.add(checkbox("Print log messages to console", jumpy.debug, "debug"), c);
//		if (jumpy.host.equals("UMS")) {
			c.gridx = 0; c.gridy++;
			boolean have_scrapers = resolver.scrapers != 0;
			JCheckBox resolverBox = checkbox("Act as a url resolver", have_scrapers && resolver.enabled,
				have_scrapers ? "url_resolver" :
				"<html>Requires installing the xbmc urlresolver addon<br>or youtube-dl- see docs</html>");
			panel.add(resolverBox, c);
			resolverBox.setEnabled(have_scrapers);
//		}

		JPanel p = new JPanel();
		p.add(actionButton("Revert", "Reload settings from disk.", listener, true));
		p.add(actionButton("Save", jumpy.jumpyconf, listener, true));
		c.insets = none;
		c.gridx = 1; c.gridy++;
		panel.add(p, c);

		JPanel frame = new JPanel(new GridBagLayout());
		c.insets = border;
		frame.add(panel, c);
		return frame;
	}

	public static JComponent scripts() {
		boolean state = userscripts.meta.get("!gui", "show_disabled", boolean.class);

		final JPanel panel = new JPanel(new GridBagLayout());
		final GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(0,10,10,10);

		c.gridwidth = GridBagConstraints.REMAINDER;
		c.gridheight = 1;
		c.gridx = 0; c.gridy = 0;
		c.weighty = 0;

		JCheckBox checkbox = checkbox("Show disabled scripts", state, null, new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				boolean state = e.getStateChange() != ItemEvent.DESELECTED;
				userscripts.meta.put("!gui", "show_disabled", String.valueOf(state));
				script.save(userscripts.meta, null);
				panel.remove(1);
				panel.add(scriptbar(state), c);
				panel.validate();
			}
		});
		checkbox.setHorizontalTextPosition(SwingConstants.LEFT);
		JToolBar toolBar = new JToolBar(SwingConstants.HORIZONTAL);
		toolBar.setFloatable(false);
		toolBar.setRollover(true);
		toolBar.add(new JPanel());
		toolBar.add(Box.createHorizontalGlue());
		toolBar.add(checkbox);
		panel.add(toolBar, c);

		c.gridy++;
		c.weighty = 1;
		panel.add(scriptbar(state), c);

		return panel;
	}

	public static JComponent scriptbar(boolean showall) {
		JToolBar toolbar = new JToolBar();
		toolbar.setLayout(new GridBagLayout());
		toolbar.setFloatable(false);
		toolbar.setRollover(true);

		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0; c.gridy = -1;
		c.fill = GridBagConstraints.BOTH;

		c.insets = none;

		for (Wini ini : jumpy.userscripts.inis) {
			for (Section s : ini.values()) {
				if ((! showall && ! userscripts.isEnabled(s)) || userscripts.isHiddenSection(s)) {
					continue;
				}
				script item = new script(s, ini);

				c.gridx = 0; c.gridy++;
				c.anchor = GridBagConstraints.LINE_START;

				c.gridwidth = 1;
				c.weightx = 0;
				toolbar.add(spacer(80,1), c);

				c.gridx++;
				toolbar.add(item.checkbox, c);

				c.gridx++;
				c.weightx = 1;
				c.gridwidth = 1;
				toolbar.add(item.button, c);

				c.gridx++;
				c.gridwidth = 1;
				c.weightx = 0;
				toolbar.add(spacer(120,1), c);
			}
		}

		JScrollPane sp = new JScrollPane(toolbar);
		sp.setPreferredSize(new Dimension(540,200));
		sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		sp.setBorder(null);
		JScrollBar sb = sp.getVerticalScrollBar();
		sb.setUI(new javax.swing.plaf.metal.MetalScrollBarUI() {
			@Override
			protected JButton createDecreaseButton(int orientation) {
				JButton z = new JButton();
				Dimension zero = new Dimension(0,0);
				z.setPreferredSize(zero);
				z.setMinimumSize(zero);
				z.setMaximumSize(zero);
				return z;
			}
			@Override
			protected JButton createIncreaseButton(int orientation) {
				return createDecreaseButton(orientation);
			}
			@Override
			protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
//				g.setColor(c.getBackground());
//				g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
			}
		});

		sp.validate();
		return sp;
	}

	public static JPanel about() {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0; c.gridy = 0;
		c.fill = GridBagConstraints.CENTER;
		c.insets = border;

		try {
			BufferedImage logo = ImageIO.read(jumpy.getClass().getClassLoader()
				.getResourceAsStream("resources/jumpy2-48.png"));
			panel.add(new JLabel(new ImageIcon(logo)));
			c.gridy++;
		} catch (Exception e) { e.printStackTrace(); }

		panel.add(new JLabel("<html><center>"
			+ "Jumpy is a general purpose framework<br>"
			+ "for scripts in python and other languages<br>"
			+ "to plug into PMS.<br><br>"
			+ "Included is a set of python scripts to run xbmc addons.<br>"
			+ "</center></html>"), c);
		JPanel links = new JPanel();
		links.add(linkButton("Readme", "file://" + jumpy.home + "readme.html"));
		links.add(linkButton("Code", websiteurl));
		links.add(linkButton("Forum", forumurl));
		c.insets = none;
		c.gridy++;
		panel.add(links, c);

		return panel;
	}

	public static JComponent playerPanel(player p, boolean inibutton) {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.NORTHWEST;
		c.insets = border;
		c.gridx = 0; c.gridy = 0;
		boolean notitle = p.isnative || !inibutton;
		String about = "<html>"
			+ (notitle ? "" : "Jumpy User-Defined Player<br><br>")
			+ (p.desc == null ? "" : p.desc + "<br><br>")
			+ "<table width=500 align=left valign=top >"
			+ "<tr><td width=80>Supported</td><td><font color=blue>"
			+ (p.supportStr.length() < 71 ? p.supportStr : WordUtils.wrap(p.supportStr, 70, "<br>", true))
			+ "</font></td></tr>"
			+ "<tr><td>Playback</td><td><font color=blue>"
			+ (p.delay * p.buffersize == 1 ? " default" : ""
				+ (p.delay == -1 ? "" : " delay: " + p.delay + "s")
				+ (p.buffersize == -1 ? "" : " buffer: " + p.buffersize + "mb")
				)
			+ "</font></td></tr>"
			+ (p.cmdStr == null || "".equals(p.cmdStr) ? "" :
				("<tr><td>Command</td><td><font color=blue>" + p.cmdStr + "</font></td></tr>"))
			+ "</table></html>";
		JLabel label = new JLabel(about, SwingConstants.LEFT);
		label.setVerticalAlignment(SwingConstants.TOP);
		panel.add(label, c);
		if (p.cmdline != null) {
			JToolBar toolBar = new JToolBar(SwingConstants.VERTICAL);
			toolBar.setFloatable(false);
			toolBar.setRollover(true);
			String _script = p.cmdline.getScript(true);
			if (_script != null) {
				toolBar.add(editButton(_script));
			}
			if (inibutton) {
				c.gridx = 0; c.gridy = 2;
				toolBar.add(editButton(p.jumpy.scriptsini));
			}
			c.gridx = 0; c.gridy = 1;
			panel.add(toolBar, c);
		}

		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		top.add(panel);
		return top;
	}

	public static JButton actionButton(String label, String help) {
		return actionButton(label, help, listener, false);
	}

	public static JButton actionButton(String label, String help, ActionListener listener) {
		return actionButton(label, help, listener, false);
	}

	public static JButton actionButton(String label, String help, ActionListener listener, boolean defaultSize) {
		JButton button = new JButton(label);
		button.setActionCommand(label);
		button.setToolTipText(help);
		if (defaultSize) button.setPreferredSize(new Dimension(75,25));
		button.addActionListener(listener);
		return button;
	}

	public static JButton optionPaneButton(String label, String help) {
		final JButton button = new JButton(label);
		button.setActionCommand(label);
		button.setToolTipText(help);
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// find the enclosing JOptionPane and set its return value
				JComponent parent = (JComponent)e.getSource();
				while ((parent = (JComponent)parent.getParent()) != null ) {
					if (parent instanceof JOptionPane) {
						((JOptionPane)parent).setValue(button);
						return;
					}
				}
			}
		});
		button.setPreferredSize(new Dimension(75,25));
		return button;
	}

	public static JSpinner numberBox(int value, int min, int max, int step, String help) {
		SpinnerModel model = new SpinnerNumberModel(new Integer(value), new Integer(min), new Integer(max), new Integer(step));
		JSpinner spinner = new JSpinner(model);
		spinner.addChangeListener(listener);
		spinner.setToolTipText(help);
		return spinner;
	}

	public static JCheckBox checkbox(String label, boolean val, String help) {
		return checkbox(label, val, help, listener);
	}

	public static JCheckBox checkbox(String label, boolean val, String help, ItemListener listener) {
		JCheckBox checkbox = new JCheckBox(label, val);
		checkbox.setToolTipText(help);
		checkbox.setFocusPainted(false);
		checkbox.addItemListener(listener);
		return checkbox;
	}

//	public static JTextField textbox(String val, String help) {
//		JTextField textbox = new JTextField(val);
//		textbox.setToolTipText(help);
//		textbox.addActionListener(listener);
//		return textbox;
//	}

	public static JTextArea textbox(String val, String help) {
		return textbox(val, help, listener);
	}

	public static JTextArea textbox(String val, String help, DocumentListener listener) {
		JTextArea textbox = new JTextArea(val);
//		textbox.setLineWrap( true );
//		textbox.setWrapStyleWord( true );
		textbox.setColumns(40);
//		textbox.setMaximumSize(new Dimension(500,300));
		textbox.setToolTipText(help);
		textbox.getDocument().addDocumentListener(listener);
		return textbox;
	}

	public static final Matcher markdown = Pattern.compile("\\[(.+)\\]\\((.+)\\)").matcher("");

	public static JButton linkButton(String markup) {
		if (markdown.reset(markup).find()) {
			return linkButton(markdown.group(1), markdown.group(2));
		} else {
			return linkButton("link", markup);
		}
	}

	public static JButton linkButton(String label, final String uri) {
		JButton link = new JButton("<html><a href=''><font color=blue>" + label + "</font></a></html>");
		link.setOpaque(false);
		link.setContentAreaFilled(false);
		link.setBorderPainted(false);
		link.setFocusPainted(false);
		link.setToolTipText(uri);
//		link.setMargin(none);
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				utils.browse(uri);
			}
		});
		return link;
	}

	public static JButton editButton(final String uri) {
		final File file = new File(uri);
		final boolean exists = file.exists();
		JButton edit =  new JButton((exists ? "" : "+ ") + file.getName(),
				exists ? MetalIconFactory.getTreeLeafIcon() : null);
		edit.setToolTipText(uri);
		edit.setFocusPainted(false);
		edit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				boolean isnew = !exists && create(file, true);
				if (! (exists || isnew)) {
					return;
				}
				utils.textedit(uri);
				if (isnew) {
					rebuild((JComponent)e.getSource());
				}
			}
		});
		return edit;
	}

	public static JPanel spacer(int w, int h) {
		JPanel p = new JPanel();
		p.setPreferredSize(new Dimension(w, h));
		return p;
	}

	public static boolean create(File file, boolean confirm) {
		if (file.exists()) {
			return true;
		}
		if(confirm && JOptionPane.showConfirmDialog(null, "Create " + file.getName() + "?", "Create File", JOptionPane.YES_NO_OPTION) == 1) {
			return false;
		}
		if (file.getAbsolutePath().equals(jumpy.jumpyconf) && jumpy.writeconf()) {
			jumpy.log("creating jumpy.conf");
			return true;
		} else if (file.getAbsolutePath().equals(jumpy.scriptsini)) {
			try {
				File examples = new File(jumpy.home, "examples");
				jumpy.log("creating jumpy-scripts.ini");
				FileUtils.copyFile(new File(examples, "sample-jumpy-scripts.ini"), file);
				for (File csv : FileUtils.listFiles(examples, new String[]{"csv"}, false)) {
					jumpy.log("creating " + csv.getName());
					FileUtils.copyFileToDirectory(csv, file.getParentFile());
				}
				return true;
			}
			catch (Exception e) { e.printStackTrace(); return false; }
		}
		try {
			jumpy.log("creating " + file.getPath());
			file.createNewFile();
		}
		catch (Exception e) { e.printStackTrace(); return false; }
		return true;
	}

	public static void progressRun(String msg, JComponent parent, final Runnable r) {
		final JProgressBar progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		UIManager.put("ProgressBar.cycleTime", new Integer(5000));
		progressBar.setBorderPainted(false);
		progressBar.setStringPainted(true);
		progressBar.setString(msg);
		progressBar.setOpaque(false);
		Color c = progressBar.getForeground();
		UIManager.put("ProgressBar.foreground", new Color(c.getRed(), c.getGreen(), c.getBlue(), 192));
		Dimension size = parent.getPreferredSize();
		progressBar.setPreferredSize(size);
		progressBar.setMaximumSize(size);
		parent.add(progressBar, 0);
		parent.validate();
		final JComponent p = parent;
		new Thread(new Runnable() {
			public void run() {
				try {
					r.run();
				} finally {
					// we're in a new thread here so we defer
					// gui tasks to the swing Event Dispatch Thread
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							p.remove(progressBar);
							p.repaint();
						}
					});
				}
			}
		}).start();
	}

	public static void checkLatest() {
		latest = latesturl = null;
		if (jumpy.check_update) {
			progressRun("Checking for updates", statusbar, new Runnable() {
				public void run() {
					String text = utils.gettext(updateurl + "LATEST");
					if (text != null) {
						latest = text.split("version=")[1].split("\\r?\\n")[0].trim();
						latesturl = text.split((utils.windows ? "win32" : utils.mac ? "osx" : "linux") + "=")[1]
							.split("\\r?\\n")[0].trim();
						jumpy.log("latest version is " + latest);
						if (utils.isNewer(latest, jumpy.version)) {
							JButton btn = actionButton(
								"<html><font color=blue>Update available:  Jumpy " + latest + "</font></html>",
								"Install Jumpy " + latest);
							btn.setActionCommand("Update");
							btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
							statusbar.add(btn);
						}
					}
				}
			});
		} else statusbar.removeAll();
	}

	private static void update() {
		int opt = JOptionPane.showConfirmDialog(null,
			"Install Jumpy " + latest + " and restart " + jumpy.host + "?", "Update", JOptionPane.YES_NO_OPTION);
		if (opt == JOptionPane.YES_OPTION) {
			progressRun("Installing Jumpy " + latest, statusbar, new Runnable() {
				public void run() {
					if (! utils.update(latesturl)) {
						JOptionPane.showMessageDialog(null, "Couldn't update to " + latest, "Error", JOptionPane.ERROR_MESSAGE);
					}
				}
			});
		}
	}

	private static void rebuild(JComponent c) {
		// self-destruct and rebuild
		((Window)c.getTopLevelAncestor()).dispose();
		JOptionPane.showOptionDialog((JFrame) (SwingUtilities.getWindowAncestor((Component) PMS.get().getFrame())),
			mainPanel(), "Options", JOptionPane.CLOSED_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);
	}

	public static void showError(Exception e) {
		e.printStackTrace();
		JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
	}

	static Color tf = null, tb, tf0, tb0;
	static Border td, td0;

	public static void defaultLAF(boolean enable) {
		if (tf == null) {
			tf = UIManager.getLookAndFeelDefaults().getColor("ToolTip.foreground");
			tb = UIManager.getLookAndFeelDefaults().getColor("ToolTip.background");
			td = UIManager.getLookAndFeelDefaults().getBorder("ToolTip.border");
			tf0 = UIManager.getColor("ToolTip.foreground");
			tb0 = UIManager.getColor("ToolTip.background");
			td0 = UIManager.getBorder("ToolTip.border");
		}
		if (enable) {
			UIManager.put("ToolTip.foreground", tf);
			UIManager.put("ToolTip.background", tb);
			UIManager.put("ToolTip.border", td);
		} else {
			UIManager.put("ToolTip.foreground", tf0);
			UIManager.put("ToolTip.background", tb0);
			UIManager.put("ToolTip.border", td0);
		}
	}

}



class script {

	public Persistable doc;
	public Section section;
	public Image icon;
	public String link, version;
	public JComponent checkbox;
	public JButton button;

	public String name, conf, desc;

	public script(final Section s, Persistable d) {
		section = s;
		doc = d;

		name = userscripts._get(s, "_title", userscripts.getName(s));
		conf = command.expand(userscripts._get(s, "_conf", null), null);
		desc = command.expand(userscripts._get(s, "_desc", s.getComment(s)), null);
		link = command.expand(userscripts._get(s, "_link", null), null);
		version = command.expand(userscripts._get(s, "_version", null), null);

		String thumb = command.expand(userscripts._get(s, "_icon", "#plug+f=#a6a8a4"), null);
		thumb = jumpy.getResource(thumb);
		try {
			icon = ImageIO.read(new File(thumb));
		} catch (Exception e) {
			jumpy.log("Error reading icon '" + thumb + "': " + e.getMessage());
		}

		final script self = this;

		checkbox = userscripts.canDisable(section) ?
			config.checkbox(null, userscripts.isEnabled(s), "enable", new ItemListener() {
				public void itemStateChanged(ItemEvent e) {
					userscripts.enable(s, e.getStateChange() != ItemEvent.DESELECTED);
					reset(self);
				}
			}) : new JLabel();

		final ActionListener editlistener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String c = e.getActionCommand();
				if (c.equals("Reload") || c.equals("Enable")) {
					userscripts.enable(s, true);
					reset(self);
				} else if (c.equals("Disable")) {
					userscripts.enable(s, false);
					reset(self);
				} else {
					edit(self);
				}
			}
		};

		String b = desc != null ? desc.split("\n")[0] : null;
		button = config.actionButton("<html><b>" + name + "</b>"
			+ (b != null ? ("<br>" + (b.length() > 40 ? (b.substring(0,40) + "...") : b)) : "") + "</html>",
			null, editlistener, false);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		if (icon != null) {
			button.setIcon(new ImageIcon(icon.getScaledInstance(32, -1, java.awt.Image.SCALE_SMOOTH)));
			button.setIconTextGap(16);
		}
		button.setFocusPainted(false);
		button.setMargin(config.items);
		button.setEnabled(userscripts.isEnabled(section));
		button.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e){
				if (e.getButton() > MouseEvent.BUTTON1) {
					JPopupMenu menu = new JPopupMenu();
					JMenuItem edit = new JMenuItem("Edit");
					edit.addActionListener(editlistener);
					menu.add(edit);
					boolean enabled = userscripts.isEnabled(self.section);
					if (userscripts.canDisable(self.section)) {
						JMenuItem reload = new JMenuItem(enabled ? "Reload" : "Enable");
						reload.addActionListener(editlistener);
						menu.add(reload);
						if (enabled) {
							JMenuItem disable = new JMenuItem("Disable");
							disable.addActionListener(editlistener);
							menu.add(disable);
						}
					}
					menu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});
	}

	public static void reset(final script item) {
		item.button.setEnabled(false);
		config.progressRun("", item.button, new Runnable() {
			public void run() {
				save(item.doc, item.section);
				final boolean enabled = userscripts.isEnabled(item.section);
				if (enabled) {
					userscripts.restart(item.section);
				} else {
					userscripts.remove(item.section);
				}
				// we're outside the swing Event Dispatch Thread
				// here so we defer gui tasks
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						item.button.setEnabled(enabled);
						if (item.checkbox instanceof JCheckBox) {
							((JCheckBox)item.checkbox).setSelected(enabled);
						}
					}
				});
			}
		});
	}

	public static void edit(script item) {

		OptionMap s = item.section;

		ArrayList<optItem> opts = new ArrayList();
		ArrayList<saveListener> listeners = new ArrayList();

		JButton save = config.optionPaneButton("Save", null);
		JButton close = config.optionPaneButton("Close", null);
		save.setEnabled(false);

		saveListener listener = new saveListener(item.doc, save);
		listeners.add(listener);

		getOpts(s, opts, listener);

		String _script = null;
		int i;
		for (i=0; i<opts.size(); i++) {
			String key = opts.get(i).key;
			if ("cmd".equals(key)) {
				_script = s.get(key);
				if (i > 0) {
					opts.add(0, opts.remove(i));
				}
				break;
			}
		}
		if (_script != null) {
			_script = _script.startsWith("pms ") ? item.doc.getFile().getAbsolutePath() :
				new command(_script, null).getScript(true);
		}

		String desc = item.desc;
		if (item.conf != null) {
			try {
				Options o = new userscripts.Options(new File(item.conf));
				saveListener listener2 = new saveListener(o, save);
				listeners.add(listener2);
				getOpts(o, opts, listener2);
				if (desc == null) {
					desc = o.getComment();
				}
			} catch (Exception e) {
				config.showError(e);
				return;
			}
		}

		if (desc != null) {
			if (desc.startsWith("<html>")) {
				desc = desc.substring(6).replace("</html>", "");
			} else {
				desc = desc.replace("\n", "<br>");
			}
			desc = "<html><div width=500>" + desc + (item.link != null ? "<br><br>" : "") + "</div></html>";

		}

		int choice = JOptionPane.showOptionDialog(null,
			scriptPanel(_script, desc, item.icon, item.link, opts),
			item.name + (item.version == null ? "" : (" " + item.version)),
			JOptionPane.CLOSED_OPTION, JOptionPane.PLAIN_MESSAGE,
			null, new Object[] {save, close}, close);

		switch (choice) {
//			case 1: break;
//			case 2: edit(item); break;
			case 0:
				for (optItem opt : opts) {
					JComponent val = opt.val;
					opt.src.put(opt.key,
						val instanceof JCheckBox ? String.valueOf(((JCheckBox)val).isSelected()) :
						val instanceof JTextArea ? ((JTextArea)val).getText() :
						null);
				}
				for (saveListener l : listeners) {
					if (l.changed) {
						save(l.doc, item.section);
					}
				}
				reset(item);
				break;
		}
	}

	public static void save(Persistable doc, Section section) {
		try {
			if (doc instanceof userscripts.Doc) {
				((userscripts.Doc)doc).store(section);
			} else {
				doc.store();
			}
		} catch (Exception e) {
			config.showError(e);
		}
	}

	public static void getOpts(OptionMap s, ArrayList<optItem> opts, saveListener listener) {
		for(String opt : s.keySet()) {
			if (! userscripts.isEditable(opt)) {
				continue;
			}

			String help = s.getComment(opt);
			if (help != null) {
				help = help.trim();
			}

			if (help != null && help.toLowerCase().contains("(true|false)")) {
				opts.add(new optItem(s, opt, config.checkbox("", Boolean.valueOf(s.get(opt)), help.replaceAll("\\(true\\|false\\)", "").trim(), listener)));
			} else {
				opts.add(new optItem(s, opt, config.textbox(s.get(opt), help, listener)));
			}
		}
	}

	static class optItem {
		public Map src;
		public String key;
		public JComponent val;
		public optItem (Map s, String k, JComponent v) {
			src = s; key = k; val = v;
		}
		public String getName() {
			return key.startsWith("$") ? key.substring(1) : key;
		}
	}

	static class saveListener implements DocumentListener, ItemListener {
		public boolean changed = false;
		public Persistable doc;
		public JButton save;

		public saveListener(Persistable d, JButton b) {
			doc = d; save = b;
		}

		public void changed() {
			changed = true;
			save.setEnabled(true);
			SwingUtilities.getRootPane(save).setDefaultButton(save);
			SwingUtilities.getWindowAncestor(save).pack();
		}

		public void insertUpdate(DocumentEvent e) {changed();}
		public void removeUpdate(DocumentEvent e) {changed();}
		public void changedUpdate(DocumentEvent e) {changed();}
		public void itemStateChanged(ItemEvent e) {changed();}
	}

	public static JComponent scriptPanel(String script, String desc, Image icon, String link, ArrayList<optItem> opts) {
		boolean linkbar = (link != null || script != null);

		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.anchor = GridBagConstraints.LINE_START;
		c.insets = config.border;
		c.gridx = 0; c.gridy = -1;

		c.gridx = 0; c.gridy++;
		c.gridwidth = 1;
		c.gridheight = linkbar ? 1 : 2;
		c.weightx = 1;
		JLabel i = icon == null ? new JLabel() :
			new JLabel(new ImageIcon(icon.getScaledInstance(48, -1, java.awt.Image.SCALE_SMOOTH)), JLabel.LEFT);
		i.setVerticalAlignment(SwingConstants.TOP);
		panel.add(i, c);
		c.gridx = 1;
		c.weightx = 0;
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.gridheight = 1;

		JLabel l = new JLabel(desc, SwingConstants.LEFT);
		c.insets = new Insets(5,5,0,5);
		panel.add(l, c);

		if (linkbar) {
			JToolBar toolBar = new JToolBar(SwingConstants.HORIZONTAL);
			toolBar.setFloatable(false);
			toolBar.setRollover(true);
			if (link != null) {
				JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));
				for (String u : link.split("\n")) {
					p.add(config.linkButton(u));
				}
				toolBar.add(p);
			} else {
				toolBar.add(new JPanel());
			}
			if (script != null) {
				toolBar.add(Box.createHorizontalGlue());
				JButton edit = config.editButton(script);
				edit.setHorizontalTextPosition(SwingConstants.LEFT);
				toolBar.add(edit);
			}
			c.insets = config.stack;
			c.gridx = 1; c.gridy++;
			panel.add(toolBar, c);
		}

		c.insets = config.border;

		for (optItem opt : opts) {
			JComponent val = opt.val;
			c.gridx = 0; c.gridy++;
			c.gridwidth = 1;
			c.weightx = 0;
			JLabel label = new JLabel(opt.getName() + " :", SwingConstants.LEFT);
			label.setToolTipText(val.getToolTipText());
			panel.add(label, c);
			c.gridx = 1;
			c.weightx = 1;
			c.gridwidth = GridBagConstraints.REMAINDER;
			panel.add(val instanceof JTextArea ? new JScrollPane(val) : val, c);
		}

		return panel;
	}
}


