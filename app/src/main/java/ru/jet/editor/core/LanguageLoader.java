package ru.jet.editor.core;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Teks antarmuka dibaca dari assets/language/&lt;kode&gt;.json (format: objek datar, kunci -> teks).
 *
 * Pemakaian:
 *   LanguageLoader.get("common.cancel")               -> "Cancel"
 *   LanguageLoader.get("project.deleted", name)       -> "Deleted {0}" dengan {0} diganti name
 *
 * Kunci yang tidak ada akan tampil apa adanya (nama kuncinya), supaya kekurangan terlihat.
 */
public final class LanguageLoader {
    private static final String TAG = "LanguageLoader";
    public static final String DEFAULT = "id";

    private static final Map<String, String> strings = new HashMap<>();
    private static String loaded = null;

    private LanguageLoader() { }

    /** Muat bahasa yang tersimpan di AppPrefs (atau default). Aman dipanggil berkali-kali; hanya membaca file sekali. */
    public static synchronized void init(Context c) {
        if (loaded != null) return;
        load(c.getApplicationContext(), AppPrefs.language(c));
    }

    /** Kode bahasa yang sedang dimuat. Dipakai layar untuk tahu kapan harus dibangun ulang. */
    public static synchronized String current() {
        return loaded == null ? DEFAULT : loaded;
    }

    /**
     * Daftar bahasa yang tersedia: setiap file di assets/language/*.json, diurutkan menurut nama aslinya.
     * Menambah bahasa cukup dengan menaruh file JSON baru di sana (dan nama aslinya di {@link #nativeName}).
     */
    public static List<String> available(Context c) {
        List<String> out = new ArrayList<>();
        try {
            for (String f : c.getAssets().list("language")) {
                if (f.endsWith(".json")) out.add(f.substring(0, f.length() - 5));
            }
        } catch (IOException e) {
            Log.e(TAG, "Daftar bahasa tidak bisa dibaca", e);
        }
        if (!out.contains(DEFAULT)) out.add(DEFAULT);
        Collections.sort(out, new Comparator<String>() {
            public int compare(String a, String b) {
                return nativeName(a).compareToIgnoreCase(nativeName(b));
            }
        });
        return out;
    }

    /** Nama bahasa dalam bahasanya sendiri, supaya mudah dikenali. Kode yang belum dikenal ditampilkan apa adanya. */
    public static String nativeName(String code) {
        switch (code) {
            case "en": return "English";
            case "id": return "Bahasa Indonesia";
            case "cn": return "中文";
            case "de": return "Deutsch";
            case "fr": return "Français";
            case "hi": return "हिन्दी";
            case "ja": return "日本語";
            case "pt": return "Português";
            case "ru": return "Русский";
            case "tr": return "Türkçe";
            default: return code;
        }
    }

    /** Ganti bahasa. Kode mengikuti nama file di assets/language, mis. "en" atau "id". */
    public static synchronized void load(Context c, String code) {
        strings.clear();
        String src = read(c, code);
        if (src == null && !DEFAULT.equals(code)) {
            code = DEFAULT;
            src = read(c, code);
        }
        // Bahasa dasar dibaca dulu, jadi kunci yang belum ada di bahasa terpilih tidak tampil mentah.
        if (!DEFAULT.equals(code)) {
            String base = read(c, DEFAULT);
            if (base != null) parse(base);
        }
        if (src != null) parse(src);
        loaded = code;
    }

    /** Teks untuk kunci. args menggantikan {0}, {1}, ... */
    public static String get(String key, Object... args) {
        String v = strings.get(key);
        if (v == null) {
            Log.w(TAG, "Kunci bahasa tidak ada: " + key);
            v = key;
        }
        for (int i = 0; i < args.length; i++) {
            v = v.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return v;
    }

    private static String read(Context c, String code) {
        try (InputStream in = c.getAssets().open("language/" + code + ".json")) {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (IOException e) {
            Log.e(TAG, "Bahasa '" + code + "' tidak bisa dibuka", e);
            return null;
        }
    }

    private static void parse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                strings.put(k, o.optString(k, k));
            }
        } catch (org.json.JSONException e) {
            Log.e(TAG, "JSON bahasa tidak valid", e);
        }
    }
}
