package com.pvparena;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LoadoutDiffTest
{
	private static final int BREW = 6685;
	private static final int PPOT = 2434;
	private static final int KARAMBWAN = 3144;
	private static final int DDS = 5698;

	private static final String ANCIENT = "Ancient Magicks";
	private static final String STANDARD = "Standard Spellbook";

	private static Map<Integer, Integer> bag(int... itemThenQtyPairs)
	{
		final Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < itemThenQtyPairs.length; i += 2)
		{
			m.put(itemThenQtyPairs[i], itemThenQtyPairs[i + 1]);
		}
		return m;
	}

	@Test
	public void needsMoreOfAnItem()
	{
		// Q4 #1: want 6 brews, have 2 -> add 4.
		final LoadoutDiff diff = LoadoutDiff.compute(bag(BREW, 6), bag(BREW, 2), ANCIENT, ANCIENT);
		assertEquals(4, diff.needFor(BREW));
		assertTrue(diff.excess().isEmpty());
		assertFalse(diff.isFullMatch());
	}

	@Test
	public void tooManyOfAnItemIsExcess()
	{
		// Q4 #2: want 6 brews, have 8 -> 2 excess, nothing to add.
		final LoadoutDiff diff = LoadoutDiff.compute(bag(BREW, 6), bag(BREW, 8), ANCIENT, ANCIENT);
		assertTrue(diff.toAdd().isEmpty());
		assertEquals(Integer.valueOf(2), diff.excess().get(BREW));
		assertTrue(diff.isExcess(BREW));
		assertFalse(diff.isFullMatch());
	}

	@Test
	public void unwantedItemIsExcess()
	{
		// Q4 #3: a karambwan the loadout does not include.
		final LoadoutDiff diff = LoadoutDiff.compute(bag(BREW, 6), bag(BREW, 6, KARAMBWAN, 1), ANCIENT, ANCIENT);
		assertTrue(diff.toAdd().isEmpty());
		assertEquals(Integer.valueOf(1), diff.excess().get(KARAMBWAN));
		assertFalse(diff.isFullMatch());
	}

	@Test
	public void exactMatchIsFullMatch()
	{
		final LoadoutDiff diff = LoadoutDiff.compute(bag(BREW, 6, PPOT, 4), bag(BREW, 6, PPOT, 4), ANCIENT, ANCIENT);
		assertTrue(diff.toAdd().isEmpty());
		assertTrue(diff.excess().isEmpty());
		assertFalse(diff.spellbookMismatch());
		assertTrue(diff.isFullMatch());
	}

	@Test
	public void spellbookMismatchBlocksFullMatch()
	{
		// Q14 (B): items match but spellbook differs -> not a full match.
		final LoadoutDiff diff = LoadoutDiff.compute(bag(BREW, 6), bag(BREW, 6), ANCIENT, STANDARD);
		assertTrue(diff.toAdd().isEmpty());
		assertTrue(diff.excess().isEmpty());
		assertTrue(diff.spellbookMismatch());
		assertFalse(diff.isFullMatch());
	}

	@Test
	public void unknownSpellbookIsNotFlagged()
	{
		// Fail-quiet: a null loadout spellbook never flags, so items-equal == full match.
		final LoadoutDiff diff = LoadoutDiff.compute(bag(BREW, 6), bag(BREW, 6), null, STANDARD);
		assertFalse(diff.spellbookMismatch());
		assertTrue(diff.isFullMatch());
	}

	@Test
	public void bagFlattensWornAndInventory()
	{
		// Q4 #4 (option b): a DDS worn and a DDS in inventory produce the same bag, so
		// either placement satisfies "want 1 DDS" -> full match.
		final Loadout worn = new Loadout();
		worn.setWorn(Collections.singletonList(new Loadout.WornItem(3, DDS, 1)));

		final Loadout inInv = new Loadout();
		inInv.setInventory(Collections.singletonList(new Loadout.InvItem(DDS, 1)));

		assertEquals(worn.bag(), inInv.bag());
		assertTrue(LoadoutDiff.compute(worn.bag(), inInv.bag(), null, null).isFullMatch());
	}

	@Test
	public void bagSumsDuplicateItemAcrossWornAndInventory()
	{
		final Loadout l = new Loadout();
		l.setWorn(Collections.singletonList(new Loadout.WornItem(13, BREW, 1)));
		l.setInventory(Arrays.asList(new Loadout.InvItem(BREW, 5), new Loadout.InvItem(PPOT, 4)));
		assertEquals(Integer.valueOf(6), l.bag().get(BREW));
		assertEquals(Integer.valueOf(4), l.bag().get(PPOT));
		assertEquals(10, l.itemCount());
	}
}
