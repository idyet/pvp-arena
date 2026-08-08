package com.pvparena;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
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

	/**
	 * A real v1 code (a full "max nh" loadout) captured before v2 existed, kept verbatim as a
	 * back-compat fixture. Never regenerate this from the current encoder: its value is being a
	 * frozen witness that codes already in the wild still decode.
	 */
	private static final String FROZEN_V1 =
		"pvpa-loadout-v1:eyJuYW1lIjoibWF4IG5oIiwiYnVpbGQiOjAsInNwZWxsYm9vayI6IkFuY2llbnQgTWFnaWNrcyIsIndvcm4iOlt7InNsb3QiOjAsIml0ZW1JZCI6MTA4MjgsInF1YW50aXR5IjoxfSx7InNsb3QiOjEsIml0ZW1JZCI6MjE3OTUsInF1YW50aXR5IjoxfSx7InNsb3QiOjIsIml0ZW1JZCI6NjU4NSwicXVhbnRpdHkiOjF9LHsic2xvdCI6MywiaXRlbUlkIjoxMTc5MSwicXVhbnRpdHkiOjF9LHsic2xvdCI6NCwiaXRlbUlkIjo0NzM2LCJxdWFudGl0eSI6MX0seyJzbG90Ijo1LCJpdGVtSWQiOjEyODMxLCJxdWFudGl0eSI6MX0seyJzbG90Ijo3LCJpdGVtSWQiOjQ3NTksInF1YW50aXR5IjoxfSx7InNsb3QiOjksIml0ZW1JZCI6NzQ2MiwicXVhbnRpdHkiOjF9LHsic2xvdCI6MTAsIml0ZW1JZCI6MTE4NDAsInF1YW50aXR5IjoxfSx7InNsb3QiOjEyLCJpdGVtSWQiOjExNzczLCJxdWFudGl0eSI6MX0seyJzbG90IjoxMywiaXRlbUlkIjoyMTk1MCwicXVhbnRpdHkiOjF9XSwiaW52ZW50b3J5IjpbeyJpdGVtSWQiOjEzNDQxLCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjEzNDQxLCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjI1OTc1LCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjEzNDQxLCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjEzNDQxLCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjI0MjI1LCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjExODAyLCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjEzNDQxLCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjEzNDQxLCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjI3NjkwLCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjI5MDE2LCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjEzNDQxLCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjEzNDQxLCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjEyOTU0LCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjI5MDEzLCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjEzNDQxLCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjEwOTI1LCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjEyMDA2LCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjIxOTAyLCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjEwOTI1LCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjEwOTI1LCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjEwOTI1LCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjY2ODUsInF1YW50aXR5IjoxfSx7Iml0ZW1JZCI6NjY4NSwicXVhbnRpdHkiOjF9LHsiaXRlbUlkIjo2Njg1LCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjEyNjk1LCJxdWFudGl0eSI6MX0seyJpdGVtSWQiOjI0NDQsInF1YW50aXR5IjoxfV19";

	private final LoadoutCodec codec = new LoadoutCodec(new Gson());

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
		final Loadout decoded = codec.decode(codec.encode(original));

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
		final Loadout decoded = codec.decode(codec.encode(sample()));
		assertNull(decoded.getId());
		assertEquals(0L, decoded.getSavedAt());
	}

	@Test
	public void roundTripCarriesNullSpellbook() throws LoadoutCodecException
	{
		final Loadout original = sample();
		original.setSpellbook(null);
		final Loadout decoded = codec.decode(codec.encode(original));
		assertNull(decoded.getSpellbook());
	}

	@Test
	public void encodedCodeCarriesTheVersionedPrefix()
	{
		assertTrue(codec.encode(sample()).startsWith("pvpa-loadout-v2:"));
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
		expectInvalid(codec.encode(empty));
	}

	@Test
	public void newerVersionIsDistinctFromInvalid()
	{
		// A recognized-but-newer version (beyond what this plugin writes) is a distinct signal.
		final String vNext = codec.encode(sample()).replaceFirst("^pvpa-loadout-v2:", "pvpa-loadout-v3:");
		try
		{
			codec.decode(vNext);
			fail("expected a newer-version rejection");
		}
		catch (LoadoutCodecException e)
		{
			assertEquals(LoadoutCodecException.Reason.NEWER_VERSION, e.getReason());
		}
	}

	@Test
	public void decodesFrozenV1Code() throws LoadoutCodecException
	{
		// Back-compat: a real v1 code shared before v2 existed must still import. Frozen literal
		// (not produced by the current encoder), so this cannot silently drift with the format.
		final Loadout decoded = codec.decode(FROZEN_V1);

		assertEquals("max nh", decoded.getName());
		assertEquals(0, decoded.getBuild());
		assertEquals("Ancient Magicks", decoded.getSpellbook());
		assertEquals(11, decoded.getWorn().size());

		// Matching is bag-based; the v1 code carried un-collapsed inventory dupes.
		final Map<Integer, Integer> bag = decoded.bag();
		assertEquals(Integer.valueOf(9), bag.get(13441)); // sharks
		assertEquals(Integer.valueOf(4), bag.get(10925)); // karambwans
		assertEquals(Integer.valueOf(3), bag.get(6685));  // saradomin brews
	}

	@Test
	public void v1AndV2RoundTripToTheSameLoadout() throws LoadoutCodecException
	{
		// The v2 re-encode of the frozen v1 code decodes to an equivalent bag (format change is
		// lossless for matching).
		final Loadout fromV1 = codec.decode(FROZEN_V1);
		final Loadout viaV2 = codec.decode(codec.encode(fromV1));
		assertEquals(fromV1.bag(), viaV2.bag());
		assertEquals(fromV1.getSpellbook(), viaV2.getSpellbook());
	}

	@Test
	public void rejectsCorruptV2Payload()
	{
		// Valid Base64 but not a DEFLATE stream -> inflate fails -> invalid, never a crash.
		expectInvalid("pvpa-loadout-v2:" + base64("not a deflate stream at all"));
	}

	@Test
	public void encodeCollapsesDuplicateInventoryItems() throws LoadoutCodecException
	{
		// Four brews across four inventory slots (as SetupReader captures them) must serialize
		// as a single qty-4 entry, and the decoded inventory reflects the collapse.
		final Loadout spread = new Loadout();
		spread.setName("Spread");
		spread.setInventory(Arrays.asList(
			new Loadout.InvItem(BREW, 1),
			new Loadout.InvItem(PPOT, 1),
			new Loadout.InvItem(BREW, 1),
			new Loadout.InvItem(BREW, 1),
			new Loadout.InvItem(BREW, 1)));

		final Loadout decoded = codec.decode(codec.encode(spread));

		// First-seen order preserved: brew (seen first) then prayer potion.
		assertEquals(Arrays.asList(
			new Loadout.InvItem(BREW, 4),
			new Loadout.InvItem(PPOT, 1)), decoded.getInventory());
	}

	@Test
	public void collapsePreservesBagTotals() throws LoadoutCodecException
	{
		final Loadout spread = new Loadout();
		spread.setName("Spread");
		spread.setInventory(Arrays.asList(
			new Loadout.InvItem(BREW, 1),
			new Loadout.InvItem(BREW, 1),
			new Loadout.InvItem(PPOT, 4)));

		// Matching goes through bag(); collapsing must not change it.
		assertEquals(spread.bag(), codec.decode(codec.encode(spread)).bag());
	}

	@Test
	public void encodeIsDeterministic()
	{
		// Byte-identical codes for the same loadout (LinkedHashMap first-seen order).
		final Loadout original = sample();
		assertEquals(codec.encode(original), codec.encode(original));
	}

	private static String base64(String s)
	{
		return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
	}

	private void expectInvalid(String raw)
	{
		try
		{
			codec.decode(raw);
			fail("expected an invalid-code rejection for: " + raw);
		}
		catch (LoadoutCodecException e)
		{
			assertEquals(LoadoutCodecException.Reason.INVALID, e.getReason());
		}
	}
}
