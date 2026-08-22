package com.winlator.core;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;

public abstract class GeneralComponents {
    public enum Type { BOX64, TURNIP, DXVK, VKD3D, WINED3D, SOUNDFONT, ADRENOTOOLS_DRIVER }

    public static boolean isBuiltinComponent(Type type, String identifier) {
        if (identifier == null) return false;
        switch (type) {
            case BOX64: return "0.4.4".equals(identifier);
            case TURNIP: return "26.1.0".equals(identifier);
            case DXVK: return "1.10.3".equals(identifier) || "2.4.1".equals(identifier);
            case VKD3D: return "2.14.1".equals(identifier);
            case WINED3D: return "10.10".equals(identifier);
            default: return false;
        }
    }

    public static ArrayList<String> getInstalledComponentNames(Type type, Context context) {
        return new ArrayList<>(Arrays.asList());
    }
}
