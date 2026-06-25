package com.pvparena;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;

/**
 * Feature 1 — shift-click discard.
 *
 * <p>On a loadout item whose default left-click is {@code Discard}, swap the left-click
 * so that without Shift it {@code Examine}s (harmless during drag-rearrange) and with
 * Shift it {@code Discard}s. The swap is a reorder of the existing entries, so nothing
 * is removed and a right-click Discard always works. Items whose default left-click is
 * not Discard are never touched.
 */
@Singleton
class ShiftClickDeleteHandler
{
	private static final String DISCARD = "Discard";
	private static final String EXAMINE = "Examine";

	// Interface groups of the two loadout builders. param1 of an item menu entry packs
	// its widget id; the high 16 bits are the group.
	private static final int STAGING_GROUP =
		WidgetUtil.componentToInterface(InterfaceID.PvpArenaStagingareaSupplies.UNIVERSE);
	private static final int UNRANKED_GROUP =
		WidgetUtil.componentToInterface(InterfaceID.PvpArenaUnrankedduel.UNIVERSE);

	enum Decision
	{
		/** Keep Discard as the left-click (Shift held: intentional deletion). */
		DISCARD_LEFT,
		/** Promote Examine to the left-click (no Shift: protect against misclicks). */
		EXAMINE_LEFT,
		/** Leave the menu as-is. */
		UNCHANGED
	}

	private final Client client;

	@Inject
	ShiftClickDeleteHandler(Client client)
	{
		this.client = client;
	}

	/**
	 * Pure swap decision (unit-tested). See DESIGN.md Feature 1.
	 *
	 * @param hasDiscardDefault the item's current default left-click is Discard
	 * @param hasExamine        an Examine entry exists for the same item
	 * @param shiftHeld         Shift is held
	 */
	static Decision desiredLeftClick(boolean hasDiscardDefault, boolean hasExamine, boolean shiftHeld)
	{
		if (!hasDiscardDefault)
		{
			// Not a left-click-discard item; never change its left-click.
			return Decision.UNCHANGED;
		}

		if (shiftHeld)
		{
			return Decision.DISCARD_LEFT;
		}

		// No Shift: promote Examine so a stray click can't discard. If there is nothing
		// benign to promote, leave Discard rather than inventing a no-op.
		return hasExamine ? Decision.EXAMINE_LEFT : Decision.UNCHANGED;
	}

	/**
	 * Reorders the current (closed-menu) entries so the left-click matches
	 * {@link #desiredLeftClick}. Called from {@code onPostMenuSort} after the world and
	 * config gates have passed.
	 */
	void applyLeftClickSwap()
	{
		final Menu menu = client.getMenu();
		final MenuEntry[] entries = menu.getMenuEntries();
		if (entries.length == 0)
		{
			return;
		}

		// The left-click action is the last entry (top of the menu).
		final int topIdx = entries.length - 1;
		final MenuEntry top = entries[topIdx];

		if (!isBuilderItemEntry(top))
		{
			return;
		}

		final boolean topIsDiscard = DISCARD.equalsIgnoreCase(top.getOption());
		final boolean topIsExamine = EXAMINE.equalsIgnoreCase(top.getOption());
		if (!topIsDiscard && !topIsExamine)
		{
			// Some other option is the default left-click: leave it alone.
			return;
		}

		// Find the Discard / Examine entries belonging to the same hovered widget.
		final int widgetId = top.getParam1();
		int discardIdx = -1;
		int examineIdx = -1;
		for (int i = topIdx; i >= 0; i--)
		{
			final MenuEntry e = entries[i];
			if (e.getParam1() != widgetId)
			{
				continue;
			}

			final String option = e.getOption();
			if (discardIdx < 0 && DISCARD.equalsIgnoreCase(option))
			{
				discardIdx = i;
			}
			else if (examineIdx < 0 && EXAMINE.equalsIgnoreCase(option))
			{
				examineIdx = i;
			}
		}

		// The natural top entry each tick reflects the game's default left-click, so
		// "default is Discard" holds exactly when Discard is currently on top.
		final Decision decision = desiredLeftClick(topIsDiscard, examineIdx >= 0, isShiftHeld());

		final int desiredIdx;
		switch (decision)
		{
			case EXAMINE_LEFT:
				desiredIdx = examineIdx;
				break;
			case DISCARD_LEFT:
				desiredIdx = discardIdx;
				break;
			default:
				return;
		}

		if (desiredIdx < 0 || desiredIdx == topIdx)
		{
			return;
		}

		// Move the desired entry into the left-click (top) slot.
		final MenuEntry desired = entries[desiredIdx];
		entries[desiredIdx] = entries[topIdx];
		entries[topIdx] = desired;
		menu.setMenuEntries(entries);
	}

	private boolean isBuilderItemEntry(MenuEntry entry)
	{
		final int group = WidgetUtil.componentToInterface(entry.getParam1());
		return group == STAGING_GROUP || group == UNRANKED_GROUP;
	}

	private boolean isShiftHeld()
	{
		return client.isKeyPressed(KeyCode.KC_SHIFT);
	}
}
