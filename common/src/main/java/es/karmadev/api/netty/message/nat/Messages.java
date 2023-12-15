package es.karmadev.api.netty.message.nat;

/*
 * Copyright 2023 KarmaDev
 *
 * This file is part of NettyChanneling.
 *
 * NettyChanneling is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NettyChanneling is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NettyChanneling. If not, see <http://www.gnu.org/licenses/>.
 */

import lombok.Getter;

/**
 * Represents all the messages
 */
@Getter
public enum Messages {
    KEY_EXCHANGE(0),
    CHANNEL_OPEN(1),
    CHANNEL_CLOSE(2),
    CHANNEL_JOIN(3),
    CHANNEL_LEAVE(4),
    CHANNEL_MESSAGE(5),
    DISCOVER(6),
    //Encoded message, always contains only bytes
    ENCODED(7),
    ACCESS_KEY(8),
    DISCONNECTION(9);

    private final long id;
    private final MessageCache cache = MessageCache.getInstance();

    Messages(final long id) {
        this.id = id;

        cache.setMinId(id);
        cache.setMaxId(id);
    }

    /**
     * Get a message by its ID
     *
     * @param id the message ID
     * @return the message
     */
    public static Messages getById(final long id) {
        for (Messages message : Messages.values()) {
            if (message.id == id) return message;
        }

        return null;
    }

    /**
     * Get if a message is valid. There's no
     * actual "valid" message ID, this method
     * just verifies if the message is sent
     * AS an internal message
     *
     * @param id the message ID
     * @return if the message is internal
     */
    public static boolean isValid(final long id) {
        MessageCache cache = MessageCache.getInstance();
        return cache.isBetween(id);
    }
}
