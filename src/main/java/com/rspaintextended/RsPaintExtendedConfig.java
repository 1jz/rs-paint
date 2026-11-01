package com.rspaintextended;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

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

	@ConfigItem(
		keyName = "transparentStrokes",
		name = "Transparent strokes",
		description = "Lets the game show through strokes. Where a stroke crosses itself, it does not get darker. Where a stroke crosses an older stroke, the colors mix.",
		section = renderingSection,
		position = 2
	)
	default boolean transparentStrokes()
	{
		return true;
	}

	@Range(min = 10, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "strokeOpacity",
		name = "Stroke opacity",
		description = "How much a stroke hides the game under it, if Transparent strokes is on. At 100%, the stroke hides the game fully.",
		section = renderingSection,
		position = 3
	)
	default int strokeOpacity()
	{
		return 75;
	}

	@ConfigItem(
		keyName = "scaleWidthWithZoom",
		name = "Scale width with zoom",
		description = "Makes strokes wider when you zoom in and thinner when you zoom out, so that they keep their size on the ground. The stroke size on the toolbar is the width at the default zoom in fixed mode.",
		section = renderingSection,
		position = 4
	)
	default boolean scaleWidthWithZoom()
	{
		return true;
	}
}
