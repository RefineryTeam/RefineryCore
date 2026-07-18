package xyz.refineryteam.refinerycore.api.version;

/**
 * Base class for {@link ServerImplementation}s. Handles version string/ordinal storage so
 * concrete implementations only need to implement {@link #supports(Feature)}.
 */
public abstract class AbstractServerImplementation implements ServerImplementation {

    private final String minecraftVersion;
    private final int versionOrdinal;

    protected AbstractServerImplementation(String minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
        this.versionOrdinal = ServerImplementations.encodeVersion(minecraftVersion);
    }

    @Override
    public final String getMinecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public final int getVersionOrdinal() {
        return versionOrdinal;
    }
}
