package com.rspaintextended;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RsPaintExtendedPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RsPaintExtendedPlugin.class);
		RuneLite.main(args);
	}
}
