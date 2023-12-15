package es.karmadev.api.netty;

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

import es.karmadev.api.channel.data.BaseMessage;

import java.io.Closeable;
import java.util.function.Consumer;

/**
 * Represents a netty
 * write-able object
 */
public interface Writeable extends Closeable {

    /**
     * Push a message directly to the
     * netty object
     *
     * @param message the message
     */
    void push(final BaseMessage message);

    /**
     * Get if the writeable object
     * is ready to process elements
     *
     * @return if the object
     * is ready
     */
    boolean isReady();

    /**
     * Mark the object as ready to
     * start processing elements
     */
    void markReady();

    /**
     * Add a message to the que
     *
     * @param message the message
     */
    void addToQue(final BaseMessage message);

    /**
     * Process the que of messages
     *
     * @param consumer the message consumer
     */
    void processQue(final Consumer<BaseMessage> consumer);
}
