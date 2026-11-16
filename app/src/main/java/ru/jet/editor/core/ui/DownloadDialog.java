package ru.jet.editor.core.ui;

import ru.jet.editor.core.LanguageLoader;

import ru.jet.editor.core.Palette;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import ru.jet.editor.core.FontManager;
import ru.jet.editor.core.InoProgressBar;
import ru.jet.editor.core.ToolDownloadService;

/**
 * Dialog download bergaya Material 3 (surface tonal, sudut besar, tombol pill).
 * Judul di kiri, progress bar (InoProgressBar), lalu baris bawah: teks progres,
 * tombol Minimize, dan Cancel. Minimize / back hanya menutup dialog; download tetap jalan
 * dan tampil di notifikasi.
 */
public class DownloadDialog {

    public interface Callbacks {
        void onMinimize();
        void onCancel();
    }
    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    private final Activity act;
    private final Dialog dialog;
    private final Callbacks cb;
    private final InoProgressBar bar;
    private final TextView progressText;

    public DownloadDialog(Activity act, String name, final Callbacks cb) {
        this.act = act;
        this.cb = cb;

        dialog = new Dialog(act);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface d) { cb.onMinimize(); }
        });

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(16));
        root.setBackground(round(Palette.SHEET, 28));

        TextView title = text(LanguageLoader.get("dl.dialog_title"), 22, Palette.TEXT, false);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(WRAP, WRAP));

        TextView sub = text(name, 14, Palette.SUB, false);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(WRAP, WRAP);
        sl.topMargin = dp(4);
        root.addView(sub, sl);

        bar = new InoProgressBar(act);
        bar.setTrackActiveColor(Palette.ACCENT);
        bar.setTrackInactiveColor(Palette.withAlpha(Palette.ACCENT, 0x33));
        bar.setMax(100);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(MATCH, WRAP);
        bl.topMargin = dp(24);
        root.addView(bar, bl);

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        progressText = text(LanguageLoader.get("dl.connecting"), 14, Palette.SUB, false);
        row.addView(progressText, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView minimize = pill(LanguageLoader.get("dl.minimize"), Palette.POPUP, Palette.TEXT);
        minimize.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
                cb.onMinimize();
            }
        });
        row.addView(minimize, pillLp());

        TextView cancel = pill(LanguageLoader.get("common.cancel"), 0, Palette.ACCENT);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
                cb.onCancel();
            }
        });
        row.addView(cancel, pillLp());

        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(MATCH, WRAP);
        rl.topMargin = dp(20);
        root.addView(row, rl);

        dialog.setContentView(root);
        FontManager.apply(root);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int maxW = act.getResources().getDisplayMetrics().widthPixels - dp(32);
            w.setLayout(Math.min(maxW, dp(400)), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    public void show() {
        dialog.show();
        ru.jet.editor.core.Popups.flat(dialog);
    }

    public void dismiss() {
        if (dialog.isShowing()) dialog.dismiss();
    }

    public void update(long done, long total) {
        if (total > 0) {
            int pct = (int) (done * 100 / total);
            bar.setIndeterminate(false);
            bar.setMax(100);
            bar.setProgress(pct);
            progressText.setText(pct + "%  ·  " + ToolDownloadService.formatSize(done)
                    + " / " + ToolDownloadService.formatSize(total));
        } else if (done <= 0) {
            bar.setIndeterminate(true);
            progressText.setText(LanguageLoader.get("dl.connecting"));
        } else {
            bar.setIndeterminate(true);
            progressText.setText(ToolDownloadService.formatSize(done));
        }
    }

    private TextView pill(String label, int bg, int fg) {
        TextView t = text(label, 14, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(20));
        if (bg != 0) shape.setColor(bg);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(20));
        mask.setColor(Palette.withAlpha(Palette.TEXT, 0xFF));
        t.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Palette.RIPPLE), shape, mask));
        t.setClickable(true);
        return t;
    }

    private LinearLayout.LayoutParams pillLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, dp(40));
        lp.leftMargin = dp(8);
        return lp;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(float v) {
        return (int) (v * act.getResources().getDisplayMetrics().density + 0.5f);
    }
}
