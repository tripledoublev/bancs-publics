import json
import os

ASSETS_DIR = "/root/apps/BenchMap/app/src/main/assets"

def simplify_poly(pts, step=2):
    # reduce density for fast native Canvas drawing
    if len(pts) <= 4:
        return pts
    res = [pts[0]]
    for i in range(1, len(pts)-1, step):
        res.append(pts[i])
    res.append(pts[-1])
    return res

# 1. Load Boroughs (Island Outlines)
with open(os.path.join(ASSETS_DIR, "montreal_boroughs.geojson"), "r", encoding="utf-8") as f:
    lim_data = json.load(f)

borough_paths = []
for feat in lim_data.get("features", []):
    geom = feat.get("geometry", {})
    gtype = geom.get("type")
    coords = geom.get("coordinates", [])
    
    name = feat.get("properties", {}).get("NOM") or feat.get("properties", {}).get("MUN_NOM") or ""
    
    if gtype == "Polygon":
        for ring in coords:
            if len(ring) >= 3:
                pts = [[round(p[1], 5), round(p[0], 5)] for p in ring] # [lat, lon]
                borough_paths.append(simplify_poly(pts, step=2))
    elif gtype == "MultiPolygon":
        for poly in coords:
            for ring in poly:
                if len(ring) >= 3:
                    pts = [[round(p[1], 5), round(p[0], 5)] for p in ring]
                    borough_paths.append(simplify_poly(pts, step=2))

print(f"Boroughs extracted: {len(borough_paths)} closed polygon rings.")

# 2. Load Parks
with open(os.path.join(ASSETS_DIR, "montreal_parks.geojson"), "r", encoding="utf-8") as f:
    parks_data = json.load(f)

park_paths = []
for feat in parks_data.get("features", []):
    geom = feat.get("geometry", {})
    gtype = geom.get("type")
    coords = geom.get("coordinates", [])
    
    # Filter for visible significant parks
    if gtype == "Polygon":
        for ring in coords:
            if len(ring) >= 3:
                pts = [[round(p[1], 5), round(p[0], 5)] for p in ring]
                park_paths.append(simplify_poly(pts, step=3))
    elif gtype == "MultiPolygon":
        for poly in coords:
            for ring in poly:
                if len(ring) >= 3:
                    pts = [[round(p[1], 5), round(p[0], 5)] for p in ring]
                    park_paths.append(simplify_poly(pts, step=3))

print(f"Parks extracted: {len(park_paths)} polygon rings.")

# 3. Load Benches
with open(os.path.join(ASSETS_DIR, "benches_compact.json"), "r", encoding="utf-8") as f:
    benches = json.load(f)
print(f"Benches loaded: {len(benches)} benches.")

# Package all into unified native file
native_map = {
    "bounds": {
        "minLat": 45.40,
        "maxLat": 45.71,
        "minLon": -73.98,
        "maxLon": -73.47
    },
    "island": borough_paths,
    "parks": park_paths,
    "benches": benches
}

out_path = os.path.join(ASSETS_DIR, "native_montreal_map.json")
with open(out_path, "w", encoding="utf-8") as f:
    json.dump(native_map, f, separators=(',', ':'))

print(f"Native map written : {out_path} ({round(os.path.getsize(out_path)/1024, 1)} Ko)")
