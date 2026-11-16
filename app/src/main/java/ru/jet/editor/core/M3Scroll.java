package ru.jet.editor.core;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import java.lang.reflect.Method;

/**
 * Scrollbar bergaya M3 untuk View bawaan (ListView, ScrollView, ...). Thumb diwarnai dari palet
 * aplikasi (aksen), bukan dari colors.xml, sehingga ikut tema terang/gelap dan App color.
 *
 * Setter thumb per-View tidak selalu tersedia di SDK publik, jadi dipanggil lewat refleksi.
 * Bila tidak tersedia, scrollbar memakai thumb dari tema (@drawable/m3_scroll_thumb di styles.xml),
 * yang juga sudah berwarna M3.
 */
public final class M3Scroll {

    /** Transparansi thumb (0..255). Cukup terlihat di kedua tema tanpa mendominasi isi. */
    private static final int THUMB_ALPHA = 0xB3;

    private M3Scroll() { }

    /** Pasang thumb bergaya M3 pada View yang bisa digulir. Aman dipanggil berulang. */
    public static void style(View v) {
        if (v == null) return;
        GradientDrawable thumb = new GradientDrawable();
        thumb.setColor(Palette.withAlpha(Palette.ACCENT, THUMB_ALPHA));
        thumb.setCornerRadius(999f);   // radius dijepit ke setengah lebar: thumb berbentuk pil
        setThumb(v, "setVerticalScrollbarThumbDrawable", thumb);
        setThumb(v, "setHorizontalScrollbarThumbDrawable", thumb);
    }

    private static void setThumb(View v, String setter, Drawable d) {
        try {
            Method m = View.class.getMethod(setter, Drawable.class);
            m.invoke(v, d);
        } catch (Exception e) {
            // Tidak tersedia di perangkat ini: tetap memakai thumb dari tema.
        }
    }
}
