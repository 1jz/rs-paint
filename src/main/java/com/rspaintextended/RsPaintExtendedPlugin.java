package com.rspaintextended;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
		String id = UUID.randomUUID().toString();
		List<StrokePoint> pts = new ArrayList<>();
		Color color;
		double strokeWidth;

		public int primaryRegion()
		{
			return new WorldPoint(pts.get(0).getX(), pts.get(0).getY(), 0).getRegionID();
		}
	}
}
