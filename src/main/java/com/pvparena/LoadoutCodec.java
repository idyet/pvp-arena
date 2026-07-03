package com.pvparena;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Encodes a single {@link Loadout} to, and decodes it from, a portable Loadout code:
 * {@code pvpa-loadout-v1:} followed by {@code Base64(JSON)} of a {@link LoadoutCode} envelope
 * (see ADR-0004). The envelope is deliberately decoupled from the internal {@link Loadout},
 * so the wire format survives internal refactors; only the leaf item types are reused.
 *
 * <p>Item ids are trusted, not validated against the item cache: unplaceable items fail soft
 * downstream at load time (via {@code unlocatableCount}), exactly like a hand-built loadout.
 */
@Singleton
final class LoadoutCodec
{
	/** Magic prefix, minus the version integer and colon: {@code pvpa-loadout-v}. */
	static final String PREFIX = "pvpa-loadout-v";
	/** The schema version this plugin writes and reads. */
	static final int VERSION = 1;

	/** The client's shared {@link Gson} (plugin hub forbids fresh instances). */
	private final Gson gson;

	@Inject
	LoadoutCodec(Gson gson)
	{
		this.gson = gson;
	}

	/** Produces the shareable code for {@code loadout} ({@code id}/{@code savedAt} are omitted). */
	String encode(Loadout loadout)
	{
		final LoadoutCode code = new LoadoutCode();
		code.setName(loadout.getName());
		code.setBuild(loadout.getBuild());
		code.setSpellbook(loadout.getSpellbook());
		code.setWorn(loadout.getWorn());
		code.setInventory(loadout.getInventory());

		final byte[] json = gson.toJson(code).getBytes(StandardCharsets.UTF_8);
		return PREFIX + VERSION + ':' + Base64.getEncoder().encodeToString(json);
	}

	/**
	 * Parses {@code raw} into a fresh {@link Loadout} with no {@code id}/{@code savedAt} — the
	 * caller mints those via {@link LoadoutManager#add}. Throws {@link LoadoutCodecException}
	 * with {@link LoadoutCodecException.Reason#NEWER_VERSION} for a recognized-but-newer
	 * version, or {@link LoadoutCodecException.Reason#INVALID} for anything else.
	 */
	Loadout decode(String raw) throws LoadoutCodecException
	{
		if (raw == null)
		{
			throw invalid();
		}

		final String s = raw.trim();
		if (!s.startsWith(PREFIX))
		{
			throw invalid();
		}

		final int colon = s.indexOf(':', PREFIX.length());
		if (colon < 0)
		{
			throw invalid();
		}

		final int version = parseVersion(s.substring(PREFIX.length(), colon));
		if (version > VERSION)
		{
			throw new LoadoutCodecException(LoadoutCodecException.Reason.NEWER_VERSION);
		}
		if (version != VERSION)
		{
			// Unparseable or older-than-v1: no decode path exists, so it is simply invalid.
			throw invalid();
		}

		final LoadoutCode code = parse(s.substring(colon + 1));

		final boolean noWorn = code.getWorn() == null || code.getWorn().isEmpty();
		final boolean noInventory = code.getInventory() == null || code.getInventory().isEmpty();
		if (noWorn && noInventory)
		{
			throw invalid();
		}

		final Loadout out = new Loadout();
		out.setName(code.getName());
		out.setBuild(code.getBuild());
		out.setSpellbook(code.getSpellbook());
		out.setWorn(code.getWorn() == null ? new ArrayList<>() : code.getWorn());
		out.setInventory(code.getInventory() == null ? new ArrayList<>() : code.getInventory());
		return out;
	}

	/** {@code -1} if the version segment is not a non-negative integer. */
	private static int parseVersion(String segment)
	{
		if (segment.isEmpty())
		{
			return -1;
		}
		for (int i = 0; i < segment.length(); i++)
		{
			if (!Character.isDigit(segment.charAt(i)))
			{
				return -1;
			}
		}
		try
		{
			return Integer.parseInt(segment);
		}
		catch (NumberFormatException e)
		{
			return -1; // overflow -> unrecognized version
		}
	}

	private LoadoutCode parse(String payload) throws LoadoutCodecException
	{
		final byte[] json;
		try
		{
			json = Base64.getDecoder().decode(payload);
		}
		catch (IllegalArgumentException e)
		{
			throw invalid();
		}

		final LoadoutCode code;
		try
		{
			code = gson.fromJson(new String(json, StandardCharsets.UTF_8), LoadoutCode.class);
		}
		catch (JsonSyntaxException e)
		{
			throw invalid();
		}
		if (code == null)
		{
			throw invalid();
		}
		return code;
	}

	private static LoadoutCodecException invalid()
	{
		return new LoadoutCodecException(LoadoutCodecException.Reason.INVALID);
	}

	/**
	 * The wire envelope (ADR-0004): top-level fields only, mirroring {@link Loadout} minus
	 * {@code id}/{@code savedAt}. The leaf item types are reused unchanged.
	 */
	@Data
	@NoArgsConstructor
	static class LoadoutCode
	{
		private String name;
		private int build;
		private String spellbook;
		private List<Loadout.WornItem> worn = new ArrayList<>();
		private List<Loadout.InvItem> inventory = new ArrayList<>();
	}
}
