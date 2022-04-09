package com.imaginarycode.minecraft.redisbungee;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerConnectRequest;
import net.md_5.bungee.api.ServerConnectRequest.Result;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Command;

import java.lang.reflect.Array;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.chat.ComponentSerializer;

/**
 * This class contains subclasses that are used for the commands RedisBungee overrides or includes:
 * /glist, /find and /lastseen.
 * <p>
 * All classes use the {@link RedisBungeeAPI}.
 *
 * @author tuxed
 * @since 0.2.3
 */
class RedisBungeeCommands {

	private static final BaseComponent[] NO_PLAYER_SPECIFIED =
			new ComponentBuilder("Du musst einen Spielernamen angeben.").color(ChatColor.RED).create();
	private static final BaseComponent[] PLAYER_NOT_FOUND =
			new ComponentBuilder("Es wurde kein solcher Spieler gefunden.").color(ChatColor.RED).create();
	private static final BaseComponent[] NO_COMMAND_SPECIFIED =
			new ComponentBuilder("Du musst einen Befehl angeben, der ausgeführt werden soll.")
					.color(ChatColor.RED).create();

	private static String playerPlural(int num) {
		return num == 1 ? num + " Spieler ist" : num + " Spieler sind";
	}

	public static class GlistCommand extends Command implements TabExecutor {

		private final RedisBungee plugin;

		GlistCommand(RedisBungee plugin) {
			super("glist", "bungeecord.command.list", "redisbungee", "rglist");
			this.plugin = plugin;
		}

		@Override
		public void execute(final CommandSender sender, final String[] args) {
			plugin.getProxy().getScheduler().runAsync(plugin, () -> {
				int count = RedisBungeeAPI.getRedisBungeeApi().getPlayerCount();

				BaseComponent[] playersOnline = new ComponentBuilder("").color(ChatColor.YELLOW)
						.append(playerPlural(count) + " derzeit online.").create();
				if (args.length > 0 && args[0].equals("showall")) {
					Multimap<String, UUID> serverToPlayers = RedisBungeeAPI.getRedisBungeeApi()
							.getServerToPlayers();
					Multimap<String, String> human = HashMultimap.create();

					serverToPlayers.entries().forEach(entry -> human.put(entry.getKey(),
							plugin.getUuidTranslator().getNameFromUuid(entry.getValue(), false)));

					new TreeSet<>(serverToPlayers.keySet()).forEach(s -> {
						TextComponent serverName = new TextComponent();
						serverName.setColor(ChatColor.GREEN);
						serverName.setText("[" + s + "] ");
						TextComponent serverCount = new TextComponent();
						serverCount.setColor(ChatColor.YELLOW);
						serverCount.setText("(" + serverToPlayers.get(s).size() + "): ");
						TextComponent serverPlayers = new TextComponent();
						serverPlayers.setColor(ChatColor.WHITE);
						serverPlayers.setText(Joiner.on(", ").join(human.get(s)));
						sender.sendMessage(serverName, serverCount, serverPlayers);
					});

					sender.sendMessage(playersOnline);
				} else {
					sender.sendMessage(playersOnline);
					sender.sendMessage(
							new ComponentBuilder("Um alle online Spieler zu sehen, nutze /glist showall.")
									.color(ChatColor.YELLOW).create());
				}
			});
		}

		@Override
		public Iterable<String> onTabComplete(CommandSender commandSender, String[] strings) {
			return strings.length > 1 ? Collections.emptyList() : Collections.singletonList("showall");
		}

	}

	public static class FindCommand extends Command implements TabExecutor {

		private final RedisBungee plugin;

		FindCommand(RedisBungee plugin) {
			super("find", "bungeecord.command.find", "rfind");
			this.plugin = plugin;
		}

		@Override
		public void execute(final CommandSender sender, final String[] args) {
			plugin.getProxy().getScheduler().runAsync(plugin, new Runnable() {
				@Override
				public void run() {
					if (args.length > 0) {
						UUID uuid = plugin.getUuidTranslator().getTranslatedUuid(args[0], true);
						if (uuid == null) {
							sender.sendMessage(PLAYER_NOT_FOUND);
							return;
						}
						ServerInfo si = RedisBungeeAPI.getRedisBungeeApi().getServerFor(uuid);
						if (si != null) {
							TextComponent message = new TextComponent();
							message.setColor(ChatColor.BLUE);
							message.setText(args[0] + " ist auf " + si.getName() + ".");
							sender.sendMessage(message);
						} else {
							sender.sendMessage(PLAYER_NOT_FOUND);
						}
					} else {
						sender.sendMessage(NO_PLAYER_SPECIFIED);
					}
				}
			});
		}

		@Override
		public Iterable<String> onTabComplete(CommandSender commandSender, String[] strings) {
			return strings.length > 1 ? Collections.emptyList()
					: RedisBungeeAPI.getRedisBungeeApi().getHumanPlayersOnline();
		}

	}

	public static class LastSeenCommand extends Command {

		private final RedisBungee plugin;

		LastSeenCommand(RedisBungee plugin) {
			super("lastseen", "redisbungee.command.lastseen", "rlastseen");
			this.plugin = plugin;
		}

		@Override
		public void execute(final CommandSender sender, final String[] args) {
			plugin.getProxy().getScheduler().runAsync(plugin, () -> {
				if (args.length > 0) {
					UUID uuid = plugin.getUuidTranslator().getTranslatedUuid(args[0], true);
					if (uuid == null) {
						sender.sendMessage(PLAYER_NOT_FOUND);
						return;
					}
					long secs = RedisBungeeAPI.getRedisBungeeApi().getLastOnline(uuid);
					TextComponent message = new TextComponent();
					if (secs == 0) {
						message.setColor(ChatColor.GREEN);
						message.setText(args[0] + " ist derzeit online.");
					} else if (secs != -1) {
						message.setColor(ChatColor.BLUE);
						message.setText(
								args[0] + " war zuletzt online am " + new SimpleDateFormat().format(secs) + ".");
					} else {
						message.setColor(ChatColor.RED);
						message.setText(args[0] + " war noch nie online.");
					}
					sender.sendMessage(message);
				} else {
					sender.sendMessage(NO_PLAYER_SPECIFIED);
				}
			});
		}
	}

	public static class IpCommand extends Command implements TabExecutor {

		private final RedisBungee plugin;

		IpCommand(RedisBungee plugin) {
			super("ip", "redisbungee.command.ip", "playerip", "rip", "rplayerip");
			this.plugin = plugin;
		}

		@Override
		public void execute(final CommandSender sender, final String[] args) {
			plugin.getProxy().getScheduler().runAsync(plugin, () -> {
				if (args.length > 0) {
					UUID uuid = plugin.getUuidTranslator().getTranslatedUuid(args[0], true);
					if (uuid == null) {
						sender.sendMessage(PLAYER_NOT_FOUND);
						return;
					}
					InetAddress ia = RedisBungeeAPI.getRedisBungeeApi().getPlayerIp(uuid);
					if (ia != null) {
						TextComponent message = new TextComponent();
						message.setColor(ChatColor.GREEN);
						message.setText(args[0] + " ist verbunden von " + ia + ".");
						sender.sendMessage(message);
					} else {
						sender.sendMessage(PLAYER_NOT_FOUND);
					}
				} else {
					sender.sendMessage(NO_PLAYER_SPECIFIED);
				}
			});
		}

		@Override
		public Iterable<String> onTabComplete(CommandSender commandSender, String[] strings) {
			return strings.length > 1 ? Collections.emptyList()
					: RedisBungeeAPI.getRedisBungeeApi().getHumanPlayersOnline();
		}

	}

	public static class PlayerProxyCommand extends Command implements TabExecutor {

		private final RedisBungee plugin;

		PlayerProxyCommand(RedisBungee plugin) {
			super("pproxy", "redisbungee.command.pproxy");
			this.plugin = plugin;
		}

		@Override
		public void execute(final CommandSender sender, final String[] args) {
			plugin.getProxy().getScheduler().runAsync(plugin, () -> {
				if (args.length > 0) {
					UUID uuid = plugin.getUuidTranslator().getTranslatedUuid(args[0], true);
					if (uuid == null) {
						sender.sendMessage(PLAYER_NOT_FOUND);
						return;
					}
					String proxy = RedisBungeeAPI.getRedisBungeeApi().getProxy(uuid);
					if (proxy != null) {
						TextComponent message = new TextComponent();
						message.setColor(ChatColor.GREEN);
						message.setText(args[0] + " ist verbunden mit " + proxy + ".");
						sender.sendMessage(message);
					} else {
						sender.sendMessage(PLAYER_NOT_FOUND);
					}
				} else {
					sender.sendMessage(NO_PLAYER_SPECIFIED);
				}
			});
		}

		@Override
		public Iterable<String> onTabComplete(CommandSender commandSender, String[] strings) {
			return strings.length > 1 ? Collections.emptyList()
					: RedisBungeeAPI.getRedisBungeeApi().getHumanPlayersOnline();
		}

	}

	public static class SendToAll extends Command {

		private final RedisBungee plugin;

		SendToAll(RedisBungee plugin) {
			super("sendtoall", "redisbungee.command.sendtoall", "rsendtoall");
			this.plugin = plugin;
		}

		@Override
		public void execute(CommandSender sender, String[] args) {
			if (args.length > 0) {
				String command = Joiner.on(" ").skipNulls().join(args);
				RedisBungeeAPI.getRedisBungeeApi().sendProxyCommand(command);
				TextComponent message = new TextComponent();
				message.setColor(ChatColor.GREEN);
				message.setText("Sende den Befehl " + command + " an alle Proxys.");
				sender.sendMessage(message);
			} else {
				sender.sendMessage(NO_COMMAND_SPECIFIED);
			}
		}
	}

	public static class ServerIds extends Command {

		public ServerIds() {
			super("serverids", "redisbungee.command.serverids");
		}

		@Override
		public void execute(CommandSender sender, String[] strings) {
			TextComponent textComponent = new TextComponent();
			textComponent.setText("Alle Server-IDs: " + Joiner.on(", ")
					.join(RedisBungeeAPI.getRedisBungeeApi().getAllServers()));
			textComponent.setColor(ChatColor.YELLOW);
			sender.sendMessage(textComponent);
		}

	}

	public static class PlistCommand extends Command implements TabExecutor {

		private final RedisBungee plugin;

		PlistCommand(RedisBungee plugin) {
			super("plist", "redisbungee.command.plist", "rplist");
			this.plugin = plugin;
		}

		@Override
		public void execute(final CommandSender sender, final String[] args) {
			plugin.getProxy().getScheduler().runAsync(plugin, () -> {
				String proxy = args.length >= 1 ? args[0] : RedisBungee.getConfiguration().getServerId();

				if (proxy.equals("showall")) {

					for (String server : RedisBungeeAPI.getRedisBungeeApi().getAllServers()) {
						Set<UUID> playersOnProxy = RedisBungeeAPI.getRedisBungeeApi().getPlayersOnProxy(server);

						Multimap<String, String> human = HashMultimap.create();

						for (UUID uuid : playersOnProxy) {
							human.put(server, plugin.getUuidTranslator().getNameFromUuid(uuid, false));
						}

						TextComponent serverName = new TextComponent();
						serverName.setColor(ChatColor.GOLD);
						serverName.setText("[" + server + "] ");
						TextComponent serverCount = new TextComponent();
						serverCount.setColor(ChatColor.YELLOW);
						serverCount.setText("(" + human.get(server).size() + "): ");
						TextComponent serverPlayers = new TextComponent();
						serverPlayers.setColor(ChatColor.WHITE);
						serverPlayers.setText(Joiner.on(", ").join(human.get(server)));
						sender.sendMessage(serverName, serverCount, serverPlayers);
					}

					return;
				}

				if (!plugin.getServerIds().contains(proxy)) {
					sender.sendMessage(new ComponentBuilder(
							proxy + " ist keine gültige Proxy. Benutze /serverids für gültigen Proxys.")
							.color(ChatColor.RED).create());
					return;
				}
				Set<UUID> players = RedisBungeeAPI.getRedisBungeeApi().getPlayersOnProxy(proxy);
				BaseComponent[] playersOnline = new ComponentBuilder("").color(ChatColor.YELLOW)
						.append(playerPlural(players.size()) + " derzeit auf " + proxy + ".").create();
				if (args.length >= 2 && args[1].equals("showall")) {
					Multimap<String, UUID> serverToPlayers = RedisBungeeAPI.getRedisBungeeApi()
							.getServerToPlayers();
					Multimap<String, String> human = HashMultimap.create();
					for (Map.Entry<String, UUID> entry : serverToPlayers.entries()) {
						if (players.contains(entry.getValue())) {
							human.put(entry.getKey(),
									plugin.getUuidTranslator().getNameFromUuid(entry.getValue(), false));
						}
					}
					for (String server : new TreeSet<>(human.keySet())) {
						TextComponent serverName = new TextComponent();
						serverName.setColor(ChatColor.RED);
						serverName.setText("[" + server + "] ");
						TextComponent serverCount = new TextComponent();
						serverCount.setColor(ChatColor.YELLOW);
						serverCount.setText("(" + human.get(server).size() + "): ");
						TextComponent serverPlayers = new TextComponent();
						serverPlayers.setColor(ChatColor.WHITE);
						serverPlayers.setText(Joiner.on(", ").join(human.get(server)));
						sender.sendMessage(serverName, serverCount, serverPlayers);
					}
					sender.sendMessage(playersOnline);
				} else {
						sender.sendMessage(playersOnline);
						sender.sendMessage(new ComponentBuilder(
								"Um alle online Spieler zu sehen, benutze /plist " + proxy + " showall.")
								.color(ChatColor.YELLOW).create());


				}
			});
		}

		@Override
		public Iterable<String> onTabComplete(CommandSender commandSender, String[] strings) {
			return strings.length > 1 ?
					(strings.length > 2 ? Collections.emptyList() : Collections.singletonList("showall")) :
					Stream.concat(RedisBungeeAPI.getRedisBungeeApi().getAllServers().stream(), Stream.of("showall")).collect(Collectors.toList());
		}

	}

	public static class DebugCommand extends Command {

		private final RedisBungee plugin;

		DebugCommand(RedisBungee plugin) {
			super("rdebug", "redisbungee.command.debug");
			this.plugin = plugin;
		}

		@Override
		public void execute(final CommandSender sender, final String[] args) {
			TextComponent poolActiveStat = new TextComponent(
					"Derzeit aktive Poolobjekte: " + this.plugin.getPool().getNumActive());
			TextComponent poolIdleStat = new TextComponent(
					"Derzeit ungenutzte Poolobjekte: " + this.plugin.getPool().getNumIdle());
			TextComponent poolWaitingStat = new TextComponent(
					"Warten auf freie Objekte: " + this.plugin.getPool().getNumWaiters());
			sender.sendMessage(poolActiveStat);
			sender.sendMessage(poolIdleStat);
			sender.sendMessage(poolWaitingStat);
		}
	}

	public static class AlertCommand extends Command implements TabExecutor {

		public AlertCommand() {
			super("alert", "redisbungee.command.alert", "broadcast");
		}

		@Override
		public void execute(CommandSender sender, String[] args) {
			if (args.length == 0) {
				sender.sendMessage(ProxyServer.getInstance().getTranslation("message_needed"));
			} else {
				StringBuilder builder = new StringBuilder();
				if (args[0].startsWith("&h")) {
					// Remove &h
					args[0] = args[0].substring(2, args[0].length());
				} else {
					builder.append(ProxyServer.getInstance().getTranslation("alert"));
				}

				for (String s : args) {
					builder.append(ChatColor.translateAlternateColorCodes('&', s));
					builder.append(" ");
				}

				String message = builder.substring(0, builder.length() - 1);

				RedisBungeeAPI.getRedisBungeeApi().broadcastMessage(TextComponent.fromLegacyText(message));
			}
		}

		@Override
		public Iterable<String> onTabComplete(CommandSender commandSender, String[] strings) {
			return Collections.emptyList();
		}

	}


	public static class AlertRawCommand extends Command implements TabExecutor {

		public AlertRawCommand() {
			super("alertraw", "redisbungee.command.alertraw", "broadcastraw");
		}

		@Override
		public void execute(CommandSender sender, String[] args) {
			if (args.length == 0) {
				sender.sendMessage(ProxyServer.getInstance().getTranslation("message_needed"));
			} else {
				String message = Joiner.on(' ').join(args);

				try {
					BaseComponent[] parse = ComponentSerializer.parse(message);
					RedisBungeeAPI.getRedisBungeeApi().broadcastMessage(parse);
				} catch (Exception e) {
					Throwable error = e;
					while (error.getCause() != null) {
						error = error.getCause();
					}
					if (sender instanceof ProxiedPlayer) {
						sender.sendMessage(new ComponentBuilder(ProxyServer.getInstance().getTranslation("error_occurred_player"))
								.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(error.getMessage())
										.color(ChatColor.RED)
										.create()))
								.create()
						);
					} else {
						sender.sendMessage(ProxyServer.getInstance().getTranslation("error_occurred_console", error.getMessage()));
					}
				}
			}

		}

		@Override
		public Iterable<String> onTabComplete(CommandSender commandSender, String[] strings) {
			return Collections.emptyList();
		}

	}

	public static class SendCommand extends Command implements TabExecutor {

		protected static class SendCallback {

			private final Map<ServerConnectRequest.Result, List<String>> results = new HashMap<>();
			private final CommandSender sender;
			private int count = 0;

			public SendCallback(CommandSender sender) {
				this.sender = sender;
				for (ServerConnectRequest.Result result : ServerConnectRequest.Result.values()) {
					results.put(result, new ArrayList<>());
				}
			}

			public void lastEntryDone() {
				sender.sendMessage(ChatColor.GREEN.toString() + ChatColor.BOLD + "Send Results:");
				for (Map.Entry<ServerConnectRequest.Result, List<String>> entry : results.entrySet()) {
					ComponentBuilder builder = new ComponentBuilder("");
					if (!entry.getValue().isEmpty()) {
						builder.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
								new ComponentBuilder(Joiner.on(", ").join(entry.getValue())).color(ChatColor.YELLOW).create()));
					}
					builder.append(entry.getKey().name() + ": ").color(ChatColor.GREEN);
					builder.append("" + entry.getValue().size()).bold(true);
					sender.sendMessage(builder.create());
				}
			}

			public static class Entry implements Callback<ServerConnectRequest.Result> {

				private final SendCallback callback;
				private final ProxiedPlayer player;
				private final ServerInfo target;

				public Entry(SendCallback callback, ProxiedPlayer player, ServerInfo target) {
					this.callback = callback;
					this.player = player;
					this.target = target;
					this.callback.count++;
				}

				@Override
				public void done(ServerConnectRequest.Result result, Throwable error) {
					callback.results.get(result).add(player.getName());
					if (result == ServerConnectRequest.Result.SUCCESS) {
						player.sendMessage(ProxyServer.getInstance().getTranslation("you_got_summoned", target.getName(), callback.sender.getName()));
					}

					if (--callback.count == 0) {
						callback.lastEntryDone();
					}
				}
			}
		}

		public SendCommand() {
			super("send", "redisbungee.command.send");
		}

		@Override
		public void execute(CommandSender sender, String[] args) {
			if (args.length != 2) {
				sender.sendMessage(ProxyServer.getInstance().getTranslation("send_cmd_usage"));
				return;
			}
			ServerInfo server = ProxyServer.getInstance().getServerInfo(args[1]);
			if (server == null) {
				sender.sendMessage(ProxyServer.getInstance().getTranslation("no_server"));
				return;
			}

			List<UUID> targets;
			if (args[0].equalsIgnoreCase("all")) {
				targets = new ArrayList<>(RedisBungeeAPI.getRedisBungeeApi().getPlayersOnline());
			} else if (args[0].equalsIgnoreCase("current")) {
				if (!(sender instanceof ProxiedPlayer)) {
					sender.sendMessage(ProxyServer.getInstance().getTranslation("player_only"));
					return;
				}
				ProxiedPlayer player = (ProxiedPlayer) sender;
				targets = new ArrayList<>(RedisBungeeAPI.getRedisBungeeApi().getPlayersOnServer(player.getServer().getInfo().getName()));
			} else {
				// If we use a server name, send the entire server. This takes priority over players.
				ServerInfo serverTarget = ProxyServer.getInstance().getServerInfo(args[0]);
				if (serverTarget != null) {
					targets = new ArrayList<>(RedisBungeeAPI.getRedisBungeeApi().getPlayersOnServer(serverTarget.getName()));
				} else {
					UUID uuid = RedisBungeeAPI.getRedisBungeeApi().getUuidFromName(args[0]);
					if (uuid == null) {
						sender.sendMessage(ProxyServer.getInstance().getTranslation("user_not_online"));
						return;
					}
					targets = Collections.singletonList(uuid);
				}
			}

			for (UUID target : targets) {
				RedisBungeeAPI.getRedisBungeeApi().sendPlayerToServer(target, server.getName());
			}

			sender.sendMessage(ChatColor.DARK_GREEN + "Attempting to send " + targets.size() + " players to " + server.getName());
		}

		@Override
		public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
			if (args.length > 2 || args.length == 0) {
				return ImmutableSet.of();
			}

			Set<String> matches = new HashSet<>();
			if (args.length == 1) {
				String search = args[0].toLowerCase(Locale.ROOT);
				for (String playerName : RedisBungeeAPI.getRedisBungeeApi().getHumanPlayersOnline()) {
					if (playerName.toLowerCase(Locale.ROOT).startsWith(search)) {
						matches.add(playerName);
					}
				}
				if ("all".startsWith(search)) {
					matches.add("all");
				}
				if ("current".startsWith(search)) {
					matches.add("current");
				}
			} else {
				String search = args[1].toLowerCase(Locale.ROOT);
				for (String server : ProxyServer.getInstance().getServers().keySet()) {
					if (server.toLowerCase(Locale.ROOT).startsWith(search)) {
						matches.add(server);
					}
				}
			}
			return matches;
		}

	}

}
