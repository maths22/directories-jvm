package com.maths22.directories;

import java.util.UUID;

public class WindowsNativeUtil {
    private WindowsNativeUtil() {
        throw new Error();
    }

    static boolean isSupported() {
        return false;
    }

    static String getWinDir(UUID uuid) {
        throw new UnsupportedOperationException("Native operations not supported before jdk17");
    }
}
