package ru.jet.editor.activity;

import ru.jet.editor.core.M3;
import ru.jet.editor.core.LanguageLoader;
import ru.jet.editor.core.M3Dialog;
import ru.jet.editor.core.Palette;
import ru.jet.editor.core.ThemeTransition;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import ru.jet.editor.R;
import ru.jet.editor.core.FontManager;
import ru.jet.editor.core.IconDrawable;
import ru.jet.editor.core.M3Menu;
import ru.jet.editor.core.ToolDownloadService;
import ru.jet.editor.core.ToolPaths;
import ru.jet.editor.core.ui.DownloadDialog;

/**
* Downloader SDK / AAPT / GRADLE.
* File disimpan di /sdcard/.JetEdit/build-tools/{sdk,aapt,gradle}.
*
* Header sama dengan SettingActivity: judul besar yang mengecil dan pindah ke samping
* tombol back saat di-scroll; tombol back dan more berukuran sama.
* Progres download diperbarui di tempat (tanpa membangun ulang daftar) supaya tidak berkedip.
*/
public class DownloaderActivity extends Activity {
	private int appliedSignature;
	
	public static final String EXTRA_TYPE = "type";
	public static final String TYPE_SDK = "SDK";
	public static final String TYPE_AAPT = "AAPT";
	public static final String TYPE_GRADLE = "GRADLE";
	
	private static final int REQ_IMPORT = 41;
	private static final int REQ_STORAGE = 42;
	private static final int REQ_NOTIF = 43;
	private static final int M_IMPORT = 1;
	
	/** Repo rilis build tools yang dipakai Sketchware-DayGreen (SdkManagerActivity, BuildToolsActivity). */
	private static final String BUILD_TOOLS_REPO = "https://github.com/IqwanXD/Jedit";
	
	// Warna dan ukuran sama dengan SettingActivity supaya tampilannya seragam
	private static final float END_SCALE = 18f / 34f;   // 34sp mengecil jadi 18sp
	
	private static final int ST_NONE = 0, ST_BUSY = 1, ST_DONE = 2;
	
	private String type;
	private String folder;
	private final List<Item> items = new ArrayList<>();
	private final List<Row> rows = new ArrayList<>();
	private LinearLayout listCol;
	private FrameLayout header;
	private TextView title;
	private int expandedH, collapsedH, maxScroll, lastY;
	private DownloadDialog dialog;
	/** Download yang menunggu izin storage / notifikasi. */
	private Item pendingItem;
	private boolean notifAsked;
	
	static class Item {
		final String name, description, url, fileName;
		
		Item(String name, String description, String url, String fileName) {
			this.name = name;
			this.description = description;
			this.url = url;
			this.fileName = fileName;
		}
	}
	
	private final ToolDownloadService.Listener dlListener = new ToolDownloadService.Listener() {
		public void onProgress(long done, long total) {
			if (dialog != null) dialog.update(done, total);
			syncRows();   // hanya teks progres yang berubah, daftar tidak dibangun ulang
		}
		
		public void onFinished(String name, File file, String error, boolean cancelled) {
			if (dialog != null) {
				dialog.dismiss();
				dialog = null;
			}
			syncRows();
			if (cancelled) toast(LanguageLoader.get("dl.cancelled"));
			else if (error != null) toast(LanguageLoader.get("dl.failed", error));
			else toast(LanguageLoader.get("dl.done", name));
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LanguageLoader.init(this);
		Palette.load(this);
		appliedSignature = Palette.signature();
		ThemeTransition.play(this);
		
		type = getIntent().getStringExtra(EXTRA_TYPE);
		if (type == null) type = TYPE_SDK;
		folder = folderOf(type);
		
		expandedH = dp(176);
		collapsedH = dp(56);
		maxScroll = expandedH - collapsedH;
		
		Window w = getWindow();
		w.setStatusBarColor(Palette.BG);
		w.setNavigationBarColor(Palette.BG);
		
		listCol = new LinearLayout(this);
		listCol.setOrientation(LinearLayout.VERTICAL);
		listCol.setPadding(dp(16), expandedH, dp(16), dp(24));
		
		items.addAll(buildItems(type));
		for (Item it : items) {
			Row r = new Row(it);
			rows.add(r);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.bottomMargin = dp(12);
			listCol.addView(r.card, lp);
		}
		
		// Konten mulai di bawah header (lewat padding atas), sama seperti SettingActivity.
		ScrollView scroll = new ScrollView(this);
		scroll.setVerticalScrollBarEnabled(false);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		scroll.addView(listCol);
		// Daftar pendek tetap bisa di-scroll sejauh maxScroll, supaya header tetap bisa mengecil.
		scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			public void onLayoutChange(View v, int l, int t, int r, int b,
			int ol, int ot, int or, int ob) {
				int min = (b - t) + maxScroll;
				if (listCol.getMinimumHeight() != min) listCol.setMinimumHeight(min);
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
		FontManager.attach(root);
		collapse(0);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		Palette.load(this);
		if (Palette.signature() != appliedSignature) { ThemeTransition.capture(this); recreate(); return; }
		ToolDownloadService.setListener(dlListener);
		syncRows();
		// Kembali dari halaman "akses semua file": lanjutkan download yang tertunda.
		if (pendingItem != null && hasStorage()) continueDownload();
	}
	
	@Override
	protected void onPause() {
		ToolDownloadService.setListener(null);
		super.onPause();
	}
	
	@Override
	protected void onDestroy() {
		if (dialog != null) {
			dialog.dismiss();
			dialog = null;
		}
		super.onDestroy();
	}
	
	// ===================== HEADER (sama dengan SettingActivity) =====================
	
	private FrameLayout buildHeader() {
		FrameLayout h = new FrameLayout(this);
		h.setBackgroundColor(Palette.BG);
		
		ImageView back = headerIcon(R.drawable.ic_back);
		back.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) { finish(); }
		});
		FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.START);
		bl.topMargin = (collapsedH - dp(48)) / 2;
		bl.leftMargin = dp(4);
		h.addView(back, bl);
		
		ImageView more = headerIcon(R.drawable.ic_more);
		more.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) { showMoreMenu(v); }
		});
		FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END);
		ml.topMargin = (collapsedH - dp(48)) / 2;
		ml.rightMargin = dp(4);
		h.addView(more, ml);
		
		title = text(titleFor(type), 34, Palette.TEXT, false);
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
	
	/** Ikon 48dp dengan ripple lingkaran 20dp, persis seperti tombol back di SettingActivity. */
	private ImageView headerIcon(int iconRes) {
		ImageView v = new ImageView(this);
		v.setScaleType(ImageView.ScaleType.CENTER);
		v.setImageDrawable(IconDrawable.of(this, iconRes, Palette.TEXT, dp(24)));
		RippleDrawable rd = new RippleDrawable(ColorStateList.valueOf(Palette.RIPPLE), null, null);
		rd.setRadius(dp(20));
		v.setBackground(rd);
		v.setClickable(true);
		return v;
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
		float topExpanded = expandedH - dp(16) - th;            // menempel di bawah header
		float topCollapsed = (collapsedH - th * END_SCALE) / 2f; // sejajar tombol back
		
		title.setPivotX(0f);
		title.setPivotY(0f);
		title.setScaleX(s);
		title.setScaleY(s);
		title.setTranslationX(dp(48) * p);                       // 16dp -> 64dp
		title.setTranslationY(topExpanded + (topCollapsed - topExpanded) * p);
	}
	
	private void showMoreMenu(View anchor) {
		new M3Menu(this)
		.add(M_IMPORT, R.drawable.ic_import_tool, LanguageLoader.get("dl.import_file"))
		.setOnItemClickListener(new M3Menu.Listener() {
			public void onItem(int id) {
				if (id == M_IMPORT) pickImport();
			}
		})
		.show(anchor);
	}
	
	// ===================== DAFTAR ITEM =====================
	
	private static String folderOf(String type) {
		if (TYPE_AAPT.equals(type)) return "aapt";
		if (TYPE_GRADLE.equals(type)) return "";   // build-tools: langsung di root
		return "sdk";
	}
	
	private static String titleFor(String type) {
		if (TYPE_AAPT.equals(type)) return LanguageLoader.get("settings.aapt_manager");
		if (TYPE_GRADLE.equals(type)) return LanguageLoader.get("settings.build_tools_manager");
		return LanguageLoader.get("settings.sdk_manager");
	}
	
	private static int iconFor(String type) {
		if (TYPE_AAPT.equals(type)) return R.drawable.ic_terminal;
		if (TYPE_GRADLE.equals(type)) return R.drawable.ic_settings;
		return R.drawable.ic_code;
	}
	
	/** URL mengikuti repo Sketchware-DayGreen (SdkManagerActivity, BuildToolsActivity, gradle-wrapper.properties). */
	private List<Item> buildItems(String type) {
		List<Item> out = new ArrayList<>();
		if (TYPE_AAPT.equals(type)) {
			String abi = Build.SUPPORTED_ABIS[0];
			out.add(new Item(LanguageLoader.get("dl.item_aapt"),
			LanguageLoader.get("dl.aapt_desc"),
			BUILD_TOOLS_REPO + "/releases/download/aapt2/aapt2-" + abi,
			"aapt2-" + abi));
		} else if (TYPE_GRADLE.equals(type)) {
			return loadMaskawin();
		} else {
			for (int api = 26; api <= 37; api++) {
				out.add(new Item(LanguageLoader.get("dl.sdk_item", api),
				LanguageLoader.get("dl.sdk_desc", api),
				BUILD_TOOLS_REPO + "/releases/download/sdk/android-" + api + ".jar",
				"android-" + api + ".jar"));
			}
		}
		return out;
	}
	
	/**
* Daftar Build-Tools dari assets/build/maskawin.json.
* Format: {"items":[{"id":"...","name":"...","file":"nama-file","url":"https://..."}]}
* Deskripsi tidak ada di JSON: diambil dari language/*.json lewat kunci maskawin.{id}.desc,
* jadi ikut bahasa yang dipilih.
*/	
	private List<Item> loadMaskawin() {
		List<Item> out = new ArrayList<>();
		try {
			InputStream in = getAssets().open("build/maskawin.json");
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			try {
				while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
			} finally {
				in.close();
			}
			JSONArray arr = new JSONObject(new String(bos.toByteArray(), "UTF-8")).getJSONArray("items");
			for (int i = 0; i < arr.length(); i++) {
				JSONObject o = arr.getJSONObject(i);
				String url = o.optString("url", "");
				String desc = url.isEmpty() ? LanguageLoader.get("dl.no_link")
				: LanguageLoader.get("maskawin." + o.optString("id", "") + ".desc");
				out.add(new Item(o.getString("name"), desc, url, o.getString("file")));
			}
		} catch (Exception e) {
			M3.toast(this, LanguageLoader.get("dl.maskawin_failed", e.getMessage()), Toast.LENGTH_LONG);
		}
		return out;
	}
	
	private File fileOf(Item it) {
		if (folder == null || folder.isEmpty()) return new File(ToolPaths.root(), it.fileName);
		return new File(ToolPaths.dir(folder), it.fileName);
	}
	
	private static int percentOf(long done, long total) {
		return total > 0 ? (int) (done * 100 / total) : 0;
	}
	
	/** Satu kartu tool. Views dibuat sekali; status dan tombol diperbarui di tempat. */
	private class Row {
		final Item it;
		final LinearLayout card;
		final TextView chip;
		final TextView statusText;
		final FrameLayout actionHost;
		int state = -1;
		
		Row(final Item it) {
			this.it = it;
			
			card = new LinearLayout(DownloaderActivity.this);
			card.setOrientation(LinearLayout.VERTICAL);
			card.setPadding(dp(16), dp(16), dp(16), dp(16));
			card.setBackground(round(Palette.CARD, 20));
			
			// Baris atas: kotak ikon tetap | nama + deskripsi | chip status
			LinearLayout head = new LinearLayout(DownloaderActivity.this);
			head.setOrientation(LinearLayout.HORIZONTAL);
			head.setGravity(Gravity.CENTER_VERTICAL);
			
			FrameLayout iconBox = new FrameLayout(DownloaderActivity.this);
			iconBox.setBackground(round(Palette.POPUP, 14));
			ImageView icon = new ImageView(DownloaderActivity.this);
			icon.setImageDrawable(IconDrawable.of(DownloaderActivity.this, iconFor(type), Palette.ACCENT, dp(24)));
			iconBox.addView(icon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
			head.addView(iconBox, new LinearLayout.LayoutParams(dp(44), dp(44)));
			
			LinearLayout texts = new LinearLayout(DownloaderActivity.this);
			texts.setOrientation(LinearLayout.VERTICAL);
			TextView name = text(it.name, 16, Palette.TEXT, true);
			name.setSingleLine(true);
			name.setEllipsize(TextUtils.TruncateAt.END);
			texts.addView(name);
			TextView desc = text(it.description, 13, Palette.SUB, false);
			desc.setMaxLines(2);
			desc.setEllipsize(TextUtils.TruncateAt.END);
			texts.addView(desc);
			LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
			tl.leftMargin = dp(12);
			head.addView(texts, tl);
			
			chip = text("", 11, Palette.SUB, false);
			chip.setGravity(Gravity.CENTER);
			chip.setPadding(dp(10), dp(4), dp(10), dp(4));
			chip.setBackground(round(Palette.withAlpha(Palette.TEXT, 0x14), 12));
			LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			cl.leftMargin = dp(8);
			head.addView(chip, cl);
			card.addView(head, new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			
			statusText = text("", 13, Palette.SUB, false);
			LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			sl.topMargin = dp(12);
			card.addView(statusText, sl);
			
			actionHost = new FrameLayout(DownloaderActivity.this);
			LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
			al.topMargin = dp(14);
			card.addView(actionHost, al);
		}
		
		/** Cocokkan tampilan dengan keadaan file/download saat ini. Hanya bagian yang berubah yang disentuh. */
		void sync() {
			File file = fileOf(it);
			boolean busy = ToolDownloadService.isRunning()
			&& it.name.equals(ToolDownloadService.getRunningName());
			boolean installed = !busy && file.exists() && file.length() > 0;
			int s = busy ? ST_BUSY : installed ? ST_DONE : ST_NONE;
			
			if (s != state) {
				state = s;
				setIfChanged(chip, busy ? LanguageLoader.get("dl.status_downloading") : installed ? LanguageLoader.get("dl.status_installed") : LanguageLoader.get("dl.status_not_installed"));
				actionHost.removeAllViews();
				actionHost.addView(buttonFor(s, it, file), new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			}
			
			String st;
			if (busy) {
				st = LanguageLoader.get("dl.status_downloading", percentOf(ToolDownloadService.getDoneBytes(),
				ToolDownloadService.getTotalBytes()) + "%");
			} else if (installed) {
				st = LanguageLoader.get("dl.status_installed_size", ToolDownloadService.formatSize(file.length()));
			} else {
				st = LanguageLoader.get("dl.status_not_installed_long");
			}
			setIfChanged(statusText, st);
		}
	}
	
	private void syncRows() {
		for (Row r : rows) r.sync();
	}
	
	private static void setIfChanged(TextView tv, String s) {
		if (!s.contentEquals(tv.getText())) tv.setText(s);
	}
	
	/** Hanya tombol Download, Hapus, atau Lihat progres (saat sedang download). */
	private LinearLayout buttonFor(int state, final Item it, final File file) {
		LinearLayout btn;
		if (state == ST_BUSY) {
			btn = actionButton(LanguageLoader.get("dl.view_progress"), true, 0);
			btn.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) { openDialog(it.name); }
			});
		} else if (state == ST_DONE) {
			btn = actionButton(LanguageLoader.get("common.delete"), false, R.drawable.ic_delete_tool);
			btn.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) { confirmRemove(it, file); }
			});
		} else {
			btn = actionButton(LanguageLoader.get("common.download"), true, R.drawable.ic_download_tool);
			btn.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) { onDownload(it); }
			});
		}
		return btn;
	}
	
	// ===================== DOWNLOAD =====================
	
	private void onDownload(Item it) {
		if (ToolDownloadService.isRunning()) {
			toast(LanguageLoader.get("dl.wait_other"));
			return;
		}
		pendingItem = it;
		if (!ensureStorage()) return;   // menunggu izin; lanjut di onRequestPermissionsResult / onResume
		continueDownload();
	}
	
	/** Minta izin notifikasi dulu (Android 13+), baru mulai service download. */
	private void continueDownload() {
		final Item it = pendingItem;
		if (it == null) return;
		
		if (Build.VERSION.SDK_INT >= 33 && !notifAsked
		&& checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
			notifAsked = true;
			requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
			return;   // dilanjutkan di onRequestPermissionsResult
		}
		
		pendingItem = null;
		if (ToolDownloadService.isRunning()) {
			toast(LanguageLoader.get("dl.wait_other"));
			return;
		}
		ToolDownloadService.start(this, it.name, it.url, fileOf(it));
		openDialog(it.name);
		syncRows();
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
		if (requestCode == REQ_STORAGE) {
			if (granted && pendingItem != null) {
				if (ensureStorage()) continueDownload();
			} else if (!granted) {
				pendingItem = null;
				toast(LanguageLoader.get("dl.storage_denied"));
			}
		} else if (requestCode == REQ_NOTIF) {
			if (!granted) toast(LanguageLoader.get("dl.notif_off"));
			continueDownload();
		}
	}
	
	private void openDialog(String name) {
		if (dialog != null) return;
		dialog = new DownloadDialog(this, name, new DownloadDialog.Callbacks() {
			public void onMinimize() {
				dialog = null;
			}
			
			public void onCancel() {
				dialog = null;
				ToolDownloadService.cancel(DownloaderActivity.this);
			}
		});
		dialog.show();
		dialog.update(ToolDownloadService.getDoneBytes(), ToolDownloadService.getTotalBytes());
	}
	
	private void confirmRemove(final Item it, final File file) {
		new M3Dialog(this)
		.title(LanguageLoader.get("dl.delete_title", it.name))
		.negative(LanguageLoader.get("common.cancel"), null)
		.positive(LanguageLoader.get("common.delete"), new M3Dialog.Action() {
			public void run() {
				if (file.exists()) file.delete();
				syncRows();
			}
		}, true)
		.show();
	}
	
	/** Cek tanpa efek samping: apakah bisa menulis ke /sdcard. */
	private boolean hasStorage() {
		if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
		return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
		== PackageManager.PERMISSION_GRANTED;
	}
	
	/** Kalau belum punya izin, minta lalu kembalikan false. */
	private boolean ensureStorage() {
		if (hasStorage()) return true;
		if (Build.VERSION.SDK_INT >= 30) {
			toast(LanguageLoader.get("dl.allow_all_files"));
			startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
			Uri.parse("package:" + getPackageName())));
		} else {
			requestPermissions(new String[]{
				Manifest.permission.READ_EXTERNAL_STORAGE,
				Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
		}
		return false;
	}
	
	// ===================== IMPORT MANUAL =====================
	
	private void pickImport() {
		if (!ensureStorage()) return;
		Intent i = new Intent(Intent.ACTION_GET_CONTENT);
		i.setType("*/*");
		i.addCategory(Intent.CATEGORY_OPENABLE);
		startActivityForResult(Intent.createChooser(i, LanguageLoader.get("dl.pick_file", titleFor(type))), REQ_IMPORT);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQ_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
			chooseImportTarget(data.getData());
		}
	}
	
	/** Pilih item mana yang diisi oleh file ini, lalu file disalin ke folder tool. */
	private void chooseImportTarget(final Uri uri) {
		final android.app.Dialog[] ref = new android.app.Dialog[1];
		LinearLayout list = new LinearLayout(this);
		list.setOrientation(LinearLayout.VERTICAL);
		for (int i = 0; i < items.size(); i++) {
			final Item target = items.get(i);
			TextView row = new TextView(this);
			row.setText(target.name);
			row.setTextSize(16);
			row.setTextColor(Palette.TEXT);
			row.setPadding(dp(16), dp(14), dp(16), dp(14));
			row.setBackground(new RippleDrawable(ColorStateList.valueOf(Palette.RIPPLE), null, null));
			row.setClickable(true);
			row.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					if (ref[0] != null) ref[0].dismiss();
					importFile(uri, target);
				}
			});
			list.addView(row, new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		ref[0] = new M3Dialog(this)
		.title(LanguageLoader.get("dl.import_as"))
		.view(list)
		.negative(LanguageLoader.get("common.cancel"), null)
		.show();
	}
	
	private void importFile(final Uri uri, final Item it) {
		final File dest = fileOf(it);
		toast(LanguageLoader.get("dl.importing", it.name));
		new Thread(new Runnable() {
			public void run() {
				String err = null;
				try {
					InputStream in = getContentResolver().openInputStream(uri);
					if (in == null) throw new IOException(LanguageLoader.get("dl.cannot_open"));
					dest.getParentFile().mkdirs();
					File part = new File(dest.getPath() + ".part");
					OutputStream out = new FileOutputStream(part);
					try {
						byte[] buf = new byte[16 * 1024];
						int n;
						while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
					} finally {
						out.close();
						in.close();
					}
					if (dest.exists()) dest.delete();
					if (!part.renameTo(dest)) throw new IOException(LanguageLoader.get("dl.cannot_save"));
				} catch (Exception e) {
					err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
				}
				final String fe = err;
				runOnUiThread(new Runnable() {
					public void run() {
						toast(fe == null ? LanguageLoader.get("dl.import_done", it.name) : LanguageLoader.get("dl.import_failed", fe));
						syncRows();
					}
				});
			}
		}).start();
	}
	
	// ===================== UI HELPERS =====================
	
	private void toast(String msg) {
		M3.toast(this, msg, Toast.LENGTH_SHORT);
	}
	
	/** Tombol pill: ikon + teks di tengah, ripple mengikuti bentuk tombol. */
	private LinearLayout actionButton(String label, boolean filled, int iconRes) {
		LinearLayout b = new LinearLayout(this);
		b.setOrientation(LinearLayout.HORIZONTAL);
		b.setGravity(Gravity.CENTER);
		int fg = filled ? Palette.ON_ACCENT : Palette.ACCENT;
		
		GradientDrawable shape = new GradientDrawable();
		shape.setCornerRadius(dp(22));
		if (filled) shape.setColor(Palette.ACCENT);
		else shape.setStroke(dp(1), Palette.ACCENT);
		GradientDrawable mask = new GradientDrawable();
		mask.setCornerRadius(dp(22));
		mask.setColor(Palette.withAlpha(Palette.TEXT, 0xFF));
		b.setBackground(new RippleDrawable(ColorStateList.valueOf(Palette.RIPPLE), shape, mask));
		
		if (iconRes != 0) {
			ImageView iv = new ImageView(this);
			iv.setImageDrawable(IconDrawable.of(this, iconRes, fg, dp(18)));
			LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(18), dp(18));
			il.rightMargin = dp(8);
			b.addView(iv, il);
		}
		TextView t = text(label, 15, fg, true);
		b.addView(t, new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		b.setClickable(true);
		return b;
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
