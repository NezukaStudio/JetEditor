package ru.jet.editor.container;

import ru.jet.editor.core.LanguageLoader;
import ru.jet.editor.core.Palette;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import ru.jet.editor.core.IconDrawable;
import ru.jet.editor.R;
import ru.jet.editor.core.InoInputLayout;

/**
 * Bottom sheet inti untuk Jet Editor (tanpa androidx).
 *
 * Cara pakai di Activity:
 *
 *   // 1x di buildUi(), setelah root dibuat
 *   sheetHost = new BottomSheet.Host(this, root);
 *
 *   // saat insets berubah
 *   sheetHost.setInsets(insLeft, insRight, insBottom, imeBottom);
 *
 *   // onBackPressed()
 *   if (sheetHost.dismissTop()) return;
 *
 *   // sheet siap pakai
 *   sheetHost.form("Judul").field(null, "Hint", "").onSubmit(...).show();
 *   sheetHost.menu("Judul", "Sub").item(R.drawable.ic_x, "Teks", runnable).show();
 *   sheetHost.confirm(R.drawable.ic_x, "Judul", "Pesan").action("OK", runnable).show();
 *
 * Sheet custom: BottomSheet s = sheetHost.create(); s.body().addView(...); s.show();
 */
public class BottomSheet extends FrameLayout {
	
	// ===== Warna =====
	// =====================================================================
	// Callback
	// =====================================================================
	
	/** Dipanggil saat tombol submit di Form ditekan. Return null = sukses (sheet ditutup), atau pesan error. */
	public interface OnSubmit {
		String onSubmit(String[] values);
	}
	
	// =====================================================================
	// Host: pengelola tumpukan sheet + insets (1 per Activity)
	// =====================================================================
	
	public static class Host {
		private final Activity activity;
		private final FrameLayout root;
		private final List<BottomSheet> stack = new ArrayList<BottomSheet>();
		private int insLeft, insRight, insBottom, imeBottom, insTop;
		private BottomSheet permSheet;
		private TextView[] permRows;
		private ImageView[] permMarks;
		
		/** Dipanggil setiap sheet dibuka atau mulai ditutup (mis. untuk menutup menu kursor editor). */
		private Runnable onChange;
		public void setOnChange(Runnable r) { onChange = r; }
		void notifyChange() { if (onChange != null) onChange.run(); }

		public Host(Activity activity, FrameLayout root) {
			this.activity = activity;
			this.root = root;
		}
		
		/** Padding sheet mengikuti nav bar atau keyboard (mana yang lebih tinggi). */
		public void setInsets(int left, int right, int bottom, int ime) {
			insLeft = left;
			insRight = right;
			insBottom = bottom;
			imeBottom = ime;
			for (int i = 0; i < stack.size(); i++) stack.get(i).applyInsets();
		}
		
		/** Inset status bar: sheet layar penuh memakai ruang di bawahnya. */
		public void setTopInset(int top) {
			insTop = top;
			for (int i = 0; i < stack.size(); i++) stack.get(i).applyInsets();
		}

		/** Untuk onBackPressed(): tutup sheet paling atas. True kalau ada yang ditutup. */
		public boolean dismissTop() {
			if (stack.isEmpty()) return false;
			stack.get(stack.size() - 1).dismiss();
			return true;
		}
		
		
		/** Sheet kosong (dengan handle) untuk diisi sendiri lewat body(). */
		public BottomSheet create() {
			return new BottomSheet(this);
		}
		
		public Form form(String title) { return new Form(this, title); }
		public Menu menu(String title, String sub) { return new Menu(this, title, sub); }
		public Confirm confirm(int iconRes, String title, String message) {
			return new Confirm(this, iconRes, title, message);
		}
		
		// ----- Sheet izin (status per baris bisa di-refresh) -----
		
		public BottomSheet showPermission(int iconRes, String title, String desc,
		String[] names, final Runnable onGrant) {
			if (permSheet != null && permSheet.isShowing()) return permSheet;
			Context c = activity;
			final BottomSheet s = new BottomSheet(this);
			permSheet = s;
			LinearLayout body = s.body();
			
			ImageView icon = new ImageView(c);
			icon.setScaleType(ImageView.ScaleType.CENTER);
			icon.setImageDrawable(IconDrawable.of(c, iconRes, Palette.ON_ACCENT_CONT, dp(c, 28)));
			icon.setBackground(round(c, Palette.ACCENT_CONT, 18));
			body.addView(icon, new LinearLayout.LayoutParams(dp(c, 56), dp(c, 56)));
			
			LinearLayout.LayoutParams tl = wrap();
			tl.topMargin = dp(c, 16);
			body.addView(text(c, title, 22, Palette.TEXT, true), tl);
			
			LinearLayout.LayoutParams dl = wrap();
			dl.topMargin = dp(c, 8);
			dl.bottomMargin = dp(c, 16);
			body.addView(text(c, desc, 15, Palette.SUB, false), dl);
			
			permRows = new TextView[names.length];
			permMarks = new ImageView[names.length];
			for (int i = 0; i < names.length; i++) {
				LinearLayout row = new LinearLayout(c);
				row.setOrientation(LinearLayout.HORIZONTAL);
				row.setGravity(Gravity.CENTER_VERTICAL);
				row.setPadding(0, dp(c, 6), 0, dp(c, 6));
				ImageView mark = new ImageView(c);
				mark.setScaleType(ImageView.ScaleType.CENTER);
				mark.setImageDrawable(IconDrawable.of(c, R.drawable.ic_close, Palette.SUB, dp(c, 18)));
				row.addView(mark, new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)));
				TextView name = text(c, names[i], 15, Palette.SUB, false);
				LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
				nl.leftMargin = dp(c, 12);
				row.addView(name, nl);
				permRows[i] = name;
				permMarks[i] = mark;
				body.addView(row);
			}
			
			LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 52));
			gl.topMargin = dp(c, 20);
			body.addView(button(c, LanguageLoader.get("perm.grant"), true, new View.OnClickListener() {
				public void onClick(View v) { if (onGrant != null) onGrant.run(); }
			}), gl);
			
			LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 48));
			ll.topMargin = dp(c, 4);
			body.addView(button(c, LanguageLoader.get("perm.not_now"), false, new View.OnClickListener() {
				public void onClick(View v) { s.dismiss(); }
			}), ll);
			
			s.show();
			return s;
		}
		
		/** ok[i] = status izin ke-i sesuai urutan names di showPermission(). */
		public void refreshPermission(boolean[] ok) {
			if (permRows == null) return;
			for (int i = 0; i < permRows.length && i < ok.length; i++) {
				Context c = activity;
				int color = ok[i] ? Palette.ACCENT : Palette.SUB;
				permMarks[i].setImageDrawable(IconDrawable.of(c, ok[i] ? R.drawable.ic_check : R.drawable.ic_close, color, dp(c, 18)));
				permRows[i].setTextColor(color);
			}
		}
		
		public void hidePermission() {
			if (permSheet != null && permSheet.isShowing()) permSheet.dismiss();
		}
	}
	
	// =====================================================================
	// Builder: Form (input teks + tombol Cancel/Submit)
	// =====================================================================
	
	public static class Form {
		private static class Spec {
			String label, hint, text;
			boolean number, half, password;
		}
		
		private final Host host;
		private final String title;
		private String desc, submitText = LanguageLoader.get("common.ok"), cancelText = LanguageLoader.get("common.cancel");
		private boolean autoFocus;
		private OnSubmit onSubmit;
		private final List<Spec> specs = new ArrayList<Spec>();
		
		Form(Host host, String title) {
			this.host = host;
			this.title = title;
		}
		
		public Form desc(String d) { desc = d; return this; }
		public Form submitText(String t) { submitText = t; return this; }
		public Form cancelText(String t) { cancelText = t; return this; }
		/** Fokus ke kolom pertama + tampilkan keyboard setelah sheet muncul. */
		public Form autoFocus(boolean b) { autoFocus = b; return this; }
		public Form onSubmit(OnSubmit s) { onSubmit = s; return this; }
		
		/** Tambah kolom. label boleh null. Nilai yang dikirim ke onSubmit sudah di-trim, urut sesuai penambahan. */
		public Form field(String label, String hint, String text) {
			Spec s = new Spec();
			s.label = label;
			s.hint = hint;
			s.text = text;
			specs.add(s);
			return this;
		}
		
		/** Kolom terakhir hanya angka. */
		public Form number() {
			if (!specs.isEmpty()) specs.get(specs.size() - 1).number = true;
			return this;
		}
		
		/** Kolom terakhir berisi password (teks disembunyikan). */
		public Form password() {
			if (!specs.isEmpty()) specs.get(specs.size() - 1).password = true;
			return this;
		}

		/** Kolom terakhir setengah lebar; dua kolom half berurutan dijajarkan satu baris. */
		public Form half() {
			if (!specs.isEmpty()) specs.get(specs.size() - 1).half = true;
			return this;
		}
		
		public BottomSheet show() {
			final Context c = host.activity;
			final BottomSheet s = new BottomSheet(host);
			LinearLayout body = s.body();
			
			LinearLayout.LayoutParams tl = wrap();
			if (desc != null) {
				body.addView(text(c, title, 22, Palette.TEXT, true));
				LinearLayout.LayoutParams dl = wrap();
				dl.topMargin = dp(c, 4);
				dl.bottomMargin = dp(c, 20);
				body.addView(text(c, desc, 14, Palette.SUB, false), dl);
			} else {
				tl.bottomMargin = dp(c, 16);
				body.addView(text(c, title, 22, Palette.TEXT, true), tl);
			}
			
			final int n = specs.size();
			final InoInputLayout[] edits = new InoInputLayout[n];
			for (int i = 0; i < n; i++) {
				Spec sp = specs.get(i);
				InoInputLayout e = formInput(c, sp.hint, sp.text);
				if (sp.number) e.setInputType(InputType.TYPE_CLASS_NUMBER);
				if (sp.password) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
				e.getEditText().setImeOptions(i == n - 1 ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT);
				edits[i] = e;
			}
			
			int i = 0;
			while (i < n) {
				Spec sp = specs.get(i);
				boolean pair = sp.half && i + 1 < n && specs.get(i + 1).half;
				int next = i + (pair ? 2 : 1);
				int bottom = next >= n ? 0 : 12;
				if (pair) {
					LinearLayout row = new LinearLayout(c);
					row.setOrientation(LinearLayout.HORIZONTAL);
					LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0,
					ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
					l1.rightMargin = dp(c, 6);
					LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0,
					ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
					l2.leftMargin = dp(c, 6);
					row.addView(column(c, sp.label, edits[i]), l1);
					row.addView(column(c, specs.get(i + 1).label, edits[i + 1]), l2);
					LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
					rl.bottomMargin = dp(c, bottom);
					body.addView(row, rl);
				} else {
					if (sp.label != null) body.addView(label(c, sp.label));
					LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
					fl.bottomMargin = dp(c, bottom);
					body.addView(edits[i], fl);
				}
				i = next;
			}
			
			final TextView error = new TextView(c);
			error.setTextColor(Palette.ERROR);
			error.setTextSize(14);
			error.setVisibility(View.GONE);
			LinearLayout.LayoutParams el = wrap();
			el.topMargin = dp(c, 10);
			body.addView(error, el);
			
			TextWatcher clear = new TextWatcher() {
				public void beforeTextChanged(CharSequence x, int a, int b, int d) {}
				public void onTextChanged(CharSequence x, int a, int b, int d) {}
				public void afterTextChanged(Editable e) { error.setVisibility(View.GONE); }
			};
			for (int k = 0; k < n; k++) edits[k].getEditText().addTextChangedListener(clear);
			
			final Runnable submit = new Runnable() {
				public void run() {
					String[] values = new String[n];
					for (int k = 0; k < n; k++) values[k] = edits[k].getText().trim();
					String err = onSubmit != null ? onSubmit.onSubmit(values) : null;
					if (err != null) {
						error.setText(err);
						error.setVisibility(View.VISIBLE);
					} else {
						s.dismiss();
					}
				}
			};
			if (n > 0) {
				edits[n - 1].getEditText().setOnEditorActionListener(new TextView.OnEditorActionListener() {
					public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent e) {
						if (actionId == EditorInfo.IME_ACTION_DONE) {
							submit.run();
							return true;
						}
						return false;
					}
				});
			}
			
			LinearLayout buttons = new LinearLayout(c);
			buttons.setOrientation(LinearLayout.HORIZONTAL);
			LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, dp(c, 52), 1f);
			cancelLp.rightMargin = dp(c, 12);
			buttons.addView(button(c, cancelText, false, new View.OnClickListener() {
				public void onClick(View v) { s.dismiss(); }
			}), cancelLp);
			buttons.addView(button(c, submitText, true, new View.OnClickListener() {
				public void onClick(View v) { submit.run(); }
			}), new LinearLayout.LayoutParams(0, dp(c, 52), 1.4f));
			LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			bl.topMargin = dp(c, 20);
			body.addView(buttons, bl);
			
			if (autoFocus && n > 0) {
				final EditText first = edits[0].getEditText();
				s.setOnShown(new Runnable() {
					public void run() {
						first.requestFocus();
						InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
						if (imm != null) imm.showSoftInput(first, InputMethodManager.SHOW_IMPLICIT);
					}
				});
			}
			s.show();
			return s;
		}
		
		private static LinearLayout column(Context c, String label, InoInputLayout e) {
			LinearLayout col = new LinearLayout(c);
			col.setOrientation(LinearLayout.VERTICAL);
			if (label != null) col.addView(label(c, label));
			col.addView(e, new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			return col;
		}
	}
	
	// =====================================================================
	// Builder: Menu (daftar baris dengan icon + chevron)
	// =====================================================================
	
	public static class Menu {
		private static class Item {
			int icon;
			String text;
			boolean danger;
			Runnable action;
		}
		
		private final Host host;
		private final String title, sub;
		private final List<Item> items = new ArrayList<Item>();
		
		Menu(Host host, String title, String sub) {
			this.host = host;
			this.title = title;
			this.sub = sub;
		}
		
		public Menu item(int iconRes, String text, Runnable action) { return add(iconRes, text, false, action); }
		public Menu danger(int iconRes, String text, Runnable action) { return add(iconRes, text, true, action); }
		
		private Menu add(int iconRes, String text, boolean danger, Runnable action) {
			Item it = new Item();
			it.icon = iconRes;
			it.text = text;
			it.danger = danger;
			it.action = action;
			items.add(it);
			return this;
		}
		
		/** Aksi item dijalankan SETELAH sheet selesai menutup. */
		public BottomSheet show() {
			Context c = host.activity;
			final BottomSheet s = new BottomSheet(host);
			LinearLayout body = s.body();
			
			TextView t = text(c, title, 24, Palette.TEXT, true);
			t.setSingleLine(true);
			t.setEllipsize(TextUtils.TruncateAt.END);
			body.addView(t);
			
			if (sub != null) {
				LinearLayout.LayoutParams sl = wrap();
				sl.topMargin = dp(c, 4);
				sl.bottomMargin = dp(c, 16);
				body.addView(text(c, sub, 15, Palette.SUB, false), sl);
			}
			
			for (int i = 0; i < items.size(); i++) {
				Item it = items.get(i);
				body.addView(menuRow(s, it.icon, it.text, it.danger, i == 0 ? 0 : 14, it.action));
			}
			s.show();
			return s;
		}
	}
	
	// =====================================================================
	// Builder: Confirm (icon + judul + pesan + tombol penuh)
	// =====================================================================
	
	public static class Confirm {
		private final Host host;
		private final int iconRes;
		private final String title, message;
		private final List<String> labels = new ArrayList<String>();
		private final List<Runnable> actions = new ArrayList<Runnable>();
		private final List<Boolean> dangers = new ArrayList<Boolean>();
		
		Confirm(Host host, int iconRes, String title, String message) {
			this.host = host;
			this.iconRes = iconRes;
			this.title = title;
			this.message = message;
		}
		
		/** Tambah tombol netral. action null = hanya menutup sheet. Aksi jalan setelah sheet tertutup. */
		public Confirm action(String label, Runnable action) {
			return add(label, action, false);
		}

		/** Tombol aksi berbahaya (misalnya hapus): diisi warna error M3. Ikon ikut bernuansa error. */
		public Confirm dangerAction(String label, Runnable action) {
			return add(label, action, true);
		}

		private Confirm add(String label, Runnable action, boolean danger) {
			labels.add(label);
			actions.add(action);
			dangers.add(danger);
			return this;
		}
		
		public BottomSheet show() {
			Context c = host.activity;
			final BottomSheet s = new BottomSheet(host);
			LinearLayout body = s.body();
			
			boolean danger = dangers.contains(Boolean.TRUE);
			int badge = danger ? Palette.ERROR_CONT : Palette.ACCENT_CONT;
			int fg = danger ? Palette.ON_ERROR_CONT : Palette.ON_ACCENT_CONT;
			FrameLayout badgeView = new FrameLayout(c);
			badgeView.setBackground(round(c, badge, 28));
			ImageView ic = new ImageView(c);
			ic.setImageDrawable(IconDrawable.of(c, iconRes, fg, dp(c, 28)));
			badgeView.addView(ic, new FrameLayout.LayoutParams(dp(c, 28), dp(c, 28), Gravity.CENTER));
			LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(c, 56), dp(c, 56));
			il.gravity = Gravity.CENTER_HORIZONTAL;
			body.addView(badgeView, il);
			
			TextView t = text(c, title, 22, Palette.TEXT, true);
			t.setGravity(Gravity.CENTER);
			t.setSingleLine(true);
			t.setEllipsize(TextUtils.TruncateAt.END);
			LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			tl.topMargin = dp(c, 12);
			body.addView(t, tl);
			
			LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			ml.topMargin = dp(c, 16);
			ml.bottomMargin = dp(c, 20);
			body.addView(text(c, message, 15, Palette.SUB, false), ml);
			
			for (int i = 0; i < labels.size(); i++) {
				final Runnable a = actions.get(i);
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 56));
				lp.topMargin = dp(c, i == 0 ? 0 : 10);
				View.OnClickListener click = new View.OnClickListener() {
					public void onClick(View v) {
						if (a != null) s.setOnDismiss(a);
						s.dismiss();
					}
				};
				body.addView(dangers.get(i)
						? dangerButton(c, labels.get(i), click)
						: confirmButton(c, labels.get(i), click), lp);
			}
			s.show();
			return s;
		}
	}
	
	// =====================================================================
	// Komponen kecil (bisa dipakai juga untuk sheet custom)
	// =====================================================================
	
	public static TextView text(Context c, CharSequence s, int sp, int color, boolean bold) {
		TextView t = new TextView(c);
		t.setText(s);
		t.setTextColor(color);
		t.setTextSize(sp);
		if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
		return t;
	}
	
	public static TextView label(Context c, String text) {
		TextView t = new TextView(c);
		t.setText(text);
		t.setTextColor(Palette.SUB);
		t.setTextSize(13);
		t.setPadding(dp(c, 4), 0, 0, dp(c, 4));
		return t;
	}
	
	/** Tombol sheet: filled = tombol utama (aksen), selain itu tombol teks. */
	public static TextView button(Context c, String text, boolean filled, View.OnClickListener l) {
		TextView b = new TextView(c);
		b.setText(text);
		b.setGravity(Gravity.CENTER);
		b.setClickable(true);
		b.setOnClickListener(l);
		if (filled) {
			b.setTextColor(Palette.ON_ACCENT);
			b.setTextSize(16);
			b.setTypeface(Typeface.DEFAULT_BOLD);
			b.setBackground(ripple(c, round(c, Palette.ACCENT, 28), 28));
		} else {
			b.setTextColor(Palette.ACCENT);
			b.setTextSize(15);
			b.setBackground(ripple(c, null, 28));
		}
		return b;
	}
	
	/** Tombol penuh bergaya netral (dialog konfirmasi). */
	public static TextView confirmButton(Context c, String text, View.OnClickListener l) {
		TextView b = new TextView(c);
		b.setText(text);
		b.setGravity(Gravity.CENTER);
		b.setTextColor(Palette.TEXT);
		b.setTextSize(16);
		b.setTypeface(Typeface.DEFAULT_BOLD);
		b.setClickable(true);
		b.setOnClickListener(l);
		b.setBackground(ripple(c, round(c, Palette.POPUP, 28), 28));
		return b;
	}

	/** Tombol penuh untuk aksi berbahaya: isi error M3, teks onError. */
	public static TextView dangerButton(Context c, String text, View.OnClickListener l) {
		TextView b = new TextView(c);
		b.setText(text);
		b.setGravity(Gravity.CENTER);
		b.setTextColor(Palette.ON_ERROR);
		b.setTextSize(16);
		b.setTypeface(Typeface.DEFAULT_BOLD);
		b.setClickable(true);
		b.setOnClickListener(l);
		b.setBackground(ripple(c, round(c, Palette.ERROR, 28), 28));
		return b;
	}
	
	/** Field satu baris untuk Form: InoInputLayout dengan warna tema sheet. */
	public static InoInputLayout formInput(Context c, String hint, String text) {
		InoInputLayout f = new InoInputLayout(c);
		f.setLabelText(hint == null ? "" : hint);
		f.setIsMultiline(false);
		f.setColors(Palette.ACCENT, Palette.OUTLINE);
		f.setTextColor(Palette.TEXT);
		f.setTextSize(16);
		f.setCornerRadius(16);
		f.setVerticalMargins(8, 0);
		f.setText(text == null ? "" : text);
		return f;
	}

	public static EditText editText(Context c, String hint, String text) {
		EditText e = new EditText(c);
		e.setHint(hint);
		e.setHintTextColor(Palette.SUB);
		e.setText(text);
		e.setTextColor(Palette.TEXT);
		e.setTextSize(16);
		e.setSingleLine(true);
		e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		e.setHighlightColor(Palette.withAlpha(Palette.ACCENT, 0x55));
		e.setBackground(round(c, Palette.BG, 16));
		e.setPadding(dp(c, 18), 0, dp(c, 18), 0);
		return e;
	}
	
	/**
	 * Baris menu: kartu bulat sendiri dengan lencana ikon bulat di kiri, teks, dan chevron.
	 * Aksi dijalankan setelah sheet tertutup.
	 */
	public static View menuRow(final BottomSheet s, int iconRes, String text, boolean danger,
	int topMarginDp, final Runnable action) {
		Context c = s.getContext();
		// Baris memakai surface container tinggi. Aksi berbahaya: badge error container, teks error.
		int bg = Palette.POPUP;
		int badge = danger ? Palette.ERROR_CONT : Palette.ACCENT_CONT;
		int fg = danger ? Palette.ON_ERROR_CONT : Palette.ON_ACCENT_CONT;
		int textColor = danger ? Palette.ERROR : Palette.TEXT;

		LinearLayout row = new LinearLayout(c);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(dp(c, 12), 0, dp(c, 16), 0);
		row.setBackground(ripple(c, round(c, bg, 24), 24));
		row.setClickable(true);

		FrameLayout badgeView = new FrameLayout(c);
		badgeView.setBackground(round(c, badge, 20));
		ImageView ic = new ImageView(c);
		ic.setImageDrawable(IconDrawable.of(c, iconRes, fg, dp(c, 24)));
		badgeView.addView(ic, new FrameLayout.LayoutParams(dp(c, 24), dp(c, 24), Gravity.CENTER));
		row.addView(badgeView, new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));

		LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0,
		ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		tl.leftMargin = dp(c, 16);
		row.addView(text(c, text, 17, textColor, false), tl);

		ImageView ch = new ImageView(c);
		ch.setImageDrawable(IconDrawable.of(c, R.drawable.ic_chevron, danger ? Palette.ERROR : Palette.SUB, dp(c, 24)));
		row.addView(ch, new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)));

		row.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (action != null) s.setOnDismiss(action);
				s.dismiss();
			}
		});
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 68));
		lp.topMargin = dp(c, topMarginDp);
		row.setLayoutParams(lp);
		return row;
	}

	// ----- util (tanpa Activity) -----
	
	static LinearLayout.LayoutParams wrap() {
		return new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
	}
	
	public static int dp(Context c, float v) {
		return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
	}
	
	public static GradientDrawable round(Context c, int color, float radiusDp) {
		GradientDrawable g = new GradientDrawable();
		g.setColor(color);
		g.setCornerRadius(dp(c, radiusDp));
		return g;
	}
	
	public static Drawable ripple(Context c, Drawable bg, float radiusDp) {
		return new RippleDrawable(ColorStateList.valueOf(Palette.RIPPLE), bg, round(c, 0xFF000000, radiusDp));
	}
	
	public static void hideKeyboard(Activity a) {
		View f = a.getCurrentFocus();
		if (f != null) {
			InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
		}
	}
	
	// =====================================================================
	// Inti sheet: scrim + panel + animasi + drag untuk menutup
	// =====================================================================
	
	private final Host host;
	private final View scrim;
	private final LinearLayout panel;
	private final int slop;
	private boolean showing, dragging, downInside, grabbed, grabMoved;
	private float downX, downY, lastY, rawTy, ty;
	private int activeId = -1;
	private VelocityTracker vt;
	private ValueAnimator anim;
	private Runnable onShown, onDismiss;
	private View scrollChild;
	private boolean expandable;
	private boolean openFull;
	/** False = sheet tidak bisa ditutup dengan menarik. Tetap bisa ditutup lewat tombol atau ketuk area gelap. */
	private boolean draggable = true;
	private int baseH, expandPx;
	private GradientDrawable panelBg;
	/** Isi yang ikut melebar saat ditarik ke layar penuh; tingginya = growBase + expandPx - inset atas. */
	private View growView;
	private int growBase;
	
	public BottomSheet(Host host) {
		super(host.activity);
		this.host = host;
		Context c = host.activity;
		slop = ViewConfiguration.get(c).getScaledTouchSlop();
		
		scrim = new View(c);
		scrim.setBackgroundColor(0x99000000);
		addView(scrim, new LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		
		panel = new LinearLayout(c);
		panel.setOrientation(LinearLayout.VERTICAL);
		float r = dp(c, 28);
		GradientDrawable g = new GradientDrawable();
		g.setColor(Palette.SHEET);
		g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
		panelBg = g;
		panel.setBackground(g);
		panel.setClickable(true);   // sentuhan di dalam sheet tidak tembus ke scrim
		addView(panel, new LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
		Gravity.BOTTOM));
		
		View handle = new View(c);
		handle.setBackground(round(c, Palette.withAlpha(Palette.TEXT, 0x55), 2));
		LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(c, 36), dp(c, 4));
		hl.gravity = Gravity.CENTER_HORIZONTAL;
		hl.bottomMargin = dp(c, 20);
		panel.addView(handle, hl);
		
		applyInsets();
	}
	
	private int dp(float v) { return dp(getContext(), v); }
	
	public LinearLayout body() { return panel; }
	public boolean isShowing() { return showing; }
	public void setOnShown(Runnable r) { onShown = r; }
	public void setOnDismiss(Runnable r) { onDismiss = r; }
	/** View scroll di dalam sheet: selama masih bisa digulir ke atas, tarik ke bawah menggulir isinya, bukan menutup sheet. */
	public void setScrollChild(View v) { scrollChild = v; }

	/** Aktifkan mode layar penuh: sheet bisa ditarik ke atas sampai menutupi seluruh layar. */
	public void setExpandable(boolean on) { expandable = on; }
	/** Sheet langsung melebar ke layar penuh setelah muncul (tetap bisa ditarik turun). */
	public void setOpenFull(boolean on) { openFull = on; }
	/** Matikan tarik-untuk-menutup. Gulir isi sheet tidak lagi ikut menutup sheet. */
	public void setDraggable(boolean on) { draggable = on; }
	/**
	 * Isi sheet (mis. ScrollView) yang ikut melebar saat sheet ditarik ke layar penuh.
	 * baseHeightPx = tinggi isi saat sheet biasa. Tinggi dihitung ulang di setiap langkah animasi
	 * tanpa mengubah layout weight, jadi tidak ada lompatan di awal animasi.
	 */
	public void setGrowView(View v, int baseHeightPx) {
		growView = v;
		growBase = baseHeightPx;
		if (expandPx > 0) refreshLayout();
	}
	/** True selama sheet sudah ditarik lebih dari posisi biasa (termasuk saat animasi). */
	public boolean isExpanded() { return expandPx > 0; }

	/** Tambahan tinggi di atas tinggi isi. 0 = biasa, maxExpand() = layar penuh. */
	private int maxExpand() {
		int screen = getResources().getDisplayMetrics().heightPixels;
		return Math.max(0, screen - baseH);
	}

	/** 0 = sheet biasa, 1 = layar penuh. Dipakai untuk inset dan radius yang berubah halus. */
	private float expandFraction() {
		int max = maxExpand();
		return expandable && max > 0 ? Math.min(1f, expandPx / (float) max) : 0f;
	}

	private void applyExpand(int px) {
		int clamped = Math.max(0, Math.min(px, maxExpand()));
		if (clamped == expandPx) return;
		expandPx = clamped;
		refreshLayout();
	}

	/** Hitung ulang tinggi panel, padding, isi, dan radius dari expandPx. Hanya properti yang berubah yang disentuh. */
	private void refreshLayout() {
		float t = expandFraction();
		int insTopNow = Math.round(host.insTop * t);

		panel.setPadding(dp(24) + host.insLeft, dp(12) + insTopNow, dp(24) + host.insRight,
				dp(24) + Math.max(host.insBottom, host.imeBottom));

		FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
		lp.height = expandPx == 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : baseH + expandPx;
		panel.setLayoutParams(lp);

		if (growView != null) {
			// total isi = padding atas + isi lain + growView + padding bawah = baseH + expandPx
			ViewGroup.LayoutParams gl = growView.getLayoutParams();
			gl.height = Math.max(0, growBase + expandPx - insTopNow);
			growView.setLayoutParams(gl);
		}

		// Radius mengecil bertahap: 28dp saat biasa, 0 saat layar penuh (ikut tarikan).
		float r = dp(28) * (1f - t);
		panelBg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
	}

	private void animateExpand(final int target) {
		cancelAnim();
		final int from = expandPx;
		if (from == target) {
			applyExpand(target);
			return;
		}
		long dur = 200 + (long) Math.min(180f, Math.abs(target - from) * 180f / Math.max(1, maxExpand()));
		final ValueAnimator a = ValueAnimator.ofInt(from, target);
		a.setDuration(dur);
		a.setInterpolator(new DecelerateInterpolator(1.5f));
		a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			public void onAnimationUpdate(ValueAnimator v) {
				applyExpand((Integer) v.getAnimatedValue());
			}
		});
		a.addListener(new AnimatorListenerAdapter() {
			private boolean canceled;
			@Override public void onAnimationCancel(Animator an) { canceled = true; }
			@Override public void onAnimationEnd(Animator an) {
				if (canceled) return;
				applyExpand(target);
				if (anim == a) anim = null;
			}
		});
		anim = a;
		a.start();
	}
	
	void applyInsets() {
		if (panel.getLayoutParams() == null) return;
		refreshLayout();
	}
	
	public void show() {
		if (showing) return;
		showing = true;
		host.stack.add(this);
		host.notifyChange();
		scrim.setAlpha(0f);
		panel.setTranslationY(getResources().getDisplayMetrics().heightPixels);
		host.root.addView(this, new FrameLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			public boolean onPreDraw() {
				getViewTreeObserver().removeOnPreDrawListener(this);
				if (!showing) return true;
				baseH = panel.getHeight();
				setTy(panel.getHeight());
				settleTo(0f, 0f, new Runnable() {
					public void run() {
						if (showing && onShown != null) onShown.run();
						if (showing && openFull && expandable) animateExpand(maxExpand());
					}
				});
				return true;
			}
		});
	}
	
	public void dismiss() { dismiss(0f); }
	
	private void dismiss(float vy) {
		if (!showing) return;
		showing = false;
		host.notifyChange();
		dragging = false;
		hideKeyboard(host.activity);
		settleTo(panel.getHeight(), vy, new Runnable() {
			public void run() {
				host.stack.remove(BottomSheet.this);
				host.root.removeView(BottomSheet.this);
				if (onDismiss != null) onDismiss.run();
			}
		});
	}
	
	private void setTy(float v) {
		ty = v;
		panel.setTranslationY(v);
		int h = panel.getHeight();
		float a = h > 0 ? 1f - Math.max(0f, Math.min(1f, v / h)) : 1f;
		scrim.setAlpha(a);
	}
	
	private void cancelAnim() {
		if (anim != null) {
			ValueAnimator a = anim;
			anim = null;
			a.cancel();
		}
	}
	
	private void settleTo(final float target, float vy, final Runnable end) {
		cancelAnim();
		float dist = Math.abs(target - ty);
		if (dist < 1f) {
			setTy(target);
			if (end != null) end.run();
			return;
		}
		float density = getResources().getDisplayMetrics().density;
		long dur = 170 + (long) Math.min(190f, dist / density * 0.6f);
		if (vy != 0f && (vy > 0f) == (target > ty)) {
			long byVelocity = (long) (dist / Math.abs(vy) * 1000f * 2f);
			dur = Math.max(120, Math.min(dur, byVelocity));
		}
		final ValueAnimator a = ValueAnimator.ofFloat(ty, target);
		a.setDuration(dur);
		a.setInterpolator(new DecelerateInterpolator(1.5f));
		a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			public void onAnimationUpdate(ValueAnimator v) {
				setTy((Float) v.getAnimatedValue());
			}
		});
		a.addListener(new AnimatorListenerAdapter() {
			private boolean canceled;
			@Override public void onAnimationCancel(Animator an) { canceled = true; }
			@Override public void onAnimationEnd(Animator an) {
				if (canceled) return;
				setTy(target);
				if (anim == a) anim = null;
				if (end != null) end.run();
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
	
	private void startDrag() {
		dragging = true;
		if (expandable && expandPx == 0) baseH = panel.getHeight();   // ukur ulang sebelum melebar
		rawTy = ty - expandPx;
		if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
	}
	
	private boolean insideScrollChild(MotionEvent ev) {
		int[] loc = new int[2];
		scrollChild.getLocationOnScreen(loc);
		float ry = ev.getRawY();
		return ry >= loc[1] && ry <= loc[1] + scrollChild.getHeight();
	}

	private boolean tryStartDrag(MotionEvent ev) {
		if (!draggable) return false;
		int i = ev.findPointerIndex(activeId);
		if (i < 0) return false;
		float dx = ev.getX(i) - downX, dy = ev.getY(i) - downY;
		if (scrollChild != null && insideScrollChild(ev)) {
			if (dy > 0f && ty <= 0f && scrollChild.canScrollVertically(-1)) return false;   // isi list naik dulu
			if (dy < 0f && ty <= 0f && scrollChild.canScrollVertically(1)) return false;    // isi list turun dulu
		}
		if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)
			&& (dy > 0f || ty > 0f || expandPx > 0 || (expandable && dy < 0f))) {
			startDrag();
			lastY = ev.getY(i);
			return true;
		}
		return false;
	}
	
	private void endDrag() {
		float vy = 0f;
		if (vt != null) {
			vt.computeCurrentVelocity(1000);
			vy = vt.getYVelocity();
		}
		if (expandable && rawTy < 0f) {
			int max = maxExpand();
			boolean goFull;
			if (vy < -dp(900)) goFull = true;            // lempar ke atas
			else if (vy > dp(900)) goFull = false;       // lempar ke bawah
			else goFull = expandPx > max * 0.35f;        // lewat 35% jarak ke layar penuh
			animateExpand(goFull ? max : 0);
			return;
		}
		boolean close;
		if (grabbed && !grabMoved) close = false;                 // cuma ketukan saat animasi masuk
		else if (vy > dp(900)) close = true;                      // lempar ke bawah
		else if (vy < -dp(900)) close = false;                    // lempar ke atas
		else close = ty > panel.getHeight() * 0.35f;              // lewat 35% tinggi sheet
		if (close) dismiss(vy); else settleTo(0f, vy, null);
	}
	
	private void resetTouch() {
		dragging = false;
		grabbed = false;
		grabMoved = false;
		activeId = -1;
		recycleTracker();
	}
	
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (!showing) return true;       // sedang menutup: sentuhan diabaikan
		int action = ev.getActionMasked();
		if (action == MotionEvent.ACTION_DOWN) {
			resetTouch();
			vt = VelocityTracker.obtain();
			activeId = ev.getPointerId(0);
			downX = ev.getX();
			downY = lastY = ev.getY();
			downInside = downY >= panel.getTop() + panel.getTranslationY();
			if (anim != null && downInside) {
				// disentuh saat animasi masuk/kembali: tangkap supaya tidak macet
				grabbed = true;
				cancelAnim();
				startDrag();
				vt.addMovement(ev);
				return true;
			}
		}
		if (vt != null) vt.addMovement(ev);
		if (action == MotionEvent.ACTION_MOVE && downInside && !dragging) tryStartDrag(ev);
		if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && !dragging) {
			resetTouch();
		}
		return dragging;
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (!showing) return true;
		if (vt == null) vt = VelocityTracker.obtain();
		vt.addMovement(ev);
		switch (ev.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				return true;
			case MotionEvent.ACTION_MOVE: {
				int i = ev.findPointerIndex(activeId);
				if (i < 0) break;
				float y = ev.getY(i);
				if (!dragging && downInside) tryStartDrag(ev);
				if (dragging) {
					if (Math.abs(y - downY) > slop) grabMoved = true;
					rawTy += y - lastY;
					lastY = y;
					if (expandable && rawTy < 0f) {
						// ke atas: panel melebar mengikuti jari sampai layar penuh
						float up = -rawTy;
						int max = maxExpand();
						float e = up <= max ? up : max + (up - max) * 0.15f;
						setTy(0f);
						applyExpand(Math.round(e));
					} else if (expandable) {
						applyExpand(0);
						setTy(Math.max(0f, rawTy));
					} else {
						// ke atas diredam (rubber band), ke bawah mengikuti jari
						float v = rawTy >= 0f ? rawTy : Math.max(rawTy * 0.15f, -dp(16));
						setTy(v);
					}
				}
				break;
			}
			case MotionEvent.ACTION_UP:
				if (dragging) {
					endDrag();
				} else if (!downInside && Math.abs(ev.getX() - downX) < slop
					&& Math.abs(ev.getY() - downY) < slop) {
					dismiss();           // ketuk di area gelap
				}
				resetTouch();
				break;
			case MotionEvent.ACTION_CANCEL:
				if (dragging) endDrag();
				resetTouch();
				break;
		}
		return true;
	}
	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		cancelAnim();
		recycleTracker();
	}
}
