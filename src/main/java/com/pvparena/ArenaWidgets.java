package com.pvparena;

import net.runelite.api.gameval.InterfaceID.PvpArenaStagingareaSupplies;
import net.runelite.api.gameval.InterfaceID.PvpArenaUnrankedduel;

/**
 * Centralised, per-build component ids for the two {@link Setup} builders, so every
 * widget assumption the Loadouts feature makes lives in one place.
 *
 * <p><b>VERIFY IN-GAME (see DESIGN.md "Feature 3" checklist).</b> Worn slots and the
 * spellbook display are reused from the proven Feature 2 path and are reliable. The
 * catalog is {@code _NITEMS_LIST} ({@link Screen#itemsList}) — confirmed in-game: the
 * "to add" highlights land on it. The catalog and the owned inventory are <b>separate</b>
 * areas: the owned inventory grid is {@code _NINVENTORY} ({@link Screen#inventory}), read
 * directly (DFS from that widget), not by exclusion — it is a sibling of {@code universe},
 * so a DFS from the root never reached it and inventory always read empty.
 */
final class ArenaWidgets
{
	/** Equipment slot index per worn-slot widget, in {@code _NSLOTn} order (n has gaps). */
	static final int[] WORN_SLOT_INDEX = {0, 1, 2, 3, 4, 5, 7, 9, 10, 12, 13};

	/** A builder screen, with per-build ({@code 0}/{@code 1}/{@code 2}) widget ids. */
	static final class Screen
	{
		final int universe;
		final int[] wornRoot;          // _NWORN — non-hidden => this build is shown
		final int[][] wornSlots;       // [build][WORN_SLOT_INDEX position] => _NSLOTn
		final int[] spellbookDisplay;  // _NSPELLBOOK_DISPLAY
		final int[] itemsList;         // _NITEMS_LIST — the catalog of selectable items
		final int[] inventory;         // _NINVENTORY — the owned supplies grid
		final int[] seek;              // _NSEEK — the catalog search input

		private Screen(int universe, int[] wornRoot, int[][] wornSlots, int[] spellbookDisplay,
			int[] itemsList, int[] inventory, int[] seek)
		{
			this.universe = universe;
			this.wornRoot = wornRoot;
			this.wornSlots = wornSlots;
			this.spellbookDisplay = spellbookDisplay;
			this.itemsList = itemsList;
			this.inventory = inventory;
			this.seek = seek;
		}
	}

	static final Screen UNRANKED = new Screen(
		PvpArenaUnrankedduel.UNIVERSE,
		new int[]{
			PvpArenaUnrankedduel._0WORN,
			PvpArenaUnrankedduel._1WORN,
			PvpArenaUnrankedduel._2WORN,
		},
		new int[][]{
			{
				PvpArenaUnrankedduel._0SLOT0, PvpArenaUnrankedduel._0SLOT1, PvpArenaUnrankedduel._0SLOT2,
				PvpArenaUnrankedduel._0SLOT3, PvpArenaUnrankedduel._0SLOT4, PvpArenaUnrankedduel._0SLOT5,
				PvpArenaUnrankedduel._0SLOT7, PvpArenaUnrankedduel._0SLOT9, PvpArenaUnrankedduel._0SLOT10,
				PvpArenaUnrankedduel._0SLOT12, PvpArenaUnrankedduel._0SLOT13,
			},
			{
				PvpArenaUnrankedduel._1SLOT0, PvpArenaUnrankedduel._1SLOT1, PvpArenaUnrankedduel._1SLOT2,
				PvpArenaUnrankedduel._1SLOT3, PvpArenaUnrankedduel._1SLOT4, PvpArenaUnrankedduel._1SLOT5,
				PvpArenaUnrankedduel._1SLOT7, PvpArenaUnrankedduel._1SLOT9, PvpArenaUnrankedduel._1SLOT10,
				PvpArenaUnrankedduel._1SLOT12, PvpArenaUnrankedduel._1SLOT13,
			},
			{
				PvpArenaUnrankedduel._2SLOT0, PvpArenaUnrankedduel._2SLOT1, PvpArenaUnrankedduel._2SLOT2,
				PvpArenaUnrankedduel._2SLOT3, PvpArenaUnrankedduel._2SLOT4, PvpArenaUnrankedduel._2SLOT5,
				PvpArenaUnrankedduel._2SLOT7, PvpArenaUnrankedduel._2SLOT9, PvpArenaUnrankedduel._2SLOT10,
				PvpArenaUnrankedduel._2SLOT12, PvpArenaUnrankedduel._2SLOT13,
			},
		},
		new int[]{
			PvpArenaUnrankedduel._0SPELLBOOK_DISPLAY,
			PvpArenaUnrankedduel._1SPELLBOOK_DISPLAY,
			PvpArenaUnrankedduel._2SPELLBOOK_DISPLAY,
		},
		new int[]{
			PvpArenaUnrankedduel._0ITEMS_LIST,
			PvpArenaUnrankedduel._1ITEMS_LIST,
			PvpArenaUnrankedduel._2ITEMS_LIST,
		},
		new int[]{
			PvpArenaUnrankedduel._0INVENTORY,
			PvpArenaUnrankedduel._1INVENTORY,
			PvpArenaUnrankedduel._2INVENTORY,
		},
		new int[]{
			PvpArenaUnrankedduel._0SEEK,
			PvpArenaUnrankedduel._1SEEK,
			PvpArenaUnrankedduel._2SEEK,
		});

	static final Screen STAGING = new Screen(
		PvpArenaStagingareaSupplies.UNIVERSE,
		new int[]{
			PvpArenaStagingareaSupplies._0WORN,
			PvpArenaStagingareaSupplies._1WORN,
			PvpArenaStagingareaSupplies._2WORN,
		},
		new int[][]{
			{
				PvpArenaStagingareaSupplies._0SLOT0, PvpArenaStagingareaSupplies._0SLOT1, PvpArenaStagingareaSupplies._0SLOT2,
				PvpArenaStagingareaSupplies._0SLOT3, PvpArenaStagingareaSupplies._0SLOT4, PvpArenaStagingareaSupplies._0SLOT5,
				PvpArenaStagingareaSupplies._0SLOT7, PvpArenaStagingareaSupplies._0SLOT9, PvpArenaStagingareaSupplies._0SLOT10,
				PvpArenaStagingareaSupplies._0SLOT12, PvpArenaStagingareaSupplies._0SLOT13,
			},
			{
				PvpArenaStagingareaSupplies._1SLOT0, PvpArenaStagingareaSupplies._1SLOT1, PvpArenaStagingareaSupplies._1SLOT2,
				PvpArenaStagingareaSupplies._1SLOT3, PvpArenaStagingareaSupplies._1SLOT4, PvpArenaStagingareaSupplies._1SLOT5,
				PvpArenaStagingareaSupplies._1SLOT7, PvpArenaStagingareaSupplies._1SLOT9, PvpArenaStagingareaSupplies._1SLOT10,
				PvpArenaStagingareaSupplies._1SLOT12, PvpArenaStagingareaSupplies._1SLOT13,
			},
			{
				PvpArenaStagingareaSupplies._2SLOT0, PvpArenaStagingareaSupplies._2SLOT1, PvpArenaStagingareaSupplies._2SLOT2,
				PvpArenaStagingareaSupplies._2SLOT3, PvpArenaStagingareaSupplies._2SLOT4, PvpArenaStagingareaSupplies._2SLOT5,
				PvpArenaStagingareaSupplies._2SLOT7, PvpArenaStagingareaSupplies._2SLOT9, PvpArenaStagingareaSupplies._2SLOT10,
				PvpArenaStagingareaSupplies._2SLOT12, PvpArenaStagingareaSupplies._2SLOT13,
			},
		},
		new int[]{
			PvpArenaStagingareaSupplies._0SPELLBOOK_DISPLAY,
			PvpArenaStagingareaSupplies._1SPELLBOOK_DISPLAY,
			PvpArenaStagingareaSupplies._2SPELLBOOK_DISPLAY,
		},
		new int[]{
			PvpArenaStagingareaSupplies._0ITEMS_LIST,
			PvpArenaStagingareaSupplies._1ITEMS_LIST,
			PvpArenaStagingareaSupplies._2ITEMS_LIST,
		},
		new int[]{
			PvpArenaStagingareaSupplies._0INVENTORY,
			PvpArenaStagingareaSupplies._1INVENTORY,
			PvpArenaStagingareaSupplies._2INVENTORY,
		},
		new int[]{
			PvpArenaStagingareaSupplies._0SEEK,
			PvpArenaStagingareaSupplies._1SEEK,
			PvpArenaStagingareaSupplies._2SEEK,
		});

	static final Screen[] SCREENS = {UNRANKED, STAGING};

	/** Whether {@code componentId} is a catalog search ({@code _NSEEK}) button of either screen. */
	static boolean isSeekWidget(int componentId)
	{
		for (Screen s : SCREENS)
		{
			for (int id : s.seek)
			{
				if (id == componentId)
				{
					return true;
				}
			}
		}
		return false;
	}

	private ArenaWidgets()
	{
	}
}
