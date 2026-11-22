package org.evchargingplatform.support;

import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * {@link ObjectProvider} that returns the given bean, or behaves as an empty provider when it is
 * {@code null}. Avoids Mockito's inability to mock ObjectProvider on Java 25. Generic version of
 * the per-test providers in the reservation and charging service tests.
 */
public final class TestObjectProvider<T> implements ObjectProvider<T> {
    private final T bean;

    private TestObjectProvider(T bean) {
        this.bean = bean;
    }

    public static <T> TestObjectProvider<T> of(T bean) {
        return new TestObjectProvider<>(bean);
    }

    public static <T> TestObjectProvider<T> empty() {
        return new TestObjectProvider<>(null);
    }

    @Override
    public T getObject() {
        return bean;
    }

    @Override
    public T getObject(Object... args) {
        return bean;
    }

    @Override
    public T getIfAvailable() {
        return bean;
    }

    @Override
    public T getIfUnique() {
        return bean;
    }

    @Override
    public void ifAvailable(Consumer<T> consumer) {
        if (bean != null) {
            consumer.accept(bean);
        }
    }

    @Override
    public void ifUnique(Consumer<T> consumer) {
        ifAvailable(consumer);
    }

    @Override
    public Iterator<T> iterator() {
        return bean == null ? Collections.emptyIterator() : Collections.singleton(bean).iterator();
    }

    @Override
    public Stream<T> stream() {
        return bean == null ? Stream.empty() : Stream.of(bean);
    }
}
