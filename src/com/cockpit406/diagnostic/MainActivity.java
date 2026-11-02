package com.cockpit406.diagnostic;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offline replay of genuine recordings. No Bluetooth, USB, vehicle writes or root. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(13, 18, 23), PANEL = Color.rgb(23, 31, 38);
    private static final int WHITE = Color.rgb(243, 246, 247), MUTED = Color.rgb(160, 177, 188);
    private static final int LIME = Color.rgb(214, 241, 57), AMBER = Color.rgb(255, 191, 86);
    private static final String[][] SIGNALS = {
        {"rpm", "Régime moteur", "tr/min", "0"},
        {"coolant_r_c", "Température d’eau · R", "°C", "0"},
        {"battery_v", "Tension calculateur", "V", "1"},
        {"map_mbar", "Pression collecteur", "mbar", "0"},
        {"throttle_deg", "Angle papillon", "°", "1"},
        {"air_r_c", "Température d’air · R", "°C", "0"},
        {"speed_kmh", "Vitesse véhicule", "km/h", "0"},
        {"injection_ms", "Temps d’injection", "ms", "1"},
        {"oxygen_mv", "Sonde à oxygène", "mV", "0"},
        {"throttle_mv", "Signal papillon", "mV", "0"},
        {"coolant_s_c", "Température d’eau · S", "°C", "0"},
        {"air_s_c", "Température d’air · S", "°C", "0"},
        {"idle_duty_pct", "Commande de ralenti", "%", "0"},
        {"canister_duty_pct", "Commande canister", "%", "0"},
        {"load_ms", "Charge moteur (libellé relevé)", "ms", "1"}
    };
    private final Handler handler = new Handler();
    private final List<Track> tracks = new ArrayList<>();
    private final Map<String, TextView> valueViews = new LinkedHashMap<>();
    private final List<Button> navButtons = new ArrayList<>();
    private final List<Button> sourceButtons = new ArrayList<>();
    private LinearLayout page;
    private TextView pageTitle, frameLabel, sourceLabel, groupLabel;
    private Button playButton;
    private SeekBar seekBar;
    private RpmGraph graph;
    private int trackIndex = 0, frameIndex = 0, tab = 0;
    private boolean playing = false;

    private static final class Sample {
        final String hex, clock;
        final Map<String, Double> values;
        Sample(JSONObject j) throws Exception {
            hex = j.getString("raw"); clock = j.getString("clock");
            values = Mp70Decoder.decode(hex);
        }
    }
    private static final class Track {
        final String name, detail;
        final List<Sample> samples = new ArrayList<>();
        Track(JSONObject j) throws Exception {
            name = j.getString("name"); detail = j.getString("detail");
            JSONArray array = j.getJSONArray("samples");
            for (int i = 0; i < array.length(); i++) samples.add(new Sample(array.getJSONObject(i)));
            if (samples.isEmpty()) throw new IllegalArgumentException("Empty recording");
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);TripBackups.start(this);
        if (!BuildInfo.DEVELOPMENT_REPLAYS || !getIntent().getBooleanExtra("replay", false)) {
            startActivity(new Intent(this, StartupActivity.class).setData(getIntent().getData()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
            return;
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = getAssets().open("replays.json")) {
                byte[] block = new byte[8192]; int n;
                while ((n = stream.read(block)) != -1) bytes.write(block, 0, n);
            }
            JSONArray array = new JSONObject(bytes.toString("UTF-8")).getJSONArray("tracks");
            for (int i = 0; i < array.length(); i++) tracks.add(new Track(array.getJSONObject(i)));
            trackIndex = Math.max(0, Math.min(tracks.size() - 1, getPreferences(0).getInt("track", 0)));
            frameIndex = Math.max(0, Math.min(track().samples.size() - 1, getPreferences(0).getInt("frame", 0)));
            tab = Math.max(0, Math.min(3, getPreferences(0).getInt("tab", 0)));
            build();
        } catch (Exception e) {
            TextView error = text("Impossible de charger les relevés.\nRevenir au cockpit puis réessayer.", 24, WHITE);
            error.setBackgroundColor(BG); error.setPadding(dp(40), dp(40), dp(40), dp(40));
            setContentView(error);
            android.util.Log.e("PP406", "Recording load failed", e);
        }
    }

    private Track track() { return tracks.get(trackIndex); }
    private int dp(float n) { return Math.round(getResources().getDisplayMetrics().density * n); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView text(String content, int size, int color) {
        TextView t = new TextView(this); t.setText(content); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("tnum"); t.setIncludeFontPadding(false); return t;
    }
    private TextView bold(String content, int size, int color) {
        TextView t = text(content, size, color); t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return t;
    }
    private GradientDrawable background(int color, int stroke) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(14));
        if (stroke != 0) g.setStroke(dp(1), stroke); return g;
    }
    private Button button(String title, boolean selected, View.OnClickListener listener) {
        Button b = new Button(this); b.setText(title); b.setTextSize(15); b.setAllCaps(false);
        b.setTextColor(selected ? BG : WHITE); b.setBackground(background(selected ? LIME : PANEL, selected ? 0 : Color.rgb(51, 66, 77)));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setOnClickListener(listener); return b;
    }
    private void space(LinearLayout parent, int h) { View v = new View(this); parent.addView(v, new LinearLayout.LayoutParams(1, dp(h))); }

    private void build() {
        LinearLayout root = row(); root.setGravity(Gravity.TOP); root.setBackgroundColor(BG);
        LinearLayout side = column(); side.setPadding(dp(22), dp(28), dp(18), dp(24));
        side.setBackgroundColor(Color.rgb(17, 23, 29));
        root.addView(side, new LinearLayout.LayoutParams(dp(192), -1));
        side.addView(bold("PP406", 40, WHITE));
        space(side, 2); side.addView(bold("diagnostique", 13, LIME));
        space(side, 35);
        String[] tabs = {"Vue d’ensemble", "Tous les capteurs", "Défauts relevés", "Véhicule"};
        for (int i = 0; i < tabs.length; i++) {
            final int choice = i;
            Button b = button(tabs[i], tab == i, v -> { tab = choice; showPage(); });
            side.addView(b, new LinearLayout.LayoutParams(-1, dp(56))); navButtons.add(b); space(side, 12);
        }
        side.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        side.addView(text("V6 24 · MP7.0", 13, MUTED)); space(side, 6);
        side.addView(text("Version " + BuildInfo.VERSION, 12, MUTED)); space(side, 20);
        side.addView(button("‹  Cockpit", false, v -> {
            stop();
            Intent cockpit = getPackageManager().getLaunchIntentForPackage("com.novalysapps.coupe406launcher");
            if (cockpit != null) startActivity(cockpit);
            else startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
            finish();
        }), new LinearLayout.LayoutParams(-1, dp(50)));

        LinearLayout main = column(); main.setPadding(dp(28), dp(25), dp(28), dp(20));
        root.addView(main, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout heading = row();
        pageTitle = bold("", 30, WHITE); heading.addView(pageTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = bold("  RELECTURE  ", 13, AMBER); badge.setPadding(dp(12), dp(10), dp(12), dp(10));
        badge.setBackground(background(Color.rgb(54, 40, 24), Color.rgb(100, 72, 34))); heading.addView(badge);
        main.addView(heading); space(main, 12);
        TextView notice = text("Relevés du 20 octobre 2026 · Aucune connexion à la voiture", 15, AMBER);
        notice.setPadding(dp(16), dp(12), dp(16), dp(12)); notice.setBackground(background(Color.rgb(40, 33, 24), 0));
        main.addView(notice, new LinearLayout.LayoutParams(-1, -2)); space(main, 18);
        page = column(); main.addView(page, new LinearLayout.LayoutParams(-1, 0, 1)); space(main, 12);
        main.addView(buildReplayControls(), new LinearLayout.LayoutParams(-1, -2));
        setContentView(root); showPage();
    }

    private LinearLayout buildReplayControls() {
        LinearLayout box = column(); box.setPadding(dp(16), dp(12), dp(16), dp(10)); box.setBackground(background(PANEL, 0));
        LinearLayout first = row();
        sourceLabel = bold("", 15, WHITE); first.addView(sourceLabel, new LinearLayout.LayoutParams(0, -2, 1));
        for (int i = 0; i < tracks.size(); i++) {
            final int choice = i;
            Button b = button(i == 0 ? "Ralenti" : "Trajet", i == trackIndex, v -> selectTrack(choice));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(104), dp(42)); p.leftMargin = dp(8);
            first.addView(b, p); sourceButtons.add(b);
        }
        box.addView(first); space(box, 6);
        LinearLayout controls = row();
        playButton = button("▶  Lire", true, v -> {
            if (playing) stop(); else {
                if (frameIndex == track().samples.size() - 1) frameIndex = 0;
                playing = true; playButton.setText("Ⅱ  Pause"); updateValues(); handler.postDelayed(tick, 350);
            }
        }); controls.addView(playButton, new LinearLayout.LayoutParams(dp(126), dp(46)));
        seekBar = new SeekBar(this); seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(LIME));
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(LIME));
        controls.addView(seekBar, new LinearLayout.LayoutParams(0, dp(48), 1));
        frameLabel = text("", 13, MUTED); frameLabel.setGravity(Gravity.RIGHT);
        controls.addView(frameLabel, new LinearLayout.LayoutParams(dp(195), -2));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar bar) { stop(); }
            public void onStopTrackingTouch(SeekBar bar) { }
            public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                if (user) { frameIndex = progress; updateValues(); }
            }
        }); box.addView(controls); return box;
    }

    private void selectTrack(int choice) {
        stop(); trackIndex = choice; frameIndex = 0;
        updateValues();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!playing) return;
            if (frameIndex < track().samples.size() - 1) {
                frameIndex++; updateValues(); handler.postDelayed(this, 350);
            } else { stop(); playButton.setText("↻  Revoir"); }
        }
    };
    private void stop() {
        playing = false; handler.removeCallbacks(tick); if (playButton != null) playButton.setText("▶  Lire");
    }

    private void showPage() {
        valueViews.clear(); graph = null; groupLabel = null; page.removeAllViews();
        String[] titles = {"Le moteur, en un regard", "Toutes les valeurs décodées", "Défauts lors du relevé", "Ta 406 Coupé"};
        pageTitle.setText(titles[tab]);
        for (int i = 0; i < navButtons.size(); i++) {
            navButtons.get(i).setBackground(background(i == tab ? LIME : PANEL, 0));
            navButtons.get(i).setTextColor(i == tab ? BG : WHITE);
        }
        if (tab == 0) {
            for (int r = 0; r < 2; r++) {
                LinearLayout line = row(); line.setGravity(Gravity.TOP);
                for (int c = 0; c < 3; c++) {
                    String[] signal = SIGNALS[r * 3 + c];
                    LinearLayout card = column(); card.setPadding(dp(18), dp(15), dp(18), dp(14)); card.setBackground(background(PANEL, 0));
                    card.addView(text(signal[1], 14, MUTED)); space(card, 10);
                    TextView value = bold("—", 38, r == 0 && c == 0 ? LIME : WHITE); card.addView(value); valueViews.put(signal[0], value);
                    space(card, 4); card.addView(text(signal[2], 13, MUTED));
                    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1); if (c != 0) p.leftMargin = dp(12);
                    line.addView(card, p);
                }
                page.addView(line, new LinearLayout.LayoutParams(-1, dp(137))); space(page, 12);
            }
            graph = new RpmGraph(); page.addView(graph, new LinearLayout.LayoutParams(-1, 0, 1));
        } else if (tab == 1) {
            groupLabel = text("", 14, MUTED); page.addView(groupLabel); space(page, 10);
            ScrollView scroll = new ScrollView(this); LinearLayout list = column();
            for (String[] signal : SIGNALS) {
                LinearLayout line = row(); line.setPadding(dp(16), dp(12), dp(16), dp(12)); line.setBackground(background(PANEL, 0));
                line.addView(text(signal[1], 17, WHITE), new LinearLayout.LayoutParams(0, -2, 1));
                TextView value = bold("—", 21, LIME); valueViews.put(signal[0], value);
                line.addView(value); TextView unit = text("  " + signal[2], 14, MUTED); unit.setMinWidth(dp(80)); line.addView(unit);
                list.addView(line, new LinearLayout.LayoutParams(-1, dp(56))); space(list, 7);
            }
            scroll.addView(list); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        } else if (tab == 2) {
            TextView info = text("3 défauts fugitifs relevés le 20 octobre. Leur présence actuelle n’est pas vérifiée.", 16, MUTED);
            page.addView(info); space(page, 18);
            fault("Thermistance d’air d’admission", "Court-circuit au plus ou circuit ouvert");
            fault("Potentiomètre papillon", "Défaut fugitif");
            fault("Sonde à oxygène", "Circuit ouvert");
            space(page, 6); page.addView(text("Aucun défaut effacé. Aucun code P normalisé n’a été fourni lors du relevé.", 14, MUTED));
        } else {
            ScrollView scroll = new ScrollView(this); LinearLayout list = column();
            identity(list, "VOITURE", "Peugeot 406 Coupé · V6 24 · Boîte manuelle");
            identity(list, "IDENTITÉ", "Véhicule de développement · identité non diffusée");
            identity(list, "CALCULATEUR MOTEUR", "Bosch MP7.0 XFZ   ·   0261204407   ·   Indice 0004");
            identity(list, "PROTOCOLE MOTEUR IDENTIFIÉ", "PSA2");
            identity(list, "CONTENU DISPONIBLE", "757 réponses enregistrées · Capteurs et injection\nDécodage indépendant · 38 valeurs comparées aux écrans de référence");
            identity(list, "À VENIR", "Connexion en direct · Tests d’actionneurs · Adaptations · Apprentissage des clés\nCes fonctions ne sont pas disponibles dans cette version.");
            scroll.addView(list); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        updateValues();
    }

    private void fault(String title, String description) {
        LinearLayout card = column(); card.setPadding(dp(20), dp(16), dp(20), dp(16)); card.setBackground(background(PANEL, 0));
        LinearLayout line = row(); line.addView(bold(title, 20, WHITE), new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(bold("FUGITIF", 12, AMBER)); card.addView(line); space(card, 8); card.addView(text(description, 16, MUTED));
        page.addView(card, new LinearLayout.LayoutParams(-1, -2)); space(page, 12);
    }
    private void identity(LinearLayout parent, String title, String content) {
        LinearLayout card = column(); card.setPadding(dp(16), dp(13), dp(16), dp(13)); card.setBackground(background(PANEL, 0));
        card.addView(bold(title, 11, LIME)); space(card, 7); card.addView(text(content, 17, WHITE));
        parent.addView(card, new LinearLayout.LayoutParams(-1, -2)); space(parent, 9);
    }
    private void updateValues() {
        if (tracks.isEmpty() || seekBar == null) return;
        Sample sample = track().samples.get(frameIndex);
        for (String[] signal : SIGNALS) {
            TextView view = valueViews.get(signal[0]); if (view == null) continue;
            Double value = sample.values.get(signal[0]);
            String display = value == null ? "—" : String.format(Locale.FRANCE, "% ." + signal[3] + "f", value).trim();
            view.setText(display);
            view.setContentDescription(signal[1] + " : " + (value == null ? "non disponible dans ce groupe" : display + " " + signal[2]));
        }
        sourceLabel.setText(track().name + " · " + track().samples.size() + " relevés");
        seekBar.setMax(track().samples.size() - 1); seekBar.setProgress(frameIndex);
        frameLabel.setText(sample.clock + " · 20/10/2026\n" + (frameIndex + 1) + " / " + track().samples.size());
        if (groupLabel != null) groupLabel.setText("Groupe " + (sample.values.get("group") == 2 ? "Capteurs" : "Injection") + " · — : valeur absente de ce groupe");
        for (int i = 0; i < sourceButtons.size(); i++) {
            sourceButtons.get(i).setBackground(background(i == trackIndex ? LIME : BG, 0));
            sourceButtons.get(i).setTextColor(i == trackIndex ? BG : WHITE);
        }
        if (graph != null) graph.invalidate();
    }

    @Override protected void onPause() {
        super.onPause(); stop();
        getPreferences(0).edit().putInt("track", trackIndex).putInt("frame", frameIndex).putInt("tab", tab).apply();
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }

    private final class RpmGraph extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RpmGraph() { super(MainActivity.this); setBackground(background(PANEL, 0)); setContentDescription("Régime moteur du relevé enregistré"); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); int w = getWidth(), h = getHeight(); if (h < dp(35)) return;
            paint.setStyle(Paint.Style.FILL); paint.setColor(MUTED); paint.setTextSize(dp(12));
            canvas.drawText("RÉGIME DANS LE RELEVÉ", dp(16), dp(22), paint);
            if (h < dp(62)) return;
            float left = dp(16), right = w - dp(16), top = dp(38), bottom = h - dp(12);
            List<Sample> samples = track().samples; double max = 1000;
            for (Sample s : samples) max = Math.max(max, s.values.get("rpm")); max = Math.ceil(max / 1000) * 1000;
            Path path = new Path();
            for (int i = 0; i < samples.size(); i++) {
                float x = left + (right - left) * i / Math.max(1, samples.size() - 1);
                float y = bottom - (float)(samples.get(i).values.get("rpm") / max) * (bottom - top);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(Color.rgb(105, 128, 139)); canvas.drawPath(path, paint);
            float x = left + (right - left) * frameIndex / Math.max(1, samples.size() - 1);
            float y = bottom - (float)(samples.get(frameIndex).values.get("rpm") / max) * (bottom - top);
            paint.setStrokeWidth(dp(1)); paint.setColor(Color.rgb(87, 102, 44)); canvas.drawLine(x, top, x, bottom, paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(LIME); canvas.drawCircle(x, y, dp(4), paint);
        }
    }
}
