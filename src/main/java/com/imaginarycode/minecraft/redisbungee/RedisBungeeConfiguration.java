package com.imaginarycode.minecraft.redisbungee;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import lombok.Getter;
import net.md_5.bungee.config.Configuration;
import redis.clients.jedis.JedisPool;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;

public class RedisBungeeConfiguration {

    @Getter
    private final JedisPool pool;
    @Getter
    private final String serverId;
    @Getter
    private final boolean registerBungeeCommands;
    @Getter
    private final List<InetAddress> exemptAddresses;


    public RedisBungeeConfiguration(JedisPool pool, Configuration configuration, String randomUUID) {
        this.pool = pool;

        if (configuration.getBoolean("use-random-id-string", false)) {
            this.serverId = configuration.getString("server-id") + "-" + randomUUID;
        } else {
            this.serverId = configuration.getString("server-id");
        }

        this.registerBungeeCommands = configuration.getBoolean("register-bungee-commands", true);

        ImmutableList.Builder<InetAddress> addressBuilder = ImmutableList.builder();

        configuration.getStringList("exempt-ip-addresses")
                .forEach(s -> addressBuilder.add(InetAddresses.forString(s)));


        this.exemptAddresses = addressBuilder.build();
    }

}
