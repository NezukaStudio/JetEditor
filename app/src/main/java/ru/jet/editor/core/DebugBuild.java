package ru.jet.editor.core;

import android.content.Context;

import dalvik.system.DexClassLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import ru.jet.editor.FileUtil;

/**
 * Build debug dari dalam aplikasi, tanpa shell.
 *   1. cek tool
 *   2. aapt2 compile + link   -> base.apk (manifest, resources.arsc, res/*)
 *   3. ECJ                    -> .class
 *   4. D8 (jvm_r8.jar / dex_r8.jar) -> classes.dex
 *   5. gabung: base + dex + assets -> unsigned.apk (struktur sama dengan proyek)
 *   6. cek struktur APK terhadap proyek
 *   7. sign v2 (apksig)       -> /sdcard/.JetEdit/build/{nama}/app.apk
 *   8. verifikasi signature dengan ApkVerifier
 *
 * Panggil dari thread latar belakang. Setiap tahap dilaporkan lewat {@link Listener}.
 */
public final class DebugBuild {

    private static final String KS_PASS = "android";
    private static final String KS_ALIAS = "debug";

    // Kunci teks tahap (dipakai sebagai label di bottom sheet lewat LanguageLoader). Urutannya sama dengan STEPS.
    public static final String STEP_CHECK = "step.check";
    public static final String STEP_DEPS = "step.deps";
    public static final String STEP_AAPT_COMPILE = "step.aapt_compile";
    public static final String STEP_AAPT_LINK = "step.aapt_link";
    public static final String STEP_JAVA = "step.java";
    public static final String STEP_DEX = "step.dex";
    public static final String STEP_PACK = "step.pack";
    public static final String STEP_STRUCT = "step.struct";
    public static final String STEP_SIGN = "step.sign";
    public static final String STEP_VERIFY = "step.verify";
    public static final String[] STEPS = {
            STEP_CHECK, STEP_DEPS, STEP_AAPT_COMPILE, STEP_AAPT_LINK, STEP_JAVA, STEP_DEX,
            STEP_PACK, STEP_STRUCT, STEP_SIGN, STEP_VERIFY
    };

    public static final String RUN = "run";
    public static final String OK = "ok";
    public static final String FAIL = "fail";

    /** Entri yang wajib ada di APK agar bisa di-install. */
    private static final String[] REQUIRED = {"AndroidManifest.xml", "resources.arsc", "classes.dex"};

    public interface Listener {
        void onStep(String step, String state, String detail);
    }

    /**
     * Pembatalan build. Dicek di sela tahap, dan proses aapt2 yang sedang berjalan dihentikan.
     * Satu token dipakai untuk satu build.
     */
    public static final class Cancel {
        private volatile boolean on;
        private final List<Process> running = new ArrayList<Process>();

        public void cancel() {
            synchronized (running) {
                on = true;
                for (Process p : running) p.destroy();
            }
        }

        public boolean isCancelled() { return on; }

        void track(Process p) {
            synchronized (running) {
                running.add(p);
                if (on) p.destroy();
            }
        }

        void untrack(Process p) {
            synchronized (running) { running.remove(p); }
        }
    }

    /** Dilempar saat pengguna membatalkan build. Ditampilkan sebagai "dibatalkan", bukan sebagai error. */
    public static final class Cancelled extends IllegalStateException {
        public Cancelled() { super(LanguageLoader.get("build.cancelled")); }
    }

    /** Token build yang sedang berjalan di thread ini. */
    private static final ThreadLocal<Cancel> ACTIVE = new ThreadLocal<Cancel>();

    private static void checkCancel() {
        Cancel c = ACTIVE.get();
        if (c != null && c.isCancelled()) throw new Cancelled();
    }

    /** Sumber bytes keystore (file lokal atau Uri hasil pilihan user). */
    public interface KeySource {
        InputStream open() throws IOException;
    }

    /** Cara menandatangani APK: keystore, alias, password, dan nama file hasil. */
    public static final class Signing {
        final KeySource keys;
        final String alias;
        final char[] storePass;
        final char[] keyPass;
        final String apkName;
        final boolean usesDebugKey;

        public Signing(KeySource keys, String alias, char[] storePass, char[] keyPass,
                       String apkName, boolean usesDebugKey) {
            this.keys = keys;
            this.alias = alias;
            this.storePass = storePass;
            this.keyPass = keyPass == null ? storePass : keyPass;
            this.apkName = apkName;
            this.usesDebugKey = usesDebugKey;
        }

        /** debug.jks bawaan tool (password android, alias debug) -> app.apk. */
        public static Signing debug() {
            return new Signing(new KeySource() {
                public InputStream open() throws IOException {
                    return new FileInputStream(keystoreFile());
                }
            }, KS_ALIAS, KS_PASS.toCharArray(), KS_PASS.toCharArray(), "app.apk", true);
        }
    }

    private static final Listener NO_LISTENER = new Listener() {
        public void onStep(String step, String state, String detail) {}
    };

    private DebugBuild() {}

    /** Versi lama tanpa listener, dipertahankan agar pemanggil lama tetap jalan. */
    public static File buildApk(Context ctx, File projectDir, StringBuilder log) throws Exception {
        return buildApk(ctx, projectDir, log, null);
    }

    /** Jalankan seluruh pipeline dan kembalikan file APK yang sudah ditandatangani dan diverifikasi. */
    public static File buildApk(Context ctx, File projectDir, StringBuilder log, Listener ls) throws Exception {
        return buildApk(ctx, projectDir, log, ls, Signing.debug());
    }

    /** Pipeline lengkap dengan penandatanganan yang dipilih (debug atau release). */
    public static File buildApk(Context ctx, File projectDir, StringBuilder log, Listener ls, Signing sign) throws Exception {
        return buildApk(ctx, projectDir, log, ls, sign, null);
    }

    /** Sama seperti di atas, dengan token pembatalan. cancel boleh null. */
    public static File buildApk(Context ctx, File projectDir, StringBuilder log, Listener ls, Signing sign,
                                Cancel cancel) throws Exception {
        ACTIVE.set(cancel);
        try {
            return buildApkInternal(ctx, projectDir, log, ls, sign);
        } finally {
            ACTIVE.remove();
        }
    }

    private static File buildApkInternal(Context ctx, File projectDir, StringBuilder log, Listener ls, Signing sign) throws Exception {
        if (ls == null) ls = NO_LISTENER;
        String current = STEP_CHECK;
        try {
            checkCancel();
            ls.onStep(current, RUN, "");
            List<String> missing = missingTools(projectDir, sign.usesDebugKey);
            if (!missing.isEmpty()) throw new IllegalStateException(LanguageLoader.get("tools.missing", missing));
            ls.onStep(current, OK, "");

            String appName = projectDir.getName();
            String api = compileSdkOf(projectDir);
            File androidJar = new File(ToolPaths.dir("sdk"), "android-" + api + ".jar");

            File work = new File(ctx.getCacheDir(), "build/" + appName);
            deleteRecursive(work);
            File gen = new File(work, "gen");
            File classes = new File(work, "classes");
            File dexDir = new File(work, "dex");
            gen.mkdirs();
            classes.mkdirs();
            dexDir.mkdirs();

            File manifest = new File(projectDir, "app/src/main/AndroidManifest.xml");
            File res = new File(projectDir, "app/src/main/res");
            File assets = new File(projectDir, "app/src/main/assets");
            if (!res.isDirectory()) throw new IllegalStateException("Folder res tidak ada: " + res);

            // 2. Dependensi: unduh (Google Maven, Maven Central), bongkar AAR, gabung manifest.
            current = STEP_DEPS;
            checkCancel();
            ls.onStep(current, RUN, "");
            GradleDeps.Spec spec = GradleDeps.parse(projectDir);
            for (String w : spec.warnings) log.append("PERINGATAN: ").append(w).append('\n');
            List<MavenResolver.Lib> libs = resolveDeps(spec, log);
            String appId = spec.applicationId != null ? spec.applicationId : MavenResolver.readPackage(manifest);
            File merged = new File(work, "AndroidManifest.xml");
            List<File> libManifests = new ArrayList<File>();
            for (MavenResolver.Lib l : libs) {
                if (l.manifest != null && l.manifest.isFile()) libManifests.add(l.manifest);
            }
            ManifestMerger.merge(manifest, libManifests, appId, merged);
            log.append("dependensi: ").append(libs.size()).append(" library, ")
                    .append(spec.localJars.size()).append(" jar lokal\n");
            ls.onStep(current, OK, libs.size() + " library, " + spec.localJars.size() + " jar lokal");

            // 3. aapt2 compile: res proyek dan res tiap library. Hasilnya disimpan di cache bersama
            // (kunci = isi folder res + versi aapt2), jadi library yang sama tidak dikompilasi ulang
            // saat membangun proyek lain. Kalau semua sudah ada di cache, tahap ini langsung centang.
            current = STEP_AAPT_COMPILE;
            checkCancel();
            File aapt = prepareAapt(ctx);
            String[] a = aaptLauncher(ctx, aapt, log);
            File cacheDir = aaptCacheDir(ctx);
            List<File> resDirs = new ArrayList<File>();
            resDirs.add(res);
            for (MavenResolver.Lib l : libs) {
                if (l.res != null) resDirs.add(l.res);
            }
            List<String> linkInputs = new ArrayList<String>();
            List<File[]> pending = new ArrayList<File[]>();   // [folder res, zip tujuan]
            for (File dir : resDirs) {
                checkCancel();
                File zip = new File(cacheDir, resKey(dir, aapt) + ".zip");
                if (!zip.isFile()) pending.add(new File[]{dir, zip});
                linkInputs.add(zip.getAbsolutePath());
            }
            if (pending.isEmpty()) {
                ls.onStep(current, OK, LanguageLoader.get("build.from_cache"));
            } else {
                ls.onStep(current, RUN, "");
                for (File[] job : pending) {
                    checkCancel();
                    // tulis ke berkas sementara dulu, supaya build yang dibatalkan tidak meninggalkan cache rusak
                    File tmp = new File(job[1].getPath() + ".tmp");
                    tmp.delete();
                    run(log, work, a, "compile", "--dir", job[0].getAbsolutePath(), "-o", tmp.getAbsolutePath());
                    if (!tmp.renameTo(job[1])) throw new IOException("Gagal menyimpan cache aapt2: " + job[1]);
                }
                ls.onStep(current, OK, "");
            }

            // 4. aapt2 link: manifest gabungan, R untuk tiap library, assets library.
            current = STEP_AAPT_LINK;
            checkCancel();
            ls.onStep(current, RUN, "");
            File base = new File(work, "base.apk");
            List<String> link = new ArrayList<String>(Arrays.asList(
                    "link",
                    "-I", androidJar.getAbsolutePath(),
                    "--manifest", merged.getAbsolutePath(),
                    "--java", gen.getAbsolutePath(),
                    "--min-sdk-version", String.valueOf(spec.minSdk),
                    "--target-sdk-version", String.valueOf(spec.targetSdk),
                    "--auto-add-overlay"));
            String extra = extraPackages(libs, appId);
            if (!extra.isEmpty()) {
                link.add("--extra-packages");
                link.add(extra);
            }
            for (MavenResolver.Lib l : libs) {
                if (l.assets != null) {
                    link.add("-A");
                    link.add(l.assets.getAbsolutePath());
                }
            }
            link.add("-o");
            link.add(base.getAbsolutePath());
            link.addAll(linkInputs);
            run(log, work, a, link.toArray(new String[0]));
            ls.onStep(current, OK, "");

            // 5. Java (termasuk R.java dari gen dan semua classpath library)
            current = STEP_JAVA;
            checkCancel();
            ls.onStep(current, RUN, "");
            List<File> sources = new ArrayList<File>();
            collectFiles(new File(projectDir, "app/src/main/java"), ".java", sources);
            collectFiles(gen, ".java", sources);
            if (sources.isEmpty()) throw new IllegalStateException("Tidak ada file .java");
            EcjCompiler.Result r = EcjCompiler.compile(ctx, sources, classpathOf(libs, spec.localJars),
                    androidJar, classes, AppPrefs.javaLevel(ctx));
            log.append(r.log).append('\n');
            if (!r.ok) throw new IllegalStateException("Compile Java gagal");
            ls.onStep(current, OK, sources.size() + " file .java");

            // 6. Dex: kelas proyek + jar library yang dipaket (compileOnly hanya classpath).
            current = STEP_DEX;
            checkCancel();
            ls.onStep(current, RUN, "");
            if ("dx".equals(AppPrefs.dexer(ctx))) {
                // Dx belum dibundel: pilihan user dicatat, build tetap memakai D8.
                log.append(LanguageLoader.get("build.dx_fallback")).append('\n');
            }
            List<File> classFiles = new ArrayList<File>();
            collectFiles(classes, ".class", classFiles);
            List<File> dexInputs = new ArrayList<File>(classFiles);
            dexInputs.addAll(packagedJars(libs, spec.localJars));
            dex(ctx, dexInputs, compileOnlyJars(libs), androidJar, dexDir, spec.minSdk);
            File dex = new File(dexDir, "classes.dex");
            log.append("dex: ").append(dex.length()).append(" byte\n");
            ls.onStep(current, OK, dex.length() + " byte");

            // 7. Gabung
            current = STEP_PACK;
            checkCancel();
            ls.onStep(current, RUN, "");
            File unsigned = new File(work, "unsigned.apk");
            packageApk(base, dex, assets, libs, spec.localJars, unsigned);
            ls.onStep(current, OK, "");

            // 8. Struktur
            current = STEP_STRUCT;
            checkCancel();
            ls.onStep(current, RUN, "");
            String structDetail = checkStructure(projectDir, unsigned, log);
            ls.onStep(current, OK, structDetail);

            // 9. Sign
            current = STEP_SIGN;
            checkCancel();
            ls.onStep(current, RUN, "");
            File out = ToolPaths.apkOutput(appName, sign.apkName);
            out.getParentFile().mkdirs();
            if (out.exists()) out.delete();
            // v1 (JAR) wajib untuk minSdk < 24, v2 selalu aktif.
            signApk(ctx, unsigned, out, sign, spec.minSdk < 24);
            log.append("APK: ").append(out.getAbsolutePath()).append('\n');
            ls.onStep(current, OK, out.length() + " byte");

            // 10. Verifikasi
            current = STEP_VERIFY;
            checkCancel();
            ls.onStep(current, RUN, "");
            verifyApk(ctx, out, log);
            ls.onStep(current, OK, "signature v2 valid");

            return out;
        } catch (Exception e) {
            ls.onStep(current, FAIL, String.valueOf(e.getMessage()));
            throw e;
        }
    }

    // ---------- dependensi ----------

    /** Resolve dependensi dari build.gradle. Pembatalan dilaporkan sebagai Cancelled, bukan error. */
    private static List<MavenResolver.Lib> resolveDeps(GradleDeps.Spec spec, final StringBuilder log) throws Exception {
        if (spec.coords.isEmpty()) return new ArrayList<MavenResolver.Lib>();
        MavenResolver r = new MavenResolver(
                new File(ToolPaths.root(), "cache/maven"),
                Arrays.asList(MavenResolver.GOOGLE, MavenResolver.CENTRAL),
                new MavenResolver.Log() {
                    public void log(String msg) { log.append(msg).append('\n'); }
                },
                new MavenResolver.Cancelled() {
                    public boolean isCancelled() {
                        Cancel c = ACTIVE.get();
                        return c != null && c.isCancelled();
                    }
                });
        try {
            return r.resolve(spec.coords);
        } catch (IllegalStateException e) {
            checkCancel();
            throw e;
        }
    }

    /** Package R untuk tiap AAR (kecuali package aplikasi sendiri), dipisah ':'. */
    private static String extraPackages(List<MavenResolver.Lib> libs, String appPkg) {
        Set<String> pk = new LinkedHashSet<String>();
        for (MavenResolver.Lib l : libs) {
            if (l.aar && l.pkg != null && !l.pkg.isEmpty() && !l.pkg.equals(appPkg)) pk.add(l.pkg);
        }
        StringBuilder sb = new StringBuilder();
        for (String p : pk) {
            if (sb.length() > 0) sb.append(':');
            sb.append(p);
        }
        return sb.toString();
    }

    /** Classpath untuk ECJ: semua library (termasuk compileOnly) dan jar lokal. */
    private static List<File> classpathOf(List<MavenResolver.Lib> libs, List<File> localJars) {
        List<File> cp = new ArrayList<File>();
        for (MavenResolver.Lib l : libs) {
            if (l.classesJar != null) cp.add(l.classesJar);
            cp.addAll(l.extraJars);
        }
        cp.addAll(localJars);
        return cp;
    }

    /** Jar yang masuk APK: library implementation/api/runtimeOnly dan jar lokal. */
    private static List<File> packagedJars(List<MavenResolver.Lib> libs, List<File> localJars) {
        List<File> out = new ArrayList<File>();
        for (MavenResolver.Lib l : libs) {
            if (!l.packaged) continue;
            if (l.classesJar != null) out.add(l.classesJar);
            out.addAll(l.extraJars);
        }
        out.addAll(localJars);
        return out;
    }

    /** Jar compileOnly: hanya dibutuhkan D8 sebagai classpath, tidak masuk APK. */
    private static List<File> compileOnlyJars(List<MavenResolver.Lib> libs) {
        List<File> out = new ArrayList<File>();
        for (MavenResolver.Lib l : libs) {
            if (l.packaged) continue;
            if (l.classesJar != null) out.add(l.classesJar);
            out.addAll(l.extraJars);
        }
        return out;
    }

    public static List<String> missingTools(File projectDir) {
        return missingTools(projectDir, true);
    }

    public static List<String> missingTools(File projectDir, boolean needDebugKey) {
        List<String> out = new ArrayList<String>();
        String api = compileSdkOf(projectDir);

        File jar = new File(ToolPaths.dir("sdk"), "android-" + api + ".jar");
        if (!jar.isFile() || jar.length() == 0) out.add(LanguageLoader.get("tools.sdk", api));

        File aapt = new File(ToolPaths.dir("aapt"), "aapt2-" + abi());
        if (!aapt.isFile() || aapt.length() == 0) out.add(LanguageLoader.get("tools.aapt"));

        if (ToolPaths.r8Jar() == null) out.add(LanguageLoader.get("tools.r8"));
        File apksig = apksigJar();
        if (apksig == null) out.add(LanguageLoader.get("tools.apksig"));
        else if (!hasClassesDex(apksig)) out.add(LanguageLoader.get("tools.apksig_undexed"));
        if (needDebugKey && !keystoreFile().isFile()) out.add("debug.jks");
        return out;
    }

    /** compileSdk dari app/build.gradle, default 34 seperti template project baru. */
    public static String compileSdkOf(File projectDir) {
        File gradle = new File(projectDir, "app/build.gradle");
        if (gradle.isFile()) {
            try {
                String text = FileUtil.readFile(gradle.getAbsolutePath());
                Matcher m = Pattern.compile("compileSdk(?:Version)?\\s*=?\\s*(\\d+)").matcher(text);
                if (m.find()) return m.group(1);
            } catch (Exception ignored) {
                // pakai default
            }
        }
        return "34";
    }

    // ---------- cache aapt2 ----------

    /** Folder cache hasil aapt2 compile, dipakai bersama oleh semua proyek. */
    private static File aaptCacheDir(Context ctx) {
        File d = new File(ctx.getCacheDir(), "aapt-cache");
        d.mkdirs();
        return d;
    }

    /**
     * Kunci cache: SHA-256 dari versi aapt2 dan isi folder res (path relatif + isi tiap file).
     * Nama proyek dan lokasi folder tidak ikut, jadi library yang sama cocok di proyek mana pun.
     */
    private static String resKey(File resDir, File aapt) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(Long.toString(aapt.length()).getBytes("UTF-8"));
        md.update((byte) 0);
        List<File> files = new ArrayList<File>();
        collectAll(resDir, files);
        TreeMap<String, File> byRel = new TreeMap<String, File>();
        for (File f : files) byRel.put(relative(resDir, f), f);
        for (Map.Entry<String, File> e : byRel.entrySet()) {
            md.update(e.getKey().getBytes("UTF-8"));
            md.update((byte) 0);
            InputStream in = new FileInputStream(e.getValue());
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            } finally {
                in.close();
            }
            md.update((byte) 0);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) hex.append(String.format(Locale.US, "%02x", b));
        return hex.toString();
    }

    // ---------- tahap ----------

    private static File prepareAapt(Context ctx) throws IOException {
        File src = new File(ToolPaths.dir("aapt"), "aapt2-" + abi());
        File dst = new File(ctx.getFilesDir(), "aapt2");
        if (!dst.isFile() || dst.length() != src.length()) {
            copy(new FileInputStream(src), dst);
        }
        dst.setExecutable(true, false);
        return dst;
    }

    /**
     * Cari cara menjalankan aapt2 yang bisa dipakai di perangkat ini. Sebagian ROM (Samsung dan
     * beberapa Android berbasis AOSP) menolak eksekusi file dari folder data app, jadi dicoba
     * berurutan: langsung dari filesDir, langsung dari codeCacheDir, lalu lewat linker sistem
     * (linker membuka aapt2 sebagai data, sehingga tidak butuh izin exec langsung).
     * Setiap percobaan yang gagal dicatat ke log supaya penyebabnya terlihat.
     */
    private static String[] aaptLauncher(Context ctx, File aapt, StringBuilder log) throws IOException {
        String src = aapt.getAbsolutePath();
        List<String[]> candidates = new ArrayList<String[]>();
        candidates.add(new String[]{src});

        try {
            File alt = new File(ctx.getCodeCacheDir(), "aapt2");
            if (!alt.isFile() || alt.length() != aapt.length()) copy(new FileInputStream(aapt), alt);
            alt.setExecutable(true, false);
            candidates.add(new String[]{alt.getAbsolutePath()});
        } catch (IOException e) {
            log.append("salin aapt2 ke cache: ").append(e.getMessage()).append('\n');
        }

        String linker = abi().contains("64") ? "/system/bin/linker64" : "/system/bin/linker";
        if (new File(linker).isFile()) candidates.add(new String[]{linker, src});

        StringBuilder errors = new StringBuilder();
        for (String[] c : candidates) {
            String err = probeAapt(c);
            if (err == null) return c;
            errors.append(Arrays.toString(c)).append(" -> ").append(err).append('\n');
        }
        log.append("aapt2 tidak bisa dijalankan:\n").append(errors);
        throw new IOException("aapt2 tidak bisa dijalankan di perangkat ini (lihat log build)");
    }

    /** Jalankan "aapt2 version". Null jika berhasil; jika gagal, kembalikan penyebabnya. */
    private static String probeAapt(String[] launcher) {
        List<String> cmd = new ArrayList<String>(Arrays.asList(launcher));
        cmd.add("version");
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String text = new String(readAll(p.getInputStream()), "UTF-8").trim();
            int code = p.waitFor();
            if (code == 0) return null;
            return "exit " + code + ": " + text;
        } catch (Throwable t) {
            return t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    /**
     * Dex input (.class atau .jar) lewat D8 dari jvm_r8.jar atau dex_r8.jar; hasil: outDir/classes.dex.
     * classpath berisi jar compileOnly dan android.jar sebagai referensi, tidak ikut ke dex.
     */
    private static void dex(Context ctx, List<File> inputs, List<File> classpath, File androidJar,
                            File outDir, int minSdk) throws Exception {
        File r8 = ToolPaths.r8Jar();
        if (r8 == null) throw new IllegalStateException("jvm_r8.jar / dex_r8.jar tidak ada di build-tools");
        ClassLoader loader = dexToolLoader(ctx, r8, "r8");
        Class<?> cmdCls = loader.loadClass("com.android.tools.r8.D8Command");
        Class<?> d8Cls = loader.loadClass("com.android.tools.r8.D8");
        Class<?> modeCls = loader.loadClass("com.android.tools.r8.CompilationMode");
        Class<?> outCls = loader.loadClass("com.android.tools.r8.OutputMode");

        List<Path> program = new ArrayList<Path>();
        for (File f : inputs) program.add(Paths.get(f.getAbsolutePath()));

        Object b = cmdCls.getMethod("builder").invoke(null);
        invoke(b, "addProgramFiles", program);
        if (!classpath.isEmpty()) {
            List<Path> cp = new ArrayList<Path>();
            for (File f : classpath) cp.add(Paths.get(f.getAbsolutePath()));
            try {
                invoke(b, "addClasspathFiles", cp);
            } catch (NoSuchMethodException e) {
                // Build r8 lain mungkin tidak punya addClasspathFiles. Sebagai referensi, addLibraryFiles cukup.
                invoke(b, "addLibraryFiles", cp);
            }
        }
        if (androidJar != null) {
            invoke(b, "addLibraryFiles",
                    Collections.singletonList(Paths.get(androidJar.getAbsolutePath())));
        }
        invoke(b, "setMinApiLevel", minSdk);
        invoke(b, "setMode", modeCls.getField("RELEASE").get(null));
        invoke(b, "setOutput", Paths.get(outDir.getAbsolutePath()),
                outCls.getField("DexIndexed").get(null));
        Object cmd = invoke(b, "build");

        try {
            d8Cls.getMethod("run", cmdCls).invoke(null, cmd);
        } catch (InvocationTargetException e) {
            Throwable root = e.getCause();
            while (root != null && root.getCause() != null) root = root.getCause();
            throw new IllegalStateException("D8 gagal: " + e.getCause() + " | penyebab: " + root, e.getCause());
        }
    }

    /**
     * Susun APK: entri dari base, lalu classes.dex, lalu assets proyek, lalu jni/*.so dari AAR,
     * lalu resource non-kelas dari jar. resources.arsc STORED dan rata 4 byte.
     * Assets library sudah masuk base lewat aapt2 -A.
     */
    private static void packageApk(File base, File dex, File assetsDir, List<MavenResolver.Lib> libs,
                                   List<File> localJars, File out) throws IOException {
        ZipFile zf = new ZipFile(base);
        CountingStream cs = new CountingStream(new FileOutputStream(out));
        ZipOutputStream zout = new ZipOutputStream(cs);
        Set<String> seen = new HashSet<String>();
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                seen.add(e.getName());
                byte[] data = readAll(zf.getInputStream(e));
                putEntry(zout, cs, e.getName(), data, "resources.arsc".equals(e.getName()));
            }
            putEntry(zout, cs, "classes.dex", readFile(dex), false);
            seen.add("classes.dex");
            if (assetsDir.isDirectory()) {
                List<File> all = new ArrayList<File>();
                collectAll(assetsDir, all);
                for (File f : all) {
                    String name = "assets/" + relative(assetsDir, f);
                    if (seen.add(name)) putEntry(zout, cs, name, readFile(f), false);
                }
            }
            for (MavenResolver.Lib l : libs) {
                if (!l.packaged || l.jni == null) continue;
                List<File> all = new ArrayList<File>();
                collectAll(l.jni, all);
                for (File f : all) {
                    String name = "lib/" + relative(l.jni, f);
                    if (seen.add(name)) putEntry(zout, cs, name, readFile(f), false);
                }
            }
            for (File jar : packagedJars(libs, localJars)) {
                copyJarResources(jar, zout, cs, seen);
            }
        } finally {
            zout.close();
            zf.close();
        }
    }

    /** Salin resource non-kelas dari jar. META-INF dilewati kecuali META-INF/services. */
    private static void copyJarResources(File jar, ZipOutputStream zout, CountingStream cs, Set<String> seen)
            throws IOException {
        ZipFile z = new ZipFile(jar);
        try {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory() || n.endsWith(".class")) continue;
                if (n.startsWith("META-INF/") && !n.startsWith("META-INF/services/")) continue;
                if (!seen.add(n)) continue;
                putEntry(zout, cs, n, readAll(z.getInputStream(e)), false);
            }
        } finally {
            z.close();
        }
    }

    /** Menghitung byte yang sudah ditulis, supaya offset data entri bisa diatur. */
    private static final class CountingStream extends FilterOutputStream {
        long count;

        CountingStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }

    private static void putEntry(ZipOutputStream zout, CountingStream cs, String name, byte[] data, boolean store)
            throws IOException {
        ZipEntry e = new ZipEntry(name);
        if (store) {
            CRC32 crc = new CRC32();
            crc.update(data);
            e.setMethod(ZipEntry.STORED);
            e.setSize(data.length);
            e.setCompressedSize(data.length);
            e.setCrc(crc.getValue());
            // Header lokal = 30 byte + nama + extra. Isi extra supaya data mulai di kelipatan 4.
            long dataStart = cs.count + 30 + name.getBytes("UTF-8").length;
            int pad = (int) ((4 - dataStart % 4) % 4);
            if (pad > 0) e.setExtra(alignExtra(pad));
        }
        zout.putNextEntry(e);
        zout.write(data);
        zout.closeEntry();
    }

    /** Extra field: id 0xD935 (dipakai zipalign), panjang data, lalu padding nol. */
    private static byte[] alignExtra(int pad) {
        byte[] x = new byte[4 + pad];
        x[0] = (byte) 0x35;
        x[1] = (byte) 0xD9;
        x[2] = (byte) (pad & 0xff);
        x[3] = (byte) ((pad >> 8) & 0xff);
        return x;
    }

    /** Pastikan resources.arsc STORED dan datanya mulai di offset kelipatan 4 (syarat targetSdk 30+). */
    private static void checkArscAligned(File apk) throws IOException {
        byte[] d = readFile(apk);
        byte[] nb = "resources.arsc".getBytes("UTF-8");
        for (int i = 0; i + 30 + nb.length <= d.length; i++) {
            if (d[i] != 0x50 || d[i + 1] != 0x4b || d[i + 2] != 0x03 || d[i + 3] != 0x04) continue;
            int method = (d[i + 8] & 0xff) | ((d[i + 9] & 0xff) << 8);
            int nameLen = (d[i + 26] & 0xff) | ((d[i + 27] & 0xff) << 8);
            int extraLen = (d[i + 28] & 0xff) | ((d[i + 29] & 0xff) << 8);
            if (nameLen != nb.length) continue;
            boolean same = true;
            for (int j = 0; j < nb.length && same; j++) same = d[i + 30 + j] == nb[j];
            if (!same) continue;
            if (method != 0) throw new IllegalStateException("resources.arsc harus STORED");
            int dataStart = i + 30 + nameLen + extraLen;
            if (dataStart % 4 != 0) {
                throw new IllegalStateException("resources.arsc tidak rata 4 byte (offset " + dataStart + ")");
            }
            return;
        }
        throw new IllegalStateException("resources.arsc tidak ditemukan di APK");
    }

    /**
     * Bandingkan isi APK dengan isi proyek. Entri wajib yang hilang membuat build gagal,
     * res dan assets yang hilang hanya jadi peringatan di log.
     */
    private static String checkStructure(File projectDir, File apk, StringBuilder log) throws IOException {
        Set<String> entries = new HashSet<String>();
        ZipFile zf = new ZipFile(apk);
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) entries.add(en.nextElement().getName());
        } finally {
            zf.close();
        }

        List<String> missingRequired = new ArrayList<String>();
        for (String req : REQUIRED) if (!entries.contains(req)) missingRequired.add(req);
        if (!missingRequired.isEmpty()) {
            throw new IllegalStateException("APK tidak lengkap, hilang: " + missingRequired);
        }
        checkArscAligned(apk);

        int resCount = 0;
        int warn = 0;
        File res = new File(projectDir, "app/src/main/res");
        List<File> resFiles = new ArrayList<File>();
        collectAll(res, resFiles);
        for (File f : resFiles) {
            String rel = relative(res, f);
            if (rel.startsWith("values")) continue; // masuk ke resources.arsc
            resCount++;
            if (!entries.contains("res/" + rel)) {
                log.append("PERINGATAN: res/").append(rel).append(" tidak ada di APK\n");
                warn++;
            }
        }

        int assetCount = 0;
        File assets = new File(projectDir, "app/src/main/assets");
        List<File> assetFiles = new ArrayList<File>();
        collectAll(assets, assetFiles);
        for (File f : assetFiles) {
            String rel = relative(assets, f);
            assetCount++;
            if (!entries.contains("assets/" + rel)) {
                log.append("PERINGATAN: assets/").append(rel).append(" tidak ada di APK\n");
                warn++;
            }
        }

        String detail = entries.size() + " entri, " + resCount + " res, " + assetCount + " asset";
        if (warn > 0) detail += ", " + warn + " peringatan";
        log.append("Struktur: ").append(detail).append('\n');
        return detail;
    }

    private static void signApk(Context ctx, File inApk, File outApk, Signing sg, boolean v1) throws Exception {
        ClassLoader cl = apksigLoader(ctx);

        KeyStore store = loadStore(sg.keys, sg.storePass);
        String alias = sg.alias;
        PrivateKey key = (PrivateKey) store.getKey(alias, sg.keyPass);
        if (key == null) {
            // PKCS12 menyimpan alias huruf kecil; coba versi kecilnya juga
            alias = sg.alias.toLowerCase(java.util.Locale.ROOT);
            key = (PrivateKey) store.getKey(alias, sg.keyPass);
        }
        if (key == null) throw new IllegalStateException("Alias '" + sg.alias + "' tidak ada di keystore");
        X509Certificate cert = (X509Certificate) store.getCertificate(alias);

        Class<?> cfgBuilderCls = cl.loadClass("com.android.apksig.ApkSigner$SignerConfig$Builder");
        Object cfgBuilder = cfgBuilderCls.getConstructor(String.class, PrivateKey.class, List.class)
                .newInstance(alias, key, Collections.singletonList(cert));
        Object cfg = invoke(cfgBuilder, "build");

        Class<?> apkBuilderCls = cl.loadClass("com.android.apksig.ApkSigner$Builder");
        Object apkBuilder = apkBuilderCls.getConstructor(List.class)
                .newInstance(Collections.singletonList(cfg));
        invoke(apkBuilder, "setInputApk", inApk);
        // Output eksplisit. Tanpa ini hasil signing tidak dijamin ditulis ke app.apk.
        invoke(apkBuilder, "setOutputApk", outApk);
        invoke(apkBuilder, "setV1SigningEnabled", v1);
        invoke(apkBuilder, "setV2SigningEnabled", true);
        invoke(apkBuilder, "setV3SigningEnabled", false);
        Object signer = invoke(apkBuilder, "build");
        invoke(signer, "sign");

        if (!outApk.isFile() || outApk.length() == 0) {
            throw new IllegalStateException("Signing tidak menulis " + outApk.getName());
        }
    }

    /** PKCS12 dulu (format keystore yang dibuat Keystore creator). JKS tidak didukung Android, jadi ditolak dengan pesan jelas. */
    private static KeyStore loadStore(KeySource src, char[] pass) throws Exception {
        byte[] data = readAll(src.open());
        String why;
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new java.io.ByteArrayInputStream(data), pass);
            return ks;
        } catch (Exception e) {
            why = String.valueOf(e.getMessage());
        }
        // Magic JKS: FE ED FE ED. Android tidak punya provider JKS, jadi file ini harus dikonversi dulu.
        if (data.length >= 4 && (data[0] & 0xff) == 0xFE && (data[1] & 0xff) == 0xED
                && (data[2] & 0xff) == 0xFE && (data[3] & 0xff) == 0xED) {
            throw new IllegalStateException("Format JKS tidak didukung Android. Konversi ke PKCS12 di PC: "
                    + "keytool -importkeystore -srckeystore FILE.jks -destkeystore FILE.p12 -deststoretype pkcs12");
        }
        throw new IllegalStateException("Keystore tidak bisa dibuka (password salah atau format tidak didukung): " + why);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** Verifikasi signature dengan ApkVerifier. Gagal jika tidak valid. */
    private static void verifyApk(Context ctx, File apk, StringBuilder log) throws Exception {
        ClassLoader cl = apksigLoader(ctx);
        Class<?> vbCls = cl.loadClass("com.android.apksig.ApkVerifier$Builder");
        Object vb = vbCls.getConstructor(File.class).newInstance(apk);
        Object verifier = invoke(vb, "build");
        Object result = invoke(verifier, "verify");
        boolean ok = (Boolean) invoke(result, "isVerified");
        if (!ok) {
            throw new IllegalStateException("Signature tidak valid: " + invoke(result, "getErrors"));
        }
        log.append("verifikasi: OK\n");
    }

    // ---------- loader ----------

    /** apksig adalah jar JVM yang sudah di-dex di PC (ada classes.dex). Disalin ke folder privat, read-only, lalu dimuat. */
    private static ClassLoader apksigLoader(Context ctx) throws Exception {
        File src = apksigJar();
        if (src == null) throw new IllegalStateException("apksig-*.jar tidak ada di build-tools");
        if (!hasClassesDex(src)) throw new IllegalStateException("apksig-*.jar belum di-dex (tidak ada classes.dex)");
        // Nama cache memakai ukuran dan waktu ubah, supaya jar yang diganti tidak memakai cache lama.
        File dexJar = new File(ctx.getDir("tools", Context.MODE_PRIVATE),
                "apksig-dex-" + src.length() + "-" + src.lastModified() + ".jar");
        if (!dexJar.isFile()) copy(new FileInputStream(src), dexJar);
        dexJar.setReadOnly();
        File opt = ctx.getDir("opt-apksig", Context.MODE_PRIVATE);
        return new DexClassLoader(dexJar.getAbsolutePath(), opt.getAbsolutePath(), null,
                ctx.getClassLoader());
    }

    /** True jika jar sudah berisi classes.dex (tidak perlu di-dex ulang). */
    private static boolean hasClassesDex(File jar) {
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(jar)) {
            return z.getEntry("classes.dex") != null;
        } catch (IOException e) {
            return false;
        }
    }


    /** Muat jar dex dari build-tools (disalin ke folder privat, read-only). */
    private static ClassLoader dexToolLoader(Context ctx, File src, String name) throws IOException {
        if (!src.isFile()) throw new IllegalStateException(src.getName() + " tidak ada di build-tools");
        File dst = new File(ctx.getDir("tools", Context.MODE_PRIVATE), name + ".jar");
        if (dst.exists()) {
            dst.setWritable(true);
            dst.delete();
        }
        copy(new FileInputStream(src), dst);
        dst.setReadOnly();
        File opt = ctx.getDir("opt-" + name, Context.MODE_PRIVATE);
        return new DexClassLoader(dst.getAbsolutePath(), opt.getAbsolutePath(), null,
                ctx.getClassLoader());
    }

    // ---------- helper ----------

    /** Jalankan aapt2 dengan launcher (lihat aaptLauncher) dan argumen args. */
    private static void run(StringBuilder log, File dir, String[] launcher, String... args) throws Exception {
        checkCancel();
        Cancel cancel = ACTIVE.get();
        List<String> cmd = new ArrayList<String>(Arrays.asList(launcher));
        cmd.addAll(Arrays.asList(args));
        Process p = new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).start();
        String text;
        try {
            if (cancel != null) cancel.track(p);
            text = new String(readAll(p.getInputStream()), "UTF-8");
            p.waitFor();
        } finally {
            if (cancel != null) cancel.untrack(p);
        }
        checkCancel();   // proses dihentikan karena dibatalkan: jangan laporkan sebagai error aapt2
        log.append("$ ").append(args[0]).append('\n').append(text).append('\n');
        if (p.exitValue() != 0) throw new IllegalStateException("aapt2 " + args[0] + " gagal:\n" + text);
    }

    /** Memanggil method publik yang cocok dengan nama dan jumlah argumen. */
    private static Object invoke(Object target, String name, Object... args) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name)) continue;
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length != args.length) continue;
            boolean match = true;
            for (int i = 0; i < args.length && match; i++) {
                if (args[i] != null) match = pt[i].isPrimitive() || pt[i].isInstance(args[i]);
            }
            if (match) {
                try {
                    return m.invoke(target, args);
                } catch (InvocationTargetException e) {
                    Throwable c = e.getCause();
                    if (c instanceof Exception) throw (Exception) c;
                    throw e;
                }
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }


    private static File keystoreFile() {
        return new File(ToolPaths.root(), "debug.jks");
    }

    private static File apksigJar() {
        File[] fs = ToolPaths.root().listFiles();
        if (fs == null) return null;
        for (File f : fs) {
            if (f.isFile() && f.getName().startsWith("apksig") && f.getName().endsWith(".jar")) return f;
        }
        return null;
    }

    private static String abi() {
        String[] abis = android.os.Build.SUPPORTED_ABIS;
        return abis.length > 0 ? abis[0] : "arm64-v8a";
    }

    private static void collectFiles(File dir, String suffix, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectFiles(f, suffix, out);
            else if (f.getName().endsWith(suffix)) out.add(f);
        }
    }

    /** Semua file di dalam dir, tanpa filter. */
    private static void collectAll(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectAll(f, out);
            else out.add(f);
        }
    }

    private static String relative(File base, File f) {
        return f.getAbsolutePath().substring(base.getAbsolutePath().length() + 1).replace(File.separatorChar, '/');
    }

    private static void copy(InputStream in, File dst) throws IOException {
        OutputStream out = new FileOutputStream(dst);
        try {
            pipe(in, out);
        } finally {
            out.close();
            in.close();
        }
    }

    private static byte[] readFile(File f) throws IOException {
        return readAll(new FileInputStream(f));
    }

    private static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }
}