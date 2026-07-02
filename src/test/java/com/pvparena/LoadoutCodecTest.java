package com.pvparena;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class LoadoutCodecTest
{
	private static final int AGS = 11802;
	private static final int BREW = 6685;
	private static final int PPOT = 2434;

	private static final String ANCIENT = "Ancient Magicks";

	private static Loadout sample()
	{
		final Loadout l = new Loadout();
		l.setId("original-id");
		l.setName("Pure rush");
		l.setBuild(2);
		l.setSpellbook(ANCIENT);
		l.setWorn(Collections.singletonList(new Loadout.WornItem(3, AGS, 1)));
		l.setInventory(Arrays.asList(new Loadout.InvItem(BREW, 6), new Loadout.InvItem(PPOT, 4)));
		l.setSavedAt(123456789L);
		return l;
	}

	@Test
	public void roundTripPreservesContent() throws LoadoutCodecException
	{
		final Loadout original = sample();
		final Loadout decoded = LoadoutCodec.decode(LoadoutCodec.encode(original));

		assertEquals(original.getName(), decoded.getName());
		assertEquals(original.getBuild(), decoded.getBuild());
		assertEquals(original.getSpellbook(), decoded.getSpellbook());
		assertEquals(original.getWorn(), decoded.getWorn());
		assertEquals(original.getInventory(), decoded.getInventory());
	}

	@Test
	public void decodeMintsFreshIdentity() throws LoadoutCodecException
	{
		// id/savedAt are not carried; the caller (LoadoutManager.add) mints them.
		final Loadout decoded = LoadoutCodec.decode(LoadoutCodec.encode(sample()));
		assertNull(decoded.getId());
		assertEquals(0L, decoded.getSavedAt());
	}

	@Test
	public void roundTripCarriesNullSpellbook() throws LoadoutCodecException
	{
		final Loadout original = sample();
		original.setSpellbook(null);
		final Loadout decoded = LoadoutCodec.decode(LoadoutCodec.encode(original));
		assertNull(decoded.getSpellbook());
	}

	@Test
	public void encodedCodeCarriesTheVersionedPrefix()
	{
		assertTrue(LoadoutCodec.encode(sample()).startsWith("pvpa-loadout-v1:"));
	}

	@Test
	public void rejectsBadPrefix()
	{
		expectInvalid("just some random clipboard text");
	}

	@Test
	public void rejectsNullInput()
	{
		expectInvalid(null);
	}

	@Test
	public void rejectsBadBase64()
	{
		expectInvalid("pvpa-loadout-v1:@@not-valid-base64@@");
	}

	@Test
	public void rejectsBadJson()
	{
		expectInvalid("pvpa-loadout-v1:" + base64("{ this is not json"));
	}

	@Test
	public void rejectsEmptyLoadout()
	{
		final Loadout empty = new Loadout();
		empty.setName("empty");
		// No worn and no inventory -> nothing to share.
		expectInvalid(LoadoutCodec.encode(empty));
	}

	@Test
	public void newerVersionIsDistinctFromInvalid()
	{
		final String v2 = LoadoutCodec.encode(sample()).replaceFirst("^pvpa-loadout-v1:", "pvpa-loadout-v2:");
		try
		{
			LoadoutCodec.decode(v2);
			fail("expected a newer-version rejection");
		}
		catch (LoadoutCodecException e)
		{
			assertEquals(LoadoutCodecException.Reason.NEWER_VERSION, e.getReason());
		}
	}

	private static String base64(String s)
	{
		return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
	}

	private static void expectInvalid(String raw)
	{
		try
		{
			LoadoutCodec.decode(raw);
			fail("expected an invalid-code rejection for: " + raw);
		}
		catch (LoadoutCodecException e)
		{
			assertEquals(LoadoutCodecException.Reason.INVALID, e.getReason());
		}
	}
}
