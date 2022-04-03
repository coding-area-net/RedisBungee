package com.imaginarycode.minecraft.redisbungee;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Command;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import net.md_5.bungee.api.plugin.TabExecutor;

/**
 * This class contains subclasses that are used for the commands RedisBungee overrides or includes: /glist, /find and /lastseen.
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
            new ComponentBuilder("Du musst einen Befehl angeben, der ausgeführt werden soll.").color(ChatColor.RED).create();

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
                    Multimap<String, UUID> serverToPlayers = RedisBungeeAPI.getRedisBungeeApi().getServerToPlayers();
                    Multimap<String, String> human = HashMultimap.create();

                    serverToPlayers.entries().forEach(entry -> human.put(entry.getKey(), plugin.getUuidTranslator().getNameFromUuid(entry.getValue(), false)));

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
                    sender.sendMessage(new ComponentBuilder("Um alle online Spieler zu sehen, nutze /glist showall.").color(ChatColor.YELLOW).create());
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
                            message.setText(args[0] + " is on " + si.getName() + ".");
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
            return strings.length > 1 ? Collections.emptyList() : RedisBungeeAPI.getRedisBungeeApi().getHumanPlayersOnline();
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
                        message.setText(args[0] + " war zuletzt online am " + new SimpleDateFormat().format(secs) + ".");
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
            return strings.length > 1 ? Collections.emptyList() : RedisBungeeAPI.getRedisBungeeApi().getHumanPlayersOnline();
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
            return strings.length > 1 ? Collections.emptyList() : RedisBungeeAPI.getRedisBungeeApi().getHumanPlayersOnline();
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

    public static class ServerId extends Command {
        private final RedisBungee plugin;

        ServerId(RedisBungee plugin) {
            super("serverid", "redisbungee.command.serverid", "rserverid");
            this.plugin = plugin;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            TextComponent textComponent = new TextComponent();
            textComponent.setText("You are on " + this.plugin.getServerIds() + ".");
            textComponent.setColor(ChatColor.YELLOW);
            sender.sendMessage(textComponent);
        }
    }

    public static class ServerIds extends Command {
        public ServerIds() {
            super("serverids", "redisbungee.command.serverids");
        }

        @Override
        public void execute(CommandSender sender, String[] strings) {
            TextComponent textComponent = new TextComponent();
            textComponent.setText("Alle Server-IDs: " + Joiner.on(", ").join(RedisBungeeAPI.getRedisBungeeApi().getAllServers()));
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
                if (!plugin.getServerIds().contains(proxy)) {
                    sender.sendMessage(new ComponentBuilder(proxy + " ist keine gültige Proxy. Benutze /serverids für gültigen Proxys.").color(ChatColor.RED).create());
                    return;
                }
                Set<UUID> players = RedisBungeeAPI.getRedisBungeeApi().getPlayersOnProxy(proxy);
                BaseComponent[] playersOnline = new ComponentBuilder("").color(ChatColor.YELLOW)
                        .append(playerPlural(players.size()) + " derzeit auf Proxy " + proxy + ".").create();
                if (args.length >= 2 && args[1].equals("showall")) {
                    Multimap<String, UUID> serverToPlayers = RedisBungeeAPI.getRedisBungeeApi().getServerToPlayers();
                    Multimap<String, String> human = HashMultimap.create();
                    for (Map.Entry<String, UUID> entry : serverToPlayers.entries()) {
                        if (players.contains(entry.getValue())) {
                            human.put(entry.getKey(), plugin.getUuidTranslator().getNameFromUuid(entry.getValue(), false));
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
                    sender.sendMessage(new ComponentBuilder("Um alle online Spieler zu sehen, benutze /plist " + proxy + " showall.").color(ChatColor.YELLOW).create());
                }
            });
        }

        @Override
        public Iterable<String> onTabComplete(CommandSender commandSender, String[] strings) {
            return strings.length > 1 ?
                (strings.length > 2 ?  Collections.emptyList() : Collections.singletonList("showall")) :
                RedisBungeeAPI.getRedisBungeeApi().getAllServers();
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
            TextComponent poolActiveStat = new TextComponent("Derzeit aktive Poolobjekte: " + this.plugin.getPool().getNumActive());
            TextComponent poolIdleStat = new TextComponent("Derzeit ungenutzte Poolobjekte: " + this.plugin.getPool().getNumIdle());
            TextComponent poolWaitingStat = new TextComponent("Warten auf freie Objekte: " + this.plugin.getPool().getNumWaiters());
            sender.sendMessage(poolActiveStat);
            sender.sendMessage(poolIdleStat);
            sender.sendMessage(poolWaitingStat);
        }
    }
}
