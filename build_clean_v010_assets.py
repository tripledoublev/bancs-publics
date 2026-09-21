#!/usr/bin/env python3
"""
Compiles Montreal vector map with:
- 48,012 official Ville de Montréal Geobase street segments (surveyor-grade full island)
- 2,600+ regional connectors, river bridges, and adjacent Laval/Longueuil segments from OSM
- 9,602 public benches associated with exact street name, civic house number, and borough
Binary Map Format: Version 3
"""
import json
import struct
import math
import os
import time

ASSETS_DIR = "/root/apps/BenchMap/app/src/main/assets"
OUTPUT_BIN = os.path.join(ASSETS_DIR, "montreal_map.bin")
GEOBASE_PATH = "/root/apps/BenchMap/data/geobase.json"
NATIVE_MAP_PATH = "/root/apps/BenchMap/data/native_montreal_map.json"
OSM_STREETS_PATH = "/root/apps/BenchMap/streets_full_island.json"
OSM_RAW_PATH = "/root/apps/BenchMap/streets_raw.json"

def lon_to_merc_x(lon):
    return math.radians(lon)

def lat_to_merc_y(lat):
    rad = math.radians(max(-85.05112878, min(85.05112878, lat)))
    return math.log(math.tan(math.pi / 4.0 + rad / 2.0))

def main():
    t0 = time.time()
    print("1. Loading datasets...")
    with open(GEOBASE_PATH, "r", encoding="utf-8") as f:
        geobase = json.load(f)
    geo_feats = geobase.get("features", [])
    print(f"Loaded {len(geo_feats)} official Geobase street features.")

    with open(NATIVE_MAP_PATH, "r", encoding="utf-8") as f:
        native_data = json.load(f)
    benches = native_data["benches"]
    print(f"Loaded {len(benches)} benches, {len(native_data['island'])} island rings, {len(native_data['parks'])} park rings.")

    # Load OSM elements for bridges and off-island connectors
    osm_major = []
    osm_minor = []
    osm_raw_elements = []
    
    for path in (OSM_STREETS_PATH, OSM_RAW_PATH):
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            for el in data.get("elements", []):
                osm_raw_elements.append(el)
                geom = el.get("geometry", [])
                if len(geom) < 2: continue
                hw = el.get("tags", {}).get("highway", "")
                name = el.get("tags", {}).get("name", "")
                
                # Check if bridge or off-island
                is_bridge = "pont" in name.lower()
                is_off_island = any(p['lat'] > 45.54 and -73.78 < p['lon'] < -73.65 or p['lat'] < 45.42 or p['lon'] > -73.50 for p in geom)
                
                if is_bridge or is_off_island:
                    pts = []
                    for p in geom:
                        pts.append(float(p["lat"]))
                        pts.append(float(p["lon"]))
                    if hw in ('motorway', 'motorway_link', 'trunk', 'trunk_link', 'primary', 'primary_link', 'secondary', 'secondary_link') or is_bridge:
                        osm_major.append(pts)
                    else:
                        osm_minor.append(pts)

    print(f"Loaded {len(osm_major)} OSM bridges/major connectors, {len(osm_minor)} OSM secondary connectors.")

    # 2. Build Spatial Index of Geobase Streets
    print("2. Indexing Geobase and OSM streets for spatial bench association...")
    GRID = 0.005 # ~500m grid cells
    geo_grid = {}
    osm_grid = {}
    cos_lat = 0.701

    def get_cells(lat1, lon1, lat2, lon2):
        r1, r2 = int(min(lat1, lat2) / GRID), int(max(lat1, lat2) / GRID)
        c1, c2 = int(min(lon1, lon2) / GRID), int(max(lon1, lon2) / GRID)
        for r in range(r1, r2 + 1):
            for c in range(c1, c2 + 1):
                yield (r, c)

    for feat in geo_feats:
        coords = feat.get("geometry", {}).get("coordinates", [])
        if len(coords) < 2: continue

        seg_lens = []
        tot_len = 0.0
        for i in range(len(coords) - 1):
            lon1, lat1 = coords[i][:2]
            lon2, lat2 = coords[i+1][:2]
            dx = (lon2 - lon1) * cos_lat * 111000
            dy = (lat2 - lat1) * 111000
            sl = math.hypot(dx, dy)
            seg_lens.append(sl)
            tot_len += sl

        cum_dists = [0.0]
        c_acc = 0.0
        for sl in seg_lens:
            c_acc += sl
            cum_dists.append(c_acc)

        for i in range(len(coords) - 1):
            lon1, lat1 = coords[i][:2]
            lon2, lat2 = coords[i+1][:2]
            for cell in get_cells(lat1, lon1, lat2, lon2):
                geo_grid.setdefault(cell, []).append((feat, coords, cum_dists, seg_lens, tot_len, i))

    for el in osm_raw_elements:
        name = el.get("tags", {}).get("name")
        if not name: continue
        geom = el.get("geometry", [])
        if len(geom) < 2: continue
        for i in range(len(geom) - 1):
            p1, p2 = geom[i], geom[i+1]
            for cell in get_cells(p1["lat"], p1["lon"], p2["lat"], p2["lon"]):
                osm_grid.setdefault(cell, []).append((name, p1["lat"], p1["lon"], p2["lat"], p2["lon"]))

    print(f"Built spatial grid: {len(geo_grid)} Geobase cells, {len(osm_grid)} OSM cells.")

    # 3. Categorize Streets into Major Arteries and Minor Residential Streets
    major_streets = []
    minor_streets = []

    for feat in geo_feats:
        props = feat.get("properties", {})
        c = props.get("CLASSE", 0)
        coords = feat.get("geometry", {}).get("coordinates", [])
        if len(coords) < 2: continue

        pts = []
        for p in coords:
            pts.append(float(p[1])) # lat
            pts.append(float(p[0])) # lon

        if c in (6, 7, 8):
            major_streets.append(pts)
        else:
            minor_streets.append(pts)

    major_streets.extend(osm_major)
    minor_streets.extend(osm_minor)
    print(f"Total vector streets: {len(major_streets)} major arteries & bridges, {len(minor_streets)} residential & local streets.")

    # 4. Associate Each Bench with Street Name, Civic Address, and Borough
    print("3. Matching 9,602 benches to nearest street, address number, and borough...")
    bench_street_names = []
    bench_borough_names = []
    bench_address_nums = []

    street_set = set([""])
    borough_set = set([""])

    for b in benches:
        plat, plon = float(b[0]), float(b[1])
        cr, cc = int(plat / GRID), int(plon / GRID)
        px = plon * cos_lat * 111000
        py = plat * 111000

        # Pass 1: Query Geobase
        best_d = 250.0 # search within 250m
        best_feat = None
        best_addr_num = 0
        best_cross = 0.0
        best_frac = 0.0

        for dr in (-1, 0, 1):
            for dc in (-1, 0, 1):
                for feat, coords, cum_dists, seg_lens, tot_len, i in geo_grid.get((cr + dr, cc + dc), []):
                    lon1, lat1 = coords[i][:2]
                    lon2, lat2 = coords[i+1][:2]
                    x1, y1 = lon1 * cos_lat * 111000, lat1 * 111000
                    x2, y2 = lon2 * cos_lat * 111000, lat2 * 111000
                    dx, dy = x2 - x1, y2 - y1
                    dpx, dpy = px - x1, py - y1
                    l2 = dx*dx + dy*dy
                    t = 0.0 if l2 == 0 else max(0.0, min(1.0, (dpx*dx + dpy*dy) / l2))
                    dist = math.hypot(px - (x1 + t*dx), py - (y1 + t*dy))
                    if dist < best_d:
                        best_d = dist
                        best_feat = feat
                        best_cross = dx * dpy - dy * dpx
                        best_frac = (cum_dists[i] + t * seg_lens[i]) / tot_len if tot_len > 0 else 0.0

        street_name = ""
        arr = ""
        addr_num = 0

        if best_feat:
            props = best_feat["properties"]
            street_name = props.get("ODONYME", "").strip()
            arr = props.get("ARR_GCH") if best_cross > 0 else props.get("ARR_DRT")
            if not arr:
                arr = props.get("ARR_GCH") or props.get("ARR_DRT") or props.get("LIM_GCH") or props.get("LIM_DRT") or ""
            arr = arr.strip()

            deb_g, fin_g = props.get("DEB_GCH"), props.get("FIN_GCH")
            deb_d, fin_d = props.get("DEB_DRT"), props.get("FIN_DRT")

            num = 0
            if best_cross > 0 and deb_g and fin_g:
                num = int(round(deb_g + best_frac * (fin_g - deb_g)))
                if deb_g % 2 != num % 2: num += 1
            elif deb_d and fin_d:
                num = int(round(deb_d + best_frac * (fin_d - deb_d)))
                if deb_d % 2 != num % 2: num += 1
            elif deb_g: num = deb_g
            elif deb_d: num = deb_d
            addr_num = max(0, min(65535, int(num)))
        else:
            # Pass 2: Fallback to OSM for off-island benches or deep parks
            best_osm_d = 600.0
            best_osm_name = ""
            for dr in (-1, 0, 1):
                for dc in (-1, 0, 1):
                    for name, lat1, lon1, lat2, lon2 in osm_grid.get((cr + dr, cc + dc), []):
                        x1, y1 = lon1 * cos_lat * 111000, lat1 * 111000
                        x2, y2 = lon2 * cos_lat * 111000, lat2 * 111000
                        dx, dy = x2 - x1, y2 - y1
                        dpx, dpy = px - x1, py - y1
                        l2 = dx*dx + dy*dy
                        t = 0.0 if l2 == 0 else max(0.0, min(1.0, (dpx*dx + dpy*dy) / l2))
                        dist = math.hypot(px - (x1 + t*dx), py - (y1 + t*dy))
                        if dist < best_osm_d:
                            best_osm_d = dist
                            best_osm_name = name
            street_name = best_osm_name
            if plat > 45.54 and -73.78 < plon < -73.65:
                arr = "Laval"
            elif plon > -73.50:
                arr = "Longueuil"
            else:
                arr = "Montréal"

        bench_street_names.append(street_name)
        bench_borough_names.append(arr)
        bench_address_nums.append(addr_num)

        if street_name: street_set.add(street_name)
        if arr: borough_set.add(arr)

    # Verification on target bench (45.48123, -73.56470)
    for i, b in enumerate(benches):
        if abs(b[0] - 45.48123) < 0.0001 and abs(b[1] - -73.56470) < 0.0001:
            print(f"*** TARGET BENCH (45.48123, -73.56470):")
            print(f"    Street: '{bench_street_names[i]}'")
            print(f"    Civic Number: {bench_address_nums[i]}")
            print(f"    Borough: '{bench_borough_names[i]}'")
            print(f"    Full Address: '{bench_address_nums[i]} {bench_street_names[i]}'")

    # Verification on Laval bench (45.56500, -73.69913)
    for i, b in enumerate(benches):
        if abs(b[0] - 45.56500) < 0.001 and abs(b[1] - -73.69913) < 0.001:
            print(f"*** LAVAL BENCH ({b[0]}, {b[1]}):")
            print(f"    Street: '{bench_street_names[i]}'")
            print(f"    Borough: '{bench_borough_names[i]}'")
            break

    # 5. Build String Tables
    parks = sorted(list(set(b[2] for b in benches)))
    park_to_idx = {p: i for i, p in enumerate(parks)}

    materials = sorted(list(set(b[3] for b in benches)))
    mat_to_idx = {m: i for i, m in enumerate(materials)}

    streets = sorted(list(street_set))
    street_to_idx = {s: i for i, s in enumerate(streets)}

    boroughs = sorted(list(borough_set))
    borough_to_idx = {bg: i for i, bg in enumerate(boroughs)}

    # 6. Serialize to Binary Format (Version 3)
    print("4. Serializing binary map v3...")
    out = bytearray()
    out.extend(b'BMAP')
    out.extend(struct.pack('>I', 3))

    def write_str_table(table, label):
        out.extend(struct.pack('>H', len(table)))
        for s in table:
            encoded = s.encode('utf-8')
            out.extend(struct.pack('>H', len(encoded)))
            out.extend(encoded)
        print(f"Wrote {len(table)} {label} entries.")

    write_str_table(parks, "parks")
    write_str_table(materials, "materials")
    write_str_table(streets, "street names")
    write_str_table(boroughs, "borough names")

    def serialize_layer(layer, label):
        print(f"Serializing {label} ({len(layer)} items)...")
        out.extend(struct.pack('>I', len(layer)))
        for pts in layer:
            count = len(pts)
            out.extend(struct.pack('>I', count))
            min_x, max_x = float('inf'), float('-inf')
            min_y, max_y = float('inf'), float('-inf')
            floats = []
            for j in range(0, count, 2):
                lat = pts[j]
                lon = pts[j+1]
                mx = lon_to_merc_x(lon)
                my = lat_to_merc_y(lat)
                floats.append(mx)
                floats.append(my)
                if mx < min_x: min_x = mx
                if mx > max_x: max_x = mx
                if my < min_y: min_y = my
                if my > max_y: max_y = my
            out.extend(struct.pack('>4f', min_x, max_x, min_y, max_y))
            out.extend(struct.pack(f'>{count}f', *floats))

    serialize_layer(native_data['island'], "island polygons")
    serialize_layer(native_data['parks'], "park polygons")
    serialize_layer(major_streets, "major arteries & bridges")
    serialize_layer(minor_streets, "residential & local streets")

    print(f"Serializing {len(benches)} benches...")
    out.extend(struct.pack('>I', len(benches)))
    for i, b in enumerate(benches):
        lat = float(b[0])
        lon = float(b[1])
        mx = lon_to_merc_x(lon)
        my = lat_to_merc_y(lat)
        p_idx = park_to_idx[b[2]]
        s_idx = street_to_idx[bench_street_names[i]]
        bg_idx = borough_to_idx[bench_borough_names[i]]
        addr_num = bench_address_nums[i]
        m_idx = mat_to_idx[b[3]]
        backrest = int(b[4]) if len(b) > 4 else -1
        seats = int(b[5]) if len(b) > 5 else 0

        # Format v3: 4f (16B) + 3h (6B) + 1H (2B) + 1h (2B) + 2b (2B) = 28 bytes per bench
        out.extend(struct.pack('>4fhhhHhbb', lat, lon, mx, my, p_idx, s_idx, bg_idx, addr_num, m_idx, backrest, seats))

    with open(OUTPUT_BIN, "wb") as f:
        f.write(out)

    size_mb = len(out) / (1024 * 1024)
    print(f"SUCCESS: Generated {OUTPUT_BIN} ({size_mb:.2f} MB in {time.time()-t0:.1f}s)")

if __name__ == "__main__":
    main()
