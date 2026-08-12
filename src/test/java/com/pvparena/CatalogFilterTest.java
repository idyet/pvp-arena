package com.pvparena;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Unit spec for {@link CatalogFilter#itemIdSharingRow}, the pure lookup behind resolving which
 * catalog item an {@code Add} click targets (Feature 3). Confirmed in-game 2026-08-11: the click's
 * param0 is the dense index of a bare op hotspot (item id -1) that shares its {@code originalY} with
 * the row's icon widget; repack moves a row's widgets together and hides non-loadout rows, so the
 * clicked item is the non-hidden icon on the same {@code originalY}. Pure like {@link PendingAddsTest}.
 */
public class CatalogFilterTest
{
	private static final int DDS = 5698;
	private static final int SHARK = 385;
	private static final boolean SHOWN = false;
	private static final boolean HIDDEN = true;

	@Test
	public void resolvesTheIconSharingTheClickedRowY()
	{
		// One visible row at y=64: bare hotspot (-1) + icon (DDS) both on that originalY.
		final int[] ys = {64, 64};
		final int[] itemIds = {-1, DDS};
		final boolean[] hidden = {SHOWN, SHOWN};
		assertEquals(DDS, CatalogFilter.itemIdSharingRow(64, ys, itemIds, hidden));
	}

	@Test
	public void picksTheClickedRowNotAnotherVisibleRow()
	{
		// Two visible rows repacked to distinct Ys; the clicked hotspot's Y selects exactly one.
		final int[] ys = {0, 0, 32, 32};
		final int[] itemIds = {-1, DDS, -1, SHARK};
		final boolean[] hidden = {SHOWN, SHOWN, SHOWN, SHOWN};
		assertEquals(DDS, CatalogFilter.itemIdSharingRow(0, ys, itemIds, hidden));
		assertEquals(SHARK, CatalogFilter.itemIdSharingRow(32, ys, itemIds, hidden));
	}

	@Test
	public void skipsAHiddenRowCollidingOnTheSameY()
	{
		// A hidden (filtered-out) item kept its original Y, which collides with the clicked
		// visible row's repacked Y; only the visible icon must be returned.
		final int[] ys = {32, 32, 32};
		final int[] itemIds = {SHARK, -1, DDS}; // SHARK hidden, DDS the visible clicked row
		final boolean[] hidden = {HIDDEN, SHOWN, SHOWN};
		assertEquals(DDS, CatalogFilter.itemIdSharingRow(32, ys, itemIds, hidden));
	}

	@Test
	public void returnsMinusOneWhenNoVisibleItemSharesTheRow()
	{
		// Row holds only the hotspot; the only item on that Y is hidden.
		final int[] ys = {96, 96};
		final int[] itemIds = {-1, DDS};
		final boolean[] hidden = {SHOWN, HIDDEN};
		assertEquals(-1, CatalogFilter.itemIdSharingRow(96, ys, itemIds, hidden));
	}

	@Test
	public void returnsMinusOneForEmptyRows()
	{
		assertEquals(-1, CatalogFilter.itemIdSharingRow(0, new int[0], new int[0], new boolean[0]));
	}
}
