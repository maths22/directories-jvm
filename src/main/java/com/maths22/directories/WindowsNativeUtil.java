package com.maths22.directories;

import java.util.UUID;

public class WindowsNativeUtil {
    private WindowsNativeUtil() {
        throw new Error();
    }

    static {
        System.load("C:\\Users\\burro\\github\\directories-jvm\\build\\native\\Debug\\directories.dll");
    }

    static native String getWinDir(UUID uuid);
}
