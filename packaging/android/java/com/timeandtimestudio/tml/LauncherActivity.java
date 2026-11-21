package com.timeandtimestudio.tml;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * What runs before the game (StarlingActivity) when it is handed something, in a process and a
 * task of its own: a file opened with Starling or shared to it (a resource pack, a world, a mod,
 * the prepared resources) is copied into the game's import inbox, which the game installs from
 * (crates/horizon-client/src/import.rs); and a restart the game asked for starts it again once
 * its old process has ended.
 */
public final class LauncherActivity extends Activity {
    /** The game's process id: start the game again once it has ended. */
    static final String AFTER = "com.timeandtimestudio.tml.AFTER";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        final int after = intent == null ? 0 : intent.getIntExtra(AFTER, 0);
        Uri file = handedFile(intent);
        if (after != 0) {
            showRestarting("Starting TML again…");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    // A few seconds at most: the game saves and ends as its activity finishes.
                    for (int i = 0; i < 100 && new File("/proc/" + after).exists(); i++) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            play();
                        }
                    });
                }
            }, "starling-restart").start();
        } else if (file != null) {
            final AlertDialog copying =
                    new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setMessage("Handing the file to TML...")
                            .setCancelable(false)
                            .show();
            receive(this, file, new Runnable() {
                @Override
                public void run() {
                    copying.dismiss();
                    play();
                }
            });
        } else if (StartupRecovery.interrupted(this)) {
            startActivity(new Intent(this, DiagnosticsActivity.class)
                    .putExtra(StartupRecovery.RECOVERY, true));
            finish();
        } else {
            play();
        }
    }

    private void play() {
        // The game's own task (singleTask): it stays in the recents, this one does not.
        Intent game = new Intent(this, GameActivity.class);
        game.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(game);
        finish();
        // Straight into the game's black first frame, no window animation between.
        overridePendingTransition(0, 0);
    }

    @SuppressWarnings("deprecation")
    private void applyWindowColors() {
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF121214));
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            getWindow().setStatusBarColor(0xFF121214);
            getWindow().setNavigationBarColor(0xFF121214);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
    }

    /**
     * While the game starts again (a world whose mods are not the running ones): a screen of its
     * own, dark, landscape and without bars as the game is, so the phone does not show what is
     * under this see-through activity between the two processes, as if the game had closed.
     */
    @SuppressWarnings("deprecation")
    private void showRestarting(String message) {
        // Android 8.0 refuses an orientation to a see-through activity (it throws).
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O) {
            try {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } catch (IllegalStateException e) {
                // As it is, then.
            }
        }
        applyWindowColors();
        setContentView(new RestartView(this, message));
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController bars = decor.getWindowInsetsController();
            if (bars != null) {
                bars.hide(WindowInsets.Type.systemBars());
                bars.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private static final class RestartView extends View {
        private final String message;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final long start = SystemClock.uptimeMillis();
        private Canvas canvas;

        RestartView(Context context, String message) {
            super(context);
            this.message = message;
        }

        private void rounded(float x, float y, float w, float h, float r, int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            rect.set(x, y, x + w, y + h);
            canvas.drawRoundRect(rect, r, r, paint);
        }

        private void fill(float x, float y, float w, float h, int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawRect(x, y, x + w, y + h, paint);
        }

        private void text(String s, float cx, float top, float size, Typeface face, int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(face);
            paint.setTextSize(size);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(color);
            canvas.drawText(s, cx, top - paint.ascent(), paint);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            this.canvas = canvas;
            int bg = 0xFF121214;
            int orange = 0xFFFF751F;
            int textColor = 0xFFFFFFFF;
            int muted = 0xFF9BA1A6;
            int track = 0x479BA1A6;
            float width = getWidth(), height = getHeight();
            float u = Math.max(Math.min(width / 1040f, height / 700f), 1f);
            float w = width / u, h = height / u;
            canvas.drawColor(bg);

            float size = Math.max(40f, Math.min(w / 16f, 96f));
            float small = Math.max(11f, Math.min(size * 0.25f, 18f));
            float block = size * 1.45f + small * 0.6f + small * 1.5f;
            float top = (h - block) / 2f;
            float cx = w / 2f;

            // TML dual-T vector icon centered above the wordmark
            float markS = Math.max(36f, Math.min(size * 0.70f, 64f));
            float markCy = Math.max(top - markS * 0.75f, markS * 0.6f);
            float mx = cx - markS * 0.5f;
            float my = markCy - markS * 0.5f;
            float barH = Math.max(markS * (60f / 388f), 2f);
            float stemW = Math.max(markS * (56f / 388f), 2f);

            fill(mx * u, my * u, markS * u, barH * u, orange);
            fill((mx + (markS - stemW) * 0.5f) * u, my * u, stemW * u, markS * u, orange);
            float midBarY = my + markS * (118f / 388f);
            fill(mx * u, midBarY * u, markS * u, barH * u, orange);
            fill(mx * u, midBarY * u, stemW * u, (markS - (midBarY - my)) * u, orange);

            text("TML", cx * u, top * u, size * u,
                    Typeface.create("sans-serif-medium", Typeface.BOLD), textColor);
            float subY = top + size * 1.45f + small * 0.6f;
            text("Rust based minecraft client", cx * u, subY * u, small * u,
                    Typeface.MONOSPACE, muted);

            float t = (SystemClock.uptimeMillis() - start) / 1000f;
            float bw = Math.max(80f, Math.min(size * 1.8f, 140f)), bh = 4f;
            float bx = cx - bw / 2f, by = subY + small * 1.7f;
            rounded(bx * u, by * u, bw * u, bh * u, 2f * u, track);
            float segment = bw * 0.56f;
            float phase = 0.5f - 0.5f * (float) Math.cos(t * Math.PI);
            rounded((bx + (bw - segment) * phase) * u, by * u, segment * u, bh * u,
                    2f * u, orange);
            text(message, cx * u, (by + 12f) * u, 11f * u, Typeface.DEFAULT, muted);
            postInvalidateOnAnimation();
        }
    }

    /** The file an "Open with" or a share hands to Horizon, if any. */
    @SuppressWarnings("deprecation")
    private static Uri handedFile(Intent intent) {
        if (intent == null) {
            return null;
        }
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            return intent.getData();
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        return null;
    }

    /**
     * Copy a file into the game's import inbox as `name.part`, renamed to `name` once complete,
     * on a thread of its own; then `done` on the UI thread (if any).
     */
    static void receive(final Activity activity, final Uri file, final Runnable done) {
        final File inbox = new File(activity.getFilesDir(), "import-inbox");
        final String name = displayName(activity, file);
        new Thread(new Runnable() {
            @Override
            public void run() {
                inbox.mkdirs();
                File part = new File(inbox, name + ".part");
                try (InputStream in = activity.getContentResolver().openInputStream(file);
                        OutputStream out = new FileOutputStream(part)) {
                    if (in == null) {
                        throw new java.io.IOException("nothing to read");
                    }
                    byte[] buffer = new byte[1 << 16];
                    for (int n; (n = in.read(buffer)) > 0; ) {
                        out.write(buffer, 0, n);
                    }
                } catch (Exception e) {
                    part.delete();
                    android.util.Log.w("starling", "could not receive " + name, e);
                }
                if (part.exists() && !part.renameTo(new File(inbox, name))) {
                    part.delete();
                }
                if (done != null) {
                    activity.runOnUiThread(done);
                }
            }
        }, "starling-receive").start();
    }

    /** The file's name as the app it came from shows it, without folders. */
    private static String displayName(Context context, Uri file) {
        String name = null;
        try (Cursor cursor = context.getContentResolver().query(
                file, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (Exception e) {
            // A file:// or an odd provider: the path below.
        }
        if (name == null) {
            name = file.getLastPathSegment();
        }
        if (name == null || name.isEmpty()) {
            name = "imported";
        }
        name = name.replaceAll("[/\\\\:*?\"<>|]", "_");
        // ".part" is the inbox's mark for a file still arriving.
        return name.endsWith(".part") ? name + "_" : name;
    }
}
