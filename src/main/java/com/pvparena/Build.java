package com.pvparena;

import lombok.Getter;

/**
 * The three fixed PvP Arena account builds, selected in-game by
 * {@code VarbitID.PVPA_TRANSMIT_BUILD} (value {@code 0}/{@code 1}/{@code 2} -> setup
 * panel {@code _0}/{@code _1}/{@code _2} / loadout slot A/B/C). Used only to group and
 * label {@link Loadout}s; loadouts are <b>not</b> locked to a build (see DESIGN.md
 * "Feature 3 -> Build context").
 *
 * <p>VERIFY IN-GAME: the exact value-&gt;label order (which of 0/1/2 is Pure, etc.).
 * If wrong, only the labels here need swapping.
 */
@Getter
enum Build
{
	MAX_MED(0, "Max/Med"),
	ZERKER(1, "Zerker"),
	PURE(2, "Pure");

	private final int value;
	private final String label;

	Build(int value, String label)
	{
		this.value = value;
		this.label = label;
	}

	static Build fromValue(int value)
	{
		for (Build b : values())
		{
			if (b.value == value)
			{
				return b;
			}
		}
		return null;
	}

	static String labelFor(int value)
	{
		final Build b = fromValue(value);
		return b == null ? "Build " + value : b.label;
	}
}
