package com.pvparena;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

/**
 * Regression guard for the loadouts panel-disappears-on-save bug: the loadout list was
 * persisted under the same {@code pvparena} config key ({@code "loadouts"}) as the feature
 * toggle {@link PvpArenaConfig#loadouts()}, so saving a loadout overwrote the boolean and
 * {@code updateNav()} tore the panel out. A storage key must never collide with a
 * {@link ConfigItem} key in the same group.
 */
public class LoadoutStorageKeyTest
{
	private static Set<String> configItemKeys()
	{
		final Set<String> keys = new HashSet<>();
		for (Method m : PvpArenaConfig.class.getMethods())
		{
			final ConfigItem ci = m.getAnnotation(ConfigItem.class);
			if (ci != null)
			{
				keys.add(ci.keyName());
			}
		}
		return keys;
	}

	@Test
	public void storageKeyDoesNotCollideWithAConfigItem()
	{
		assertFalse(
			"LoadoutManager.KEY \"" + LoadoutManager.KEY + "\" collides with a PvpArenaConfig @ConfigItem keyName; "
				+ "saving loadouts would overwrite that config value",
			configItemKeys().contains(LoadoutManager.KEY));
	}
}
