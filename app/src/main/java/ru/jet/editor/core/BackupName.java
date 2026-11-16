package ru.jet.editor.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Menyusun nama file backup dari pola yang diatur user.
 *
 * Variabel:
 *   $projectName  nama folder proyek
 *   $versionCode  versionCode dari app/build.gradle
 *   $versionName  versionName dari app/build.gradle
 *   $pkgName      package aplikasi
 *   $timeInMs     waktu backup dalam milidetik
 *   $time(pola)   waktu dengan pola SimpleDateFormat, mis. $time(yyyy-MM-dd'T'HHmmss)
 */
public final class BackupName {

    private static final Pattern TIME = Pattern.compile("\\$time\\(([^)]*)\\)");
    private static final Pattern UNSAFE = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private BackupName() { }

    /** Nama file lengkap (berakhiran .zip) untuk satu backup. */
    public static String fileName(String format, String projectName, String versionName,
                                  String versionCode, String pkgName, long nowMs) {
        String f = render(format, projectName, versionName, versionCode, pkgName, nowMs);
        f = UNSAFE.matcher(f).replaceAll("_").trim();
        if (f.isEmpty()) f = projectName;
        return f.toLowerCase(Locale.ROOT).endsWith(".zip") ? f : f + ".zip";
    }

    /** Mengganti semua variabel dalam pola. Hasilnya belum dibersihkan dari karakter terlarang. */
    public static String render(String format, String projectName, String versionName,
                                String versionCode, String pkgName, long nowMs) {
        String out = format == null ? "" : format;

        Matcher m = TIME.matcher(out);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String pattern = m.group(1);
            String text;
            try {
                text = new SimpleDateFormat(pattern, Locale.US).format(new Date(nowMs));
            } catch (IllegalArgumentException e) {
                text = pattern;     // pola tidak valid: tampilkan apa adanya
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(text));
        }
        m.appendTail(sb);
        out = sb.toString();

        out = out.replace("$projectName", nz(projectName))
                .replace("$versionCode", nz(versionCode))
                .replace("$versionName", nz(versionName))
                .replace("$pkgName", nz(pkgName))
                .replace("$timeInMs", String.valueOf(nowMs));
        return out;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
