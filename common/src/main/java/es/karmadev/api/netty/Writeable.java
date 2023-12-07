package es.karmadev.api.netty;

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
