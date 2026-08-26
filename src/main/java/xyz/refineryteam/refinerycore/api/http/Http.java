package xyz.refineryteam.refinerycore.api.http;

import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.JsonElement;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Minimal async HTTP client for plugin-sized needs: update checks, Discord
 * webhooks, REST APIs. Runs on a dedicated daemon pool (never the common
 * ForkJoinPool or the main thread), with retry/backoff built in.
 * <p>
 * Usage:
 * <pre>{@code
 * Http http = Http.of(plugin);
 *
 * http.get("https://api.example.com/version")
 *     .header("Authorization", "Bearer " + token)
 *     .executeAsync()
 *     .thenAccept(response -> {
 *         if (response.ok()) handle(response.body());
 *     });
 *
 * http.post(webhookUrl)
 *     .jsonBody("{\"content\":\"Server started\"}")
 *     .retries(2)
 *     .executeAsync();
 * }</pre>
 */
public final class Http {

    private final JavaPlugin plugin;
    private final HttpClient client;

    private Http(@NonNull JavaPlugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
                    Thread t = new Thread(r, "RefineryCore-HTTP");
                    t.setDaemon(true);
                    return t;
                }))
                .build();
    }

    /**
     * Creates an HTTP client bound to the given plugin. Retry backoff is
     * scheduled through the plugin's async scheduler.
     *
     * @param plugin the owning plugin
     * @return a new client; call {@link #close()} from onDisable if desired
     */
    public static @NonNull Http of(@NonNull JavaPlugin plugin) {
        return new Http(plugin);
    }

    /**
     * Starts building a GET request.
     *
     * @param url the absolute URL to request
     * @return a request builder
     */
    public @NonNull Request get(@NonNull String url) {
        return new Request(this, "GET", url);
    }

    /**
     * Starts building a POST request.
     *
     * @param url the absolute URL to request
     * @return a request builder
     */
    public @NonNull Request post(@NonNull String url) {
        return new Request(this, "POST", url);
    }

    /**
     * Starts building a PUT request.
     *
     * @param url the absolute URL to request
     * @return a request builder
     */
    public @NonNull Request put(@NonNull String url) {
        return new Request(this, "PUT", url);
    }

    /**
     * Starts building a DELETE request.
     *
     * @param url the absolute URL to request
     * @return a request builder
     */
    public @NonNull Request delete(@NonNull String url) {
        return new Request(this, "DELETE", url);
    }

    /**
     * Shuts down the underlying client. Call from onDisable if you own the
     * instance; safe to skip since threads are daemons.
     */
    public void close() {
        client.close();
    }

    public static final class Request {
        private final Http parent;
        private final String method;
        private final String url;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private String body;
        private String contentType = "application/json";
        private int retries = 0;
        private Duration timeout = Duration.ofSeconds(15);

        private Request(@NonNull Http parent, @NonNull String method, @NonNull String url) {
            this.parent = parent;
            this.method = method;
            this.url = url;
        }

        /**
         * Adds a request header.
         *
         * @param name  header name, e.g. {@code "Authorization"}
         * @param value header value
         * @return this builder
         */
        public @NonNull Request header(@NonNull String name, @NonNull String value) {
            headers.put(name, value);
            return this;
        }

        /**
         * Sets a JSON body and Content-Type in one call.
         *
         * @param json the raw JSON string to send
         * @return this builder
         */
        public @NonNull Request jsonBody(@NonNull String json) {
            this.body = json;
            this.contentType = "application/json";
            return this;
        }

        /**
         * Sets an arbitrary body with an explicit content type.
         *
         * @param body        the raw body string
         * @param contentType MIME type, e.g. {@code "text/plain"}
         * @return this builder
         */
        public @NonNull Request body(@NonNull String body, @NonNull String contentType) {
            this.body = body;
            this.contentType = contentType;
            return this;
        }

        /**
         * Form-encoded convenience: builds
         * {@code key1=value1&key2=value2} with the matching content type.
         *
         * @param fields form fields; keys and values are URL-encoded
         * @return this builder
         */
        public @NonNull Request formBody(@NonNull Map<String, String> fields) {
            StringBuilder out = new StringBuilder();
            fields.forEach((k, v) -> {
                if (out.length() > 0) out.append('&');
                out.append(java.net.URLEncoder.encode(k, java.nio.charset.StandardCharsets.UTF_8))
                   .append('=')
                   .append(java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8));
            });
            return body(out.toString(), "application/x-www-form-urlencoded");
        }

        /**
         * How many times to retry on IOException or 5xx before giving up.
         * Backoff is exponential: 500ms, 1s, 2s...
         *
         * @param retries number of retry attempts after the first try
         * @return this builder
         */
        public @NonNull Request retries(int retries) {
            this.retries = Math.max(0, retries);
            return this;
        }

        /**
         * Per-attempt timeout. Default 15 seconds.
         *
         * @param timeout maximum time for a single attempt
         * @return this builder
         */
        public @NonNull Request timeout(@NonNull Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Executes asynchronously, returning the final response after any
         * retries. Never blocks the calling thread; never throws — errors
         * arrive as a completed-exceptionally future.
         *
         * @return future completing with the final {@link Response}
         */
        public @NonNull CompletableFuture<Response> executeAsync() {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout);

            headers.forEach(builder::header);
            if (body != null && !method.equals("GET") && !method.equals("DELETE")) {
                builder.header("Content-Type", contentType);
                builder.method(method, HttpRequest.BodyPublishers.ofString(body));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpRequest request = builder.build();
            return attempt(request, 0);
        }

        /**
         * Executes synchronously on the calling thread. Only use off the
         * main thread.
         *
         * @return the final {@link Response}; transport failures yield a
         *         response with status -1 and the error set
         */
        public @NonNull Response execute() {
            try {
                return executeAsync().join();
            } catch (java.util.concurrent.CompletionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                return new Response(-1, "", cause);
            }
        }

        private @NonNull CompletableFuture<Response> attempt(HttpRequest request, int attemptNumber) {
            return parent.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenCompose(resp -> {
                        int status = resp.statusCode();
                        boolean retryable = status >= 500;
                        if (retryable && attemptNumber < retries) {
                            return backoff(request, attemptNumber);
                        }
                        return CompletableFuture.completedFuture(new Response(status, resp.body(), null));
                    })
                    .exceptionally(throwable -> {
                        Throwable cause = throwable instanceof java.util.concurrent.CompletionException ce && ce.getCause() != null
                                ? ce.getCause() : throwable;
                        if (cause instanceof IOException && attemptNumber < retries) {
                            return backoffJoin(request, attemptNumber);
                        }
                        return new Response(-1, "", cause);
                    });
        }

        private @NonNull CompletableFuture<Response> backoff(HttpRequest request, int attemptNumber) {
            long delayMs = 500L << attemptNumber;
            CompletableFuture<Response> future = new CompletableFuture<>();
            parent.plugin.getServer().getAsyncScheduler().runDelayed(parent.plugin, t ->
                    attempt(request, attemptNumber + 1).whenComplete((r, ex) -> {
                        if (ex != null) future.completeExceptionally(ex);
                        else future.complete(r);
                    }), delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            return future;
        }

        private @NonNull Response backoffJoin(HttpRequest request, int attemptNumber) {
            try {
                return backoff(request, attemptNumber).join();
            } catch (java.util.concurrent.CompletionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                return new Response(-1, "", cause);
            }
        }
    }

    /**
     * A completed HTTP exchange.
     */
    public record Response(int statusCode, @NonNull String body, @Nullable Throwable error) {

        /** True for 2xx statuses. */
        public boolean ok() {
            return statusCode >= 200 && statusCode < 300;
        }

        /**
         * Parses the body as JSON. Paper ships Gson, so this works without
         * any extra dependency.
         *
         * @return parsed JSON element, or null when the response is not OK
         *         or the body isn't valid JSON
         */
        public @Nullable JsonElement json() {
            if (!ok()) return null;
            try {
                return com.google.gson.JsonParser.parseString(body);
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * The error that prevented completion, or null if the request went
         * through (even with a non-2xx status).
         *
         * @return the transport-level error, or null
         */
        public @Nullable Throwable error() {
            return error;
        }
    }
}
