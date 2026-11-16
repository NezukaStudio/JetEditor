package ru.jet.editor.core;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Resolver dependensi Maven untuk build di perangkat.
 *
 *  - Unduh POM dan artifact (.aar / .jar) dari Google Maven lalu Maven Central, dengan cache.
 *  - Ikuti parent POM, dependencyManagement, BOM (scope import), dan properti ${...}.
 *  - Konflik versi: versi tertinggi dipakai (perilaku default Gradle).
 *  - AAR dibongkar: classes.jar, libs/*.jar, res/, assets/, jni/, AndroidManifest.xml.
 *
 * Batasan: tidak membaca Gradle Module Metadata (.module), jadi POM yang jadi sumber
 * kebenaran. Hampir semua library androidx dan Material memakai POM yang lengkap.
 */
public final class MavenResolver {

    public static final String GOOGLE = "https://dl.google.com/dl/android/maven2/";
    public static final String CENTRAL = "https://repo1.maven.org/maven2/";
    private static final int MAX_ITER = 8;

    public interface Log { void log(String s); }
    public interface Cancelled { boolean isCancelled(); }

    /** Satu library yang sudah diunduh dan (untuk AAR) sudah dibongkar. */
    public static final class Lib {
        public final String group;
        public final String artifact;
        public final String version;
        public final boolean packaged;
        public final boolean aar;
        /** classes.jar untuk AAR, atau file .jar untuk library jar murni. */
        public File classesJar;
        /** Jar tambahan dari libs/ di dalam AAR. */
        public final List<File> extraJars = new ArrayList<File>();
        public File res;
        public File assets;
        public File jni;
        public File manifest;
        /** Package Java dari manifest AAR. Dipakai untuk aapt2 --extra-packages. */
        public String pkg;

        Lib(String g, String a, String v, boolean packaged, boolean aar) {
            this.group = g; this.artifact = a; this.version = v;
            this.packaged = packaged; this.aar = aar;
        }

        public String coord() { return group + ":" + artifact + ":" + version; }
    }

    private final File cache;
    private final List<String> repos;
    private final Log log;
    private final Cancelled cancelled;
    private final Map<String, Pom> pomCache = new HashMap<String, Pom>();

    public MavenResolver(File cacheDir, List<String> repos, Log log, Cancelled cancelled) {
        this.cache = cacheDir;
        this.repos = repos;
        this.log = log;
        this.cancelled = cancelled;
    }

    // ---------- model POM ----------

    private static final class Dep {
        String g, a, v, scope, type;
        boolean optional;
    }

    private static final class Pom {
        String g, a, v, packaging = "jar";
        Pom parent;
        final Map<String, String> props = new HashMap<String, String>();
        final List<Dep> deps = new ArrayList<Dep>();
        final List<Dep> managed = new ArrayList<Dep>();
        Map<String, Dep> effManaged;
    }

    // ---------- API utama ----------

    /**
     * Selesaikan koordinat root beserta transitifnya, unduh, dan bongkar AAR.
     * Root yang gagal diunduh melempar exception; transitif yang gagal hanya dicatat.
     */
    public List<Lib> resolve(List<GradleDeps.Coord> roots) throws Exception {
        Map<String, String> pinned = new HashMap<String, String>();
        Map<String, Art> nodes = null;
        for (int iter = 0; iter < MAX_ITER; iter++) {
            checkCancel();
            Map<String, String> highest = new HashMap<String, String>();
            nodes = walk(roots, pinned, highest);
            if (highest.equals(pinned)) break;
            pinned = highest;
        }

        List<Lib> out = new ArrayList<Lib>();
        for (Art n : nodes.values()) {
            checkCancel();
            Lib lib = fetch(n);
            if (lib != null) out.add(lib);
        }
        return out;
    }

    // ---------- graf ----------

    private static final class Art {
        final String g, a, v;
        final boolean packaged;
        final boolean root;
        final Pom pom;
        Art(String g, String a, String v, boolean packaged, Pom pom, boolean root) {
            this.g = g; this.a = a; this.v = v; this.packaged = packaged; this.pom = pom; this.root = root;
        }
    }

    /**
     * Telusuri graf dari root. Versi per ga diambil dari pinned bila ada.
     * Semua versi yang diminta dicatat di highest (ambil yang tertinggi).
     */
    private Map<String, Art> walk(List<GradleDeps.Coord> roots, Map<String, String> pinned,
                                   Map<String, String> highest) throws Exception {
        Map<String, Art> nodes = new LinkedHashMap<String, Art>();
        // Root yang packaged dulu, lalu compileOnly. Node yang sudah packaged tidak ditimpa.
        for (int pass = 0; pass < 2; pass++) {
            boolean wantPackaged = pass == 0;
            Deque<Object[]> queue = new ArrayDeque<Object[]>();
            for (GradleDeps.Coord c : roots) {
                if (c.packaged != wantPackaged) continue;
                record(highest, c.group + ":" + c.artifact, c.version);
                queue.add(new Object[]{c.group, c.artifact, c.version, wantPackaged, true});
            }
            while (!queue.isEmpty()) {
                Object[] item = queue.poll();
                String g = (String) item[0], a = (String) item[1], reqV = (String) item[2];
                boolean packaged = (Boolean) item[3];
                boolean root = (Boolean) item[4];
                String ga = g + ":" + a;
                String v = pinned.containsKey(ga) ? pinned.get(ga) : reqV;
                Art existing = nodes.get(ga);
                if (existing != null) {
                    // Sudah ada. Jika sebelumnya compileOnly dan sekarang packaged, naikkan status.
                    if (!existing.packaged && packaged && existing.v.equals(v)) {
                        nodes.put(ga, new Art(g, a, v, true, existing.pom, existing.root));
                    }
                    continue;
                }
                Pom pom = loadPomOrNull(g, a, v, root);
                if (pom == null) continue;
                nodes.put(ga, new Art(g, a, v, packaged, pom, root));
                for (Dep d : effectiveDeps(pom)) {
                    if (d.optional) continue;
                    if (!"compile".equals(d.scope) && !"runtime".equals(d.scope) && d.scope != null) continue;
                    String dga = d.g + ":" + d.a;
                    record(highest, dga, d.v);
                    queue.add(new Object[]{d.g, d.a, d.v, packaged, false});
                }
            }
        }
        return nodes;
    }

    private static void record(Map<String, String> highest, String ga, String v) {
        String cur = highest.get(ga);
        if (cur == null || compareVersions(v, cur) > 0) highest.put(ga, v);
    }

    /** Pembanding versi longgar: bagian angka dibandingkan numerik, sisanya leksikografis. */
    static int compareVersions(String a, String b) {
        String[] pa = a.split("[.\\-+]");
        String[] pb = b.split("[.\\-+]");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            String x = i < pa.length ? pa[i] : "0";
            String y = i < pb.length ? pb[i] : "0";
            boolean nx = x.matches("\\d+"), ny = y.matches("\\d+");
            int c;
            if (nx && ny) {
                c = new java.math.BigInteger(x).compareTo(new java.math.BigInteger(y));
            } else {
                c = x.compareTo(y);
            }
            if (c != 0) return c;
        }
        return 0;
    }

    // ---------- unduh ----------

    private Lib fetch(Art n) throws Exception {
        String packaging = n.pom.packaging;
        if ("pom".equals(packaging)) return null;   // hanya kumpulan dependensi
        boolean aar = "aar".equals(packaging);
        if (!aar && !"jar".equals(packaging) && !"bundle".equals(packaging)) {
            log.log("Lewati " + n.g + ":" + n.a + " (packaging " + packaging + " tidak didukung)");
            return null;
        }
        String ext = aar ? "aar" : "jar";
        File file = download(n.g, n.a, n.v, ext);
        if (file == null) {
            String msg = n.g + ":" + n.a + ":" + n.v + " (." + ext + " tidak ditemukan di repo)";
            // Dependensi langsung yang hilang harus gagal jelas, bukan diam-diam dilewati.
            if (n.root) throw new IllegalStateException("Dependensi tidak ditemukan: " + msg);
            log.log("Lewati transitif " + msg);
            return null;
        }
        Lib lib = new Lib(n.g, n.a, n.v, n.packaged, aar);
        if (aar) explodeAar(file, lib);
        else lib.classesJar = file;
        return lib;
    }

    /** Unduh file dari repo pertama yang punya; hasil disimpan di cache. Null jika tidak ada. */
    private File download(String g, String a, String v, String ext) throws Exception {
        String rel = g.replace('.', '/') + "/" + a + "/" + v + "/" + a + "-" + v + "." + ext;
        File dest = new File(cache, rel);
        File missing = new File(cache, rel + ".missing");
        if (dest.isFile() && dest.length() > 0) return dest;
        if (missing.isFile()) return null;

        if (probe(rel, dest)) return dest;
        return null;
    }

    /**
     * Cari rel di setiap repo lalu unduh ke dest. False jika tidak ada di repo mana pun.
     * Jika SEMUA repo gagal dihubungi (jaringan), melempar exception dan tidak menulis penanda
     * .missing, supaya build berikutnya mencoba lagi.
     */
    private boolean probe(String rel, File dest) throws Exception {
        File missing = new File(cache, rel + ".missing");
        IOException lastErr = null;
        int unreachable = 0;
        for (String repo : repos) {
            checkCancel();
            int code;
            try {
                code = head(repo + rel);
            } catch (IOException e) {
                lastErr = e;
                unreachable++;
                continue;
            }
            if (code == 200) {
                get(repo + rel, dest);
                return true;
            }
        }
        if (unreachable == repos.size() && lastErr != null) {
            throw new IOException("Tidak bisa terhubung ke repositori Maven (" + lastErr.getMessage()
                    + "). Periksa koneksi internet.", lastErr);
        }
        dest.getParentFile().mkdirs();
        missing.createNewFile();
        return false;
    }

    private String fetchText(String g, String a, String v) throws Exception {
        String rel = g.replace('.', '/') + "/" + a + "/" + v + "/" + a + "-" + v + ".pom";
        File dest = new File(cache, rel);
        File missing = new File(cache, rel + ".missing");
        if (!dest.isFile() || dest.length() == 0) {
            if (missing.isFile()) return null;
            if (!probe(rel, dest)) return null;
        }
        return new String(java.nio.file.Files.readAllBytes(dest.toPath()), "UTF-8");
    }

    private static int head(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod("HEAD");
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            return c.getResponseCode();
        } finally {
            c.disconnect();
        }
    }

    private static void get(String url, File dest) throws IOException {
        dest.getParentFile().mkdirs();
        File part = new File(dest.getParentFile(), dest.getName() + ".part");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        InputStream in = null;
        OutputStream out = null;
        try {
            in = c.getInputStream();
            out = new FileOutputStream(part);
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } finally {
            if (out != null) out.close();
            if (in != null) in.close();
            c.disconnect();
        }
        if (dest.exists()) dest.delete();
        if (!part.renameTo(dest)) throw new IOException("Gagal menyimpan " + dest.getName());
    }

    // ---------- bongkar AAR ----------

    private void explodeAar(File aar, Lib lib) throws Exception {
        File dir = new File(cache, "exploded/" + lib.group + "/" + lib.artifact + "/" + lib.version);
        File done = new File(dir, ".done");
        if (!done.isFile()) {
            deleteRecursive(dir);
            dir.mkdirs();
            unzip(aar, dir);
            if (!done.createNewFile()) throw new IOException("Gagal menandai " + dir);
        }
        lib.classesJar = new File(dir, "classes.jar");
        if (!lib.classesJar.isFile()) lib.classesJar = null;
        File libs = new File(dir, "libs");
        File[] jars = libs.listFiles();
        if (jars != null) for (File j : jars) if (j.getName().endsWith(".jar")) lib.extraJars.add(j);
        File res = new File(dir, "res");
        lib.res = res.isDirectory() && hasFiles(res) ? res : null;
        File assets = new File(dir, "assets");
        lib.assets = assets.isDirectory() && hasFiles(assets) ? assets : null;
        File jni = new File(dir, "jni");
        lib.jni = jni.isDirectory() ? jni : null;
        lib.manifest = new File(dir, "AndroidManifest.xml");
        if (lib.manifest.isFile()) {
            lib.pkg = readPackage(lib.manifest);
        }
        if (lib.pkg == null) {
            // AAR baru kadang tidak menulis atribut package. Pakai groupId sebagai tebakan terakhir.
            lib.pkg = lib.group;
            log.log("Peringatan: package tidak ada di manifest " + lib.coord() + ", memakai " + lib.pkg);
        }
    }

    static String readPackage(File manifest) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(manifest);
        return d.getDocumentElement().getAttribute("package");
    }

    private static boolean hasFiles(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return false;
        for (File f : fs) {
            if (f.isFile()) return true;
            if (f.isDirectory() && hasFiles(f)) return true;
        }
        return false;
    }

    static void unzip(File zip, File dest) throws IOException {
        String base = dest.getCanonicalPath() + File.separator;
        ZipFile zf = new ZipFile(zip);
        try {
            java.util.Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                File out = new File(dest, e.getName());
                if (!out.getCanonicalPath().startsWith(base)) {
                    throw new IOException("Entri zip di luar folder tujuan: " + e.getName());
                }
                if (e.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                out.getParentFile().mkdirs();
                InputStream in = zf.getInputStream(e);
                OutputStream os = new FileOutputStream(out);
                try {
                    byte[] buf = new byte[16 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
                } finally {
                    os.close();
                    in.close();
                }
            }
        } finally {
            zf.close();
        }
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }

    // ---------- POM ----------

    private Pom loadPomOrNull(String g, String a, String v, boolean root) throws Exception {
        String key = g + ":" + a + ":" + v;
        if (pomCache.containsKey(key)) return pomCache.get(key);
        String xml = fetchText(g, a, v);
        if (xml == null) {
            if (root) throw new IllegalStateException("Dependensi tidak ditemukan: " + key);
            log.log("Lewati transitif " + key + " (POM tidak ada)");
            pomCache.put(key, null);
            return null;
        }
        Pom p = parsePom(xml, g, a, v);
        pomCache.put(key, p);
        return p;
    }

    private Pom parsePom(String xml, String g, String a, String v) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        f.setExpandEntityReferences(false);
        Element root = f.newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)))
                .getDocumentElement();

        Pom p = new Pom();
        p.g = text(root, "groupId");
        p.a = text(root, "artifactId");
        p.v = text(root, "version");
        Element parent = child(root, "parent");
        if (parent != null) {
            String pg = text(parent, "groupId"), pa = text(parent, "artifactId"), pv = text(parent, "version");
            if (p.g == null) p.g = pg;
            if (p.v == null) p.v = pv;
            if (pg != null && pa != null && pv != null) {
                p.parent = loadPomOrNull(pg, pa, pv, false);
            }
        }
        if (p.g == null) p.g = g;
        if (p.a == null) p.a = a;
        if (p.v == null) p.v = v;
        String pk = text(root, "packaging");
        if (pk != null) p.packaging = pk;

        Element props = child(root, "properties");
        if (props != null) {
            for (Element e : elements(props)) p.props.put(e.getTagName(), e.getTextContent().trim());
        }
        Element dm = child(root, "dependencyManagement");
        if (dm != null && child(dm, "dependencies") != null) {
            for (Element e : elements(child(dm, "dependencies"), "dependency")) p.managed.add(readDep(e));
        }
        Element deps = child(root, "dependencies");
        if (deps != null) {
            for (Element e : elements(deps, "dependency")) p.deps.add(readDep(e));
        }
        return p;
    }

    private Dep readDep(Element e) {
        Dep d = new Dep();
        d.g = text(e, "groupId");
        d.a = text(e, "artifactId");
        d.v = text(e, "version");
        d.scope = text(e, "scope");
        d.type = text(e, "type");
        String opt = text(e, "optional");
        d.optional = "true".equals(opt);
        return d;
    }

    /**
     * Dependensi efektif: dari parent lalu anak (anak menimpa). Versi yang kosong diambil dari
     * dependencyManagement. Properti ${...} diganti.
     */
    private List<Dep> effectiveDeps(Pom p) throws Exception {
        Map<String, Dep> merged = new LinkedHashMap<String, Dep>();
        List<Dep> chain = new ArrayList<Dep>();
        collectDeps(p, chain);
        Map<String, Dep> managed = effectiveManaged(p);
        for (Dep d : chain) {
            Dep r = copy(d, p);
            String ga = r.g + ":" + r.a;
            if (r.v == null) {
                Dep m = managed.get(ga);
                r.v = m == null ? null : interp(m.v, p);
            }
            if (r.v == null) continue;
            merged.put(ga, r);
        }
        return new ArrayList<Dep>(merged.values());
    }

    private void collectDeps(Pom p, List<Dep> out) {
        if (p == null) return;
        collectDeps(p.parent, out);
        for (Dep d : p.deps) {
            // anak menimpa parent: hapus dulu yang sama
            String ga = d.g + ":" + d.a;
            for (int i = out.size() - 1; i >= 0; i--) {
                if ((out.get(i).g + ":" + out.get(i).a).equals(ga)) out.remove(i);
            }
            out.add(d);
        }
    }

    private Map<String, Dep> effectiveManaged(Pom p) throws Exception {
        if (p.effManaged != null) return p.effManaged;
        Map<String, Dep> m = new HashMap<String, Dep>();
        if (p.parent != null) m.putAll(effectiveManaged(p.parent));
        for (Dep d : p.managed) {
            if ("import".equals(d.scope) && "pom".equals(d.type)) {
                Pom bom = loadPomOrNull(interp(d.g, p), interp(d.a, p), interp(d.v, p), false);
                if (bom != null) {
                    for (Map.Entry<String, Dep> e : effectiveManaged(bom).entrySet()) {
                        if (!m.containsKey(e.getKey())) m.put(e.getKey(), e.getValue());
                    }
                }
                continue;
            }
            m.put(d.g + ":" + d.a, d);
        }
        p.effManaged = m;
        return m;
    }

    /** Salin dependensi dengan properti sudah diganti. */
    private Dep copy(Dep d, Pom owner) {
        Dep r = new Dep();
        r.g = interp(d.g, owner);
        r.a = interp(d.a, owner);
        r.v = d.v == null ? null : interp(d.v, owner);
        r.scope = d.scope;
        r.type = d.type;
        r.optional = d.optional;
        return r;
    }

    private static final Pattern PROP = Pattern.compile("\\$\\{([^}]+)\\}");

    private String interp(String s, Pom owner) {
        if (s == null) return null;
        String cur = s;
        for (int i = 0; i < 10 && cur.contains("${"); i++) {
            Matcher m = PROP.matcher(cur);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String val = lookup(m.group(1), owner);
                m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? m.group(0) : val));
            }
            m.appendTail(sb);
            cur = sb.toString();
        }
        return cur;
    }

    private String lookup(String key, Pom p) {
        if (p == null) return null;
        if ("project.version".equals(key) || "version".equals(key)) return effective(p, "v");
        if ("project.groupId".equals(key)) return effective(p, "g");
        if ("project.artifactId".equals(key)) return p.a;
        for (Pom q = p; q != null; q = q.parent) {
            if (q.props.containsKey(key)) return q.props.get(key);
        }
        return null;
    }

    private static String effective(Pom p, String field) {
        for (Pom q = p; q != null; q = q.parent) {
            String val = "v".equals(field) ? q.v : q.g;
            if (val != null) return val;
        }
        return null;
    }

    // ---------- util ----------

    private void checkCancel() {
        if (cancelled != null && cancelled.isCancelled()) {
            throw new IllegalStateException("Dibatalkan");
        }
    }

    private static Element child(Element parent, String name) {
        for (Element e : elements(parent)) {
            if (name.equals(e.getTagName())) return e;
        }
        return null;
    }

    private static String text(Element parent, String name) {
        Element e = child(parent, name);
        if (e == null) return null;
        String t = e.getTextContent().trim();
        return t.isEmpty() ? null : t;
    }

    private static List<Element> elements(Element parent) {
        List<Element> out = new ArrayList<Element>();
        if (parent == null) return out;
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            org.w3c.dom.Node n = kids.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) out.add((Element) n);
        }
        return out;
    }

    private static List<Element> elements(Element parent, String tag) {
        List<Element> out = new ArrayList<Element>();
        for (Element e : elements(parent)) if (tag.equals(e.getTagName())) out.add(e);
        return out;
    }
}
