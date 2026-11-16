package ru.jet.editor.core;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Definisi bahasa, dibaca dari assets/syntax/languages/*.json.
 *
 * Format:
 *  {
 *    "name": "java",
 *    "extensions": ["java"],
 *    "filenames": [],                  // opsional: nama file persis, mis. "Makefile"
 *    "colonIndent": false,             // opsional: indent otomatis setelah ':' (python)
 *    "hashColorAlphaFirst": false,     // opsional: #RRGGBBAA untuk preview warna (css/html)
 *    "rules": [
 *      { "scope": "comment", "pattern": "//[^\\n]*" },
 *      { "scope": "keyword", "words": ["if", "else", "for"] }
 *    ]
 *  }
 * Urutan rules = prioritas (yang pertama menang). Pola regex tidak boleh memakai
 * grup capture biasa "( )"; gunakan "(?: )".
 * "scope" adalah nama token, dicocokkan ke bagian "tokens" pada tema.
 */
public final class Language {
    private static final String TAG = "Language";

    public final String name;
    public final List<String> extensions;
    public final List<String> fileNames;
    public final boolean colonIndent;
    public final boolean hashColorAlphaFirst;
    /** Scope tiap rule, indeks sama dengan grup regex (grup ke-i = scopes[i-1]). */
    public final String[] scopes;
    /** Null jika tidak ada rule atau regex gagal dikompilasi (highlight dimatikan). */
    public final Pattern pattern;

    private Language(String name, List<String> extensions, List<String> fileNames,
                     boolean colonIndent, boolean hashColorAlphaFirst,
                     String[] scopes, Pattern pattern) {
        this.name = name;
        this.extensions = extensions;
        this.fileNames = fileNames;
        this.colonIndent = colonIndent;
        this.hashColorAlphaFirst = hashColorAlphaFirst;
        this.scopes = scopes;
        this.pattern = pattern;
    }

    static Language parse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String name = o.optString("name", "?");
            JSONArray rules = o.optJSONArray("rules");

            List<String> scopeList = new ArrayList<>();
            StringBuilder re = new StringBuilder();
            if (rules != null) {
                for (int i = 0; i < rules.length(); i++) {
                    JSONObject r = rules.getJSONObject(i);
                    String regex = r.has("words")
                            ? wordsRegex(strings(r.getJSONArray("words")))
                            : r.getString("pattern");
                    if (re.length() > 0) re.append('|');
                    re.append('(').append(regex).append(')');
                    scopeList.add(r.getString("scope"));
                }
            }

            Pattern p = null;
            if (re.length() > 0) {
                try {
                    p = Pattern.compile(re.toString(), Pattern.MULTILINE);
                } catch (PatternSyntaxException e) {
                    Log.e(TAG, "Regex salah di bahasa '" + name + "'", e);
                }
            }

            return new Language(name,
                    strings(o.optJSONArray("extensions")),
                    strings(o.optJSONArray("filenames")),
                    o.optBoolean("colonIndent", false),
                    o.optBoolean("hashColorAlphaFirst", false),
                    scopeList.toArray(new String[0]),
                    p);
        } catch (JSONException e) {
            Log.e(TAG, "JSON bahasa tidak valid", e);
            return null;
        }
    }

    private static String wordsRegex(List<String> words) {
        StringBuilder sb = new StringBuilder("\\b(?:");
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append(Pattern.quote(words.get(i)));
        }
        return sb.append(")\\b").toString();
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, null);
            if (s != null) out.add(s);
        }
        return out;
    }
}
