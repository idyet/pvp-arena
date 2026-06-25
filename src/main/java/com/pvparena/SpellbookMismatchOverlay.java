package com.pvparena;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * Feature 2 — spellbook mismatch.
 *
 * <p>While the unranked duel screen is open on a PvP Arena world and the local player's
 * active-loadout spellbook differs from the opponent's, draws a red outline on the
 * player's spellbook display, the opponent's spellbook display, and the confirm button,
 * plus a warning icon on the confirm button.
 *
 * <p>The comparison is on the <b>display label text</b> (e.g. {@code "Ancient Magicks"}),
 * not varbit values: the loadout spellbook varbit uses a non-standard encoding
 * (Ancient=0, Standard=1, Lunar=2) and the opponent-spellbook varbit is unreliable
 * (shared with transmit-progress state). Both sides render the same label vocabulary,
 * so a string compare is encoding-proof and matches exactly what the player sees.
 * Fails quiet whenever either label is unavailable.
 */
class SpellbookMismatchOverlay extends Overlay
{
	private static final Color OUTLINE_COLOR = Color.RED;
	private static final Stroke OUTLINE_STROKE = new BasicStroke(2f);
	private static final int ICON_PADDING_RIGHT = 6;

	// Local player's three spellbook display widgets, indexed by build slot (A/B/C).
	private static final int[] PLAYER_SPELLBOOK_DISPLAYS = {
		InterfaceID.PvpArenaUnrankedduel._0SPELLBOOK_DISPLAY,
		InterfaceID.PvpArenaUnrankedduel._1SPELLBOOK_DISPLAY,
		InterfaceID.PvpArenaUnrankedduel._2SPELLBOOK_DISPLAY,
	};

	private final Client client;
	private final PvpArenaConfig config;
	private final PvpArenaPlugin plugin;

	private BufferedImage warningIcon;

	@Inject
	SpellbookMismatchOverlay(Client client, PvpArenaConfig config, PvpArenaPlugin plugin, SpriteManager spriteManager)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		spriteManager.getSpriteAsync(SpriteID.PVP_WARNING_ICON, 0, img -> warningIcon = img);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.spellbookMismatch() || !plugin.inPvpArena())
		{
			return null;
		}

		// Interface-layer gate: only act when the unranked duel screen is loaded.
		final Widget root = client.getWidget(InterfaceID.PvpArenaUnrankedduel.UNIVERSE);
		if (root == null || root.isHidden())
		{
			return null;
		}

		final int build = client.getVarbitValue(VarbitID.PVPA_TRANSMIT_BUILD);
		final Widget player = playerSpellbookWidget(build);
		final Widget opponent = client.getWidget(InterfaceID.PvpArenaUnrankedduel.OPPONENTSPELLBOOK_DISPLAY);
		final Widget confirm = client.getWidget(InterfaceID.PvpArenaUnrankedduel.OPPONENT_CONFIRM);

		if (!isMismatch(spellbookLabel(player), spellbookLabel(opponent)))
		{
			return null;
		}

		g.setStroke(OUTLINE_STROKE);
		g.setColor(OUTLINE_COLOR);

		// The player's and opponent's displays live on separate tabs and are never
		// visible at once; outline() skips hidden widgets, so each highlights on its tab.
		outline(g, player);
		outline(g, opponent);
		outline(g, confirm);
		drawWarningIcon(g, confirm);

		return null;
	}

	/**
	 * Pure mismatch decision (unit-tested). Case-insensitive label compare; fails quiet
	 * (returns false) whenever either label is null or blank.
	 */
	static boolean isMismatch(String playerBook, String opponentBook)
	{
		if (playerBook == null || opponentBook == null)
		{
			return false;
		}

		final String p = playerBook.trim();
		final String o = opponentBook.trim();
		if (p.isEmpty() || o.isEmpty())
		{
			return false;
		}

		return !p.equalsIgnoreCase(o);
	}

	private Widget playerSpellbookWidget(int build)
	{
		if (build < 0 || build >= PLAYER_SPELLBOOK_DISPLAYS.length)
		{
			return null;
		}
		return client.getWidget(PLAYER_SPELLBOOK_DISPLAYS[build]);
	}

	/**
	 * The spellbook label lives on a child of the display widget. Reads the widget's own
	 * text first, then the first non-empty child text. Returns null if none.
	 */
	static String spellbookLabel(Widget display)
	{
		if (display == null)
		{
			return null;
		}

		String text = display.getText();
		if (text == null || text.isEmpty())
		{
			final Widget[] children = display.getChildren();
			if (children != null)
			{
				for (Widget child : children)
				{
					if (child != null && child.getText() != null && !child.getText().isEmpty())
					{
						text = child.getText();
						break;
					}
				}
			}
		}

		return text == null ? null : Text.removeTags(text).trim();
	}

	private void outline(Graphics2D g, Widget widget)
	{
		if (widget == null || widget.isHidden())
		{
			return;
		}

		final Rectangle bounds = widget.getBounds();
		if (bounds != null)
		{
			g.draw(bounds);
		}
	}

	private void drawWarningIcon(Graphics2D g, Widget confirm)
	{
		if (warningIcon == null || confirm == null || confirm.isHidden())
		{
			return;
		}

		final Rectangle bounds = confirm.getBounds();
		if (bounds == null)
		{
			return;
		}

		// Right-aligned with padding, vertically centered on the confirm button.
		final int x = bounds.x + bounds.width - warningIcon.getWidth() - ICON_PADDING_RIGHT;
		final int y = bounds.y + (bounds.height - warningIcon.getHeight()) / 2;
		g.drawImage(warningIcon, x, y, null);
	}
}
