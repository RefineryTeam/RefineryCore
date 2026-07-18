package xyz.refineryteam.refinerycore.api.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Applies a per-player cooldown to a {@link DefaultHandler} or
 * {@link Subcommand} method. Bypassed entirely by senders holding
 * {@code bypassPermission} (if set) or console senders.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Cooldown {
    long value();
    TimeUnit unit() default TimeUnit.SECONDS;
    String bypassPermission() default "";
    String message() default "<red>Wait <white>%time%s</white> before using this again.";
}