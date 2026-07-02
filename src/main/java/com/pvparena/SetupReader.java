package com.pvparena;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/**
 * Reads the live {@link Setup} (worn equipment, inventory supplies + quantities,
 * spellbook) from the {@link Setup builder} widgets — never from varbits (ADR-0001).
 * Used both to snapshot on save and to build the current bag for the load diff. All
 * reads are null-safe and degrade to empty when a guessed widget id is wrong.
 *
 * <p>The catalog and the owned inventory are separate areas. The catalog is the
 * {@code _NITEMS_LIST} widget (verified: "to add" highlights land on it). The owned
 * inventory grid's exact id is uncertain, so it is read by exclusion: every visible
 * item widget under the screen that is not in the catalog list or the worn slots.
 */
@Singleton
class SetupReader
{
	/** An item-bearing widget plus what it holds. {@code slot} is the equipment slot, or -1. */
	static final class ItemWidget
	{
		final int itemId;
		final int quantity;
		final int slot;
		final Widget widget;

		ItemWidget(int itemId, int quantity, int slot, Widget widget)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.slot = slot;
			this.widget = widget;
		}
	}

	private final Client client;

	@Inject
	SetupReader(Client client)
	{
		this.client = client;
	}

	/** The open builder screen, or null if neither is loaded/visible. */
	ArenaWidgets.Screen openScreen()
	{
		for (ArenaWidgets.Screen s : ArenaWidgets.SCREENS)
		{
			final Widget root = client.getWidget(s.universe);
			if (root != null && !root.isHidden())
			{
				return s;
			}
		}
		return null;
	}

	boolean isBuilderOpen()
	{
		return openScreen() != null;
	}

	/**
	 * Build (0/1/2) currently shown: the build whose {@code _NWORN} panel is visible.
	 * Falls back to {@code PVPA_TRANSMIT_BUILD} if none reads as visible.
	 */
	int activeBuild(ArenaWidgets.Screen s)
	{
		for (int b = 0; b < s.wornRoot.length; b++)
		{
			final Widget w = client.getWidget(s.wornRoot[b]);
			if (w != null && !w.isHidden())
			{
				return b;
			}
		}
		final int v = client.getVarbitValue(VarbitID.PVPA_TRANSMIT_BUILD);
		return (v >= 0 && v <= 2) ? v : 0;
	}

	List<ItemWidget> wornItems(ArenaWidgets.Screen s, int build)
	{
		final List<ItemWidget> out = new ArrayList<>();
		if (build < 0 || build >= s.wornSlots.length)
		{
			return out;
		}
		final int[] slots = s.wornSlots[build];
		for (int i = 0; i < slots.length; i++)
		{
			final Widget w = client.getWidget(slots[i]);
			final int itemId = itemIdOf(w);
			if (itemId > 0)
			{
				out.add(new ItemWidget(itemId, Math.max(1, w.getItemQuantity()), ArenaWidgets.WORN_SLOT_INDEX[i], w));
			}
		}
		return out;
	}

	/** All catalog rows for the build (every selectable item) — "to add" highlight targets. */
	List<ItemWidget> catalogItems(ArenaWidgets.Screen s, int build)
	{
		final List<ItemWidget> out = new ArrayList<>();
		if (build < 0 || build >= s.itemsList.length)
		{
			return out;
		}
		collectItems(client.getWidget(s.itemsList[build]), out);
		return out;
	}

	/** Owned supplies for the shown build: every item widget in the {@code _NINVENTORY} grid. */
	List<ItemWidget> inventoryItems(ArenaWidgets.Screen s, int build)
	{
		final List<ItemWidget> out = new ArrayList<>();
		if (build < 0 || build >= s.inventory.length)
		{
			return out;
		}
		collectItems(client.getWidget(s.inventory[build]), out);
		return out;
	}

	String spellbookLabel(ArenaWidgets.Screen s, int build)
	{
		return SpellbookMismatchOverlay.spellbookLabel(spellbookWidget(s, build));
	}

	Widget spellbookWidget(ArenaWidgets.Screen s, int build)
	{
		if (build < 0 || build >= s.spellbookDisplay.length)
		{
			return null;
		}
		return client.getWidget(s.spellbookDisplay[build]);
	}

	/** Reads the live setup into a transient {@link Loadout} (no id/name). Null if no builder. */
	Loadout readSnapshot()
	{
		final ArenaWidgets.Screen s = openScreen();
		if (s == null)
		{
			return null;
		}
		final int build = activeBuild(s);

		final Loadout l = new Loadout();
		l.setBuild(build);
		l.setSpellbook(spellbookLabel(s, build));

		final List<Loadout.WornItem> worn = new ArrayList<>();
		for (ItemWidget iw : wornItems(s, build))
		{
			worn.add(new Loadout.WornItem(iw.slot, iw.itemId, iw.quantity));
		}
		l.setWorn(worn);

		final List<Loadout.InvItem> inv = new ArrayList<>();
		for (ItemWidget iw : inventoryItems(s, build))
		{
			inv.add(new Loadout.InvItem(iw.itemId, iw.quantity));
		}
		l.setInventory(inv);
		return l;
	}

	/** itemId -&gt; total quantity across worn + inventory for the shown build. */
	Map<Integer, Integer> currentBag(ArenaWidgets.Screen s, int build)
	{
		final Map<Integer, Integer> bag = new HashMap<>();
		for (ItemWidget iw : wornItems(s, build))
		{
			bag.merge(iw.itemId, iw.quantity, Integer::sum);
		}
		for (ItemWidget iw : inventoryItems(s, build))
		{
			bag.merge(iw.itemId, iw.quantity, Integer::sum);
		}
		return bag;
	}

	/**
	 * DFS the subtree from {@code root}, collecting every item-bearing widget. Skips
	 * hidden subtrees. Started directly at the catalog / inventory grid widget, so there
	 * is nothing to exclude.
	 */
	private void collectItems(Widget root, List<ItemWidget> out)
	{
		if (root == null)
		{
			return;
		}

		final Set<Widget> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		final Deque<Widget> stack = new ArrayDeque<>();
		stack.push(root);
		while (!stack.isEmpty())
		{
			final Widget w = stack.pop();
			if (w == null || !seen.add(w) || w.isHidden())
			{
				continue;
			}

			final int itemId = w.getItemId();
			if (itemId > 0)
			{
				out.add(new ItemWidget(itemId, Math.max(1, w.getItemQuantity()), -1, w));
			}

			pushAll(stack, w.getDynamicChildren());
			pushAll(stack, w.getChildren());
			pushAll(stack, w.getNestedChildren());
		}
	}

	private static void pushAll(Deque<Widget> stack, Widget[] kids)
	{
		if (kids != null)
		{
			for (Widget k : kids)
			{
				if (k != null)
				{
					stack.push(k);
				}
			}
		}
	}

	private int itemIdOf(Widget w)
	{
		if (w == null)
		{
			return -1;
		}
		if (w.getItemId() > 0)
		{
			return w.getItemId();
		}
		final Widget[] kids = w.getChildren();
		if (kids != null)
		{
			for (Widget k : kids)
			{
				if (k != null && k.getItemId() > 0)
				{
					return k.getItemId();
				}
			}
		}
		return -1;
	}
}
