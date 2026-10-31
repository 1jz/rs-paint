package com.rspaintextended;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "RsPaint Extended",
	description = "Draw on the ground.",
	tags = {"paint", "draw", "ground", "marker", "tile", "color", "art"}
)
public class RsPaintExtendedPlugin extends Plugin
{
	@Inject Client client;
	@Provides RsPaintExtendedConfig provideConfig(ConfigManager configManager) { return configManager.getConfig(RsPaintExtendedConfig.class); }
	@Inject RsPaintExtendedConfig config;
	@Inject KeyManager keyManager;
	@Inject MouseManager mouseManager;
	@Inject RsPaintExtendedOverlay overlay;
	@Inject EventBus eventBus;
	@Inject OverlayManager overlayManager;

	@Override
	protected void startUp() throws Exception
	{
		overlay.startUp();
		overlayManager.add(overlay);
		mouseManager.registerMouseListener(overlay);
		keyManager.registerKeyListener(overlay);
		eventBus.register(overlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		mouseManager.unregisterMouseListener(overlay);
		keyManager.unregisterKeyListener(overlay);
		eventBus.unregister(overlay);
		overlayManager.remove(overlay);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
	}

	@Value
	static class StrokePoint {
		int x, y;
		float subx, suby;
	}

	static class Stroke {
		List<StrokePoint> pts = new ArrayList<>();
		Color color;
		double strokeWidth;
//		List<Integer> regions = new ArrayList<>();

		public int primaryRegion()
		{
			return new WorldPoint(pts.get(0).getX(), pts.get(0).getY(), 0).getRegionID();
		}
	}
}
