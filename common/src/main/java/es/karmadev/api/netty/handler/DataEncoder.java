package es.karmadev.api.netty.handler;

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
