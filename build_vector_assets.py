import urllib.request
import urllib.parse
import json
import os

ASSETS_DIR = "/root/apps/BenchMap/app/src/main/assets"
os.makedirs(ASSETS_DIR, exist_ok=True)
OVERPASS_URL = "https://overpass-api.de/api/interpreter"

def query_overpass(query):
    data = urllib.parse.urlencode({"data": query}).encode("utf-8")
    req = urllib.request.Request(OVERPASS_URL, data=data, headers={"User-Agent": "MontrealBenchMap/1.0"})
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

def simplify_points(pts, epsilon=0.00015):
    # Quick simplification
    if len(pts) <= 2:
        return pts
    out = [pts[0]]
    for i in range(1, len(pts)-1):
        # Keep if moved enough
        p = pts[i]
        last = out[-1]
        if abs(p[0] - last[0]) > epsilon or abs(p[1] - last[1]) > epsilon:
            out.append(p)
    out.append(pts[-1])
    return out

print("1. Extraction des contours d'eau (Fleuve Saint-Laurent, Canal Lachine)...")
water_query = """[out:json][timeout:60];
(
  way["natural"="water"](45.42, -73.75, 45.62, -73.48);
  way["waterway"~"river|canal"](45.42, -73.75, 45.62, -73.48);
);
out geom;"""
water_raw = query_overpass(water_query)
water_lines = []
for el in water_raw.get("elements", []):
    geom = el.get("geometry", [])
    if len(geom) >= 2:
        pts = [[round(p["lat"], 5), round(p["lon"], 5)] for p in geom]
        water_lines.append(simplify_points(pts))
print(f"   -> {len(water_lines)} lignes d'eau extraites.")

print("2. Extraction des grands parcs montréalais...")
parks_query = """[out:json][timeout:60];
(
  way["leisure"="park"](45.44, -73.70, 45.60, -73.50);
);
out geom;"""
parks_raw = query_overpass(parks_query)
parks_polys = []
for el in parks_raw.get("elements", []):
    geom = el.get("geometry", [])
    name = el.get("tags", {}).get("name", "")
    if len(geom) >= 3:
        pts = [[round(p["lat"], 5), round(p["lon"], 5)] for p in geom]
        parks_polys.append({
            "name": name,
            "pts": simplify_points(pts)
        })
print(f"   -> {len(parks_polys)} parcs extraits.")

print("3. Extraction des grandes artères / voies majeures (Sherbrooke, St-Laurent, etc.)...")
roads_query = """[out:json][timeout:60];
(
  way["highway"~"primary|secondary|trunk"](45.44, -73.68, 45.58, -73.52);
);
out geom;"""
roads_raw = query_overpass(roads_query)
road_lines = []
for el in roads_raw.get("elements", []):
    geom = el.get("geometry", [])
    if len(geom) >= 2:
        pts = [[round(p["lat"], 5), round(p["lon"], 5)] for p in geom]
        road_lines.append(simplify_points(pts))
print(f"   -> {len(road_lines)} segments routiers majeurs extraits.")

# Pack all vector layers together
vector_bundle = {
    "bounds": {
        "minLat": 45.40,
        "maxLat": 45.65,
        "minLon": -73.80,
        "maxLon": -73.48
    },
    "water": water_lines,
    "parks": parks_polys,
    "roads": road_lines
}

out_file = os.path.join(ASSETS_DIR, "montreal_vector_map.json")
with open(out_file, "w", encoding="utf-8") as f:
    json.dump(vector_bundle, f, separators=(',', ':'))

print(f"Vector map généré avec succès : {out_file} ({round(os.path.getsize(out_file)/1024, 1)} Ko)")
