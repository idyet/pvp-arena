package com.pvparena;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

/**
 * Feature 3 (Loadouts) — collapses the arena catalog ({@code _NITEMS_LIST}) to just the
 * rows whose item belongs to the active loadout, so the player sees only what to add.
 *
 * <p>Unlike the rest of the feature (overlay-only) this <b>mutates widgets</b> — hide +
 * repack — so it is built to be fully reversible: {@link #clear()} restores every touched
 * widget's hidden flag and original-Y plus the list's scroll height.
 *
 * <p>The catalog is a flat scroll list (verified in-game 2026-06): ~460 rows, ~4 widgets
 * each, all direct dynamic-children of the list, every widget's {@code originalY} an exact
 * multiple of {@value #STRIDE} equal to {@code rowIndex * STRIDE}. Filtering groups children
 * by that band, hides non-loadout rows, repacks survivors into a gap-free {@code 0..n} run
 * and shrinks the scroll height. The game rebuilds the list on its own (item add/remove,
 * build switch), resetting our work; {@link #maintain} re-applies idempotently — a
 * scroll-height check short-circuits when the list is still filtered — so the caller can
 * poll it every client tick. All methods must run on the client thread.
 */
@Singleton
class CatalogFilter
{
	private static final int STRIDE = 32;

	private final Client client;

	/** Pre-mutation state of one touched widget, for {@link #restore()}. */
	private static final class Saved
	{
		final Widget widget;
		final boolean hidden;
		final int originalY;

		Saved(Widget widget)
		{
			this.widget = widget;
			this.hidden = widget.isSelfHidden();
			this.originalY = widget.getOriginalY();
		}
	}

	private final List<Saved> saved = new ArrayList<>();
	private boolean applied;
	private int appliedBuild = -1;
	private List<Integer> appliedKeep;
	private int appliedListId = -1;
	private int appliedScrollHeight = -1;
	private int originalScrollHeight = -1;

	@Inject
	CatalogFilter(Client client)
	{
		this.client = client;
	}

	/**
	 * Ensures the {@code build} catalog shows only rows whose item is in {@code orderedKeep},
	 * repacked into that exact order (index 0 nearest the top). Cheap and idempotent when
	 * already applied to the same context; re-applies after the game rebuilds the list.
	 */
	void maintain(ArenaWidgets.Screen s, int build, List<Integer> orderedKeep)
	{
		if (orderedKeep == null || build < 0 || build >= s.itemsList.length)
		{
			return;
		}
		final Widget list = client.getWidget(s.itemsList[build]);
		if (list == null)
		{
			return;
		}

		if (applied)
		{
			if (build != appliedBuild || !orderedKeep.equals(appliedKeep))
			{
				restore(); // different context/order: undo (self-guards staleness), then re-apply
			}
			else if (list.getScrollHeight() == appliedScrollHeight)
			{
				return; // still filtered as intended
			}
			else
			{
				forget(); // same context but the game rebuilt: cached widgets are stale
			}
		}

		applyFresh(list, build, orderedKeep);
	}

	/** Restores the catalog if filtered; safe to call every tick when inactive. */
	void clear()
	{
		if (applied)
		{
			restore();
		}
	}

	private void applyFresh(Widget list, int build, List<Integer> orderedKeep)
	{
		final Widget[] kids = list.getDynamicChildren();
		if (kids == null || kids.length == 0)
		{
			return;
		}

		// Desired display rank per item id (lower = nearer the top of the list).
		final Map<Integer, Integer> rank = new HashMap<>();
		for (int i = 0; i < orderedKeep.size(); i++)
		{
			rank.putIfAbsent(orderedKeep.get(i), i);
		}

		// Group children into rows by their stride band, ascending (TreeMap = catalog order).
		final TreeMap<Integer, List<Widget>> rows = new TreeMap<>();
		for (Widget k : kids)
		{
			if (k != null)
			{
				rows.computeIfAbsent(Math.floorDiv(k.getOriginalY(), STRIDE), r -> new ArrayList<>()).add(k);
			}
		}

		// Snapshot every touched widget, hide non-kept rows, collect the kept rows.
		saved.clear();
		originalScrollHeight = list.getScrollHeight();
		final List<List<Widget>> kept = new ArrayList<>();
		for (List<Widget> row : rows.values())
		{
			for (Widget w : row)
			{
				saved.add(new Saved(w));
			}
			if (rank.containsKey(rowItemId(row)))
			{
				kept.add(row);
			}
			else
			{
				for (Widget w : row)
				{
					w.setHidden(true);
				}
			}
		}

		// Repack survivors into a gap-free run, orderedKeep index 0 topmost (verified in-game:
		// smaller originalY renders nearer the top).
		kept.sort(Comparator.comparingInt(row -> rank.getOrDefault(rowItemId(row), Integer.MAX_VALUE)));
		int newIndex = 0;
		for (List<Widget> row : kept)
		{
			final int band = Math.floorDiv(row.get(0).getOriginalY(), STRIDE) * STRIDE;
			for (Widget w : row)
			{
				w.setHidden(false);
				w.setOriginalY(newIndex * STRIDE + (w.getOriginalY() - band));
				w.revalidate();
			}
			newIndex++;
		}

		appliedScrollHeight = newIndex * STRIDE;
		list.setScrollHeight(appliedScrollHeight);
		list.revalidateScroll();

		applied = true;
		appliedBuild = build;
		appliedKeep = new ArrayList<>(orderedKeep);
		appliedListId = list.getId();
	}

	private void restore()
	{
		// Re-fetch live: if the list was rebuilt its scroll height differs from what we set,
		// meaning our cached widgets are detached/stale and the list is already default.
		final Widget live = appliedListId < 0 ? null : client.getWidget(appliedListId);
		if (live == null || live.getScrollHeight() != appliedScrollHeight)
		{
			forget();
			return;
		}

		for (Saved sv : saved)
		{
			sv.widget.setHidden(sv.hidden);
			sv.widget.setOriginalY(sv.originalY);
			sv.widget.revalidate();
		}
		if (originalScrollHeight >= 0)
		{
			live.setScrollHeight(originalScrollHeight);
			live.revalidateScroll();
		}
		forget();
	}

	private void forget()
	{
		saved.clear();
		applied = false;
		appliedBuild = -1;
		appliedKeep = null;
		appliedListId = -1;
		appliedScrollHeight = -1;
		originalScrollHeight = -1;
	}

	private static int rowItemId(List<Widget> row)
	{
		for (Widget w : row)
		{
			if (w.getItemId() > 0)
			{
				return w.getItemId();
			}
		}
		return -1;
	}
}
