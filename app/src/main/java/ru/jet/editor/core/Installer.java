package ru.jet.editor.core;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.io.IOException;

/**
 * Pasang APK lewat ACTION_VIEW + ContentProvider (InstallProvider), seperti dem.zip.
 * Semua dialog konfirmasi dan hasil ditampilkan oleh installer bawaan perangkat; JetEditor
 * tidak menampilkan dialog sendiri.
 * Izin: REQUEST_INSTALL_PACKAGES di manifest, plus "Pasang aplikasi tidak dikenal" untuk JetEditor (Android 8+).
 */
public final class Installer {

    private static final String PREFS = "install_state";
    private static final String K_PKG = "pkg";
    private static final String K_AT = "at";
    /** Buka otomatis hanya jika JetEditor kembali tampil dalam rentang ini sejak installer dibuka. */
    private static final long AUTO_OPEN_WINDOW_MS = 10L * 60L * 1000L;

    private Installer() {}

    /** True jika JetEditor boleh memasang aplikasi. Android di bawah 8 selalu boleh (lewat izin manifest). */
    public static boolean canInstall(Context c) {
        return Build.VERSION.SDK_INT < 26 || c.getPackageManager().canRequestPackageInstalls();
    }

    /** Buka layar sistem supaya user mengizinkan JetEditor memasang aplikasi. */
    public static void openInstallSettings(Context c) {
        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + c.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    /** Nama package yang ada di dalam APK, atau null bila APK tidak bisa dibaca. */
    public static String packageOf(Context c, File apk) {
        android.content.pm.PackageInfo pi =
                c.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        return pi == null ? null : pi.packageName;
    }

    /** content://ru.jet.editor.apkprovider/apk/{path APK}, path dipecah per segmen. */
    static Uri uriOf(File apk) {
        Uri.Builder b = new Uri.Builder()
                .scheme("content")
                .authority(InstallProvider.AUTHORITY)
                .appendPath(InstallProvider.SEGMENT);
        for (String seg : apk.getAbsolutePath().split("/")) {
            if (!seg.isEmpty()) b.appendPath(seg);
        }
        return b.build();
    }

    /** Buka installer bawaan untuk APK. Panggil dari thread latar belakang atau UI thread. */
    public static void install(Context c, File apk) throws IOException {
        if (!apk.isFile()) throw new IOException("APK tidak ditemukan: " + apk.getAbsolutePath());
        remember(c, packageOf(c, apk));
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uriOf(apk), "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void remember(Context c, String pkg) {
        prefs(c).edit().putString(K_PKG, pkg).putLong(K_AT, System.currentTimeMillis()).apply();
    }

    /**
     * Dipanggil saat JetEditor tampil lagi. Installer tidak mengirim hasil ke aplikasi, jadi
     * "buka otomatis" dijalankan di sini: bila pengaturan aktif, package yang baru dipasang
     * (dan JetEditor kembali tampil dalam rentang waktu wajar) langsung dibuka.
     */
    public static void onResumed(Context c) {
        SharedPreferences sp = prefs(c);
        String pkg = sp.getString(K_PKG, null);
        if (pkg == null) return;
        long at = sp.getLong(K_AT, 0L);
        sp.edit().remove(K_PKG).remove(K_AT).apply();
        if (!AppPrefs.autoOpenInstalled(c)) return;
        if (System.currentTimeMillis() - at > AUTO_OPEN_WINDOW_MS) return;
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) return;   // belum terpasang (dibatalkan user)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(launch);
    }
}
