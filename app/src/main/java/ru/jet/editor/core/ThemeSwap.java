package ru.jet.editor.core;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Ganti tema/warna TANPA recreate: isi layar dibangun ulang di tempat, lalu gambar lama
 * (snapshot) dipudarkan di atasnya. Activity tetap hidup, jadi tidak ada jeda pembuatan ulang.
 *
 *   Bitmap snap = ThemeSwap.snapshot(this);   // sebelum isi dibangun ulang
 *   buildUi();                                 // setContentView baru di activity yang sama
 *   ThemeSwap.fadeOut(this, snap);
 */
public final class ThemeSwap {

    private static final long FADE_MS = 240L;

    private ThemeSwap() { }

    /** Gambar layar yang sedang tampil. null bila tidak bisa (layar belum diukur / memori kurang). */
    public static Bitmap snapshot(Activity a) {
        View root = a.getWindow().getDecorView();
        int w = root.getWidth(), h = root.getHeight();
        if (w <= 0 || h <= 0) return null;
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(b));
            return b;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /** Pudarkan snapshot di atas isi baru. Tanpa snapshot, perubahan langsung terlihat. */
    public static void fadeOut(Activity a, final Bitmap old) {
        if (old == null) return;
        final ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        final ImageView overlay = new ImageView(a);
        overlay.setImageBitmap(old);
        overlay.setScaleType(ImageView.ScaleType.FIT_XY);
        decor.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.animate()
                .alpha(0f)
                .setDuration(FADE_MS)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .withEndAction(new Runnable() {
                    public void run() {
                        decor.removeView(overlay);
                        old.recycle();
                    }
                })
                .start();
    }
}
