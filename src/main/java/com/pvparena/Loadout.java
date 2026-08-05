package com.pvparena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

	/**
	 * Collapses duplicate inventory entries (same {@code itemId}) into one, summing quantities,
	 * so a bag holding four separate sharks becomes a single {@code qty 4} entry. Since
	 * {@link InvItem} carries no slot/position, duplicate entries are pure redundancy — this is
	 * a lossless normalization that shortens serialized codes (see ADR-0004).
	 *
	 * <p>Preserves first-seen order for deterministic, byte-identical output, and clamps each
	 * entry to {@code >= 1} before summing — exactly what {@link #bag()} does per entry — so a
	 * collapsed list feeds {@code bag()} identically to the uncollapsed original for every input
	 * (including a hand-forged code with a non-positive quantity). Pure and idempotent; never
	 * mutates {@code items}.
	 */
	static List<InvItem> collapseInventory(List<InvItem> items)
	{
		final Map<Integer, Integer> byId = new LinkedHashMap<>();
		if (items != null)
		{
			for (InvItem i : items)
			{
				byId.merge(i.getItemId(), Math.max(1, i.getQuantity()), Integer::sum);
			}
		}

		final List<InvItem> out = new ArrayList<>(byId.size());
		for (Map.Entry<Integer, Integer> e : byId.entrySet())
		{
			out.add(new InvItem(e.getKey(), e.getValue()));
		}
		return out;
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
