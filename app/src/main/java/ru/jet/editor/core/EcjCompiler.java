package ru.jet.editor.core;

import android.content.Context;

import dalvik.system.DexClassLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;

/**
 * Compile Java memakai ECJ yang sudah di-dex.
 * Sumber jar: /sdcard/.JetEdit/build-tools/ecj.jar jika ada, jika tidak pakai assets/tools/ecj.jar.
 * Jar disalin ke folder privat app dan ditandai read-only (Android 14+ menolak dex yang bisa ditulis).
 *
 * Panggil dari thread latar belakang, bukan dari UI thread.
 */
public final class EcjCompiler {

    private static final String ASSET = "tools/ecj.jar";
    private static final String EXTERNAL_JAR = "/sdcard/.JetEdit/build-tools/ecj.jar";
    private static final String MAIN = "org.eclipse.jdt.core.compiler.batch.BatchCompiler";

    public static final class Result {
        public final boolean ok;
        /** Pesan kompiler (error dan warning), termasuk penyebab exception bila ada. */
        public final String log;

        Result(boolean ok, String log) {
            this.ok = ok;
            this.log = log;
        }
    }

    private EcjCompiler() {}

    /**
     * @param sources   daftar file .java yang dikompilasi
     * @param classpath entri classpath tambahan, boleh kosong
     * @param bootJar   android.jar untuk API yang dipilih
     * @param outDir    folder keluaran .class
     */
    public static Result compile(Context ctx, List<File> sources, List<File> classpath,
                                 File bootJar, File outDir) throws Exception {
        return compile(ctx, sources, classpath, bootJar, outDir, "1.8");
    }

    /** javaLevel: "1.7", "1.8", "11", "15", atau "16" (dipakai untuk -source dan -target). */
    public static Result compile(Context ctx, List<File> sources, List<File> classpath,
                                 File bootJar, File outDir, String javaLevel) throws Exception {
        outDir.mkdirs();

        ClassLoader loader = loadEcj(ctx);
        Class<?> batch = loader.loadClass(MAIN);
        Class<?> progressCls = loader.loadClass("org.eclipse.jdt.core.compiler.CompilationProgress");
        Method compile = batch.getMethod("compile", String[].class, PrintWriter.class,
                PrintWriter.class, progressCls);

        List<String> args = new java.util.ArrayList<>();
        args.add("-source");
        args.add(javaLevel);
        args.add("-target");
        args.add(javaLevel);
        args.add("-encoding");
        args.add("UTF-8");
        args.add("-proc:none");
        args.add("-nowarn");
        args.add("-bootclasspath");
        // Stub java.lang.invoke untuk compile lambda. Tidak dipaket ke APK; android.jar tidak memuatnya.
        String boot = bootJar.getAbsolutePath();
        File lambdaStub = findLambdaStub();
        if (lambdaStub != null) boot += File.pathSeparator + lambdaStub.getAbsolutePath();
        args.add(boot);
        if (!classpath.isEmpty()) {
            args.add("-classpath");
            args.add(joinPaths(classpath));
        }
        args.add("-d");
        args.add(outDir.getAbsolutePath());
        for (File f : sources) args.add(f.getAbsolutePath());

        StringWriter buf = new StringWriter();
        PrintWriter out = new PrintWriter(buf);
        boolean ok;
        // ECJ mencari pesan sesuai locale perangkat; paksa Inggris supaya tidak mencari bundle in_ID.
        Locale prev = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        try {
            ok = (Boolean) compile.invoke(null, args.toArray(new String[0]), out, out, null);
        } catch (InvocationTargetException e) {
            // Exception dari dalam ECJ dibungkus; tampilkan penyebab aslinya, bukan hanya nama wrapper.
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            out.println("ECJ melempar exception:");
            cause.printStackTrace(out);
            ok = false;
        } finally {
            Locale.setDefault(prev);
        }
        out.flush();
        return new Result(ok, buf.toString());
    }

    /**
     * Cari file lambda*.jar di ToolPaths.root(). Mengembalikan null jika tidak ada.
     * Jika ada lebih dari satu, yang namanya paling awal secara alfabet dipakai.
     */
    private static File findLambdaStub() {
        File root = ToolPaths.root();
        if (root == null) return null;
        File[] matches = root.listFiles((dir, name) ->
                name.startsWith("lambda") && name.endsWith(".jar"));
        if (matches == null || matches.length == 0) return null;
        Arrays.sort(matches);
        return matches[0];
    }

    /** Salin ecj.jar ke folder privat app, tandai read-only, lalu muat dengan DexClassLoader. */
    private static ClassLoader loadEcj(Context ctx) throws Exception {
        File dir = ctx.getDir("ecj", Context.MODE_PRIVATE);
        File jar = new File(dir, "ecj.jar");
        if (jar.exists()) {
            jar.setWritable(true);
            jar.delete();
        }

        File ext = new File(EXTERNAL_JAR);
        if (ext.isFile() && ext.length() > 0) {
            copyStream(new FileInputStream(ext), jar);
        } else {
            copyStream(ctx.getAssets().open(ASSET), jar);
        }
        jar.setReadOnly();

        File opt = ctx.getDir("ecj-opt", Context.MODE_PRIVATE);
        return new DexClassLoader(jar.getAbsolutePath(), opt.getAbsolutePath(), null,
                ctx.getClassLoader());
    }

    private static void copyStream(InputStream in, File dest) throws Exception {
        OutputStream out = new FileOutputStream(dest);
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } finally {
            out.close();
            in.close();
        }
    }

    private static String joinPaths(List<File> files) {
        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            if (sb.length() > 0) sb.append(File.pathSeparatorChar);
            sb.append(f.getAbsolutePath());
        }
        return sb.toString();
    }
}