package com.pvparena;

import com.google.inject.Provides;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "PvP Arena",
	description = "Quality-of-life utilities for dueling on PvP Arena worlds: shift-click discard, spellbook-mismatch warnings, and loadouts.",
	tags = {"pvp", "arena", "duel", "spellbook", "loadout", "discard", "qol", "pk", "nh", "veng"}
)
public class PvpArenaPlugin extends Plugin implements LoadoutPanel.Actions
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

	@Inject
	private LoadoutOverlay loadoutOverlay;

	@Inject
	private LoadoutManager loadoutManager;

	@Inject
	private SetupReader setupReader;

	@Inject
	private CatalogFilter catalogFilter;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	private LoadoutPanel panel;
	private NavigationButton navButton;
	private boolean navAdded;

	/** Cached so the Swing panel can read it without touching the client thread. */
	private volatile boolean builderOpen;
	private String lastActiveId;
	private int lastUnrankedProgress;

	@Provides
	PvpArenaConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PvpArenaConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(spellbookMismatchOverlay);
		overlayManager.add(loadoutOverlay);

		panel = new LoadoutPanel(loadoutManager, this);
		navButton = NavigationButton.builder()
			.tooltip("PvP Arena loadouts")
			.icon(navIcon())
			.priority(7)
			.panel(panel)
			.build();

		clientThread.invoke(this::updateNav);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(spellbookMismatchOverlay);
		overlayManager.remove(loadoutOverlay);
		removeNav();
		clientThread.invoke(catalogFilter::clear);
		loadoutManager.clearActive();
		panel = null;
		builderOpen = false;
		lastActiveId = null;
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

	@Subscribe
	public void onGameTick(GameTick event)
	{
		final boolean prevOpen = builderOpen;
		final boolean open = config.loadouts() && inPvpArena() && setupReader.isBuilderOpen();

		if (!prevOpen && open)
		{
			// Fresh builder session: forget any stale duel-progress reading.
			lastUnrankedProgress = 0;
			logBuilderContents();
		}

		// Duel-begin heuristic (VERIFY in-game): the builder closing while an unranked
		// confirm was in progress means the fight is starting -> drop the armed loadout.
		// A plain browse-and-close (progress 0) keeps it armed, per the load lifecycle.
		if (prevOpen && !open && lastUnrankedProgress > 0)
		{
			loadoutManager.clearActive();
		}

		final String activeId = idOf(loadoutManager.getActive());
		if (open != prevOpen || !Objects.equals(activeId, lastActiveId))
		{
			refreshPanel();
		}
		builderOpen = open;
		lastActiveId = activeId;

		updateNav();
	}

	/**
	 * Drives the catalog filter (Feature 3). Polled every client tick because the game
	 * rebuilds the catalog on its own (item add/remove, build switch) and {@link CatalogFilter}
	 * must re-apply after each rebuild; it self-short-circuits when already filtered.
	 */
	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (!config.loadouts() || !inPvpArena())
		{
			catalogFilter.clear();
			return;
		}

		final ArenaWidgets.Screen s = setupReader.openScreen();
		final Loadout active = loadoutManager.getActive();
		if (s == null || active == null)
		{
			catalogFilter.clear();
			return;
		}

		final int build = setupReader.activeBuild(s);

		if (loadoutManager.isFilterOn())
		{
			// Keep only rows the loadout still highlights (need > 0), same set as the green
			// to-add outline; the catalog shrinks as the player adds items.
			final Map<Integer, Integer> current = setupReader.currentBag(s, build);
			final LoadoutDiff diff = LoadoutDiff.compute(active.bag(), current,
				active.getSpellbook(), setupReader.spellbookLabel(s, build));
			catalogFilter.maintain(s, build, filterOrder(active, diff.toAdd().keySet()));
		}
		else
		{
			catalogFilter.clear();
		}
	}

	/**
	 * Display order (top to bottom) for the filtered catalog: equipable items first by
	 * equipment slot (head to ammo), then everything else by target quantity ascending.
	 * "Equipable" is by item stats, so equipable items stored in the loadout's inventory
	 * (switches) sort with the worn gear, not the consumables. Ties broken by item id.
	 */
	private List<Integer> filterOrder(Loadout l, Set<Integer> needed)
	{
		final Map<Integer, Integer> target = l.bag();
		final List<Integer> wearables = new ArrayList<>();
		final List<Integer> rest = new ArrayList<>();
		for (int id : needed)
		{
			(isEquipable(id) ? wearables : rest).add(id);
		}
		wearables.sort(Comparator.comparingInt((Integer id) -> equipSlot(id)).thenComparingInt(id -> id));
		rest.sort(Comparator.comparingInt((Integer id) -> target.getOrDefault(id, 1)).thenComparingInt(id -> id));

		final List<Integer> order = new ArrayList<>(wearables.size() + rest.size());
		order.addAll(wearables);
		order.addAll(rest);
		return order;
	}

	private boolean isEquipable(int itemId)
	{
		final ItemStats stats = itemManager.getItemStats(itemId);
		return stats != null && stats.isEquipable();
	}

	/** Equipment slot for ordering wearables head-to-toe; large for non-equipment. */
	private int equipSlot(int itemId)
	{
		final ItemStats stats = itemManager.getItemStats(itemId);
		if (stats == null || stats.getEquipment() == null)
		{
			return Integer.MAX_VALUE;
		}
		return stats.getEquipment().getSlot();
	}

	/**
	 * Disengage the catalog filter when the player clicks the catalog search ({@code _NSEEK})
	 * button, so the native search isn't fought. {@link #onClientTick} restores the full
	 * catalog on the next tick.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!config.loadouts() || !loadoutManager.isFilterOn())
		{
			return;
		}
		final Widget w = event.getWidget();
		if (ArenaWidgets.isSeekWidget(event.getParam1())
			|| (w != null && ArenaWidgets.isSeekWidget(w.getId())))
		{
			loadoutManager.setFilterOn(false);
			refreshPanel();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.PVPA_UNRANKEDDUEL_TRANSMIT_PROGRESS)
		{
			lastUnrankedProgress = event.getValue();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		final GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			loadoutManager.clearActive();
		}
		updateNav();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!PvpArenaConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if (!config.loadouts())
		{
			loadoutManager.clearActive();
		}
		clientThread.invoke(this::updateNav);
		refreshPanel();
	}

	// --- LoadoutPanel.Actions -------------------------------------------------------

	@Override
	public boolean isBuilderOpen()
	{
		return builderOpen;
	}

	@Override
	public void saveCurrentSetup()
	{
		clientThread.invoke(() ->
		{
			final Loadout snapshot = setupReader.readSnapshot();
			SwingUtilities.invokeLater(() -> finishSave(snapshot));
		});
	}

	private void finishSave(Loadout snapshot)
	{
		if (snapshot == null)
		{
			JOptionPane.showMessageDialog(panel, "Open the arena setup screen first.");
			return;
		}

		final String def = Build.labelFor(snapshot.getBuild()) + " loadout";
		final Object input = JOptionPane.showInputDialog(panel, "Loadout name:", "Save loadout",
			JOptionPane.PLAIN_MESSAGE, null, null, def);
		if (input == null)
		{
			return;
		}

		String name = input.toString().trim();
		if (name.isEmpty())
		{
			name = def;
		}
		loadoutManager.add(snapshot, name);
		refreshPanel();
	}

	@Override
	public void load(Loadout loadout)
	{
		loadoutManager.setActive(loadout);
		refreshPanel();
	}

	@Override
	public void stop()
	{
		loadoutManager.clearActive();
		clientThread.invoke(catalogFilter::clear);
		refreshPanel();
	}

	@Override
	public boolean isFilterOn()
	{
		return loadoutManager.isFilterOn();
	}

	@Override
	public void toggleFilter()
	{
		loadoutManager.setFilterOn(!loadoutManager.isFilterOn());
		refreshPanel(); // onClientTick applies/clears on the client thread next tick
	}

	@Override
	public void updateFromSetup(Loadout loadout)
	{
		final String id = loadout.getId();
		clientThread.invoke(() ->
		{
			final Loadout snapshot = setupReader.readSnapshot();
			SwingUtilities.invokeLater(() ->
			{
				if (snapshot == null)
				{
					JOptionPane.showMessageDialog(panel, "Open the arena setup screen first.");
					return;
				}
				loadoutManager.update(id, snapshot);
				refreshPanel();
			});
		});
	}

	@Override
	public void rename(Loadout loadout)
	{
		final Object input = JOptionPane.showInputDialog(panel, "Loadout name:", "Rename loadout",
			JOptionPane.PLAIN_MESSAGE, null, null, loadout.getName());
		if (input == null)
		{
			return;
		}
		final String name = input.toString().trim();
		if (!name.isEmpty())
		{
			loadoutManager.rename(loadout.getId(), name);
			refreshPanel();
		}
	}

	@Override
	public void delete(Loadout loadout)
	{
		final int result = JOptionPane.showConfirmDialog(panel,
			"Delete \"" + loadout.getName() + "\"?", "Delete loadout", JOptionPane.YES_NO_OPTION);
		if (result == JOptionPane.YES_OPTION)
		{
			loadoutManager.delete(loadout.getId());
			refreshPanel();
		}
	}

	/**
	 * Export. Pure EDT: clipboard + config only, no client thread (unlike
	 * {@link #saveCurrentSetup}). Availability is gated only incidentally by panel
	 * visibility (PvP Arena worlds), see ADR-0004.
	 */
	@Override
	public void copyCode(Loadout loadout)
	{
		if (loadout == null)
		{
			return;
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
			new StringSelection(LoadoutCodec.encode(loadout)), null);
		JOptionPane.showMessageDialog(panel,
			"Copied loadout code for \"" + loadout.getName() + "\" to your clipboard.",
			"Loadout code copied", JOptionPane.INFORMATION_MESSAGE);
	}

	/** Import. Reads the clipboard, decodes, and adds a fresh loadout in the encoded build. */
	@Override
	public void importCode()
	{
		final String raw = readClipboard();
		if (raw == null || raw.trim().isEmpty())
		{
			JOptionPane.showMessageDialog(panel,
				"Couldn't read a loadout code from your clipboard. Copy one and try again.",
				"Import loadout code", JOptionPane.WARNING_MESSAGE);
			return;
		}

		final Loadout decoded;
		try
		{
			decoded = LoadoutCodec.decode(raw);
		}
		catch (LoadoutCodecException e)
		{
			JOptionPane.showMessageDialog(panel, importErrorMessage(e.getReason()),
				"Import loadout code", JOptionPane.ERROR_MESSAGE);
			return;
		}

		final String name = decoded.getName() == null || decoded.getName().trim().isEmpty()
			? Build.labelFor(decoded.getBuild()) + " loadout"
			: decoded.getName();
		loadoutManager.add(decoded, name);
		refreshPanel();
	}

	private String readClipboard()
	{
		try
		{
			final Object data = Toolkit.getDefaultToolkit().getSystemClipboard()
				.getData(DataFlavor.stringFlavor);
			return data == null ? null : data.toString();
		}
		catch (UnsupportedFlavorException | IOException | IllegalStateException e)
		{
			// Non-text clipboard, or the clipboard was busy: treat as nothing to import.
			log.debug("Clipboard read failed", e);
			return null;
		}
	}

	private static String importErrorMessage(LoadoutCodecException.Reason reason)
	{
		if (reason == LoadoutCodecException.Reason.NEWER_VERSION)
		{
			return "This loadout code was made with a newer version of the plugin. "
				+ "Update PvP Arena to import it.";
		}
		return "That doesn't look like a valid loadout code. Copy the full code "
			+ "(it starts with \"pvpa-loadout-\") and try again.";
	}

	@Override
	public int unlocatableCount(Loadout active)
	{
		if (active == null || active.getId() == null)
		{
			return 0;
		}
		return active.getId().equals(loadoutOverlay.getLastUnlocatableActiveId())
			? loadoutOverlay.getLastUnlocatable()
			: 0;
	}

	// --- helpers --------------------------------------------------------------------

	private void refreshPanel()
	{
		final LoadoutPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(p::rebuild);
		}
	}

	private void updateNav()
	{
		final boolean show = config.loadouts() && inPvpArena();
		if (show && !navAdded)
		{
			clientToolbar.addNavigation(navButton);
			navAdded = true;
			refreshPanel();
		}
		else if (!show && navAdded)
		{
			removeNav();
		}
	}

	private void removeNav()
	{
		if (navAdded && navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navAdded = false;
		}
	}

	private static String idOf(Loadout loadout)
	{
		return loadout == null ? null : loadout.getId();
	}

	/** Debug aid (VERIFY in-game): confirms the worn/inventory/catalog reads find items. */
	private void logBuilderContents()
	{
		if (!log.isDebugEnabled())
		{
			return;
		}
		final ArenaWidgets.Screen s = setupReader.openScreen();
		if (s == null)
		{
			return;
		}
		final int b = setupReader.activeBuild(s);
		log.debug("PvP Arena builder open: build={} worn={} inventory={} catalog={}",
			b, setupReader.wornItems(s, b).size(), setupReader.inventoryItems(s, b).size(),
			setupReader.catalogItems(s, b).size());
	}

	private static BufferedImage navIcon()
	{
		return ImageUtil.loadImageResource(PvpArenaPlugin.class, "icon.png");
	}
}
