package ru.jet.editor.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Pretty print sederhana untuk editor. Tanpa dependensi Android supaya bisa diuji di JVM biasa.
 *
 * - JSON  : urutan key dipertahankan (org.json tidak bisa, karena pakai HashMap).
 * - XML   : satu tag per baris, teks pendek tetap sebaris dengan tag pembukanya.
 * - C-like: Java, Kotlin, Gradle, JS, TS, CSS, C/C++ — indent berdasarkan kurung { ( [ dengan
 *           string, char literal, dan komentar diabaikan.
 */
public final class PrettyPrinter {

    private static final String IND = "    ";

    private PrettyPrinter() {}

    /** @throws IllegalArgumentException kalau ekstensi belum didukung. */
    public static String format(String fileName, String src) {
        String ext = ext(fileName);
        if (ext.equals("json")) return json(src);
        if (ext.equals("xml") || ext.equals("html") || ext.equals("htm") || ext.equals("svg")) return xml(src);
        if (ext.equals("java") || ext.equals("kt") || ext.equals("kts") || ext.equals("gradle")
                || ext.equals("js") || ext.equals("ts") || ext.equals("css")
                || ext.equals("c") || ext.equals("h") || ext.equals("cpp")) return braces(src);
        throw new IllegalArgumentException("Pretty print belum mendukung ." + ext);
    }


    static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    // =====================================================================
    // JSON
    // =====================================================================

    static String json(String s) {
        StringBuilder out = new StringBuilder(s.length() + 64);
        int indent = 0;
        boolean inStr = false;
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (inStr) {
                out.append(c);
                if (c == '\\' && i + 1 < n) {
                    out.append(s.charAt(++i));
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            switch (c) {
                case '"':
                    inStr = true;
                    out.append(c);
                    break;
                case '{':
                case '[': {
                    out.append(c);
                    int next = nextNonSpace(s, i + 1);
                    char close = c == '{' ? '}' : ']';
                    if (next < n && s.charAt(next) == close) {
                        out.append(close);      // {} atau [] kosong tetap sebaris
                        i = next;
                    } else {
                        indent++;
                        newline(out, indent);
                    }
                    break;
                }
                case '}':
                case ']':
                    indent = Math.max(0, indent - 1);
                    newline(out, indent);
                    out.append(c);
                    break;
                case ',':
                    out.append(c);
                    newline(out, indent);
                    break;
                case ':':
                    out.append(": ");
                    break;
                case ' ':
                case '\t':
                case '\r':
                case '\n':
                    break;               // spasi di luar string dibuang
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    private static void newline(StringBuilder out, int indent) {
        out.append('\n');
        for (int i = 0; i < indent; i++) out.append(IND);
    }

    private static int nextNonSpace(String s, int from) {
        int i = from;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    // =====================================================================
    // XML
    // =====================================================================

    private static final int T_TEXT = 0, T_OPEN = 1, T_CLOSE = 2, T_SELF = 3, T_RAW = 4;

    static String xml(String s) {
        List<String> toks = new ArrayList<>();
        List<Integer> kinds = new ArrayList<>();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '<') {
                int end;
                if (s.startsWith("<!--", i)) {
                    end = s.indexOf("-->", i);
                    end = end < 0 ? n : end + 3;
                    add(toks, kinds, s.substring(i, end), T_RAW);
                } else if (s.startsWith("<![CDATA[", i)) {
                    end = s.indexOf("]]>", i);
                    end = end < 0 ? n : end + 3;
                    add(toks, kinds, s.substring(i, end), T_RAW);
                } else {
                    // cari '>' yang di luar tanda kutip atribut
                    end = n;
                    char q = 0;
                    for (int j = i + 1; j < n; j++) {
                        char d = s.charAt(j);
                        if (q != 0) {
                            if (d == q) q = 0;
                        } else if (d == '"' || d == '\'') {
                            q = d;
                        } else if (d == '>') {
                            end = j + 1;
                            break;
                        }
                    }
                    String tag = collapse(s.substring(i, end));
                    int kind;
                    if (tag.startsWith("</")) kind = T_CLOSE;
                    else if (tag.startsWith("<?") || tag.startsWith("<!")) kind = T_RAW;
                    else if (tag.endsWith("/>")) kind = T_SELF;
                    else kind = T_OPEN;
                    add(toks, kinds, tag, kind);
                }
                i = end;
            } else {
                int end = s.indexOf('<', i);
                if (end < 0) end = n;
                add(toks, kinds, s.substring(i, end), T_TEXT);
                i = end;
            }
        }

        StringBuilder out = new StringBuilder(s.length() + 64);
        int indent = 0;
        boolean first = true;
        for (int k = 0; k < toks.size(); k++) {
            int kind = kinds.get(k);
            String t = toks.get(k);
            if (kind == T_TEXT) continue;   // teks di antara tag ditangani di bawah

            if (kind == T_OPEN && k + 2 < toks.size()
                    && kinds.get(k + 1) == T_TEXT && kinds.get(k + 2) == T_CLOSE
                    && !toks.get(k + 1).trim().isEmpty()) {
                line(out, first, indent, t + toks.get(k + 1).trim() + toks.get(k + 2));
                first = false;
                k += 2;
                continue;
            }
            if (kind == T_OPEN && k + 1 < toks.size() && kinds.get(k + 1) == T_CLOSE) {
                line(out, first, indent, t + toks.get(k + 1));
                first = false;
                k += 1;
                continue;
            }
            if (kind == T_CLOSE) {
                indent = Math.max(0, indent - 1);
            }
            line(out, first, indent, t);
            first = false;
            if (kind == T_OPEN) indent++;
        }
        // teks yang berdiri sendiri (jarang di XML Android) tetap dipertahankan
        return out.length() == 0 ? "" : out.append('\n').toString();
    }

    private static void add(List<String> toks, List<Integer> kinds, String t, int kind) {
        if (kind == T_TEXT && t.trim().isEmpty()) return;
        toks.add(kind == T_TEXT ? t.trim() : t);
        kinds.add(kind);
    }

    private static void line(StringBuilder out, boolean first, int indent, String text) {
        if (!first) out.append('\n');
        for (int i = 0; i < indent; i++) out.append(IND);
        out.append(text);
    }

    /** Spasi/baris baru di dalam satu tag dirapikan jadi satu spasi (nilai atribut tetap). */
    private static String collapse(String tag) {
        return tag.replaceAll("\\s*\\r?\\n\\s*", " ").replaceAll("[ \\t]{2,}", " ");
    }

    // =====================================================================
    // C-like (kurung kurawal)
    // =====================================================================

    static String braces(String src) {
        boolean endsWithNl = src.endsWith("\n");
        String[] lines = src.split("\r?\n", -1);
        StringBuilder out = new StringBuilder(src.length() + 64);
        int depth = 0;
        boolean inBlock = false;       // di dalam komentar /* ... */ dari baris sebelumnya
        boolean firstOut = true;
        boolean pendingBlank = false;   // baris kosong dipadatkan jadi maksimal satu

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                pendingBlank = !firstOut;
                continue;
            }

            int level = depth;
            boolean startsInBlock = inBlock;
            if (!startsInBlock && (line.charAt(0) == '}' || line.charAt(0) == ')' || line.charAt(0) == ']')) {
                level = Math.max(0, depth - 1);
            }

            // hitung perubahan kedalaman dan status komentar/string untuk baris ini
            int delta = 0;
            boolean inStr = false;
            char quote = 0;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (inBlock) {
                    if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                        inBlock = false;
                        i++;
                    }
                    continue;
                }
                if (inStr) {
                    if (c == '\\') i++;
                    else if (c == quote) inStr = false;
                    continue;
                }
                if (c == '/' && i + 1 < line.length()) {
                    char d = line.charAt(i + 1);
                    if (d == '/') break;                 // sisa baris komentar
                    if (d == '*') {
                        inBlock = true;
                        i++;
                        continue;
                    }
                }
                if (c == '"' || c == '\'') {
                    inStr = true;
                    quote = c;
                    continue;
                }
                if (c == '{' || c == '(' || c == '[') delta++;
                else if (c == '}' || c == ')' || c == ']') delta--;
            }
            // string yang tidak ditutup di akhir baris (mis. text block) tidak ikut ke baris berikutnya
            if (inStr) inStr = false;

            if (!firstOut) {
                out.append('\n');
                if (pendingBlank) out.append('\n');
            }
            pendingBlank = false;
            firstOut = false;
            for (int k = 0; k < level; k++) out.append(IND);
            if (startsInBlock && line.charAt(0) == '*') out.append(' ');
            out.append(line);

            depth = Math.max(0, depth + delta);
        }
        if (endsWithNl) out.append('\n');
        return out.toString();
    }
}
