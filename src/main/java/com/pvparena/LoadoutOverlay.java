package com.pvparena;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Feature 3 — paints the load diff while a {@link Setup builder} is open and a
 * {@link LoadoutManager#getActive() loadout is active}: green outline (covering the
 * whole catalog option, icon + text) with a required-count when more than one is
 * needed, and a red outline over excess/unwanted setup items and a differing spellbook.
 * While the catalog is filtered ({@link LoadoutManager#isFilterOn()}) the to-add outline
 * is suppressed — every visible row already belongs to the loadout — leaving just the count.
 * Re-evaluates every frame, so counts fall and outlines clear as the player adds items.
 * Auto-clears the active loadout on full match. Overlay-only, fully reversible.
 */
class LoadoutOverlay extends Overlay
{
	private static final Color ADD_COLOR = new Color(0, 200, 0);
	private static final Color EXCESS_COLOR = Color.RED;
	private static final Stroke STROKE = new BasicStroke(2f);
	private static final Font COUNT_FONT = FontManager.getRunescapeSmallFont();
	private static final long MATCH_CONFIRM_MS = 2500L;

	private final Client client;
	private final PvpArenaConfig config;
	private final PvpArenaPlugin plugin;
	private final LoadoutManager manager;
	private final SetupReader reader;
	private final ItemManager itemManager;

	/** Needed items the current catalog had no row for; read by the panel for its note. */
	@Getter
	private volatile int lastUnlocatable;
	@Getter
	private volatile String lastUnlocatableActiveId;

	private long matchConfirmUntil;
	/** Id the match-tracking is armed for, and whether it has been seen unmatched since. */
	private String armedId;
	private boolean sawUnmatched;

	@Inject
	LoadoutOverlay(Client client, PvpArenaConfig config, PvpArenaPlugin plugin,
		LoadoutManager manager, SetupReader reader, ItemManager itemManager)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		this.manager = manager;
		this.reader = reader;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.loadouts() || !plugin.inPvpArena())
		{
			return null;
		}

		final ArenaWidgets.Screen screen = reader.openScreen();
		if (screen == null)
		{
			lastUnlocatable = 0;
			return null;
		}

		// Brief "matched" confirmation persists after auto-clear (when active is null).
		if (System.currentTimeMillis() < matchConfirmUntil)
		{
			drawMatchedConfirmation(g, screen);
		}

		final Loadout active = manager.getActive();
		if (active == null)
		{
			lastUnlocatable = 0;
			armedId = null;
			sawUnmatched = false;
			return null;
		}

		// Re-arm match tracking whenever a different loadout is loaded.
		if (!active.getId().equals(armedId))
		{
			armedId = active.getId();
			sawUnmatched = false;
		}

		final int build = reader.activeBuild(screen);
		final List<SetupReader.ItemWidget> worn = reader.wornItems(screen, build);
		final List<SetupReader.ItemWidget> inventory = reader.inventoryItems(screen, build);
		final List<SetupReader.ItemWidget> catalog = reader.catalogItems(screen, build);

		final Map<Integer, Integer> current = new HashMap<>();
		for (SetupReader.ItemWidget iw : worn)
		{
			current.merge(iw.itemId, iw.quantity, Integer::sum);
		}
		for (SetupReader.ItemWidget iw : inventory)
		{
			current.merge(iw.itemId, iw.quantity, Integer::sum);
		}

		final String currentSpellbook = reader.spellbookLabel(screen, build);
		final LoadoutDiff diff = LoadoutDiff.compute(active.bag(), current, active.getSpellbook(), currentSpellbook);

		g.setStroke(STROKE);

		// To add: green outline over the whole option + count (only when >1), clipped to list.
		// While the catalog is filtered to just the loadout's items the outline is redundant
		// (every visible row already belongs to the loadout) and only clutters the list, so
		// suppress it there and keep the count alone.
		final boolean filtered = manager.isFilterOn();
		final Set<Integer> located = new HashSet<>();
		final Widget list = client.getWidget(screen.itemsList[build]);
		final Shape oldClip = g.getClip();
		if (list != null && !list.isHidden() && list.getBounds() != null)
		{
			g.setClip(list.getBounds());
		}
		for (SetupReader.ItemWidget row : catalog)
		{
			final int need = diff.needFor(row.itemId);
			if (need > 0 && row.widget != null && !row.widget.isHidden())
			{
				located.add(row.itemId);
				final Rectangle rowBounds = row.widget.getBounds();
				if (rowBounds == null)
				{
					continue;
				}
				if (!filtered)
				{
					final Rectangle bounds = optionBounds(row.widget);
					if (bounds != null)
					{
						g.setColor(ADD_COLOR);
						g.draw(bounds);
					}
				}
				if (need > 1)
				{
					drawCount(g, rowBounds, need);
				}
			}
		}
		g.setClip(oldClip);

		// Excess / unwanted: red outline only over the surplus that must be discarded to match
		// the loadout (wants 3 brews, setup has 5 -> mark just the last 2), not every copy. The
		// diff's per-item excess count is the budget. Inventory is spent first, walked bottom-up
		// so the trailing slots are marked, then worn — an equipped item is kept over a bag spare.
		// Ammo (bolts/arrows/darts) is never flagged as inventory excess: the equipped and
		// inventory forms report different item ids, so a stack sitting in the bag reads as
		// unwanted even though equipping it satisfies the loadout (see ADR-0002 / DESIGN.md
		// "variant surprises"). Suppress the red mark rather than confuse the player.
		final Map<Integer, Integer> remaining = new HashMap<>(diff.excess());
		final List<SetupReader.ItemWidget> orderedInv = new ArrayList<>(inventory);
		orderedInv.removeIf(iw -> isAmmo(iw.itemId));
		orderedInv.sort(Comparator.comparingInt(LoadoutOverlay::rowY).thenComparingInt(LoadoutOverlay::rowX));
		markExcess(g, orderedInv, remaining);
		markExcess(g, worn, remaining);

		// Spellbook differs.
		if (diff.spellbookMismatch())
		{
			outline(g, reader.spellbookWidget(screen, build), EXCESS_COLOR);
		}

		// Needed items with no catalog row to click (e.g. not available for this build).
		int unlocatable = 0;
		for (int itemId : diff.toAdd().keySet())
		{
			if (!located.contains(itemId))
			{
				unlocatable++;
			}
		}
		lastUnlocatable = unlocatable;
		lastUnlocatableActiveId = active.getId();

		// Only auto-clear once the player has actually worked toward the loadout: loading
		// an already-matching loadout (e.g. the one just saved from this setup) stays armed
		// rather than vanishing instantly.
		if (!diff.isFullMatch())
		{
			sawUnmatched = true;
		}
		else if (sawUnmatched)
		{
			manager.clearActive();
			matchConfirmUntil = System.currentTimeMillis() + MATCH_CONFIRM_MS;
			lastUnlocatable = 0;
			armedId = null;
			sawUnmatched = false;
		}

		return null;
	}

	/**
	 * Outlines only the last {@code remaining[itemId]} copies of each excess item — the exact
	 * surplus over the loadout — walking the list from the end so the trailing slots are marked
	 * and drawing the budget down by each widget's stack size (so one full stack can cover it).
	 */
	private void markExcess(Graphics2D g, List<SetupReader.ItemWidget> items, Map<Integer, Integer> remaining)
	{
		for (int i = items.size() - 1; i >= 0; i--)
		{
			final SetupReader.ItemWidget iw = items.get(i);
			final int left = remaining.getOrDefault(iw.itemId, 0);
			if (left <= 0)
			{
				continue;
			}
			outline(g, iw.widget, EXCESS_COLOR);
			remaining.put(iw.itemId, left - iw.quantity);
		}
	}

	/** Whether {@code itemId} equips to the ammo slot (bolts/arrows/darts/knives/etc.). */
	private boolean isAmmo(int itemId)
	{
		final ItemStats stats = itemManager.getItemStats(itemId);
		return stats != null && stats.getEquipment() != null
			&& stats.getEquipment().getSlot() == EquipmentInventorySlot.AMMO.getSlotIdx();
	}

	private static int rowY(SetupReader.ItemWidget iw)
	{
		final Rectangle b = iw.widget == null ? null : iw.widget.getBounds();
		return b == null ? Integer.MAX_VALUE : b.y;
	}

	private static int rowX(SetupReader.ItemWidget iw)
	{
		final Rectangle b = iw.widget == null ? null : iw.widget.getBounds();
		return b == null ? Integer.MAX_VALUE : b.x;
	}

	private void outline(Graphics2D g, Widget widget, Color color)
	{
		if (widget == null || widget.isHidden())
		{
			return;
		}
		final Rectangle b = widget.getBounds();
		if (b != null)
		{
			g.setColor(color);
			g.draw(b);
		}
	}

	/**
	 * Bounds of a full catalog option: the item icon unioned with the same-row sibling
	 * widgets (the item name text). Falls back to the row-container parent when that is
	 * itself the option, or the icon alone if neither is available.
	 */
	private Rectangle optionBounds(Widget icon)
	{
		final Rectangle ib = icon.getBounds();
		if (ib == null)
		{
			return null;
		}

		final Widget parent = icon.getParent();
		if (parent == null)
		{
			return ib;
		}

		final Rectangle pb = parent.getBounds();
		if (pb != null && pb.width >= ib.width && pb.height <= ib.height * 2 + 6)
		{
			return pb; // the parent is the option row itself
		}

		Rectangle r = new Rectangle(ib);
		r = unionRow(r, parent.getDynamicChildren(), ib);
		r = unionRow(r, parent.getChildren(), ib);
		return r;
	}

	private Rectangle unionRow(Rectangle r, Widget[] kids, Rectangle band)
	{
		if (kids != null)
		{
			for (Widget k : kids)
			{
				if (k == null || k.isHidden())
				{
					continue;
				}
				final Rectangle b = k.getBounds();
				if (b == null)
				{
					continue;
				}
				// Require a majority vertical overlap so only true same-row siblings merge.
				// A bare 1px overlap (e.g. an adjacent row shifted mid-scroll) must not
				// stretch the highlight across two options.
				final int overlap = Math.min(b.y + b.height, band.y + band.height) - Math.max(b.y, band.y);
				if (overlap * 2 >= Math.min(band.height, b.height))
				{
					r = r.union(b);
				}
			}
		}
		return r;
	}

	private void drawCount(Graphics2D g, Rectangle bounds, int n)
	{
		final String t = Integer.toString(n);
		g.setFont(COUNT_FONT);
		final int tw = g.getFontMetrics().stringWidth(t);
		final int x = bounds.x + bounds.width - tw - 1;
		final int y = bounds.y + bounds.height - 1;

		g.setColor(Color.BLACK);
		g.drawString(t, x + 1, y + 1);
		g.setColor(ADD_COLOR);
		g.drawString(t, x, y);
	}

	private void drawMatchedConfirmation(Graphics2D g, ArenaWidgets.Screen screen)
	{
		final Widget root = client.getWidget(screen.universe);
		if (root == null || root.isHidden() || root.getBounds() == null)
		{
			return;
		}
		final Rectangle b = root.getBounds();
		final String t = "Loadout matched";
		g.setFont(COUNT_FONT);
		final int tw = g.getFontMetrics().stringWidth(t);
		final int checkW = 8;
		final int gap = 3;
		final int x = b.x + (b.width - (checkW + gap + tw)) / 2;
		final int y = b.y + 14;

		drawCheck(g, x, y, checkW);

		final int tx = x + checkW + gap;
		g.setColor(Color.BLACK);
		g.drawString(t, tx + 1, y + 1);
		g.setColor(ADD_COLOR);
		g.drawString(t, tx, y);
	}

	/** Vector checkmark (the RuneScape font has no ✓ glyph), green with a black shadow. */
	private void drawCheck(Graphics2D g, int x, int baseline, int size)
	{
		final Object oldAa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		final Stroke oldStroke = g.getStroke();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		final int[] xs = {x, x + size / 3, x + size};
		final int[] ys = {baseline - 3, baseline, baseline - size};

		g.setColor(Color.BLACK);
		g.drawPolyline(new int[]{xs[0] + 1, xs[1] + 1, xs[2] + 1}, new int[]{ys[0] + 1, ys[1] + 1, ys[2] + 1}, 3);
		g.setColor(ADD_COLOR);
		g.drawPolyline(xs, ys, 3);

		g.setStroke(oldStroke);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			oldAa == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : oldAa);
	}
}
