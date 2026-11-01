package com.rspaintextended;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup(RsPaintExtendedConfig.GROUP)
public interface RsPaintExtendedConfig extends Config
{
	String GROUP = "rspaintextended";

	@ConfigItem(
		keyName = "drawKeybind",
		name = "Draw keybind",
		description = "Press this key to turn draw mode on or off. In draw mode, click and drag to draw."
	)
	default Keybind drawKeybind()
	{
		return new Keybind(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "eraseKeybind",
		name = "Erase keybind",
		description = "Press this key to turn erase mode on or off. In erase mode, click and drag to erase."
	)
	default Keybind eraseKeybind()
	{
		return new Keybind(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK);
	}

	@ConfigSection(
		name = "Rendering",
		description = "These settings change only how strokes look on your screen. They do not change the strokes that party members get.",
		position = 2
	)
	String renderingSection = "rendering";

	@ConfigItem(
		keyName = "antialiasing",
		name = "Smooth edges",
		description = "Draws strokes with antialiasing (soft line edges).",
		section = renderingSection,
		position = 0
	)
	default boolean antialiasing()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lineStyle",
		name = "Line style",
		description = "Straight connects the points with straight lines. Smooth draws curves near the points and hides small steps. Smooth through points draws curves that touch each point.",
		section = renderingSection,
		position = 1
	)
	default LineStyle lineStyle()
	{
		return LineStyle.SMOOTH;
	}
}
