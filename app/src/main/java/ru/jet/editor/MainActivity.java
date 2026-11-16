package ru.jet.editor;

import ru.jet.editor.core.M3;
import ru.jet.editor.core.AppPrefs;
import ru.jet.editor.core.BackupName;
import ru.jet.editor.core.LanguageLoader;
import ru.jet.editor.core.Palette;
import ru.jet.editor.core.ThemeTransition;
import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import ru.jet.editor.activity.ProjectActivity;
import ru.jet.editor.activity.SettingActivity;
import ru.jet.editor.container.BottomSheet;
import ru.jet.editor.container.KeystoreSheet;
import ru.jet.editor.container.ProjectSheet;
import ru.jet.editor.core.SimpleSwipeRefreshLayout;
import ru.jet.editor.core.FontManager;
import ru.jet.editor.core.IconDrawable;

/**
* Jet Editor - project manager (Activity utama).
* Full programmatic UI: tanpa XML, tanpa androidx, tanpa AppCompat.
*
* Manifest yang dibutuhkan:
*   <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
*   <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
*   <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
*   <application android:requestLegacyExternalStorage="true"
*                android:theme="@android:style/Theme.Material.NoActionBar" ...>
*
* Gambar judul drawer: taruh drawable bernama "drawer_title" (res/drawable/drawer_title.png/xml).
* Kalau belum ada, otomatis tampil teks "JetEdit".
*/
public class MainActivity extends Activity {
    private int appliedSignature;
    private String appliedLang;
	
	// ===== Warna =====
	// ===== Icon (instance) =====
	// Tambah icon baru: taruh file res/drawable/ic_nama.xml (fillColor putih),
	// lalu tambah satu baris di sini. Pakai: icon(I_NAMA, warna, dp(ukuran)).
	// Hapus icon: hapus barisnya + file xml-nya.
	private static final int I_MENU = R.drawable.ic_menu;
	private static final int I_SEARCH = R.drawable.ic_search;
	private static final int I_MORE = R.drawable.ic_more;
	private static final int I_FOLDER = R.drawable.ic_folder;
	private static final int I_HISTORY = R.drawable.ic_history;
	private static final int I_ADD = R.drawable.ic_add;
	private static final int I_INFO = R.drawable.ic_info;
	private static final int I_SETTINGS = R.drawable.ic_settings;
    private static final int I_TERMINAL = R.drawable.ic_terminal;
	private static final int I_KEY = R.drawable.ic_key;
	private static final int I_DISCORD = R.drawable.ic_discord;
	private static final int I_GITHUB = R.drawable.ic_github;
	private static final int I_TELEGRAM = R.drawable.ic_telegram;
	private static final int I_COPY = R.drawable.ic_copy;
	private static final int I_BACKUP = R.drawable.ic_backup;
	private static final int I_TUNE = R.drawable.ic_tune;
	private static final int I_DELETE = R.drawable.ic_delete;
	private static final int I_CHEVRON = R.drawable.ic_chevron;
	
	private IconDrawable icon(int res, int color, int size) {
		return IconDrawable.of(this, res, color, size);
	}
	
	// ===== Link (ganti sendiri) =====
	private static final String URL_DISCORD = "https://discord.gg/u7QPpQD7QC";
	private static final String URL_GITHUB = "https://github.com/IqwanXD/";
	private static final String URL_TELEGRAM = "https://t.me/IqwanoINO/";
	
	private static final String DRAWER_TITLE_RES = "drawer_title";
	
	private static final int REQ_RUNTIME = 101;
	private static final int REQ_ALL_FILES = 102;
	private static final int REQ_PICK_ICON = 103;
	
	// ===== View =====
	private FrameLayout root, content, listFrame;
	private LinearLayout drawer, mainCol, fabCol, fabRestore, fabCreate;
	private FrameLayout.LayoutParams fabColLp;
	private View fabScrim;
	private DrawerLayoutView drawerLayout;
	private BottomSheet.Host sheetHost;
	private ProjectSheet projectSheet;
	private ListView listView;
	private SimpleSwipeRefreshLayout swipe;
	private TextView emptyText;
	private EditText searchEdit;
	
	// ===== State =====
	private ProjectAdapter adapter;
	private final List<Project> all = new ArrayList<Project>();
	private final List<Project> shown = new ArrayList<Project>();
	private boolean fabOpen;
	private int drawerW;
	// Window insets (edge-to-edge): status bar / nav bar / cutout / keyboard
	private int insTop, insBottom, insLeft, insRight, imeBottom;
	
	// ===== Model =====
	static class Project {
		File dir;
		String title;
		String sub;
		String pkg;
		int id;
		Bitmap icon;     // icon launcher dari res project (null = pakai gambar default)
	}
	
	// =====================================================================
	// Lifecycle
	// =====================================================================
	
	@Override
	protected void onCreate(android.os.Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        LanguageLoader.init(this);
        appliedLang = LanguageLoader.current();
        Palette.load(this);
        appliedSignature = Palette.signature();
        ThemeTransition.track(this);
        ThemeTransition.play(this);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		drawerW = (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
		buildUi();
		setupEdgeToEdge();
		if (hasAll()) {
			ensureFolders();
			loadProjects();
		} else {
			updateEmpty();
			showSheet();
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
        Palette.load(this);
        if (Palette.signature() != appliedSignature) rebuildInPlace();
        if (!LanguageLoader.current().equals(appliedLang)) { recreate(); return; }
		if (hasAll()) {
			hideSheet();
			ensureFolders();
			loadProjects();
		} else {
			refreshSheetStatus();
		}
	}
	
	/** Ketuk di luar kolom search: lepas fokus + tutup keyboard. */
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
		if (sheetHost != null && sheetHost.dismissTop()) {
			return;
		} else if (fabOpen) {
			setFab(false);
		} else if (drawerLayout != null && drawerLayout.isOpen()) {
			drawerLayout.close();
		} else {
			super.onBackPressed();
		}
	}
	
	// =====================================================================
	// Edge-to-edge (tanpa androidx)
	// =====================================================================
	
	/**
* Status bar & navigation bar transparan, konten digambar sampai ke tepi layar.
* Karena bar transparan, warna di balik bar = apa yang sedang tampil di layar:
* saat drawer terbuka, area bar ikut berwarna drawer (Palette.DRAWER), bukan warna utama.
*/	
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
			w.setNavigationBarContrastEnforced(false);   // tanpa scrim abu-abu di 3-button nav
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
			b = wi.getStableInsetBottom();               // tinggi nav bar saja
			int sb = wi.getSystemWindowInsetBottom();    // nav bar atau keyboard
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
		if (drawer != null) {
			drawer.setPadding(dp(24) + insLeft, dp(20) + insTop, dp(24), dp(20) + insBottom);
		}
		if (mainCol != null) mainCol.setPadding(insLeft, insTop, insRight, 0);
		if (listView != null) listView.setPadding(dp(12), dp(8), dp(12), dp(96) + insBottom);
		if (fabCol != null && fabColLp != null) {
			fabColLp.rightMargin = dp(16) + insRight;
			fabColLp.bottomMargin = dp(16) + insBottom;
			fabCol.setLayoutParams(fabColLp);
		}
		if (sheetHost != null) {
			sheetHost.setInsets(insLeft, insRight, insBottom, imeBottom);
			sheetHost.setTopInset(insTop);
		}
	}
	
	// =====================================================================
	// Permission + folder
	// =====================================================================
	
	private boolean runtimeGranted() {
		if (Build.VERSION.SDK_INT < 23) return true;
		return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
		&& checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
	}
	
	private boolean rwGranted() {
		if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
		return runtimeGranted();
	}
	
	private boolean allFilesGranted() {
		if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
		return true;
	}
	
	private boolean hasAll() {
		return rwGranted() && allFilesGranted();
	}
	
	private void requestAllPermissions() {
		if (Build.VERSION.SDK_INT >= 23 && !runtimeGranted()) {
			requestPermissions(new String[]{
				Manifest.permission.READ_EXTERNAL_STORAGE,
				Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_RUNTIME);
		} else {
			requestAllFiles();
		}
	}
	
	private void requestAllFiles() {
		if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
			try {
				Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
				Uri.parse("package:" + getPackageName()));
				startActivityForResult(i, REQ_ALL_FILES);
			} catch (Exception e) {
				Intent i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
				startActivityForResult(i, REQ_ALL_FILES);
			}
			return;
		}
		onPermissionsReady();
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == REQ_RUNTIME) requestAllFiles();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQ_ALL_FILES) onPermissionsReady();
		if (requestCode == REQ_PICK_ICON && resultCode == RESULT_OK && data != null && data.getData() != null) {
			Bitmap b = decodeIconSource(data.getData());
			if (b == null) toast(LanguageLoader.get("image.read_failed"));
			else projectSheet.setPickedIcon(b);
		}
	}
	
	private void onPermissionsReady() {
		if (hasAll()) {
			hideSheet();
			ensureFolders();
			loadProjects();
		} else {
			refreshSheetStatus();
		}
	}
	
	private File rootDir() {
		return new File(Environment.getExternalStorageDirectory(), ".JetEdit");
	}
	
	private File projectsDir() {
		return new File(rootDir(), "projects");
	}
	
	private File backupDir() {
		return new File(rootDir(), "backup");
	}
	
	private boolean ensureFolders() {
		File r = rootDir();
		boolean ok = r.isDirectory() || r.mkdirs();
		File b = backupDir();
		File p = projectsDir();
		ok = (b.isDirectory() || b.mkdirs()) && ok;
		ok = (p.isDirectory() || p.mkdirs()) && ok;
		return ok;
	}
	
	// =====================================================================
	// UI builder
	// =====================================================================
	
	/** Tema/warna berubah: bangun ulang isi layar ini di tempat (tanpa recreate), lalu pudarkan layar lama. */
	private void rebuildInPlace() {
		android.graphics.Bitmap snap = ru.jet.editor.core.ThemeSwap.snapshot(this);
		appliedSignature = Palette.signature();
		buildUi();
		setupEdgeToEdge();
		ru.jet.editor.core.ThemeSwap.fadeOut(this, snap);
	}

	private void buildUi() {
		root = new FrameLayout(this);
		sheetHost = new BottomSheet.Host(this, root);
		projectSheet = new ProjectSheet(this, sheetHost);
		root.setBackgroundColor(Palette.DRAWER);
		buildDrawer();
		buildContent();
		drawerLayout = new DrawerLayoutView(this);
		drawerLayout.setViews(content, drawer);
		root.addView(drawerLayout, new FrameLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		setContentView(root);
		FontManager.attach(root);
		root.setFocusableInTouchMode(true);   // supaya search tidak auto-fokus
		root.requestFocus();
		applyInsets();
	}
	
	// ----- Drawer -----
	
	private void buildDrawer() {
		drawer = new LinearLayout(this);
		drawer.setOrientation(LinearLayout.VERTICAL);
		drawer.setBackgroundColor(Palette.DRAWER);
		drawer.setPadding(dp(24), dp(20), dp(24), dp(20));
		drawer.setClickable(true);
		
		int resId = getResources().getIdentifier(DRAWER_TITLE_RES, "drawable", getPackageName());
		if (resId != 0) {
			ImageView title = new ImageView(this);
			title.setImageResource(resId);
			title.setAdjustViewBounds(true);
			title.setScaleType(ImageView.ScaleType.FIT_START);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
			lp.bottomMargin = dp(20);
			drawer.addView(title, lp);
		} else {
			TextView title = new TextView(this);
			title.setText(LanguageLoader.get("drawer.title"));
			title.setTextColor(Palette.ACCENT);
			title.setTextSize(34);
			title.setTypeface(Typeface.DEFAULT_BOLD);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, dp(56));
			lp.bottomMargin = dp(20);
			title.setGravity(Gravity.CENTER_VERTICAL);
			drawer.addView(title, lp);
		}
		
		drawer.addView(drawerItem(I_INFO, LanguageLoader.get("common.information"), new Runnable() {
			public void run() { showInformation(); }
		}));
		drawer.addView(drawerItem(I_SETTINGS, LanguageLoader.get("common.settings"), new Runnable() {
			public void run() {
				startActivity(new Intent(MainActivity.this, SettingActivity.class));
			}
		}));
        drawer.addView(drawerItem(I_TERMINAL, LanguageLoader.get("drawer.terminal"), new Runnable() {
			public void run() {
                // Isi fungsi
            }
		}));
		
		drawer.addView(drawerItem(I_KEY, LanguageLoader.get("drawer.keystore"), new Runnable() {
			public void run() {
				new KeystoreSheet(MainActivity.this, sheetHost).show();
			}
		}));
		
		View div = new View(this);
		div.setBackgroundColor(Palette.DIVIDER);
		LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
		dl.topMargin = dp(16);
		dl.bottomMargin = dp(16);
		drawer.addView(div, dl);
		
		TextView related = new TextView(this);
		related.setText(LanguageLoader.get("drawer.related"));
		related.setTextColor(Palette.SUB);
		related.setTextSize(14);
		related.setTypeface(Typeface.DEFAULT_BOLD);
		related.setPadding(0, 0, 0, dp(8));
		drawer.addView(related);
		
		drawer.addView(drawerItem(I_DISCORD, LanguageLoader.get("drawer.discord"), new Runnable() {
			public void run() { openUrl(URL_DISCORD); }
		}));
		drawer.addView(drawerItem(I_GITHUB, LanguageLoader.get("drawer.github"), new Runnable() {
			public void run() { openUrl(URL_GITHUB); }
		}));
		drawer.addView(drawerItem(I_TELEGRAM, LanguageLoader.get("drawer.telegram"), new Runnable() {
			public void run() { openUrl(URL_TELEGRAM); }
		}));
		
	}
	
	private View drawerItem(int iconType, String text, final Runnable action) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(0, dp(14), 0, dp(14));
		row.setClickable(true);
		row.setBackground(ripple(null, 12));
		
		ImageView icon = new ImageView(this);
		icon.setImageDrawable(icon(iconType, Palette.SUB, dp(26)));
		row.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));
		
		TextView t = new TextView(this);
		t.setText(text);
		t.setTextColor(Palette.TEXT);
		t.setTextSize(16);
		LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		tl.leftMargin = dp(20);
		row.addView(t, tl);
		
		row.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				drawerLayout.close();
				action.run();
			}
		});
		return row;
	}
	
	// ----- Content -----
	
	private void buildContent() {
		content = new FrameLayout(this);
		content.setBackgroundColor(Palette.BG);
		
		LinearLayout main = new LinearLayout(this);
		mainCol = main;
		main.setOrientation(LinearLayout.VERTICAL);
		
		main.addView(buildTopBar(), new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		
		listFrame = new FrameLayout(this);
		main.addView(listFrame, new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
		
		listView = new ListView(this);
		listView.setDivider(new ColorDrawable(Color.TRANSPARENT));
		listView.setDividerHeight(dp(6));
		listView.setSelector(new ColorDrawable(Color.TRANSPARENT));
		listView.setPadding(dp(12), dp(8), dp(12), dp(96) + insBottom);
		listView.setClipToPadding(false);
		listView.setVerticalScrollBarEnabled(false);
		listView.setOverScrollMode(View.OVER_SCROLL_NEVER);
		listView.setVerticalFadingEdgeEnabled(true);   // fade atas & bawah
		listView.setFadingEdgeLength(dp(40));
		adapter = new ProjectAdapter();
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				openProject(shown.get(position));
			}
		});
		swipe = new SimpleSwipeRefreshLayout(this);
		swipe.addView(listView, new FrameLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		swipe.setTargetView(listView);
		swipe.setOnRefreshListener(new SimpleSwipeRefreshLayout.OnRefreshListener() {
			public void onRefresh() {
				if (hasAll()) loadProjects();
				swipe.setRefreshing(false);
			}
		});
		listFrame.addView(swipe, new FrameLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		
		emptyText = new TextView(this);
		emptyText.setTextColor(Palette.SUB);
		emptyText.setTextSize(15);
		emptyText.setGravity(Gravity.CENTER);
		emptyText.setVisibility(View.GONE);
		FrameLayout.LayoutParams el = new FrameLayout.LayoutParams(
		ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
		listFrame.addView(emptyText, el);
		
		content.addView(main, new FrameLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		
		buildFab();
		
	}
	
	private View buildTopBar() {
		LinearLayout bar = new LinearLayout(this);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		bar.setPadding(dp(12), dp(12), dp(12), dp(4));
		
		ImageView menu = circleButton(I_MENU);
		menu.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				drawerLayout.toggle();
			}
		});
		bar.addView(menu, new LinearLayout.LayoutParams(dp(52), dp(52)));
		
		searchEdit = new EditText(this);
		searchEdit.setHint(LanguageLoader.get("main.search_hint"));
		searchEdit.setHintTextColor(Palette.SUB);
		searchEdit.setTextColor(Palette.TEXT);
		searchEdit.setTextSize(17);
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
			public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
			public void onTextChanged(CharSequence s, int a, int b, int c) {}
			public void afterTextChanged(Editable s) { applyFilter(s.toString()); }
		});
		LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, dp(52), 1f);
		sl.leftMargin = dp(8);
		sl.rightMargin = dp(8);
		bar.addView(searchEdit, sl);
		
		ImageView search = circleButton(I_SEARCH);
		search.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (searchEdit.getText().length() == 0) {
					searchEdit.requestFocus();
					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					if (imm != null) imm.showSoftInput(searchEdit, 0);
				} else {
					clearSearchFocus();
				}
			}
		});
		bar.addView(search, new LinearLayout.LayoutParams(dp(52), dp(52)));
		return bar;
	}
	
	private ImageView circleButton(int iconType) {
		ImageView v = new ImageView(this);
		v.setScaleType(ImageView.ScaleType.CENTER);
		v.setImageDrawable(icon(iconType, Palette.TEXT, dp(24)));
		GradientDrawable g = new GradientDrawable();
		g.setShape(GradientDrawable.OVAL);
		g.setColor(Palette.CARD);
		v.setBackground(g);
		v.setClickable(true);
		return v;
	}
	
	// ----- FAB -----
	
	private static final int FAB_GAP = 16;   // jarak antar item FAB (dp)
	
	private void buildFab() {
		fabScrim = new View(this);
		fabScrim.setBackgroundColor(0x66000000);
		fabScrim.setClickable(true);
		fabScrim.setVisibility(View.GONE);
		fabScrim.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) { setFab(false); }
		});
		content.addView(fabScrim, new FrameLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		
		fabCol = new LinearLayout(this);
		fabCol.setOrientation(LinearLayout.VERTICAL);
		fabCol.setGravity(Gravity.END);
		
		fabRestore = fabRow(LanguageLoader.get("main.restore"), I_HISTORY, new View.OnClickListener() {
			public void onClick(View v) {
				setFab(false);
				if (!hasAll()) { showSheet(); return; }
				showRestoreDialog();
			}
		});
		fabCreate = fabRow(LanguageLoader.get("common.create"), I_ADD, new View.OnClickListener() {
			public void onClick(View v) {
				setFab(false);
				if (!hasAll()) { showSheet(); return; }
				showCreateSheet();
			}
		});
		fabRestore.setVisibility(View.GONE);
		fabCreate.setVisibility(View.GONE);
		
		ImageView main = fabButton(I_FOLDER);
		main.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) { setFab(!fabOpen); }
		});
		
		// Margin harus dipasang di LayoutParams saat addView (kalau tidak, ditimpa)
		LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		l1.bottomMargin = dp(FAB_GAP);
		LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		l2.bottomMargin = dp(FAB_GAP);
		fabCol.addView(fabRestore, l1);
		fabCol.addView(fabCreate, l2);
		fabCol.addView(main, new LinearLayout.LayoutParams(dp(56), dp(56)));
		
		fabColLp = new FrameLayout.LayoutParams(
		ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
		Gravity.BOTTOM | Gravity.END);
		fabColLp.rightMargin = dp(16) + insRight;
		fabColLp.bottomMargin = dp(16) + insBottom;
		content.addView(fabCol, fabColLp);
	}
	
	private LinearLayout fabRow(String label, int iconType, View.OnClickListener l) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
		
		TextView chip = new TextView(this);
		chip.setText(label);
		chip.setTextColor(Palette.TEXT);
		chip.setTextSize(16);
		chip.setPadding(dp(20), dp(12), dp(20), dp(12));
		chip.setBackground(ripple(round(Palette.SHEET, 18), 18));
		chip.setClickable(true);
		chip.setOnClickListener(l);
		LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		cl.rightMargin = dp(16);
		row.addView(chip, cl);
		
		ImageView b = fabButton(iconType);
		b.setOnClickListener(l);
		row.addView(b, new LinearLayout.LayoutParams(dp(56), dp(56)));
		return row;
	}
	
	private ImageView fabButton(int iconType) {
		ImageView b = new ImageView(this);
		b.setScaleType(ImageView.ScaleType.CENTER);
		b.setImageDrawable(icon(iconType, Palette.ON_ACCENT_CONT, dp(26)));
		b.setBackground(ripple(round(Palette.ACCENT_CONT, 18), 18));
		b.setClickable(true);
		return b;
	}
	
	/** Buka/tutup menu FAB. Aman di-spam: animasi lama selalu dibatalkan dulu. */
	private void setFab(boolean open) {
		if (fabOpen == open) return;
		fabOpen = open;
		
		fabScrim.animate().cancel();
		if (open) {
			if (fabScrim.getVisibility() != View.VISIBLE) {
				fabScrim.setAlpha(0f);
				fabScrim.setVisibility(View.VISIBLE);
			}
			fabScrim.animate().alpha(1f).setDuration(180).start();
		} else {
			fabScrim.animate().alpha(0f).setDuration(140).withEndAction(new Runnable() {
				public void run() { if (!fabOpen) fabScrim.setVisibility(View.GONE); }
			}).start();
		}
		
		// urutan stagger: item paling dekat tombol utama muncul dulu
		final View[] rows = new View[]{fabCreate, fabRestore};
		for (int i = 0; i < rows.length; i++) {
			final View r = rows[i];
			r.animate().cancel();
			if (open) {
				if (r.getVisibility() != View.VISIBLE) {
					r.setAlpha(0f);
					r.setTranslationY(dp(16));
					r.setVisibility(View.VISIBLE);
				}
				r.animate().alpha(1f).translationY(0f)
				.setStartDelay(i * 40L).setDuration(200)
				.setInterpolator(new DecelerateInterpolator(1.6f)).start();
			} else {
				r.animate().alpha(0f).translationY(dp(12))
				.setStartDelay(0).setDuration(120)
				.setInterpolator(new DecelerateInterpolator(1.2f))
				.withEndAction(new Runnable() {
					public void run() { if (!fabOpen) r.setVisibility(View.GONE); }
				}).start();
			}
		}
	}
	
	// ----- Drawer (custom: mode PUSH, realtime drag + swipe dari tepi kiri) -----
	
	private class DrawerLayoutView extends FrameLayout {
		private static final float PARALLAX = 0.25f;   // drawer ikut bergeser pelan dari kiri
		private static final float SHRINK = 0.06f;     // konten mengecil sedikit saat terdorong
		private static final int EDGE_DP = 40;         // lebar zona swipe dari tepi kiri
		private FrameLayout contentHolder;
		private View scrim, drawerV;
		private final int drawerWidth, slop, edge;
		
		private float offset = 0f;          // 0 = tertutup, 1 = terbuka
		private boolean targetOpen;         // tujuan animasi terakhir
		private float downX, downY, lastX;
		private int activeId = -1;
		private boolean dragging, candidate, layered;
		private boolean grabbed, grabMoved; // animasi "ditangkap" jari saat sedang berjalan
		private float resumeTarget;
		private VelocityTracker vt;
		private ValueAnimator anim;
		
		DrawerLayoutView(Context c) {
			super(c);
			drawerWidth = drawerW;
			slop = ViewConfiguration.get(c).getScaledTouchSlop();
			edge = dp(EDGE_DP);
		}
		
		void setViews(View content, View drawerView) {
			removeAllViews();
			
			// Lapisan bawah: drawer (diam di belakang, terlihat saat konten bergeser)
			drawerV = drawerView;
			drawerV.setClickable(true);
			addView(drawerV, new LayoutParams(drawerWidth, ViewGroup.LayoutParams.MATCH_PARENT));
			
			// Lapisan atas: konten + peredup. Seluruhnya yang bergeser (push).
			contentHolder = new FrameLayout(getContext());
			contentHolder.addView(content, new LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			scrim = new View(getContext());
			scrim.setBackgroundColor(Color.BLACK);
			scrim.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) { close(); }
			});
			contentHolder.addView(scrim, new LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			if (Build.VERSION.SDK_INT >= 21) {
				contentHolder.setOutlineProvider(new ViewOutlineProvider() {
					@Override public void getOutline(View v, Outline o) {
						o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(28) * offset);
					}
				});
			}
			addView(contentHolder, new LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			setOffset(0f);
		}
		
		boolean isOpen() { return offset > 0f; }
		
		void setOffset(float o) {
			float prev = offset;
			offset = Math.max(0f, Math.min(1f, o));
			
			// konten terdorong ke kanan sejauh lebar drawer
			float sc = 1f - SHRINK * offset;
			contentHolder.setPivotX(0f);
			contentHolder.setPivotY(contentHolder.getHeight() / 2f);
			contentHolder.setScaleX(sc);
			contentHolder.setScaleY(sc);
			contentHolder.setTranslationX(drawerWidth * offset);
			if (Build.VERSION.SDK_INT >= 21 && (offset > 0f || prev > 0f)) {
				contentHolder.setClipToOutline(offset > 0f);   // sudut membulat hanya saat bergeser
				contentHolder.invalidateOutline();
			}
			
			// drawer paralaks halus dari kiri
			drawerV.setTranslationX(-drawerWidth * PARALLAX * (1f - offset));
			drawerV.setVisibility(offset > 0f ? View.VISIBLE : View.INVISIBLE);
			
			// konten diredupkan
			scrim.setAlpha(offset * 0.35f);
			scrim.setVisibility(offset > 0f ? View.VISIBLE : View.GONE);
			
			if (prev == 0f && offset > 0f) {
				hideKeyboard();
				if (fabOpen) setFab(false);
			}
		}
		
		void open() { animateTo(1f, 0f); }
		void close() { animateTo(0f, 0f); }
		/** Toggle berdasarkan TUJUAN animasi, bukan posisi saat ini, supaya spam tombol konsisten. */
		void toggle() { animateTo(targetOpen ? 0f : 1f, 0f); }
		
		// ----- animasi -----
		
		private void startMotion() {
			if (!layered) {
				layered = true;
				contentHolder.setLayerType(View.LAYER_TYPE_HARDWARE, null);
			}
		}
		
		private void endMotion() {
			if (layered) {
				layered = false;
				contentHolder.setLayerType(View.LAYER_TYPE_NONE, null);
			}
		}
		
		private void cancelAnim() {
			if (anim != null) {
				ValueAnimator a = anim;
				anim = null;
				a.cancel();
			}
		}
		
		private void animateTo(final float target, float vx) {
			cancelAnim();
			targetOpen = target > 0.5f;
			float dist = Math.abs(target - offset);
			if (dist < 0.001f) {
				setOffset(target);
				endMotion();
				return;
			}
			startMotion();
			
			long dur = 120 + (long) (200 * dist);
			if (vx != 0f && (vx > 0f) == (target > offset)) {
				long byVelocity = (long) (dist * drawerWidth / Math.abs(vx) * 1000f * 2f);
				dur = Math.max(110, Math.min(dur, byVelocity));
			}
			
			final ValueAnimator a = ValueAnimator.ofFloat(offset, target);
			a.setDuration(dur);
			a.setInterpolator(new DecelerateInterpolator(1.4f));
			a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
				public void onAnimationUpdate(ValueAnimator v) {
					setOffset((Float) v.getAnimatedValue());
				}
			});
			a.addListener(new AnimatorListenerAdapter() {
				private boolean canceled;
				@Override public void onAnimationCancel(Animator an) { canceled = true; }
				@Override public void onAnimationEnd(Animator an) {
					if (canceled) return;          // dibatalkan = ada animasi/drag baru yang mengambil alih
					setOffset(target);
					if (anim == a) {
						anim = null;
						endMotion();
					}
				}
			});
			anim = a;
			a.start();
		}
		
		// ----- sentuhan -----
		
		private void recycleTracker() {
			if (vt != null) {
				vt.recycle();
				vt = null;
			}
		}
		
		private boolean tryStartDrag(MotionEvent ev) {
			int i = ev.findPointerIndex(activeId);
			if (i < 0) return false;
			float x = ev.getX(i), y = ev.getY(i);
			float dx = x - downX, dy = y - downY;
			if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) && (offset > 0f || dx > 0f)) {
				dragging = true;
				lastX = x;
				startMotion();
				if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
				return true;
			}
			return false;
		}
		
		private void finishTouch() {
			if (dragging) {
				float vx = 0f;
				if (vt != null) {
					vt.computeCurrentVelocity(1000);
					vx = vt.getXVelocity();
				}
				boolean open;
				if (grabbed && !grabMoved) open = resumeTarget > 0.5f;      // cuma ketukan: lanjutkan animasi tadi
				else if (Math.abs(vx) > dp(400)) open = vx > 0f;
				else open = offset > 0.5f;
				animateTo(open ? 1f : 0f, vx);
			}
			dragging = false;
			candidate = false;
			grabbed = false;
			grabMoved = false;
			activeId = -1;
			recycleTracker();
		}
		
		@Override
		public boolean onInterceptTouchEvent(MotionEvent ev) {
			int action = ev.getActionMasked();
			if (action == MotionEvent.ACTION_DOWN) {
				recycleTracker();
				vt = VelocityTracker.obtain();
				activeId = ev.getPointerId(0);
				downX = lastX = ev.getX();
				downY = ev.getY();
				dragging = false;
				grabbed = false;
				grabMoved = false;
				if (anim != null) {
					// Jari menyentuh saat animasi berjalan: tangkap, jangan biarkan macet di tengah.
					resumeTarget = targetOpen ? 1f : 0f;
					cancelAnim();
					dragging = true;
					grabbed = true;
					candidate = true;
					startMotion();
					vt.addMovement(ev);
					return true;
				}
				candidate = offset > 0f || downX <= edge;
			}
			if (vt != null) vt.addMovement(ev);
			switch (action) {
				case MotionEvent.ACTION_MOVE:
				if (!dragging && candidate) tryStartDrag(ev);
				break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
				if (!dragging) {
					candidate = false;
					activeId = -1;
					recycleTracker();
				}
				break;
			}
			return dragging;
		}
		
		@Override
		public boolean onTouchEvent(MotionEvent ev) {
			if (vt == null) vt = VelocityTracker.obtain();
			vt.addMovement(ev);
			switch (ev.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
				return true;
				case MotionEvent.ACTION_MOVE:
				// Tidak ada child yang mengonsumsi DOWN -> onInterceptTouchEvent tidak dipanggil lagi,
				// jadi deteksi drag juga dilakukan di sini.
				if (!dragging && candidate) tryStartDrag(ev);
				if (dragging) {
					int i = ev.findPointerIndex(activeId);
					if (i < 0) break;
					float x = ev.getX(i);
					if (Math.abs(x - downX) > slop) grabMoved = true;
					float dx = x - lastX;
					lastX = x;
					if (dx != 0f) setOffset(offset + dx / drawerWidth);
				}
				break;
				case MotionEvent.ACTION_POINTER_UP: {
					int idx = ev.getActionIndex();
					if (ev.getPointerId(idx) == activeId) {
						int ni = idx == 0 ? 1 : 0;
						if (ni < ev.getPointerCount()) {
							activeId = ev.getPointerId(ni);
							lastX = ev.getX(ni);
						}
					}
					break;
				}
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
				finishTouch();
				break;
			}
			return true;
		}
		
		@Override
		protected void onLayout(boolean changed, int l, int t, int r, int b) {
			super.onLayout(changed, l, t, r, b);
			if (Build.VERSION.SDK_INT >= 29) {
				// Cegah gesture back sistem merebut swipe tepi kiri (maks 200dp tinggi per sisi)
				int len = dp(200);
				int top = Math.max(0, ((b - t) - len) / 2);
				setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, top, edge, top + len)));
			}
		}
		
		@Override
		protected void onDetachedFromWindow() {
			super.onDetachedFromWindow();
			cancelAnim();
			endMotion();
			recycleTracker();
		}
	}
	
	// ----- Bottom sheet: izin storage -----
	
	private static final String[] PERM_NAMES = {
		"READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE"};
	
	private void showSheet() {
		sheetHost.showPermission(I_FOLDER, LanguageLoader.get("perm.storage_title"),
		LanguageLoader.get("perm.storage_message"), PERM_NAMES, new Runnable() {
			public void run() { requestAllPermissions(); }
		});
		refreshSheetStatus();
	}
	
	private void hideSheet() {
		sheetHost.hidePermission();
	}
	
	private void refreshSheetStatus() {
		boolean rw = rwGranted();
		sheetHost.refreshPermission(new boolean[]{rw, rw, allFilesGranted()});
	}
	
	// ----- Bottom sheet: create project -----
	
	private void showCreateSheet() {
		ProjectSheet.Values init = new ProjectSheet.Values();
		init.pkg = "com.my.newproject";
		projectSheet.show(LanguageLoader.get("main.create_project"), LanguageLoader.get("main.new_folder_desc"), LanguageLoader.get("common.create"), init, null,
		new ProjectSheet.Callback() {
			public String onSubmit(ProjectSheet.Values v) {
				String err = validateForm(v);
				if (err == null) err = createProject(v);
				if (err == null) loadProjects();
				return err;
			}
			public void onPickIcon() { pickIcon(); }
		});
	}
	
	private void pickIcon() {
		try {
			Intent i = new Intent(Intent.ACTION_GET_CONTENT);
			i.setType("image/*");
			i.addCategory(Intent.CATEGORY_OPENABLE);
			startActivityForResult(Intent.createChooser(i, LanguageLoader.get("common.choose_icon")), REQ_PICK_ICON);
		} catch (Exception e) {
			toast(LanguageLoader.get("toast.no_picker"));
		}
	}
	
	/** Baca gambar pilihan user, potong persegi, maksimal 512px. */
	private Bitmap decodeIconSource(Uri uri) {
		try {
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			InputStream in = getContentResolver().openInputStream(uri);
			if (in == null) return null;
			try { BitmapFactory.decodeStream(in, null, o); } finally { in.close(); }
			if (o.outWidth <= 0 || o.outHeight <= 0) return null;
			int sample = 1;
			while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= 512) sample *= 2;
			BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inSampleSize = sample;
			in = getContentResolver().openInputStream(uri);
			if (in == null) return null;
			Bitmap b;
			try { b = BitmapFactory.decodeStream(in, null, o2); } finally { in.close(); }
			if (b == null) return null;
			int side = Math.min(b.getWidth(), b.getHeight());
			Bitmap sq = Bitmap.createBitmap(b, (b.getWidth() - side) / 2, (b.getHeight() - side) / 2, side, side);
			if (side > 512) sq = Bitmap.createScaledBitmap(sq, 512, 512, true);
			return sq;
		} catch (Throwable e) {
			return null;
		}
	}
	
	/** Validasi form project (create & settings). Return null kalau valid. */
	private String validateForm(ProjectSheet.Values v) {
		if (v.name.length() == 0) return LanguageLoader.get("validate.app_name_empty");
		if (!v.pkg.matches("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")) return LanguageLoader.get("validate.package");
		if (v.vName.length() == 0 || v.vName.matches(".*['\"\\\\\\s].*")) return LanguageLoader.get("validate.version_name");
		if (!v.vCode.matches("\\d{1,9}") || Integer.parseInt(v.vCode) < 1) return LanguageLoader.get("validate.version_code");
		if (!v.compileSdk.matches("\\d{1,2}") || Integer.parseInt(v.compileSdk) < 1) return LanguageLoader.get("validate.compile_sdk");
		if (!v.minSdk.matches("\\d{1,2}") || Integer.parseInt(v.minSdk) < 1) return LanguageLoader.get("validate.min_sdk");
		if (!v.targetSdk.matches("\\d{1,2}") || Integer.parseInt(v.targetSdk) < 1) return LanguageLoader.get("validate.target_sdk");
		if (Integer.parseInt(v.minSdk) > Integer.parseInt(v.targetSdk)) return LanguageLoader.get("validate.min_le_target");
		if (Integer.parseInt(v.targetSdk) > Integer.parseInt(v.compileSdk)) return LanguageLoader.get("validate.target_le_compile");
		if (v.appClass.length() > 0
		&& !v.appClass.matches("^\\.?[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$")) {
			return LanguageLoader.get("validate.app_class");
		}
		return null;
	}
	
	// =====================================================================
	// Project list
	// =====================================================================
	
	private void loadProjects() {
		all.clear();
		File[] dirs = projectsDir().listFiles();
		if (dirs != null) {
			List<File> list = new ArrayList<File>();
			for (int i = 0; i < dirs.length; i++) {
				if (dirs[i].isDirectory() && !dirs[i].getName().startsWith(".")) list.add(dirs[i]);
			}
			Collections.sort(list, new Comparator<File>() {
				public int compare(File a, File b) {
					long x = a.lastModified(), y = b.lastModified();
					return x < y ? -1 : (x == y ? 0 : 1);
				}
			});
			for (int i = 0; i < list.size(); i++) {
				Project p = parseProject(list.get(i), i + 1);
				all.add(0, p); // terbaru di atas
			}
		}
		applyFilter(searchEdit.getText().toString());
	}
	
	private Project parseProject(File dir, int id) {
		Project p = new Project();
		p.dir = dir;
		p.id = id;
		String folder = dir.getName();
		File main = new File(dir, "app/src/main");
		
		String manifest = readText(new File(main, "AndroidManifest.xml"));
		String pkg = find(manifest,
		"package\\s*=\\s*\"([^\"]+)\"");
		String label = find(readText(new File(main, "res/values/strings.xml")),
		"<string\\s+name=\"app_name\"[^>]*>([^<]*)</string>");
		String gradle = readText(new File(dir, "app/build.gradle"));
		String vName = find(gradle, "versionName\\s*[= ]\\s*['\"]([^'\"]+)['\"]");
		String vCode = find(gradle, "versionCode\\s*[= ]\\s*(\\d+)");
		
		p.title = (label != null && label.length() > 0) ? label : folder;
		p.pkg = pkg != null ? pkg : "-";
		p.sub = folder + " - " + (vName != null ? vName : "1.0") + " (" + (vCode != null ? vCode : "1") + ")";
		p.icon = loadProjectIcon(main, manifest);
		return p;
	}
	
	private String find(String text, String regex) {
		if (text == null) return null;
		Matcher m = Pattern.compile(regex).matcher(text);
		return m.find() ? m.group(1) : null;
	}
	
	private void applyFilter(String q) {
		String s = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
		shown.clear();
		for (int i = 0; i < all.size(); i++) {
			Project p = all.get(i);
			if (s.length() == 0 || p.title.toLowerCase(Locale.ROOT).contains(s)
			|| p.pkg.toLowerCase(Locale.ROOT).contains(s)
			|| p.dir.getName().toLowerCase(Locale.ROOT).contains(s)) {
				shown.add(p);
			}
		}
		adapter.notifyDataSetChanged();
		updateEmpty();
	}
	
	private void updateEmpty() {
		boolean empty = shown.isEmpty();
		if (!hasAll()) {
			emptyText.setVisibility(View.GONE);
			return;
		}
		if (empty) {
			boolean searching = searchEdit != null && searchEdit.getText().length() > 0;
			emptyText.setText(searching ? LanguageLoader.get("main.no_results")
			: LanguageLoader.get("main.empty"));
			emptyText.setVisibility(View.VISIBLE);
		} else {
			emptyText.setVisibility(View.GONE);
		}
	}
	
	private void openProject(Project p) {
		Intent i = new Intent(this, ProjectActivity.class);
		i.putExtra("title", p.title);
		i.putExtra("path", p.dir.getAbsolutePath());
		startActivity(i);
	}
	
	private class ProjectAdapter extends BaseAdapter {
		public int getCount() { return shown.size(); }
		public Object getItem(int i) { return shown.get(i); }
		public long getItemId(int i) { return i; }
		
		public View getView(int position, View cv, ViewGroup parent) {
			Holder h;
			if (cv == null) {
				h = new Holder();
				LinearLayout card = new LinearLayout(MainActivity.this);
				card.setOrientation(LinearLayout.HORIZONTAL);
				card.setGravity(Gravity.CENTER_VERTICAL);
				card.setPadding(dp(12), dp(10), dp(4), dp(10));
				card.setBackground(ripple(round(Palette.CARD, 16), 16));
				
				h.icon = new ProjectIconView(MainActivity.this);
				card.addView(h.icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
				
				LinearLayout texts = new LinearLayout(MainActivity.this);
				texts.setOrientation(LinearLayout.VERTICAL);
				h.title = new TextView(MainActivity.this);
				h.title.setTextColor(Palette.TEXT);
				h.title.setTextSize(19);
				h.title.setSingleLine(true);
				h.title.setEllipsize(TextUtils.TruncateAt.END);
				h.sub = new TextView(MainActivity.this);
				h.sub.setTextColor(Palette.SUB);
				h.sub.setTextSize(14);
				h.sub.setSingleLine(true);
				h.sub.setEllipsize(TextUtils.TruncateAt.END);
				h.pkg = new TextView(MainActivity.this);
				h.pkg.setTextColor(Palette.SUB);
				h.pkg.setTextSize(14);
				h.pkg.setSingleLine(true);
				h.pkg.setEllipsize(TextUtils.TruncateAt.END);
				texts.addView(h.title);
				texts.addView(h.sub);
				texts.addView(h.pkg);
				LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0,
				ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
				tl.leftMargin = dp(14);
				card.addView(texts, tl);
				
				h.more = new ImageView(MainActivity.this);
				h.more.setScaleType(ImageView.ScaleType.CENTER);
				h.more.setImageDrawable(icon(I_MORE, Palette.TEXT, dp(22)));
				h.more.setClickable(true);
				card.addView(h.more, new LinearLayout.LayoutParams(dp(44), dp(44)));
				
				card.setTag(h);
				cv = card;
			} else {
				h = (Holder) cv.getTag();
			}
			final Project p = shown.get(position);
			h.icon.setBitmap(p.icon);
			h.icon.setText(String.valueOf(p.id));
			h.title.setText(p.title);
			h.sub.setText(p.sub);
			h.pkg.setText(p.pkg);
			h.more.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) { showProjectMenu(v, p); }
			});
			return cv;
		}
	}
	
	static class Holder {
		ProjectIconView icon;
		TextView title, sub, pkg;
		ImageView more;
	}
	
	// ----- Bottom sheet: menu project (titik tiga) -----
	
	private void showProjectMenu(View anchor, final Project p) {
		sheetHost.menu(p.dir.getName(), String.valueOf(p.id))
		.item(I_COPY, LanguageLoader.get("common.clone"), new Runnable() {
			public void run() { cloneProject(p); }
		})
		.item(I_BACKUP, LanguageLoader.get("common.backup"), new Runnable() {
			public void run() { backupProject(p); }
		})
		.item(I_SETTINGS, LanguageLoader.get("common.settings"), new Runnable() {
			public void run() { showSettingsSheet(p); }
		})
		.danger(I_DELETE, LanguageLoader.get("common.remove"), new Runnable() {
			public void run() { confirmDelete(p); }
		})
		.show();
	}
	
	// ----- Bottom sheet: settings project -----
	
	private void showSettingsSheet(final Project p) {
		File main = new File(p.dir, "app/src/main");
		String label = find(readText(new File(main, "res/values/strings.xml")),
		"<string\\s+name=\"app_name\"[^>]*>([^<]*)</string>");
		String gradle = readText(new File(p.dir, "app/build.gradle"));
		ProjectSheet.Values init = new ProjectSheet.Values();
		init.name = label != null ? label : p.title;
		init.pkg = "-".equals(p.pkg) ? "" : p.pkg;
		String vName = find(gradle, "versionName\\s*=?\\s*['\"]([^'\"]+)['\"]");
		String vCode = find(gradle, "versionCode\\s*=?\\s*(\\d+)");
		String compile = find(gradle, "compileSdk(?:Version)?\\s*=?\\s*(\\d+)");
		String min = find(gradle, "minSdk(?:Version)?\\s*=?\\s*(\\d+)");
		String target = find(gradle, "targetSdk(?:Version)?\\s*=?\\s*(\\d+)");
		String appCls = find(readText(new File(main, "AndroidManifest.xml")),
		"<application\\b[^>]*?android:name\\s*=\\s*\"([^\"]*)\"");
		if (vName != null) init.vName = vName;
		if (vCode != null) init.vCode = vCode;
		if (compile != null) init.compileSdk = compile;
		if (min != null) init.minSdk = min;
		if (target != null) init.targetSdk = target;
		init.appClass = appCls != null ? appCls : "";
		
		projectSheet.show(LanguageLoader.get("main.project_settings"), p.dir.getName(), LanguageLoader.get("common.save_changes"), init, p.icon,
		new ProjectSheet.Callback() {
			public String onSubmit(ProjectSheet.Values v) {
				String err = validateForm(v);
				if (err == null) err = saveSettings(p, v);
				if (err == null) {
					loadProjects();
					toast(LanguageLoader.get("toast.settings_saved"));
				}
				return err;
			}
			public void onPickIcon() { pickIcon(); }
		});
	}
	
	/** Simpan nama app, package, versi. Return null kalau sukses, atau pesan error. */
	private String saveSettings(Project p, ProjectSheet.Values v) {
		String name = v.name, pkg = v.pkg, vName = v.vName, vCode = v.vCode;
		File main = new File(p.dir, "app/src/main");
		String oldPkg = p.pkg;
		try {
			// nama aplikasi
			File strings = new File(main, "res/values/strings.xml");
			String esc = name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
			.replace("'", "\\'");
			String sx = readText(strings);
			if (sx != null && Pattern.compile("<string\\s+name=\"app_name\"[^>]*>[^<]*</string>")
			.matcher(sx).find()) {
				sx = sx.replaceFirst("(<string\\s+name=\"app_name\"[^>]*>)[^<]*(</string>)",
				"$1" + Matcher.quoteReplacement(esc) + "$2");
			} else if (sx != null && sx.contains("</resources>")) {
				sx = sx.replace("</resources>", "    <string name=\"app_name\">" + esc
				+ "</string>\n</resources>");
			} else {
				sx = "<resources>\n    <string name=\"app_name\">" + esc + "</string>\n</resources>\n";
			}
			writeText(strings, sx);
			
			// versi + package di build.gradle
			File gf = new File(p.dir, "app/build.gradle");
			String g = readText(gf);
			if (g != null) {
				g = g.replaceAll("(versionName\\s*=?\\s*)(['\"])[^'\"]*\\2",
				"$1$2" + Matcher.quoteReplacement(vName) + "$2");
				g = g.replaceAll("(versionCode\\s*=?\\s*)\\d+", "$1" + vCode);
				g = g.replaceAll("((?:namespace|applicationId)\\s*=?\\s*)(['\"])[^'\"]*\\2",
				"$1$2" + Matcher.quoteReplacement(pkg) + "$2");
				g = applySdk(g, v.compileSdk, v.minSdk, v.targetSdk);
				writeText(gf, g);
			}
			
			// ganti package di source + pindahkan foldernya
			if (!pkg.equals(oldPkg) && !"-".equals(oldPkg)) {
				renamePackageIn(main, oldPkg, pkg);
				String[] roots = {"java", "kotlin"};
				for (int i = 0; i < roots.length; i++) {
					movePackageDir(new File(main, roots[i]), oldPkg, pkg);
				}
			} else if ("-".equals(oldPkg)) {
				// manifest belum punya package: tulis langsung
				File mf = new File(main, "AndroidManifest.xml");
				String m = readText(mf);
				if (m != null && !m.contains("package=")) {
					writeText(mf, m.replaceFirst("<manifest", "<manifest package=\"" + pkg + "\""));
				}
			}
			String ac = v.appClass;
			if (!"-".equals(oldPkg) && !pkg.equals(oldPkg) && ac.startsWith(oldPkg + ".")) {
				ac = pkg + ac.substring(oldPkg.length());   // nama lengkap ikut package baru
			}
			applyAppClass(main, pkg, ac);
			if (v.icon != null) writeLauncherIcon(main, v.icon, v.radius);
		} catch (IOException e) {
			return LanguageLoader.get("common.failed", e.getMessage());
		}
		return null;
	}
	
	private void renamePackageIn(File f, String oldPkg, String newPkg) throws IOException {
		if (f.isDirectory()) {
			File[] ch = f.listFiles();
			if (ch != null) {
				for (int i = 0; i < ch.length; i++) renamePackageIn(ch[i], oldPkg, newPkg);
			}
			return;
		}
		String n = f.getName();
		if (!(n.endsWith(".java") || n.endsWith(".kt") || n.endsWith(".xml")
		|| n.endsWith(".gradle") || n.endsWith(".pro"))) return;
		if (f.length() > 131072) return;   // readText memotong file besar: lewati agar tidak rusak
		String t = readText(f);
		if (t == null || !t.contains(oldPkg)) return;
		String r = t.replaceAll("(?<![A-Za-z0-9_.])" + Pattern.quote(oldPkg) + "(?![A-Za-z0-9_])",
		Matcher.quoteReplacement(newPkg));
		if (!r.equals(t)) writeText(f, r);
	}
	
	private void movePackageDir(File srcRoot, String oldPkg, String newPkg) {
		File oldDir = new File(srcRoot, oldPkg.replace('.', '/'));
		if (!oldDir.isDirectory()) return;
		File tmp = new File(srcRoot, ".pkgmove");
		deleteRec(tmp);
		if (!oldDir.renameTo(tmp)) return;
		File newDir = new File(srcRoot, newPkg.replace('.', '/'));
		File parent = newDir.getParentFile();
		if (parent != null) parent.mkdirs();
		if (!tmp.renameTo(newDir)) {
			tmp.renameTo(oldDir);   // gagal: kembalikan
			return;
		}
		File d = oldDir;
		while (d != null && !d.equals(srcRoot)) {
			String[] left = d.list();
			if (left != null && left.length == 0) d.delete(); else break;
			d = d.getParentFile();
		}
	}
	
	// ----- Clone -----
	
	private void cloneProject(final Project p) {
		ensureFolders();
		String base = p.dir.getName() + "_copy";
		File dst = new File(projectsDir(), base);
		int n = 2;
		while (dst.exists()) dst = new File(projectsDir(), base + n++);
		final File target = dst;
		final File src = p.dir;
		new Thread(new Runnable() {
			public void run() {
				String msg;
				try {
					copyRec(src, target);
					msg = LanguageLoader.get("clone.done", target.getName());
				} catch (IOException e) {
					deleteRec(target);
					msg = LanguageLoader.get("clone.failed", e.getMessage());
				}
				final String m = msg;
				runOnUiThread(new Runnable() {
					public void run() {
						toast(m);
						loadProjects();
					}
				});
			}
		}).start();
	}
	
	private void copyRec(File s, File d) throws IOException {
		if (s.isDirectory()) {
			d.mkdirs();
			File[] ch = s.listFiles();
			if (ch != null) {
				for (int i = 0; i < ch.length; i++) copyRec(ch[i], new File(d, ch[i].getName()));
			}
		} else {
			FileInputStream in = new FileInputStream(s);
			try {
				FileOutputStream out = new FileOutputStream(d);
				try {
					copy(in, out);
				} finally {
					out.close();
				}
			} finally {
				in.close();
			}
		}
	}
	
	// =====================================================================
	// Create / Backup / Restore / Delete
	// =====================================================================
	
	
	private String createProject(ProjectSheet.Values v) {
		String name = v.name, pkg = v.pkg;
		if (!name.matches("[A-Za-z0-9_\\- ]+")) return LanguageLoader.get("validate.project_name");
		if (!pkg.matches("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")) return LanguageLoader.get("validate.package");
		if (!ensureFolders()) return LanguageLoader.get("validate.folder_failed");
		File proj = new File(projectsDir(), name);
		if (proj.exists()) return LanguageLoader.get("validate.project_exists");
		
		File main = new File(proj, "app/src/main");
		File javaDir = new File(main, "java/" + pkg.replace('.', '/'));
		new File(main, "res/values").mkdirs();
		new File(main, "res/drawable").mkdirs();
		javaDir.mkdirs();
		try {
			writeText(new File(proj, "settings.gradle"),
			"rootProject.name = \"" + name + "\"\ninclude ':app'\n");
			writeText(new File(proj, "build.gradle"),
			"plugins {\n    id 'com.android.application' version '8.2.0' apply false\n}\n");
			writeText(new File(proj, "app/build.gradle"),
			"plugins {\n    id 'com.android.application'\n}\n\nandroid {\n"
			+ "    namespace '" + pkg + "'\n    compileSdk " + v.compileSdk + "\n\n    defaultConfig {\n"
			+ "        applicationId '" + pkg + "'\n        minSdk " + v.minSdk + "\n"
			+ "        targetSdk " + v.targetSdk + "\n"
			+ "        versionCode " + v.vCode + "\n        versionName '" + v.vName + "'\n    }\n}\n");
			writeText(new File(main, "AndroidManifest.xml"),
			"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
			+ "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
			+ "    package=\"" + pkg + "\">\n\n"
			+ "    <application\n        android:label=\"@string/app_name\"\n"
			+ (v.appClass.length() > 0 ? "        android:name=\"" + v.appClass + "\"\n" : "")
			+ (v.icon != null ? "        android:icon=\"@mipmap/ic_launcher\"\n" : "")
			+ "        android:theme=\"@android:style/Theme.Material.NoActionBar\">\n"
			+ "        <activity\n            android:name=\".MainActivity\"\n"
			+ "            android:exported=\"true\">\n"
			+ "            <intent-filter>\n"
			+ "                <action android:name=\"android.intent.action.MAIN\" />\n"
			+ "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
			+ "            </intent-filter>\n        </activity>\n    </application>\n</manifest>\n");
			writeText(new File(main, "res/values/strings.xml"),
			"<resources>\n    <string name=\"app_name\">" + xmlEscape(name) + "</string>\n</resources>\n");
			if (v.icon != null) writeLauncherIcon(main, v.icon, v.radius);
			if (v.appClass.length() > 0) writeAppClassStub(main, pkg, v.appClass);
			writeText(new File(javaDir, "MainActivity.java"),
			"package " + pkg + ";\n\nimport android.app.Activity;\nimport android.os.Bundle;\n"
			+ "import android.widget.TextView;\n\npublic class MainActivity extends Activity {\n\n"
			+ "    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n"
			+ "        super.onCreate(savedInstanceState);\n"
			+ "        TextView t = new TextView(this);\n"
			+ "        t.setText(\"Hello World\");\n        setContentView(t);\n    }\n}\n");
		} catch (IOException e) {
			return LanguageLoader.get("common.failed", e.getMessage());
		}
		return null;
	}
	
	private String xmlEscape(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'");
	}
	
	/** Atur compileSdk, minSdk, targetSdk di isi app/build.gradle (maxSdk dibiarkan apa adanya). */
	private String applySdk(String g, String compile, String min, String target) {
		g = setGradleInt(g, "compileSdk", compile, "android", "    ");
		g = setGradleInt(g, "minSdk", min, "defaultConfig", "        ");
		g = setGradleInt(g, "targetSdk", target, "defaultConfig", "        ");
		return g;
	}
	
	/**
	 * Ganti angka key (mis. minSdk / minSdkVersion). Kalau key belum ada, ditambahkan tepat setelah
	 * pembuka blok. Kalau key ada tapi bukan angka biasa (mis. compileSdk release(36)), dibiarkan.
	 */
	private String setGradleInt(String g, String key, String val, String block, String indent) {
		Matcher m = Pattern.compile("(\\b" + key + "(?:Version)?\\s*=?\\s*)\\d+").matcher(g);
		if (m.find()) return m.replaceFirst("$1" + val);
		if (Pattern.compile("\\b" + key + "(?:Version)?\\b").matcher(g).find()) return g;
		return Pattern.compile("(?m)^([ \\t]*" + block + "\\s*\\{[ \\t]*\\r?\\n)").matcher(g)
		.replaceFirst("$1" + Matcher.quoteReplacement(indent + key + " " + val + "\n"));
	}
	
	/**
	 * Tulis / hapus android:name pada tag application di manifest. Kalau class diubah dan berada di
	 * package project tetapi filenya belum ada, dibuatkan class kosong turunan Application.
	 */
	private void applyAppClass(File main, String pkg, String appClass) throws IOException {
		File mf = new File(main, "AndroidManifest.xml");
		String m = readText(mf);
		if (m == null) return;
		Matcher am = Pattern.compile("<application\\b[^>]*>").matcher(m);
		if (!am.find()) return;
		String tag = am.group();
		String old = find(tag, "android:name\\s*=\\s*\"([^\"]*)\"");
		if (appClass.equals(old == null ? "" : old)) return;   // tidak berubah
		String nt = tag.replaceAll("\\s+android:name\\s*=\\s*\"[^\"]*\"", "");
		if (appClass.length() > 0) {
			nt = nt.replaceFirst("<application", Matcher.quoteReplacement("<application android:name=\"" + appClass + "\""));
		}
		writeText(mf, m.substring(0, am.start()) + nt + m.substring(am.end()));
		if (appClass.length() > 0) writeAppClassStub(main, pkg, appClass);
	}
	
	/** Buat class Application kosong bila appClass ada di package project dan filenya belum ada. */
	private void writeAppClassStub(File main, String pkg, String appClass) throws IOException {
		String fq = appClass.startsWith(".") ? pkg + appClass
		: (appClass.indexOf('.') < 0 ? pkg + "." + appClass : appClass);
		if (!fq.startsWith(pkg + ".")) return;   // class dari luar package project: tidak dibuat
		int dot = fq.lastIndexOf('.');
		String cpkg = fq.substring(0, dot), cls = fq.substring(dot + 1);
		String path = cpkg.replace('.', '/') + "/" + cls;
		File java = new File(main, "java/" + path + ".java");
		File kt = new File(main, "kotlin/" + path + ".kt");
		if (java.exists() || kt.exists()) return;
		writeText(java, "package " + cpkg + ";\n\nimport android.app.Application;\n\n"
		+ "public class " + cls + " extends Application {\n\n"
		+ "    @Override\n    public void onCreate() {\n        super.onCreate();\n    }\n}\n");
	}
	
	/**
	 * Tulis icon launcher (res/mipmap-*dpi/ic_launcher.png) dari gambar persegi dengan sudut
	 * dibulatkan radiusPct persen, hapus icon lama, dan arahkan manifest ke @mipmap/ic_launcher.
	 */
	private void writeLauncherIcon(File main, Bitmap src, int radiusPct) throws IOException {
		File res = new File(main, "res");
		File[] dirs = res.listFiles();
		if (dirs != null) {
			for (File d : dirs) {
				if (!d.isDirectory() || !d.getName().startsWith("mipmap")) continue;
				String[] old = {"ic_launcher.png", "ic_launcher.webp", "ic_launcher.jpg", "ic_launcher.jpeg", "ic_launcher.xml"};
				for (String n : old) new File(d, n).delete();
			}
		}
		String[] names = {"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"};
		int[] px = {48, 72, 96, 144, 192};
		for (int i = 0; i < names.length; i++) {
			File dir = new File(res, names[i]);
			dir.mkdirs();
			Bitmap b = ProjectSheet.makeIcon(src, px[i], radiusPct);
			FileOutputStream out = new FileOutputStream(new File(dir, "ic_launcher.png"));
			try {
				b.compress(Bitmap.CompressFormat.PNG, 100, out);
			} finally {
				out.close();
			}
		}
		File mf = new File(main, "AndroidManifest.xml");
		String m = readText(mf);
		if (m == null) return;
		if (Pattern.compile("android:icon\\s*=\\s*\"[^\"]*\"").matcher(m).find()) {
			m = m.replaceAll("android:icon\\s*=\\s*\"[^\"]*\"", "android:icon=\"@mipmap/ic_launcher\"");
		} else {
			m = m.replaceFirst("<application", "<application\n        android:icon=\"@mipmap/ic_launcher\"");
		}
		m = m.replaceAll("android:roundIcon\\s*=\\s*\"[^\"]*\"", "android:roundIcon=\"@mipmap/ic_launcher\"");
		writeText(mf, m);
	}
	
	private void backupProject(final Project p) {
		if (!hasAll()) { showSheet(); return; }
		ensureFolders();
		String[] ver = readVersion(p.dir);
		final File out = new File(backupDir(), BackupName.fileName(AppPrefs.backupNameFormat(this),
				p.dir.getName(), ver[0], ver[1], p.pkg, System.currentTimeMillis()));
		new Thread(new Runnable() {
			public void run() {
				String msg;
				try {
					ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out));
					try {
						zipRec(p.dir, p.dir.getName(), z);
					} finally {
						z.close();
					}
					msg = LanguageLoader.get("backup.saved", out.getName());
				} catch (IOException e) {
					msg = LanguageLoader.get("backup.failed", e.getMessage());
				}
				final String m = msg;
				runOnUiThread(new Runnable() {
					public void run() { toast(m); }
				});
			}
		}).start();
	}
	
	/** [versionName, versionCode] dari app/build.gradle, dengan nilai bawaan kalau tidak ketemu. */
	private String[] readVersion(File dir) {
		String gradle = readText(new File(dir, "app/build.gradle"));
		String vName = find(gradle, "versionName\\s*=?\\s*['\"]([^'\"]+)['\"]");
		String vCode = find(gradle, "versionCode\\s*=?\\s*(\\d+)");
		return new String[]{vName != null ? vName : "1.0", vCode != null ? vCode : "1"};
	}
	
	private void zipRec(File f, String path, ZipOutputStream z) throws IOException {
		if (f.isDirectory()) {
			File[] ch = f.listFiles();
			if (ch != null) {
				for (int i = 0; i < ch.length; i++) zipRec(ch[i], path + "/" + ch[i].getName(), z);
			}
		} else {
			z.putNextEntry(new ZipEntry(path));
			FileInputStream in = new FileInputStream(f);
			try {
				copy(in, z);
			} finally {
				in.close();
			}
			z.closeEntry();
		}
	}
	
	/** Bottom sheet pemilih file: jelajah /sdcard, pilih file .zip untuk di-restore. */
	private void showRestoreDialog() {
		final BottomSheet s = sheetHost.create();
		s.setDraggable(false);   // gulir daftar tidak boleh ikut menutup sheet; tutup lewat klik saja
		final LinearLayout body = s.body();
		
		body.addView(BottomSheet.text(this, LanguageLoader.get("backup.restore_title"), 26, Palette.TEXT, false));
		
		final TextView pathView = BottomSheet.text(this, "", 14, Palette.SUB, false);
		pathView.setSingleLine(true);
		pathView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
		LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		pl.topMargin = dp(4);
		pl.bottomMargin = dp(12);
		body.addView(pathView, pl);
		
		android.widget.ScrollView scroll = new android.widget.ScrollView(this);
		scroll.setVerticalScrollBarEnabled(false);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		final LinearLayout listCol = new LinearLayout(this);
		listCol.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(listCol, new ViewGroup.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.5f);
		body.addView(scroll, new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, maxH));
		s.setScrollChild(scroll);
		
		body.addView(BottomSheet.button(this, LanguageLoader.get("common.cancel"), false, new View.OnClickListener() {
			public void onClick(View v) { s.dismiss(); }
		}), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
		
		fillPicker(s, listCol, pathView, Environment.getExternalStorageDirectory());
		s.show();
	}
	
	private void fillPicker(final BottomSheet s, final LinearLayout col, final TextView pathView, final File dir) {
		col.removeAllViews();
		final File sd = Environment.getExternalStorageDirectory();
		String path = dir.getAbsolutePath();
		String sdPath = sd.getAbsolutePath();
		pathView.setText(path.startsWith(sdPath) ? "/sdcard" + path.substring(sdPath.length()) : path);
		
		if (!dir.equals(sd) && dir.getParentFile() != null) {
			final File up = dir.getParentFile();
			col.addView(pickerRow(I_CHEVRON, "..", LanguageLoader.get("picker.parent"), true, new Runnable() {
				public void run() { fillPicker(s, col, pathView, up); }
			}));
		}
		
		File[] files = dir.listFiles();
		List<File> dirs = new ArrayList<File>();
		List<File> zips = new ArrayList<File>();
		if (files != null) {
			for (int i = 0; i < files.length; i++) {
				File f = files[i];
				if (f.isDirectory()) dirs.add(f);
				else if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) zips.add(f);
			}
		}
		Comparator<File> byName = new Comparator<File>() {
			public int compare(File a, File b) {
				return a.getName().compareToIgnoreCase(b.getName());
			}
		};
		Collections.sort(dirs, byName);
		Collections.sort(zips, new Comparator<File>() {   // zip terbaru di atas
			public int compare(File a, File b) {
				long x = a.lastModified(), y = b.lastModified();
				return x < y ? 1 : (x == y ? 0 : -1);
			}
		});
		
		for (int i = 0; i < dirs.size(); i++) {
			final File d = dirs.get(i);
			col.addView(pickerRow(I_FOLDER, d.getName(), null, true, new Runnable() {
				public void run() { fillPicker(s, col, pathView, d); }
			}));
		}
		for (int i = 0; i < zips.size(); i++) {
			final File z = zips.get(i);
			String info = formatSize(z.length()) + "  -  "
			+ new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(z.lastModified()));
			col.addView(pickerRow(I_BACKUP, z.getName(), info, false, new Runnable() {
				public void run() {
					s.dismiss();
					restoreZip(z);
				}
			}));
		}
		
		if (dirs.isEmpty() && zips.isEmpty()) {
			TextView e = BottomSheet.text(this, LanguageLoader.get("backup.empty"), 14, Palette.SUB, false);
			e.setGravity(Gravity.CENTER);
			e.setPadding(0, dp(24), 0, dp(24));
			col.addView(e, new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
	}
	
	private View pickerRow(int iconRes, String title, String sub, boolean isFolder, final Runnable action) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(dp(12), dp(10), dp(12), dp(10));
		row.setClickable(true);
		row.setBackground(BottomSheet.ripple(this, null, 16));
		row.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) { action.run(); }
		});
		
		ImageView icon = new ImageView(this);
		icon.setImageResource(iconRes);
		icon.setColorFilter(isFolder ? Palette.SUB : Palette.ACCENT);
		row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
		
		LinearLayout txt = new LinearLayout(this);
		txt.setOrientation(LinearLayout.VERTICAL);
		TextView t = BottomSheet.text(this, title, 16, Palette.TEXT, false);
		t.setSingleLine(true);
		t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
		txt.addView(t);
		if (sub != null) {
			TextView st = BottomSheet.text(this, sub, 12, Palette.SUB, false);
			st.setSingleLine(true);
			txt.addView(st);
		}
		LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0,
		ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		tl.leftMargin = dp(16);
		row.addView(txt, tl);
		return row;
	}
	
	private String formatSize(long b) {
		if (b < 1024) return b + " B";
		if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024f);
		return String.format(Locale.US, "%.1f MB", b / (1024f * 1024f));
	}
	
	/** Nama zip tanpa ekstensi .zip, dipakai sebagai nama folder bila zip tidak punya folder induk. */
	private static String zipBaseName(File zip) {
		String n = zip.getName();
		int i = n.toLowerCase().lastIndexOf(".zip");
		return i > 0 ? n.substring(0, i) : n;
	}

	/** True jika semua isi zip berada di dalam satu folder induk yang sama. */
	private static boolean zipHasRootFolder(File zip) throws IOException {
		String root = null;
		ZipInputStream zin = new ZipInputStream(new FileInputStream(zip));
		try {
			ZipEntry e;
			while ((e = zin.getNextEntry()) != null) {
				String n = e.getName();
				while (n.startsWith("/")) n = n.substring(1);
				if (n.startsWith("./")) n = n.substring(2);
				int slash = n.indexOf('/');
				if (slash <= 0) return false;          // file atau folder langsung di root zip
				String top = n.substring(0, slash);
				if (root == null) root = top;
				else if (!root.equals(top)) return false;
			}
		} finally {
			zin.close();
		}
		return root != null;
	}

	private void restoreZip(final File zip) {
		new Thread(new Runnable() {
			public void run() {
				String msg;
				try {
					String base = projectsDir().getCanonicalPath() + File.separator;
					// zip punya folder induk: ekstrak apa adanya. Tidak punya: buat folder baru bernama nama zip.
					File target = zipHasRootFolder(zip) ? projectsDir() : new File(projectsDir(), zipBaseName(zip));
					target.mkdirs();
					ZipInputStream zin = new ZipInputStream(new FileInputStream(zip));
					try {
						ZipEntry e;
						while ((e = zin.getNextEntry()) != null) {
							File out = new File(target, e.getName());
							if (!out.getCanonicalPath().startsWith(base)) continue; // anti zip-slip
							if (e.isDirectory()) {
								out.mkdirs();
							} else {
								File parent = out.getParentFile();
								if (parent != null) parent.mkdirs();
								FileOutputStream fos = new FileOutputStream(out);
								try {
									copy(zin, fos);
								} finally {
									fos.close();
								}
							}
							zin.closeEntry();
						}
					} finally {
						zin.close();
					}
					msg = LanguageLoader.get("restore.done", zip.getName());
				} catch (IOException e) {
					msg = LanguageLoader.get("restore.failed", e.getMessage());
				}
				final String m = msg;
				runOnUiThread(new Runnable() {
					public void run() {
						toast(m);
						loadProjects();
					}
				});
			}
		}).start();
	}
	
	// ----- Bottom sheet: konfirmasi hapus project -----
	
	private void confirmDelete(final Project p) {
		sheetHost.confirm(I_DELETE, LanguageLoader.get("remove.title", p.title),
		LanguageLoader.get("remove.message"))
		.action(LanguageLoader.get("common.backup"), new Runnable() {
			public void run() { backupProject(p); }
		})
		.action(LanguageLoader.get("common.cancel"), null)
		.dangerAction(LanguageLoader.get("common.remove"), new Runnable() {
			public void run() {
				deleteRec(p.dir);
				loadProjects();
				toast(LanguageLoader.get("toast.project_removed"));
			}
		})
		.show();
	}
	
	private void deleteRec(File f) {
		if (f.isDirectory()) {
			File[] ch = f.listFiles();
			if (ch != null) {
				for (int i = 0; i < ch.length; i++) deleteRec(ch[i]);
			}
		}
		f.delete();
	}
	
	// =====================================================================
	// Misc
	// =====================================================================
	
	private void showInformation() {
		String ver = "1.0";
		int code = 1;
		try {
			android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
			ver = pi.versionName;
			code = pi.versionCode;
		} catch (Exception ignored) {
		}
		final BottomSheet s = sheetHost.create();
		LinearLayout body = s.body();
		
		LinearLayout head = new LinearLayout(this);
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);
		ImageView ic = new ImageView(this);
		ic.setBackground(BottomSheet.round(this, Palette.ACCENT_CONT, 16));
		ic.setScaleType(ImageView.ScaleType.CENTER);
		ic.setImageDrawable(icon(I_INFO, Palette.ON_ACCENT_CONT, dp(28)));
		head.addView(ic, new LinearLayout.LayoutParams(dp(52), dp(52)));
		LinearLayout titles = new LinearLayout(this);
		titles.setOrientation(LinearLayout.VERTICAL);
		titles.addView(BottomSheet.text(this, LanguageLoader.get("info.app_name"), 22, Palette.TEXT, true));
		titles.addView(BottomSheet.text(this, LanguageLoader.get("common.information"), 14, Palette.SUB, false));
		LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0,
		ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		tl.leftMargin = dp(16);
		head.addView(titles, tl);
		body.addView(head, new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		
		LinearLayout card = new LinearLayout(this);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setBackground(BottomSheet.round(this, Palette.SHEET, 20));
		card.setPadding(dp(20), dp(8), dp(20), dp(8));
		card.addView(infoRow(LanguageLoader.get("info.version"), ver + " (" + code + ")", true));
		card.addView(infoRow(LanguageLoader.get("info.package"), getPackageName(), true));
		card.addView(infoRow(LanguageLoader.get("info.projects"), String.valueOf(all.size()), true));
		card.addView(infoRow(LanguageLoader.get("info.folder"), rootDir().getAbsolutePath(), false));
		LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		cl.topMargin = dp(20);
		body.addView(card, cl);
		
		LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
		bl.topMargin = dp(20);
		body.addView(BottomSheet.button(this, LanguageLoader.get("common.close"), true, new View.OnClickListener() {
			public void onClick(View v) { s.dismiss(); }
		}), bl);
		s.show();
	}
	
	private View infoRow(String label, String value, boolean divider) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.VERTICAL);
		row.setPadding(0, dp(12), 0, dp(12));
		row.addView(BottomSheet.text(this, label, 12, Palette.SUB, false));
		TextView v = BottomSheet.text(this, value, 16, Palette.TEXT, false);
		v.setTextIsSelectable(true);
		LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		vl.topMargin = dp(2);
		row.addView(v, vl);
		if (!divider) return row;
		LinearLayout wrap = new LinearLayout(this);
		wrap.setOrientation(LinearLayout.VERTICAL);
		wrap.addView(row);
		View line = new View(this);
		line.setBackgroundColor(Palette.withAlpha(Palette.TEXT, 0x22));
		wrap.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
		return wrap;
	}
	
	private void openUrl(String url) {
		try {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
		} catch (Exception e) {
			toast(LanguageLoader.get("toast.link_failed"));
		}
	}
	
	private void toast(String s) {
		M3.toast(this, s, Toast.LENGTH_SHORT);
	}
	
	private void hideKeyboard() {
		View f = getCurrentFocus();
		if (f != null) {
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
		}
	}
	
	private int dp(float v) {
		return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
	}
	
	private GradientDrawable round(int color, float radiusDp) {
		GradientDrawable g = new GradientDrawable();
		g.setColor(color);
		g.setCornerRadius(dp(radiusDp));
		return g;
	}
	
	private Drawable ripple(Drawable bg, float radiusDp) {
		return new RippleDrawable(ColorStateList.valueOf(Palette.withAlpha(Palette.TEXT, 0x22)), bg, round(0xFF000000, radiusDp));
	}
	
	// ===== Icon project dari res =====
	
	/** Baca icon launcher project dari res. Null kalau project tidak punya icon. */
	private Bitmap loadProjectIcon(File main, String manifest) {
		String name = find(manifest, "android:icon\\s*=\\s*\"@(?:mipmap|drawable)/([A-Za-z0-9_]+)\"");
		if (name == null) name = "ic_launcher";
		File res = new File(main, "res");
		try {
			if (findResFile(res, name, new String[]{".png", ".webp", ".jpg", ".xml"}) == null) return null;
			int size = dp(104);
			Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
			Canvas c = new Canvas(out);
			File xml = findResFile(res, name, new String[]{".xml"});
			String text = xml != null ? readText(xml) : null;
			if (text != null && text.contains("<adaptive-icon")) {
				// adaptive icon: gambar background lalu foreground
				drawResLayer(c, res, find(text, "<background[^>]*drawable=\"@(?:mipmap|drawable)/([A-Za-z0-9_]+)\""), size);
				drawResLayer(c, res, find(text, "<foreground[^>]*drawable=\"@(?:mipmap|drawable)/([A-Za-z0-9_]+)\""), size);
			} else {
				drawResLayer(c, res, name, size);   // bitmap atau vector
			}
			return out;
		} catch (Throwable e) {
			return null;
		}
	}
	
	private void drawResLayer(Canvas c, File res, String name, int size) {
		if (name == null) return;
		File bmp = findResFile(res, name, new String[]{".png", ".webp", ".jpg"});
		if (bmp != null) {
			Bitmap b = decodeScaled(bmp, size);
			if (b != null) c.drawBitmap(b, null, new RectF(0, 0, size, size), null);
			return;
		}
		File xml = findResFile(res, name, new String[]{".xml"});
		if (xml == null) return;
		Drawable d = parseResXml(xml);
		if (d == null) return;
		d.setBounds(0, 0, size, size);
		d.draw(c);
	}
	
	/** Cari file res (mipmap/drawable) dengan nama tertentu; kepadatan tertinggi yang dipilih. */
	private File findResFile(File res, String name, String[] exts) {
		File[] dirs = res.listFiles();
		if (dirs == null) return null;
		File best = null;
		int bestRank = Integer.MAX_VALUE;
		for (File d : dirs) {
			String dn = d.getName();
			if (!d.isDirectory() || !(dn.startsWith("mipmap") || dn.startsWith("drawable"))) continue;
			int rank = densityRank(dn);
			for (String ext : exts) {
				File f = new File(d, name + ext);
				if (f.isFile() && rank < bestRank) {
					best = f;
					bestRank = rank;
				}
			}
		}
		return best;
	}
	
	private static int densityRank(String dirName) {
		String[] order = {"xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi"};
		for (int i = 0; i < order.length; i++) {
			if (dirName.contains(order[i])) return i;
		}
		return order.length;
	}
	
	private Bitmap decodeScaled(File f, int target) {
		try {
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(f.getAbsolutePath(), o);
			if (o.outWidth <= 0 || o.outHeight <= 0) return null;
			int sample = 1;
			while (o.outWidth / (sample * 2) >= target && o.outHeight / (sample * 2) >= target) sample *= 2;
			o.inJustDecodeBounds = false;
			o.inSampleSize = sample;
			return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
		} catch (Throwable e) {
			return null;
		}
	}
	
	private Drawable parseResXml(File f) {
		try {
			XmlPullParser parser = Xml.newPullParser();
			FileInputStream in = new FileInputStream(f);
			try {
				parser.setInput(in, "UTF-8");
				return Drawable.createFromXml(getResources(), parser);
			} finally {
				in.close();
			}
		} catch (Throwable e) {
			return null;
		}
	}
	
	private String readText(File f) {
		if (!f.isFile()) return null;
		try {
			FileInputStream in = new FileInputStream(f);
			try {
				java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
				byte[] buf = new byte[4096];
				int n, total = 0;
				while ((n = in.read(buf)) > 0 && total < 131072) {
					bo.write(buf, 0, n);
					total += n;
				}
				return bo.toString("UTF-8");
			} finally {
				in.close();
			}
		} catch (IOException e) {
			return null;
		}
	}
	
	private void writeText(File f, String text) throws IOException {
		File parent = f.getParentFile();
		if (parent != null) parent.mkdirs();
		FileOutputStream out = new FileOutputStream(f);
		try {
			out.write(text.getBytes("UTF-8"));
		} finally {
			out.close();
		}
	}
	
	private static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[8192];
		int n;
		while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
	}
	
	// =====================================================================
	// Icon project (kepala android + nomor)
	// =====================================================================
	
	static class ProjectIconView extends View {
		private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		private String text = "";
		private Bitmap bmp;
		
		ProjectIconView(Context c) {
			super(c);
		}
		
		void setText(String t) {
			text = t;
			invalidate();
		}
		
		void setBitmap(Bitmap b) {
			bmp = b;
			invalidate();
		}
		
		@Override
		protected void onDraw(Canvas c) {
			float w = getWidth(), h = getHeight();
			float s = Math.min(w, h);
			if (bmp != null) {
				Path clip = new Path();
				clip.addRoundRect(new RectF(0, 0, w, h), s * 0.22f, s * 0.22f, Path.Direction.CW);
				c.save();
				c.clipPath(clip);
				c.drawBitmap(bmp, null, new RectF(0, 0, w, h), null);
				c.restore();
			} else {
				drawFallback(c, w, h, s);
			}
			drawNumber(c, w, h, s);
		}
		
		private void drawFallback(Canvas c, float w, float h, float s) {
			p.setStyle(Paint.Style.FILL);
			p.setColor(0xFF0E1110);
			c.drawCircle(w / 2f, h / 2f, s / 2f, p);
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(s * 0.025f);
			p.setColor(0xFF2C3532);
			c.drawCircle(w / 2f, h / 2f, s / 2f - 1f, p);
			
			// kepala (dome)
			p.setStyle(Paint.Style.FILL);
			p.setColor(0xFFA6A1A6);
			c.drawArc(new RectF(s * 0.22f, s * 0.20f, s * 0.78f, s * 0.76f), 180, 180, true, p);
			// mata
			p.setColor(0xFF1B1B1D);
			c.drawCircle(s * 0.39f, s * 0.43f, s * 0.035f, p);
			c.drawCircle(s * 0.61f, s * 0.43f, s * 0.035f, p);
			// antena
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeCap(Paint.Cap.ROUND);
			p.setStrokeWidth(s * 0.035f);
			p.setColor(0xFFA6A1A6);
			c.drawLine(s * 0.34f, s * 0.26f, s * 0.28f, s * 0.15f, p);
			c.drawLine(s * 0.66f, s * 0.26f, s * 0.72f, s * 0.15f, p);
			// badan
			p.setStyle(Paint.Style.FILL);
			p.setColor(0xFF2E2E31);
			c.drawRoundRect(new RectF(s * 0.22f, s * 0.50f, s * 0.78f, s * 0.90f), s * 0.08f, s * 0.08f, p);
		}
		
		private void drawNumber(Canvas c, float w, float h, float s) {
			p.setColor(Palette.withAlpha(Palette.TEXT, 0xFF));
			p.setTextAlign(Paint.Align.CENTER);
			p.setTypeface(Typeface.DEFAULT);
			p.setTextSize(s * 0.20f);
			p.setShadowLayer(s * 0.04f, 0, 0, 0xCC000000);   // supaya angka terbaca di atas icon terang
			c.drawText(text, w / 2f, s * 0.84f, p);
			p.clearShadowLayer();
			p.setTextAlign(Paint.Align.LEFT);
		}
	}
}
