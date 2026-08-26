package xyz.refineryteam.refinerycore.api.gui.preset;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.interaction.AnvilPrompt;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Anvil-based single-line text input, presented as a GUI preset so it sits
 * alongside the other menu flows. Thin composition over {@link AnvilPrompt}
 * with a simpler signature for the common "ask for a string" case.
 * <p>
 * Usage:
 * <pre>{@code
 * TextInputGUI.of(plugin, "<gray>Name your kit")
 *     .initialText("my-kit")
 *     .validate(text -> text.length() <= 16, "<red>Max 16 characters.")
 *     .onInput((player, text) -> createKit(player, text))
 *     .onCancel(player -> player.sendMessage("Cancelled."))
 *     .open(player);
 * }</pre>
 */
public final class TextInputGUI {

    private final org.bukkit.plugin.Plugin plugin;
    private final String title;
    private String initialText = "";
    private Predicate<String> validator;
    private String validationFailMessage = "<red>Invalid input.";
    private BiConsumer<Player, String> onInput;
    private Consumer<Player> onCancel;

    private TextInputGUI(org.bukkit.plugin.Plugin plugin, @NonNull String title) {
        this.plugin = plugin;
        this.title = title;
    }

    public static @NonNull TextInputGUI of(org.bukkit.plugin.Plugin plugin, @NonNull String title) {
        return new TextInputGUI(plugin, title);
    }

    public @NonNull TextInputGUI initialText(@NonNull String text) {
        this.initialText = text;
        return this;
    }

    /**
     * If the predicate fails, the anvil stays open and {@code failMessage}
     * is shown instead of resolving.
     */
    public @NonNull TextInputGUI validate(@NonNull Predicate<String> validator, @NonNull String failMessage) {
        this.validator = validator;
        this.validationFailMessage = failMessage;
        return this;
    }

    public @NonNull TextInputGUI onInput(@NonNull BiConsumer<Player, String> handler) {
        this.onInput = handler;
        return this;
    }

    public @NonNull TextInputGUI onCancel(@NonNull Consumer<Player> handler) {
        this.onCancel = handler;
        return this;
    }

    /**
     * Opens the input UI for the given player.
     */
    public void open(@NonNull Player target) {
        AnvilPrompt.builder(plugin)
                .title(title)
                .initialText(initialText)
                .validate(validator, validationFailMessage)
                .onInput(onInput)
                .onCancel(onCancel != null ? onCancel : p -> {})
                .open(target);
    }
}
