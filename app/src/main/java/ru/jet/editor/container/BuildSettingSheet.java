package ru.jet.editor.container;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import ru.jet.editor.core.AppPrefs;
import ru.jet.editor.core.LanguageLoader;
import ru.jet.editor.core.MaterialSwitch;
import ru.jet.editor.core.Palette;

/**
 * Bottom sheet "Setelan build": keystore (debug / pilih sendiri + buat keystore),
 * buka otomatis setelah dipasang, dan versi Java. Perubahan baru disimpan saat tombol Simpan ditekan.
 */
public final class BuildSettingSheet {

    private static final String[] JAVA_LEVELS = {"1.7", "1.8", "11", "15", "16"};

    private final Activity act;
    private final BottomSheet.Host host;

    // keadaan sementara (draft) sampai user menekan Simpan
    private boolean debug;
    private boolean autoOpen;
    private String java;

    public BuildSettingSheet(Activity act, BottomSheet.Host host) {
        this.act = act;
        this.host = host;
    }

    public void show() {
        debug = AppPrefs.buildDebug(act);
        autoOpen = AppPrefs.autoOpenInstalled(act);
        java = AppPrefs.javaLevel(act);

        final BottomSheet s = host.create();
        final LinearLayout body = s.body();

        body.addView(BottomSheet.text(act, LanguageLoader.get("menu.build_setting"), 22, Palette.TEXT, true));

        // ----- Keystore -----
        body.addView(section(LanguageLoader.get("buildset.keystore_section"), 16));
        final TextView debugSub = BottomSheet.text(act, "", 13, Palette.SUB, false);
        final MaterialSwitch debugSw = switchView();
        // Keadaan awal langsung tampil tanpa animasi; ketukan berikutnya memakai animasi geser.
        debugSw.setCheckedImmediate(debug);
        final Runnable refreshDebug = new Runnable() {
            public void run() {
                debugSw.setChecked(debug);
                debugSub.setText(LanguageLoader.get(debug ? "buildset.debug_on" : "buildset.debug_off"));
            }
        };
        refreshDebug.run();
        body.addView(switchRow(LanguageLoader.get("buildset.debug"), debugSub, debugSw, new Runnable() {
            public void run() {
                debug = !debug;
                refreshDebug.run();
            }
        }), rowLp(act, 8));

        // tombol Buat keystore di bawah saklar debug
        TextView create = BottomSheet.confirmButton(act, LanguageLoader.get("buildset.create_keystore"),
                new View.OnClickListener() {
                    public void onClick(View v) { new KeystoreSheet(act, host).show(); }
                });
        body.addView(create, rowLp(act, 10, 52));

        // ----- Buka otomatis setelah dipasang -----
        final TextView autoSub = BottomSheet.text(act, "", 13, Palette.SUB, false);
        final MaterialSwitch autoSw = switchView();
        autoSw.setCheckedImmediate(autoOpen);
        final Runnable refreshAuto = new Runnable() {
            public void run() {
                autoSw.setChecked(autoOpen);
                autoSub.setText(LanguageLoader.get(autoOpen ? "settings.auto_open_on" : "settings.auto_open_off"));
            }
        };
        refreshAuto.run();
        body.addView(switchRow(LanguageLoader.get("settings.auto_open"), autoSub, autoSw, new Runnable() {
            public void run() {
                autoOpen = !autoOpen;
                refreshAuto.run();
            }
        }), rowLp(act, 20));

        // ----- Java -----
        body.addView(section(LanguageLoader.get("buildset.java_section"), 20));
        body.addView(javaRow(), rowLp(act, 8));

        // ----- Batal | Simpan -----
        LinearLayout buttons = new LinearLayout(act);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = BottomSheet.confirmButton(act, LanguageLoader.get("common.cancel"), new View.OnClickListener() {
            public void onClick(View v) { s.dismiss(); }
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, BottomSheet.dp(act, 52), 1f);
        cl.rightMargin = BottomSheet.dp(act, 12);
        buttons.addView(cancel, cl);
        TextView save = BottomSheet.button(act, LanguageLoader.get("common.save"), true, new View.OnClickListener() {
            public void onClick(View v) {
                AppPrefs.setBuildDebug(act, debug);
                AppPrefs.setAutoOpenInstalled(act, autoOpen);
                AppPrefs.setJavaLevel(act, java);
                s.dismiss();
            }
        });
        buttons.addView(save, new LinearLayout.LayoutParams(0, BottomSheet.dp(act, 52), 1.4f));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = BottomSheet.dp(act, 24);
        body.addView(buttons, bl);

        s.show();
    }

    // ---------------------------------------------------------- komponen

    private TextView section(String label, int topDp) {
        TextView t = BottomSheet.text(act, label, 14, Palette.ACCENT, true);
        LinearLayout.LayoutParams lp = BottomSheet.wrap();
        lp.topMargin = BottomSheet.dp(act, topDp);
        lp.bottomMargin = BottomSheet.dp(act, 4);
        t.setLayoutParams(lp);
        return t;
    }

    private MaterialSwitch switchView() {
        MaterialSwitch sw = new MaterialSwitch(act);
        sw.setEnabled(false);
        sw.setFocusable(false);
        sw.setActiveColor(Palette.ACCENT);
        sw.setInactiveColor(Color.TRANSPARENT);
        sw.setStrokeActiveColor(Palette.ACCENT);
        sw.setStrokeInactiveColor(Palette.OUTLINE);
        sw.setThumbActiveColor(Palette.ON_ACCENT);
        sw.setThumbInactiveColor(Palette.OUTLINE);
        return sw;
    }

    /** Baris berisi judul, keterangan, dan saklar. Ketuk baris untuk mengubah saklar. */
    private View switchRow(String title, TextView sub, MaterialSwitch sw, final Runnable onTap) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(BottomSheet.dp(act, 16), BottomSheet.dp(act, 12), BottomSheet.dp(act, 12), BottomSheet.dp(act, 12));
        row.setBackground(BottomSheet.ripple(act, BottomSheet.round(act, Palette.POPUP, 20), 20));
        row.setClickable(true);

        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(BottomSheet.text(act, title, 16, Palette.TEXT, false));
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(sw, new LinearLayout.LayoutParams(BottomSheet.dp(act, 54), BottomSheet.dp(act, 42)));

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onTap.run(); }
        });
        return row;
    }

    /** Pilihan versi Java: radio dan label sejajar dalam satu baris, tanpa kotak latar. */
    private View javaRow() {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        fillJava(row);
        return row;
    }

    /** Isi ulang baris Java sesuai pilihan terkini. Dipanggil ulang setiap pilihan berubah. */
    private void fillJava(final LinearLayout row) {
        row.removeAllViews();
        for (final String level : JAVA_LEVELS) {
            final boolean selected = level.equals(java);
            LinearLayout opt = new LinearLayout(act);
            opt.setOrientation(LinearLayout.HORIZONTAL);
            opt.setGravity(Gravity.CENTER_VERTICAL);
            opt.setPadding(BottomSheet.dp(act, 2), BottomSheet.dp(act, 10), BottomSheet.dp(act, 2), BottomSheet.dp(act, 10));
            opt.setClickable(true);
            opt.setBackground(BottomSheet.ripple(act, null, 16));
            opt.addView(radio(selected), new LinearLayout.LayoutParams(
                    BottomSheet.dp(act, 20), BottomSheet.dp(act, 20)));
            TextView label = BottomSheet.text(act, level, 14,
                    selected ? Palette.ACCENT : Palette.TEXT, selected);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ll.leftMargin = BottomSheet.dp(act, 6);
            opt.addView(label, ll);
            opt.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    java = level;
                    fillJava(row);
                }
            });
            row.addView(opt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    /** Lingkaran radio: cincin aksen, dengan titik di tengah kalau terpilih. */
    private View radio(boolean selected) {
        FrameLayout f = new FrameLayout(act);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(BottomSheet.dp(act, 2), selected ? Palette.ACCENT : Palette.OUTLINE);
        f.setBackground(ring);
        if (selected) {
            View dot = new View(act);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Palette.ACCENT);
            dot.setBackground(d);
            f.addView(dot, new FrameLayout.LayoutParams(BottomSheet.dp(act, 10), BottomSheet.dp(act, 10), Gravity.CENTER));
        }
        return f;
    }

    private static LinearLayout.LayoutParams rowLp(Activity act, int topDp) {
        return rowLp(act, topDp, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams rowLp(Activity act, int topDp, int heightDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightDp == ViewGroup.LayoutParams.WRAP_CONTENT ? heightDp : BottomSheet.dp(act, heightDp));
        lp.topMargin = BottomSheet.dp(act, topDp);
        return lp;
    }
}
