package com.rspaintextended;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "RsPaint Extended",
	description = "Draw on the ground.",
	tags = {"paint", "draw", "ground", "marker", "tile", "color", "art"}
)
public class RsPaintExtendedPlugin extends Plugin
{
	@Provides RsPaintExtendedConfig provideConfig(ConfigManager configManager) { return configManager.getConfig(RsPaintExtendedConfig.class); }
	@Inject KeyManager keyManager;
	@Inject MouseManager mouseManager;
	@Inject RsPaintExtendedOverlay overlay;
	@Inject EventBus eventBus;
	@Inject OverlayManager overlayManager;
	@Inject PartyCanvasSync partySync;
	@Inject RsPaintExtendedPanel panel;
	@Inject ClientToolbar clientToolbar;

	private NavigationButton navigationButton;

	@Override
	protected void startUp() throws Exception
	{
		overlay.startUp();
		overlayManager.add(overlay);
		mouseManager.registerMouseListener(overlay);
		keyManager.registerKeyListener(overlay.drawHotkey);
		keyManager.registerKeyListener(overlay.eraseHotkey);
		keyManager.registerKeyListener(overlay.undoRedoKeys);
		eventBus.register(overlay);

		partySync.startUp();
		eventBus.register(partySync);

		eventBus.register(panel);
		navigationButton = NavigationButton.builder()
			.tooltip("RsPaint Extended")
			.icon(RsPaintExtendedPanel.createIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		panel.refresh();
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		mouseManager.unregisterMouseListener(overlay);
		keyManager.unregisterKeyListener(overlay.drawHotkey);
		keyManager.unregisterKeyListener(overlay.eraseHotkey);
		keyManager.unregisterKeyListener(overlay.undoRedoKeys);
		eventBus.unregister(overlay);

		eventBus.unregister(partySync);
		partySync.shutDown();

		// All members show again on the next start, so the panel must rebuild its switches.
		eventBus.unregister(panel);
		panel.reset();
		clientToolbar.removeNavigation(navigationButton);
	}

	@Value
	static class StrokePoint {
		int x, y;
		float subx, suby;
	}

	static class Stroke {
		// The stored width is in screen pixels. If "Scale width with zoom" is on, the overlay changes it with the zoom.
		static final int MIN_WIDTH = 1;
		static final int MAX_WIDTH = 100;

		/** Returns the width, or 1 if the width is not a number from MIN_WIDTH to MAX_WIDTH. */
		static double validWidth(double width)
		{
			return width >= MIN_WIDTH && width <= MAX_WIDTH ? width : 1;
		}

		/** From the stroke that draws first, at the bottom, to the stroke that draws last, on top. */
		static final Comparator<Stroke> DRAW_ORDER = Comparator.comparingLong((Stroke s) -> s.order).thenComparing(s -> s.id);

		// A random 63-bit number in base 36. It is shorter than a UUID, and only one member's strokes must not collide.
		String id = Long.toString(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE, Character.MAX_RADIX);
		List<StrokePoint> pts = new ArrayList<>();
		Color color;
		double strokeWidth;
		// The draw order from StrokeClock.
		long order;

		public int primaryRegion()
		{
			return new WorldPoint(pts.get(0).getX(), pts.get(0).getY(), 0).getRegionID();
		}
	}
}
