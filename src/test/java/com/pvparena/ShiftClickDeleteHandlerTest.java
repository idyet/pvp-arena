package com.pvparena;

import static com.pvparena.ShiftClickDeleteHandler.Decision.DISCARD_LEFT;
import static com.pvparena.ShiftClickDeleteHandler.Decision.EXAMINE_LEFT;
import static com.pvparena.ShiftClickDeleteHandler.Decision.UNCHANGED;
import static com.pvparena.ShiftClickDeleteHandler.desiredLeftClick;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShiftClickDeleteHandlerTest
{
	@Test
	public void nonDiscardItemNeverChanges()
	{
		// Default left-click is not Discard: leave it regardless of Shift / Examine.
		assertEquals(UNCHANGED, desiredLeftClick(false, false, false));
		assertEquals(UNCHANGED, desiredLeftClick(false, true, false));
		assertEquals(UNCHANGED, desiredLeftClick(false, false, true));
		assertEquals(UNCHANGED, desiredLeftClick(false, true, true));
	}

	@Test
	public void discardItemWithoutShiftPromotesExamine()
	{
		assertEquals(EXAMINE_LEFT, desiredLeftClick(true, true, false));
	}

	@Test
	public void discardItemWithShiftKeepsDiscard()
	{
		assertEquals(DISCARD_LEFT, desiredLeftClick(true, true, true));
	}

	@Test
	public void discardItemWithoutExamineFallsBackToUnchanged()
	{
		// Nothing benign to promote, so leave Discard rather than invent a no-op.
		assertEquals(UNCHANGED, desiredLeftClick(true, false, false));
	}

	@Test
	public void discardItemWithoutExamineStillDiscardsOnShift()
	{
		assertEquals(DISCARD_LEFT, desiredLeftClick(true, false, true));
	}
}
