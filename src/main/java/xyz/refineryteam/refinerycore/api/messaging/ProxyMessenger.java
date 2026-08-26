package xyz.refineryteam.refinerycore.api.messaging;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Sends and receives plugin messages over the modern
 * {@code BungeeCord→Velocity} compatible channel that Velocity's
 * {@code bungee-plugin-message-channel} support exposes. This is the
 * standard way for a backend Paper plugin to ask the proxy to move players
 * between servers, forward chat, or broadcast custom payloads — no extra
 * proxy-side mod needed for the built-in subchannels.
 * <p>
 * <b>Requires</b> {@code settings.toml → proxies.velocity.enable = true} on
 * modern setups; the channel is registered under
 * {@code "BungeeCord"} exactly as legacy clients expect.
 * <p>
 * Usage (send):
 * <pre>{@code
 * ProxyMessenger messenger = new ProxyMessenger(plugin);
 *
 * // Built-in: move a player to another backend server.
 * messenger.connect(player, "survival-2");
 *
 * // Built-in: forward a custom payload to every backend server,
 * // received by your own plugin on each server via onMessage().
 * messenger.forwardToAll("myplugin:sync", dataBytes);
 * }</pre>
 * Usage (receive):
 * <pre>{@code
 * messenger.onMessage("myplugin:sync", (player, data) -> {
 *     String name = new String(data, StandardCharsets.UTF_8);
 *     handleRemoteJoin(name);
 * });
 * }</pre>
 */
public final class ProxyMessenger {

    /** The legacy-compatible channel Velocity also listens on. */
    public static final String CHANNEL = "BungeeCord";

    private final Plugin plugin;
    private final Map<String, BiConsumer<Player, byte[]>> handlers = new ConcurrentHashMap<>();

    /**
     * Creates a messenger and registers the proxy channel on the plugin's
     * behalf. Call {@link #shutdown()} from onDisable.
     *
     * @param plugin the owning plugin; channels are registered under it
     */
    public ProxyMessenger(@NonNull Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, (channel, player, message) -> {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subchannel = in.readUTF();
            BiConsumer<Player, byte[]> handler = handlers.get(subchannel);
            if (handler != null) {
                // ByteArrayDataInput can't report its read position, so
                // re-derive the payload: the subchannel header is a 2-byte
                // modified-UTF8 length prefix plus the characters themselves.
                int headerBytes = 2 + subchannel.length();
                byte[] payload = new byte[Math.max(0, message.length - headerBytes)];
                System.arraycopy(message, headerBytes, payload, 0, payload.length);
                handler.accept(player, payload);
            }
        });
    }

    /**
     * Registers a handler for a custom subchannel. The receiving player is
     * whoever the proxy used to deliver the message (often arbitrary) —
     * treat it as transport context, not as the message subject.
     *
     * @param subchannel the subchannel name to listen for
     * @param handler    receives the transport player and the payload bytes
     */
    public void onMessage(@NonNull String subchannel, @NonNull BiConsumer<Player, byte[]> handler) {
        handlers.put(subchannel, handler);
    }

    /**
     * Removes a previously registered handler.
     *
     * @param subchannel the subchannel whose handler is removed
     */
    public void offMessage(@NonNull String subchannel) {
        handlers.remove(subchannel);
    }

    /**
     * Asks the proxy to connect the player to another backend server.
     *
     * @param player     the player to move
     * @param serverName the target backend server name
     */
    public void connect(@NonNull Player player, @NonNull String serverName) {
        var out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName);
        player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }

    /**
     * Asks the proxy to connect a named player (possibly not online on
     * this server) to another backend server.
     *
     * @param playerName the player to move
     * @param serverName the target backend server name
     */
    public void connectOther(@NonNull String playerName, @NonNull String serverName) {
        requireAnyPlayer();
        var out = ByteStreams.newDataOutput();
        out.writeUTF("ConnectOther");
        out.writeUTF(playerName);
        out.writeUTF(serverName);
        dispatch(out.toByteArray());
    }

    /**
     * Forwards a custom payload to the same subchannel on every other
     * backend server. Pair with {@link #onMessage(String, BiConsumer)} on
     * the receiving side.
     *
     * @param subchannel the subchannel to forward on
     * @param payload    the raw payload bytes
     */
    public void forwardToAll(@NonNull String subchannel, byte @NonNull [] payload) {
        requireAnyPlayer();
        var out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF("ALL");
        out.writeUTF(subchannel);
        out.writeShort(payload.length);
        out.write(payload);
        dispatch(out.toByteArray());
    }

    /**
     * Same as {@link #forwardToAll(String, byte[])} but targeted at one backend
     * server by name.
     *
     * @param serverName the target backend server name
     * @param subchannel the subchannel to forward on
     * @param payload    the raw payload bytes
     */
    public void forwardToServer(@NonNull String serverName, @NonNull String subchannel, byte @NonNull [] payload) {
        requireAnyPlayer();
        var out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF(serverName);
        out.writeUTF(subchannel);
        out.writeShort(payload.length);
        out.write(payload);
        dispatch(out.toByteArray());
    }

    /**
     * Forwards to all servers including this one (the proxy relays back).
     *
     * @param subchannel the subchannel to forward on
     * @param payload    the raw payload bytes
     */
    public void forwardToAllIncludingSelf(@NonNull String subchannel, byte @NonNull [] payload) {
        requireAnyPlayer();
        var out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF("ONLINE");
        out.writeUTF(subchannel);
        out.writeShort(payload.length);
        out.write(payload);
        dispatch(out.toByteArray());
    }

    /**
     * Requests the player count of a server ("ALL" for global). The reply
     * arrives on the same subchannel — register a handler to receive it:
     * <pre>{@code
     * messenger.onMessage("PlayerCount", (p, data) -> {
     *     var in = ByteStreams.newDataInput(data);
     *     String server = in.readUTF();
     *     int count = in.readInt();
     * });
     * }</pre>
     *
     * @param serverName the server to query, or "ALL" for every backend
     */
    public void requestPlayerCount(@NonNull String serverName) {
        requireAnyPlayer();
        var out = ByteStreams.newDataOutput();
        out.writeUTF("PlayerCount");
        out.writeUTF(serverName);
        dispatch(out.toByteArray());
    }

    /**
     * Requests the real IP of a player behind the proxy. Reply arrives on
     * the "IP" subchannel.
     *
     * @param playerName the player whose IP is requested
     */
    public void requestRealIp(@NonNull String playerName) {
        requireAnyPlayer();
        var out = ByteStreams.newDataOutput();
        out.writeUTF("IP");
        out.writeUTF(playerName);
        dispatch(out.toByteArray());
    }

    // Plugin messages need at least one online player as the transport;
    // the proxy routes through them regardless of destination.
    private void requireAnyPlayer() {
        if (plugin.getServer().getOnlinePlayers().isEmpty()) {
            throw new IllegalStateException(
                "Proxy messages require at least one online player to act as the transport.");
        }
    }

    private void dispatch(byte[] message) {
        Player carrier = plugin.getServer().getOnlinePlayers().iterator().next();
        carrier.sendPluginMessage(plugin, CHANNEL, message);
    }

    /**
     * Unregisters channels and handlers. Call from onDisable.
     */
    public void shutdown() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin);
        handlers.clear();
    }
}
