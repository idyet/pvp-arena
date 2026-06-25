package com.pvparena;

import static com.pvparena.SpellbookMismatchOverlay.isMismatch;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpellbookMismatchTest
{
	@Test
	public void sameLabelIsNotAMismatch()
	{
		assertFalse(isMismatch("Standard Spellbook", "Standard Spellbook"));
	}

	@Test
	public void differentLabelsAreAMismatch()
	{
		assertTrue(isMismatch("Ancient Magicks", "Standard Spellbook"));
		assertTrue(isMismatch("Lunar Spellbook", "Standard Spellbook"));
	}

	@Test
	public void compareIsCaseInsensitiveAndTrimmed()
	{
		assertFalse(isMismatch("standard spellbook", "Standard Spellbook"));
		assertFalse(isMismatch("  Ancient Magicks  ", "Ancient Magicks"));
	}

	@Test
	public void nullLabelFailsQuiet()
	{
		assertFalse(isMismatch(null, "Standard Spellbook"));
		assertFalse(isMismatch("Standard Spellbook", null));
		assertFalse(isMismatch(null, null));
	}

	@Test
	public void blankLabelFailsQuiet()
	{
		assertFalse(isMismatch("", "Standard Spellbook"));
		assertFalse(isMismatch("Standard Spellbook", "   "));
	}
}
