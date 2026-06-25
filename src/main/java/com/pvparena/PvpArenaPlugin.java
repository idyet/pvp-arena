package com.pvparena;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.api.events.PostMenuSort;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "PvP Arena",
	description = "Quality-of-life utilities for dueling on PvP Arena worlds: shift-click discard and spellbook-mismatch warnings.",
	tags = {"pvp", "arena", "duel", "spellbook", "loadout", "discard", "qol", "pk", "nh", "veng"}
)
public class PvpArenaPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private PvpArenaConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SpellbookMismatchOverlay spellbookMismatchOverlay;

	@Inject
	private ShiftClickDeleteHandler shiftClickDeleteHandler;

	@Provides
	PvpArenaConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PvpArenaConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(spellbookMismatchOverlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(spellbookMismatchOverlay);
	}

	/**
	 * Shared world-layer gate. Every feature additionally checks that its own target
	 * interface is loaded before acting (see DESIGN.md "Activation gate").
	 */
	public boolean inPvpArena()
	{
		return client.getWorldType().contains(WorldType.PVP_ARENA);
	}

	/**
	 * Feature 1 (shift-click discard) hooks the left-click menu, which only resolves
	 * while the right-click menu is closed. {@link PostMenuSort} fires in exactly that
	 * window, so a deliberate right-click Discard is never touched.
	 */
	@Subscribe
	public void onPostMenuSort(PostMenuSort event)
	{
		if (!config.shiftClickDelete() || !inPvpArena())
		{
			return;
		}

		shiftClickDeleteHandler.applyLeftClickSwap();
	}
}
