package com.imaginarycode.minecraft.redisbungee.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import net.md_5.bungee.api.plugin.Event;

/**
 * This event is posted when a PubSub message is received.
 * <p>
 * <strong>Warning</strong>: This event is fired in a separate thread!
 *
 * @since 0.2.6
 */

@Getter
@ToString
@RequiredArgsConstructor
public class PubSubMessageEvent extends Event {
    private final String channel;
    private final String message;
}
