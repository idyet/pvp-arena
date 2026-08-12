package com.pvparena;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Unit spec for {@link PendingAdds}, the optimistic-add reconciler behind the catalog
 * filter (Feature 3): a click is credited immediately so the filtered row disappears on
 * the next {@code ClientTick} instead of waiting a game tick for the lagging inventory
 * read. Pure like {@link LoadoutDiffTest}; no client deps (the game tick is passed in).
 *
 * <p>A prediction retires by one of two paths: the <b>fast path</b> — the real read grows to
 * meet it — or the <b>TTL backstop</b> — {@code TTL_TICKS} (2) game ticks past its last click,
 * for when the add never lands because the item was removed again. Cases that only exercise the
 * fast path hold the tick constant (0); the TTL cases advance it.
 */
public class PendingAddsTest
{
	private static final int DDS = 5698;
	private static final int SHARK = 385;
	private static final int BREW = 6685;

	private static Map<Integer, Integer> bag(int... itemThenQtyPairs)
	{
		final Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < itemThenQtyPairs.length; i += 2)
		{
			m.put(itemThenQtyPairs[i], itemThenQtyPairs[i + 1]);
		}
		return m;
	}

	@Test
	public void recordCreditsItemImmediately()
	{
		// The whole point: +1 shows up before the real read has it (empty current bag).
		final PendingAdds p = new PendingAdds();
		p.record(DDS, 0);
		assertEquals(Integer.valueOf(1), p.effectiveBag(bag(), 0).get(DDS));
	}

	@Test
	public void predictionRetiresWhenRealReadCatchesUp()
	{
		final PendingAdds p = new PendingAdds();
		p.record(DDS, 0);
		// Frame 1: real still lagging (pre-add). Baseline captured at 0, prediction stands.
		assertEquals(Integer.valueOf(1), p.effectiveBag(bag(), 0).get(DDS));
		// Frame N: the add landed in the real read. Prediction retires, no double-count.
		assertEquals(Integer.valueOf(1), p.effectiveBag(bag(DDS, 1), 0).get(DDS));
		// Prediction is gone: later reads pass through untouched.
		assertEquals(Integer.valueOf(1), p.effectiveBag(bag(DDS, 1), 0).get(DDS));
	}

	@Test
	public void partialStackStaysPredictedUntilRealFullyCatchesUp()
	{
		// Want three sharks from empty: three rapid clicks, real lags then lands.
		final PendingAdds p = new PendingAdds();
		p.record(SHARK, 0);
		p.record(SHARK, 0);
		p.record(SHARK, 0);
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(), 0).get(SHARK));          // 0 real + 3 predicted
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(SHARK, 1), 0).get(SHARK));  // 1 real + 2 predicted
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(SHARK, 2), 0).get(SHARK));  // 2 real + 1 predicted
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(SHARK, 3), 0).get(SHARK));  // 3 real, prediction retired
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(SHARK, 3), 0).get(SHARK));  // pure passthrough
	}

	@Test
	public void baselineIsTheRealCountWhenTheAddBegan()
	{
		// Topping up a held stack: baseline-diff must credit against the real count, not zero.
		final PendingAdds p = new PendingAdds();
		p.record(BREW, 0);
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(BREW, 2), 0).get(BREW)); // baseline 2, +1 predicted
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(BREW, 3), 0).get(BREW)); // real caught up, retired
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(BREW, 3), 0).get(BREW)); // passthrough
	}

	@Test
	public void predictionExpiresAfterTtlWhenReadNeverCatchesUp()
	{
		// The add never lands in the read (item removed again). The add-only diff can't see the
		// retraction, so the TTL backstop retires the prediction 2 ticks after the click — the
		// row returns instead of sticking forever.
		final PendingAdds p = new PendingAdds();
		p.record(DDS, 0);
		assertEquals(Integer.valueOf(1), p.effectiveBag(bag(), 0).get(DDS)); // t0: live
		assertEquals(Integer.valueOf(1), p.effectiveBag(bag(), 1).get(DDS)); // t1: within TTL
		assertNull(p.effectiveBag(bag(), 2).get(DDS));                       // t2: TTL expired
	}

	@Test
	public void ttlRefreshesOnEachClickSoAStackFillNeverExpiresMidFill()
	{
		// Clicking a stack across several ticks: each click refreshes the TTL, so the prediction
		// survives even though the first click is now older than the TTL.
		final PendingAdds p = new PendingAdds();
		p.record(SHARK, 0);
		p.record(SHARK, 1);
		p.record(SHARK, 2);
		// t3 is 3 ticks past the first click but only 1 past the last: still live.
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(), 3).get(SHARK));
		// No further clicks: expires 2 ticks after the last one (2 + 2 = 4).
		assertNull(p.effectiveBag(bag(), 4).get(SHARK));
	}

	@Test
	public void removalHealsViaTtlAfterTheAddNeverLands()
	{
		// Held one DDS, want a second: an Add click credits +1 on top of the real 1.
		final PendingAdds p = new PendingAdds();
		p.record(DDS, 0);
		assertEquals(Integer.valueOf(2), p.effectiveBag(bag(DDS, 1), 0).get(DDS)); // 1 real + 1 predicted

		// Player removes the held DDS before the add is observed: the read drops to 0. The
		// add-only diff can't reconcile a decrease, so the prediction lingers within the TTL
		// window (the ~2-tick removal-heal delay), then the TTL retires it and the row returns.
		assertEquals(Integer.valueOf(2), p.effectiveBag(bag(), 1).get(DDS)); // t1: still predicted
		assertNull(p.effectiveBag(bag(), 2).get(DDS));                       // t2: healed
	}

	@Test
	public void clearWipesEveryPrediction()
	{
		final PendingAdds p = new PendingAdds();
		p.record(DDS, 0);
		p.clear();
		assertTrue(p.effectiveBag(bag(), 0).isEmpty());
	}

	@Test
	public void effectiveBagDoesNotMutateTheInputBag()
	{
		final PendingAdds p = new PendingAdds();
		p.record(DDS, 0);
		final Map<Integer, Integer> current = bag();
		p.effectiveBag(current, 0);
		assertNull(current.get(DDS));
		assertTrue(current.isEmpty());
	}
}
