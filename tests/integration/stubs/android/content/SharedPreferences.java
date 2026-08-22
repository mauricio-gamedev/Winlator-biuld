package android.content;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
        private final Set<String> removals = new HashSet<>();

        public Editor putString(String key, String value) {
            pending.put(key, value);
            removals.remove(key);
            return this;
        }

        public Editor remove(String key) {
            pending.remove(key);
            removals.add(key);
            return this;
        }

        public void apply() {
            for (String key : removals) values.remove(key);
            values.putAll(pending);
        }
    }
}
