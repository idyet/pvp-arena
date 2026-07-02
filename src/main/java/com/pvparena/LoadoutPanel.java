package com.pvparena;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Feature 3 — the Loadouts sidebar. Lists saved {@link Loadout}s in three fixed build
 * groups, a builder-gated "Save current setup" button, and per-entry actions. Pure
 * Swing on the EDT: it only reads cached state (never touches the client directly) and
 * delegates client-coupled work to {@link Actions}.
 */
@Slf4j
class LoadoutPanel extends PluginPanel
{
	private static final Color ACTIVE_BG = new Color(64, 54, 24);
	private static final Color ICON_FG = new Color(200, 200, 200);

	/** Square canvas (px) shared by both control icons so the buttons match. */
	private static final int ICON_SIZE = 8;
	/** Fixed square size for the stop/options buttons. */
	private static final Dimension CONTROL_BTN_SIZE = new Dimension(18, 18);

	/** A filled square ("stop comparing"). */
	private static final Icon STOP_ICON = stopIcon();
	/** A horizontal ellipsis ("loadout options"). */
	private static final Icon MENU_ICON = menuIcon();
	/** A funnel: filled = filter engaged, outline = full catalog shown. */
	private static final Icon FILTER_ON_ICON = filterIcon(true);
	private static final Icon FILTER_OFF_ICON = filterIcon(false);

	/** Callbacks into the plugin for anything that needs the client thread or a dialog. */
	interface Actions
	{
		boolean isBuilderOpen();

		void saveCurrentSetup();

		void load(Loadout loadout);

		void stop();

		boolean isFilterOn();

		void toggleFilter();

		void updateFromSetup(Loadout loadout);

		void rename(Loadout loadout);

		void delete(Loadout loadout);

		/** Export: copy this loadout to the clipboard as a Loadout code. */
		void copyCode(Loadout loadout);

		/** Import: mint a loadout from a Loadout code on the clipboard. */
		void importCode();

		int unlocatableCount(Loadout active);
	}

	private final LoadoutManager manager;
	private final Actions actions;

	private final JButton saveButton = new JButton("Save current setup");
	/** Always enabled (unlike {@code saveButton}); import touches only clipboard + config. */
	private final JButton importButton = new JButton("Import loadout code");
	private final JPanel groups = new JPanel();
	private final Map<Integer, Boolean> collapsed = new HashMap<>();

	LoadoutPanel(LoadoutManager manager, Actions actions)
	{
		this.manager = manager;
		this.actions = actions;

		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		saveButton.addActionListener(e -> actions.saveCurrentSetup());
		importButton.setToolTipText("Create a loadout from a code on your clipboard");
		importButton.addActionListener(e -> actions.importCode());

		final JPanel top = new JPanel(new GridLayout(0, 1, 0, 4));
		top.add(saveButton);
		top.add(importButton);
		add(top, BorderLayout.NORTH);

		groups.setLayout(new BoxLayout(groups, BoxLayout.Y_AXIS));
		add(groups, BorderLayout.CENTER);

		rebuild();
	}

	/** Rebuilds the whole list from current state. Safe to call from any thread. */
	void rebuild()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::rebuild);
			return;
		}

		try
		{
			final boolean builderOpen = actions.isBuilderOpen();
			saveButton.setEnabled(builderOpen);
			saveButton.setToolTipText(builderOpen
				? "Save the build you're currently editing as a loadout"
				: "Open the arena setup screen to save");

			groups.removeAll();
			for (Build build : Build.values())
			{
				groups.add(buildSection(build));
				groups.add(javax.swing.Box.createVerticalStrut(6));
			}
			groups.revalidate();
			groups.repaint();
		}
		catch (Exception e)
		{
			log.warn("Loadout panel rebuild failed", e);
		}
	}

	private JPanel buildSection(Build build)
	{
		final List<Loadout> loadouts = manager.forBuild(build.getValue());
		final boolean isCollapsed = collapsed.getOrDefault(build.getValue(), false);

		final JPanel section = new JPanel(new BorderLayout());
		section.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

		final JButton header = new JButton((isCollapsed ? "+ " : "- ") + build.getLabel() + "  (" + loadouts.size() + ")");
		header.setHorizontalAlignment(SwingConstants.LEFT);
		header.setFocusPainted(false);
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.addActionListener(e ->
		{
			collapsed.put(build.getValue(), !isCollapsed);
			rebuild();
		});
		section.add(header, BorderLayout.NORTH);

		if (!isCollapsed)
		{
			final JPanel body = new JPanel();
			body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
			body.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));

			if (loadouts.isEmpty())
			{
				final JLabel empty = new JLabel("No loadouts yet");
				empty.setForeground(Color.GRAY);
				empty.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
				body.add(empty);
			}
			else
			{
				for (Loadout l : loadouts)
				{
					body.add(entryRow(l));
					body.add(javax.swing.Box.createVerticalStrut(3));
				}
			}
			section.add(body, BorderLayout.CENTER);
		}

		return section;
	}

	private JPanel entryRow(Loadout loadout)
	{
		final boolean isActive = manager.isActive(loadout);

		final JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 4));
		row.setBackground(isActive ? ACTIVE_BG : ColorScheme.DARKER_GRAY_COLOR);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

		final JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);

		final JLabel name = new JLabel(loadout.getName() == null ? "(unnamed)" : loadout.getName());
		text.add(name);

		final StringBuilder sub = new StringBuilder();
		final String book = shortSpellbook(loadout.getSpellbook());
		if (book != null)
		{
			sub.append(book);
		}
		if (isActive)
		{
			final int missing = actions.unlocatableCount(loadout);
			if (missing > 0)
			{
				sub.append(sub.length() > 0 ? "  " : "").append(missing).append(" not in catalog");
			}
		}
		if (sub.length() > 0)
		{
			final JLabel tag = new JLabel(sub.toString());
			tag.setForeground(Color.GRAY);
			text.add(tag);
		}
		row.add(text, BorderLayout.CENTER);

		final JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		controls.setOpaque(false);
		if (isActive)
		{
			final boolean filterOn = actions.isFilterOn();
			final JButton filter = controlButton(filterOn ? FILTER_ON_ICON : FILTER_OFF_ICON,
				filterOn
					? "Showing only this loadout's items - click to show the full catalog"
					: "Filter the catalog to only this loadout's items");
			filter.addActionListener(e -> actions.toggleFilter());
			controls.add(filter);

			final JButton stop = controlButton(STOP_ICON, "Stop comparing");
			stop.addActionListener(e -> actions.stop());
			controls.add(stop);
		}
		final JButton menu = controlButton(MENU_ICON, "Loadout options");
		menu.addActionListener(e -> showMenu(loadout, menu));
		controls.add(menu);

		// Wrap in GridBag so the fixed-height controls center vertically in the row.
		final JPanel controlsWrap = new JPanel(new GridBagLayout());
		controlsWrap.setOpaque(false);
		controlsWrap.add(controls);
		row.add(controlsWrap, BorderLayout.EAST);

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				actions.load(loadout);
			}
		});

		return row;
	}

	private void showMenu(Loadout loadout, Component anchor)
	{
		final JPopupMenu popup = new JPopupMenu();

		final JMenuItem rename = new JMenuItem("Rename");
		rename.addActionListener(e -> actions.rename(loadout));
		popup.add(rename);

		final JMenuItem update = new JMenuItem("Update from current setup");
		update.setEnabled(actions.isBuilderOpen());
		update.addActionListener(e -> actions.updateFromSetup(loadout));
		popup.add(update);

		final JMenuItem copyCode = new JMenuItem("Copy loadout code");
		copyCode.addActionListener(e -> actions.copyCode(loadout));
		popup.add(copyCode);

		final JMenuItem delete = new JMenuItem("Delete");
		delete.addActionListener(e -> actions.delete(loadout));
		popup.add(delete);

		popup.show(anchor, 0, anchor.getHeight());
	}

	private static String shortSpellbook(String label)
	{
		if (label == null || label.trim().isEmpty())
		{
			return null;
		}
		final String t = label.trim();
		final int space = t.indexOf(' ');
		return space > 0 ? t.substring(0, space) : t;
	}

	/** A fixed-size square icon button so the stop and options controls match exactly. */
	private static JButton controlButton(Icon icon, String tooltip)
	{
		final JButton b = new JButton(icon);
		b.setToolTipText(tooltip);
		b.setMargin(new java.awt.Insets(0, 0, 0, 0));
		b.setPreferredSize(CONTROL_BTN_SIZE);
		b.setMinimumSize(CONTROL_BTN_SIZE);
		b.setMaximumSize(CONTROL_BTN_SIZE);
		return b;
	}

	private static BufferedImage iconCanvas()
	{
		return new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
	}

	private static Icon stopIcon()
	{
		final int side = 6;
		final int off = (ICON_SIZE - side) / 2;
		final BufferedImage img = iconCanvas();
		final Graphics2D g = img.createGraphics();
		g.setColor(ICON_FG);
		g.fillRect(off, off, side, side);
		g.dispose();
		return new ImageIcon(img);
	}

	private static Icon filterIcon(boolean on)
	{
		// A funnel: wide mouth at top tapering to a short stem.
		final int[] xs = {0, ICON_SIZE, ICON_SIZE - 3, ICON_SIZE - 3, 3, 3};
		final int[] ys = {1, 1, 4, ICON_SIZE, ICON_SIZE, 4};
		final Polygon funnel = new Polygon(xs, ys, xs.length);
		final BufferedImage img = iconCanvas();
		final Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(on ? ICON_FG : new Color(110, 110, 110));
		if (on)
		{
			g.fill(funnel);
		}
		else
		{
			g.draw(funnel);
		}
		g.dispose();
		return new ImageIcon(img);
	}

	private static Icon menuIcon()
	{
		final int dot = 2;
		final int gap = 1;
		final int totalW = dot * 3 + gap * 2;
		final int x0 = (ICON_SIZE - totalW) / 2;
		final int y = (ICON_SIZE - dot) / 2;
		final BufferedImage img = iconCanvas();
		final Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(ICON_FG);
		for (int i = 0; i < 3; i++)
		{
			g.fillOval(x0 + i * (dot + gap), y, dot, dot);
		}
		g.dispose();
		return new ImageIcon(img);
	}
}
