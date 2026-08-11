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
 * read. Baseline-diff reconciliation means every prediction retires once the real read
 * catches up, with no double-count. Pure like {@link LoadoutDiffTest}; no client deps.
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
		p.record(DDS);
		assertEquals(Integer.valueOf(1), p.effectiveBag(bag()).get(DDS));
	}

	@Test
	public void predictionRetiresWhenRealReadCatchesUp()
	{
		final PendingAdds p = new PendingAdds();
		p.record(DDS);
		// Frame 1: real still lagging (pre-add). Baseline captured at 0, prediction stands.
		assertEquals(Integer.valueOf(1), p.effectiveBag(bag()).get(DDS));
		// Frame N: the add landed in the real read. Prediction retires, no double-count.
		assertEquals(Integer.valueOf(1), p.effectiveBag(bag(DDS, 1)).get(DDS));
		// Prediction is gone: later reads pass through untouched.
		assertEquals(Integer.valueOf(1), p.effectiveBag(bag(DDS, 1)).get(DDS));
	}

	@Test
	public void partialStackStaysPredictedUntilRealFullyCatchesUp()
	{
		// Want three sharks from empty: three rapid clicks, real lags then lands.
		final PendingAdds p = new PendingAdds();
		p.record(SHARK);
		p.record(SHARK);
		p.record(SHARK);
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag()).get(SHARK));          // 0 real + 3 predicted
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(SHARK, 1)).get(SHARK));  // 1 real + 2 predicted
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(SHARK, 2)).get(SHARK));  // 2 real + 1 predicted
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(SHARK, 3)).get(SHARK));  // 3 real, prediction retired
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(SHARK, 3)).get(SHARK));  // pure passthrough
	}

	@Test
	public void baselineIsTheRealCountWhenTheAddBegan()
	{
		// Topping up a held stack: baseline-diff must credit against the real count, not zero.
		final PendingAdds p = new PendingAdds();
		p.record(BREW);
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(BREW, 2)).get(BREW)); // baseline 2, +1 predicted
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(BREW, 3)).get(BREW)); // real caught up, retired
		assertEquals(Integer.valueOf(3), p.effectiveBag(bag(BREW, 3)).get(BREW)); // passthrough
	}

	@Test
	public void clearWipesEveryPrediction()
	{
		final PendingAdds p = new PendingAdds();
		p.record(DDS);
		p.clear();
		assertTrue(p.effectiveBag(bag()).isEmpty());
	}

	@Test
	public void effectiveBagDoesNotMutateTheInputBag()
	{
		final PendingAdds p = new PendingAdds();
		p.record(DDS);
		final Map<Integer, Integer> current = bag();
		p.effectiveBag(current);
		assertNull(current.get(DDS));
		assertTrue(current.isEmpty());
	}
}
