#!/usr/bin/env python3
"""
Compiles Montreal vector map & 9,602 benches into ultra-fast binary format (montreal_map.bin)
Version: 1 (BMAP)
"""
import json
import struct
import math
import os

ASSETS_DIR = "/root/apps/BenchMap/app/src/main/assets"
SOURCE_JSON = os.path.join(ASSETS_DIR, "native_montreal_map.json")
OUTPUT_BIN = os.path.join(ASSETS_DIR, "montreal_map.bin")

def lon_to_merc_x(lon):
    return math.radians(lon)

def lat_to_merc_y(lat):
    rad = math.radians(max(-85.05112878, min(85.05112878, lat)))
    return math.log(math.tan(math.pi / 4.0 + rad / 2.0))

def main():
    print(f"Reading source JSON: {SOURCE_JSON}...")
    with open(SOURCE_JSON, "r", encoding="utf-8") as f:
        d = json.load(f)

    # 1. Deduplicate & dictionary encode string tables
    parks = sorted(list(set(b[2] for b in d['benches'])))
    park_to_idx = {p: i for i, p in enumerate(parks)}

    materials = sorted(list(set(b[3] for b in d['benches'])))
    mat_to_idx = {m: i for i, m in enumerate(materials)}

    out = bytearray()
    
    # 2. Magic Header & Version
    out.extend(b'BMAP')
    out.extend(struct.pack('>I', 1))

    # 3. String Tables
    out.extend(struct.pack('>H', len(parks)))
    for p in parks:
        encoded = p.encode('utf-8')
        out.extend(struct.pack('>H', len(encoded)))
        out.extend(encoded)

    out.extend(struct.pack('>H', len(materials)))
    for m in materials:
        encoded = m.encode('utf-8')
        out.extend(struct.pack('>H', len(encoded)))
        out.extend(encoded)

    # 4. Geometry Serializer
    def serialize_geometry(layer, name):
        print(f"Serializing {name} ({len(layer)} items)...")
        out.extend(struct.pack('>I', len(layer)))
        for pts in layer:
            count = len(pts)
            out.extend(struct.pack('>I', count))
            min_x, max_x = float('inf'), float('-inf')
            min_y, max_y = float('inf'), float('-inf')
            floats = []
            for j in range(0, count, 2):
                lat = float(pts[j])
                lon = float(pts[j + 1])
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

    serialize_geometry(d['island'], "island polygons")
    serialize_geometry(d['parks'], "park polygons")
    serialize_geometry(d['major'], "major streets")
    serialize_geometry(d['minor'], "minor streets")

    # 5. Benches Serializer
    benches = d['benches']
    print(f"Serializing {len(benches)} benches...")
    out.extend(struct.pack('>I', len(benches)))
    for b in benches:
        lat = float(b[0])
        lon = float(b[1])
        mx = lon_to_merc_x(lon)
        my = lat_to_merc_y(lat)
        p_idx = park_to_idx[b[2]]
        m_idx = mat_to_idx[b[3]]
        backrest = int(b[4]) if len(b) > 4 else -1
        seats = int(b[5]) if len(b) > 5 else 0
        out.extend(struct.pack('>4fhhbb', lat, lon, mx, my, p_idx, m_idx, backrest, seats))

    with open(OUTPUT_BIN, "wb") as f:
        f.write(out)

    size_mb = len(out) / (1024 * 1024)
    print(f"Successfully wrote {OUTPUT_BIN} ({size_mb:.2f} MB)")

if __name__ == "__main__":
    main()
