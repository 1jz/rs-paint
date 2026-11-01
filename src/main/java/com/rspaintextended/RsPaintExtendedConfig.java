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

	@ConfigItem(
		keyName = "undoKeybind",
		name = "Undo keybind",
		description = "In draw mode or erase mode, press this key to undo your last line or erase."
	)
	default Keybind undoKeybind()
	{
		return new Keybind(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "redoKeybind",
		name = "Redo keybind",
		description = "In draw mode or erase mode, press this key to redo the last change that you undid."
	)
	default Keybind redoKeybind()
	{
		return new Keybind(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK);
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
		description = "Lets the game show through strokes that have a transparent color, and through all strokes if Stroke opacity is less than 100%. Where a stroke crosses itself, it does not get darker. Where a stroke crosses an older stroke, the colors mix. If this is off, all strokes are opaque.",
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
		description = "Changes the opacity of all strokes, if Transparent strokes is on. It multiplies the opacity of each stroke color. At 100%, each stroke keeps the opacity of its color.",
		section = renderingSection,
		position = 3
	)
	default int strokeOpacity()
	{
		return 100;
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
