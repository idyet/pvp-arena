package com.pvparena;

/**
 * Thrown when a {@link com.pvparena.LoadoutCodec.LoadoutCode Loadout code} cannot be decoded.
 * {@link #getReason()} separates a generically invalid code from one produced by a newer
 * plugin version, so the caller can pick a matching message (see ADR-0004).
 */
class LoadoutCodecException extends Exception
{
	/** Why a code was rejected. */
	enum Reason
	{
		/** Bad prefix, bad Base64, bad JSON, an unknown version, or an empty (no-item) payload. */
		INVALID,
		/** A recognized prefix carrying a version newer than this plugin understands. */
		NEWER_VERSION
	}

	private final Reason reason;

	LoadoutCodecException(Reason reason)
	{
		super(reason.name());
		this.reason = reason;
	}

	Reason getReason()
	{
		return reason;
	}
}
