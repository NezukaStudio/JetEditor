package ru.jet.editor.core;

import android.os.Environment;

import java.io.File;

/** Lokasi tool dan hasil build: /sdcard/.JetEdit/build-tools dan /sdcard/.JetEdit/build. */
public final class ToolPaths {

    private ToolPaths() {}

    public static File root() {
        return new File(Environment.getExternalStorageDirectory(), ".JetEdit/build-tools");
    }

    /** folder: "sdk", "aapt", atau "gradle". */
    public static File dir(String folder) {
        return new File(root(), folder);
    }

    /** Hasil build: /sdcard/.JetEdit/build/{appName}/app.apk */
    public static File apkOutput(String appName) {
        return apkOutput(appName, "app.apk");
    }

    /** Hasil build dengan nama file tertentu (mis. app-release.apk). */
    public static File apkOutput(String appName, String fileName) {
        return new File(Environment.getExternalStorageDirectory(),
                ".JetEdit/build/" + appName + "/" + fileName);
    }

    /**
     * r8.jar yang dipakai: jvm_r8.jar dulu; kalau tidak ada, dex_r8.jar; dan sebaliknya.
     * Null kalau keduanya tidak ada.
     */
    public static File r8Jar() {
        File jvm = new File(root(), "jvm_r8.jar");
        if (jvm.isFile()) return jvm;
        File dex = new File(root(), "dex_r8.jar");
        if (dex.isFile()) return dex;
        return null;
    }

    /** Folder keystore buatan Keystore creator: /sdcard/.JetEdit/keys/{alias}/keystore.jks */
    public static File keysRoot() {
        return new File(Environment.getExternalStorageDirectory(), ".JetEdit/keys");
    }
}
