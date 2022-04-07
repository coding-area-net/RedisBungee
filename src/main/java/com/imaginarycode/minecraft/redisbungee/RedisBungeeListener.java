package com.imaginarycode.minecraft.redisbungee;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.imaginarycode.minecraft.redisbungee.events.PubSubMessageEvent;
import com.imaginarycode.minecraft.redisbungee.util.RedisCallable;
import lombok.AllArgsConstructor;
import net.md_5.bungee.api.AbstractReconnectHandler;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.net.InetAddress;
import java.util.*;

@AllArgsConstructor
public class RedisBungeeListener implements Listener {
    private static final BaseComponent[] ALREADY_LOGGED_IN =
            new ComponentBuilder("Sie sind bereits auf diesem Server eingeloggt.").color(ChatColor.RED)
                    .append("\n\nVersuchen Sie bitte, sich in ein paar Minuten erneut anzumelden.\n Wenn dies Ihr Problem nicht behebt, wenden Sie sich bitte an das Personal.")
                    .color(ChatColor.GRAY)
                    .create();
    private static final BaseComponent[] ONLINE_MODE_RECONNECT =
            new ComponentBuilder("Huch! Du musst die Verbindung wiederherstellen.").color(ChatColor.RED)
                    .append("\n\nWir haben jemanden gefunden, der deinen Benutzernamen benutzt. Er wurde rausgeschmissen und Du kannst dich wieder einloggen.\n Wenn dies nicht funktioniert, wenden Sie sich bitte an das Personal.")
                    .color(ChatColor.GRAY)
                    .create();
    private final RedisBungee plugin;
    private final List<InetAddress> exemptAddresses;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(final LoginEvent event) {
        event.registerIntent(plugin);
        plugin.getProxy().getScheduler().runAsync(plugin, new RedisCallable<Void>(plugin) {
            @Override
            protected Void call(Jedis jedis) {
                try {
                    if (event.isCancelled()) {
                        return null;
                    }

                    // We make sure they aren't trying to use an existing player's name.
                    // This is problematic for online-mode servers as they always disconnect old clients.
                    if (plugin.getProxy().getConfig().isOnlineMode()) {
                        ProxiedPlayer player = plugin.getProxy().getPlayer(event.getConnection().getName());

                        if (player != null) {
                            event.setCancelled(true);
                            // TODO: Make it accept a BaseComponent[] like everything else.
                            event.setCancelReason(ONLINE_MODE_RECONNECT);
                            return null;
                        }
                    }

                    for (String s : plugin.getServerIds()) {
                        if (jedis.sismember("proxy:" + s + ":usersOnline", event.getConnection().getUniqueId().toString())) {
                            event.setCancelled(true);
                            // TODO: Make it accept a BaseComponent[] like everything else.
                            event.setCancelReason(ALREADY_LOGGED_IN);
                            return null;
                        }
                    }
                    return null;
                } finally {
                    event.completeIntent(plugin);
                }
            }
        });
    }

    @EventHandler
    public void onPostLogin(final PostLoginEvent event) {
        plugin.getProxy().getScheduler().runAsync(plugin, new RedisCallable<Void>(plugin) {
            @Override
            protected Void call(Jedis jedis) {
                // this code was moved out from login event due being async..
                // and it can be cancelled but it will show as false in redis-bungee
                // which will register the player into the redis database.
                Pipeline pipeline = jedis.pipelined();
                plugin.getUuidTranslator().persistInfo(event.getPlayer().getName(), event.getPlayer().getUniqueId(), pipeline);
                RedisUtil.createPlayer(event.getPlayer(), pipeline, false);
                pipeline.sync();
                // the end of moved code.

                jedis.publish("redisbungee-data", RedisBungee.getGson().toJson(new DataManager.DataManagerMessage<>(
                        event.getPlayer().getUniqueId(), DataManager.DataManagerMessage.Action.JOIN,
                        new DataManager.LoginPayload(event.getPlayer().getAddress().getAddress()))));
                return null;
            }
        });
    }

    @EventHandler
    public void onPlayerDisconnect(final PlayerDisconnectEvent event) {
        plugin.getProxy().getScheduler().runAsync(plugin, new RedisCallable<Void>(plugin) {
            @Override
            protected Void call(Jedis jedis) {
                Pipeline pipeline = jedis.pipelined();
                RedisUtil.cleanUpPlayer(event.getPlayer().getUniqueId().toString(), pipeline);
                pipeline.sync();
                return null;
            }
        });
    }

    @EventHandler
    public void onServerChange(final ServerConnectedEvent event) {
        final String currentServer = event.getPlayer().getServer() == null ? null : event.getPlayer().getServer().getInfo().getName();
        plugin.getProxy().getScheduler().runAsync(plugin, new RedisCallable<Void>(plugin) {
            @Override
            protected Void call(Jedis jedis) {
                jedis.hset("player:" + event.getPlayer().getUniqueId().toString(), "server", event.getServer().getInfo().getName());
                jedis.publish("redisbungee-data", RedisBungee.getGson().toJson(new DataManager.DataManagerMessage<>(
                        event.getPlayer().getUniqueId(), DataManager.DataManagerMessage.Action.SERVER_CHANGE,
                        new DataManager.ServerChangePayload(event.getServer().getInfo().getName(), currentServer))));
                return null;
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPing(final ProxyPingEvent event) {
        if (exemptAddresses.contains(event.getConnection().getAddress().getAddress())) {
            return;
        }

        ServerInfo forced = AbstractReconnectHandler.getForcedHost(event.getConnection());

        if (forced != null && event.getConnection().getListener().isPingPassthrough()) {
            return;
        }

        event.getResponse().getPlayers().setOnline(plugin.getCount());
    }

    @SuppressWarnings("UnstableApiUsage")
    @EventHandler
    public void onPluginMessage(final PluginMessageEvent event) {
        if ((event.getTag().equals("legacy:redisbungee") || event.getTag().equals("RedisBungee")) && event.getSender() instanceof Server) {
            final String currentChannel = event.getTag();
            final byte[] data = Arrays.copyOf(event.getData(), event.getData().length);
            plugin.getProxy().getScheduler().runAsync(plugin, () -> {
                ByteArrayDataInput in = ByteStreams.newDataInput(data);

                String subchannel = in.readUTF();
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                String type;

                switch (subchannel) {
                    case "PlayerList":
                        out.writeUTF("PlayerList");
                        Set<UUID> original = Collections.emptySet();
                        type = in.readUTF();
                        if (type.equals("ALL")) {
                            out.writeUTF("ALL");
                            original = plugin.getPlayers();
                        } else {
                            try {
                                original = RedisBungeeAPI.getRedisBungeeApi().getPlayersOnServer(type);
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        Set<String> players = new HashSet<>();
                        for (UUID uuid : original)
                            players.add(plugin.getUuidTranslator().getNameFromUuid(uuid, false));
                        out.writeUTF(Joiner.on(',').join(players));
                        break;
                    case "PlayerCount":
                        out.writeUTF("PlayerCount");
                        type = in.readUTF();
                        if (type.equals("ALL")) {
                            out.writeUTF("ALL");
                            out.writeInt(plugin.getCount());
                        } else {
                            out.writeUTF(type);
                            try {
                                out.writeInt(RedisBungeeAPI.getRedisBungeeApi().getPlayersOnServer(type).size());
                            } catch (IllegalArgumentException e) {
                                out.writeInt(0);
                            }
                        }
                        break;
                    case "LastOnline":
                        String user = in.readUTF();
                        out.writeUTF("LastOnline");
                        out.writeUTF(user);
                        out.writeLong(RedisBungeeAPI.getRedisBungeeApi().getLastOnline(plugin.getUuidTranslator().getTranslatedUuid(user, true)));
                        break;
                    case "ServerPlayers":
                        String type1 = in.readUTF();
                        out.writeUTF("ServerPlayers");
                        Multimap<String, UUID> multimap = RedisBungeeAPI.getRedisBungeeApi().getServerToPlayers();

                        boolean includesUsers;

                        switch (type1) {
                            case "COUNT":
                                includesUsers = false;
                                break;
                            case "PLAYERS":
                                includesUsers = true;
                                break;
                            default:
                                // TODO: Should I raise an error?
                                return;
                        }

                        out.writeUTF(type1);

                        if (includesUsers) {
                            Multimap<String, String> human = HashMultimap.create();
                            for (Map.Entry<String, UUID> entry : multimap.entries()) {
                                human.put(entry.getKey(), plugin.getUuidTranslator().getNameFromUuid(entry.getValue(), false));
                            }
                            serializeMultimap(human, true, out);
                        } else {
                            serializeMultiset(multimap.keys(), out);
                        }
                        break;
                    case "Proxy":
                        out.writeUTF("Proxy");
                        out.writeUTF(RedisBungee.getConfiguration().getServerId());
                        break;
                    case "PlayerProxy":
                        String username = in.readUTF();
                        out.writeUTF("PlayerProxy");
                        out.writeUTF(username);
                        out.writeUTF(RedisBungeeAPI.getRedisBungeeApi().getProxy(plugin.getUuidTranslator().getTranslatedUuid(username, true)));
                        break;
                    default:
                        return;
                }

                ((Server) event.getSender()).sendData(currentChannel, out.toByteArray());
            });
        }
    }

    private void serializeMultiset(Multiset<String> collection, ByteArrayDataOutput output) {
        output.writeInt(collection.elementSet().size());
        for (Multiset.Entry<String> entry : collection.entrySet()) {
            output.writeUTF(entry.getElement());
            output.writeInt(entry.getCount());
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void serializeMultimap(Multimap<String, String> collection, boolean includeNames, ByteArrayDataOutput output) {
        output.writeInt(collection.keySet().size());
        for (Map.Entry<String, Collection<String>> entry : collection.asMap().entrySet()) {
            output.writeUTF(entry.getKey());
            if (includeNames) {
                serializeCollection(entry.getValue(), output);
            } else {
                output.writeInt(entry.getValue().size());
            }
        }
    }

    private void serializeCollection(Collection<?> collection, ByteArrayDataOutput output) {
        output.writeInt(collection.size());
        for (Object o : collection) {
            output.writeUTF(o.toString());
        }
    }

    @EventHandler
    public void onPubSubMessage(PubSubMessageEvent event) {
        String channel = event.getChannel();
        if (channel.equals("redisbungee-command-allservers") || channel.equals("redisbungee-command-" + RedisBungeeAPI.getRedisBungeeApi().getServerId())) {
            String message = event.getMessage();
            if (message.startsWith("/"))
                message = message.substring(1);
            plugin.getLogger().info("Aufrufen eines PubSub Befehls: /" + message);
            plugin.getProxy().getPluginManager().dispatchCommand(RedisBungeeCommandSender.instance, message);
        } else if (channel.equals("redisbungee-message-allservers") || channel.equals("redisbungee-message-" + RedisBungeeAPI.getRedisBungeeApi().getServerId())) {
            plugin.getLogger().info("Receiving pub chat message");

            JSONObject jsonObject = new JSONObject(event.getMessage());
            ProxiedPlayer receiver = null;
            if (jsonObject.has("receiver")) {
                UUID uuid = UUID.fromString(jsonObject.getString("receiver"));
                receiver = ProxyServer.getInstance().getPlayer(uuid);
                if (receiver == null) {
                    return; // Player not on this proxy
                }
            }

            String rawMessage = jsonObject.getString("message");
            BaseComponent[] components = ComponentSerializer.parse(rawMessage);

            if (receiver == null) {
                ProxyServer.getInstance().broadcast(components);
            } else {
                receiver.sendMessage(components);
            }

        }
    }
}
