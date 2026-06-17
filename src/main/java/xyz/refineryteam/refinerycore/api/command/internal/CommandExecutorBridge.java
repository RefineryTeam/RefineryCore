package xyz.refineryteam.refinerycore.api.command.internal;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import xyz.refineryteam.refinerycore.api.command.CommandContext;
import xyz.refineryteam.refinerycore.api.command.RefineryCommand;
import xyz.refineryteam.refinerycore.api.command.annotation.DefaultHandler;
import xyz.refineryteam.refinerycore.api.command.annotation.PlayerOnly;
import xyz.refineryteam.refinerycore.api.command.annotation.Subcommand;
import xyz.refineryteam.refinerycore.api.command.help.HelpMenu;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class CommandExecutorBridge implements CommandExecutor, TabCompleter {

    private final RefineryCommand instance;
    private final Map<String, Method> subcommandMap = new HashMap<>();
    private Method defaultHandler = null;

    public CommandExecutorBridge(@NonNull RefineryCommand instance) {
        this.instance = instance;
        scan();
    }

    private void scan() {
        for (Method method : instance.getClass().getDeclaredMethods()) {
            method.setAccessible(true);

            if (method.isAnnotationPresent(Subcommand.class)) {
                Subcommand annotation = method.getAnnotation(Subcommand.class);
                subcommandMap.put(annotation.value().toLowerCase(), method);
            }

            if (method.isAnnotationPresent(DefaultHandler.class)) {
                defaultHandler = method;
            }
        }
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String @NonNull [] args) {
        CommandContext context = new CommandContext(sender, label, args);

        xyz.refineryteam.refinerycore.api.command.annotation.Command rootMeta = instance.getClass().getAnnotation(xyz.refineryteam.refinerycore.api.command.annotation.Command.class);
        if (rootMeta != null && !rootMeta.permission().isEmpty() && !sender.hasPermission(rootMeta.permission())) {
            instance.onPermissionDenied(context);
            return true;
        }

        if (args.length == 0 || !subcommandMap.containsKey(args[0].toLowerCase())) {
            if (defaultHandler != null) {
                dispatch(defaultHandler, context);
            } else {
                instance.onNoMatch(context);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            HelpMenu.send(sender, instance);
            return true;
        }

        Method method = subcommandMap.get(args[0].toLowerCase());
        CommandContext subContext = new CommandContext(sender, label, Arrays.copyOfRange(args, 1, args.length));

        Subcommand annotation = method.getAnnotation(Subcommand.class);

        if (!annotation.permission().isEmpty() && !sender.hasPermission(annotation.permission())) {
            instance.onPermissionDenied(subContext);
            return true;
        }

        if (method.isAnnotationPresent(PlayerOnly.class) && !(sender instanceof org.bukkit.entity.Player)) {
            instance.onPlayerOnly(subContext);
            return true;
        }

        dispatch(method, subContext);
        return true;
    }

    private void dispatch(Method method, CommandContext context) {
        try {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 0) {
                method.invoke(instance);
            } else if (params.length == 1 && params[0] == CommandContext.class) {
                method.invoke(instance, context);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to dispatch command method: " + method.getName(), e);
        }
    }

    @Override
    public @NonNull List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String @NonNull [] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Map.Entry<String, Method> entry : subcommandMap.entrySet()) {
                Subcommand annotation = entry.getValue().getAnnotation(Subcommand.class);
                if (!annotation.permission().isEmpty() && !sender.hasPermission(annotation.permission())) continue;
                if (entry.getKey().startsWith(partial)) suggestions.add(entry.getKey());
            }
            return suggestions;
        }

        if (args.length >= 2) {
            String sub = args[0].toLowerCase();
            Method method = subcommandMap.get(sub);
            if (method == null) return suggestions;

            Subcommand annotation = method.getAnnotation(Subcommand.class);
            String[] completions = annotation.completions();
            int argIndex = args.length - 2;

            if (argIndex < completions.length) {
                String partial = args[args.length - 1].toLowerCase();
                String spec = completions[argIndex];

                suggestions.addAll(resolveCompletionSpec(sender, spec, partial));
            }
        }

        return suggestions;
    }

    private List<String> resolveCompletionSpec(CommandSender sender, @NonNull String spec, String partial) {
        return switch (spec) {
            case "<player>" -> sender.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .toList();
            case "<boolean>" -> Stream.of("true", "false")
                    .filter(s -> s.startsWith(partial))
                    .toList();
            case "<number>" -> List.of();
            default -> {
                if (spec.startsWith("<enum:")) {
                    String className = spec.substring(6, spec.length() - 1);
                    yield resolveEnumCompletions(className, partial);
                }
                List<String> literals = Arrays.asList(spec.split("\\|"));
                yield literals.stream().filter(s -> s.toLowerCase().startsWith(partial)).toList();
            }
        };
    }

    private List<String> resolveEnumCompletions(String className, String partial) {
        try {
            Class<?> clazz = Class.forName(className);
            if (!clazz.isEnum()) return List.of();
            return Arrays.stream(clazz.getEnumConstants())
                    .map(e -> ((Enum<?>) e).name().toLowerCase())
                    .filter(s -> s.startsWith(partial))
                    .toList();
        } catch (ClassNotFoundException e) {
            return List.of();
        }
    }
}