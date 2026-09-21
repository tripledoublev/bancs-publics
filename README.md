# BenchMap (Les bancs de Montréal) 🪑

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android_10+-3DDC84.svg?logo=android&logoColor=white)](https://android.com)
[![Offline First](https://img.shields.io/badge/Architecture-100%25_Offline_Vector-black.svg)]()
[![Open Data](https://img.shields.io/badge/Data-Données_ouvertes_Montréal-blue.svg)](https://donnees.montreal.ca/)

A 100% local-first, privacy-respecting, native vector map of all **9,602 public benches** across the Island of Montreal. Built with a custom hardware-accelerated Canvas engine, zero external tiles, zero cloud dependencies, and an authentic Swiss graphic design aesthetic.

---

## Features

- **100% Offline Vector Rendering**: Custom Canvas engine rendering 37 borough polygons, 1,648 municipal parks, 29,708 street segments, and 9,602 benches via OpenGL `drawLines` hardware batching.
- **Ultra-Fast Binary Map Format**: Custom IEEE 754 float binary format (`montreal_map.bin`) loads the complete municipal bench cartography in **<70 ms** with zero JSON garbage collection overhead.
- **Dual Swiss Themes**:
  - **Nordic Paper & Precision Ink (Light)**: Warm architectural paper, sage green parks, crisp arterial slate lines, and deep charcoal bench markers.
  - **Swiss Nocturne (Dark / OLED)**: True pitch-black oceanic water, matte obsidian slate landmass, and high-contrast chalk platinum / luminous mint emerald dots.
- **Every Bench Linked to Street & Park**: Spatial matching connects every bench to its actual street name (e.g., *Rue du Centre*, *Rue Wellington*, *Boulevard Saint-Laurent*) and park name.
- **Smart Discovery**:
  - 🎲 **Au hasard (Random Bench)**: Jump to any surprising bench on the island.
  - 🧭 **Le plus proche (Nearest Bench)**: Instant Euclidean k-NN search to locate your closest seat.
  - 📍 **Turn-by-turn Walking Directions**: One-tap intent to start pedestrian navigation in OsmAnd, Organic Maps, or Google Maps.
  - ↗ **Share Coordinates**: Easy Swiss card formatting to share meeting points.
- **Compass & Orientation Cone**: Native hardware rotation vector sensor drives real-time walking heading and location accuracy halos.
- **100% Private & Open Source**: No tracking, no analytics, no ads, no cloud accounts, and no Google Play Services dependencies.

---

## Screenshots

| Nordic Paper (Light Mode) | Swiss Nocturne (Dark Mode) |
| :---: | :---: |
| <img src="screenshot_light_mode.png" width="340" /> | <img src="dark_mode.png" width="340" /> |

---

## Tech Stack

- **Platform**: Android 10+ (API 29 to API 34)
- **Language**: Java 17
- **UI Architecture**: Hardware-accelerated Custom View (`MontrealBenchMapView.java`) + Material Components 3
- **Spatial Indexing**: 2D Uniform Spatial Grid partitioning over WGS 84 Web Mercator projection
- **Data Source**: [Données ouvertes — Ville de Montréal](https://donnees.montreal.ca/)

---

## Building from Source

### Prerequisites
- JDK 17
- Android SDK (API 34, Build-Tools 34.0.0)

### Clone & Build
```bash
git clone https://github.com/<your-username>/BenchMap.git
cd BenchMap

# Build Debug APK
./gradlew assembleDebug

# Build Production Release APK
./gradlew assembleRelease

# Build Google Play App Bundle (.aab)
./gradlew bundleRelease
```

Output APK will be located at:
`app/build/outputs/apk/release/app-release-unsigned.apk` (or signed if `benchmap-release.jks` is provided).

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
Data provided under the [Données ouvertes de la Ville de Montréal](https://donnees.montreal.ca/) open license.
