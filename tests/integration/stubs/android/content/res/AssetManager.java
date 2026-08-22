package android.content.res;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public class AssetManager {
    private final Set<String> assets = new HashSet<>();

    public void addAsset(String name) { assets.add(name); }
    public void removeAsset(String name) { assets.remove(name); }

    public InputStream open(String name) throws IOException {
        if (!assets.contains(name)) throw new IOException("missing asset: " + name);
        return new ByteArrayInputStream(new byte[0]);
    }
}
