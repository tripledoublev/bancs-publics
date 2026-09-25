package map.bench.bancs_publics;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Headless Developer BroadcastReceiver for ADB data backup, export, and restore.
 *
 * Commands:
 *   adb shell am broadcast -a map.bench.bancs_publics.ACTION_EXPORT
 *   adb shell am broadcast -a map.bench.bancs_publics.ACTION_IMPORT [--es file /path/to/backup.zip]
 *   adb shell am broadcast -a map.bench.bancs_publics.ACTION_STATUS
 */
public class DevExportReceiver extends BroadcastReceiver {
    private static final String TAG = "DevExport";

    public static final String ACTION_EXPORT = "map.bench.bancs_publics.ACTION_EXPORT";
    public static final String ACTION_IMPORT = "map.bench.bancs_publics.ACTION_IMPORT";
    public static final String ACTION_STATUS = "map.bench.bancs_publics.ACTION_STATUS";
    public static final String ACTION_DATA_RELOADED = "map.bench.bancs_publics.DATA_RELOADED";

    // Legacy actions for backward compatibility
    public static final String LEGACY_ACTION_EXPORT = "com.machine.benchmap.ACTION_EXPORT";
    public static final String LEGACY_ACTION_IMPORT = "com.machine.benchmap.ACTION_IMPORT";
    public static final String LEGACY_ACTION_STATUS = "com.machine.benchmap.ACTION_STATUS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();

        if (LEGACY_ACTION_EXPORT.equals(action)) action = ACTION_EXPORT;
        if (LEGACY_ACTION_IMPORT.equals(action)) action = ACTION_IMPORT;
        if (LEGACY_ACTION_STATUS.equals(action)) action = ACTION_STATUS;

        switch (action) {
            case ACTION_EXPORT:
                handleExport(context, intent);
                break;
            case ACTION_IMPORT:
                handleImport(context, intent);
                break;
            case ACTION_STATUS:
                handleStatus(context);
                break;
            default:
                Log.w(TAG, "Unknown action: " + action);
                break;
        }
    }

    private void handleStatus(Context context) {
        try {
            BenchCollectionManager mgr = BenchCollectionManager.getInstance(context);
            List<BenchCollection> cols = mgr.getCollections();
            List<SavedBench> saved = mgr.getAllSavedBenches();
            List<Bench> custom = mgr.getAllCustomBenches();
            File photosDir = mgr.getPhotosDirectory();

            int photoCount = 0;
            long totalBytes = 0;
            if (photosDir != null && photosDir.exists()) {
                File[] files = photosDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && f.getName().endsWith(".jpg")) {
                            photoCount++;
                            totalBytes += f.length();
                        }
                    }
                }
            }

            String msg = String.format(Locale.US,
                    "STATUS: %d collections, %d saved benches (%d custom), %d photos (%.2f MB on disk)",
                    cols.size(), saved.size(), custom.size(), photoCount, totalBytes / (1024.0 * 1024.0));

            Log.i(TAG, msg);
            showToast(context, msg);
            setResultCode(Activity.RESULT_OK);
            setResultData(msg);
        } catch (Exception e) {
            String err = "ERROR: " + e.getMessage();
            Log.e(TAG, err, e);
            setResultCode(Activity.RESULT_CANCELED);
            setResultData(err);
        }
    }

    private void handleExport(Context context, Intent intent) {
        try {
            BenchCollectionManager mgr = BenchCollectionManager.getInstance(context);
            List<SavedBench> allSaved = mgr.getAllSavedBenches();
            List<Bench> allCustom = mgr.getAllCustomBenches();
            File internalFilesDir = context.getFilesDir();
            File collectionsFile = new File(internalFilesDir, "collections.json");
            File photosDir = mgr.getPhotosDirectory();

            String customPath = intent.getStringExtra("path");
            if (customPath == null) customPath = intent.getStringExtra("dest");
            if (customPath == null) customPath = intent.getStringExtra("file");

            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File targetZip;

            if (customPath != null && !customPath.trim().isEmpty()) {
                targetZip = new File(customPath.trim());
            } else {
                targetZip = new File(downloadDir, "BancsPublics-Backup-latest.zip");
            }

            // Ensure parent directory exists
            File parent = targetZip.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            int photoCount = 0;

            // Attempt to write to targetZip. If access denied, fall back to app's external files dir
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(targetZip);
            } catch (Exception e) {
                Log.w(TAG, "Cannot write to " + targetZip.getAbsolutePath() + ", falling back to external files dir: " + e.getMessage());
                File extDir = context.getExternalFilesDir(null);
                if (extDir != null) {
                    targetZip = new File(extDir, "BancsPublics-Backup-latest.zip");
                    fos = new FileOutputStream(targetZip);
                } else {
                    throw e;
                }
            }

            try (BufferedOutputStream bos = new BufferedOutputStream(fos);
                 ZipOutputStream zos = new ZipOutputStream(bos)) {

                // 1. Write collections.json
                if (collectionsFile.exists()) {
                    zos.putNextEntry(new ZipEntry("collections.json"));
                    try (FileInputStream fis = new FileInputStream(collectionsFile)) {
                        copyStream(fis, zos);
                    }
                    zos.closeEntry();
                }

                // 2. Generate and write benches.geojson (RFC 7946 GeoJSON FeatureCollection)
                String geoJsonStr = buildGeoJson(allSaved);
                zos.putNextEntry(new ZipEntry("benches.geojson"));
                zos.write(geoJsonStr.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // 3. Write shared_prefs (if exists)
                File prefsFile = new File(context.getApplicationInfo().dataDir, "shared_prefs/benchmap_prefs.xml");
                if (prefsFile.exists()) {
                    zos.putNextEntry(new ZipEntry("benchmap_prefs.xml"));
                    try (FileInputStream fis = new FileInputStream(prefsFile)) {
                        copyStream(fis, zos);
                    }
                    zos.closeEntry();
                }

                // 4. Write all photos under bench_photos/
                if (photosDir != null && photosDir.exists()) {
                    File[] files = photosDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.isFile() && f.getName().endsWith(".jpg")) {
                                zos.putNextEntry(new ZipEntry("bench_photos/" + f.getName()));
                                try (FileInputStream fis = new FileInputStream(f)) {
                                    copyStream(fis, zos);
                                }
                                zos.closeEntry();
                                photoCount++;
                            }
                        }
                    }
                }
            }

            // Also keep timestamped copy if placed in downloadDir
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File timestampedZip = new File(targetZip.getParentFile(), "BancsPublics-Backup-" + timestamp + ".zip");
            try (FileInputStream fis = new FileInputStream(targetZip);
                 FileOutputStream tsFos = new FileOutputStream(timestampedZip)) {
                copyStream(fis, tsFos);
            } catch (Exception ignored) {}

            // Also copy to getExternalFilesDir as guaranteed fallback if not already there
            try {
                File extDir = context.getExternalFilesDir(null);
                if (extDir != null) {
                    File extZip = new File(extDir, "BancsPublics-Backup-latest.zip");
                    if (!extZip.getAbsolutePath().equals(targetZip.getAbsolutePath())) {
                        try (FileInputStream fis = new FileInputStream(targetZip);
                             FileOutputStream extFos = new FileOutputStream(extZip)) {
                            copyStream(fis, extFos);
                        }
                    }
                }
            } catch (Exception ignored) {}

            long zipSizeKb = targetZip.length() / 1024;
            String msg = String.format(Locale.US,
                    "SUCCESS: Exported %d benches (%d custom), %d photos to %s (%d KB)",
                    allSaved.size(), allCustom.size(), photoCount, targetZip.getAbsolutePath(), zipSizeKb);

            Log.i(TAG, msg);
            showToast(context, "Export terminé: " + photoCount + " photos (" + zipSizeKb + " KB)");
            setResultCode(Activity.RESULT_OK);
            setResultData(msg);

        } catch (Exception e) {
            String err = "ERROR exporting: " + e.getMessage();
            Log.e(TAG, err, e);
            showToast(context, err);
            setResultCode(Activity.RESULT_CANCELED);
            setResultData(err);
        }
    }

    private void handleImport(Context context, Intent intent) {
        try {
            File zipToImport = null;
            String customFile = intent.getStringExtra("file");
            if (customFile == null) customFile = intent.getStringExtra("path");
            if (customFile != null && !customFile.trim().isEmpty()) {
                zipToImport = new File(customFile.trim());
            }

            if (zipToImport == null || !zipToImport.exists()) {
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File candidate1 = new File(downloadDir, "bancs_publics_restore.zip");
                File candidate2 = new File(downloadDir, "BancsPublics-Backup-latest.zip");
                File candidate3 = new File(context.getExternalFilesDir(null), "BancsPublics-Backup-latest.zip");
                File candidate4 = new File(downloadDir, "BancsPublics-Backup.zip");

                if (candidate1.exists()) {
                    zipToImport = candidate1;
                } else if (candidate2.exists()) {
                    zipToImport = candidate2;
                } else if (candidate3.exists()) {
                    zipToImport = candidate3;
                } else if (candidate4.exists()) {
                    zipToImport = candidate4;
                }
            }

            if (zipToImport == null || !zipToImport.exists()) {
                String err = "ERROR: No backup zip found. Place zip at /sdcard/Download/bancs_publics_restore.zip or BancsPublics-Backup-latest.zip";
                Log.e(TAG, err);
                setResultCode(Activity.RESULT_CANCELED);
                setResultData(err);
                return;
            }

            BenchCollectionManager mgr = BenchCollectionManager.getInstance(context);
            File internalFilesDir = context.getFilesDir();
            File photosDir = mgr.getPhotosDirectory();
            if (!photosDir.exists()) {
                photosDir.mkdirs();
            }

            int extractedFiles = 0;
            int photoCount = 0;

            try (FileInputStream fis = new FileInputStream(zipToImport);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 ZipInputStream zis = new ZipInputStream(bis)) {

                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory()) {
                        continue;
                    }

                    if ("collections.json".equals(name)) {
                        File dest = new File(internalFilesDir, "collections.json");
                        try (FileOutputStream destFos = new FileOutputStream(dest)) {
                            copyStream(zis, destFos);
                        }
                        extractedFiles++;
                    } else if ("benchmap_prefs.xml".equals(name)) {
                        File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
                        if (!prefsDir.exists()) prefsDir.mkdirs();
                        File dest = new File(prefsDir, "benchmap_prefs.xml");
                        try (FileOutputStream destFos = new FileOutputStream(dest)) {
                            copyStream(zis, destFos);
                        }
                        extractedFiles++;
                    } else if (name.startsWith("bench_photos/") || name.startsWith("photos/")) {
                        String fileName = name.substring(name.lastIndexOf('/') + 1);
                        if (!fileName.isEmpty() && fileName.endsWith(".jpg")) {
                            File dest = new File(photosDir, fileName);
                            try (FileOutputStream destFos = new FileOutputStream(dest)) {
                                copyStream(zis, destFos);
                            }
                            photoCount++;
                            extractedFiles++;
                        }
                    }
                    zis.closeEntry();
                }
            }

            // Reload database in singleton
            mgr.reload();

            // Broadcast internal notification so MainActivity updates live UI
            context.sendBroadcast(new Intent(ACTION_DATA_RELOADED));

            String msg = String.format(Locale.US,
                    "SUCCESS: Imported %d files (%d photos) from %s",
                    extractedFiles, photoCount, zipToImport.getAbsolutePath());

            Log.i(TAG, msg);
            showToast(context, "Restauration terminée: " + photoCount + " photos.");
            setResultCode(Activity.RESULT_OK);
            setResultData(msg);

        } catch (Exception e) {
            String err = "ERROR importing: " + e.getMessage();
            Log.e(TAG, err, e);
            showToast(context, err);
            setResultCode(Activity.RESULT_CANCELED);
            setResultData(err);
        }
    }

    private String buildGeoJson(List<SavedBench> benches) {
        try {
            JSONObject root = new JSONObject();
            root.put("type", "FeatureCollection");

            JSONArray features = new JSONArray();
            for (SavedBench sb : benches) {
                JSONObject feat = new JSONObject();
                feat.put("type", "Feature");

                JSONObject geom = new JSONObject();
                geom.put("type", "Point");
                JSONArray coords = new JSONArray();
                coords.put(sb.lon);
                coords.put(sb.lat);
                geom.put("coordinates", coords);
                feat.put("geometry", geom);

                JSONObject props = new JSONObject();
                props.put("benchId", sb.benchId);
                props.put("name", sb.name);
                props.put("subtitle", sb.subtitle != null ? sb.subtitle : "");
                props.put("park", sb.park != null ? sb.park : "");
                props.put("street", sb.street != null ? sb.street : "");
                props.put("borough", sb.borough != null ? sb.borough : "");
                props.put("note", sb.note != null ? sb.note : "");
                props.put("isCustom", sb.isCustom);
                props.put("material", sb.material != null ? sb.material : "");
                props.put("backrest", sb.backrest);
                props.put("seats", sb.seats);
                props.put("addedAt", sb.addedAt);
                props.put("updatedAt", sb.updatedAt);

                JSONArray photosArr = new JSONArray();
                if (sb.photoPaths != null) {
                    for (String p : sb.photoPaths) {
                        photosArr.put(p);
                    }
                }
                props.put("photos", photosArr);
                props.put("photoCount", photosArr.length());

                feat.put("properties", props);
                features.put(feat);
            }

            root.put("features", features);
            return root.toString(2);
        } catch (Exception e) {
            return "{\"type\":\"FeatureCollection\",\"features\":[]}";
        }
    }

    private void copyStream(InputStream in, java.io.OutputStream out) throws java.io.IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private void showToast(Context context, String text) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {}
        });
    }
}
