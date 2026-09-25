package map.bench.bancs_publics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a saved bench with personal notes and local photo attachments.
 * 100% on-device local storage.
 */
public class SavedBench {
    public final String benchId;
    public final double lat;
    public final double lon;
    public final String name;
    public final String subtitle;
    public final String park;
    public final String street;
    public final String borough;
    public String note;
    public final List<String> photoPaths; // Relative paths in internal filesDir (e.g. "bench_photos/...")
    public final boolean isCustom;
    public final String material;
    public final int backrest;
    public final int seats;
    public final long addedAt;
    public long updatedAt;

    public SavedBench(Bench bench) {
        this(
                bench.getId(),
                bench.lat,
                bench.lon,
                bench.getDisplayName(),
                bench.getDisplaySubtitle(),
                bench.park,
                bench.street,
                bench.borough,
                "",
                new ArrayList<>(),
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                bench.isCustom,
                bench.material,
                bench.backrest,
                bench.seats
        );
    }

    public SavedBench(String benchId, double lat, double lon, String name, String subtitle,
                      String park, String street, String borough, String note,
                      List<String> photoPaths, long addedAt, long updatedAt) {
        this(benchId, lat, lon, name, subtitle, park, street, borough, note, photoPaths, addedAt, updatedAt, false, "", -1, 0);
    }

    public SavedBench(String benchId, double lat, double lon, String name, String subtitle,
                      String park, String street, String borough, String note,
                      List<String> photoPaths, long addedAt, long updatedAt,
                      boolean isCustom, String material, int backrest, int seats) {
        this.benchId = benchId;
        this.lat = lat;
        this.lon = lon;
        this.name = (name != null && !name.trim().isEmpty()) ? name : (isCustom ? "Mon banc" : "Banc public");
        this.subtitle = (subtitle != null) ? subtitle : "";
        this.park = (park != null) ? park : "";
        this.street = (street != null) ? street : "";
        this.borough = (borough != null) ? borough : "";
        this.note = (note != null) ? note : "";
        this.photoPaths = (photoPaths != null) ? new ArrayList<>(photoPaths) : new ArrayList<>();
        this.addedAt = addedAt > 0 ? addedAt : System.currentTimeMillis();
        this.updatedAt = updatedAt > 0 ? updatedAt : this.addedAt;
        this.isCustom = isCustom;
        this.material = material != null ? material : "";
        this.backrest = backrest;
        this.seats = seats;
    }

    public Bench toBench() {
        return new Bench(lat, lon, park, street, borough, 0, material, backrest, seats, isCustom);
    }

    public boolean hasNote() {
        return note != null && !note.trim().isEmpty();
    }

    public boolean hasPhotos() {
        return photoPaths != null && !photoPaths.isEmpty();
    }

    public String getPrimaryPhoto() {
        if (photoPaths != null && !photoPaths.isEmpty()) {
            return photoPaths.get(0);
        }
        return null;
    }

    public void addPhoto(String relativePath) {
        if (relativePath != null && !photoPaths.contains(relativePath)) {
            photoPaths.add(relativePath);
            this.updatedAt = System.currentTimeMillis();
        }
    }

    public void removePhoto(String relativePath) {
        if (photoPaths != null && photoPaths.remove(relativePath)) {
            this.updatedAt = System.currentTimeMillis();
        }
    }

    public void setNote(String newNote) {
        this.note = (newNote != null) ? newNote : "";
        this.updatedAt = System.currentTimeMillis();
    }

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("benchId", benchId);
            obj.put("lat", lat);
            obj.put("lon", lon);
            obj.put("name", name);
            obj.put("subtitle", subtitle);
            obj.put("park", park);
            obj.put("street", street);
            obj.put("borough", borough);
            obj.put("note", note);
            JSONArray photosArr = new JSONArray();
            if (photoPaths != null) {
                for (String p : photoPaths) {
                    photosArr.put(p);
                }
            }
            obj.put("photoPaths", photosArr);
            obj.put("addedAt", addedAt);
            obj.put("updatedAt", updatedAt);
            obj.put("isCustom", isCustom);
            obj.put("material", material);
            obj.put("backrest", backrest);
            obj.put("seats", seats);
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static SavedBench fromJson(JSONObject obj) {
        if (obj == null) return null;
        String benchId = obj.optString("benchId", "");
        if (benchId.isEmpty()) return null;
        double lat = obj.optDouble("lat", 0.0);
        double lon = obj.optDouble("lon", 0.0);
        String name = obj.optString("name", "Banc public");
        String subtitle = obj.optString("subtitle", "");
        String park = obj.optString("park", "");
        String street = obj.optString("street", "");
        String borough = obj.optString("borough", "");
        String note = obj.optString("note", "");
        long addedAt = obj.optLong("addedAt", System.currentTimeMillis());
        long updatedAt = obj.optLong("updatedAt", addedAt);
        boolean isCustom = obj.optBoolean("isCustom", benchId.startsWith("custom_"));
        String material = obj.optString("material", "");
        int backrest = obj.optInt("backrest", -1);
        int seats = obj.optInt("seats", 0);

        List<String> photos = new ArrayList<>();
        JSONArray photosArr = obj.optJSONArray("photoPaths");
        if (photosArr != null) {
            for (int i = 0; i < photosArr.length(); i++) {
                String path = photosArr.optString(i, null);
                if (path != null && !path.trim().isEmpty()) {
                    photos.add(path);
                }
            }
        }
        return new SavedBench(benchId, lat, lon, name, subtitle, park, street, borough, note, photos, addedAt, updatedAt, isCustom, material, backrest, seats);
    }
}
