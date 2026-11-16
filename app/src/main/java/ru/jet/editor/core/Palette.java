package ru.jet.editor.core;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;

import java.util.Arrays;

/**
 * Palet warna chrome aplikasi (bukan palet editor kode), gaya Material 3 Expressive.
 *
 * Sumber warna, berurutan:
 *  1. Warna dinamis (Android 12+, saat "Dynamic color" aktif): tonal palette dari wallpaper
 *     (system_accent1 dan system_neutral1/2) langsung dari sistem.
 *  2. Warna aksen pilihan sendiri: tonal palette M3 (tone 0..100) dengan hue seed.
 *  3. Ungu M3 bawaan.
 *
 * Peran token mengikuti skema M3 (tone dalam tanda kurung, terang / gelap):
 *   ACCENT = primary (40 / 80), ON_ACCENT = onPrimary (100 / 20),
 *   ACCENT_CONT = primaryContainer (90 / 30), ON_ACCENT_CONT = onPrimaryContainer (10 / 90),
 *   BG = surface (98 / 6), DRAWER = surfaceContainerLow (96 / 10),
 *   CARD = surfaceContainer (94 / 12), SHEET = surfaceContainerHigh (92 / 17),
 *   POPUP = surfaceContainerHighest (90 / 22), TEXT = onSurface (10 / 90),
 *   SUB = onSurfaceVariant (30 / 80), OUTLINE = outline (50 / 60).
 *
 * Panggil {@link #load(Context)} sebelum membangun UI.
 */
public final class Palette {
    /** Seed bawaan: primary M3 baseline (ungu). */
    private static final int DEFAULT_SEED = 0xFF6750A4;

    public static boolean dark = true;
    /** true bila warna sekarang berasal dari wallpaper (Dynamic color). */
    public static boolean dynamicActive;

    public static int BG, DRAWER, CARD, SHEET, POPUP, TEXT, SUB, OUTLINE, DIVIDER, RIPPLE;
    public static int ACCENT, ON_ACCENT, ACCENT_CONT, ON_ACCENT_CONT, ERROR;
    /** Peran error M3: teks di atas tombol merah, dan latar serta isi baris berbahaya. */
    public static int ON_ERROR, ERROR_CONT, ON_ERROR_CONT;

    private Palette() { }

    /** Hitung ulang palet dari AppPrefs. Aman dipanggil berkali-kali. */
    public static void load(Context c) {
        dark = AppPrefs.isDark(c);
        dynamicActive = AppPrefs.isDynamic(c) && Build.VERSION.SDK_INT >= 31;

        if (dynamicActive) {
            loadSystem(c);
        } else {
            int seed = AppPrefs.appColor(c);
            loadTonal(seed != 0 ? seed : DEFAULT_SEED);
        }
        DIVIDER = withAlpha(OUTLINE, 0x33);
        RIPPLE = withAlpha(TEXT, 0x1F);
        // Peran error M3 (baseline): tema terang dan gelap.
        ERROR = dark ? 0xFFFFB4AB : 0xFFBA1A1A;
        ON_ERROR = dark ? 0xFF690005 : 0xFFFFFFFF;
        ERROR_CONT = dark ? 0xFF93000A : 0xFFFFDAD6;
        ON_ERROR_CONT = dark ? 0xFFFFDAD6 : 0xFF410002;
    }

    /**
     * Tone M3 dan indeks palet sistem yang sesuai. Di Android, system_*_N memakai N = 1000 - tone * 10
     * (system_*_0 = putih, system_*_1000 = hitam). Tone di antara dua langkah diinterpolasi.
     */
    private static final int[] TONES = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99, 100};
    private static final int[] IDX   = {1000, 900, 800, 700, 600, 500, 400, 300, 200, 100, 50, 10, 0};

    /** Palet dari wallpaper (Dynamic color). Nilai tone mengikuti skema M3, sama dengan palet non-dinamis. */
    private static void loadSystem(Context c) {
        if (dark) {
            ACCENT = tone(c, "system_accent1_", 80);            // primary 80
            ON_ACCENT = tone(c, "system_accent1_", 20);         // onPrimary 20
            ACCENT_CONT = tone(c, "system_accent1_", 30);       // primaryContainer 30
            ON_ACCENT_CONT = tone(c, "system_accent1_", 90);    // onPrimaryContainer 90

            BG = tone(c, "system_neutral1_", 6);                // surface 6
            DRAWER = tone(c, "system_neutral1_", 10);           // surfaceContainerLow 10
            CARD = tone(c, "system_neutral1_", 12);             // surfaceContainer 12
            SHEET = tone(c, "system_neutral1_", 17);            // surfaceContainerHigh 17
            POPUP = tone(c, "system_neutral1_", 22);            // surfaceContainerHighest 22
            TEXT = tone(c, "system_neutral1_", 90);             // onSurface 90
            SUB = tone(c, "system_neutral2_", 80);              // onSurfaceVariant 80
            OUTLINE = tone(c, "system_neutral2_", 60);          // outline 60
        } else {
            ACCENT = tone(c, "system_accent1_", 40);            // primary 40
            ON_ACCENT = tone(c, "system_accent1_", 100);        // onPrimary 100
            ACCENT_CONT = tone(c, "system_accent1_", 90);       // primaryContainer 90
            ON_ACCENT_CONT = tone(c, "system_accent1_", 10);    // onPrimaryContainer 10

            BG = tone(c, "system_neutral1_", 98);               // surface 98
            DRAWER = tone(c, "system_neutral1_", 96);           // surfaceContainerLow 96
            CARD = tone(c, "system_neutral1_", 94);             // surfaceContainer 94
            SHEET = tone(c, "system_neutral1_", 92);            // surfaceContainerHigh 92
            POPUP = tone(c, "system_neutral1_", 90);            // surfaceContainerHighest 90
            TEXT = tone(c, "system_neutral1_", 10);             // onSurface 10
            SUB = tone(c, "system_neutral2_", 30);              // onSurfaceVariant 30
            OUTLINE = tone(c, "system_neutral2_", 50);          // outline 50
        }
    }

    /** Warna palet sistem pada tone tertentu (0..100), diinterpolasi di antara langkah yang tersedia. */
    private static int tone(Context c, String family, float tone) {
        float t = Math.max(0f, Math.min(100f, tone));
        for (int i = 0; i < TONES.length - 1; i++) {
            if (t <= TONES[i + 1]) {
                float f = (t - TONES[i]) / (float) (TONES[i + 1] - TONES[i]);
                return mix(res(c, family + IDX[i]), res(c, family + IDX[i + 1]), f);
            }
        }
        return res(c, family + IDX[IDX.length - 1]);
    }

    /**
     * Tonal palette M3 dari satu warna seed. Hue seed dipertahankan. Tingkat tone mengikuti
     * skema M3 baseline: primary 40/80, container 90/30, surface 98/6 (terang/gelap), dan
     * tangga surface container Low, Container, High, Highest yang bertingkat jelas.
     */
    private static void loadTonal(int seed) {
        float[] hsl = toHsl(seed);
        float h = hsl[0];
        float s = Math.max(0.40f, Math.min(0.95f, hsl[1]));
        float ns = 0.05f;   // rona netral surface: tipis, ikut hue seed

        if (dark) {
            ACCENT = fromHsl(h, s, 0.80f);                // primary tone 80
            ON_ACCENT = fromHsl(h, s * 0.6f, 0.20f);      // onPrimary tone 20
            ACCENT_CONT = fromHsl(h, s * 0.55f, 0.30f);   // primaryContainer tone 30
            ON_ACCENT_CONT = fromHsl(h, s, 0.90f);        // onPrimaryContainer tone 90

            BG = fromHsl(h, ns, 0.06f);                   // surface 6
            DRAWER = fromHsl(h, ns, 0.10f);               // surfaceContainerLow 10
            CARD = fromHsl(h, ns, 0.12f);                 // surfaceContainer 12
            SHEET = fromHsl(h, ns, 0.17f);                // surfaceContainerHigh 17
            POPUP = fromHsl(h, ns, 0.22f);                // surfaceContainerHighest 22
            TEXT = fromHsl(h, 0.06f, 0.90f);              // onSurface 90
            SUB = fromHsl(h, 0.08f, 0.80f);               // onSurfaceVariant 80
            OUTLINE = fromHsl(h, 0.08f, 0.60f);           // outline 60
        } else {
            ACCENT = fromHsl(h, s, 0.40f);                // primary tone 40
            ON_ACCENT = 0xFFFFFFFF;                       // onPrimary tone 100
            ACCENT_CONT = fromHsl(h, s * 0.80f, 0.90f);   // primaryContainer tone 90
            ON_ACCENT_CONT = fromHsl(h, s, 0.10f);        // onPrimaryContainer tone 10

            BG = fromHsl(h, ns * 0.6f, 0.98f);            // surface 98
            DRAWER = fromHsl(h, ns, 0.96f);               // surfaceContainerLow 96
            CARD = fromHsl(h, ns, 0.94f);                 // surfaceContainer 94
            SHEET = fromHsl(h, ns, 0.92f);                // surfaceContainerHigh 92
            POPUP = fromHsl(h, ns, 0.90f);                // surfaceContainerHighest 90
            TEXT = fromHsl(h, 0.06f, 0.10f);              // onSurface 10
            SUB = fromHsl(h, 0.08f, 0.30f);               // onSurfaceVariant 30
            OUTLINE = fromHsl(h, 0.08f, 0.50f);           // outline 50
        }
    }

    /** Ringkasan seluruh palet. Dipakai untuk tahu apakah layar perlu dibangun ulang. */
    public static int signature() {
        return Arrays.hashCode(new int[]{
                dark ? 1 : 0, dynamicActive ? 1 : 0,
                BG, DRAWER, CARD, SHEET, POPUP, TEXT, SUB, OUTLINE,
                ACCENT, ON_ACCENT, ACCENT_CONT, ON_ACCENT_CONT, ERROR,
                ON_ERROR, ERROR_CONT, ON_ERROR_CONT});
    }

    private static int res(Context c, String name) {
        int id = c.getResources().getIdentifier(name, "color", "android");
        return id == 0 ? 0xFF808080 : c.getResources().getColor(id);
    }

    /** Campur dua warna. t = 0 -> a, t = 1 -> b. */
    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Ganti kanal alpha sebuah warna. a = 0..255. */
    public static int withAlpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    // ------------------------------------------------------------ HSL

    private static float[] toHsl(int c) {
        float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f, h = 0f, s = 0f;
        float d = max - min;
        if (d > 1e-6f) {
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6f : 0f);
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f;
        }
        return new float[]{h, s, l};
    }

    private static int fromHsl(float h, float s, float l) {
        float r, g, b;
        if (s <= 0f) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
            float p = 2f * l - q;
            r = hue(p, q, h + 1f / 3f);
            g = hue(p, q, h);
            b = hue(p, q, h - 1f / 3f);
        }
        return 0xFF000000 | (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }

    private static float hue(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f / 6f) return p + (q - p) * 6f * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
        return p;
    }

    // ------------------------------------------------------------ bar sistem

    /** Warna status bar dan navigation bar mengikuti tema, termasuk ikonnya. */
    public static void styleBars(Window w) {
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        applyBarIcons(w);
    }

    /**
     * Hanya ikon bar (terang/gelap) yang diatur di sini, tanpa mengubah warna bar.
     * Dipakai di layar yang bar-nya transparan dan menggambar sampai ke tepi.
     */
    public static void applyBarIcons(Window w) {
        // getDecorView() dulu: decor dibuat di sini. Tanpa ini getInsetsController() bisa NPE
        // bila dipanggil sebelum setContentView (mis. dari SettingActivity.buildScreen).
        View d = w.getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(dark ? 0 : mask, mask);
            }
            return;
        }
        int flags = d.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= 23) {
            flags = dark ? (flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
                    : (flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            flags = dark ? (flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
                    : (flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        d.setSystemUiVisibility(flags);
    }
}
