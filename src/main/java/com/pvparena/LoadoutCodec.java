package com.pvparena;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
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
	/**
	 * The schema version this plugin writes. Encode always emits this; decode still accepts every
	 * released version back to {@code 1} (see ADR-0005). v1 payload is {@code Base64(JSON)};
	 * v2 payload is {@code Base64(raw-DEFLATE(JSON))} of the same {@link LoadoutCode} envelope.
	 */
	static final int VERSION = 2;

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
		// Collapse defensively: loadouts persisted before this normalization existed still hold
		// one entry per inventory slot, so this keeps their exported codes short too. A copy is
		// produced (see Loadout.collapseInventory), so the cached loadout is never mutated.
		code.setInventory(Loadout.collapseInventory(loadout.getInventory()));

		final byte[] json = gson.toJson(code).getBytes(StandardCharsets.UTF_8);
		// v2: raw DEFLATE the JSON before Base64 (ADR-0005). Deterministic for a fixed input, so
		// the same loadout still produces a byte-identical code.
		return PREFIX + VERSION + ':' + Base64.getEncoder().encodeToString(deflate(json));
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
		if (version < 1)
		{
			// Unparseable or zero: no decode path exists, so it is simply invalid.
			throw invalid();
		}

		// Every released version decodes to the same LoadoutCode envelope; only the byte pipeline
		// differs. v1 is Base64(JSON); v2 adds a raw-DEFLATE layer under the Base64 (ADR-0005).
		final byte[] payload = base64(s.substring(colon + 1));
		final byte[] json = version == 1 ? payload : inflate(payload);
		final LoadoutCode code = parseJson(json);

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

	private static byte[] base64(String payload) throws LoadoutCodecException
	{
		try
		{
			return Base64.getDecoder().decode(payload);
		}
		catch (IllegalArgumentException e)
		{
			throw invalid();
		}
	}

	private LoadoutCode parseJson(byte[] json) throws LoadoutCodecException
	{
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

	/** Raw DEFLATE (nowrap: no zlib header/checksum) at max level; deterministic for a fixed input. */
	private static byte[] deflate(byte[] data)
	{
		final Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
		deflater.setInput(data);
		deflater.finish();

		final ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(32, data.length / 3));
		final byte[] buf = new byte[2048];
		while (!deflater.finished())
		{
			out.write(buf, 0, deflater.deflate(buf));
		}
		deflater.end();
		return out.toByteArray();
	}

	/** Inverse of {@link #deflate}; a corrupt or truncated stream is an invalid code, not a crash. */
	private static byte[] inflate(byte[] compressed) throws LoadoutCodecException
	{
		final Inflater inflater = new Inflater(true);
		// A nowrap inflater needs one extra dummy byte past the end of the raw DEFLATE stream
		// (a documented ZLIB requirement); without it a valid stream never reaches finished().
		inflater.setInput(Arrays.copyOf(compressed, compressed.length + 1));

		final ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, compressed.length * 3));
		final byte[] buf = new byte[2048];
		try
		{
			while (!inflater.finished())
			{
				final int n = inflater.inflate(buf);
				if (n > 0)
				{
					out.write(buf, 0, n);
				}
				else if (!inflater.finished())
				{
					// No progress and not done: the stream is truncated or wants a dictionary.
					throw invalid();
				}
			}
		}
		catch (DataFormatException e)
		{
			throw invalid();
		}
		finally
		{
			inflater.end();
		}
		return out.toByteArray();
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
