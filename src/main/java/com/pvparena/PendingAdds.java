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
 * dependency (the game tick is passed in, not read). Holds three parallel maps keyed by
 * item id:
 * <ul>
 *   <li>{@code pending} — clicks credited but not yet seen in the real read.</li>
 *   <li>{@code baseline} — the real count captured the first tick a prediction was live,
 *       so reconciliation diffs against where the stack started, not against zero.</li>
 *   <li>{@code recordedTick} — the game tick of the most recent click, for the TTL backstop.</li>
 * </ul>
 *
 * <p>Reconciliation is baseline-diff: once the real read has grown by at least the predicted
 * amount since its baseline the prediction retires; until then the shortfall is added back on
 * top of the live read. A prediction whose add never lands in the read (the item was removed
 * again by <em>any</em> path — Discard, wipe, trash, drag) can't be reconciled by an add-only
 * diff, so a {@value #TTL_TICKS}-game-tick TTL past the last click retires it as a backstop;
 * the row then returns instead of sticking forever. Retirement is not inferred from the read
 * moving <em>down</em> — that read dips transiently while the grid rebuilds during rapid
 * filling, and dropping a live prediction there re-opens the double-add. Not thread-safe — all
 * access is on the client thread (the tick + click handlers), same as {@link CatalogFilter}.
 */
@Singleton
class PendingAdds
{
	/**
	 * Game ticks a prediction survives past its last click before the TTL backstop retires it.
	 * Must exceed the ~1-tick read lag the prediction bridges (else the double-add window reopens);
	 * small so a removed row returns quickly.
	 */
	private static final int TTL_TICKS = 2;

	/** itemId -&gt; clicks credited but not yet reflected in the real read. */
	private final Map<Integer, Integer> pending = new HashMap<>();
	/** itemId -&gt; real count when the prediction went 0-&gt;1 (the diff baseline). */
	private final Map<Integer, Integer> baseline = new HashMap<>();
	/** itemId -&gt; game tick of the most recent click, for the TTL backstop. */
	private final Map<Integer, Integer> recordedTick = new HashMap<>();

	/**
	 * Credits one optimistic add of {@code itemId} (a single {@code Add} catalog click) at game
	 * tick {@code tick}, refreshing its TTL so an actively-clicked stack never expires mid-fill.
	 */
	void record(int itemId, int tick)
	{
		pending.merge(itemId, 1, Integer::sum);
		recordedTick.put(itemId, tick);
	}

	/**
	 * Reconciles predictions against {@code currentBag} (the live worn+inventory read) at game
	 * tick {@code tick} and returns a fresh bag with any surviving predictions merged on top.
	 * Never mutates the input. Retires a prediction once the real read has grown by at least the
	 * predicted amount since its baseline, or {@value #TTL_TICKS} ticks past its last click if the
	 * read never catches up (the item was removed again).
	 */
	Map<Integer, Integer> effectiveBag(Map<Integer, Integer> currentBag, int tick)
	{
		final Map<Integer, Integer> result = new HashMap<>(currentBag);

		final Iterator<Map.Entry<Integer, Integer>> it = pending.entrySet().iterator();
		while (it.hasNext())
		{
			final Map.Entry<Integer, Integer> e = it.next();
			final int itemId = e.getKey();
			final int predicted = e.getValue();

			// Backstop: the add never landed within the TTL, so the item was removed again by a
			// path the add-only diff can't see (Discard/wipe/trash/drag). Give up on the read
			// catching up and drop the prediction so the row returns.
			if (tick - recordedTick.getOrDefault(itemId, tick) >= TTL_TICKS)
			{
				it.remove();
				baseline.remove(itemId);
				recordedTick.remove(itemId);
				continue;
			}

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
				recordedTick.remove(itemId);
			}
		}
		return result;
	}

	/** Forgets every prediction; called wherever the catalog filter is cleared. */
	void clear()
	{
		pending.clear();
		baseline.clear();
		recordedTick.clear();
	}
}
