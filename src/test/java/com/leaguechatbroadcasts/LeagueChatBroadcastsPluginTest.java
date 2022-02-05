package com.leaguechatbroadcasts;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class LeagueChatBroadcastsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LeagueChatBroadcastsPlugin.class);
		RuneLite.main(args);
	}
}