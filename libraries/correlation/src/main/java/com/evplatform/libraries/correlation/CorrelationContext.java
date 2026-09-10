package com.evplatform.libraries.correlation;

import java.util.Objects;

/**
 * Thread-scoped correlation and causation identifiers.
 *
 * Propagation primitive for in-process context hand-off (worker threads,
 * async continuations pick the values up explicitly). Callers MUST
 * {@link #clear()} in finally blocks to avoid cross-request leakage on
 * pooled threads.
 */
public final class CorrelationContext {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    /** Immutable snapshot of the two identifiers. */
    public record Context(String correlationId, String causationId) {
        public Context {
            Objects.requireNonNull(correlationId, "correlationId");
        }
    }

    private CorrelationContext() {
    }

    /** @return the current context, or {@code null} when none is set. */
    public static Context current() {
        return HOLDER.get();
    }

    /**
     * @return the current correlation id, generating and binding a fresh one
     *         when the context is absent (entry-point behaviour).
     */
    public static String correlationId() {
        Context c = HOLDER.get();
        if (c == null) {
            String id = CorrelationIds.newId();
            HOLDER.set(new Context(id, null));
            return id;
        }
        return c.correlationId();
    }

    /** @return the current causation id, or {@code null}. */
    public static String causationId() {
        Context c = HOLDER.get();
        return c == null ? null : c.causationId();
    }

    /** Replaces the context for the current thread. */
    public static void set(Context context) {
        HOLDER.set(Objects.requireNonNull(context));
    }

    /** Binds causation for the current thread, keeping the correlation id. */
    public static void setCausation(String causationId) {
        String correlation = correlationId();
        HOLDER.set(new Context(correlation, causationId));
    }

    /** Clears the thread state; mandatory in finally blocks. */
    public static void clear() {
        HOLDER.remove();
    }
}
