package ru.jet.editor.core;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Pemilih warna aksen dalam dialog Material 3: roda hue (InoHuePicker) untuk warna dan saturasi,
 * bar nilai (InoSaturationValuePicker) untuk kecerahan, kolom kode hex, dan tombol Batal/Terapkan.
 * Dialog tampil di tengah dengan latar redup. Ketuk di luar kartu menutupnya tanpa menyimpan.
 * Tata letak dan ukuran sama dengan pemilih warna di Music.
 */
public final class ColorPicker {

    public interface Callback {
        /** Dipanggil hanya saat Terapkan ditekan. */
        void onApply(int argb);
    }

    private static final int CARD_MAX_DP = 360;
    private static final int SIDE_DP = 24;

    private ColorPicker() { }

    public static void show(final Activity act, int initial, final Callback cb) {
        final Context c = act;
        final float[] hsv = new float[3];
        Color.colorToHSV(initial, hsv);

        final Dialog d = new Dialog(act, android.R.style.Theme_Translucent_NoTitleBar);
        final Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // Layar penuh di belakang kartu: latar redup menutupi status bar dan navigasi juga.
        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        w.setDimAmount(0.32f);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        w.setWindowAnimations(android.R.style.Animation_Dialog);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        Palette.applyBarIcons(w);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(c, SIDE_DP), dp(c, SIDE_DP), dp(c, SIDE_DP), dp(c, SIDE_DP - 4));

        col.addView(text(c, LanguageLoader.get("color.title"), 24, Palette.TEXT, true), lp(-2, -2, 0));
        col.addView(text(c, LanguageLoader.get("color.desc"), 14, Palette.SUB, false),
                lp(-2, -2, dp(c, 4)));

        // Pratinjau: lingkaran warna, dan kolom kode hex yang bisa diketik manual.
        final boolean[] syncing = {false};   // true saat teks diubah oleh picker, bukan oleh pengguna
        LinearLayout preview = new LinearLayout(c);
        preview.setOrientation(LinearLayout.HORIZONTAL);
        preview.setGravity(Gravity.CENTER_VERTICAL);
        final View swatch = new View(c);
        final GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        swatch.setBackground(dot);
        preview.addView(swatch, new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));

        final InoInputLayout hexInput = new InoInputLayout(c);
        hexInput.setLabelText(LanguageLoader.get("color.hex_label"));
        hexInput.setColors(Palette.ACCENT, Palette.SUB);
        hexInput.setTextColor(Palette.TEXT);
        hexInput.setTextSize(16);
        hexInput.setCornerRadius(16);
        hexInput.setIsMultiline(false);
        hexInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, -2, 1f);
        hl.leftMargin = dp(c, 16);
        preview.addView(hexInput, hl);
        col.addView(preview, lp(-1, -2, dp(c, 20)));

        final Runnable refresh = () -> {
            int argb = Color.HSVToColor(hsv);
            dot.setColor(argb);
            syncing[0] = true;
            hexInput.setText(hex(argb));
            syncing[0] = false;
        };

        final InoSaturationValuePicker value = new InoSaturationValuePicker(c);
        final InoHuePicker hue = new InoHuePicker(c);

        hue.setOnColorChangedListener((h, s) -> {
            hsv[0] = h;
            hsv[1] = s;
            value.updateHueAndSaturation(h, s);   // bar nilai ikut warna yang dipilih
            refresh.run();
        });
        value.setOnValueModifierListener(v -> {
            hsv[2] = v;
            refresh.run();
        });

        // Kode hex yang valid langsung memindahkan roda dan bar nilai.
        hexInput.getEditText().addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (syncing[0]) return;
                Integer parsed = parseHex(s.toString());
                if (parsed == null) return;
                Color.colorToHSV(parsed, hsv);
                dot.setColor(parsed);
                hue.setHueAndSaturation(hsv[0], hsv[1]);
                value.updateHueAndSaturation(hsv[0], hsv[1]);
                value.setValue(hsv[2]);
            }
        });

        // Posisi awal dari warna yang sekarang dipakai.
        hue.setHueAndSaturation(hsv[0], hsv[1]);
        value.updateHueAndSaturation(hsv[0], hsv[1]);
        value.setValue(hsv[2]);
        refresh.run();

        col.addView(hue, lp(-1, dp(c, 240), dp(c, 20)));
        col.addView(value, lp(-1, dp(c, 56), dp(c, 12)));

        // Tombol: Batal (tanpa menyimpan) dan Terapkan.
        TextView cancel = button(c, LanguageLoader.get("common.cancel"), Palette.ACCENT, 0);
        cancel.setOnClickListener(v -> d.dismiss());

        TextView apply = button(c, LanguageLoader.get("color.apply"), Palette.ON_ACCENT, Palette.ACCENT);
        apply.setOnClickListener(v -> {
            if (cb != null) cb.onApply(Color.HSVToColor(hsv));
            d.dismiss();
        });

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(cancel, new LinearLayout.LayoutParams(-2, dp(c, 48)));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, dp(c, 48));
        al.leftMargin = dp(c, 8);
        row.addView(apply, al);
        col.addView(row, lp(-1, -2, dp(c, 24)));

        // Kartu: ScrollView bergaya M3 (sudut besar). Tinggi dibatasi ke ruang yang tersisa,
        // jadi saat keyboard muncul (adjustResize) tombol Terapkan tetap bisa dijangkau.
        final ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        GradientDrawable card = new GradientDrawable();
        card.setColor(Palette.SHEET);
        card.setCornerRadius(dp(c, 28));
        scroll.setBackground(card);
        scroll.setClipToOutline(true);
        scroll.setClickable(true);   // sentuhan di dalam kartu tidak menutup dialog
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));

        final int cardW = Math.min(dp(c, CARD_MAX_DP), act.getResources().getDisplayMetrics().widthPixels - dp(c, 48));
        final FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(cardW,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);

        final FrameLayout frame = new FrameLayout(c);
        frame.setOnClickListener(v -> d.dismiss());   // ketuk latar redup = batal
        frame.addView(scroll, sl);

        final int[] ins = {0, 0};   // inset atas dan bawah (status bar, navigasi)
        final Runnable fit = () -> {
            int natural = col.getHeight();
            int max = frame.getHeight() - ins[0] - ins[1] - dp(c, 48);
            int h = natural <= 0 ? FrameLayout.LayoutParams.WRAP_CONTENT
                    : Math.min(natural, Math.max(max, dp(c, 200)));
            if (sl.height != h) {
                sl.height = h;
                scroll.setLayoutParams(sl);
            }
        };
        frame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fit.run());
        frame.setOnApplyWindowInsetsListener((v, wi) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = wi.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                ins[0] = i.top;
                ins[1] = i.bottom;
            } else {
                ins[0] = wi.getSystemWindowInsetTop();
                ins[1] = wi.getSystemWindowInsetBottom();
            }
            fit.run();
            return wi;
        });

        d.setContentView(frame);
        FontManager.attach(frame);   // dialog punya window sendiri: pasang font aplikasi
        d.setCancelable(true);
        d.show();
    }

    /** #RRGGBB atau RRGGBB (huruf besar/kecil). Null bila tidak valid. */
    static Integer parseHex(String s) {
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (!t.matches("[0-9a-fA-F]{6}")) return null;
        return 0xFF000000 | Integer.parseInt(t, 16);
    }

    public static String hex(int argb) {
        return String.format("#%06X", 0xFFFFFF & argb);
    }

    /** Tombol pil: fill 0 = tanpa latar (Batal), selain itu terisi (Terapkan). */
    private static TextView button(Context c, String label, int textColor, int fill) {
        TextView t = text(c, label, 15, textColor, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 24), 0, dp(c, 24), 0);
        GradientDrawable bg = null;
        if (fill != 0) {
            bg = new GradientDrawable();
            bg.setColor(fill);
            bg.setCornerRadius(dp(c, 24));
        }
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(Palette.RIPPLE), bg, null));
        t.setClickable(true);
        return t;
    }

    private static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private static LinearLayout.LayoutParams lp(int w, int h, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = top;
        return p;
    }

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
