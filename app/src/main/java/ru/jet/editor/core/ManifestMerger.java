package ru.jet.editor.core;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Gabungkan AndroidManifest.xml proyek dengan manifest library (AAR).
 *
 * Aturan (disederhanakan dari manifest merger AGP):
 *  - Manifest proyek menang. Elemen library ditambahkan bila kuncinya belum ada.
 *  - Kunci = nama tag + android:name (atau name untuk meta-data). Untuk elemen tanpa nama
 *    (mis. <intent>), elemen ikut ditambahkan utuh.
 *  - <uses-permission>, <permission>, <uses-feature>, dan komponen di <application>
 *    (activity, service, receiver, provider, meta-data, uses-library) digabung.
 *  - <uses-sdk> dari library diabaikan; minSdk/targetSdk diambil dari build.gradle.
 *  - Atribut tools:* dihapus. Elemen dengan tools:node="remove*" dari library dibuang.
 *  - ${applicationId} di nilai atribut diganti dengan package aplikasi.
 */
public final class ManifestMerger {

    public static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    public static final String TOOLS_NS = "http://schemas.android.com/tools";

    private static final Set<String> APP_CHILDREN = new HashSet<String>();
    static {
        for (String t : new String[]{"activity", "activity-alias", "service", "receiver", "provider",
                "meta-data", "uses-library", "uses-native-library"}) {
            APP_CHILDREN.add(t);
        }
    }

    private ManifestMerger() {}

    /** Hasil penggabungan ditulis ke {@code out}. Manifest proyek dibaca dari {@code appManifest}. */
    public static void merge(File appManifest, List<File> libManifests, String appPackage, File out)
            throws Exception {
        Document app = parse(appManifest);
        Element root = app.getDocumentElement();
        Element appEl = child(root, "application");
        if (appEl == null) {
            appEl = app.createElement("application");
            root.appendChild(appEl);
        }

        for (File lf : libManifests) {
            Document lib = parse(lf);
            Element libRoot = lib.getDocumentElement();
            for (Element e : children(libRoot)) {
                String tag = e.getTagName();
                if ("uses-sdk".equals(tag) || isRemoved(e)) continue;
                if ("application".equals(tag)) {
                    for (Element c : children(e)) {
                        if (!APP_CHILDREN.contains(c.getTagName()) || isRemoved(c)) continue;
                        addIfAbsent(app, appEl, c, appPackage);
                    }
                } else if ("queries".equals(tag)) {
                    Element q = child(root, "queries");
                    if (q == null) {
                        q = app.createElement("queries");
                        root.appendChild(q);
                    }
                    for (Element c : children(e)) addIfAbsent(app, q, c, appPackage);
                } else {
                    addIfAbsent(app, root, e, appPackage);
                }
            }
        }

        stripTools(root);
        applyPlaceholders(root, appPackage);
        write(app, out);
    }

    // ---------- inti ----------

    private static void addIfAbsent(Document doc, Element parent, Element src, String pkg) {
        String key = keyOf(src);
        if (key != null) {
            for (Element existing : children(parent)) {
                if (src.getTagName().equals(existing.getTagName()) && key.equals(keyOf(existing))) {
                    return;     // manifest proyek menang
                }
            }
        }
        Node copy = doc.importNode(src, true);
        parent.appendChild(copy);
    }

    /** Kunci identitas elemen, atau null jika elemen tidak punya nama. */
    static String keyOf(Element e) {
        String name = e.getAttributeNS(ANDROID_NS, "name");
        if (name.isEmpty()) name = e.getAttribute("name");
        if (name.isEmpty()) return null;
        return e.getTagName() + "|" + name;
    }

    private static boolean isRemoved(Element e) {
        String node = e.getAttributeNS(TOOLS_NS, "node");
        return node != null && node.startsWith("remove");
    }

    private static void stripTools(Element e) {
        NamedNodeMap attrs = e.getAttributes();
        List<String> drop = new ArrayList<String>();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            if (TOOLS_NS.equals(a.getNamespaceURI())) drop.add(a.getNodeName());
        }
        for (String n : drop) e.removeAttribute(n);
        for (Element c : children(e)) {
            if (c.getTagName().startsWith("tools:")) e.removeChild(c);
            else stripTools(c);
        }
    }

    private static void applyPlaceholders(Element e, String pkg) {
        if (pkg == null) return;
        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            String v = a.getNodeValue();
            if (v != null && v.contains("${applicationId}")) {
                a.setNodeValue(v.replace("${applicationId}", pkg));
            }
        }
        for (Element c : children(e)) applyPlaceholders(c, pkg);
    }

    // ---------- util ----------

    private static Document parse(File f) throws Exception {
        DocumentBuilderFactory df = DocumentBuilderFactory.newInstance();
        df.setNamespaceAware(true);
        return df.newDocumentBuilder().parse(f);
    }

    private static void write(Document doc, File out) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        File abs = out.getAbsoluteFile();
        abs.getParentFile().mkdirs();
        t.transform(new DOMSource(doc), new StreamResult(abs));
    }

    private static Element child(Element parent, String tag) {
        for (Element e : children(parent)) if (tag.equals(e.getTagName())) return e;
        return null;
    }

    static List<Element> children(Element parent) {
        List<Element> out = new ArrayList<Element>();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) out.add((Element) n);
        }
        return out;
    }
}
