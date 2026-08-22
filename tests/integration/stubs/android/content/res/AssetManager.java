package android.content.res;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class AssetManager {
    private final Map<String, byte[]> assets = new HashMap<>();

    public void addAsset(String name) { assets.put(name, new byte[]{1}); }
    public void addAsset(String name, byte[] data) { assets.put(name, data == null ? new byte[0] : data.clone()); }
    public void removeAsset(String name) { assets.remove(name); }

    public InputStream open(String name) throws IOException {
        byte[] data = assets.get(name);
        if (data == null) throw new IOException("missing asset: " + name);
        return new ByteArrayInputStream(data);
    }
}
