package org.abdullahcxd.consumers;

/**
 * Boolean result consumer is a functional interface method that accepts a generic type as
 * its value and results in a boolean.
 * @param <Object>
 */
@FunctionalInterface
public interface BooleanResultConsumer<Object> {

    boolean accept(Object value);

}
