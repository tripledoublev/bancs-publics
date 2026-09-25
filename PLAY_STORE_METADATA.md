# 🇨🇦 Bancs publics (Montréal)
## Google Play Store Listing & Metadata Package

---

### 1. Store Identity & Core Info
- **App Title (Max 30 chars)**:
  - **FR (Défaut)**: `Bancs publics` (13 caractères)
  - **EN**: `Bancs publics` (13 characters)
- **Package Name**: `map.bench.bancs_publics`
- **Current Version**: `0.2.10` (versionCode `14`)
- **Category**: `Maps & Navigation` (Cartes et navigation) or `Travel & Local` (Voyages et guides locaux)
- **Price**: Gratuit (Free) — 0 publicité, 0 achat intégré, 0 collecte de données.
- **Content Rating**: Everyone / Tous publics (PEGI 3).

---

### 2. Short Description (Max 80 characters)
- **Français (FR-CA / FR)**:
  ```text
  Carte vectorielle 100% hors-ligne des 9 602 bancs publics de Montréal.
  ```
  *(71 caractères)*

- **English (EN-US / EN-CA)**:
  ```text
  100% offline native vector map of all 9,602 public benches in Montreal.
  ```
  *(71 characters)*

---

### 3. Full Description (Max 4000 characters)

#### 🇫🇷 Français (Description Complète)
```text
Bancs de Montréal est une application cartographique locale et minimaliste dédiée à la flânerie urbaine, à la détente et à la découverte de l'espace public montréalais.

Conçue selon les principes du design suisse, elle répertorie avec précision les 9 602 bancs publics répartis sur l'ensemble de l'île de Montréal, 100 % hors-ligne, sans connexion Internet requise.

🏛️ CARACTÉRISTIQUES PRINCIPALES :
• 9 602 bancs géolocalisés : Chaque banc est identifié avec son nom de rue ou son parc municipal (ex. Rue Wellington, Rue du Centre, Boulevard Saint-Laurent, Parc La Fontaine).
• 100 % hors-ligne & autonome : Aucune tuile à télécharger, aucun appel API externe. L'ensemble de la carte vectorielle (rues, parcs, arrondissements et bancs) est embarqué et se charge en moins de 100 millisecondes.
• Moteur vectoriel natif fluide : Zoom au doigt précis au point focal, gestes d'inertie naturels et affichage à 60/120 FPS optimisé pour préserver la batterie de votre téléphone.
• Boussole & Localisation GPS : Visualisez instantanément votre position et l'orientation de votre regard grâce au capteur de rotation intégré.
• Découverte aléatoire : Touchez le bouton aléatoire pour vous laisser surprendre et découvrir un banc méconnu à travers les ruelles et parcs de la métropole.
• Itinéraires pédestres : Lancez en un clic le guidage à pied vers le banc sélectionné via votre application de navigation préférée.
• Partage élégant : Partagez les coordonnées géographiques précises, le nom de la rue et les caractéristiques techniques du banc (matériau, dossier, assises) avec vos proches.

🔒 RESPECT TOTAL DE VOTRE VIE PRIVÉE :
• Zéro traqueur, zéro publicité, zéro script d'analyse.
• Zéro transmission réseau : vos coordonnées GPS sont traitées uniquement dans la mémoire vive de votre appareil pour centrer la carte et ne quittent jamais votre téléphone.
• Aucune création de compte nécessaire.

📊 DONNÉES ET CRÉDITS :
Données ouvertes fournies par la Ville de Montréal (Données Ouvertes Montréal) sous licence Creative Commons CC-BY 4.0.
```

---

#### 🇬🇧 English (Full Description)
```text
Montreal Benches (BenchMap) is a minimalist, local-first vector map app created for urban flâneurs, walkers, readers, and anyone seeking a quiet spot in Montreal.

Built with a clean Swiss aesthetic, it charts all 9,602 public benches across the island of Montreal — 100% offline with zero Internet required.

🏛️ KEY FEATURES:
• 9,602 mapped benches: Every bench is matched with its real street name or municipal park (e.g. Rue Wellington, Rue du Centre, Saint-Laurent Blvd, Parc La Fontaine).
• 100% offline & local-first: No tiles to download, no external map API calls. The entire vector dataset (streets, parks, borough boundaries, and benches) loads in under 100ms.
• Ultra-fast native vector engine: Multi-touch pinch-to-zoom anchored on touch focal point, smooth inertial panning, and 60/120 FPS rendering optimized for battery efficiency.
• Real-time compass & GPS pin: See your live location and heading direction driven by the device rotation vector sensor.
• Random bench discovery: Tap the random shuffle button to explore an unexpected public bench across the city's parks and quiet streets.
• Walking navigation: One-tap navigation to open walking directions to any bench in your preferred navigation app.
• Clean sharing: Easily share coordinates, street names, and bench details (material, backrest, seating capacity) with friends.

🔒 100% PRIVACY GUARANTEE:
• Zero tracking, zero ads, zero analytics SDKs.
• Zero network usage: GPS coordinates are processed strictly in transient device RAM and never leave your phone.
• No account or registration required.

📊 DATA & CREDITS:
Open data provided by the City of Montreal (Données Ouvertes Montréal) under the Creative Commons CC-BY 4.0 license.
```

---

### 4. Graphic Assets Manifest
All store assets are pre-rendered and available in `/sdcard/Download/store_assets/`:

| File | Resolution | Requirements / Notes |
| :--- | :--- | :--- |
| `store_icon_512.png` | 512 × 512 px | 32-bit PNG, no alpha on border, Swiss bench pin emblem |
| `store_feature_graphic_1024x500.png` | 1024 × 500 px | JPEG or 24-bit PNG (no alpha), Montreal street grid + Swiss typography |
| `screenshot_1_overview.png` | 1080 × 2400 px | 20:9 phone screenshot showing full island bench constellation |
| `screenshot_2_detail.png` | 1080 × 2400 px | 20:9 phone screenshot showing street name detail card & navigation |

---

### 5. Google Play Data Safety Declarations

When completing the Google Play Console **Data Safety** questionnaire:

1. **Does your app collect or share any user data?**
   👉 Select **No**.
   *(Although the app accesses GPS for on-screen user pin display, Google Play classifies ephemeral on-device location that is not transmitted or logged as "Ephemeral processing", which does NOT require collection disclosure if it doesn't leave the device).*

2. **Location Permission Declaration (`ACCESS_FINE_LOCATION`)**:
   - Purpose: **App functionality** (Showing the user's current position and heading direction relative to nearby benches).
   - Is this data transmitted off the device? **No**.
   - Is this data stored on disk? **No**.

3. **Privacy Policy**:
   - Hosted URL required: Use the provided `privacy_policy.html` hosted on GitHub Pages or any static site.
   - Example URL: `https://<your-username>.github.io/benchmap/privacy_policy.html`
