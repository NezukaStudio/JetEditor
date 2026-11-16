package ru.jet.editor.core;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Dialog bergaya Material 3 Expressive tanpa AndroidX:
 * sudut sangat membulat, ikon opsional di atas judul (24dp), judul 24sp, teks pendukung 14sp,
 * tombol pill bertonal, dan tombol aksi berbahaya memakai peran error M3.
 * Isi (view) bisa berupa daftar atau InoInputLayout. Font aplikasi ikut dipasang ke dialog.
 *
 * Pakai:
 *   new M3Dialog(ctx).icon(R.drawable.ic_folder).title("Hapus?").message("...")
 *       .negative("Batal", null)
 *       .positive("Hapus", new M3Dialog.Action() { public void run() { ... } }, true)
 *       .show();
 */
public class M3Dialog {

    public interface Action { void run(); }
    private final Context ctx;
    private final float density;
    private String title, message, posText, negText;
    private int iconRes;
    private View content;
    private Action posAction, negAction;
    private boolean danger;

    public M3Dialog(Context ctx) {
        this.ctx = ctx;
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    /** Ikon kecil di atas judul, mis. R.drawable.ic_folder. 0 = tanpa ikon. */
    public M3Dialog icon(int res) { this.iconRes = res; return this; }
    public M3Dialog title(String t) { this.title = t; return this; }
    public M3Dialog message(String m) { this.message = m; return this; }
    public M3Dialog view(View v) { this.content = v; return this; }

    public M3Dialog negative(String text, Action a) {
        this.negText = text;
        this.negAction = a;
        return this;
    }

    public M3Dialog positive(String text, Action a) {
        return positive(text, a, false);
    }

    /** danger = true memberi tombol peran error (merah), untuk aksi yang menghapus. */
    public M3Dialog positive(String text, Action a, boolean danger) {
        this.posText = text;
        this.posAction = a;
        this.danger = danger;
        return this;
    }

    public Dialog show() {
        final Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(buildRoot(d));
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(dialogWidth(), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
        if (w != null && Build.VERSION.SDK_INT >= 21) {
            // Jendela dialog digambar sampai tepi layar. Bar sistem dibuat transparan dan scrim
            // sistemnya dimatikan, supaya status bar dan nav bar memakai dim yang sama dengan
            // bagian lain layar, bukan warna scrim sistem yang berbeda-beda.
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 29) {
                w.setStatusBarContrastEnforced(false);
                w.setNavigationBarContrastEnforced(false);
            }
        }
        Popups.flat(w);   // tanpa bayangan: hanya kartu tonal dari palet
        // Dialog punya window sendiri, jadi font aplikasi harus dipasang ke sini juga.
        if (w != null) FontManager.attach(w.getDecorView());
        return d;
    }

    private int dialogWidth() {
        int screen = ctx.getResources().getDisplayMetrics().widthPixels;
        return Math.min(screen - dp(64), dp(320));
    }

    private LinearLayout buildRoot(final Dialog d) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Palette.SHEET);
        bg.setCornerRadius(dp(32));
        root.setBackground(bg);

        if (iconRes != 0) {
            ImageView icon = new ImageView(ctx);
            icon.setImageDrawable(IconDrawable.of(ctx, iconRes, Palette.ACCENT, dp(24)));
            root.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }
        if (!TextUtils.isEmpty(title)) {
            TextView t = new TextView(ctx);
            t.setText(title);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            t.setTypeface(null, Typeface.BOLD);
            t.setTextColor(Palette.TEXT);
            root.addView(t, matchWrap(iconRes != 0 ? dp(16) : 0));
        }
        if (!TextUtils.isEmpty(message)) {
            TextView m = new TextView(ctx);
            m.setText(message);
            m.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            m.setLineSpacing(0, 1.3f);
            m.setTextColor(Palette.SUB);
            root.addView(m, matchWrap(dp(8)));
        }
        if (content != null) {
            ViewGroup parent = (ViewGroup) content.getParent();
            if (parent != null) parent.removeView(content);
            root.addView(content, matchWrap(dp(24)));
        }

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (negText != null) row.addView(button(negText, false, negAction, d), buttonParams());
        if (posText != null) row.addView(button(posText, true, posAction, d), buttonParams());
        root.addView(row, matchWrap(dp(24)));
        return root;
    }

    private TextView button(String text, boolean primary, final Action action, final Dialog d) {
        TextView b = new TextView(ctx);
        b.setText(text);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(20), 0, dp(20), 0);

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(20));
        boolean red = primary && danger;
        if (primary) {
            shape.setColor(red ? Palette.ERROR : Palette.ACCENT);
            b.setTextColor(red ? Palette.ON_ERROR : Palette.ON_ACCENT);
        } else {
            shape.setColor(Color.TRANSPARENT);
            b.setTextColor(Palette.ACCENT);
        }
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Palette.RIPPLE), shape, null));

        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (action != null) action.run();
                d.dismiss();
            }
        });
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        lp.leftMargin = dp(8);
        return lp;
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
