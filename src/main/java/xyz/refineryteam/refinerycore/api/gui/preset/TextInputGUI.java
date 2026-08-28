package xyz.refineryteam.refinerycore.api.gui.preset;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.interaction.ChatPrompt;
import xyz.refineryteam.refinerycore.api.minimessage.EasyMiniMessage;
import xyz.refineryteam.refinerycore.api.text.Placeholders;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Chat-based single-line text input, presented as a GUI preset so it sits
 * alongside the other menu flows. Thin composition over {@link ChatPrompt}
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

    private final Plugin plugin;
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

    /**
     * Starts building a text input for the given plugin.
     *
    * @param plugin the plugin opening the prompt
    * @param title  MiniMessage prompt message
     * @return a new builder
     */
    public static @NonNull TextInputGUI of(@NonNull Plugin plugin, @NonNull String title) {
        return new TextInputGUI(plugin, title);
    }

    /**
    * Retained for API compatibility; chat prompts cannot pre-fill input.
     *
     * @param text initial text shown in the input
     * @return this builder
     */
    public @NonNull TextInputGUI initialText(@NonNull String text) {
        this.initialText = text;
        return this;
    }

    /**
    * If the predicate fails, the prompt remains active and {@code failMessage}
    * is shown instead of resolving.
     *
     * @param validator   predicate the submitted text must pass
     * @param failMessage MiniMessage error shown when validation fails
     * @return this builder
     */
    public @NonNull TextInputGUI validate(@NonNull Predicate<String> validator, @NonNull String failMessage) {
        this.validator = validator;
        this.validationFailMessage = failMessage;
        return this;
    }

    /**
     * Sets the submit handler.
     *
     * @param handler receives the player and the validated text
     * @return this builder
     */
    public @NonNull TextInputGUI onInput(@NonNull BiConsumer<Player, String> handler) {
        this.onInput = handler;
        return this;
    }

    /**
    * Sets the cancel handler, invoked when the player types {@code cancel}.
     *
     * @param handler receives the player who cancelled
     * @return this builder
     */
    public @NonNull TextInputGUI onCancel(@NonNull Consumer<Player> handler) {
        this.onCancel = handler;
        return this;
    }

    /**
    * Opens the input prompt for the given player.
     *
    * @param target the player who will type the response
     */
    public void open(@NonNull Player target) {
        ChatPrompt prompt = ChatPrompt.of(plugin, target)
                .prompt(title)
                .onInput(text -> {
                    if (validator != null && !validator.test(text)) {
                        target.sendMessage(EasyMiniMessage.format(Placeholders.apply(validationFailMessage, target)));
                        open(target);
                        return;
                    }
                    if (onInput != null) onInput.accept(target, text);
                })
                .onCancel(() -> {
                    if (onCancel != null) onCancel.accept(target);
                });
        prompt.send();
    }
}
