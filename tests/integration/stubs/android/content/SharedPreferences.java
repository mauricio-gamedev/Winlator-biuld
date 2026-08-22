package android.content;

import java.util.HashMap;
import java.util.Map;

public class SharedPreferences {
    private final Map<String, String> values = new HashMap<>();

    public String getString(String key, String defaultValue) {
        String value = values.get(key);
        return value != null ? value : defaultValue;
    }

    public Editor edit() {
        return new Editor();
    }

    public final class Editor {
        private final Map<String, String> pending = new HashMap<>();

        public Editor putString(String key, String value) {
            pending.put(key, value);
            return this;
        }

        public void apply() {
            values.putAll(pending);
        }
    }
}
