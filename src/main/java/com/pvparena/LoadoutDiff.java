package com.pvparena;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure diff between a target {@link Loadout} bag and the current {@link Setup} bag
 * (total possession, see ADR-0002 and DESIGN.md "Feature 3 -> Matching / diff").
 * Unit-tested; no client dependencies.
 */
class LoadoutDiff
{
	/** itemId -&gt; how many MORE are needed (drives catalog "to add" highlights). */
	private final Map<Integer, Integer> toAdd;
	/** itemId -&gt; how many too MANY are present (drives setup excess marks). */
	private final Map<Integer, Integer> excess;
	private final boolean spellbookMismatch;

	private LoadoutDiff(Map<Integer, Integer> toAdd, Map<Integer, Integer> excess, boolean spellbookMismatch)
	{
		this.toAdd = toAdd;
		this.excess = excess;
		this.spellbookMismatch = spellbookMismatch;
	}

	Map<Integer, Integer> toAdd()
	{
		return Collections.unmodifiableMap(toAdd);
	}

	Map<Integer, Integer> excess()
	{
		return Collections.unmodifiableMap(excess);
	}

	boolean spellbookMismatch()
	{
		return spellbookMismatch;
	}

	/** How many more of {@code itemId} to add (0 if none). */
	int needFor(int itemId)
	{
		return toAdd.getOrDefault(itemId, 0);
	}

	/** Whether {@code itemId} is present in excess / unwanted in the setup. */
	boolean isExcess(int itemId)
	{
		return excess.containsKey(itemId);
	}

	/**
	 * Full match: nothing to add, nothing in excess, and the spellbook matches. The
	 * trigger for auto-clearing the {@link LoadoutManager#getActive() active loadout}.
	 */
	boolean isFullMatch()
	{
		return toAdd.isEmpty() && excess.isEmpty() && !spellbookMismatch;
	}

	/**
	 * @param want          target bag (itemId -&gt; qty) from the loadout
	 * @param have          current bag (itemId -&gt; qty) from the live setup
	 * @param wantSpellbook loadout spellbook label (nullable)
	 * @param haveSpellbook current setup spellbook label (nullable)
	 */
	static LoadoutDiff compute(Map<Integer, Integer> want, Map<Integer, Integer> have,
		String wantSpellbook, String haveSpellbook)
	{
		final Map<Integer, Integer> toAdd = new HashMap<>();
		final Map<Integer, Integer> excess = new HashMap<>();

		for (Map.Entry<Integer, Integer> e : want.entrySet())
		{
			final int need = e.getValue() - have.getOrDefault(e.getKey(), 0);
			if (need > 0)
			{
				toAdd.put(e.getKey(), need);
			}
		}

		for (Map.Entry<Integer, Integer> e : have.entrySet())
		{
			final int over = e.getValue() - want.getOrDefault(e.getKey(), 0);
			if (over > 0)
			{
				excess.put(e.getKey(), over);
			}
		}

		// Reuse Feature 2's case-insensitive, fail-quiet label compare: a null/blank on
		// either side yields no mismatch (we never flag an unknown spellbook).
		final boolean spellbookMismatch = SpellbookMismatchOverlay.isMismatch(wantSpellbook, haveSpellbook);

		return new LoadoutDiff(toAdd, excess, spellbookMismatch);
	}
}
