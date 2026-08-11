package com.pvparena;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.inject.Singleton;

/**
 * Optimistic-add ledger for the catalog filter (Feature 3). The owned-inventory widget
 * ({@code _NINVENTORY}) that {@link SetupReader#currentBag} reads lags an item add by
 * ~1 game tick (~600ms), so a filtered "to add" row lingers during the window and a fast
 * second click double-adds. This credits each click immediately, letting the row vanish on
 * the next {@link net.runelite.api.events.ClientTick} (~1 frame) instead.
 *
 * <p>Pure and unit-tested like {@link LoadoutDiff} — no {@link net.runelite.api.Client}
 * dependency. Holds two parallel maps keyed by item id:
 * <ul>
 *   <li>{@code pending} — clicks credited but not yet seen in the real read.</li>
 *   <li>{@code baseline} — the real count captured the first tick a prediction was live,
 *       so reconciliation diffs against where the stack started, not against zero.</li>
 * </ul>
 *
 * <p>Reconciliation is baseline-diff with no timeout (real inventory is the source of
 * truth and always catches up, since every add lands except on a full grid, which the
 * caller guards): each tick, once the real read has grown by at least the predicted
 * amount the prediction retires; until then the shortfall is added back on top of the
 * live read. Not thread-safe — all access is on the client thread (the tick + click
 * handlers), same as {@link CatalogFilter}.
 */
@Singleton
class PendingAdds
{
	/** itemId -&gt; clicks credited but not yet reflected in the real read. */
	private final Map<Integer, Integer> pending = new HashMap<>();
	/** itemId -&gt; real count when the prediction went 0-&gt;1 (the diff baseline). */
	private final Map<Integer, Integer> baseline = new HashMap<>();

	/** Credits one optimistic add of {@code itemId} (a single {@code Add} catalog click). */
	void record(int itemId)
	{
		pending.merge(itemId, 1, Integer::sum);
	}

	/**
	 * Reconciles predictions against {@code currentBag} (the live worn+inventory read) and
	 * returns a fresh bag with any surviving predictions merged on top. Never mutates the
	 * input. Retires a prediction once the real read has grown by at least the predicted
	 * amount since its baseline.
	 */
	Map<Integer, Integer> effectiveBag(Map<Integer, Integer> currentBag)
	{
		final Map<Integer, Integer> result = new HashMap<>(currentBag);

		final Iterator<Map.Entry<Integer, Integer>> it = pending.entrySet().iterator();
		while (it.hasNext())
		{
			final Map.Entry<Integer, Integer> e = it.next();
			final int itemId = e.getKey();
			final int predicted = e.getValue();

			// First tick a prediction is live, pin the baseline to the (pre-add) real count.
			final int base = baseline.computeIfAbsent(itemId, k -> currentBag.getOrDefault(k, 0));
			final int gained = currentBag.getOrDefault(itemId, 0) - base;
			final int surviving = predicted - gained;

			if (surviving > 0)
			{
				result.merge(itemId, surviving, Integer::sum);
			}
			else
			{
				// Real read caught up: drop the prediction and its baseline.
				it.remove();
				baseline.remove(itemId);
			}
		}
		return result;
	}

	/** Forgets every prediction; called wherever the catalog filter is cleared. */
	void clear()
	{
		pending.clear();
		baseline.clear();
	}
}
