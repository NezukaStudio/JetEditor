package ru.jet.editor.core;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Pergantian tema dengan crossfade. Alur:
 *  1. refreshOthers(): layar lain yang masih hidup di belakang dibangun ulang dulu (tanpa animasi),
 *     jadi saat pengguna kembali sudah memakai tema baru tanpa jeda.
 *  2. capture(): sebelum recreate(), gambar layar yang sekarang tampil (masih palet lama).
 *  3. recreate() membangun layar baru dengan palet baru.
 *  4. play(): di onCreate layar baru, gambar lama diletakkan di atas lalu dipudarkan.
 * Bitmap hanya dipakai oleh activity yang mengambilnya (lihat pendingOwner), sehingga layar
 * lain yang ikut dibangun ulang tidak ikut menampilkan gambar yang salah.
 */
public final class ThemeTransition {

    private static final long DURATION_MS = 360L;
    private static Bitmap pending;
    private static Class<?> pendingOwner;
    private static final ArrayList<WeakReference<Activity>> live = new ArrayList<>();

    private ThemeTransition() { }

    /** Catat activity yang perlu ikut diperbarui saat tema berganti (dipanggil di onCreate). */
    public static void track(Activity a) {
        Iterator<WeakReference<Activity>> it = live.iterator();
        while (it.hasNext()) {
            Activity x = it.next().get();
            if (x == null || x.isDestroyed()) it.remove();
            else if (x == a) return;
        }
        live.add(new WeakReference<>(a));
    }

    /**
     * Bangun ulang layar lain yang masih hidup (mis. MainActivity saat SettingActivity sedang tampil),
     * tanpa crossfade. Dipanggil sebelum capture() supaya bitmap tidak tertukar.
     */
    public static void refreshOthers(Activity except) {
        for (WeakReference<Activity> ref : new ArrayList<>(live)) {
            Activity x = ref.get();
            if (x == null || x == except || x.isFinishing() || x.isDestroyed()) continue;
            x.recreate();
        }
    }

    public static void capture(Activity a) {
        pending = null;
        pendingOwner = null;
        View root = a.getWindow().getDecorView();
        int w = root.getWidth(), h = root.getHeight();
        if (w <= 0 || h <= 0) return;
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(b));
            pending = b;
            pendingOwner = a.getClass();
        } catch (OutOfMemoryError e) {
            pending = null;   // tanpa animasi pun tidak apa-apa
        }
    }

    public static void play(Activity a) {
        if (pending == null || pendingOwner != a.getClass()) return;
        final Bitmap b = pending;
        pending = null;
        pendingOwner = null;

        final ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        final ImageView overlay = new ImageView(a);
        overlay.setImageBitmap(b);
        overlay.setScaleType(ImageView.ScaleType.FIT_XY);
        decor.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.animate()
                .alpha(0f)
                .setDuration(DURATION_MS)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .withEndAction(new Runnable() {
                    public void run() {
                        decor.removeView(overlay);
                        b.recycle();
                    }
                })
                .start();
    }
}
