package com.cockpit406.diagnostic;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

/** The tester's SM-T580 must grant root before the diagnostic can be opened. */
public final class RootGateActivity extends Activity {
    private final Handler handler = new Handler();
    private volatile Process process;
    private final AtomicBoolean complete = new AtomicBoolean();
    private TextView status;
    private Button retry;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!"SM-T580".equalsIgnoreCase(Build.MODEL)) { openDiagnostic(); return; }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(36, 24, 36, 24);
        layout.setBackgroundColor(Color.rgb(8, 18, 23));
        TextView title = new TextView(this);
        title.setText("PP406 · accès root requis");
        title.setTextColor(Color.WHITE); title.setTextSize(26);
        layout.addView(title);
        status = new TextView(this);
        status.setTextColor(Color.WHITE); status.setTextSize(17);
        status.setPadding(0, 20, 0, 20);
        layout.addView(status);
        retry = new Button(this);
        retry.setText("Vérifier à nouveau");
        retry.setOnClickListener(v -> checkRoot());
        layout.addView(retry);
        setContentView(layout);
        checkRoot();
    }

    private void checkRoot() {
        if (process != null) return;
        complete.set(false); retry.setEnabled(false);
        status.setText("Vérification du root… Autorisez PP406 si Magisk ou SuperSU le demande.");
        final AtomicBoolean finished = new AtomicBoolean();
        final Runnable timeout = () -> {
            if (!finished.compareAndSet(false, true)) return;
            Process running = process; process = null;
            if (running != null) running.destroy();
            fail("La vérification a expiré. Vérifiez la demande Magisk/SuperSU, puis réessayez.");
        };
        handler.postDelayed(timeout, 45000);
        new Thread(() -> {
            boolean allowed = false;
            try {
                Process p = new ProcessBuilder("su", "-c", "id -u").redirectErrorStream(true).start();
                process = p;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line = reader.readLine();
                    allowed = p.waitFor() == 0 && "0".equals(line == null ? "" : line.trim());
                }
            } catch (Exception ignored) { }
            final boolean result = allowed;
            runOnUiThread(() -> {
                if (!finished.compareAndSet(false, true)) return;
                handler.removeCallbacks(timeout); process = null;
                if (isFinishing()) return;
                if (result) openDiagnostic();
                else fail("Accès root refusé ou indisponible. PP406 nécessite le root sur cette tablette. Autorisez PP406 dans Magisk/SuperSU, puis réessayez.");
            });
        }, "pp406-root-check").start();
    }

    private void fail(String message) { status.setText(message); retry.setEnabled(true); }
    private void openDiagnostic() {
        if (!complete.compareAndSet(false, true)) return;
        Intent original = getIntent();
        Intent next = new Intent(this, MainActivity.class);
        if (original != null) {
            next.setAction(original.getAction());
            next.setData(original.getData());
            if (original.getExtras() != null) next.putExtras(original.getExtras());
        }
        startActivity(next); finish();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        Process running = process; process = null;
        if (running != null) running.destroy();
        super.onDestroy();
    }
}
