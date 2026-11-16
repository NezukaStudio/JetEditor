package ru.jet.editor.container;

import ru.jet.editor.core.LanguageLoader;
import ru.jet.editor.core.Palette;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import ru.jet.editor.R;
import ru.jet.editor.core.IconDrawable;
import ru.jet.editor.core.M3Scroll;
import ru.jet.editor.core.InoInputLayout;
import ru.jet.editor.core.Slider;

/**
 * Bottom sheet form project (dipakai untuk Create project dan Project settings).
 * Isi: pilih icon + rounded (Slider), nama, package, versi, compile/min/target SDK (popup),
 * dan application class.
 *
 * Pakai:
 *   ProjectSheet ps = new ProjectSheet(activity, sheetHost);
 *   ps.show("Create project", "Deskripsi", "Create", values, null, callback);
 *   // setelah user memilih gambar (onActivityResult): ps.setPickedIcon(bitmapKotak);
 */
public class ProjectSheet {
	/** API level yang tampil di popup + nama versi Android-nya (urutan harus sama). */
	private static final int[] SDK_API = {21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37};
	private static final String[] SDK_NAME = {"5.0", "5.1", "6.0", "7.0", "7.1", "8.0", "8.1", "9", "10",
		"11", "12", "12L", "13", "14", "15", "16", "17"};

	/** Nilai form. icon = null berarti icon tidak diganti. radius dalam persen (0..50). */
	public static class Values {
		public String name = "", pkg = "", vName = "1.0", vCode = "1";
		public String compileSdk = "34", minSdk = "21", targetSdk = "34";
		/** Application class (".MyApp" atau nama lengkap). Kosong = tanpa application class. */
		public String appClass = "";
		public Bitmap icon;
		public int radius = 22;
	}

	public interface Callback {
		/** Return null kalau sukses (sheet ditutup), atau pesan error. */
		String onSubmit(Values v);
		/** Buka pemilih gambar; hasilnya kirim balik lewat setPickedIcon(). */
		void onPickIcon();
	}

	private final Activity act;
	private final BottomSheet.Host host;
	private BottomSheet sheet;
	private ImageView preview;
	private Slider seek;
	private TextView radiusLabel, radiusHint;
	private View focusHolder;           // dipakai untuk menarik fokus dari EditText saat popup dibuka
	private PopupWindow popup;          // popup SDK yang sedang terbuka
	private Bitmap picked;      // sumber icon pilihan user (persegi)
	private Bitmap existing;    // icon project yang sudah ada (hanya tampilan)

	public ProjectSheet(Activity act, BottomSheet.Host host) {
		this.act = act;
		this.host = host;
	}

	public boolean isShowing() {
		return sheet != null && sheet.isShowing();
	}

	/** Dipanggil Activity setelah gambar dipilih. src sebaiknya sudah dipotong persegi. */
	public void setPickedIcon(Bitmap src) {
		if (src == null || !isShowing()) return;
		picked = src;
		seek.setEnabled(true);
		seek.setAlpha(1f);
		radiusHint.setVisibility(View.GONE);
		refreshPreview();
	}

	// =====================================================================

	public void show(String title, String desc, String submitText, Values init,
	Bitmap existingIcon, final Callback cb) {
		final Context c = act;
		picked = null;
		existing = existingIcon;
		final BottomSheet s = host.create();
		sheet = s;
		LinearLayout body = s.body();

		body.addView(BottomSheet.text(c, title, 22, Palette.TEXT, true));
		LinearLayout.LayoutParams dl = BottomSheet.wrap();
		dl.topMargin = dp(4);
		dl.bottomMargin = dp(16);
		body.addView(BottomSheet.text(c, desc, 14, Palette.SUB, false), dl);

		// ----- area scroll -----
		final MaxScroll scroll = new MaxScroll(c);
		scroll.setVerticalScrollBarEnabled(false);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		scroll.setFocusable(true);
		scroll.setFocusableInTouchMode(true);
		focusHolder = scroll;
		LinearLayout col = new LinearLayout(c);
		col.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(col, new ViewGroup.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		body.addView(scroll, new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		s.setScrollChild(scroll);

		// pesan error (ditempel ke body setelah area scroll, dibuat di sini supaya bisa dipakai field)
		final TextView error = new TextView(c);
		error.setTextColor(Palette.ERROR);
		error.setTextSize(14);
		error.setVisibility(View.GONE);
		final Runnable clearError = new Runnable() {
			public void run() { error.setVisibility(View.GONE); }
		};

		// ----- icon -----
		LinearLayout iconRow = new LinearLayout(c);
		iconRow.setOrientation(LinearLayout.HORIZONTAL);
		iconRow.setGravity(Gravity.CENTER_VERTICAL);

		preview = new ImageView(c);
		iconRow.addView(preview, new LinearLayout.LayoutParams(dp(72), dp(72)));

		LinearLayout side = new LinearLayout(c);
		side.setOrientation(LinearLayout.VERTICAL);
		TextView iconLabel = BottomSheet.label(c, LanguageLoader.get("sheet.app_icon"));
		side.addView(iconLabel);
		LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
		TextView pick = BottomSheet.button(c, LanguageLoader.get("common.choose_icon"), false, new View.OnClickListener() {
			public void onClick(View v) {
				BottomSheet.hideKeyboard(act);
				cb.onPickIcon();
			}
		});
		pick.setPadding(dp(16), 0, dp(16), 0);
		pick.setBackground(BottomSheet.ripple(c, BottomSheet.round(c, Palette.CARD, 22), 22));
		side.addView(pick, bl);
		LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0,
		ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		sl.leftMargin = dp(16);
		iconRow.addView(side, sl);
		col.addView(iconRow, new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		// ----- rounded (Slider) -----
		radiusLabel = BottomSheet.label(c, LanguageLoader.get("sheet.rounded", init.radius));
		LinearLayout.LayoutParams rl = BottomSheet.wrap();
		rl.topMargin = dp(14);
		col.addView(radiusLabel, rl);

		seek = new Slider(c);
		seek.setMax(50);
		seek.setProgress(Math.max(0, Math.min(50, init.radius)));
		seek.setColors(Palette.ACCENT, Palette.withAlpha(Palette.ACCENT, 0x4D), Palette.ACCENT);
		seek.setEnabled(false);
		seek.setAlpha(0.4f);
		seek.setOnSliderChangeListener(new Slider.SliderListener() {
			public void onValueChanged(Slider slider, int value, boolean fromUser) {
				radiusLabel.setText(LanguageLoader.get("sheet.rounded", value));
				refreshPreview();
			}
			public void onStartTracking(Slider slider) {}
			public void onStopTracking(Slider slider) {}
		});
		col.addView(seek, new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		radiusHint = BottomSheet.text(c, LanguageLoader.get("sheet.rounded_hint"), 12, Palette.SUB, false);
		radiusHint.setPadding(dp(4), 0, 0, 0);
		col.addView(radiusHint, BottomSheet.wrap());

		// ----- field (InoInputLayout) -----
		final int TXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
		final InoInputLayout fName = input(LanguageLoader.get("sheet.app_name"), init.name, TXT, EditorInfo.IME_ACTION_NEXT);
		final InoInputLayout fPkg = input(LanguageLoader.get("sheet.package"), init.pkg, TXT, EditorInfo.IME_ACTION_NEXT);
		final InoInputLayout fVName = input(LanguageLoader.get("sheet.version_name"), init.vName, TXT, EditorInfo.IME_ACTION_NEXT);
		final InoInputLayout fVCode = input(LanguageLoader.get("sheet.version_code"), init.vCode, InputType.TYPE_CLASS_NUMBER,
		EditorInfo.IME_ACTION_NEXT);
		final InoInputLayout fApp = input(LanguageLoader.get("sheet.app_class"), init.appClass, TXT,
		EditorInfo.IME_ACTION_DONE);

		// ----- SDK: popup pilihan, bukan input manual -----
		final SdkField fCompile = new SdkField(LanguageLoader.get("sheet.compile_sdk"), init.compileSdk, 34, clearError);
		final SdkField fMin = new SdkField(LanguageLoader.get("sheet.min_sdk"), init.minSdk, 21, clearError);
		final SdkField fTarget = new SdkField(LanguageLoader.get("sheet.target_sdk"), init.targetSdk, 34, clearError);

		addRow(col, fName, 8);
		addRow(col, fPkg, 0);
		addPair(col, fVName, fVCode, 0);
		addRow(col, fCompile.root, 0);
		addRow(col, fMin.root, 0);
		addRow(col, fTarget.root, 0);
		addRow(col, fApp, 0);

		// ----- error + tombol (tetap terlihat, tidak ikut scroll) -----
		LinearLayout.LayoutParams el = BottomSheet.wrap();
		el.topMargin = dp(10);
		body.addView(error, el);

		TextWatcher clear = new TextWatcher() {
			public void beforeTextChanged(CharSequence x, int a, int b, int d) {}
			public void onTextChanged(CharSequence x, int a, int b, int d) {}
			public void afterTextChanged(Editable e) { error.setVisibility(View.GONE); }
		};
		fName.getEditText().addTextChangedListener(clear);
		fPkg.getEditText().addTextChangedListener(clear);
		fVName.getEditText().addTextChangedListener(clear);
		fVCode.getEditText().addTextChangedListener(clear);
		fApp.getEditText().addTextChangedListener(clear);

		final Runnable submit = new Runnable() {
			public void run() {
				Values v = new Values();
				v.name = fName.getText().trim();
				v.pkg = fPkg.getText().trim();
				v.vName = fVName.getText().trim();
				v.vCode = fVCode.getText().trim();
				v.compileSdk = String.valueOf(fCompile.value);
				v.minSdk = String.valueOf(fMin.value);
				v.targetSdk = String.valueOf(fTarget.value);
				v.appClass = fApp.getText().trim();
				v.icon = picked;
				v.radius = seek.getProgress();
				String err = cb.onSubmit(v);
				if (err != null) {
					error.setText(err);
					error.setVisibility(View.VISIBLE);
				} else {
					s.dismiss();
				}
			}
		};
		fApp.getEditText().setOnEditorActionListener(new TextView.OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int id, android.view.KeyEvent e) {
				if (id == EditorInfo.IME_ACTION_DONE) {
					submit.run();
					return true;
				}
				return false;
			}
		});

		LinearLayout buttons = new LinearLayout(c);
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, dp(52), 1f);
		cl.rightMargin = dp(12);
		buttons.addView(BottomSheet.button(c, LanguageLoader.get("common.cancel"), false, new View.OnClickListener() {
			public void onClick(View v) { s.dismiss(); }
		}), cl);
		buttons.addView(BottomSheet.button(c, submitText, true, new View.OnClickListener() {
			public void onClick(View v) { submit.run(); }
		}), new LinearLayout.LayoutParams(0, dp(52), 1.4f));
		LinearLayout.LayoutParams bll = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		bll.topMargin = dp(16);
		body.addView(buttons, bll);

		s.setOnDismiss(new Runnable() {
			public void run() {
				if (popup != null && popup.isShowing()) popup.dismiss();
				popup = null;
				if (sheet == s) sheet = null;
				picked = null;
			}
		});
		refreshPreview();
		s.show();
	}

	// =====================================================================
	// Field
	// =====================================================================

	/** Satu baris input bergaya InoInputLayout (satu baris, warna sesuai tema sheet). */
	private InoInputLayout input(String label, String text, int inputType, int ime) {
		InoInputLayout f = new InoInputLayout(act);
		f.setLabelText(label);
		f.setIsMultiline(false);
		f.setColors(Palette.ACCENT, Palette.OUTLINE);
		f.setTextColor(Palette.TEXT);
		f.setTextSize(16);
		f.setCornerRadius(16);
		f.setVerticalMargins(8, 8);
		f.setInputType(inputType);
		f.getEditText().setImeOptions(ime);
		f.getEditText().setHighlightColor(Palette.withAlpha(Palette.ACCENT, 0x55));
		f.setText(text == null ? "" : text);
		return f;
	}

	private void addRow(LinearLayout col, View v, int topDp) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.topMargin = dp(topDp);
		col.addView(v, lp);
	}

	private void addPair(LinearLayout col, View v1, View v2, int topDp) {
		LinearLayout row = new LinearLayout(act);
		row.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0,
		ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		a.rightMargin = dp(6);
		LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0,
		ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		b.leftMargin = dp(6);
		row.addView(v1, a);
		row.addView(v2, b);
		LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		rl.topMargin = dp(topDp);
		col.addView(row, rl);
	}

	// =====================================================================
	// SDK dropdown (popup)
	// =====================================================================

	static String sdkLabel(int api) {
		for (int i = 0; i < SDK_API.length; i++) {
			if (SDK_API[i] == api) return LanguageLoader.get("sdk.label_android", api, SDK_NAME[i]);
		}
		return LanguageLoader.get("sdk.label_api", api);
	}

	/** Daftar level untuk popup; kalau nilai awal di luar daftar (mis. 19) ikut ditambahkan. */
	static int[] sdkLevels(int current) {
		for (int i = 0; i < SDK_API.length; i++) {
			if (SDK_API[i] == current) return SDK_API;
		}
		int[] out = new int[SDK_API.length + 1];
		int j = 0;
		boolean put = false;
		for (int i = 0; i < SDK_API.length; i++) {
			if (!put && current < SDK_API[i]) {
				out[j++] = current;
				put = true;
			}
			out[j++] = SDK_API[i];
		}
		if (!put) out[j] = current;
		return out;
	}

	/** Field read-only bergaya InoInputLayout + panah; diketuk membuka popup daftar SDK. */
	private class SdkField {
		final FrameLayout root;
		final InoInputLayout field;
		final Runnable onChange;
		int value;

		SdkField(String label, String initial, int fallback, Runnable onChange) {
			this.onChange = onChange;
			int v;
			try {
				v = Integer.parseInt(initial == null ? "" : initial.trim());
			} catch (NumberFormatException e) {
				v = fallback;
			}
			if (v < 1) v = fallback;
			value = v;

			field = input(label, sdkLabel(v), InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_NEXT);
			EditText e = field.getEditText();
			e.setKeyListener(null);
			e.setCursorVisible(false);
			e.setFocusable(false);
			ArrowDrawable arrow = new ArrowDrawable(Palette.SUB);
			arrow.setBounds(0, 0, dp(24), dp(24));
			e.setCompoundDrawables(null, null, arrow, null);

			// lapisan transparan penangkap ketukan (EditText di bawahnya tidak bisa diketik/diseleksi)
			View hit = new View(act);
			hit.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) { open(); }
			});
			root = new FrameLayout(act);
			root.addView(field, new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			root.addView(hit, new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		}

		void open() {
			if (popup != null && popup.isShowing()) return;
			BottomSheet.hideKeyboard(act);
			if (focusHolder != null) focusHolder.requestFocus();   // lepas fokus dari EditText lain

			final EditText anchor = field.getEditText();
			final int[] levels = sdkLevels(value);
			int selected = 0;
			for (int i = 0; i < levels.length; i++) {
				if (levels[i] == value) selected = i;
			}

			// Daftar di dalam kartu yang membulat. Scrollbar di-inset ke dalam padding
			// supaya tidak keluar dari sudut popup.
			final ListView lv = new ListView(act);
			M3Scroll.style(lv);
			lv.setDividerHeight(0);
			lv.setSelector(new ColorDrawable(0));
			lv.setClipToPadding(false);
			lv.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
			lv.setScrollBarSize(dp(3));
			lv.setPadding(dp(6), dp(8), dp(10), dp(8));
			lv.setAdapter(new BaseAdapter() {
				public int getCount() { return levels.length; }
				public Object getItem(int i) { return levels[i]; }
				public long getItemId(int i) { return i; }
				public View getView(int i, View convert, ViewGroup parent) {
					boolean sel = levels[i] == value;

					LinearLayout item = new LinearLayout(act);
					item.setOrientation(LinearLayout.HORIZONTAL);
					item.setGravity(Gravity.CENTER_VERTICAL);
					item.setMinimumHeight(dp(48));

					// Baris terpilih diberi pill; baris lain memakai ukuran dan padding yang sama,
					// jadi teks dan centang selalu sejajar.
					LinearLayout pill = new LinearLayout(act);
					pill.setOrientation(LinearLayout.HORIZONTAL);
					pill.setGravity(Gravity.CENTER_VERTICAL);
					pill.setPadding(dp(14), 0, dp(12), 0);
					pill.setBackground(sel ? BottomSheet.round(act, Palette.withAlpha(Palette.ACCENT, 0x33), 12) : null);
					item.addView(pill, new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

					TextView t = new TextView(act);
					t.setText(sdkLabel(levels[i]));
					t.setTextSize(15);
					t.setTextColor(sel ? Palette.ACCENT : Palette.TEXT);
					pill.addView(t, new LinearLayout.LayoutParams(
						0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

					ImageView check = new ImageView(act);
					if (sel) check.setImageDrawable(IconDrawable.of(act, R.drawable.ic_check, Palette.ACCENT, dp(20)));
					pill.addView(check, new LinearLayout.LayoutParams(dp(20), dp(20)));
					return item;
				}
			});

			FrameLayout content = new FrameLayout(act);
			content.setBackground(BottomSheet.round(act, Palette.POPUP, 16));
			content.setClipToOutline(true);
			content.addView(lv, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			ru.jet.editor.core.FontManager.attach(content);   // popup punya window sendiri: pasang font aplikasi

			int h = Math.min(dp(48) * levels.length + dp(16), dp(264));
			final PopupWindow pop = new PopupWindow(content, anchor.getWidth(), h, true);
			popup = pop;
			pop.setBackgroundDrawable(new ColorDrawable(0));   // latar window transparan, sudut dari content
			pop.setOutsideTouchable(true);
			pop.setElevation(0f);
			pop.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
			lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
					value = levels[position];
					field.setText(sdkLabel(value));
					if (onChange != null) onChange.run();
					pop.dismiss();
				}
			});
			pop.setOnDismissListener(new PopupWindow.OnDismissListener() {
				public void onDismiss() {
					field.setActive(false);
					if (popup == pop) popup = null;
				}
			});
			field.setActive(true);
			pop.showAsDropDown(anchor, 0, dp(4));
			lv.setSelectionFromTop(Math.max(0, selected - 2), dp(8));
		}
	}

	/** Segitiga kecil penunjuk dropdown. */
	private static class ArrowDrawable extends Drawable {
		private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Path path = new Path();

		ArrowDrawable(int color) {
			p.setColor(color);
		}

		@Override public void draw(Canvas c) {
			Rect b = getBounds();
			float w = b.width() * 0.42f;
			float h = w * 0.5f;
			float cx = b.exactCenterX(), cy = b.exactCenterY();
			path.reset();
			path.moveTo(cx - w / 2f, cy - h / 2f);
			path.lineTo(cx + w / 2f, cy - h / 2f);
			path.lineTo(cx, cy + h / 2f);
			path.close();
			c.drawPath(path, p);
		}

		@Override public void setAlpha(int a) { p.setAlpha(a); }
		@Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
		@Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
	}

	// =====================================================================

	private void refreshPreview() {
		Context c = act;
		if (picked != null) {
			preview.setBackground(null);
			preview.setScaleType(ImageView.ScaleType.FIT_XY);
			preview.setImageBitmap(makeIcon(picked, dp(72), seek.getProgress()));
		} else if (existing != null) {
			preview.setBackground(null);
			preview.setScaleType(ImageView.ScaleType.FIT_XY);
			preview.setImageBitmap(existing);
		} else {
			preview.setBackground(BottomSheet.round(c, Palette.CARD, 16));
			preview.setScaleType(ImageView.ScaleType.CENTER);
			preview.setImageDrawable(IconDrawable.of(c, R.drawable.ic_add, Palette.SUB, dp(28)));
		}
	}

	/** Potong persegi (center crop), skala ke size x size, sudut dibulatkan radiusPct persen (50 = lingkaran). */
	public static Bitmap makeIcon(Bitmap src, int size, int radiusPct) {
		Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas cv = new Canvas(out);
		Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
		float r = size * Math.max(0, Math.min(50, radiusPct)) / 100f;
		Path clip = new Path();
		clip.addRoundRect(new RectF(0, 0, size, size), r, r, Path.Direction.CW);
		cv.clipPath(clip);
		int w = src.getWidth(), h = src.getHeight();
		int side = Math.min(w, h);
		android.graphics.Rect from = new android.graphics.Rect((w - side) / 2, (h - side) / 2,
		(w + side) / 2, (h + side) / 2);
		cv.drawBitmap(src, from, new RectF(0, 0, size, size), p);
		return out;
	}

	private int dp(float v) {
		return BottomSheet.dp(act, v);
	}

	/** ScrollView yang tingginya dibatasi sisa ruang sheet (ikut menyusut saat keyboard muncul). */
	private static class MaxScroll extends ScrollView {
		MaxScroll(Context c) {
			super(c);
		}

		@Override
		protected void onMeasure(int w, int h) {
			int screen = getResources().getDisplayMetrics().heightPixels;
			int reserve = BottomSheet.dp(getContext(), 132);   // judul + error + tombol
			int avail = MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED
			? screen : MeasureSpec.getSize(h);
			int max = Math.max(BottomSheet.dp(getContext(), 120),
			Math.min((int) (screen * 0.62f), avail - reserve));
			super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
		}
	}
}
