package com.limelight.diagnostics;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.preference.Preference;
import com.limelight.R;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CapabilityReportExport {
    private static final AtomicBoolean running = new AtomicBoolean();
    private CapabilityReportExport() {}

    public static void share(Activity activity, Preference preference) {
        if (!running.compareAndSet(false, true)) return;
        preference.setEnabled(false);
        Toast.makeText(activity, R.string.capability_report_collecting, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File output = null;
            try {
                File folder = new File(activity.getCacheDir(), "capability-reports");
                if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("Cache unavailable");
                // Fixed path bounds retained reports; only one export may run at a time.
                output = new File(folder, "reflect-capabilities.json");
                String json = CapabilityReport.collect(activity.getApplicationContext()).toString(2);
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    stream.write(json.getBytes(StandardCharsets.UTF_8));
                }
                final File report = output;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    try {
                        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", report);
                        Intent share = new Intent(Intent.ACTION_SEND).setType("application/json")
                                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        share.setClipData(ClipData.newRawUri("Reflect capabilities", uri));
                        activity.startActivity(Intent.createChooser(share, activity.getString(R.string.capability_report_title)));
                    } catch (RuntimeException e) {
                        Toast.makeText(activity, R.string.capability_report_failed, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                if (output != null) output.delete();
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed())
                        Toast.makeText(activity, R.string.capability_report_failed, Toast.LENGTH_LONG).show();
                });
            } finally {
                running.set(false);
                activity.runOnUiThread(() -> preference.setEnabled(true));
            }
        }, "Reflect-capability-report").start();
    }
}
