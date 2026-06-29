package com.pvparena;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Persistence + active-state for {@link Loadout}s. Stored globally (not RSProfile-scoped,
 * see ADR-0003) as a JSON list under the {@code pvparena} config group. Holds the single
 * {@link #getActive() active loadout} in memory (ephemeral, by id).
 */
@Slf4j
@Singleton
class LoadoutManager
{
	/** Distinct from the {@code loadouts} feature-toggle config key: sharing it clobbers the toggle. */
	static final String KEY = "savedLoadouts";
	private static final Type LIST_TYPE = new TypeToken<List<Loadout>>()
	{
	}.getType();

	private final ConfigManager configManager;
	private final Gson gson;

	/** Lazily-loaded, authoritative in-memory copy; persisted on every mutation. */
	private List<Loadout> cache;
	/** Id of the active loadout (the one loaded for comparison), or null. */
	private String activeId;
	/** Whether the catalog filter is engaged for the active loadout (auto-on when loaded). */
	private boolean filterOn;

	@Inject
	LoadoutManager(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	synchronized List<Loadout> getAll()
	{
		ensureLoaded();
		return new ArrayList<>(cache);
	}

	synchronized List<Loadout> forBuild(int build)
	{
		ensureLoaded();
		final List<Loadout> out = new ArrayList<>();
		for (Loadout l : cache)
		{
			if (l.getBuild() == build)
			{
				out.add(l);
			}
		}
		return out;
	}

	synchronized Loadout byId(String id)
	{
		ensureLoaded();
		if (id == null)
		{
			return null;
		}
		for (Loadout l : cache)
		{
			if (id.equals(l.getId()))
			{
				return l;
			}
		}
		return null;
	}

	/** Persists {@code snapshot} as a new loadout with the given display name. */
	synchronized Loadout add(Loadout snapshot, String name)
	{
		ensureLoaded();
		snapshot.setId(UUID.randomUUID().toString());
		snapshot.setName(name);
		snapshot.setSavedAt(System.currentTimeMillis());
		cache.add(snapshot);
		persist();
		return snapshot;
	}

	synchronized void delete(String id)
	{
		ensureLoaded();
		cache.removeIf(l -> id != null && id.equals(l.getId()));
		if (id != null && id.equals(activeId))
		{
			activeId = null;
			filterOn = false;
		}
		persist();
	}

	synchronized void rename(String id, String name)
	{
		final Loadout l = byId(id);
		if (l != null)
		{
			l.setName(name);
			persist();
		}
	}

	/** Overwrites an existing loadout's contents from a freshly-read setup snapshot. */
	synchronized void update(String id, Loadout snapshot)
	{
		final Loadout l = byId(id);
		if (l != null)
		{
			l.setBuild(snapshot.getBuild());
			l.setSpellbook(snapshot.getSpellbook());
			l.setWorn(snapshot.getWorn());
			l.setInventory(snapshot.getInventory());
			l.setSavedAt(System.currentTimeMillis());
			persist();
		}
	}

	synchronized Loadout getActive()
	{
		return byId(activeId);
	}

	synchronized void setActive(Loadout l)
	{
		activeId = l == null ? null : l.getId();
		filterOn = l != null; // catalog filter engages automatically on load
	}

	synchronized void clearActive()
	{
		activeId = null;
		filterOn = false;
	}

	synchronized boolean isActive(Loadout l)
	{
		return l != null && l.getId() != null && l.getId().equals(activeId);
	}

	/** Whether the catalog filter is engaged (only meaningful while a loadout is active). */
	synchronized boolean isFilterOn()
	{
		return activeId != null && filterOn;
	}

	synchronized void setFilterOn(boolean on)
	{
		filterOn = on;
	}

	private void ensureLoaded()
	{
		if (cache != null)
		{
			return;
		}

		final String json = configManager.getConfiguration(PvpArenaConfig.GROUP, KEY);
		List<Loadout> parsed = null;
		if (json != null && !json.isEmpty())
		{
			try
			{
				parsed = gson.fromJson(json, LIST_TYPE);
			}
			catch (JsonSyntaxException e)
			{
				log.warn("Could not parse stored loadouts, starting empty", e);
			}
		}
		cache = parsed != null ? parsed : new ArrayList<>();
	}

	private void persist()
	{
		configManager.setConfiguration(PvpArenaConfig.GROUP, KEY, gson.toJson(cache, LIST_TYPE));
	}
}
