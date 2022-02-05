package com.leaguechatbroadcasts;

import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanID;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;
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
	private final Map<String, Set<String>> CLAN_MEMBER_NAMES = new HashMap<>();
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
		for (String key : CLAN_MEMBER_NAMES.keySet()) {
			if (!channelNames.contains(key)) CLAN_MEMBER_NAMES.remove(key);
		}

		for (ClanChannel channel : channels) {
			CLAN_MEMBER_NAMES.put(channel.getName(), getNamesOfMembers(channel));
		}
	}

	@Subscribe
	public void onClanMemberJoined(ClanMemberJoined event) {
		String channelName = event.getClanChannel().getName();
		if (!CLAN_MEMBER_NAMES.containsKey(channelName)) return;
		CLAN_MEMBER_NAMES.get(channelName).add(event.getClanMember().getName());
	}

	@Subscribe
	public void onClanMemberLeft(ClanMemberLeft event) {
		String channelName = event.getClanChannel().getName();
		if (!CLAN_MEMBER_NAMES.containsKey(channelName)) return;
		CLAN_MEMBER_NAMES.get(channelName).remove(event.getClanMember().getName());
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

		String message = Text.sanitize(event.getMessage());
		Matcher regex = expectedFormat.matcher(message);

		if (!regex.matches()) return;

		Set<String> allNames = new HashSet<>();
		CLAN_MEMBER_NAMES.values().forEach(allNames::addAll);

		Optional<String> possibleClanmateName = allNames.stream().filter(message::startsWith).findAny();
		if (!possibleClanmateName.isPresent()) return;
		String clanmateName = possibleClanmateName.get();

		Optional<String> possibleClanName = CLAN_MEMBER_NAMES.entrySet().stream().filter(entry -> entry.getValue().contains(clanmateName)).map(Map.Entry::getKey).findAny();
		if (!possibleClanName.isPresent()) return;
		String clanName = possibleClanName.get();

		Optional<ClanChannel> possibleClan = getClanChannels().stream().filter(clanChannel -> clanChannel.getName().equals(clanName)).findAny();
		if (!possibleClan.isPresent()) return;
		ClanChannel clan = possibleClan.get();

		Optional<ClanChannelMember> possibleClanMember = clan.getMembers().stream().filter(clanMember -> clanMember.getName().equals(clanmateName)).findAny();
		if (!possibleClanMember.isPresent()) return;
		ClanChannelMember clanMember = possibleClanMember.get();

		if (LEAGUE_WORLDS.contains(clanMember.getWorld())) {
			event.getMessageNode().setValue(IconID.LEAGUE + event.getMessage());
		}
	}
}
