package map.bench.bancs_publics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a custom playlist / list of public benches (e.g. "❤️ Coups de cœur", "Bancs ensoleillés").
 */
public class BenchCollection {
    public static final String DEFAULT_ID = "default_favorites";
    public static final String MES_BANCS_ID = "col_mes_bancs";

    public final String id;
    public String name;
    public String emoji;
    public String description;
    public final long createdAt;
    public final List<String> benchIds;

    public boolean isSystemList() {
        return DEFAULT_ID.equals(id) || MES_BANCS_ID.equals(id);
    }

    public BenchCollection(String id, String name, String emoji, String description) {
        this(id, name, emoji, description, System.currentTimeMillis(), new ArrayList<>());
    }

    public BenchCollection(String id, String name, String emoji, String description,
                           long createdAt, List<String> benchIds) {
        this.id = (id != null && !id.trim().isEmpty()) ? id : "col_" + System.currentTimeMillis();
        this.name = (name != null && !name.trim().isEmpty()) ? name : "Ma liste";
        this.emoji = (emoji != null && !emoji.trim().isEmpty()) ? emoji : "📌";
        this.description = (description != null) ? description : "";
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
        this.benchIds = (benchIds != null) ? new ArrayList<>(benchIds) : new ArrayList<>();
    }

    public boolean containsBench(String benchId) {
        return benchIds != null && benchIds.contains(benchId);
    }

    public boolean addBench(String benchId) {
        if (benchId != null && !containsBench(benchId)) {
            benchIds.add(0, benchId); // Newest additions at top
            return true;
        }
        return false;
    }

    public boolean removeBench(String benchId) {
        if (benchIds != null) {
            return benchIds.remove(benchId);
        }
        return false;
    }

    public int size() {
        return benchIds != null ? benchIds.size() : 0;
    }

    public String getDisplayTitle() {
        if (emoji != null && !emoji.trim().isEmpty()) {
            return emoji + " " + name;
        }
        return name;
    }

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("name", name);
            obj.put("emoji", emoji);
            obj.put("description", description);
            obj.put("createdAt", createdAt);
            JSONArray arr = new JSONArray();
            if (benchIds != null) {
                for (String bid : benchIds) {
                    arr.put(bid);
                }
            }
            obj.put("benchIds", arr);
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static BenchCollection fromJson(JSONObject obj) {
        if (obj == null) return null;
        String id = obj.optString("id", "");
        if (id.isEmpty()) return null;
        String name = obj.optString("name", "Ma liste");
        String emoji = obj.optString("emoji", "📌");
        String description = obj.optString("description", "");
        long createdAt = obj.optLong("createdAt", System.currentTimeMillis());

        List<String> benchIds = new ArrayList<>();
        JSONArray arr = obj.optJSONArray("benchIds");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String bid = arr.optString(i, null);
                if (bid != null && !bid.trim().isEmpty() && !benchIds.contains(bid)) {
                    benchIds.add(bid);
                }
            }
        }
        return new BenchCollection(id, name, emoji, description, createdAt, benchIds);
    }
}
