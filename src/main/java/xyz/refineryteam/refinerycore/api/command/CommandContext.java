package xyz.refineryteam.refinerycore.api.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage;

import java.util.Arrays;
import java.util.Optional;

public final class CommandContext {

    public interface ExecutiveContext {
        void execute();
    }

    private static final String PREFIX = "<gradient:#A78BFA:#7F77DD>Refinery</gradient> <dark_gray>»</dark_gray> ";
    private final CommandSender sender;
    private final String[] args;
    private final String label;

    public CommandContext(@NonNull CommandSender sender, @NonNull String label, @NonNull String[] args) {
        this.sender = sender;
        this.label = label;
        this.args = args;
    }

    public CommandSender sender() {
        return sender;
    }

    public Player player() {
        if (!(sender instanceof Player p)) throw new IllegalStateException("Sender is not a player.");
        return p;
    }

    public boolean isPlayer() {
        return sender instanceof Player;
    }

    public String label() {
        return label;
    }

    public String[] args() {
        return args;
    }

    public int argCount() {
        return args.length;
    }

    public boolean permission(String permission) {
        return sender().hasPermission(permission);
    }

    public Optional<String> arg(int index) {
        if (index < 0 || index >= args.length) return Optional.empty();
        return Optional.of(args[index]);
    }

    public Optional<Integer> argInt(int index) {
        return arg(index).flatMap(s -> {
            try { return Optional.of(Integer.parseInt(s)); }
            catch (NumberFormatException e) { return Optional.empty(); }
        });
    }

    public Optional<Double> argDouble(int index) {
        return arg(index).flatMap(s -> {
            try { return Optional.of(Double.parseDouble(s)); }
            catch (NumberFormatException e) { return Optional.empty(); }
        });
    }

    public Optional<Long> argLong(int index) {
        return arg(index).flatMap(s -> {
            try { return Optional.of(Long.parseLong(s)); }
            catch (NumberFormatException e) { return Optional.empty(); }
        });
    }

    public Optional<Boolean> argBoolean(int index) {
        return arg(index).map(s -> s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes"));
    }

    public <E extends Enum<E>> Optional<E> argEnum(int index, Class<E> type) {
        return arg(index).flatMap(s -> {
            try { return Optional.of(Enum.valueOf(type, s.toUpperCase())); }
            catch (IllegalArgumentException e) { return Optional.empty(); }
        });
    }

    public Optional<Player> argPlayer(int index) {
        return arg(index).map(name -> {
            return sender.getServer().getPlayerExact(name);
        });
    }

    public @NonNull String joinArgs(int fromIndex) {
        if (fromIndex >= args.length) return "";
        return String.join(" ", Arrays.copyOfRange(args, fromIndex, args.length));
    }

    public void reply(@NonNull String miniMessage) {
        sender.sendMessage(EasyMiniMessage.format(miniMessage));
    }

    public void replyRefineryPrefix(String message) {
        reply(PREFIX + message);
    }

    public void replyRaw(@NonNull String plain) {
        sender.sendMessage(plain);
    }

    /**
     * Executes the executive context, and if it fails it doesn't return {@link CommandContext}, but if it executes it does return.
     * @param consumer Consumer to execute
     * @return Nullable CommandContext.
     */
    public @NonNull ExecutiveResult executiveContext(@NonNull ExecutiveContext consumer) {
        try {
            consumer.execute();
            return ExecutiveResult.success(this);
        } catch (Exception e) {
            return ExecutiveResult.failure(this, e);
        }
    }
}