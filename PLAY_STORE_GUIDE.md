# Guide de Publication Google Play Store & Configuration GitHub 🚀

Ce guide récapitule toutes les étapes et réponses exactes pour publier **BenchMap** sur le **Google Play Console**, avec l'ensemble des éléments hébergés directement et gratuitement sur **GitHub**.

---

## 1. Activer GitHub Pages en 3 clics (Hébergement du site & de la politique)

Toutes les pages web nécessaires (`index.html`, `privacy.html`, `terms.html`, `support.html`, captures et icône) sont prêtes dans le dossier [`docs/`](docs/).

1. Rendez-vous sur votre dépôt GitHub : **`github.com/tripledoublev/bancs-publics`**.
2. Cliquez sur l'onglet **Settings** (Paramètres du dépôt).
3. Dans le menu de gauche, sous la section *Code and automation*, cliquez sur **Pages**.
4. Sous **Build and deployment** :
   - **Source** : Sélectionnez `Deploy from a branch`.
   - **Branch** : Choisissez `main` et dans le sélecteur de dossier à droite, sélectionnez **`/docs`**.
5. Cliquez sur **Save**.

En 60 secondes, votre site et vos documents légaux sont accessibles publiquement :
- 🌐 **Site web officiel** : `https://tripledoublev.github.io/bancs-publics/`
- 🛡️ **Politique de confidentialité (Obligatoire Play Store)** : `https://tripledoublev.github.io/bancs-publics/privacy.html`
- 📜 **Conditions d'utilisation** : `https://tripledoublev.github.io/bancs-publics/terms.html`
- 💬 **Support & Contact** : `https://tripledoublev.github.io/bancs-publics/support.html`

---

## 2. Création de l'application dans Google Play Console

1. Rendez-vous sur [play.google.com/console](https://play.google.com/console).
2. Cliquez sur **Créer une application** (*Create app*).
3. Remplissez les champs initiaux :
   - **Nom de l'application** : `BenchMap — Bancs de Montréal`
   - **Langue par défaut** : `Français (Canada) - fr-CA`
   - **Type d'application** : `Application`
   - **Gratuite ou payante** : `Gratuite`
   - Cochez les déclarations de conformité aux règles du programme et aux lois américaines sur l'exportation.
4. Cliquez sur **Créer l'application**.

---

## 3. Configuration du contenu de l'application (Tâches obligatoires)

Dans le tableau de bord, descendez à la section **Configurer votre application** (*Set up your app*) :

### 🛡️ A. Règles de confidentialité (Privacy Policy)
- Collez l'URL GitHub Pages :
  ```
  https://tripledoublev.github.io/bancs-publics/privacy.html
  ```

### 🔑 B. Accès aux applications (App Access)
- Sélectionnez : **Toutes les fonctionnalités sont disponibles sans restriction**.
  *(BenchMap ne nécessite aucun identifiant, mot de passe ou compte pour fonctionner).*

### 📢 C. Annonces (Ads)
- Sélectionnez : **Non, mon application ne contient pas d'annonces**.

### 🎂 D. Public cible et contenu (Target Audience)
- Sélectionnez les tranches d'âge : **13-15 ans, 16-17 ans, 18 ans et plus** (ou simplement 18+).
- *Attrait involontaire pour les enfants* : Sélectionnez **Non**.

### 📰 E. Applications d'actualités (News Apps)
- Sélectionnez : **Non**.

### 🦠 F. Suivi des contacts COVID-19 / État
- Sélectionnez : **Mon application n'est pas une application de suivi des contacts COVID-19...**

### 🚗 G. Sécurité routière et fonctionnalités de conduite
- Sélectionnez : **Non**.

### 💼 H. Services financiers / Gouvernement
- Sélectionnez : **Non**.

---

## 4. Section « Sécurité des données » (Data Safety) — CRUCIAL

C'est ici que Google Play valide l'absence de collecte de données. Grâce à l'architecture 100 % hors-ligne de BenchMap :

1. **Collecte et partage de données** :
   - *« Votre application collecte-t-elle ou partage-t-elle des types de données utilisateur obligatoires ou facultatifs ? »*
   - 👉 Sélectionnez : **NON**.
2. Cliquez sur **Suivant** et confirmez le récapitulatif :
   - Aucune donnée collectée.
   - Aucune donnée partagée avec des tiers.
   - Aucune pratique de chiffrement en transit requise (car aucune transmission réseau n'existe).
3. Enregistrez la section.

---

## 5. Fiche Google Play Store (Main Store Listing)

Dans le menu de gauche, rendez-vous dans **Présence sur le Play Store** > **Fiche principale du Play Store**.

### Textes (prêts dans [`store_assets/listing_fr.txt`](store_assets/listing_fr.txt)) :
- **Nom de l'application** (28 / 30 car.) :
  ```
  BenchMap — Bancs de Montréal
  ```
- **Description courte** (75 / 80 car.) :
  ```
  Atlas vectoriel hors-ligne des 9 602 bancs publics de Montréal. 100% privé.
  ```
- **Description complète** :
  Copiez-collez le contenu de [`store_assets/listing_fr.txt`](store_assets/listing_fr.txt).

### Éléments graphiques (générés et prêts dans [`store_assets/`](store_assets/)) :
1. **Icône de l'application (512x512 PNG)** :
   - Téléversez : [`store_assets/icon_512.png`](store_assets/icon_512.png)
2. **Graphique de fonctionnalité (1024x500 PNG)** :
   - Téléversez : [`store_assets/feature_graphic_1024x500.png`](store_assets/feature_graphic_1024x500.png)
3. **Captures d'écran pour téléphone** :
   - Téléversez au moins 3 captures d'écran :
     - [`screenshot_light_mode.png`](screenshot_light_mode.png) *(Mode Clair Nordic Paper avec boussole intégrée)*
     - [`screenshot_dark_mode.png`](screenshot_dark_mode.png) *(Mode Sombre Nocturne OLED avec Parc Angrignon)*
     - [`screenshot_photo_viewer.png`](screenshot_photo_viewer.png) *(Visualiseur photo immersif plein écran)*

---

## 6. Coordonnées de contact du développeur

Dans **Présence sur le Play Store** > **Paramètres du Play Store** :
- **Catégorie d'application** : `Plans et navigation` (ou `Voyages et infos locales`).
- **Balises (Tags)** : `Cartes et navigation`, `Voyages et transports`, `Hors connexion`.
- **Adresse courriel** : `vincentcharlebois@gmail.com`
- **Site web** : `https://tripledoublev.github.io/bancs-publics/`

---

## 7. Téléverser le fichier de production (App Bundle .aab)

Google Play n'accepte plus les fichiers `.apk` pour les nouvelles applications, il exige le format optimisé **Android App Bundle (`.aab`)**.

Le bundle signé de la version **v0.2.3** est déjà compilé et prêt :
- Emplacement sur votre téléphone : **`/sdcard/Download/BenchMap-v0.2.3-release.aab`**
- Emplacement dans le projet : **`app/build/outputs/bundle/release/app-release.aab`**

1. Dans le menu de gauche, rendez-vous dans **Production** > **Créer une version**.
2. Téléversez le fichier `BenchMap-v0.2.3-release.aab`.
3. Nom de version : `0.2.3` (code version 6).
4. Notes de version (Release Notes) :
   ```
   Version 0.2.3 :
   • Intégration de la boussole suisse au bandeau de navigation supérieur avec aiguille dynamique et recentrage instantané au Nord.
   • Moteur de recherche d'adresses civiques montréalaises, rues, parcs et quartiers avec centrage automatique.
   • Ajout de bancs personnalisés (« Mes bancs ») par appui long avec glyphe distinctif en losange (◆).
   • Visualiseur photo plein écran immersif en noir absolu OLED (#000000).
   • Optimisation physique tactile et isolation gestuelle éliminant tout clic intempestif lors du zoom.
   • 100% hors-ligne, zéro serveur, respect absolu de la vie privée.
   ```
5. Cliquez sur **Enregistrer**, puis **Vérifier la version**.
6. Cliquez sur **Lancer le déploiement en production** pour envoyer l'application en examen auprès de Google !
