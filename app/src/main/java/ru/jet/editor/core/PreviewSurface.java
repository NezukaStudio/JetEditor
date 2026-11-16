package ru.jet.editor.core;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;

/**
 * Area preview: hasil inflate ditaruh di dalam bingkai HP, di atas latar grid titik.
 * Bisa di-zoom (cubit dua jari atau tombol +/-) dan digeser dengan satu jari.
 * Lapisan debug (batas, id, nilai) digambar di dalam layar, jadi ikut ter-zoom.
 */
public class PreviewSurface extends FrameLayout {

    /** Ukuran layar dalam dp, urutan sama dengan pilihan di bottom sheet. */
    public static final int[] SIZE_W = {320, 360, 412, 600};
    public static final int[] SIZE_H = {568, 780, 915, 960};

    private static final float MIN_ZOOM = 0.2f;
    private static final float MAX_ZOOM = 3f;
    private static final int COLOR_BOUNDS = 0xFFE53935;
    private static final int COLOR_LABEL_BG = 0xCC1E88E5;
    private static final int COLOR_VALUE_BG = 0xCC43A047;

    private final float density;
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;

    private Map<Integer, String> idNames;
    private FrameLayout phone;     // bingkai HP
    private Screen screen;         // layar: tempat hasil inflate + lapisan debug
    private TextView errorView;
    private final TextView zoomLabel;

    private boolean bounds, ids, values;
    private float zoom = 1f, panX, panY;
    private boolean needFit = true;
    private float lastX, lastY;
    private boolean scaling;

    public PreviewSurface(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        dotPaint.setColor(Palette.SUB);
        dotPaint.setAlpha(70);

        scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            public boolean onScale(ScaleGestureDetector d) {
                scaling = true;
                setZoom(zoom * d.getScaleFactor());
                return true;
            }
        });

        // kontrol zoom di pojok kanan bawah
        LinearLayout controls = new LinearLayout(c);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.addView(zoomButton("+", new Runnable() { public void run() { setZoom(zoom * 1.25f); } }),
                new LinearLayout.LayoutParams(dp(40), dp(40)));
        zoomLabel = zoomButton("100%", new Runnable() { public void run() { fit(); } });
        zoomLabel.setTextSize(11);
        controls.addView(zoomLabel, new LinearLayout.LayoutParams(dp(40), dp(40)));
        controls.addView(zoomButton("−", new Runnable() { public void run() { setZoom(zoom / 1.25f); } }),
                new LinearLayout.LayoutParams(dp(40), dp(40)));
        LayoutParamsHolder.add(this, controls, Gravity.END | Gravity.CENTER_VERTICAL, dp(12));
    }

    /**
     * Pasang hasil inflate di dalam bingkai HP. refit = true menghitung ulang zoom agar muat di layar.
     * Zoom dan posisi dipertahankan saat hanya isinya yang berubah (mengetik).
     */
    public void setContent(View content, Map<Integer, String> idNames, int sizeIndex, boolean refit) {
        this.idNames = idNames;
        clearPhone();
        if (sizeIndex < 0 || sizeIndex >= SIZE_W.length) sizeIndex = 1;
        int w = dp(SIZE_W[sizeIndex]);
        int h = dp(SIZE_H[sizeIndex]);
        int bezel = dp(10);
        int frameRadius = dp(36);

        screen = new Screen(getContext(), idNames);
        screen.setDebug(bounds, ids, values);
        screen.addView(content, new FrameLayout.LayoutParams(content.getLayoutParams()));
        // sudut layar ikut membulat dan konsentris dengan bingkai (radius bingkai dikurangi bezel),
        // supaya isi tidak keluar dari lengkung HP
        final float screenRadius = frameRadius - bezel;
        screen.setClipToOutline(true);
        screen.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), screenRadius);
            }
        });

        phone = new FrameLayout(getContext());
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(0xFF1C1C1C);
        frame.setCornerRadius(frameRadius);
        frame.setStroke(dp(2), 0xFF4A4A4A);
        phone.setBackground(frame);
        phone.setPadding(bezel, bezel, bezel, bezel);
        phone.addView(screen, new FrameLayout.LayoutParams(w, h));
        addView(phone, 0, new FrameLayout.LayoutParams(w + 2 * bezel, h + 2 * bezel, Gravity.CENTER));

        if (refit) needFit = true;
        applyTransform();
        invalidate();
    }

    /** Tampilkan pesan error di tempat layar (mis. XML rusak). */
    public void setError(String message) {
        clearPhone();
        errorView = new TextView(getContext());
        errorView.setText(message);
        errorView.setTextSize(14);
        errorView.setTextColor(Palette.ERROR);
        errorView.setPadding(dp(16), dp(16), dp(16), dp(16));
        addView(errorView, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
    }

    public void setDebug(boolean bounds, boolean ids, boolean values) {
        this.bounds = bounds;
        this.ids = ids;
        this.values = values;
        if (screen != null) screen.setDebug(bounds, ids, values);
    }

    private void clearPhone() {
        if (phone != null) removeView(phone);
        if (errorView != null) removeView(errorView);
        phone = null;
        screen = null;
        errorView = null;
    }

    // ------------------------------------------------------------ zoom & geser

    private void setZoom(float z) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
        applyTransform();
    }

    /** Zoom supaya seluruh bingkai muat di area, lalu tengahkan. */
    private void fit() {
        if (phone == null || getWidth() == 0 || getHeight() == 0) return;
        float pw = phone.getWidth(), ph = phone.getHeight();
        if (pw == 0 || ph == 0) return;
        float z = Math.min((getWidth() - dp(24)) / pw, (getHeight() - dp(24)) / ph);
        zoom = Math.max(MIN_ZOOM, Math.min(1f, z));
        panX = 0;
        panY = 0;
        applyTransform();
    }

    private void applyTransform() {
        if (phone != null) {
            phone.setScaleX(zoom);
            phone.setScaleY(zoom);
            phone.setTranslationX(panX);
            phone.setTranslationY(panY);
        }
        zoomLabel.setText(Math.round(zoom * 100) + "%");
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (needFit && phone != null && getWidth() > 0 && getHeight() > 0) {
            needFit = false;
            fit();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                scaling = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!scaling && e.getPointerCount() == 1 && phone != null) {
                    panX += e.getX() - lastX;
                    panY += e.getY() - lastY;
                    lastX = e.getX();
                    lastY = e.getY();
                    applyTransform();
                }
                break;
            default:
                break;
        }
        return true;
    }

    // ------------------------------------------------------------ latar grid

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Palette.BG);
        float step = dp(16);
        float r = dp(1);
        for (float y = step / 2; y < getHeight(); y += step) {
            for (float x = step / 2; x < getWidth(); x += step) {
                canvas.drawCircle(x, y, r, dotPaint);
            }
        }
        super.onDraw(canvas);
    }

    private TextView zoomButton(String label, final Runnable action) {
        TextView t = new TextView(getContext());
        t.setText(label);
        t.setTextSize(18);
        t.setTextColor(Palette.TEXT);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Palette.CARD);
        bg.setCornerRadius(dp(20));
        t.setBackground(bg);
        t.setClickable(true);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    /** Helper kecil agar penempatan tombol zoom tidak perlu LayoutParams di tiap pemanggil. */
    private static final class LayoutParamsHolder {
        static void add(FrameLayout parent, View child, int gravity, int margin) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
            lp.setMargins(margin, margin, margin, margin);
            parent.addView(child, lp);
        }
    }

    // ------------------------------------------------------------ layar + lapisan debug

    /** Layar perangkat: menggambar batas, id, dan nilai view di atas hasil inflate. */
    static final class Screen extends FrameLayout {
        private final Map<Integer, String> idNames;
        private final float density;
        private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect tmp = new Rect();
        private boolean bounds, ids, values;

        Screen(Context c, Map<Integer, String> idNames) {
            super(c);
            this.idNames = idNames;
            this.density = c.getResources().getDisplayMetrics().density;
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(density);
            boxPaint.setColor(COLOR_BOUNDS);
            bgPaint.setStyle(Paint.Style.FILL);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(9f * density);
            setBackgroundColor(Color.WHITE);
        }

        void setDebug(boolean bounds, boolean ids, boolean values) {
            this.bounds = bounds;
            this.ids = ids;
            this.values = values;
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (!bounds && !ids && !values) return;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                drawTree(canvas, child, child.getLeft(), child.getTop());
            }
        }

        private void drawTree(Canvas canvas, View v, int x, int y) {
            if (v.getVisibility() != VISIBLE) return;
            tmp.set(x, y, x + v.getWidth(), y + v.getHeight());
            if (bounds) canvas.drawRect(tmp, boxPaint);
            if (ids) {
                String name = idNames == null ? null : idNames.get(v.getId());
                if (name != null) label(canvas, name, x, y, COLOR_LABEL_BG);
            }
            if (values) drawValues(canvas, v, x, y);
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    View c = g.getChildAt(i);
                    drawTree(canvas, c, x + c.getLeft() - g.getScrollX(), y + c.getTop() - g.getScrollY());
                }
            }
        }

        private void drawValues(Canvas canvas, View v, int x, int y) {
            StringBuilder sb = new StringBuilder();
            sb.append(dp(v.getWidth())).append(" × ").append(dp(v.getHeight()));
            if (v.getPaddingLeft() + v.getPaddingTop() + v.getPaddingRight() + v.getPaddingBottom() > 0) {
                sb.append("\npad ").append(dp(v.getPaddingLeft())).append(",").append(dp(v.getPaddingTop()))
                  .append(",").append(dp(v.getPaddingRight())).append(",").append(dp(v.getPaddingBottom()));
            }
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
                if (m.leftMargin + m.topMargin + m.rightMargin + m.bottomMargin > 0) {
                    sb.append("\nmar ").append(dp(m.leftMargin)).append(",").append(dp(m.topMargin))
                      .append(",").append(dp(m.rightMargin)).append(",").append(dp(m.bottomMargin));
                }
            }
            label(canvas, sb.toString(), x, y + v.getHeight(), COLOR_VALUE_BG);
        }

        /** Label kecil dengan latar. Naik dari (x, y) agar tidak keluar dari bawah view. */
        private void label(Canvas canvas, String text, int x, int y, int bg) {
            String[] lines = text.split("\n");
            float lineH = textPaint.getTextSize() + 2f * density;
            float w = 0;
            for (String s : lines) w = Math.max(w, textPaint.measureText(s));
            float h = lines.length * lineH;
            float top = y - h;
            if (top < 0) top = y;
            bgPaint.setColor(bg);
            canvas.drawRect(x, top, x + w + 6f * density, top + h, bgPaint);
            float ty = top + textPaint.getTextSize();
            for (String s : lines) {
                canvas.drawText(s, x + 3f * density, ty, textPaint);
                ty += lineH;
            }
        }

        private int dp(int px) {
            return Math.round(px / density);
        }
    }
}
