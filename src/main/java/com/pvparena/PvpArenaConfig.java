package com.pvparena;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(PvpArenaConfig.GROUP)
public interface PvpArenaConfig extends Config
{
	String GROUP = "pvparena";

	@ConfigItem(
		keyName = "shiftClickDelete",
		name = "Shift-click discard",
		description = "On loadout items that left-click discard, swap so left-click examines and Shift + left-click discards. Right-click is unchanged."
	)
	default boolean shiftClickDelete()
	{
		return true;
	}

	@ConfigItem(
		keyName = "spellbookMismatch",
		name = "Highlight spellbook mismatch",
		description = "On the unranked duel screen, outline your spellbook, the opponent's spellbook, and the confirm button when the two spellbooks differ."
	)
	default boolean spellbookMismatch()
	{
		return true;
	}
}
