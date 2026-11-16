package ru.jet.editor.core;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

import ru.jet.editor.R;

/**
 * Popup menu bergaya Material 3 (tanpa AndroidX):
 * container tonal membulat, ikon di depan, item berbentuk pill saat ditekan,
 * pemisah, item checkable (centang di belakang), item bahaya (merah), item nonaktif,
 * bayangan (elevation) dan animasi scale+fade dari sudut anchor.
 *
 * Pakai:
 *   new M3Menu(ctx)
 *       .add(ID_SAVE, R.drawable.ic_save, "Save")
 *       .addCheck(ID_WRAP, R.drawable.ic_wrap_text, "Word wrap", wrapOn)
 *       .addDivider()
 *       .addDanger(ID_DEL, R.drawable.ic_delete, "Delete")
 *       .setOnItemClickListener(new M3Menu.Listener() { public void onItem(int id) { ... } })
 *       .show(anchorView);
 */
public class M3Menu {

    public interface Listener { void onItem(int id); }

    /** Lebar popup: maksimum, minimum, dan tinggi baris. */
    private static final int MAX_WIDTH_DP = 232;
    private static final int MIN_WIDTH_DP = 160;
    private static final int MIN_HEIGHT_DP = 120;
    private static final int ITEM_HEIGHT_DP = 44;

    // Warna (tema gelap, selaras dengan palet MainActivity)
    private static class Item {
        int id, icon;
        String title;
        boolean checkable, checked, danger, enabled = true, divider;
    }

    private final Context ctx;
    private final float density;
    private final ArrayList<Item> items = new ArrayList<Item>();
    private Listener listener;
    private PopupWindow window;

    public M3Menu(Context c) {
        ctx = c;
        density = c.getResources().getDisplayMetrics().density;
    }

    public M3Menu add(int id, int icon, String title) { return add(id, icon, title, true); }

    public M3Menu add(int id, int icon, String title, boolean enabled) {
        Item i = new Item();
        i.id = id; i.icon = icon; i.title = title; i.enabled = enabled;
        items.add(i);
        return this;
    }

    public M3Menu addCheck(int id, int icon, String title, boolean checked) {
        Item i = new Item();
        i.id = id; i.icon = icon; i.title = title; i.checkable = true; i.checked = checked;
        items.add(i);
        return this;
    }

    public M3Menu addDanger(int id, int icon, String title) {
        Item i = new Item();
        i.id = id; i.icon = icon; i.title = title; i.danger = true;
        items.add(i);
        return this;
    }

    public M3Menu addDivider() {
        Item i = new Item();
        i.divider = true;
        items.add(i);
        return this;
    }

    public M3Menu setOnItemClickListener(Listener l) { listener = l; return this; }

    public void dismiss() {
        if (window != null && window.isShowing()) window.dismiss();
    }

    /** Menu muncul di bawah anchor, rata kanan (cocok untuk tombol titik tiga). */
    public void show(View anchor) { show(anchor, Gravity.END, 0); }

    /**
     * @param gravity Gravity.END = sisi kanan menu sejajar sisi kanan anchor;
     *                Gravity.START = sisi kiri menu sejajar sisi kiri anchor.
     * @param xInset  geser ke arah dalam (px), dipakai mis. untuk menu long-press di row tree.
     *                Menu otomatis tampil di atas anchor bila di bawahnya tidak muat.
     */
    public void show(View anchor, int gravity, int xInset) {
        if (items.isEmpty()) return;

        // ----- kartu menu -----
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(6), dp(6), dp(6), dp(6));
        card.setMinimumWidth(dp(MIN_WIDTH_DP));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Palette.SHEET);
        bg.setCornerRadius(dp(18));
        card.setBackground(bg);
        for (int i = 0; i < items.size(); i++) card.addView(buildRow(items.get(i)));

        // ----- batas layar -----
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
        int insetTop = 0, insetBottom = 0;
        if (Build.VERSION.SDK_INT >= 23) {
            WindowInsets wi = anchor.getRootWindowInsets();
            if (wi != null) {
                if (Build.VERSION.SDK_INT >= 30) {
                    insetTop = wi.getInsets(WindowInsets.Type.statusBars()).top;
                    insetBottom = wi.getInsets(WindowInsets.Type.navigationBars()).bottom;
                } else {
                    insetTop = wi.getStableInsetTop();
                    insetBottom = wi.getStableInsetBottom();
                }
            }
        }
        int margin = dp(8);
        int minY = insetTop + margin;
        int maxY = screenH - insetBottom - margin;
        int maxW = Math.min(dp(MAX_WIDTH_DP), screenW - 2 * margin);

        // ukur: lebar dibatasi maxW (teks panjang turun ke baris kedua), tinggi penuh
        card.measure(View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int mw = card.getMeasuredWidth();
        int fullH = card.getMeasuredHeight();

        // ----- posisi: bawah atau atas anchor, mana yang lebih lega -----
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int anchorTop = loc[1];
        int anchorBottom = loc[1] + anchor.getHeight();
        int spaceBelow = maxY - anchorBottom - dp(2);
        int spaceAbove = anchorTop - minY - dp(2);
        boolean above = spaceBelow < fullH && spaceAbove > spaceBelow;
        int room = above ? spaceAbove : spaceBelow;
        // tinggi: penuh bila muat; kalau tidak, dibatasi dan daftarnya bisa di-scroll
        int mh = Math.min(fullH, Math.max(room, dp(MIN_HEIGHT_DP)));
        mh = Math.min(mh, maxY - minY);

        int y = above ? anchorTop - mh - dp(2) : anchorBottom + dp(2);
        y = Math.max(minY, Math.min(y, maxY - mh));

        boolean end = (gravity & Gravity.END) == Gravity.END || (gravity & Gravity.RIGHT) == Gravity.RIGHT;
        int x = end ? loc[0] + anchor.getWidth() - mw - xInset : loc[0] + xInset;
        x = Math.max(margin, Math.min(x, screenW - mw - margin));

        // ----- wadah: ScrollView supaya item yang tidak muat tetap bisa dijangkau -----
        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(card, new FrameLayout.LayoutParams(mw, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout wrap = new FrameLayout(ctx);
        wrap.addView(scroll, new FrameLayout.LayoutParams(mw, mh));
        FontManager.apply(wrap);

        window = new PopupWindow(wrap, mw, mh, true);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setOutsideTouchable(true);
        window.setElevation(0f);

        // animasi masuk: scale + fade dari sudut yang paling dekat anchor
        wrap.setPivotX(end ? mw : 0);
        wrap.setPivotY(above ? mh : 0);
        wrap.setScaleX(0.88f);
        wrap.setScaleY(0.88f);
        wrap.setAlpha(0f);

        window.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);

        wrap.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    private View buildRow(final Item it) {
        if (it.divider) {
            View d = new View(ctx);
            d.setBackgroundColor(Palette.withAlpha(Palette.TEXT, 0x24));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            lp.setMargins(dp(6), dp(4), dp(6), dp(4));
            d.setLayoutParams(lp);
            return d;
        }

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(ITEM_HEIGHT_DP));
        row.setPadding(dp(10), dp(4), dp(12), dp(4));
        row.setClickable(it.enabled);
        row.setAlpha(it.enabled ? 1f : 0.38f);
        if (it.enabled) row.setBackground(pillRipple());

        int tint = it.danger ? Palette.ERROR : Palette.SUB;
        if (it.icon != 0) {
            ImageView icon = new ImageView(ctx);
            icon.setImageDrawable(IconDrawable.of(ctx, it.icon, tint, dp(22)));
            row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        }

        TextView t = new TextView(ctx);
        t.setText(it.title);
        t.setTextSize(15f);
        t.setTextColor(it.danger ? Palette.ERROR : Palette.TEXT);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.leftMargin = it.icon != 0 ? dp(10) : 0;
        row.addView(t, tl);

        if (it.checkable) {
            ImageView check = new ImageView(ctx);
            check.setImageDrawable(IconDrawable.of(ctx, R.drawable.ic_check, Palette.ACCENT, dp(18)));
            check.setVisibility(it.checked ? View.VISIBLE : View.INVISIBLE);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(18), dp(18));
            cl.leftMargin = dp(10);
            row.addView(check, cl);
            if (it.checked) t.setTextColor(Palette.ACCENT);
        }

        if (it.enabled) {
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    dismiss();
                    if (listener != null) listener.onItem(it.id);
                }
            });
        }
        return row;
    }

    private RippleDrawable pillRipple() {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.BLACK);
        mask.setCornerRadius(dp(16));
        return new RippleDrawable(ColorStateList.valueOf(Palette.RIPPLE), null, mask);
    }

    private int dp(float v) { return (int) (v * density + 0.5f); }
}
