package xyz.refineryteam.refinerycore.api.minimessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;

public class EasyMiniMessage {

    private static final MiniMessage mini = MiniMessage.miniMessage();

    public static @NonNull Component format(@NonNull String content) {
        return mini.deserialize(content);
    }

    public static @NonNull Component format(@NonNull String content, @NonNull TagResolver... resolvers) {
        return mini.deserialize(content, resolvers);
    }

}