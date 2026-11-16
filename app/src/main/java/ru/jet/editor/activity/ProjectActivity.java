package ru.jet.editor.activity;

import ru.jet.editor.core.M3;
import ru.jet.editor.core.LanguageLoader;
import ru.jet.editor.core.Palette;
import ru.jet.editor.core.ThemeTransition;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.jet.editor.FileUtil;
import ru.jet.editor.R;
import ru.jet.editor.core.CodeEditor;
import ru.jet.editor.core.DebugBuild;
import ru.jet.editor.core.EcjCompiler;
import ru.jet.editor.core.FontManager;
import ru.jet.editor.core.IconDrawable;
import ru.jet.editor.core.InoInputLayout;
import ru.jet.editor.core.PreviewSurface;
import ru.jet.editor.core.LayoutPreview;
import ru.jet.editor.core.M3Dialog;
import ru.jet.editor.core.M3Menu;
import ru.jet.editor.core.PrettyPrinter;
import ru.jet.editor.core.PushDrawerLayout;
import ru.jet.editor.core.SyntaxAssets;
import ru.jet.editor.container.BottomSheet;
import ru.jet.editor.container.BuildSheet;
import ru.jet.editor.container.BuildSettingSheet;
import ru.jet.editor.container.PreviewSizeSheet;
import ru.jet.editor.core.AppPrefs;
import ru.jet.editor.core.Installer;

/**
 * Project editor.
 *
 * - Edge-to-edge (status bar & nav bar transparan, mengikuti MainActivity)
 * - Drawer PUSH yang sama dengan MainActivity (PushDrawerLayout)
 * - Project tree: indent guide, ikon per tipe file, highlight file aktif, pencarian rekursif
 * - Menu titik tiga bergaya Material 3 (M3Menu)
 * - Editor: CodeEditor (gutter, bracket match, auto indent, undo/redo, word wrap, dll)
 * Tidak menggunakan AndroidX/AppCompat.
 */
public class ProjectActivity extends Activity {
    private int appliedSignature;
    private String appliedLang;

    private static final int I_MENU = R.drawable.ic_menu;
    private static final int I_SEARCH = R.drawable.ic_search;
    private static final int I_CLOSE = R.drawable.ic_close;
    private static final int I_MORE = R.drawable.ic_more;
    private static final int I_FOLDER_OPEN = R.drawable.ic_folder;
    private static final int I_FOLDER_CLOSED = R.drawable.ic_folder_closed;
    private static final int I_FILE = R.drawable.ic_file;
    private static final int I_CODE = R.drawable.ic_code;
    private static final int I_IMAGE = R.drawable.ic_image;
    private static final int I_CHEVRON = R.drawable.ic_chevron;
    private static final int I_SAVE = R.drawable.ic_save;
    private static final int I_UNDO = R.drawable.ic_undo;
    private static final int I_REDO = R.drawable.ic_redo;
    private static final int I_WRAP = R.drawable.ic_wrap_text;
    private static final int I_REFRESH = R.drawable.ic_refresh;
    private static final int I_COLLAPSE = R.drawable.ic_unfold_less;
    private static final int I_HIDDEN = R.drawable.ic_visibility;
    private static final int I_COPY = R.drawable.ic_copy;
    private static final int I_LINK = R.drawable.ic_link;
    private static final int I_PLAY = R.drawable.ic_play;
    private static final int I_KEY = R.drawable.ic_key;
    private static final int I_DELETE = R.drawable.ic_delete;
    private static final int I_RENAME = R.drawable.ic_rename;
    private static final int I_TUNE = R.drawable.ic_tune;
    private static final int I_BUILD = R.drawable.ic_settings;
    private static final int I_BOUNDS = R.drawable.ic_visibility;
    private static final int I_IDS = R.drawable.ic_info;

    // id item menu
    private static final int M_SAVE = 1, M_UNDO = 2, M_REDO = 3, M_WRAP = 4, M_CLOSE = 5,
            M_REFRESH = 6, M_COLLAPSE = 7, M_HIDDEN = 8, M_PRETTY = 9, M_SYNTAX = 10,
            M_COPY_PATH = 20, M_COPY_REL = 21, M_OPEN = 22,
            M_NEW_FILE = 30, M_NEW_FOLDER = 31, M_DELETE = 32, M_RENAME = 33,
            M_BUILD_SETTING = 11, M_FIND = 12,
            M_P_BOUNDS = 40, M_P_IDS = 41, M_P_VALUES = 42, M_P_SIZE = 43;

    private static final long MAX_FILE_BYTES = 1536 * 1024;
    private static final String BINARY_EXT = ",png,jpg,jpeg,gif,webp,bmp,ico,zip,jar,apk,aab,dex,so,class,ttf,otf,woff,woff2,mp3,mp4,ogg,wav,pdf,keystore,jks,";

    // ===== View =====
    private FrameLayout root;
    private FrameLayout page;
    private BottomSheet.Host sheetHost;
    private LinearLayout column;
    private LinearLayout drawer;
    private PushDrawerLayout drawerLayout;
    private ListView treeList;
    private TextView treeEmpty;
    private EditText searchEdit;
    private TreeAdapter treeAdapter;
    private CodeEditor codeEditor;
    /** File yang sedang terbuka sebagai tab (urutan = urutan dibuka). */
    private final List<Node> openTabs = new ArrayList<>();
    private HorizontalScrollView tabScroll;
    private LinearLayout tabBar;
    private LinearLayout findHost;
    private EditText findEdit, replaceEdit;
    private TextView findStatus;
    private ImageView previewButton;
    private FrameLayout previewHost;          // sejajar codeEditor, tampil saat mode preview
    private PreviewSurface previewSurface;    // area preview (bingkai HP), dipakai ulang antar render
    private boolean previewOn;
    private boolean previewBounds, previewIds, previewValues;
    private final Handler previewHandler = new Handler(Looper.getMainLooper());
    private final Runnable previewRefresh = new Runnable() {
        public void run() { if (previewOn) renderPreview(false); }
    };
    private LinearLayout quickHost;
    private ImageView undoBtn, redoBtn;
    private TextView fileTab;
    private TextView emptyView;
    private TextView pathText;

    // ===== State =====
    private File rootDir;
    private String projectTitle;
    private Node rootNode;
    private Node openedNode;
    private boolean modified;
    private boolean showHidden;
    private boolean searching;
    private int searchGen;
    private final Handler handler = new Handler();
    private Runnable searchTask;
    private int insTop, insBottom, insLeft, insRight, imeBottom;

    private final ArrayList<Node> visibleRows = new ArrayList<Node>();

    // =====================================================================
    // Lifecycle
    // =====================================================================

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LanguageLoader.init(this);
        appliedLang = LanguageLoader.current();
        Palette.load(this);
        appliedSignature = Palette.signature();
        ThemeTransition.track(this);
        ThemeTransition.play(this);

        projectTitle = getIntent().getStringExtra("title");
        String path = getIntent().getStringExtra("path");
        rootDir = path == null ? null : new File(path);
        if (projectTitle == null || projectTitle.length() == 0) {
            projectTitle = rootDir != null ? rootDir.getName() : LanguageLoader.get("project.default_title");
        }

        getWindow().setBackgroundDrawable(new ColorDrawable(Palette.DRAWER));
        buildUi();
        setupEdgeToEdge();

        if (rootDir != null && rootDir.isDirectory()) {
            rootNode = new Node(rootDir, projectTitle, 0);
            expand(rootNode, true);
        }
        rebuildTree();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSilently();      // jangan sampai edit hilang kalau app ditutup
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchGen++;
        if (searchTask != null) handler.removeCallbacks(searchTask);
    }

    /** Ketuk di luar kolom search: lepas fokus + tutup keyboard (sama seperti MainActivity). */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN
                && searchEdit != null && searchEdit.hasFocus()) {
            int[] loc = new int[2];
            searchEdit.getLocationOnScreen(loc);
            float x = ev.getRawX(), y = ev.getRawY();
            if (x < loc[0] || x > loc[0] + searchEdit.getWidth()
                    || y < loc[1] || y > loc[1] + searchEdit.getHeight()) {
                clearSearchFocus();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void clearSearchFocus() {
        hideKeyboard();
        if (searchEdit != null) searchEdit.clearFocus();
        if (root != null) root.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (sheetHost != null && sheetHost.dismissTop()) return;
        if (drawerLayout != null && drawerLayout.isOpen()) {
            drawerLayout.close();
            return;
        }
        if (previewOn) {
            setPreviewMode(false);
            return;
        }
        if (openedNode != null) {
            closeFile();
            return;
        }
        super.onBackPressed();
    }

    // =====================================================================
    // Edge-to-edge (tanpa androidx) — sama dengan MainActivity
    // =====================================================================

    @Override
    protected void onResume() {
        super.onResume();
        Installer.onResumed(this);   // buka otomatis setelah installer bawaan selesai (jika pengaturan aktif)
        Palette.load(this);
        if (Palette.signature() != appliedSignature) rebuildInPlace();
        if (!LanguageLoader.current().equals(appliedLang)) { recreate(); return; }
        refreshToolbarOptions();   // pengaturan bisa berubah dari SettingActivity
    }

    /**
     * Tema/warna berubah: UI dibangun ulang di activity yang sama. File yang sedang dibuka disimpan
     * dulu lalu dibuka lagi, supaya editor tidak kehilangan isi.
     */
    private void rebuildInPlace() {
        android.graphics.Bitmap snap = ru.jet.editor.core.ThemeSwap.snapshot(this);
        saveSilently();
        final Node keep = openedNode;
        appliedSignature = Palette.signature();
        getWindow().setBackgroundDrawable(new ColorDrawable(Palette.DRAWER));
        buildUi();
        setupEdgeToEdge();
        rebuildTree();
        if (keep != null) openFile(keep);
        ru.jet.editor.core.ThemeSwap.fadeOut(this, snap);
    }

    private void setupEdgeToEdge() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            Palette.applyBarIcons(w);
        } else if (Build.VERSION.SDK_INT >= 21) {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets wi) {
                onInsets(wi);
                return wi;
            }
        });
        root.requestApplyInsets();
    }

    private void onInsets(WindowInsets wi) {
        int t, b, l, r, ime;
        if (Build.VERSION.SDK_INT >= 30) {
            int types = WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout();
            t = wi.getInsets(types).top;
            b = wi.getInsets(types).bottom;
            l = wi.getInsets(types).left;
            r = wi.getInsets(types).right;
            ime = wi.getInsets(WindowInsets.Type.ime()).bottom;
        } else {
            t = wi.getSystemWindowInsetTop();
            l = wi.getSystemWindowInsetLeft();
            r = wi.getSystemWindowInsetRight();
            b = wi.getStableInsetBottom();
            int sb = wi.getSystemWindowInsetBottom();
            ime = sb > b ? sb : 0;
        }
        if (t == insTop && b == insBottom && l == insLeft && r == insRight && ime == imeBottom) return;
        insTop = t;
        insBottom = b;
        insLeft = l;
        insRight = r;
        imeBottom = ime;
        applyInsets();
    }

    private void applyInsets() {
        int bottom = imeBottom > 0 ? imeBottom : insBottom;
        // drawer: list tree boleh menggulir sampai tepi bawah layar (di bawah nav bar transparan);
        // saat keyboard terbuka, drawer berhenti di atas keyboard.
        if (drawer != null) drawer.setPadding(insLeft, dp(20) + insTop, 0, imeBottom > 0 ? imeBottom : 0);
        if (treeList != null) treeList.setPadding(dp(12), dp(14), dp(10), dp(16) + (imeBottom > 0 ? 0 : insBottom));
        if (column != null) column.setPadding(insLeft, insTop, insRight, 0);
        if (quickHost != null) quickHost.setPadding(0, 0, 0, bottom);
        if (sheetHost != null) {
            sheetHost.setInsets(insLeft, insRight, insBottom, imeBottom);
            sheetHost.setTopInset(insTop);
        }
    }

    // =====================================================================
    // UI
    // =====================================================================

    private void buildUi() {
        root = new FrameLayout(this);
        sheetHost = new BottomSheet.Host(this, root);
        sheetHost.setOnChange(new Runnable() {
            public void run() { if (codeEditor != null) codeEditor.dismissCursorUi(); }
        });
        root.setBackgroundColor(Palette.DRAWER);

        page = buildContent();
        drawer = buildDrawer();

        int drawerW = (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
        drawerLayout = new PushDrawerLayout(this, drawerW);
        drawerLayout.setViews(page, drawer);
        drawerLayout.setListener(new PushDrawerLayout.Listener() {
            public void onDrawerOpening() { hideKeyboard(); }
        });
        root.addView(drawerLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        FontManager.attach(root);
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        refreshToolbarOptions();
        applyInsets();
    }

    private FrameLayout buildContent() {
        FrameLayout p = new FrameLayout(this);
        p.setBackgroundColor(Palette.BG);

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        // ----- top bar -----
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(12), dp(12), dp(6));

        ImageView menu = circleButton(I_MENU);
        menu.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { drawerLayout.toggle(); }
        });
        bar.addView(menu, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL);
        fileTab = text(projectTitle, 17, Palette.TEXT, true);
        fileTab.setSingleLine(true);
        fileTab.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(fileTab);
        pathText = text(LanguageLoader.get("project.tree"), 12, Palette.SUB, false);
        pathText.setSingleLine(true);
        pathText.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titles.addView(pathText);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = dp(12);
        tl.rightMargin = dp(8);
        bar.addView(titles, tl);

        // tombol Run (play outline), tepat di sebelah kiri tombol more
        ImageView play = circleButton(I_PLAY);
        play.setImageDrawable(IconDrawable.of(this, I_PLAY, Palette.ACCENT, dp(24)));
        play.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { runProject(); }
        });
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(dp(52), dp(52));
        playLp.rightMargin = dp(8);
        bar.addView(play, playLp);

        // ikon preview XML di samping tombol Run; hanya tampil saat pengaturan aktif
        previewButton = circleButton(I_IMAGE);
        previewButton.setImageDrawable(IconDrawable.of(this, I_IMAGE, Palette.ACCENT, dp(24)));
        previewButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { togglePreview(); }
        });
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(dp(52), dp(52));
        previewLp.rightMargin = dp(8);
        bar.addView(previewButton, previewLp);

        ImageView more = circleButton(I_MORE);
        more.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showEditorMenu(v); }
        });
        bar.addView(more, new LinearLayout.LayoutParams(dp(52), dp(52)));

        column.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(Palette.DIVIDER);
        column.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // ----- tab file terbuka (hanya saat pengaturan "Gunakan tab" aktif) -----
        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setBackgroundColor(Palette.CARD);
        tabScroll.setVisibility(View.GONE);
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setGravity(Gravity.CENTER_VERTICAL);
        tabScroll.addView(tabBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        column.addView(tabScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ----- bar cari & ganti (tersembunyi sampai dibuka dari menu editor) -----
        findHost = buildFindBar();
        findHost.setVisibility(View.GONE);
        column.addView(findHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ----- editor -----
        FrameLayout editorHolder = new FrameLayout(this);
        codeEditor = new CodeEditor(this);
        codeEditor.setTheme(resolveSyntaxTheme());
        codeEditor.setVisibility(View.GONE);
        codeEditor.setOnSaveRequested(new Runnable() {
            public void run() { saveFile(); }
        });
        editorHolder.addView(codeEditor, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // preview layout: menggantikan editor di area yang sama (tab tetap di atasnya)
        previewHost = new FrameLayout(this);
        previewHost.setBackgroundColor(0xFFFFFFFF);
        previewHost.setVisibility(View.GONE);
        editorHolder.addView(previewHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyView = text(LanguageLoader.get("project.pick_file"), 15, Palette.SUB, false);
        emptyView.setGravity(Gravity.CENTER);
        editorHolder.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        column.addView(editorHolder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ----- quick bar (hanya saat ada file) -----
        quickHost = new LinearLayout(this);
        quickHost.setOrientation(LinearLayout.VERTICAL);
        quickHost.setBackgroundColor(Palette.CARD);
        quickHost.setVisibility(View.GONE);
        View qd = new View(this);
        qd.setBackgroundColor(Palette.DIVIDER);
        quickHost.addView(qd, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        quickHost.addView(buildQuickBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(quickHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        p.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        codeEditor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                if (codeEditor.isHighlighting()) return;
                if (previewOn) {
                    previewHandler.removeCallbacks(previewRefresh);
                    previewHandler.postDelayed(previewRefresh, 350);
                }
                if (findHost != null && findHost.getVisibility() == View.VISIBLE) updateFindStatus();
                if (openedNode != null) {
                    if (!modified) {
                        modified = true;
                        updateTab();
                    }
                    updateUndoButtons();
                }
            }
        });

        return p;
    }

    private View buildQuickBar() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));

        undoBtn = quickIcon(I_UNDO, new View.OnClickListener() {
            public void onClick(View v) { codeEditor.undo(); updateUndoButtons(); }
        });
        redoBtn = quickIcon(I_REDO, new View.OnClickListener() {
            public void onClick(View v) { codeEditor.redo(); updateUndoButtons(); }
        });
        row.addView(undoBtn);
        row.addView(redoBtn);

        View sep = new View(this);
        sep.setBackgroundColor(Palette.DIVIDER);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(1), dp(22));
        sl.setMargins(dp(6), 0, dp(6), 0);
        row.addView(sep, sl);

        String[] symbols = {LanguageLoader.get("keys.tab"), "{", "}", "(", ")", "[", "]", ";", ",", ".", "=", "+", "-", "*", "/", "<", ">", "\"", "'", ":", "_", "!", "&", "|"};
        for (int i = 0; i < symbols.length; i++) {
            final String symbol = symbols[i];
            TextView b = text(symbol, 15, Palette.TEXT, false);
            b.setGravity(Gravity.CENTER);
            b.setMinWidth(dp(44));
            b.setPadding(dp(10), dp(9), dp(10), dp(9));
            b.setBackground(ripple(null, 12));
            b.setClickable(true);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (codeEditor.getVisibility() != View.VISIBLE) return;
                    codeEditor.insertText(symbol.equals(LanguageLoader.get("keys.tab")) ? "    " : symbol);
                    codeEditor.requestFocus();
                }
            });
            row.addView(b);
        }
        scroll.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private ImageView quickIcon(int res, View.OnClickListener l) {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER);
        v.setImageDrawable(IconDrawable.of(this, res, Palette.TEXT, dp(22)));
        v.setBackground(ripple(null, 12));
        v.setClickable(true);
        v.setOnClickListener(l);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(40)));
        return v;
    }

    private void updateUndoButtons() {
        if (undoBtn == null) return;
        undoBtn.setAlpha(codeEditor.canUndo() ? 1f : 0.35f);
        redoBtn.setAlpha(codeEditor.canRedo() ? 1f : 0.35f);
    }

    private LinearLayout buildDrawer() {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.VERTICAL);
        d.setBackgroundColor(Palette.DRAWER);
        d.setPadding(0, dp(20), 0, dp(20));
        d.setClickable(true);

        // ----- header -----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(24), 0, dp(18), 0);

        TextView title = text(projectTitle, 27, Palette.ACCENT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        TextView sub = text(rootDir == null ? LanguageLoader.get("project.default_title") : rootDir.getAbsolutePath(), 11, Palette.SUB, false);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.START);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(sub, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        searchEdit = new EditText(this);
        searchEdit.setHint(LanguageLoader.get("project.search_hint"));
        searchEdit.setHintTextColor(Palette.SUB);
        searchEdit.setTextColor(Palette.TEXT);
        searchEdit.setTextSize(16);
        searchEdit.setSingleLine(true);
        searchEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        searchEdit.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchEdit.setBackground(round(Palette.CARD, 26));
        searchEdit.setPadding(dp(20), 0, dp(16), 0);
        searchEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent e) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    clearSearchFocus();
                    return true;
                }
                return false;
            }
        });
        searchEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) {}
            public void afterTextChanged(Editable s) { onSearchChanged(); }
        });
        searchRow.addView(searchEdit, new LinearLayout.LayoutParams(0, dp(50), 1f));

        final ImageView search = circleButton(I_SEARCH);
        search.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (searchEdit.getText().length() == 0) {
                    searchEdit.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(searchEdit, InputMethodManager.SHOW_IMPLICIT);
                } else {
                    searchEdit.setText("");
                }
            }
        });
        LinearLayout.LayoutParams sl2 = new LinearLayout.LayoutParams(dp(52), dp(52));
        sl2.leftMargin = dp(8);
        searchRow.addView(search, sl2);

        // Tombol segarkan pohon di sebelah kanan tombol search (sama dengan menu "Segarkan pohon").
        final ImageView refresh = circleButton(I_REFRESH);
        refresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { refreshTree(); }
        });
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(dp(52), dp(52));
        rl.leftMargin = dp(8);
        searchRow.addView(refresh, rl);
        header.addView(searchRow);

        d.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ----- tree -----
        FrameLayout treeFrame = new FrameLayout(this);

        treeList = new ListView(this);
        treeList.setDivider(null);
        treeList.setDividerHeight(0);
        treeList.setSelector(new ColorDrawable(Color.TRANSPARENT));
        treeList.setPadding(dp(12), dp(14), dp(10), dp(16));
        treeList.setClipToPadding(false);
        treeList.setVerticalScrollBarEnabled(false);
        treeList.setOverScrollMode(View.OVER_SCROLL_NEVER);
        treeList.setVerticalFadingEdgeEnabled(true);
        treeList.setFadingEdgeLength(dp(32));

        treeAdapter = new TreeAdapter();
        treeList.setAdapter(treeAdapter);
        treeList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= visibleRows.size()) return;
                onNodeClick(visibleRows.get(position));
            }
        });
        treeList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= visibleRows.size()) return false;
                showNodeMenu(view, visibleRows.get(position));
                return true;
            }
        });
        treeFrame.addView(treeList, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        treeEmpty = text("", 14, Palette.SUB, false);
        treeEmpty.setGravity(Gravity.CENTER);
        treeEmpty.setVisibility(View.GONE);
        treeFrame.addView(treeEmpty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        d.addView(treeFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return d;
    }

    private void onNodeClick(Node n) {
        if (searching) {
            if (n.dir) reveal(n.file);
            else openFile(n);
            return;
        }
        if (n.dir) {
            expand(n, !n.expanded);
            rebuildTree();
        } else {
            openFile(n);
        }
    }

    // =====================================================================
    // File ops
    // =====================================================================

    private void openFile(Node n) {
        if (n == null || n.dir) return;
        if (openedNode != null && openedNode.file.equals(n.file)) {
            drawerLayout.close();
            return;
        }
        String name = n.file.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
        if (ext.length() > 0 && BINARY_EXT.contains("," + ext + ",")) {
            M3.toast(this, LanguageLoader.get("project.binary"), Toast.LENGTH_SHORT);
            return;
        }
        if (n.file.length() > MAX_FILE_BYTES) {
            M3.toast(this, LanguageLoader.get("project.too_large"), Toast.LENGTH_SHORT);
            return;
        }
        try {
            saveSilently();    // simpan file sebelumnya kalau ada perubahan
            String contentText = FileUtil.readFile(n.file.getAbsolutePath());
            codeEditor.load(name, contentText);
            codeEditor.setVisibility(View.VISIBLE);
            quickHost.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            openedNode = n;
            modified = false;
            if (indexOfTab(n.file) < 0) openTabs.add(n);
            updateTab();
            updateUndoButtons();
            pathText.setText(relativePath(n.file));
            treeAdapter.notifyDataSetChanged();
            drawerLayout.close();
            if (previewOn) {
                if (isLayoutNode(n)) {
                    codeEditor.setVisibility(View.GONE);
                    quickHost.setVisibility(View.GONE);
                    renderPreview(true);
                } else {
                    setPreviewMode(false);
                }
            }
            if (!previewOn) codeEditor.requestFocus();
        } catch (Exception e) {
            M3.toast(this, LanguageLoader.get("project.open_failed"), Toast.LENGTH_SHORT);
        }
    }

    /**
     * Tombol Run = compile DEBUG (bukan release). Simpan dulu, lalu cek tool dari SDK/AAPT Manager.
     * Tahap compile sendiri belum tersambung, lihat DebugBuild.
     */
    /** Tombol Run: pilih Debug (debug.jks bawaan) atau Release (keystore pilihan user). */
    private void runProject() {
        saveSilently();
        if (rootDir == null || !rootDir.isDirectory()) {
            M3.toast(this, LanguageLoader.get("project.not_found"), Toast.LENGTH_SHORT);
            return;
        }
        // Setelan debug di "Setelan build": on = debug.jks bawaan, off = user pilih keystore dulu.
        if (AppPrefs.buildDebug(this)) {
            if (ensureInstallPermission()) startBuild(DebugBuild.Signing.debug(), LanguageLoader.get("build.debug_title"));
        } else {
            pickReleaseKeystore();
        }
    }

    /** Jalankan pipeline build di thread latar belakang sambil menampilkan progres. */
    private void startBuild(final DebugBuild.Signing sign, String label) {
        if (rootDir == null || !rootDir.isDirectory()) {
            M3.toast(this, LanguageLoader.get("project.not_found"), Toast.LENGTH_SHORT);
            return;
        }
        if (sheetHost == null) return;
        final File dir = rootDir;
        requestNotificationPermission();
        final BuildSheet sheet = new BuildSheet(this, sheetHost, dir.getName(), label);
        sheet.open();
        new Thread(new Runnable() {
            public void run() {
                StringBuilder log = new StringBuilder();
                try {
                    File apk = DebugBuild.buildApk(ProjectActivity.this, dir, log, sheet, sign, sheet.cancelToken());
                    sheet.finish(true, LanguageLoader.get("build.apk_ready", apk.getAbsolutePath()));
                    installAfterBuild(apk);
                } catch (DebugBuild.Cancelled e) {
                    sheet.finishCancelled();
                } catch (Exception e) {
                    sheet.finish(false, e + "\n\n" + tail(log.toString()));
                }
            }
        }).start();
    }

    private static final int REQ_NOTIFY = 4202;

    /** Android 13+: notifikasi progres build butuh izin POST_NOTIFICATIONS. Diminta sekali saat build pertama. */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        String perm = "android.permission.POST_NOTIFICATIONS";
        if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{perm}, REQ_NOTIFY);
        }
    }

    /** Minta izin 'pasang aplikasi' sebelum build dimulai. Jika belum ada, layar pengaturan dibuka dan build dibatalkan. */
    private boolean ensureInstallPermission() {
        if (Installer.canInstall(this)) return true;
        M3.toast(this, LanguageLoader.get("install.allow_run"), Toast.LENGTH_LONG);
        Installer.openInstallSettings(this);
        return false;
    }

    /** Setelah build berhasil: pasang APK. Kalau izin belum ada, user diarahkan ke pengaturan dulu. */
    private void installAfterBuild(final File apk) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (!Installer.canInstall(ProjectActivity.this)) {
                    M3.toast(ProjectActivity.this,
                            LanguageLoader.get("install.allow_build"), Toast.LENGTH_LONG);
                    Installer.openInstallSettings(ProjectActivity.this);
                    return;
                }
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            Installer.install(ProjectActivity.this, apk);
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    M3.toast(ProjectActivity.this,
                                            LanguageLoader.get("install.failed", e.getMessage()), Toast.LENGTH_LONG);
                                }
                            });
                        }
                    }
                }).start();
            }
        });
    }

    private static final int REQ_PICK_KEYSTORE = 4201;

    /** Release: user memilih file keystore lewat file picker sistem. */
    private void pickReleaseKeystore() {
        if (!ensureInstallPermission()) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_PICK_KEYSTORE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_KEYSTORE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            askReleaseCredentials(data.getData());
        }
    }

    /** Setelah keystore dipilih: minta password keystore, alias, dan password kunci. */
    private void askReleaseCredentials(final Uri keystoreUri) {
        if (sheetHost == null) return;
        sheetHost.form(LanguageLoader.get("keystore.release_title"))
                .desc(LanguageLoader.get("keystore.release_desc"))
                .field(null, LanguageLoader.get("keystore.password"), "").password()
                .field(null, LanguageLoader.get("keystore.alias"), "")
                .field(null, LanguageLoader.get("keystore.key_password"), "").password()
                .onSubmit(new BottomSheet.OnSubmit() {
                    public String onSubmit(String[] v) {
                        if (v[0].isEmpty()) return LanguageLoader.get("keystore.password_required");
                        if (v[1].isEmpty()) return LanguageLoader.get("keystore.alias_required");
                        char[] storePass = v[0].toCharArray();
                        char[] keyPass = v[2].isEmpty() ? null : v[2].toCharArray();
                        DebugBuild.KeySource source = new DebugBuild.KeySource() {
                            public InputStream open() throws IOException {
                                InputStream in = getContentResolver().openInputStream(keystoreUri);
                                if (in == null) throw new IOException(LanguageLoader.get("keystore.file_unreadable"));
                                return in;
                            }
                        };
                        DebugBuild.Signing sign = new DebugBuild.Signing(source, v[1],
                                storePass, keyPass, "app-release.apk", false);
                        startBuild(sign, LanguageLoader.get("build.release_title"));
                        return null;
                    }
                })
                .show();
    }

    /** Ambil bagian akhir log supaya dialog tidak terlalu panjang. */
    private static String tail(String s) {
        final int max = 3000;
        if (s == null || s.length() == 0) return LanguageLoader.get("common.no_message");
        return s.length() <= max ? s : "...\n" + s.substring(s.length() - max);
    }

    /** Rapikan isi file yang sedang dibuka (JSON, XML, dan bahasa berbasis kurung). */
    private void prettyPrint() {
        if (openedNode == null) {
            M3.toast(this, LanguageLoader.get("project.open_file_first"), Toast.LENGTH_SHORT);
            return;
        }
        String src = codeEditor.getText().toString();
        String out;
        try {
            out = PrettyPrinter.format(openedNode.file.getName(), src);
        } catch (IllegalArgumentException e) {
            M3.toast(this, e.getMessage(), Toast.LENGTH_SHORT);
            return;
        }
        if (out.equals(src)) {
            M3.toast(this, LanguageLoader.get("format.already"), Toast.LENGTH_SHORT);
            return;
        }
        codeEditor.setText(out);   // lewat watcher: file ditandai berubah, tinggal Save
        updateUndoButtons();
        M3.toast(this, LanguageLoader.get("format.done"), Toast.LENGTH_SHORT);
    }

    private void saveFile() {
        if (openedNode == null) {
            M3.toast(this, LanguageLoader.get("project.no_files"), Toast.LENGTH_SHORT);
            return;
        }
        FileUtil.writeFile(openedNode.file.getAbsolutePath(), codeEditor.getText().toString());
        modified = false;
        updateTab();
        M3.toast(this, LanguageLoader.get("project.saved"), Toast.LENGTH_SHORT);
    }

    /** Simpan tanpa toast, hanya kalau ada perubahan. */
    private void saveSilently() {
        if (openedNode == null || !modified) return;
        FileUtil.writeFile(openedNode.file.getAbsolutePath(), codeEditor.getText().toString());
        modified = false;
        updateTab();
    }

    private void closeFile() {
        saveSilently();
        if (previewOn) setPreviewMode(false);
        if (openedNode != null) removeTabFile(openedNode.file);
        openedNode = null;
        modified = false;
        codeEditor.setText("");
        codeEditor.setVisibility(View.GONE);
        quickHost.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        fileTab.setText(projectTitle);
        fileTab.setTextColor(Palette.TEXT);
        pathText.setText(LanguageLoader.get("project.tree"));
        hideKeyboard();
        treeAdapter.notifyDataSetChanged();
        refreshTabs();
    }

    private void updateTab() {
        if (openedNode == null) {
            fileTab.setText(projectTitle);
        } else {
            fileTab.setText(openedNode.file.getName() + (modified ? " •" : ""));
            fileTab.setTextColor(modified ? Palette.ACCENT : Palette.TEXT);
        }
        refreshTabs();
    }

    private void refreshToolbarOptions() {
        if (previewButton != null) {
            previewButton.setVisibility(AppPrefs.xmlPreview(this) ? View.VISIBLE : View.GONE);
        }
        if (previewOn && !AppPrefs.xmlPreview(this)) setPreviewMode(false);
        refreshTabs();
    }

    private int indexOfTab(File file) {
        for (int i = 0; i < openTabs.size(); i++) {
            if (openTabs.get(i).file.equals(file)) return i;
        }
        return -1;
    }

    private void removeTabFile(File file) {
        int i = indexOfTab(file);
        if (i >= 0) openTabs.remove(i);
    }

    /** Gambar ulang bar tab. Hanya tampil saat pengaturan aktif dan ada file terbuka. */
    private void refreshTabs() {
        if (tabBar == null || tabScroll == null) return;
        for (java.util.Iterator<Node> it = openTabs.iterator(); it.hasNext(); ) {
            if (!it.next().file.exists()) it.remove();   // file sudah dihapus lewat tree
        }
        tabBar.removeAllViews();
        for (Node n : openTabs) tabBar.addView(buildTab(n));
        boolean show = AppPrefs.editorTabs(this) && !openTabs.isEmpty();
        tabScroll.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private View buildTab(final Node n) {
        final boolean active = openedNode != null && openedNode.file.equals(n.file);
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.HORIZONTAL);
        tab.setGravity(Gravity.CENTER_VERTICAL);
        tab.setPadding(dp(12), 0, dp(4), 0);
        tab.setMinimumHeight(dp(40));
        tab.setBackgroundColor(active ? Palette.BG : Palette.CARD);
        tab.setClickable(true);
        tab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openFile(n); }
        });

        String label = n.file.getName() + (active && modified ? " •" : "");
        TextView name = text(label, 13, active ? Palette.ACCENT : Palette.TEXT, active);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMaxWidth(dp(160));
        tab.addView(name);

        ImageView close = new ImageView(this);
        close.setImageDrawable(IconDrawable.of(this, I_CLOSE, Palette.SUB, dp(16)));
        int pad = dp(6);
        close.setPadding(pad, pad, pad, pad);
        close.setClickable(true);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeTab(n); }
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(28), dp(28));
        cl.leftMargin = dp(4);
        tab.addView(close, cl);
        return tab;
    }

    /** Tutup satu tab. Kalau yang ditutup sedang aktif, pindah ke tab di sebelahnya. */
    private void closeTab(Node n) {
        int idx = indexOfTab(n.file);
        if (idx < 0) return;
        boolean active = openedNode != null && openedNode.file.equals(n.file);
        if (!active) {
            openTabs.remove(idx);
            refreshTabs();
            return;
        }
        saveSilently();
        openTabs.remove(idx);
        if (openTabs.isEmpty()) closeFile();
        else openFile(openTabs.get(Math.max(0, idx - 1)));
    }


    // =====================================================================
    // Menu (Material 3)
    // =====================================================================

    private static final String PREFS_EDITOR = "jet_editor";
    private static final String PREF_SYNTAX_THEME = "syntax_theme";

    /**
     * Tema syntax yang dipakai: pilihan manual user bila ada, jika tidak otomatis mengikuti tema app
     * (gelap: Oren Dracula, terang: GitHub Light).
     */
    private String resolveSyntaxTheme() {
        String saved = getSharedPreferences(PREFS_EDITOR, MODE_PRIVATE).getString(PREF_SYNTAX_THEME, null);
        return saved != null ? saved : SyntaxAssets.autoTheme(Palette.dark);
    }

    /** Dialog M3 berisi daftar tema. Baris pertama "Otomatis" mengikuti tema app; tema lain disimpan sebagai pilihan manual. */
    private void showSyntaxThemeDialog() {
        final List<String> names = SyntaxAssets.themeNames(this);
        final String current = codeEditor.getThemeName();
        final boolean autoMode = getSharedPreferences(PREFS_EDITOR, MODE_PRIVATE)
                .getString(PREF_SYNTAX_THEME, null) == null;
        final Dialog[] dialogRef = new Dialog[1];

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        // Baris otomatis: swatch memakai tema yang akan dipilih otomatis.
        String autoName = SyntaxAssets.autoTheme(Palette.dark);
        list.addView(themeRow(autoName, LanguageLoader.get("syntax.auto"), autoMode, new Runnable() {
            public void run() {
                getSharedPreferences(PREFS_EDITOR, MODE_PRIVATE).edit().remove(PREF_SYNTAX_THEME).apply();
                codeEditor.setTheme(resolveSyntaxTheme());
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            }
        }), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (final String name : names) {
            final boolean selected = !autoMode && name.equals(current);
            list.addView(themeRow(name, SyntaxAssets.themeTitle(this, name), selected, new Runnable() {
                public void run() {
                    codeEditor.setTheme(name);
                    getSharedPreferences(PREFS_EDITOR, MODE_PRIVATE).edit()
                            .putString(PREF_SYNTAX_THEME, name).apply();
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                }
            }), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        dialogRef[0] = new M3Dialog(this)
                .title(LanguageLoader.get("menu.syntax"))
                .message(LanguageLoader.get("syntax.desc"))
                .view(list)
                .negative(LanguageLoader.get("common.close"), null)
                .show();
    }

    /** Satu baris pilihan tema: swatch (latar + keyword dari tema itu), label, dan tanda centang. */
    private View themeRow(String themeName, String label, boolean selected, final Runnable onPick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(12), 0);

        // swatch: warna latar + warna keyword dari tema itu sendiri
        ru.jet.editor.core.Theme t = SyntaxAssets.theme(this, themeName);
        View swatchBg = new View(this);
        GradientDrawable bgShape = new GradientDrawable();
        bgShape.setShape(GradientDrawable.RECTANGLE);
        bgShape.setCornerRadius(dp(6));
        bgShape.setColor(t.editor("background"));
        bgShape.setStroke(dp(1), Palette.withAlpha(Palette.TEXT, 0x33));
        swatchBg.setBackground(bgShape);
        row.addView(swatchBg, new LinearLayout.LayoutParams(dp(22), dp(22)));

        View swatchKw = new View(this);
        GradientDrawable kwShape = new GradientDrawable();
        kwShape.setShape(GradientDrawable.OVAL);
        kwShape.setColor(t.token("keyword"));
        LinearLayout.LayoutParams kwLp = new LinearLayout.LayoutParams(dp(10), dp(10));
        kwLp.leftMargin = dp(-14);
        swatchKw.setBackground(kwShape);
        row.addView(swatchKw, kwLp);

        TextView text = text(label, 16, selected ? Palette.ACCENT : Palette.TEXT, selected);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.leftMargin = dp(16);
        row.addView(text, labelLp);

        if (selected) {
            ImageView check = new ImageView(this);
            check.setImageResource(R.drawable.ic_check);
            check.setColorFilter(Palette.ACCENT);
            row.addView(check, new LinearLayout.LayoutParams(dp(20), dp(20)));
        }

        GradientDrawable selBg = new GradientDrawable();
        selBg.setCornerRadius(dp(16));
        selBg.setColor(selected ? Palette.withAlpha(Palette.ACCENT, 0x26) : Color.TRANSPARENT);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(Palette.RIPPLE), selBg, null));
        row.setMinimumHeight(dp(56));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onPick.run(); }
        });
        return row;
    }

    private void showEditorMenu(View anchor) {
        if (previewOn) {
            showPreviewMenu(anchor);
            return;
        }
        boolean has = openedNode != null;
        M3Menu menu = new M3Menu(this)
                .add(M_SAVE, I_SAVE, LanguageLoader.get("common.save"), has)
                .add(M_UNDO, I_UNDO, LanguageLoader.get("menu.undo"), has && codeEditor.canUndo())
                .add(M_REDO, I_REDO, LanguageLoader.get("menu.redo"), has && codeEditor.canRedo())
                .add(M_PRETTY, I_CODE, LanguageLoader.get("menu.pretty"), has)
                .add(M_FIND, I_SEARCH, LanguageLoader.get("menu.find_replace"), has)
                .add(M_SYNTAX, I_TUNE, LanguageLoader.get("menu.syntax"), true)
                .addCheck(M_WRAP, I_WRAP, LanguageLoader.get("menu.word_wrap"), codeEditor.isWordWrap())
                .add(M_CLOSE, I_CLOSE, LanguageLoader.get("menu.close_file"), has)
                .addDivider()
                .add(M_REFRESH, I_REFRESH, LanguageLoader.get("menu.refresh"))
                .add(M_COLLAPSE, I_COLLAPSE, LanguageLoader.get("menu.collapse"))
                .addCheck(M_HIDDEN, I_HIDDEN, LanguageLoader.get("menu.show_hidden"), showHidden);
        // "Setelan build" hanya muncul kalau saklar di Pengaturan aktif (default: mati).
        if (AppPrefs.showBuildSetting(this)) {
            menu.addDivider().add(M_BUILD_SETTING, I_BUILD, LanguageLoader.get("menu.build_setting"));
        }
        menu.setOnItemClickListener(new M3Menu.Listener() {
                    public void onItem(int id) {
                        switch (id) {
                            case M_BUILD_SETTING:
                                if (sheetHost != null) new BuildSettingSheet(ProjectActivity.this, sheetHost).show();
                                break;
                            case M_SAVE: saveFile(); break;
                            case M_UNDO: codeEditor.undo(); updateUndoButtons(); break;
                            case M_REDO: codeEditor.redo(); updateUndoButtons(); break;
                            case M_PRETTY: prettyPrint(); break;
                            case M_FIND: showFindBar(true); break;
                            case M_SYNTAX: showSyntaxThemeDialog(); break;
                            case M_WRAP: codeEditor.setWordWrap(!codeEditor.isWordWrap()); break;
                            case M_CLOSE: closeFile(); break;
                            case M_REFRESH: refreshTree(); break;
                            case M_COLLAPSE:
                                collapseAll(rootNode);
                                if (rootNode != null) expand(rootNode, true);
                                rebuildTree();
                                break;
                            case M_HIDDEN:
                                showHidden = !showHidden;
                                refreshTree();
                                break;
                        }
                    }
                })
                .show(anchor);
    }

    private void showNodeMenu(View anchor, final Node n) {
        M3Menu m = new M3Menu(this);
        if (!n.dir) m.add(M_OPEN, I_CODE, LanguageLoader.get("common.open"));
        m.add(M_NEW_FILE, I_FILE, LanguageLoader.get("menu.new_file"))
                .add(M_NEW_FOLDER, I_FOLDER_CLOSED, LanguageLoader.get("menu.new_folder"))
                .add(M_COPY_PATH, I_COPY, LanguageLoader.get("menu.copy_path"))
                .add(M_COPY_REL, I_LINK, LanguageLoader.get("menu.copy_rel"));
        if (n != rootNode) m.add(M_RENAME, I_RENAME, LanguageLoader.get("common.rename"));
        if (n != rootNode) m.addDanger(M_DELETE, I_DELETE, LanguageLoader.get("common.delete"));
        m.setOnItemClickListener(new M3Menu.Listener() {
                public void onItem(int id) {
                    if (id == M_OPEN) {
                        openFile(n);
                    } else if (id == M_NEW_FILE) {
                        promptNew(newEntryDir(n), false);
                    } else if (id == M_NEW_FOLDER) {
                        promptNew(newEntryDir(n), true);
                    } else if (id == M_RENAME) {
                        promptRename(n);
                    } else if (id == M_DELETE) {
                        confirmDelete(n);
                    } else {
                        String s = id == M_COPY_REL ? relativePath(n.file) : n.file.getAbsolutePath();
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("path", s));
                        M3.toast(ProjectActivity.this, LanguageLoader.get("project.copied"), Toast.LENGTH_SHORT);
                    }
                }
            })
            .show(anchor, Gravity.START, dp(48));
    }

    /** Folder tempat entri baru dibuat: folder itu sendiri, atau folder induk kalau yang dipilih file. */
    private File newEntryDir(Node n) {
        if (n.dir) return n.file;
        File parent = n.file.getParentFile();
        return parent != null ? parent : rootDir;
    }

    /** Dialog M3 untuk nama file/folder baru. Kolom memakai InoInputLayout (label mengambang, border aksen saat fokus). */
    private void promptNew(final File dir, final boolean folder) {
        if (dir == null) return;
        final InoInputLayout input = new InoInputLayout(this);
        input.setIsMultiline(false);
        input.setLabelText(folder ? LanguageLoader.get("project.name_folder") : LanguageLoader.get("project.name_file"));
        Dialog d = new M3Dialog(this)
                .icon(folder ? I_FOLDER_CLOSED : I_FILE)
                .title(folder ? LanguageLoader.get("new.folder_title") : LanguageLoader.get("new.file_title"))
                .message(LanguageLoader.get("project.in_dir", relativePath(dir)))
                .view(input)
                .negative(LanguageLoader.get("common.cancel"), null)
                .positive(LanguageLoader.get("common.create"), new M3Dialog.Action() {
                    public void run() {
                        createEntry(dir, input.getText().trim(), folder);
                    }
                })
                .show();
        // langsung siap diketik; keyboard ikut muncul
        Window w = d.getWindow();
        if (w != null) w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        input.getEditText().requestFocus();
    }

    /** Dialog ganti nama file/folder. Kolom sudah berisi nama sekarang dan langsung bisa diedit. */
    private void promptRename(final Node n) {
        if (n == null || n == rootNode) return;
        final InoInputLayout input = new InoInputLayout(this);
        input.setIsMultiline(false);
        input.setLabelText(n.dir ? LanguageLoader.get("project.name_folder") : LanguageLoader.get("project.name_file"));
        input.getEditText().setText(n.name);
        Dialog d = new M3Dialog(this)
                .icon(I_RENAME)
                .title(LanguageLoader.get("project.rename_title", n.name))
                .view(input)
                .negative(LanguageLoader.get("common.cancel"), null)
                .positive(LanguageLoader.get("common.rename"), new M3Dialog.Action() {
                    public void run() {
                        renameNode(n, input.getText().trim());
                    }
                })
                .show();
        Window w = d.getWindow();
        if (w != null) w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        input.getEditText().requestFocus();
        input.getEditText().selectAll();
    }

    private void renameNode(Node n, String newName) {
        if (newName.length() == 0 || newName.equals(".") || newName.equals("..")
                || newName.indexOf('/') >= 0 || newName.indexOf('\\') >= 0) {
            M3.toast(this, LanguageLoader.get("project.invalid_name"), Toast.LENGTH_SHORT);
            return;
        }
        if (newName.equals(n.name)) return;
        File dst = new File(n.file.getParentFile(), newName);
        if (dst.exists()) {
            M3.toast(this, LanguageLoader.get("project.exists", newName), Toast.LENGTH_SHORT);
            return;
        }
        // file yang sedang dibuka: simpan dan tutup dulu, lalu buka lagi dengan nama baru
        boolean reopen = !n.dir && openedNode != null && openedNode.file.equals(n.file);
        if (openedNode != null && isSameOrInside(openedNode.file, n.file)) closeFile();
        if (!n.file.renameTo(dst)) {
            M3.toast(this, LanguageLoader.get("project.rename_failed", n.name), Toast.LENGTH_SHORT);
            refreshTree();
            return;
        }
        M3.toast(this, LanguageLoader.get("project.renamed", newName), Toast.LENGTH_SHORT);
        refreshTree();
        reveal(dst);
        if (reopen) openFile(new Node(dst, newName, 0));
    }

    private void createEntry(File dir, String name, boolean folder) {
        if (name.length() == 0 || name.equals(".") || name.equals("..")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            M3.toast(this, LanguageLoader.get("project.invalid_name"), Toast.LENGTH_SHORT);
            return;
        }
        File f = new File(dir, name);
        if (f.exists()) {
            M3.toast(this, LanguageLoader.get("project.exists", name), Toast.LENGTH_SHORT);
            return;
        }
        boolean ok;
        if (folder) {
            ok = f.mkdirs();
        } else {
            try {
                ok = f.createNewFile();
            } catch (IOException e) {
                ok = false;
            }
        }
        if (!ok) {
            M3.toast(this, LanguageLoader.get("project.create_failed", name), Toast.LENGTH_SHORT);
            return;
        }
        refreshTree();
        reveal(f);                       // buka folder induk dan tampilkan entri baru
        if (!folder) openFile(new Node(f, name, 0));
    }

    private void confirmDelete(final Node n) {
        new M3Dialog(this)
                .title(LanguageLoader.get("project.delete_title", n.name))
                .message(n.dir ? LanguageLoader.get("project.delete_folder_msg")
                        : LanguageLoader.get("project.delete_file_msg"))
                .negative(LanguageLoader.get("common.cancel"), null)
                .positive(LanguageLoader.get("common.delete"), new M3Dialog.Action() {
                    public void run() {
                        deleteNode(n);
                    }
                }, true)
                .show();
    }

    private void deleteNode(Node n) {
        if (openedNode != null && isSameOrInside(openedNode.file, n.file)) {
            modified = false;            // jangan tulis ulang file yang baru dihapus
            closeFile();
        }
        boolean ok = deleteRecursive(n.file);
        M3.toast(this, ok ? LanguageLoader.get("project.deleted", n.name) : LanguageLoader.get("project.delete_failed", n.name), Toast.LENGTH_SHORT);
        refreshTree();
    }

    private static boolean isSameOrInside(File f, File dir) {
        String a = f.getAbsolutePath();
        String b = dir.getAbsolutePath();
        return a.equals(b) || a.startsWith(b + File.separator);
    }

    private static boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    if (!deleteRecursive(k)) return false;
                }
            }
        }
        return f.delete();
    }

    private String relativePath(File f) {
        if (rootDir == null) return f.getPath();
        String base = rootDir.getAbsolutePath();
        String full = f.getAbsolutePath();
        if (full.startsWith(base)) {
            String s = full.substring(base.length());
            while (s.startsWith(File.separator)) s = s.substring(1);
            return s.length() == 0 ? "." : s;
        }
        return full;
    }

    // =====================================================================
    // Tree model
    // =====================================================================

    private static class Node {
        final File file;
        final String name;
        final int depth;
        final boolean dir;
        boolean expanded;
        ArrayList<Node> children;
        String sub;          // hanya untuk hasil pencarian: folder induk relatif

        Node(File file, String name, int depth) {
            this.file = file;
            this.name = name;
            this.depth = depth;
            this.dir = file.isDirectory();
        }
    }

    private void expand(Node n, boolean value) {
        if (n == null || !n.dir) return;
        if (value && n.children == null) loadChildren(n);
        n.expanded = value;
    }

    private void loadChildren(Node n) {
        ArrayList<Node> list = new ArrayList<Node>();
        File[] files = n.file.listFiles();
        if (files == null) {
            n.children = list;
            return;
        }
        ArrayList<File> sorted = new ArrayList<File>();
        for (File f : files) {
            if (!showHidden && f.getName().startsWith(".")) continue;
            sorted.add(f);
        }
        Collections.sort(sorted, new Comparator<File>() {
            public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (File f : sorted) list.add(new Node(f, f.getName(), n.depth + 1));
        n.children = list;
    }

    private void rebuildTree() {
        if (searching) return;       // mode pencarian diisi oleh startSearch()
        visibleRows.clear();
        if (rootNode != null) flatten(rootNode);
        if (treeAdapter != null) treeAdapter.notifyDataSetChanged();
        updateTreeEmpty();
    }

    private void flatten(Node n) {
        visibleRows.add(n);
        if (n.expanded && n.children != null) {
            for (Node c : n.children) flatten(c);
        }
    }

    private void updateTreeEmpty() {
        if (treeEmpty == null) return;
        String msg = null;
        if (rootNode == null) msg = LanguageLoader.get("project.not_found");
        else if (searching && visibleRows.isEmpty()) msg = LanguageLoader.get("project.no_results");
        else if (!searching && rootNode.children != null && rootNode.children.isEmpty()) msg = LanguageLoader.get("project.empty_folder");
        treeEmpty.setText(msg == null ? "" : msg);
        treeEmpty.setVisibility(msg == null ? View.GONE : View.VISIBLE);
    }

    private void refreshTree() {
        HashSet<String> open = new HashSet<String>();
        collectExpanded(rootNode, open);
        rootNode = (rootDir == null || !rootDir.isDirectory()) ? null : new Node(rootDir, projectTitle, 0);
        restoreExpanded(rootNode, open);
        if (searching) onSearchChanged(); else rebuildTree();
    }

    private void collectExpanded(Node n, HashSet<String> out) {
        if (n == null || !n.expanded) return;
        out.add(n.file.getAbsolutePath());
        if (n.children != null) for (Node c : n.children) collectExpanded(c, out);
    }

    private void restoreExpanded(Node n, HashSet<String> open) {
        if (n == null || !n.dir) return;
        if (open.contains(n.file.getAbsolutePath()) || n == rootNode) {
            expand(n, true);
            if (n.children != null) for (Node c : n.children) if (c.dir) restoreExpanded(c, open);
        }
    }

    private void collapseAll(Node n) {
        if (n == null || n.children == null) return;
        for (Node c : n.children) {
            c.expanded = false;
            collapseAll(c);
        }
    }

    /** Buka semua folder induk sampai target terlihat di tree, lalu gulir ke sana. */
    private void reveal(File target) {
        if (rootNode == null) return;
        searchEdit.setText("");      // keluar dari mode pencarian (rebuild dilakukan onSearchChanged)
        Node cur = rootNode;
        expand(cur, true);
        String rel = relativePath(target);
        if (!".".equals(rel)) {
            String[] parts = rel.split("/");
            for (int i = 0; i < parts.length; i++) {
                Node next = null;
                if (cur.children != null) {
                    for (Node c : cur.children) {
                        if (c.name.equals(parts[i])) { next = c; break; }
                    }
                }
                if (next == null) break;
                cur = next;
                if (cur.dir) expand(cur, true);
            }
        }
        searching = false;
        rebuildTree();
        int idx = visibleRows.indexOf(cur);
        if (idx >= 0) treeList.setSelection(Math.max(0, idx - 2));
        clearSearchFocus();
    }

    // =====================================================================
    // Search (rekursif, di background)
    // =====================================================================

    private void onSearchChanged() {
        if (searchTask != null) handler.removeCallbacks(searchTask);
        final String q = searchEdit == null ? "" : searchEdit.getText().toString().trim().toLowerCase();
        if (q.length() == 0) {
            searchGen++;
            searching = false;
            rebuildTree();
            return;
        }
        searching = true;
        searchTask = new Runnable() {
            public void run() { startSearch(q); }
        };
        handler.postDelayed(searchTask, 180);
    }

    private void startSearch(final String q) {
        final int gen = ++searchGen;
        final File base = rootDir;
        final boolean hidden = showHidden;
        if (base == null) return;
        new Thread(new Runnable() {
            public void run() {
                final ArrayList<Node> out = new ArrayList<Node>();
                ArrayDeque<File> queue = new ArrayDeque<File>();
                queue.add(base);
                int visited = 0;
                outer:
                while (!queue.isEmpty() && visited < 20000) {
                    if (gen != searchGen) return;
                    File d = queue.poll();
                    File[] fs = d.listFiles();
                    if (fs == null) continue;
                    for (File f : fs) {
                        visited++;
                        String nm = f.getName();
                        if (!hidden && nm.startsWith(".")) continue;
                        if (f.isDirectory()) queue.add(f);
                        if (nm.toLowerCase().contains(q)) {
                            Node n = new Node(f, nm, 0);
                            File parent = f.getParentFile();
                            n.sub = parent == null ? "" : relativePath(parent);
                            out.add(n);
                            if (out.size() >= 300) break outer;
                        }
                    }
                }
                final String query = q;
                Collections.sort(out, new Comparator<Node>() {
                    public int compare(Node a, Node b) {
                        boolean sa = a.name.toLowerCase().startsWith(query);
                        boolean sb = b.name.toLowerCase().startsWith(query);
                        if (sa != sb) return sa ? -1 : 1;
                        if (a.dir != b.dir) return a.dir ? -1 : 1;
                        return a.name.compareToIgnoreCase(b.name);
                    }
                });
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (isFinishing() || gen != searchGen) return;
                        visibleRows.clear();
                        visibleRows.addAll(out);
                        treeAdapter.notifyDataSetChanged();
                        treeList.setSelection(0);
                        updateTreeEmpty();
                    }
                });
            }
        }).start();
    }

    // =====================================================================
    // Tree adapter & row
    // =====================================================================

    private class TreeAdapter extends BaseAdapter {
        public int getCount() { return visibleRows.size(); }
        public Object getItem(int position) { return visibleRows.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position, View convertView, ViewGroup parent) {
            TreeRow row = convertView instanceof TreeRow ? (TreeRow) convertView : new TreeRow();
            row.bind(visibleRows.get(position));
            return row;
        }
    }

    private class TreeRow extends LinearLayout {
        private final ImageView arrow;
        private final ImageView icon;
        private final TextView label;
        private final TextView sub;
        private final Paint guide = new Paint();
        private int guideDepth;
        private boolean flat;

        TreeRow() {
            super(ProjectActivity.this);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(44));
            setWillNotDraw(false);
            guide.setColor(Palette.withAlpha(Palette.TEXT, 0x1F));

            arrow = new ImageView(ProjectActivity.this);
            arrow.setScaleType(ImageView.ScaleType.CENTER);
            addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(44)));

            icon = new ImageView(ProjectActivity.this);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            addView(icon, new LinearLayout.LayoutParams(dp(24), dp(44)));

            LinearLayout texts = new LinearLayout(ProjectActivity.this);
            texts.setOrientation(VERTICAL);
            texts.setGravity(Gravity.CENTER_VERTICAL);

            label = text("", 15, Palette.TEXT, false);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            texts.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            sub = text("", 11, Palette.SUB, false);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.START);
            texts.addView(sub, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = dp(8);
            lp.rightMargin = dp(8);
            addView(texts, lp);
        }

        void bind(Node n) {
            boolean isRoot = n == rootNode && !searching;
            flat = searching;
            boolean active = openedNode != null && !n.dir && openedNode.file.equals(n.file);

            guideDepth = flat ? 0 : n.depth;
            setPadding(dp(6) + (flat ? dp(6) : n.depth * dp(16)), 0, dp(8), 0);

            GradientDrawable content = active ? round(Palette.withAlpha(Palette.ACCENT, 0x26), 14) : null;
            setBackground(new RippleDrawable(ColorStateList.valueOf(Palette.RIPPLE), content, round(0xFF000000, 14)));

            if (flat || !n.dir) {
                arrow.setVisibility(flat ? GONE : INVISIBLE);
            } else {
                arrow.setVisibility(VISIBLE);
                arrow.setImageDrawable(IconDrawable.of(ProjectActivity.this, I_CHEVRON, Palette.SUB, dp(18)));
                arrow.setRotation(n.expanded ? 90f : 0f);
            }

            if (n.dir) {
                int res = (n.expanded && !flat) ? I_FOLDER_OPEN : I_FOLDER_CLOSED;
                icon.setImageDrawable(IconDrawable.of(ProjectActivity.this, res,
                        isRoot ? Palette.ACCENT : Palette.SUB, dp(21)));
            } else {
                icon.setImageDrawable(IconDrawable.of(ProjectActivity.this, fileIcon(n.name),
                        fileColor(n.name), dp(20)));
            }

            label.setText(n.name);
            label.setTextColor(active || isRoot ? Palette.ACCENT : Palette.TEXT);
            boolean bold = isRoot || active;
            Typeface cur = label.getTypeface();
            if (cur == null || cur.isBold() != bold) {
                label.setTypeface(cur, bold ? Typeface.BOLD : Typeface.NORMAL);   // jaga font UI dari FontManager
            }

            if (flat && n.sub != null && n.sub.length() > 0 && !".".equals(n.sub)) {
                sub.setText(n.sub);
                sub.setVisibility(VISIBLE);
            } else {
                sub.setVisibility(GONE);
            }
        }

        /** Garis indent vertikal di tiap level induk. */
        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (flat) return;
            for (int k = 1; k < guideDepth; k++) {
                float x = dp(6) + k * dp(16) + dp(12);
                c.drawRect(x, 0, x + dp(1), getHeight(), guide);
            }
        }
    }

    private int fileIcon(String name) {
        String e = ext(name);
        if (e.equals("png") || e.equals("jpg") || e.equals("jpeg") || e.equals("webp")
                || e.equals("gif") || e.equals("bmp") || e.equals("ico")) return I_IMAGE;
        if (e.equals("java") || e.equals("kt") || e.equals("kts") || e.equals("xml") || e.equals("json")
                || e.equals("gradle") || e.equals("html") || e.equals("htm") || e.equals("css")
                || e.equals("js") || e.equals("ts") || e.equals("py") || e.equals("sh")
                || e.equals("c") || e.equals("h") || e.equals("cpp") || e.equals("smali")
                || e.equals("aidl") || e.equals("yml") || e.equals("yaml")) return I_CODE;
        return I_FILE;
    }

    private int fileColor(String name) {
        String e = ext(name);
        if (e.equals("java")) return 0xFFE8A04C;
        if (e.equals("kt") || e.equals("kts")) return 0xFFB39DDB;
        if (e.equals("xml")) return 0xFF7FB3E6;
        if (e.equals("json")) return 0xFFFFD54F;
        if (e.equals("gradle") || e.equals("properties")) return 0xFFA7B6C2;
        if (e.equals("html") || e.equals("htm") || e.equals("css") || e.equals("js") || e.equals("ts")) return 0xFF64B5F6;
        if (e.equals("png") || e.equals("jpg") || e.equals("jpeg") || e.equals("webp") || e.equals("gif")) return 0xFFF48FB1;
        if (e.equals("py") || e.equals("sh")) return 0xFFFFB74D;
        return Palette.SUB;
    }

    private String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
        }
    }

    private ImageView circleButton(int res) {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER);
        v.setImageDrawable(IconDrawable.of(this, res, Palette.TEXT, dp(24)));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Palette.CARD);
        v.setBackground(bg);
        v.setClickable(true);
        return v;
    }

    // ===== Cari & ganti: bar di atas editor =====

    private LinearLayout buildFindBar() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Palette.CARD);
        col.setPadding(dp(8), dp(6), dp(8), dp(8));

        // baris 1: cari, jumlah hasil, sebelumnya, berikutnya, tutup
        LinearLayout row1 = new LinearLayout(this);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        findEdit = findField(LanguageLoader.get("find.hint"));
        findEdit.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        findEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent e) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) { findStep(true); return true; }
                return false;
            }
        });
        findEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) {}
            public void afterTextChanged(Editable s) { updateFindStatus(); }
        });
        row1.addView(findEdit, new LinearLayout.LayoutParams(0, dp(42), 1f));

        findStatus = text("", 12, Palette.SUB, false);
        findStatus.setPadding(dp(8), 0, dp(2), 0);
        row1.addView(findStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));

        // chevron menghadap kanan; "sebelumnya" diputar 180 derajat
        ImageView prev = findIcon(R.drawable.ic_chevron, new View.OnClickListener() {
            public void onClick(View v) { findStep(false); }
        });
        prev.setRotation(180f);
        row1.addView(prev, new LinearLayout.LayoutParams(dp(40), dp(40)));
        row1.addView(findIcon(R.drawable.ic_chevron, new View.OnClickListener() {
            public void onClick(View v) { findStep(true); }
        }), new LinearLayout.LayoutParams(dp(40), dp(40)));
        row1.addView(findIcon(R.drawable.ic_close, new View.OnClickListener() {
            public void onClick(View v) { showFindBar(false); }
        }), new LinearLayout.LayoutParams(dp(40), dp(40)));
        col.addView(row1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // baris 2: ganti dengan, ganti, semua
        LinearLayout row2 = new LinearLayout(this);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        replaceEdit = findField(LanguageLoader.get("find.replace_hint"));
        row2.addView(replaceEdit, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        bl.leftMargin = dp(6);
        row2.addView(findText(LanguageLoader.get("find.replace"), new View.OnClickListener() {
            public void onClick(View v) { replaceCurrent(); }
        }), bl);
        LinearLayout.LayoutParams bl2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        bl2.leftMargin = dp(6);
        row2.addView(findText(LanguageLoader.get("find.replace_all"), new View.OnClickListener() {
            public void onClick(View v) { replaceAll(); }
        }), bl2);
        LinearLayout.LayoutParams r2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2.topMargin = dp(6);
        col.addView(row2, r2);
        return col;
    }

    private EditText findField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Palette.SUB);
        e.setTextColor(Palette.TEXT);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setBackground(round(Palette.DRAWER, 12));
        e.setPadding(dp(12), 0, dp(12), 0);
        return e;
    }

    private ImageView findIcon(int res, View.OnClickListener l) {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER);
        v.setImageDrawable(IconDrawable.of(this, res, Palette.TEXT, dp(20)));
        v.setBackground(ripple(round(0x00000000, 20), 20));
        v.setClickable(true);
        v.setOnClickListener(l);
        return v;
    }

    private TextView findText(String label, View.OnClickListener l) {
        TextView t = text(label, 13, Palette.ACCENT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), 0, dp(12), 0);
        t.setBackground(ripple(round(Palette.DRAWER, 12), 12));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    private void showFindBar(boolean show) {
        if (findHost == null) return;
        if (show && openedNode == null) {
            M3.toast(this, LanguageLoader.get("project.pick_file"), Toast.LENGTH_SHORT);
            return;
        }
        findHost.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) clearFindHighlights();
        if (show) {
            // teks yang sedang dipilih di editor langsung jadi kata cari (satu baris saja)
            int s = codeEditor.getSelectionStart(), e = codeEditor.getSelectionEnd();
            if (s >= 0 && e > s && e - s <= 80) {
                String sel = codeEditor.getText().subSequence(s, e).toString();
                if (sel.indexOf('\n') < 0) findEdit.setText(sel);
            }
            findEdit.requestFocus();
            findEdit.setSelection(findEdit.length());
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(findEdit, InputMethodManager.SHOW_IMPLICIT);
            updateFindStatus();
        } else {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(findEdit.getWindowToken(), 0);
            codeEditor.requestFocus();
        }
    }

    /** Semua kecocokan (tanpa membedakan huruf besar kecil) di file yang sedang dibuka. */
    private List<int[]> findMatches() {
        List<int[]> out = new ArrayList<int[]>();
        if (openedNode == null || findEdit == null || codeEditor == null) return out;
        String q = findEdit.getText().toString();
        if (q.length() == 0) return out;
        Matcher m = Pattern.compile(Pattern.quote(q), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(codeEditor.getText());
        while (m.find()) out.add(new int[]{m.start(), m.end()});
        return out;
    }

    private void updateFindStatus() {
        if (findStatus == null || findEdit == null) return;
        if (findEdit.length() == 0) {
            findStatus.setText("");
            clearFindHighlights();
            return;
        }
        List<int[]> ms = findMatches();
        findStatus.setText(ms.isEmpty()
                ? LanguageLoader.get("find.none")
                : LanguageLoader.get("find.count", ms.size()));
        highlightMatches(ms);
    }

    /** Sorot kuning untuk semua kecocokan; kecocokan yang sedang dipilih berwarna oranye. */
    private void highlightMatches(List<int[]> ms) {
        clearFindHighlights();
        if (codeEditor == null || codeEditor.getText() == null) return;
        Editable ed = codeEditor.getText();
        int selS = codeEditor.getSelectionStart();
        int selE = codeEditor.getSelectionEnd();
        for (int[] m : ms) {
            boolean current = m[0] == selS && m[1] == selE;
            ed.setSpan(current ? new CurrentFindSpan() : new FindSpan(),
                    m[0], m[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void clearFindHighlights() {
        if (codeEditor == null || codeEditor.getText() == null) return;
        Editable ed = codeEditor.getText();
        for (FindSpan sp : ed.getSpans(0, ed.length(), FindSpan.class)) ed.removeSpan(sp);
        for (CurrentFindSpan sp : ed.getSpans(0, ed.length(), CurrentFindSpan.class)) ed.removeSpan(sp);
    }

    /** Warna sorotan find & replace. */
    private static final class FindSpan extends BackgroundColorSpan {
        FindSpan() { super(0x66FFEB3B); }
    }

    private static final class CurrentFindSpan extends BackgroundColorSpan {
        CurrentFindSpan() { super(0xCCFF9800); }
    }

    /** Pilih kecocokan berikutnya (atau sebelumnya), berputar di ujung file. */
    private void findStep(boolean forward) {
        List<int[]> ms = findMatches();
        if (ms.isEmpty()) { updateFindStatus(); return; }
        int selStart = codeEditor.getSelectionStart();
        int selEnd = codeEditor.getSelectionEnd();
        int[] pick = null;
        if (forward) {
            for (int[] m : ms) if (m[0] >= selEnd) { pick = m; break; }
            if (pick == null) pick = ms.get(0);
        } else {
            for (int i = ms.size() - 1; i >= 0; i--) if (ms.get(i)[1] <= selStart) { pick = ms.get(i); break; }
            if (pick == null) pick = ms.get(ms.size() - 1);
        }
        codeEditor.setSelection(pick[0], pick[1]);
        codeEditor.ensureCursorVisible();
        updateFindStatus();
    }

    /** Ganti kecocokan yang sedang dipilih; kalau belum ada yang dipilih, pilih kecocokan berikutnya dulu. */
    private void replaceCurrent() {
        if (openedNode == null || findEdit.length() == 0) return;
        int s = codeEditor.getSelectionStart();
        int e = codeEditor.getSelectionEnd();
        if (s >= 0 && e > s
                && codeEditor.getText().subSequence(s, e).toString().equalsIgnoreCase(findEdit.getText().toString())) {
            String repl = replaceEdit.getText().toString();
            codeEditor.getText().replace(s, e, repl);
            codeEditor.setSelection(s + repl.length());
        }
        findStep(true);
    }

    /** Ganti semua kecocokan dalam satu perubahan (satu langkah undo). */
    private void replaceAll() {
        if (openedNode == null) return;
        List<int[]> ms = findMatches();
        if (ms.isEmpty()) { updateFindStatus(); return; }
        String text = codeEditor.getText().toString();
        String repl = replaceEdit.getText().toString();
        StringBuilder sb = new StringBuilder(text.length() + ms.size() * repl.length());
        int last = 0;
        for (int[] m : ms) {
            sb.append(text, last, m[0]).append(repl);
            last = m[1];
        }
        sb.append(text, last, text.length());
        Editable ed = codeEditor.getText();
        ed.replace(0, ed.length(), sb.toString());
        M3.toast(this, LanguageLoader.get("find.replaced", ms.size()), Toast.LENGTH_SHORT);
        updateFindStatus();
    }

    // ===== Preview layout XML (layar penuh di editor, bukan dialog) =====

    private boolean isLayoutNode(Node n) {
        if (n == null || n.dir) return false;
        File parent = n.file.getParentFile();
        return parent != null && "layout".equals(parent.getName()) && n.file.getName().endsWith(".xml");
    }

    /** Tombol di toolbar: image = buka preview, code = kembali ke editor kode. */
    private void togglePreview() {
        if (previewOn) {
            setPreviewMode(false);
            return;
        }
        if (openedNode == null || rootNode == null) {
            M3.toast(this, LanguageLoader.get("project.pick_file"), Toast.LENGTH_SHORT);
            return;
        }
        if (!isLayoutNode(openedNode)) {
            M3.toast(this, LanguageLoader.get("preview.not_layout"), Toast.LENGTH_SHORT);
            return;
        }
        setPreviewMode(true);
    }

    private void setPreviewMode(boolean on) {
        previewOn = on;
        previewHandler.removeCallbacks(previewRefresh);
        previewButton.setImageDrawable(IconDrawable.of(this, on ? I_CODE : I_IMAGE, Palette.ACCENT, dp(24)));
        if (on) {
            hideKeyboard();
            codeEditor.setVisibility(View.GONE);
            quickHost.setVisibility(View.GONE);
            if (findHost != null) findHost.setVisibility(View.GONE);
            previewHost.setVisibility(View.VISIBLE);
            renderPreview(true);
        } else {
            previewHost.removeAllViews();
            previewSurface = null;
            previewHost.setVisibility(View.GONE);
            if (openedNode != null) {
                codeEditor.setVisibility(View.VISIBLE);
                quickHost.setVisibility(View.VISIBLE);
                codeEditor.requestFocus();
            }
        }
    }

    /** Folder yang berisi res/ proyek (app/src/main pada proyek Android standar). */
    private File previewRoot() {
        File main = new File(rootNode.file, "app/src/main");
        return new File(main, "res").isDirectory() ? main : rootNode.file;
    }

    /**
     * Inflate isi editor saat ini (termasuk yang belum disimpan) dan tampilkan di bingkai HP.
     * refit = true menghitung ulang zoom; false mempertahankan zoom dan posisi saat mengetik.
     */
    private void renderPreview(boolean refit) {
        if (openedNode == null || rootNode == null) return;
        if (previewSurface == null) {
            previewSurface = new PreviewSurface(this);
            previewHost.removeAllViews();
            previewHost.addView(previewSurface, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            refit = true;
        }
        String xml = codeEditor.getText().toString();
        try {
            LayoutPreview lp = new LayoutPreview(this, previewRoot());
            View content = lp.inflateXml(xml);
            previewSurface.setContent(content, lp.idNames(), AppPrefs.previewSize(this), refit);
            previewSurface.setDebug(previewBounds, previewIds, previewValues);
        } catch (Exception e) {
            previewSurface.setError(LanguageLoader.get("preview.failed", String.valueOf(e.getMessage())));
        }
    }

    /** Menu titik tiga saat mode preview: hanya pilihan tampilan debug. */
    private void showPreviewMenu(View anchor) {
        new M3Menu(this)
                .addCheck(M_P_BOUNDS, I_BOUNDS, LanguageLoader.get("preview.show_bounds"), previewBounds)
                .addCheck(M_P_IDS, I_IDS, LanguageLoader.get("preview.show_id"), previewIds)
                .addCheck(M_P_VALUES, I_TUNE, LanguageLoader.get("preview.show_values"), previewValues)
                .addDivider()
                .add(M_P_SIZE, I_IMAGE, LanguageLoader.get("preview.size_menu"))
                .setOnItemClickListener(new M3Menu.Listener() {
                    public void onItem(int id) {
                        if (id == M_P_SIZE) {
                            if (sheetHost != null) {
                                PreviewSizeSheet.show(ProjectActivity.this, sheetHost, new Runnable() {
                                    public void run() { renderPreview(true); }
                                });
                            }
                            return;
                        }
                        if (id == M_P_BOUNDS) previewBounds = !previewBounds;
                        else if (id == M_P_IDS) previewIds = !previewIds;
                        else if (id == M_P_VALUES) previewValues = !previewValues;
                        if (previewSurface != null) {
                            previewSurface.setDebug(previewBounds, previewIds, previewValues);
                        }
                    }
                })
                .show(anchor);
    }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private RippleDrawable ripple(GradientDrawable content, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(Palette.RIPPLE), content, round(0xFF000000, radius));
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
