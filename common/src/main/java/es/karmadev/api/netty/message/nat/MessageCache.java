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

@Getter
class MessageCache {

    @Getter
    private final static MessageCache instance = new MessageCache();

    private long minId;
    private long maxId;

    private MessageCache() {}

    public void setMinId(final long newId) {
        if (newId < minId) minId = newId;
    }

    public void setMaxId(final long newId) {
        if (newId > maxId) maxId = newId;
    }

    public boolean isBetween(final long id) {
        return id >= minId && id <= maxId;
    }
}
