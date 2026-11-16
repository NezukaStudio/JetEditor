package ru.jet.editor.core;

import android.content.Context;
import android.content.SharedPreferences;

/** Preferensi tampilan aplikasi (SharedPreferences "jet_prefs"). Semua pembacaan aman dipakai dari mana saja. */
public final class AppPrefs {
    private static final String FILE = "jet_prefs";
    private static final String K_DARK = "dark";
    private static final String K_DYNAMIC = "dynamic_color";
    private static final String K_ACCENT = "app_color";
    private static final String K_LANG = "language";
    private static final String K_SHOW_BUILD = "show_build_setting";
    private static final String K_BACKUP_FORMAT = "backup_name_format";
    private static final String K_AUTO_OPEN = "auto_open_installed";
    private static final String K_BUILD_DEBUG = "build_debug";
    private static final String K_DEXER = "build_dexer";
    private static final String K_JAVA = "build_java";
    private static final String K_EDITOR_TABS = "editor_tabs";
    private static final String K_XML_PREVIEW = "xml_preview_button";

    /** Pola nama backup bawaan: sama dengan nama backup sebelum fitur ini ada. */
    public static final String DEFAULT_BACKUP_FORMAT = "$projectName_$time(yyyyMMdd_HHmmss)";

    private AppPrefs() { }

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Tema gelap (default). false = terang. */
    public static boolean isDark(Context c) { return prefs(c).getBoolean(K_DARK, true); }

    public static void setDark(Context c, boolean on) { prefs(c).edit().putBoolean(K_DARK, on).apply(); }

    /** Warna dari wallpaper (Material You, Android 12+). Default aktif. */
    public static boolean isDynamic(Context c) { return prefs(c).getBoolean(K_DYNAMIC, true); }

    /** Menyalakan warna dinamis menghapus warna aksen pilihan sendiri, supaya kembali ke wallpaper. */
    public static void setDynamic(Context c, boolean on) {
        android.content.SharedPreferences.Editor e = prefs(c).edit().putBoolean(K_DYNAMIC, on);
        if (on) e.remove(K_ACCENT);
        e.apply();
    }

    /** Warna aksen pilihan sendiri (ARGB). 0 = belum dipilih. */
    public static int appColor(Context c) { return prefs(c).getInt(K_ACCENT, 0); }

    /** Memilih warna sendiri otomatis mematikan warna dinamis, supaya pilihan ini yang berlaku. */
    public static void setAppColor(Context c, int argb) {
        prefs(c).edit().putInt(K_ACCENT, argb).putBoolean(K_DYNAMIC, false).apply();
    }

    /** Kode bahasa yang dipilih (nama file di assets/language tanpa .json). Default: {@link LanguageLoader#DEFAULT}. */
    public static String language(Context c) { return prefs(c).getString(K_LANG, LanguageLoader.DEFAULT); }

    public static void setLanguage(Context c, String code) { prefs(c).edit().putString(K_LANG, code).apply(); }

    /** Menu "Setelan build" di popup proyek. Default: sembunyi. */
    public static boolean showBuildSetting(Context c) { return prefs(c).getBoolean(K_SHOW_BUILD, false); }

    public static void setShowBuildSetting(Context c, boolean on) { prefs(c).edit().putBoolean(K_SHOW_BUILD, on).apply(); }

    /** Pola nama file backup (variabel: lihat {@link BackupName}). */
    public static String backupNameFormat(Context c) { return prefs(c).getString(K_BACKUP_FORMAT, DEFAULT_BACKUP_FORMAT); }

    public static void setBackupNameFormat(Context c, String format) { prefs(c).edit().putString(K_BACKUP_FORMAT, format).apply(); }

    /** Buka aplikasi otomatis setelah build berhasil dipasang. Default: mati. */
    public static boolean autoOpenInstalled(Context c) { return prefs(c).getBoolean(K_AUTO_OPEN, false); }

    public static void setAutoOpenInstalled(Context c, boolean on) { prefs(c).edit().putBoolean(K_AUTO_OPEN, on).apply(); }

    /** true = build debug memakai debug.jks bawaan. false = tiap Run user memilih keystore sendiri. */
    public static boolean buildDebug(Context c) { return prefs(c).getBoolean(K_BUILD_DEBUG, true); }

    public static void setBuildDebug(Context c, boolean on) { prefs(c).edit().putBoolean(K_BUILD_DEBUG, on).apply(); }

    /** "d8" atau "dx". Dx belum tersedia di JetEditor: pilihan ini mencatat peringatan dan tetap memakai D8. */
    public static String dexer(Context c) { return prefs(c).getString(K_DEXER, "d8"); }

    public static void setDexer(Context c, String dexer) { prefs(c).edit().putString(K_DEXER, dexer).apply(); }

    /** Level Java untuk ECJ: "1.7", "1.8", "11", "15", atau "16". */
    public static String javaLevel(Context c) { return prefs(c).getString(K_JAVA, "1.8"); }

    public static void setJavaLevel(Context c, String level) { prefs(c).edit().putString(K_JAVA, level).apply(); }

    /** Tab file terbuka di atas editor, seperti Android Studio. Default: mati. */
    public static boolean editorTabs(Context c) { return prefs(c).getBoolean(K_EDITOR_TABS, false); }

    public static void setEditorTabs(Context c, boolean on) { prefs(c).edit().putBoolean(K_EDITOR_TABS, on).apply(); }

    /** Ikon preview XML di samping tombol Run (masih dummy). Default: mati. */
    public static boolean xmlPreview(Context c) { return prefs(c).getBoolean(K_XML_PREVIEW, false); }

    public static void setXmlPreview(Context c, boolean on) { prefs(c).edit().putBoolean(K_XML_PREVIEW, on).apply(); }

    private static final String K_PREVIEW_SIZE = "preview_size_index";

    /** Indeks ukuran layar preview (lihat PreviewSurface.SIZE_W). Default: 1 = Standar. */
    public static int previewSize(Context c) { return prefs(c).getInt(K_PREVIEW_SIZE, 1); }

    public static void setPreviewSize(Context c, int index) { prefs(c).edit().putInt(K_PREVIEW_SIZE, index).apply(); }
}
