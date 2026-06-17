package xyz.refineryteam.refinerycore.api.command;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public final class ExecutiveResult {

    private final boolean success;
    private final Exception error;
    private final CommandContext context;

    private ExecutiveResult(CommandContext context, boolean success, Exception error) {
        this.context = context;
        this.success = success;
        this.error = error;
    }

    @Contract(value = "_ -> new", pure = true)
    static @NonNull ExecutiveResult success(CommandContext context) {
        return new ExecutiveResult(context, true, null);
    }

    @Contract(value = "_, _ -> new", pure = true)
    static @NonNull ExecutiveResult failure(CommandContext context, Exception error) {
        return new ExecutiveResult(context, false, error);
    }

    public ExecutiveResult onSuccess(@NonNull Runnable consumer) {
        if (success) consumer.run();
        return this;
    }

    public ExecutiveResult onFailure(@NonNull Runnable consumer) {
        if (!success) consumer.run();
        return this;
    }

    public ExecutiveResult onFailure(java.util.function.Consumer<Exception> consumer) {
        if (!success && error != null) consumer.accept(error);
        return this;
    }

    public boolean succeeded() {
        return success;
    }

    public CommandContext context() {
        return context;
    }
}