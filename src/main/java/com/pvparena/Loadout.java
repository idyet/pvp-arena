package com.pvparena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A saved, named, persisted snapshot of a {@link Setup} (worn equipment, inventory
 * supplies + quantities, spellbook) that the plugin stores and lets the player recall.
 * Grouped for display by the {@link Build} it was saved from.
 *
 * <p>Worn and inventory are stored distinctly for snapshot fidelity, but matching
 * flattens them via {@link #bag()} (total possession, see ADR-0002). Serialized to the
 * {@code pvparena} config as JSON.
 */
@Data
@NoArgsConstructor
class Loadout
{
	private String id;
	private String name;
	/** Build value 0/1/2 it was saved from (grouping/display only; see {@link Build}). */
	private int build;
	/** Spellbook display label text (e.g. {@code "Ancient Magicks"}); null if unknown. */
	private String spellbook;
	private List<WornItem> worn = new ArrayList<>();
	private List<InvItem> inventory = new ArrayList<>();
	private long savedAt;

	/**
	 * Flattens worn + inventory into a single {@code itemId -> total quantity} multiset.
	 * Matching is by total possession, ignoring worn-vs-inventory placement (ADR-0002).
	 */
	Map<Integer, Integer> bag()
	{
		final Map<Integer, Integer> bag = new HashMap<>();
		if (worn != null)
		{
			for (WornItem w : worn)
			{
				bag.merge(w.getItemId(), Math.max(1, w.getQuantity()), Integer::sum);
			}
		}
		if (inventory != null)
		{
			for (InvItem i : inventory)
			{
				bag.merge(i.getItemId(), Math.max(1, i.getQuantity()), Integer::sum);
			}
		}
		return bag;
	}

	/** Total item count across worn + inventory (for empty-loadout checks / display). */
	int itemCount()
	{
		int n = 0;
		for (int q : bag().values())
		{
			n += q;
		}
		return n;
	}

	/** A worn-equipment entry. {@code slot} is the equipment slot id; quantity covers ammo. */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	static class WornItem
	{
		private int slot;
		private int itemId;
		private int quantity;
	}

	/** An inventory supply stack. */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	static class InvItem
	{
		private int itemId;
		private int quantity;
	}
}
