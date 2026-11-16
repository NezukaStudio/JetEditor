package ru.jet.editor.core;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Toast bawaan Android dengan tampilan bergaya M3 (sama seperti M3.toast di Pinpic):
 * kartu inverse surface, sudut membulat, bayangan, dan teks inverse on surface.
 * Warna diambil dari {@link Palette} sehingga ikut tema gelap/terang yang aktif.
 * Aman dipanggil dari thread mana pun; pemanggilan di luar main thread dipindah ke main thread.
 */
public final class M3 {

    /** Jarak toast dari bawah layar (dp). Toast sistem sudah otomatis naik di atas navigation bar. */
    private static final int TOAST_BOTTOM_DP = 100;
    private static final int RADIUS_DP = 16;
    private static final int PAD_H_DP = 20;
    private static final int PAD_V_DP = 14;
    private static final int ELEVATION_DP = 10;

    private M3() { }

    public static void toast(Context c, CharSequence msg) {
        toast(c, msg, Toast.LENGTH_SHORT);
    }

    public static void toast(final Context c, final CharSequence msg, final int duration) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() { toast(c, msg, duration); }
            });
            return;
        }
        Context app = c.getApplicationContext();
        Palette.load(app);
        float d = app.getResources().getDisplayMetrics().density;

        TextView t = new TextView(app);
        t.setText(msg);
        t.setTextSize(14);
        t.setTextColor(Palette.BG);
        t.setTypeface(Typeface.DEFAULT);
        int ph = px(d, PAD_H_DP), pv = px(d, PAD_V_DP);
        t.setPadding(ph, pv, ph, pv);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Palette.TEXT);
        bg.setCornerRadius(RADIUS_DP * d);
        t.setBackground(bg);
        t.setElevation(ELEVATION_DP * d);

        Toast toast = new Toast(app);
        toast.setView(t);
        toast.setDuration(duration);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, px(d, TOAST_BOTTOM_DP));
        toast.show();
    }

    private static int px(float density, int dp) {
        return Math.round(dp * density);
    }
}
