package ru.jet.editor.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Membaca bagian yang dibutuhkan build dari app/build.gradle:
 * dependensi (implementation / api / compileOnly), minSdk, targetSdk, compileSdk,
 * applicationId, dan jar lokal di app/libs. Tidak menjalankan Gradle.
 * Murni Java, tanpa API Android.
 */
public final class GradleDeps {

    /** Satu koordinat Maven g:a:v. packaged=false untuk compileOnly. */
    public static final class Coord {
        public final String group;
        public final String artifact;
        public final String version;
        public final boolean packaged;

        public Coord(String group, String artifact, String version, boolean packaged) {
            this.group = group;
            this.artifact = artifact;
            this.version = version;
            this.packaged = packaged;
        }

        @Override public String toString() {
            return group + ":" + artifact + ":" + version;
        }
    }

    public static final class Spec {
        public final List<Coord> coords = new ArrayList<Coord>();
        public final List<File> localJars = new ArrayList<File>();
        public final List<String> warnings = new ArrayList<String>();
        public int minSdk = 21;
        public int compileSdk = 34;
        public int targetSdk = 34;
        public String applicationId;
    }

    /** implementation 'g:a:v', implementation("g:a:v"), api, compileOnly, runtimeOnly. */
    private static final Pattern DEP = Pattern.compile(
            "^\\s*(implementation|api|compileOnly|runtimeOnly)\\s*\\(?\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern APP_ID = Pattern.compile("applicationId\\s*=?\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern NAMESPACE = Pattern.compile("namespace\\s*=?\\s*['\"]([^'\"]+)['\"]");

    private GradleDeps() {}

    public static Spec parse(File projectDir) throws IOException {
        Spec spec = new Spec();
        File gradle = new File(projectDir, "app/build.gradle");
        String text = gradle.isFile()
                ? new String(Files.readAllBytes(gradle.toPath()), StandardCharsets.UTF_8)
                : "";

        spec.compileSdk = firstInt(text, "compileSdk(?:Version)?", spec.compileSdk);
        spec.minSdk = firstInt(text, "minSdk(?:Version)?", spec.minSdk);
        spec.targetSdk = firstInt(text, "targetSdk(?:Version)?", spec.targetSdk);

        Matcher m = APP_ID.matcher(text);
        if (m.find()) spec.applicationId = m.group(1);
        else {
            m = NAMESPACE.matcher(text);
            if (m.find()) spec.applicationId = m.group(1);
        }

        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.startsWith("//")) continue;
            Matcher d = DEP.matcher(line);
            if (!d.find()) continue;
            String conf = d.group(1);
            String coord = d.group(2);
            boolean packaged = !"compileOnly".equals(conf);
            if (coord.endsWith("@aar")) coord = coord.substring(0, coord.length() - 4);
            String[] p = coord.split(":");
            if (p.length < 3 || p[0].isEmpty() || p[1].isEmpty() || p[2].isEmpty()) {
                spec.warnings.add("Lewati dependensi tanpa versi: " + coord);
                continue;
            }
            spec.coords.add(new Coord(p[0], p[1], p[2], packaged));
        }

        // Baris lain seperti project(':x') dan libs.x (version catalog) tidak didukung; beri peringatan.
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.matches("^(implementation|api|compileOnly)\\s*\\(?\\s*(project\\(|libs\\.|platform\\().*")) {
                spec.warnings.add("Tidak didukung, dilewati: " + line);
            }
        }

        File libs = new File(projectDir, "app/libs");
        File[] jars = libs.listFiles();
        if (jars != null) {
            for (File f : jars) {
                if (f.isFile() && f.getName().endsWith(".jar") && f.length() > 0) spec.localJars.add(f);
            }
        }
        return spec;
    }

    private static int firstInt(String text, String keyRegex, int def) {
        Matcher m = Pattern.compile(keyRegex + "\\s*=?\\s*(\\d+)").matcher(text);
        return m.find() ? Integer.parseInt(m.group(m.groupCount())) : def;
    }
}
