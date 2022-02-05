package com.leaguechatbroadcasts;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanID;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.WorldsFetch;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import net.runelite.http.api.worlds.World;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
	name = "League Chat Broadcasts"
)
public class LeagueChatBroadcastsPlugin extends Plugin
{
	private final Map<String, Set<String>> CLAN_NAMES = new HashMap<>();
	private Set<Integer> LEAGUE_WORLDS = new HashSet<>();

	@Inject
	private Client client;


	@Inject
	private WorldService worldService;

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN) {
			LEAGUE_WORLDS = worldService.getWorlds().getWorlds().stream()
					.filter(world -> world.getTypes().contains(net.runelite.http.api.worlds.WorldType.SEASONAL))
					.map(World::getId)
					.collect(Collectors.toSet());
		}

	}

	private List<ClanChannel> getClanChannels() {
		return Arrays.asList(
				client.getClanChannel(ClanID.CLAN),
				client.getGuestClanChannel(),
				client.getClanChannel(ClanID.GROUP_IRONMAN)
		);
	}

	private Set<String> getNamesOfMembers(ClanChannel channel) {
		return channel.getMembers().stream()
				.map(ClanChannelMember::getName)
				.map(Text::toJagexName)
				.collect(Collectors.toSet());
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event) {
		List<ClanChannel> channels = getClanChannels().stream().filter(Objects::nonNull).collect(Collectors.toList());

		List<String> channelNames = channels.stream().map(ClanChannel::getName).collect(Collectors.toList());
		for (String key : CLAN_NAMES.keySet()) {
			if (!channelNames.contains(key)) CLAN_NAMES.remove(key);
		}

		for (ClanChannel channel : channels) {
			CLAN_NAMES.put(channel.getName(), getNamesOfMembers(channel));
		}
	}

	@Subscribe
	public void onClanMemberJoined(ClanMemberJoined event) {
		String channelName = event.getClanChannel().getName();
		if (!CLAN_NAMES.containsKey(channelName)) return;
		CLAN_NAMES.get(channelName).add(event.getClanMember().getName());
	}

	@Subscribe
	public void onClanMemberLeft(ClanMemberLeft event) {
		String channelName = event.getClanChannel().getName();
		if (!CLAN_NAMES.containsKey(channelName)) return;
		CLAN_NAMES.get(channelName).remove(event.getClanMember().getName());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		switch (event.getType()) {
			case CLAN_MESSAGE:
			case CLAN_GIM_MESSAGE:
			case CLAN_GUEST_MESSAGE:
				break;
			default:
				return;
		}

		// All loot drops we care about have a few words followed by a colon followed by the drop
		Pattern expectedFormat = Pattern.compile("[\\w\\s]+:.*");

		String message = event.getMessage();
		Matcher regex = expectedFormat.matcher(message);
		if (!regex.matches()) return;

		String playerName = event.getMessage().substring(0, message.indexOf(" "));

		CLAN_NAMES.entrySet().stream().filter(entry -> entry.getValue().contains(playerName)).findFirst().ifPresent(entry -> {
			String clanName = entry.getKey();

			ClanChannel channel = getClanChannels().stream().filter(clanChannel -> clanChannel.getName().equals(clanName)).findFirst().orElse(null);
			if (channel == null) return;

			ClanChannelMember clanmate = channel.getMembers().stream().filter(member -> member.getName().equals(playerName)).findFirst().orElse(null);
			if (clanmate == null) return;

			if (LEAGUE_WORLDS.contains(clanmate.getWorld())) {
				event.getMessageNode().setValue(IconID.LEAGUE + event.getMessage());
			}
		});
	}
}
