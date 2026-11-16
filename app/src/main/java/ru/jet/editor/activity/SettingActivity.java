package ru.jet.editor.activity;

import ru.jet.editor.core.M3;
import ru.jet.editor.container.BackupFormatSheet;
import ru.jet.editor.container.BottomSheet;
import android.view.WindowInsets;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import ru.jet.editor.R;
import ru.jet.editor.core.AppPrefs;
import ru.jet.editor.core.ColorPicker;
import ru.jet.editor.core.FontManager;
import ru.jet.editor.core.IconDrawable;
import ru.jet.editor.core.LanguageLoader;
import ru.jet.editor.core.M3Dialog;
import ru.jet.editor.core.MaterialSwitch;
import ru.jet.editor.core.Palette;
import ru.jet.editor.core.ThemeTransition;
import ru.jet.editor.core.ThemeSwap;

/**
 * App Settings. Judul besar mengecil dan pindah ke samping tombol back saat di-scroll.
 * Tampilan (tema gelap/terang, warna dinamis, warna aksen) langsung diterapkan dengan membangun ulang layar.
 */
public class SettingActivity extends Activity {

    private interface Toggle { void onToggle(boolean on); }

    private static final float END_SCALE = 18f / 34f;   // 34sp mengecil jadi 18sp
    private static final String KEY_SCROLL = "scrollY";

    private int expandedH, collapsedH, maxScroll, lastY;
    private FrameLayout header;
    private TextView title;
    private LinearLayout col;
    private int appliedSignature;
    private String appliedLang;
    private BottomSheet.Host sheetHost;
    /** Inset status bar dan nav bar: layar digambar sampai tepi (edge-to-edge). */
    private int insTop, insBottom;
    private boolean built;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LanguageLoader.init(this);
        appliedLang = LanguageLoader.current();
        Palette.load(this);
        appliedSignature = Palette.signature();
        // Latar window mengikuti palet: tidak ada kilatan putih saat layar dibangun ulang.
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Palette.BG));
        ThemeTransition.play(this);

        // Bottom sheet dipasang di content (bukan di root layar), jadi tetap ada saat layar dibangun ulang.
        setupEdgeToEdge();
        FrameLayout content = (FrameLayout) findViewById(android.R.id.content);
        sheetHost = new BottomSheet.Host(this, content);
        content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets wi) {
                onInsets(wi);
                return wi;
            }
        });

        int restoreY = savedInstanceState == null ? 0 : savedInstanceState.getInt(KEY_SCROLL, 0);
        buildScreen(restoreY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tema atau warna bisa berubah dari layar lain (mis. saat kembali dari menu): ikuti yang terbaru.
        Palette.load(this);
        if (Palette.signature() != appliedSignature) {
            rebuildInPlace();
        } else if (!LanguageLoader.current().equals(appliedLang)) {
            recreate();
        }
    }

    @Override
    public void onBackPressed() {
        // Tombol back menutup sheet yang sedang terbuka dulu, baru keluar dari layar.
        if (sheetHost != null && sheetHost.dismissTop()) return;
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(KEY_SCROLL, lastY);
    }

    /** Bangun seluruh isi layar dari Palette yang sedang aktif. */
    private void buildScreen(final int restoreY) {
        Window w = getWindow();
        Palette.applyBarIcons(w);
        w.setBackgroundDrawable(new ColorDrawable(Palette.BG));

        expandedH = dp(176) + insTop;     // header setinggi inset status bar di atasnya
        collapsedH = dp(56) + insTop;
        maxScroll = dp(176) - dp(56);

        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), expandedH, dp(16), dp(24) + insBottom);

        addBlock(section(LanguageLoader.get("settings.display")));
        addBlock(displayCard());

        addBlock(section(LanguageLoader.get("settings.managers")));
        addBlock(card(
                new String[]{LanguageLoader.get("settings.sdk_manager"),
                        LanguageLoader.get("settings.aapt_manager"),
                        LanguageLoader.get("settings.build_tools_manager")},
                new int[]{R.drawable.ic_code, R.drawable.ic_terminal, R.drawable.ic_settings},
                new String[]{DownloaderActivity.TYPE_SDK, DownloaderActivity.TYPE_AAPT,
                        DownloaderActivity.TYPE_GRADLE}));

        addBlock(section(LanguageLoader.get("settings.general")));
        addBlock(card(
                buildSettingRow(),
                autoOpenRow(),
                tabsRow(),
                xmlPreviewRow(),
                backupFormatRow(),
                languageRow(false, true)));
        addBlock(card(new String[]{LanguageLoader.get("settings.dummy_7")},
                new int[]{R.drawable.ic_info}));

        // Konten mulai di bawah header (lewat padding atas konten, bukan padding ScrollView).
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(col);
        // Pastikan konten selalu bisa di-scroll minimal sejauh maxScroll,
        // kalau tidak, daftar yang pendek tidak akan pernah memicu collapse.
        scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int ol, int ot, int or, int ob) {
                int min = (b - t) + maxScroll;
                if (col.getMinimumHeight() != min) col.setMinimumHeight(min);
            }
        });
        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            public void onScrollChange(View v, int x, int y, int oldX, int oldY) {
                collapse(y);
            }
        });

        header = buildHeader();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Palette.BG);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(header, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, expandedH, Gravity.TOP));

        setContentView(root);
        built = true;
        FontManager.attach(root);
        collapse(restoreY);
        if (restoreY > 0) {
            scroll.post(() -> scroll.scrollTo(0, restoreY));
        }
    }

    private FrameLayout buildHeader() {
        FrameLayout h = new FrameLayout(this);
        h.setBackgroundColor(Palette.BG);

        ImageView back = new ImageView(this);
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setImageDrawable(IconDrawable.of(this, R.drawable.ic_back, Palette.TEXT, dp(24)));
        // Ripple lingkaran kecil di sekitar ikon, tidak melebar ke seluruh bar.
        RippleDrawable br = new RippleDrawable(ColorStateList.valueOf(Palette.RIPPLE), null, null);
        br.setRadius(dp(20));
        back.setBackground(br);
        back.setClickable(true);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.START);
        bl.topMargin = insTop + (dp(56) - dp(48)) / 2;
        bl.leftMargin = dp(4);
        h.addView(back, bl);

        // Satu judul saja: posisinya dihitung di collapse() dari bawah header (terbuka)
        // ke sejajar tombol back (collapse).
        title = text(LanguageLoader.get("settings.title"), 34, Palette.TEXT, false);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        tl.leftMargin = dp(16);
        h.addView(title, tl);

        // Font dipasang setelah layout pertama, tinggi judul bisa berubah: hitung ulang posisinya.
        h.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int ol, int ot, int or, int ob) {
                collapse(lastY);
            }
        });
        return h;
    }

    /** Dipanggil setiap scroll. scrollY = jarak konten yang sudah di-scroll. */
    private void collapse(int scrollY) {
        lastY = scrollY;
        float p = Math.max(0f, Math.min(1f, scrollY / (float) maxScroll));

        int h = Math.max(collapsedH, expandedH - scrollY);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) header.getLayoutParams();
        if (lp.height != h) {
            lp.height = h;
            header.setLayoutParams(lp);
        }

        float th = title.getHeight();
        if (th == 0) {
            title.measure(0, 0);
            th = title.getMeasuredHeight();
        }
        float s = 1f + (END_SCALE - 1f) * p;
        float topExpanded = expandedH - dp(16) - th;           // menempel di bawah header
        float topCollapsed = insTop + (dp(56) - th * END_SCALE) / 2f; // sejajar tombol back

        title.setPivotX(0f);
        title.setPivotY(0f);
        title.setScaleX(s);
        title.setScaleY(s);
        title.setTranslationX(dp(48) * p);                     // 16dp -> 64dp
        title.setTranslationY(topExpanded + (topCollapsed - topExpanded) * p);
    }

    private TextView section(String label) {
        TextView t = text(label, 14, Palette.ACCENT, true);
        t.setPadding(dp(8), dp(20), dp(8), dp(4));
        return t;
    }

    private void addBlock(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        col.addView(v, lp);
    }

    private View card(String[] labels, int[] icons) {
        return card(labels, icons, null);
    }

    /** types: tujuan per baris (null = baris dummy yang hanya menampilkan toast). */
    private View card(String[] labels, int[] icons, String[] types) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(Palette.CARD, 28));
        card.setClipToOutline(true);

        for (int i = 0; i < labels.length; i++) {
            String type = types == null ? null : types[i];
            card.addView(row(labels[i], icons[i], i == 0, i == labels.length - 1, type), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i < labels.length - 1) {
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                dl.leftMargin = dp(60);
                View divider = new View(this);
                divider.setBackgroundColor(Palette.DIVIDER);
                card.addView(divider, dl);
            }
        }
        return card;
    }

    private View row(final String label, int iconRes, boolean first, boolean last, final String type) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(itemRipple(first, last));
        row.setClickable(true);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(IconDrawable.of(this, iconRes, Palette.SUB, dp(24)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(label, 16, Palette.TEXT, false));
        String sub = type == null ? LanguageLoader.get("settings.dummy_sub")
                : (DownloaderActivity.TYPE_GRADLE.equals(type)
                ? LanguageLoader.get("settings.build_tools_sub")
                : LanguageLoader.get("settings.from_github"));
        texts.addView(text(sub, 14, Palette.SUB, false));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = dp(20);
        row.addView(texts, tl);

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (type != null) {
                    Intent i = new Intent(SettingActivity.this, DownloaderActivity.class);
                    i.putExtra(DownloaderActivity.EXTRA_TYPE, type);
                    startActivity(i);
                    return;
                }
                M3.toast(SettingActivity.this,
                        LanguageLoader.get("settings.dummy_toast", label), Toast.LENGTH_SHORT);
            }
        });
        return row;
    }

    // ------------------------------------------------------------ umum

    /** Item 4: saklar "Tampilkan setelan build". Saat aktif, menu titik tiga di proyek menampilkan "Setelan build". */
    private LinearLayout buildSettingRow() {
        return switchRow(R.drawable.ic_tune, LanguageLoader.get("settings.build_setting"),
                LanguageLoader.get("settings.build_setting_on"), LanguageLoader.get("settings.build_setting_off"),
                AppPrefs.showBuildSetting(this), new Toggle() {
                    public void onToggle(boolean on) { AppPrefs.setShowBuildSetting(SettingActivity.this, on); }
                });
    }

    /** Saklar "Buka otomatis setelah dipasang": aplikasi langsung dibuka setelah build terpasang. */
    private LinearLayout autoOpenRow() {
        return switchRow(R.drawable.ic_play, LanguageLoader.get("settings.auto_open"),
                LanguageLoader.get("settings.auto_open_on"), LanguageLoader.get("settings.auto_open_off"),
                AppPrefs.autoOpenInstalled(this), new Toggle() {
                    public void onToggle(boolean on) { AppPrefs.setAutoOpenInstalled(SettingActivity.this, on); }
                });
    }

    /** Saklar "Gunakan tab": file yang dibuka tampil sebagai tab di atas editor, seperti Android Studio. */
    private LinearLayout tabsRow() {
        return switchRow(R.drawable.ic_file, LanguageLoader.get("settings.editor_tabs"),
                LanguageLoader.get("settings.editor_tabs_on"), LanguageLoader.get("settings.editor_tabs_off"),
                AppPrefs.editorTabs(this), new Toggle() {
                    public void onToggle(boolean on) { AppPrefs.setEditorTabs(SettingActivity.this, on); }
                });
    }

    /** Saklar "Tampilkan XML preview": ikon preview (dummy dulu) muncul di samping tombol Run. */
    private LinearLayout xmlPreviewRow() {
        return switchRow(R.drawable.ic_image, LanguageLoader.get("settings.xml_preview"),
                LanguageLoader.get("settings.xml_preview_on"), LanguageLoader.get("settings.xml_preview_off"),
                AppPrefs.xmlPreview(this), new Toggle() {
                    public void onToggle(boolean on) { AppPrefs.setXmlPreview(SettingActivity.this, on); }
                });
    }

    /** Sama seperti ProjectActivity: bar transparan, konten menggambar sampai tepi layar. */
    private void setupEdgeToEdge() {
        Window w = getWindow();
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    /** Terima inset; kalau inset atas/bawah berubah, bangun ulang layar supaya tata letaknya ikut. */
    private void onInsets(WindowInsets wi) {
        int t, b, l, r, ime;
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = wi.getInsets(WindowInsets.Type.systemBars());
            t = bars.top;
            b = bars.bottom;
            l = bars.left;
            r = bars.right;
            ime = wi.getInsets(WindowInsets.Type.ime()).bottom;
        } else {
            t = wi.getSystemWindowInsetTop();
            l = wi.getSystemWindowInsetLeft();
            r = wi.getSystemWindowInsetRight();
            b = wi.getSystemWindowInsetBottom();
            ime = 0;
        }
        sheetHost.setInsets(l, r, b, ime);
        sheetHost.setTopInset(t);
        if (t == insTop && b == insBottom) return;
        insTop = t;
        insBottom = b;
        if (built) {
            // Bangun ulang setelah dispatch insets selesai, bukan di tengah layout.
            col.post(new Runnable() {
                public void run() {
                    if (!isFinishing()) buildScreen(lastY);
                }
            });
        }
    }

    /** Item 5: format nama file backup. Ketuk untuk membuka bottom sheet pengaturan pola. */
    private LinearLayout backupFormatRow() {
        final LinearLayout row = baseRow();

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(IconDrawable.of(this, R.drawable.ic_backup, Palette.SUB, dp(24)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        final TextView sub = text(AppPrefs.backupNameFormat(this), 13, Palette.SUB, false);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = dp(20);
        row.addView(textColumn(text(LanguageLoader.get("settings.backup_format"), 16, Palette.TEXT, false), sub), tl);

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showBackupFormatSheet(sub); }
        });
        return row;
    }

    private void showBackupFormatSheet(final TextView sub) {
        new BackupFormatSheet(this, sheetHost, AppPrefs.backupNameFormat(this), new BackupFormatSheet.OnSave() {
            public void onSave(String format) {
                AppPrefs.setBackupNameFormat(SettingActivity.this, format);
                sub.setText(format);
            }
        }).show();
    }

    // ------------------------------------------------------------ bahasa

    /** Baris Bahasa: subjudul menampilkan nama bahasa yang sedang dipakai. Ketuk untuk memilih. */
    private LinearLayout languageRow(boolean first, boolean last) {
        final LinearLayout row = baseRow();
        row.setBackground(itemRipple(first, last));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(IconDrawable.of(this, R.drawable.ic_language, Palette.SUB, dp(24)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        final TextView sub = text(LanguageLoader.nativeName(LanguageLoader.current()), 14, Palette.SUB, false);
        LinearLayout texts = textColumn(text(LanguageLoader.get("settings.language"), 16, Palette.TEXT, false), sub);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = dp(20);
        row.addView(texts, tl);

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showLanguageDialog();
            }
        });
        return row;
    }

    /** Dialog M3 berisi daftar bahasa dari assets/language. Bahasa yang dipilih langsung diterapkan. */
    private void showLanguageDialog() {
        final List<String> codes = LanguageLoader.available(this);
        final String current = LanguageLoader.current();
        final Dialog[] dialogRef = new Dialog[1];

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (final String code : codes) {
            final boolean selected = code.equals(current);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(16), 0, dp(12), 0);
            item.setMinimumHeight(dp(56));

            GradientDrawable selBg = new GradientDrawable();
            selBg.setCornerRadius(dp(16));
            selBg.setColor(selected ? Palette.withAlpha(Palette.ACCENT, 0x26) : Color.TRANSPARENT);
            item.setBackground(new RippleDrawable(ColorStateList.valueOf(Palette.RIPPLE), selBg, null));
            item.setClickable(true);

            TextView label = text(LanguageLoader.nativeName(code), 16,
                    selected ? Palette.ACCENT : Palette.TEXT, selected);
            item.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

            if (selected) {
                ImageView check = new ImageView(this);
                check.setImageDrawable(IconDrawable.of(this, R.drawable.ic_check, Palette.ACCENT, dp(20)));
                item.addView(check, new LinearLayout.LayoutParams(dp(20), dp(20)));
            }

            item.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                    if (!code.equals(current)) changeLanguage(code);
                }
            });
            list.addView(item, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        dialogRef[0] = new M3Dialog(this)
                .icon(R.drawable.ic_language)
                .title(LanguageLoader.get("settings.language"))
                .view(list)
                .negative(LanguageLoader.get("common.close"), null)
                .show();
    }

    /** Simpan pilihan, muat ulang teks, lalu bangun ulang layar (crossfade seperti pergantian tema). */
    private void changeLanguage(final String code) {
        AppPrefs.setLanguage(this, code);
        LanguageLoader.load(this, code);
        getWindow().getDecorView().postDelayed(new Runnable() {
            public void run() {
                ThemeTransition.capture(SettingActivity.this);
                recreate();
            }
        }, 260);   // beri waktu dialog selesai ditutup sebelum gambar layar diambil
    }

    // ------------------------------------------------------------ tampilan

    /** Kartu Tampilan: tema gelap, warna dinamis, dan warna aksen. */
    private LinearLayout displayCard() {
        LinearLayout themeRow = switchRow(0, LanguageLoader.get("display.dark_theme"),
                LanguageLoader.get("display.dark_on"), LanguageLoader.get("display.dark_off"),
                AppPrefs.isDark(this), on -> {
                    AppPrefs.setDark(this, on);
                    applyTheme();
                });
        LinearLayout dynRow = switchRow(0, LanguageLoader.get("display.dynamic"),
                LanguageLoader.get("display.dynamic_on"), LanguageLoader.get("display.dynamic_off"),
                AppPrefs.isDynamic(this), on -> {
                    AppPrefs.setDynamic(this, on);
                    applyTheme();
                });
        if (Build.VERSION.SDK_INT < 31) {
            // Warna dinamis hanya ada di Android 12+: baris ini tampil redup dan tidak bisa diketuk.
            dynRow.setOnClickListener(null);
            dynRow.setClickable(false);
            dynRow.setAlpha(0.38f);
        }
        return card(themeRow, dynRow, accentRow());
    }

    /** Kartu dengan dua/lebih baris: divider tipis di antara baris, sudut kartu ikut membulat. */
    private LinearLayout card(View... rows) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(Palette.CARD, 28));
        c.setClipToOutline(true);
        for (int i = 0; i < rows.length; i++) {
            c.addView(rows[i], new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i < rows.length - 1) {
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
                dl.leftMargin = dp(16);
                dl.rightMargin = dp(16);
                View divider = new View(this);
                divider.setBackgroundColor(Palette.DIVIDER);
                c.addView(divider, dl);
            }
        }
        return c;
    }

    /** Baris dengan MaterialSwitch. Saklar hanya tampilan; baris yang menangani ketukan. */
    private LinearLayout switchRow(int iconRes, String label, final String onText, final String offText,
                                   boolean on, final Toggle toggle) {
        final LinearLayout row = baseRow();
        final TextView sub = text(on ? onText : offText, 13, Palette.SUB, false);
        final MaterialSwitch sw = new MaterialSwitch(this);
        sw.setEnabled(false);
        sw.setFocusable(false);
        sw.setActiveColor(Palette.ACCENT);
        sw.setInactiveColor(Color.TRANSPARENT);
        sw.setStrokeActiveColor(Palette.ACCENT);
        sw.setStrokeInactiveColor(Palette.OUTLINE);
        sw.setThumbActiveColor(Palette.ON_ACCENT);
        sw.setThumbInactiveColor(Palette.OUTLINE);
        sw.setCheckedImmediate(on);

        if (iconRes != 0) {
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(IconDrawable.of(this, iconRes, Palette.SUB, dp(24)));
            row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        if (iconRes != 0) tl.leftMargin = dp(20);
        row.addView(textColumn(text(label, 16, Palette.TEXT, false), sub), tl);
        row.addView(sw, new LinearLayout.LayoutParams(dp(54), dp(42)));

        row.setOnClickListener(v -> {
            boolean next = !sw.isChecked();
            sw.setChecked(next);
            sub.setText(next ? onText : offText);
            toggle.onToggle(next);
        });
        return row;
    }

    /** Baris "App color": ketuk untuk membuka pemilih warna. */
    private LinearLayout accentRow() {
        final LinearLayout row = baseRow();
        final int saved = AppPrefs.appColor(this);

        String subText = Palette.dynamicActive ? LanguageLoader.get("display.accent_dynamic")
                : saved == 0 ? LanguageLoader.get("display.accent_default") : ColorPicker.hex(saved);
        TextView sub = text(subText, 13, Palette.SUB, false);
        row.addView(textColumn(text(LanguageLoader.get("display.app_color"), 16, Palette.TEXT, false), sub),
                new LinearLayout.LayoutParams(0, -2, 1f));

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(Palette.ACCENT);
        View dotView = new View(this);
        dotView.setBackground(dot);
        row.addView(dotView, new LinearLayout.LayoutParams(dp(28), dp(28)));

        row.setOnClickListener(v -> ColorPicker.show(this, Palette.ACCENT, color -> {
            AppPrefs.setAppColor(this, color);
            applyTheme();
        }));
        return row;
    }

    /** Dasar setiap baris yang bisa diketuk. Ripple berada di baris itu, memenuhi lebar dan tinggi baris. */
    private LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(Palette.RIPPLE), null,
                new ColorDrawable(Color.WHITE)));
        row.setClickable(true);
        return row;
    }

    private LinearLayout textColumn(TextView top, TextView bottom) {
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(top);
        texts.addView(bottom);
        return texts;
    }

    /** Ripple per item. Mask membatasi efek di dalam baris itu saja; baris atas/bawah ikut membulat. */
    private RippleDrawable itemRipple(boolean first, boolean last) {
        float r = dp(20);
        float t = first ? r : 0f;
        float b = last ? r : 0f;
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadii(new float[]{t, t, t, t, b, b, b, b});
        return new RippleDrawable(ColorStateList.valueOf(Palette.RIPPLE), null, mask);
    }

    /**
     * Ganti tema atau warna. Tunggu saklar selesai bergeser (420 ms), lalu ambil gambar layar
     * dan bangun ulang dengan palet baru; layar baru memudar dari gambar lama (crossfade).
     */
    private void applyTheme() {
        // Debounce: klik beruntun hanya menjadwal SATU rebuild, setelah pengguna berhenti mengetuk.
        View decor = getWindow().getDecorView();
        decor.removeCallbacks(applyThemeTask);
        decor.postDelayed(applyThemeTask, THEME_DEBOUNCE_MS);
    }

    private static final long THEME_DEBOUNCE_MS = 150L;

    private final Runnable applyThemeTask = new Runnable() {
        public void run() {
            if (isFinishing() || isDestroyed()) return;
            Palette.load(SettingActivity.this);
            rebuildInPlace();
        }
    };

    /** Bangun ulang isi layar ini saja, tanpa recreate. Layar lama dipudarkan di atasnya. */
    private void rebuildInPlace() {
        android.graphics.Bitmap snap = ThemeSwap.snapshot(this);
        appliedSignature = Palette.signature();
        buildScreen(lastY);
        ThemeSwap.fadeOut(this, snap);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
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
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
