package ru.jet.editor.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inflate layout XML dari folder yang strukturnya seperti res/:
 *   root/res/layout/*.xml
 *   root/res/drawable(*)/*.png|webp|jpg|gif|xml
 *   root/res/values(*)/*.xml   (string, color, dimen)
 *
 * Tidak memakai LayoutInflater: di perangkat ini LayoutInflater gagal dengan
 * "XmlPullAttributes cannot be cast to XmlBlock$Parser" karena parser biasa tidak
 * diterima framework. Jadi view dibuat lewat constructor(Context) dan atribut
 * diterapkan manual. Tanpa androidx, appcompat, dan tanpa R.
 */
public class LayoutPreview {

    private static final String TAG = "LayoutPreview";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final String[] WIDGET_PACKAGES = {"", "android.widget.", "android.view.", "android.webkit."};

    private static final Pattern VALUE_RES =
            Pattern.compile("<(string|color|dimen)\\s+name=\"([\\w.]+)\"[^>]*>([^<]*)</\\1>");
    private static final Pattern VALUE_REF = Pattern.compile("@(string|color|dimen)/([\\w.]+)");
    private static final Pattern NUM_UNIT = Pattern.compile("(-?[0-9]*\\.?[0-9]+)\\s*([a-z]*)");

    private final Context ctx;
    private final File root;
    private final float density;
    private final float scaledDensity;
    private final Map<String, String> values = new HashMap<>();          // "string/app_name" -> nilai
    private final Map<String, Drawable> drawableCache = new HashMap<>();
    private final Map<String, Integer> ids = new HashMap<>();            // nama id -> id view

    public LayoutPreview(Context ctx, File root) {
        this.ctx = ctx;
        this.root = root;
        this.density = ctx.getResources().getDisplayMetrics().density;
        this.scaledDensity = ctx.getResources().getDisplayMetrics().scaledDensity;
        loadValues();
    }

    /** Inflate res/layout/{layoutName}.xml dari folder root. */
    public View inflate(String layoutName) throws Exception {
        return inflateXml(readFile(new File(root, "res/layout/" + layoutName + ".xml")));
    }

    /** Inflate dari string XML. Drawable dicari di root. */
    public View inflateXml(String xml) throws Exception {
        XmlPullParser p = newParser(resolveValues(xml));
        int ev = p.next();
        while (ev != XmlPullParser.START_TAG) {
            if (ev == XmlPullParser.END_DOCUMENT) throw new Exception("XML kosong");
            ev = p.next();
        }
        Map<String, String> la = new HashMap<>();
        View v = buildView(p, la);
        v.setLayoutParams(makeParams(null, la));
        return v;
    }

    /** Nama id yang dipakai saat inflate (id view -> nama tanpa prefiks), untuk lapisan debug. */
    public Map<Integer, String> idNames() {
        Map<Integer, String> out = new HashMap<>();
        for (Map.Entry<String, Integer> e : ids.entrySet()) out.put(e.getValue(), e.getKey());
        return out;
    }

    /** Pengganti R.drawable.xxx. Mengembalikan null jika tidak ada. */
    public Drawable getDrawable(String name) {
        if (drawableCache.containsKey(name)) return drawableCache.get(name);
        Drawable d = null;
        File f = findDrawableFile(name);
        if (f != null) {
            try {
                d = loadDrawableFile(f);
            } catch (Exception e) {
                Log.w(TAG, "drawable gagal: " + f, e);
            }
        }
        drawableCache.put(name, d);
        return d;
    }

    // ---------------------------------------------------------------- values

    private void loadValues() {
        File[] dirs = new File(root, "res").listFiles(f -> f.isDirectory() && f.getName().startsWith("values"));
        if (dirs == null) return;
        for (File dir : dirs) {
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (!f.getName().endsWith(".xml")) continue;
                try {
                    Matcher m = VALUE_RES.matcher(readFile(f));
                    while (m.find()) values.put(m.group(1) + "/" + m.group(2), m.group(3));
                } catch (IOException ignored) { }
            }
        }
    }

    private String resolveValues(String xml) {
        Matcher m = VALUE_REF.matcher(xml);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String v = values.get(m.group(1) + "/" + m.group(2));
            m.appendReplacement(out, Matcher.quoteReplacement(v != null ? v : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }

    // ---------------------------------------------------------------- build view

    private View buildView(XmlPullParser p, Map<String, String> layoutOut) throws Exception {
        String tag = p.getName();
        Map<String, String> attrs = readAttrs(p);
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            if (e.getKey().startsWith("layout_")) layoutOut.put(e.getKey(), e.getValue());
        }

        boolean include = "include".equals(tag);
        View v = include ? inflate(refName(attrs.get("layout"))) : createView(tag);

        for (Map.Entry<String, String> e : attrs.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("layout_") || k.equals("layout")) continue;
            try {
                applyView(v, k, e.getValue());
            } catch (Exception ex) {
                Log.w(TAG, "atribut " + k + " gagal", ex);
            }
        }

        if (v instanceof ViewGroup && !include) {
            ViewGroup g = (ViewGroup) v;
            int depth = p.getDepth();
            int ev = p.next();
            while (ev != XmlPullParser.END_DOCUMENT
                    && !(ev == XmlPullParser.END_TAG && p.getDepth() == depth)) {
                if (ev == XmlPullParser.START_TAG) {
                    Map<String, String> childLayout = new HashMap<>();
                    View child = buildView(p, childLayout);
                    g.addView(child, makeParams(g, childLayout));
                }
                ev = p.next();
            }
        } else {
            skipChildren(p);
        }
        return v;
    }

    private View createView(String tag) throws Exception {
        for (String pkg : WIDGET_PACKAGES) {
            try {
                Class<?> c = Class.forName(pkg + tag);
                if (View.class.isAssignableFrom(c)) {
                    Constructor<?> k = c.getConstructor(Context.class);
                    return (View) k.newInstance(ctx);
                }
            } catch (ClassNotFoundException | NoSuchMethodException ignored) { }
        }
        throw new Exception("Widget tidak dikenal: <" + tag + ">");
    }

    private static Map<String, String> readAttrs(XmlPullParser p) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < p.getAttributeCount(); i++) {
            String ns = p.getAttributeNamespace(i);
            if (ns == null || ns.isEmpty() || ns.equals(ANDROID_NS)) {
                m.put(p.getAttributeName(i), p.getAttributeValue(i));
            }
        }
        return m;
    }

    private static void skipChildren(XmlPullParser p) throws Exception {
        int depth = p.getDepth();
        int ev;
        do {
            ev = p.next();
        } while (ev != XmlPullParser.END_DOCUMENT && !(ev == XmlPullParser.END_TAG && p.getDepth() == depth));
    }

    private static String refName(String ref) {
        if (ref == null || ref.indexOf('/') < 0) {
            throw new IllegalArgumentException("<include> butuh layout=\"@layout/nama\"");
        }
        return ref.substring(ref.indexOf('/') + 1);
    }

    // ---------------------------------------------------------------- attributes

    private void applyView(View v, String name, String val) {
        switch (name) {
            case "id":
                v.setId(idFor(val));
                return;
            case "tag":
                v.setTag(val);
                return;
            case "background":
                if (val.startsWith("@drawable/")) v.setBackground(drawableFor(val));
                else if (val.startsWith("#")) v.setBackgroundColor(color(val));
                return;
            case "padding": {
                int p = px(val);
                v.setPadding(p, p, p, p);
                return;
            }
            case "paddingLeft":
            case "paddingStart":
                v.setPadding(px(val), v.getPaddingTop(), v.getPaddingRight(), v.getPaddingBottom());
                return;
            case "paddingTop":
                v.setPadding(v.getPaddingLeft(), px(val), v.getPaddingRight(), v.getPaddingBottom());
                return;
            case "paddingRight":
            case "paddingEnd":
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), px(val), v.getPaddingBottom());
                return;
            case "paddingBottom":
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), px(val));
                return;
            case "visibility":
                v.setVisibility(val.equals("gone") ? View.GONE
                        : val.equals("invisible") ? View.INVISIBLE : View.VISIBLE);
                return;
            case "minHeight":
                v.setMinimumHeight(px(val));
                return;
            case "minWidth":
                v.setMinimumWidth(px(val));
                return;
            case "text":
                if (v instanceof TextView) ((TextView) v).setText(val);
                return;
            case "hint":
                if (v instanceof TextView) ((TextView) v).setHint(val);
                return;
            case "textColor":
                if (v instanceof TextView) ((TextView) v).setTextColor(color(val));
                return;
            case "textSize":
                if (v instanceof TextView) ((TextView) v).setTextSize(TypedValue.COMPLEX_UNIT_PX, toPx(val));
                return;
            case "textStyle":
                if (v instanceof TextView) {
                    TextView t = (TextView) v;
                    int st = Typeface.NORMAL;
                    if (val.contains("bold")) st |= Typeface.BOLD;
                    if (val.contains("italic")) st |= Typeface.ITALIC;
                    t.setTypeface(t.getTypeface(), st);
                }
                return;
            case "fontFamily":
            case "typeface":
                if (v instanceof TextView && val.contains("monospace")) ((TextView) v).setTypeface(Typeface.MONOSPACE);
                return;
            case "src":
                if (v instanceof ImageView) ((ImageView) v).setImageDrawable(drawableFor(val));
                return;
            case "scaleType":
                if (v instanceof ImageView) ((ImageView) v).setScaleType(scaleType(val));
                return;
            case "orientation":
                if (v instanceof LinearLayout) {
                    ((LinearLayout) v).setOrientation(val.equals("vertical") ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
                }
                return;
            default:
                if (!setGeneric(v, name, val)) Log.w(TAG, "atribut tidak didukung: " + name);
        }
    }

    /** Fallback: panggil setter publik "set<Nama>" yang cocok dengan tipe parameternya. */
    private boolean setGeneric(View v, String name, String val) {
        String setter = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (Method m : v.getClass().getMethods()) {
            if (!m.getName().equals(setter) || m.getParameterTypes().length != 1) continue;
            Object arg = convert(m.getParameterTypes()[0], name, val);
            if (arg == null) continue;
            try {
                m.invoke(v, arg);
                return true;
            } catch (Exception e) {
                Log.w(TAG, name + " gagal diterapkan", e);
                return false;
            }
        }
        return false;
    }

    private Object convert(Class<?> t, String name, String val) {
        if (t == String.class || t == CharSequence.class || t == Object.class) return val;
        if (t == boolean.class || t == Boolean.class) return Boolean.valueOf(val);
        if (t == int.class || t == Integer.class) {
            String lower = name.toLowerCase();
            if (lower.contains("color")) return color(val);
            if (lower.contains("gravity")) return parseGravity(val);
            if (val.matches("-?\\d+")) return Integer.valueOf(val);
            return px(val);
        }
        if (t == float.class || t == Float.class) {
            if (val.matches("-?\\d*\\.?\\d+")) return Float.valueOf(val);
            return toPx(val);
        }
        if (t == Drawable.class) return drawableFor(val);
        return null;
    }

    private static ImageView.ScaleType scaleType(String v) {
        switch (v) {
            case "center": return ImageView.ScaleType.CENTER;
            case "centerCrop": return ImageView.ScaleType.CENTER_CROP;
            case "centerInside": return ImageView.ScaleType.CENTER_INSIDE;
            case "fitXY": return ImageView.ScaleType.FIT_XY;
            case "fitStart": return ImageView.ScaleType.FIT_START;
            case "fitEnd": return ImageView.ScaleType.FIT_END;
            case "matrix": return ImageView.ScaleType.MATRIX;
            default: return ImageView.ScaleType.FIT_CENTER;
        }
    }

    // ---------------------------------------------------------------- layout params

    private ViewGroup.LayoutParams makeParams(ViewGroup parent, Map<String, String> la) {
        int w = la.containsKey("layout_width") ? dimOrMatch(la.get("layout_width")) : WRAP;
        int h = la.containsKey("layout_height") ? dimOrMatch(la.get("layout_height")) : WRAP;
        int[] m = margins(la);

        if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
            if (la.containsKey("layout_weight")) p.weight = Float.parseFloat(la.get("layout_weight"));
            if (la.containsKey("layout_gravity")) p.gravity = parseGravity(la.get("layout_gravity"));
            p.setMargins(m[0], m[1], m[2], m[3]);
            return p;
        }
        if (parent instanceof FrameLayout) {
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h);
            if (la.containsKey("layout_gravity")) p.gravity = parseGravity(la.get("layout_gravity"));
            p.setMargins(m[0], m[1], m[2], m[3]);
            return p;
        }
        if (parent instanceof RelativeLayout) {
            RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(w, h);
            rule(p, la, "layout_alignParentTop", RelativeLayout.ALIGN_PARENT_TOP);
            rule(p, la, "layout_alignParentBottom", RelativeLayout.ALIGN_PARENT_BOTTOM);
            rule(p, la, "layout_alignParentLeft", RelativeLayout.ALIGN_PARENT_LEFT);
            rule(p, la, "layout_alignParentRight", RelativeLayout.ALIGN_PARENT_RIGHT);
            rule(p, la, "layout_alignParentStart", RelativeLayout.ALIGN_PARENT_START);
            rule(p, la, "layout_alignParentEnd", RelativeLayout.ALIGN_PARENT_END);
            rule(p, la, "layout_centerInParent", RelativeLayout.CENTER_IN_PARENT);
            rule(p, la, "layout_centerHorizontal", RelativeLayout.CENTER_HORIZONTAL);
            rule(p, la, "layout_centerVertical", RelativeLayout.CENTER_VERTICAL);
            rule(p, la, "layout_above", RelativeLayout.ABOVE);
            rule(p, la, "layout_below", RelativeLayout.BELOW);
            rule(p, la, "layout_toLeftOf", RelativeLayout.LEFT_OF);
            rule(p, la, "layout_toRightOf", RelativeLayout.RIGHT_OF);
            rule(p, la, "layout_alignTop", RelativeLayout.ALIGN_TOP);
            rule(p, la, "layout_alignBottom", RelativeLayout.ALIGN_BOTTOM);
            rule(p, la, "layout_alignLeft", RelativeLayout.ALIGN_LEFT);
            rule(p, la, "layout_alignRight", RelativeLayout.ALIGN_RIGHT);
            p.setMargins(m[0], m[1], m[2], m[3]);
            return p;
        }
        ViewGroup.MarginLayoutParams p = new ViewGroup.MarginLayoutParams(w, h);
        p.setMargins(m[0], m[1], m[2], m[3]);
        return p;
    }

    private void rule(RelativeLayout.LayoutParams p, Map<String, String> la, String key, int verb) {
        String v = la.get(key);
        if (v == null) return;
        if (v.equals("true")) p.addRule(verb);
        else if (v.startsWith("@")) p.addRule(verb, idFor(v));
    }

    private int[] margins(Map<String, String> la) {
        int l = la.containsKey("layout_margin") ? px(la.get("layout_margin")) : 0;
        int t = l, r = l, b = l;
        if (la.containsKey("layout_marginLeft")) l = px(la.get("layout_marginLeft"));
        if (la.containsKey("layout_marginStart")) l = px(la.get("layout_marginStart"));
        if (la.containsKey("layout_marginTop")) t = px(la.get("layout_marginTop"));
        if (la.containsKey("layout_marginRight")) r = px(la.get("layout_marginRight"));
        if (la.containsKey("layout_marginEnd")) r = px(la.get("layout_marginEnd"));
        if (la.containsKey("layout_marginBottom")) b = px(la.get("layout_marginBottom"));
        return new int[]{l, t, r, b};
    }

    private int dimOrMatch(String s) {
        if ("match_parent".equals(s) || "fill_parent".equals(s)) return MATCH;
        if ("wrap_content".equals(s)) return WRAP;
        return px(s);
    }

    // ---------------------------------------------------------------- resources

    private int idFor(String ref) {
        String name = ref.substring(ref.indexOf('/') + 1);
        Integer id = ids.get(name);
        if (id == null) {
            id = View.generateViewId();
            ids.put(name, id);
        }
        return id;
    }

    private Drawable drawableFor(String val) {
        if (val == null) return null;
        if (val.startsWith("@drawable/")) return getDrawable(val.substring("@drawable/".length()));
        if (val.startsWith("#")) return new ColorDrawable(color(val));
        return null;
    }

    private int color(String v) {
        if (v == null) return 0;
        v = v.trim();
        if (v.startsWith("#")) {
            try {
                return Color.parseColor(v);
            } catch (IllegalArgumentException e) {
                return 0;
            }
        }
        if (v.equals("@android:color/white")) return Color.WHITE;
        if (v.equals("@android:color/black")) return Color.BLACK;
        if (v.equals("@android:color/transparent")) return Color.TRANSPARENT;
        return 0;
    }

    /** "16dp", "14sp", "12px", atau angka saja (dianggap dp). */
    private float toPx(String v) {
        if (v == null) return 0f;
        Matcher m = NUM_UNIT.matcher(v.trim());
        if (!m.matches()) return 0f;
        float n = Float.parseFloat(m.group(1));
        switch (m.group(2)) {
            case "sp": return n * scaledDensity;
            case "px": return n;
            case "in": return n * 160f * density;
            case "mm": return n * 160f / 25.4f * density;
            case "pt": return n * 160f / 72f * density;
            default:   return n * density;
        }
    }

    private int px(String v) {
        return Math.round(toPx(v));
    }

    private static int parseGravity(String v) {
        int g = 0;
        if (v == null) return g;
        for (String part : v.split("\\|")) {
            switch (part.trim()) {
                case "center": g |= Gravity.CENTER; break;
                case "center_horizontal": g |= Gravity.CENTER_HORIZONTAL; break;
                case "center_vertical": g |= Gravity.CENTER_VERTICAL; break;
                case "left": g |= Gravity.LEFT; break;
                case "right": g |= Gravity.RIGHT; break;
                case "top": g |= Gravity.TOP; break;
                case "bottom": g |= Gravity.BOTTOM; break;
                case "start": g |= Gravity.START; break;
                case "end": g |= Gravity.END; break;
                case "fill": g |= Gravity.FILL; break;
                case "fill_horizontal": g |= Gravity.FILL_HORIZONTAL; break;
                case "fill_vertical": g |= Gravity.FILL_VERTICAL; break;
                default: break;
            }
        }
        return g;
    }

    // ---------------------------------------------------------------- drawable files

    private File findDrawableFile(String name) {
        File[] dirs = new File(root, "res").listFiles(f -> f.isDirectory() && f.getName().startsWith("drawable"));
        if (dirs == null) return null;
        String[] ext = {"png", "webp", "jpg", "jpeg", "gif", "xml"};
        for (File dir : dirs) {
            for (String e : ext) {
                File f = new File(dir, name + "." + e);
                if (f.exists()) return f;
            }
        }
        return null;
    }

    private Drawable loadDrawableFile(File f) throws Exception {
        if (f.getName().endsWith(".xml")) return loadXmlDrawable(readFile(f));
        Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
        return bmp == null ? null : new BitmapDrawable(ctx.getResources(), bmp);
    }

    /** Drawable XML: mendukung <shape> (solid, corners, stroke). selector/layer-list belum. */
    private Drawable loadXmlDrawable(String xml) throws Exception {
        XmlPullParser p = newParser(resolveValues(xml));
        int ev = p.next();
        while (ev != XmlPullParser.START_TAG) ev = p.next();
        if (!"shape".equals(p.getName())) return null;

        GradientDrawable g = new GradientDrawable();
        if ("oval".equals(attr(p, "shape"))) g.setShape(GradientDrawable.OVAL);

        ev = p.next();
        while (ev != XmlPullParser.END_DOCUMENT && !(ev == XmlPullParser.END_TAG && p.getDepth() == 1)) {
            if (ev == XmlPullParser.START_TAG) {
                String tag = p.getName();
                if ("solid".equals(tag)) {
                    g.setColor(color(attr(p, "color")));
                } else if ("corners".equals(tag) && attr(p, "radius") != null) {
                    g.setCornerRadius(toPx(attr(p, "radius")));
                } else if ("stroke".equals(tag)) {
                    g.setStroke(px(attr(p, "width")), color(attr(p, "color")));
                }
            }
            ev = p.next();
        }
        return g;
    }

    // ---------------------------------------------------------------- file helpers

    private static String attr(XmlPullParser p, String name) {
        return p.getAttributeValue(ANDROID_NS, name);
    }

    private XmlPullParser newParser(String xml) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        p.setInput(new StringReader(xml));
        return p;
    }

    private static String readFile(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        try {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        } finally {
            r.close();
        }
        return sb.toString();
    }
}
