package es.karmadev.api.netty.handler;

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
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.AllArgsConstructor;

import java.util.function.BiFunction;

@AllArgsConstructor
public final class DataEncoder extends MessageToByteEncoder<BaseMessage> {

    private final BiFunction<BaseMessage, Channel, BaseMessage> encodeFunction;

    @Override
    protected void encode(final ChannelHandlerContext ctx, final BaseMessage message, final ByteBuf out) {
        BaseMessage encoded = encodeFunction.apply(message, ctx.channel());

        out.writeLong(encoded.getId());
        out.writeBytes(encoded.readAll());
    }
}
