package androidx.preference;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.IdentityHashMap;
import java.util.Map;

public final class PreferenceManager {
    private static final Map<Context, SharedPreferences> PREFERENCES = new IdentityHashMap<>();

    private PreferenceManager() {}

    public static synchronized SharedPreferences getDefaultSharedPreferences(Context context) {
        SharedPreferences preferences = PREFERENCES.get(context);
        if (preferences == null) {
            preferences = new SharedPreferences();
            PREFERENCES.put(context, preferences);
        }
        return preferences;
    }

    public static synchronized void clear(Context context) {
        PREFERENCES.remove(context);
    }
}
