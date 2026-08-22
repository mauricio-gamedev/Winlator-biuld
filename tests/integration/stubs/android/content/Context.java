package android.content;

import android.content.res.AssetManager;

public class Context {
    private final AssetManager assets = new AssetManager();

    public Context getApplicationContext() { return this; }
    public AssetManager getAssets() { return assets; }
}
