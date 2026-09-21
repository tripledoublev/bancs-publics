#!/usr/bin/env python3
"""
Compiles Montreal vector map & 9,602 benches with exact street names and full island streets.
Asset: montreal_map.bin
"""
import json
import struct
import math
import os

ASSETS_DIR = "/root/apps/BenchMap/app/src/main/assets"
OUTPUT_BIN = os.path.join(ASSETS_DIR, "montreal_map.bin")

def lon_to_merc_x(lon):
    return math.radians(lon)

def lat_to_merc_y(lat):
    rad = math.radians(max(-85.05112878, min(85.05112878, lat)))
    return math.log(math.tan(math.pi / 4.0 + rad / 2.0))

def main():
    print("1. Loading all streets from full island and raw extracts...")
    ways = {}
    for path in ('/root/apps/BenchMap/streets_full_island.json', '/root/apps/BenchMap/streets_raw.json'):
        if os.path.exists(path):
            with open(path) as f:
                for el in json.load(f).get('elements', []):
                    ways[el['id']] = el

    print(f"Loaded {len(ways)} unique street elements.")

    # 2. Build spatial grid of street segments for point-to-segment distance
    print("2. Indexing streets for bench name association...")
    GRID = 0.005
    grid = {}

    def get_cells(lat1, lon1, lat2, lon2):
        r1, r2 = int(min(lat1, lat2) / GRID), int(max(lat1, lat2) / GRID)
        c1, c2 = int(min(lon1, lon2) / GRID), int(max(lon1, lon2) / GRID)
        for r in range(r1, r2 + 1):
            for c in range(c1, c2 + 1):
                yield (r, c)

    def dist_pt_seg(plat, plon, lat1, lon1, lat2, lon2):
        cos_lat = 0.701
        px = plon * cos_lat * 111000
        py = plat * 111000
        x1 = lon1 * cos_lat * 111000
        y1 = lat1 * 111000
        x2 = lon2 * cos_lat * 111000
        y2 = lat2 * 111000
        dx = x2 - x1
        dy = y2 - y1
        l2 = dx*dx + dy*dy
        if l2 == 0:
            return math.hypot(px - x1, py - y1)
        t = max(0.0, min(1.0, ((px - x1)*dx + (py - y1)*dy) / l2))
        return math.hypot(px - (x1 + t*dx), py - (y1 + t*dy))

    for el in ways.values():
        name = el.get('tags', {}).get('name')
        if not name: continue
        geom = el.get('geometry', [])
        for i in range(len(geom) - 1):
            p1, p2 = geom[i], geom[i+1]
            for cell in get_cells(p1['lat'], p1['lon'], p2['lat'], p2['lon']):
                grid.setdefault(cell, []).append((name, p1['lat'], p1['lon'], p2['lat'], p2['lon']))

    # 3. Categorize streets into Major and Minor
    major_types = {'motorway', 'motorway_link', 'trunk', 'trunk_link', 'primary', 'primary_link', 'secondary', 'secondary_link'}
    major_streets = []
    minor_streets = []

    for el in ways.values():
        hw = el.get('tags', {}).get('highway')
        geom = el.get('geometry', [])
        if len(geom) < 2: continue
        
        pts = []
        for p in geom:
            pts.append(float(p['lat']))
            pts.append(float(p['lon']))
            
        if hw in major_types:
            major_streets.append(pts)
        else:
            minor_streets.append(pts)

    print(f"Streets sorted: {len(major_streets)} major arteries, {len(minor_streets)} secondary streets.")

    # 4. Load base map data (Island, Parks, Benches)
    with open('/root/apps/BenchMap/data/native_montreal_map.json') as f:
        d = json.load(f)

    # 5. Match each bench to nearest street
    print("3. Matching 9,602 benches to nearest street names...")
    benches = d['benches']
    bench_street_names = []
    street_names_set = set([''])

    for b in benches:
        lat, lon = float(b[0]), float(b[1])
        cr, cc = int(lat / GRID), int(lon / GRID)
        best_street = ''
        min_d = 500.0 # search within 500m
        for dr in (-1, 0, 1):
            for dc in (-1, 0, 1):
                for sname, lat1, lon1, lat2, lon2 in grid.get((cr + dr, cc + dc), []):
                    d_seg = dist_pt_seg(lat, lon, lat1, lon1, lat2, lon2)
                    if d_seg < min_d:
                        min_d = d_seg
                        best_street = sname
        bench_street_names.append(best_street)
        if best_street: street_names_set.add(best_street)

    # Verification on test coordinates (45.48123, -73.56470)
    for i, b in enumerate(benches):
        if abs(b[0] - 45.48123) < 0.0001 and abs(b[1] - -73.56470) < 0.0001:
            print(f"Verified target bench ({b[0]}, {b[1]}): street -> '{bench_street_names[i]}'")

    # 6. Dictionary encode String Tables
    parks = sorted(list(set(b[2] for b in benches)))
    park_to_idx = {p: i for i, p in enumerate(parks)}

    materials = sorted(list(set(b[3] for b in benches)))
    mat_to_idx = {m: i for i, m in enumerate(materials)}

    streets = sorted(list(street_names_set))
    street_to_idx = {s: i for i, s in enumerate(streets)}

    # 7. Write binary format
    out = bytearray()
    out.extend(b'BMAP')
    out.extend(struct.pack('>I', 2)) # Version 2 with street names

    # Write String Tables (Parks, Materials, Streets)
    def write_str_table(table, name):
        out.extend(struct.pack('>H', len(table)))
        for s in table:
            encoded = s.encode('utf-8')
            out.extend(struct.pack('>H', len(encoded)))
            out.extend(encoded)
        print(f"Wrote {len(table)} {name} entries.")

    write_str_table(parks, "parks")
    write_str_table(materials, "materials")
    write_str_table(streets, "street names")

    # Geometry Serializer
    def serialize_layer(layer, name):
        print(f"Serializing {name} ({len(layer)} items)...")
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

    serialize_layer(d['island'], "island polygons")
    serialize_layer(d['parks'], "park polygons")
    serialize_layer(major_streets, "major streets")
    serialize_layer(minor_streets, "minor streets")

    # Benches Serializer (Version 2)
    print(f"Serializing {len(benches)} benches...")
    out.extend(struct.pack('>I', len(benches)))
    for i, b in enumerate(benches):
        lat = float(b[0])
        lon = float(b[1])
        mx = lon_to_merc_x(lon)
        my = lat_to_merc_y(lat)
        p_idx = park_to_idx[b[2]]
        s_idx = street_to_idx[bench_street_names[i]]
        m_idx = mat_to_idx[b[3]]
        backrest = int(b[4]) if len(b) > 4 else -1
        seats = int(b[5]) if len(b) > 5 else 0
        # 4f (16B) + 3h (6B) + 2b (2B) = 24 bytes per bench
        out.extend(struct.pack('>4fhhhbb', lat, lon, mx, my, p_idx, s_idx, m_idx, backrest, seats))

    with open(OUTPUT_BIN, "wb") as f:
        f.write(out)

    size_mb = len(out) / (1024 * 1024)
    print(f"Successfully generated {OUTPUT_BIN} ({size_mb:.2f} MB)")

if __name__ == "__main__":
    main()
