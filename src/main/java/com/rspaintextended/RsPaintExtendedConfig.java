package com.rspaintextended;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
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
}
