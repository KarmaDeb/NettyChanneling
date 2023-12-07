package es.karmadev.api.netty.future;

import es.karmadev.api.channel.future.Future;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SimpleFuture implements Future {

    private final List<Consumer<Future>> handlers = new ArrayList<>();
    private boolean complete;

    private boolean success;
    private Throwable error;

    /**
     * Get if the task is yet
     * not complete
     *
     * @return if the task is complete
     */
    @Override
    public boolean isComplete() {
        return complete;
    }

    /**
     * Get if the task was successful, this
     * method always returns false unless
     * {@link #isComplete()} is true, in that
     * case the return method will depend on
     * if the task was successful or not
     *
     * @return the task status
     */
    @Override
    public boolean isSuccess() {
        return complete && success;
    }

    /**
     * In case of the task unsuccessful state,
     * this method will return the error (if any)
     * that caused the task to not complete.
     *
     * @return the task error cause
     */
    @Override
    public @Nullable Throwable getCause() {
        return error;
    }

    /**
     * Complete the future
     *
     * @param result the result
     * @param error the error
     */
    public void complete(final boolean result, final Throwable error) {
        if (this.complete) return;

        this.complete = true;
        this.success = result;
        this.error = error;

        handlers.forEach((c) -> c.accept(this));
    }

    /**
     * Add a future listener
     *
     * @param onComplete the future complete
     *                   listener
     */
    @Override
    public void addListener(final Consumer<Future> onComplete) {
        if (complete) {
            onComplete.accept(this);
            return;
        }

        handlers.add(onComplete);
    }
}
