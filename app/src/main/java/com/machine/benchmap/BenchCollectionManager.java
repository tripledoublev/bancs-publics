package com.machine.benchmap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Log;

import android.media.ExifInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton manager for local bench playlists/collections, personal notes, and photo attachments.
 * 100% on-device private JSON persistence.
 */
public class BenchCollectionManager {
    private static final String TAG = "BenchCollectionMgr";
    private static final String FILE_NAME = "collections.json";
    private static final String PHOTOS_DIR = "bench_photos";

    private static BenchCollectionManager instance;

    private final Context appContext;
    private final List<BenchCollection> collections = new ArrayList<>();
    private final Map<String, SavedBench> savedBenches = new LinkedHashMap<>();
    private boolean isLoaded = false;

    public static synchronized BenchCollectionManager getInstance(Context context) {
        if (instance == null) {
            instance = new BenchCollectionManager(context.getApplicationContext());
        }
        return instance;
    }

    private BenchCollectionManager(Context context) {
        this.appContext = context;
        load();
    }

    public synchronized void load() {
        if (isLoaded) return;
        collections.clear();
        savedBenches.clear();

        File file = new File(appContext.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            initDefaults();
            isLoaded = true;
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JSONObject root = new JSONObject(sb.toString());

            // Load saved benches
            JSONObject benchesObj = root.optJSONObject("savedBenches");
            if (benchesObj != null) {
                JSONArray keys = benchesObj.names();
                if (keys != null) {
                    for (int i = 0; i < keys.length(); i++) {
                        String key = keys.getString(i);
                        JSONObject bJson = benchesObj.optJSONObject(key);
                        if (bJson != null) {
                            SavedBench bench = SavedBench.fromJson(bJson);
                            if (bench != null) {
                                savedBenches.put(bench.benchId, bench);
                            }
                        }
                    }
                }
            }

            // Load collections
            JSONArray colArr = root.optJSONArray("collections");
            if (colArr != null) {
                for (int i = 0; i < colArr.length(); i++) {
                    JSONObject cJson = colArr.optJSONObject(i);
                    if (cJson != null) {
                        BenchCollection col = BenchCollection.fromJson(cJson);
                        if (col != null) {
                            collections.add(col);
                        }
                    }
                }
            }

            if (collections.isEmpty()) {
                initDefaults();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading collections.json", e);
            initDefaults();
        } finally {
            isLoaded = true;
        }
    }

    private void initDefaults() {
        if (collections.isEmpty()) {
            BenchCollection defaultCol = new BenchCollection(
                    BenchCollection.DEFAULT_ID,
                    "Coups de cœur",
                    "❤️",
                    "Mes bancs préférés à Montréal"
            );
            collections.add(defaultCol);
            save();
        }
    }

    public synchronized void save() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);

            JSONArray colArr = new JSONArray();
            for (BenchCollection col : collections) {
                colArr.put(col.toJson());
            }
            root.put("collections", colArr);

            JSONObject benchesObj = new JSONObject();
            for (SavedBench b : savedBenches.values()) {
                benchesObj.put(b.benchId, b.toJson());
            }
            root.put("savedBenches", benchesObj);

            File file = new File(appContext.getFilesDir(), FILE_NAME);
            File tmpFile = new File(appContext.getFilesDir(), FILE_NAME + ".tmp");

            try (FileOutputStream fos = new FileOutputStream(tmpFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                writer.write(root.toString(2));
                writer.flush();
            }

            if (tmpFile.renameTo(file) || (file.delete() && tmpFile.renameTo(file))) {
                // Success
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving collections.json", e);
        }
    }

    public synchronized List<BenchCollection> getCollections() {
        return new ArrayList<>(collections);
    }

    public synchronized BenchCollection getCollection(String id) {
        if (id == null) return null;
        for (BenchCollection col : collections) {
            if (col.id.equals(id)) return col;
        }
        return null;
    }

    public synchronized BenchCollection createCollection(String name, String emoji, String description) {
        String id = "col_" + System.currentTimeMillis();
        BenchCollection col = new BenchCollection(id, name, emoji, description);
        collections.add(col);
        save();
        return col;
    }

    public synchronized boolean deleteCollection(String id) {
        if (id == null || id.equals(BenchCollection.DEFAULT_ID)) {
            return false; // Cannot delete default collection
        }
        for (int i = 0; i < collections.size(); i++) {
            if (collections.get(i).id.equals(id)) {
                collections.remove(i);
                save();
                return true;
            }
        }
        return false;
    }

    public synchronized void updateCollection(String id, String name, String emoji, String description) {
        BenchCollection col = getCollection(id);
        if (col != null) {
            col.name = name;
            col.emoji = emoji;
            col.description = description;
            save();
        }
    }

    public synchronized SavedBench getSavedBench(String benchId) {
        return savedBenches.get(benchId);
    }

    public synchronized boolean isBenchSaved(String benchId) {
        if (benchId == null) return false;
        SavedBench b = savedBenches.get(benchId);
        if (b != null && (b.hasNote() || b.hasPhotos())) {
            return true;
        }
        for (BenchCollection col : collections) {
            if (col.containsBench(benchId)) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<BenchCollection> getCollectionsForBench(String benchId) {
        List<BenchCollection> res = new ArrayList<>();
        if (benchId == null) return res;
        for (BenchCollection col : collections) {
            if (col.containsBench(benchId)) {
                res.add(col);
            }
        }
        return res;
    }

    public synchronized List<SavedBench> getBenchesInCollection(String collectionId) {
        List<SavedBench> res = new ArrayList<>();
        BenchCollection col = getCollection(collectionId);
        if (col == null) return res;

        for (String benchId : col.benchIds) {
            SavedBench b = savedBenches.get(benchId);
            if (b != null) {
                res.add(b);
            }
        }
        return res;
    }

    public synchronized int getTotalSavedBenchesCount() {
        return savedBenches.size();
    }

    public synchronized void saveBenchWithCollections(SavedBench bench, List<String> targetCollectionIds) {
        if (bench == null) return;
        savedBenches.put(bench.benchId, bench);

        for (BenchCollection col : collections) {
            boolean shouldContain = targetCollectionIds != null && targetCollectionIds.contains(col.id);
            if (shouldContain) {
                col.addBench(bench.benchId);
            } else {
                col.removeBench(bench.benchId);
            }
        }
        save();
    }

    public synchronized void toggleBenchInCollection(Bench bench, String collectionId) {
        if (bench == null) return;
        String bId = bench.getId();
        BenchCollection col = getCollection(collectionId);
        if (col == null) return;

        if (col.containsBench(bId)) {
            col.removeBench(bId);
            // Check if bench is in any other collection and has no note/photos
            if (getCollectionsForBench(bId).isEmpty()) {
                SavedBench sb = savedBenches.get(bId);
                if (sb == null || (!sb.hasNote() && !sb.hasPhotos())) {
                    savedBenches.remove(bId);
                }
            }
        } else {
            col.addBench(bId);
            if (!savedBenches.containsKey(bId)) {
                savedBenches.put(bId, new SavedBench(bench));
            }
        }
        save();
    }

    public synchronized void removeBenchCompletely(String benchId) {
        if (benchId == null) return;
        SavedBench sb = savedBenches.remove(benchId);
        if (sb != null && sb.photoPaths != null) {
            for (String photoPath : sb.photoPaths) {
                deletePhotoFile(photoPath);
            }
        }
        for (BenchCollection col : collections) {
            col.removeBench(benchId);
        }
        save();
    }

    // Photo helpers

    public File getPhotosDirectory() {
        File dir = new File(appContext.getFilesDir(), PHOTOS_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public File getPhotoFile(String relativePath) {
        if (relativePath == null) return null;
        return new File(appContext.getFilesDir(), relativePath);
    }

    public String importPhotoFromUri(Uri uri, String benchId) {
        try {
            File photosDir = getPhotosDirectory();
            String safeBenchId = benchId.replace(',', '_').replace('-', 'm').replace('.', 'p');
            String fileName = "photo_" + safeBenchId + "_" + System.currentTimeMillis() + ".jpg";
            File destFile = new File(photosDir, fileName);

            // 1. Decode bounds
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (InputStream is = appContext.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, opts);
            }

            int origW = opts.outWidth;
            int origH = opts.outHeight;
            if (origW <= 0 || origH <= 0) return null;

            int maxDim = 1600;
            int inSampleSize = 1;
            while ((origW / inSampleSize) > maxDim || (origH / inSampleSize) > maxDim) {
                inSampleSize *= 2;
            }

            // 2. Decode sampled image
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = inSampleSize;
            Bitmap bitmap;
            try (InputStream is = appContext.getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(is, null, opts);
            }
            if (bitmap == null) return null;

            // 3. Handle rotation
            int rotationDegrees = 0;
            try (InputStream is = appContext.getContentResolver().openInputStream(uri)) {
                if (is != null) {
                    ExifInterface exif = new ExifInterface(is);
                    int orient = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    if (orient == ExifInterface.ORIENTATION_ROTATE_90) rotationDegrees = 90;
                    else if (orient == ExifInterface.ORIENTATION_ROTATE_180) rotationDegrees = 180;
                    else if (orient == ExifInterface.ORIENTATION_ROTATE_270) rotationDegrees = 270;
                }
            } catch (Exception ignored) {}

            if (rotationDegrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                bitmap.recycle();
                bitmap = rotated;
            }

            // 4. Save to destination file
            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                fos.flush();
            }
            bitmap.recycle();

            return PHOTOS_DIR + "/" + fileName;
        } catch (Exception e) {
            Log.e(TAG, "Failed to import photo from URI " + uri, e);
            return null;
        }
    }

    public boolean deletePhotoFile(String relativePath) {
        if (relativePath == null) return false;
        File file = getPhotoFile(relativePath);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    public static Bitmap loadThumbnail(Context context, String relativePath, int targetSize) {
        if (relativePath == null) return null;
        File file = new File(context.getFilesDir(), relativePath);
        if (!file.exists()) return null;

        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);

            int maxDim = Math.max(opts.outWidth, opts.outHeight);
            int sample = 1;
            while (maxDim / sample > targetSize * 2) {
                sample *= 2;
            }

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Exception e) {
            return null;
        }
    }
}
