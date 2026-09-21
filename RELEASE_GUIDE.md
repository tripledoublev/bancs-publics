# 🚀 Guide de Déploiement & Publication / Full Release Guide
## Bancs de Montréal (BenchMap) v0.1.0

Ce guide détaille toutes les étapes concrètes pour publier **BenchMap** sur le **Google Play Store**, ainsi que sur les canaux alternatifs direct (GitHub Releases, F-Droid).

---

### 📦 1. Vos Fichiers Prêts à l'Emploi

Tous les fichiers de production ont été compilés, signés et déposés dans votre dossier de téléchargement Android (`/sdcard/Download/`) :

| Fichier | Emplacement | Utilité |
| :--- | :--- | :--- |
| **Android App Bundle (.aab)** | `/sdcard/Download/BenchMap-v0.1.0-release.aab` *(6.1 Mo)* | **Le fichier officiel obligatoire** à téléverser sur la Google Play Console. |
| **APK Release Signé** | `/sdcard/Download/BenchMap-v0.1.0-release.apk` *(12 Mo)* | L'application complète prête pour l'installation manuelle ou GitHub Release. |
| **APK Direct Simple** | `/sdcard/Download/BenchMap-Montreal.apk` *(12 Mo)* | Copie miroir pour partage direct rapide. |
| **Keystore de Signature** | `/sdcard/Download/benchmap-release.jks` | **Fichier ultra-critique** : la clé cryptographique de production pour signer les futures versions. |
| **Assets Graphiques du Store** | `/sdcard/Download/store_assets/` | Icône 512x512, Bannière 1024x500, Captures d'écran 1080x2400. |
| **Politique de Confidentialité** | `/sdcard/Download/store_assets/privacy_policy.html` | Page HTML conforme RGPD / Google Play à héberger. |

> ⚠️ **IMPORTANT : Sauvegarde de la Clé de Production (`benchmap-release.jks`)**
> - **Alias** : `benchmap`
> - **Mot de passe** : `benchmap2026mtl`
> - Conservez une copie de `benchmap-release.jks` et de ce mot de passe dans un endroit sûr (ex: coffre-fort de mots de passe, disque dur externe). Si vous perdez cette clé, Google n'autorisera aucune mise à jour de l'application sur le Play Store !

---

### 🌐 2. Héberger la Politique de Confidentialité (Gratuit en 2 minutes)

Google Play exige une URL publique vers la politique de confidentialité car l'application demande l'accès à la localisation (`ACCESS_FINE_LOCATION`).

#### Méthode GitHub Pages (Recommandée & Gratuite à vie) :
1. Créez un dépôt GitHub public (ex: `https://github.com/<votre-pseudo>/benchmap`).
2. Déposez-y le fichier `privacy_policy.html` (situé dans `/sdcard/Download/store_assets/privacy_policy.html`).
3. Allez dans **Settings** du dépôt > **Pages** > sélectionnez la branche `main` / dossier `/root` > cliquez sur **Save**.
4. Votre URL sera active :
   `https://<votre-pseudo>.github.io/benchmap/privacy_policy.html`
*(Vous pouvez aussi utiliser un Gist GitHub public ou un site Notion/Netlify si vous préférez).*

---

### 📱 3. Publication sur le Google Play Store (Étape par Étape)

#### Étape 3.1 : Créer le compte Développeur Google Play
1. Rendez-vous sur [Google Play Console](https://play.google.com/console/signup).
2. Connectez-vous avec votre compte Google.
3. Payez les frais uniques d'enregistrement de **25 $ USD** (valable à vie pour un nombre illimité d'applications).
4. Complétez la vérification d'identité exigée par Google.

#### Étape 3.2 : Créer l'application
1. Dans la Play Console, cliquez sur **Créer une application** (Create app).
2. **Nom de l'application** : `Bancs de Montréal – BenchMap`
3. **Langue par défaut** : Français (Canada) ou Français.
4. **Type d'application** : Application (App).
5. **Gratuite ou payante** : Gratuite (Free).
6. Acceptez les déclarations légales et cliquez sur **Créer l'application**.

#### Étape 3.3 : Configurer la Fiche de la boutique (Main Store Listing)
Allez dans le menu de gauche > **Présence sur le Play Store** > **Fiche principale du magasin** :
- **Titre** : `Bancs de Montréal – BenchMap`
- **Description courte** (copier depuis `PLAY_STORE_METADATA.md`) :
  `Carte vectorielle 100% hors-ligne des 9 602 bancs publics de Montréal.`
- **Description complète** : Collez le texte complet en français préparé dans `PLAY_STORE_METADATA.md`.
- **Icône de l'application** : Téléversez `/sdcard/Download/store_assets/store_icon_512.png`.
- **Graphique des fonctionnalités** : Téléversez `/sdcard/Download/store_assets/store_feature_graphic_1024x500.png`.
- **Captures d'écran de téléphone** : Téléversez `screenshot_1_overview.png` et `screenshot_2_detail.png`.

*(Optionnel : ajoutez une traduction en Anglais avec les textes fournis dans `PLAY_STORE_METADATA.md`).*

#### Étape 3.4 : Remplir le Contenu de l'application (App Content)
Dans le menu de gauche > **Contenu de l'application** (Policy & Programs) :
1. **Politique de confidentialité** : Renseignez l'URL de votre `privacy_policy.html`.
2. **Accès aux applications** : Cochez "Toutes les fonctionnalités sont disponibles sans restriction d'accès".
3. **Annonces publicitaires** : Cochez "Non, mon application ne contient aucune annonce".
4. **Classification du contenu** : Répondez au questionnaire (Tous âges / PEGI 3 / 0 violence / 0 contenu adulte).
5. **Public cible** : 18 ans et plus ou Tout public.
6. **Sécurité des données (Data Safety)** :
   - Cochez **"Non"** à la question "Votre application collecte-t-elle ou partage-t-elle des données utilisateur ?".
   - *Note d'explication : la localisation GPS est traitée exclusivement de manière éphémère dans la RAM de l'appareil et n'est ni stockée, ni transmise.*
7. **Autorisations d'accès aux fonctionnalités sensibles** :
   - Déclarez l'usage de `ACCESS_FINE_LOCATION` pour le centrage de la carte et l'orientation sur le banc le plus proche.

#### Étape 3.5 : Téléverser le Bundle et Lancer la Production
1. Dans le menu de gauche > **Production** (ou **Test interne**).
2. Cliquez sur **Créer une nouvelle version** (Create new release).
3. Sign In avec Play App Signing : Google vous propose d'utiliser la clé générée par Google ou d'utiliser votre propre clé. Choisissez l'option par défaut (Play App Signing utilise notre clé exportée).
4. Glissez-déposez le fichier :
   `/sdcard/Download/BenchMap-v0.1.0-release.aab`
5. **Nom de la version** : `0.1.0 (2)`
6. **Notes de version (Release notes)** :
   ```text
   Version inaugurale v0.1.0 de Bancs de Montréal :
   - 9 602 bancs géolocalisés avec association des noms de rues.
   - Moteur vectoriel natif 100% hors-ligne ultra-rapide.
   - Boussole en temps réel et exploration aléatoire.
   ```
7. Cliquez sur **Vérifier la version**, puis **Enregistrer et déployer**.

---

### 🔍 4. La Règle Google Play des "20 Testeurs" (Pour comptes personnels récents)

> [!NOTE]
> Depuis novembre 2023, Google impose aux **nouveaux comptes de développeurs personnels** de faire tester l'application pendant **14 jours consécutifs par au moins 20 testeurs** en piste fermée (Closed Testing) avant de pouvoir demander l'accès à la Production publique.

#### Comment gérer cela facilement :
1. **Option A : Le réseau d'amis / communauté** :
   Créez un Google Group (ex: `banc-montreal-testers@googlegroups.com`), invitez 20 amis/collègues/amateurs de plein air à rejoindre le groupe, et ajoutez ce groupe dans la piste de Test Fermé de la Google Play Console. Dès qu'ils ont installé l'application via le lien du Play Store pendant 14 jours, vous débloquez le bouton "Demander l'accès à la production".
2. **Option B : Utiliser un compte développeur Organisation / Entreprise** :
   Les comptes développeur créés au nom d'une organisation (NEQ au Québec / Corporation) ne sont **pas** soumis à cette contrainte des 20 testeurs et peuvent publier directement en production.
3. **Option C : Sous-reddit / Communautés d'entraide de testeurs** :
   Des communautés dédiées comme `r/AndroidClosedTesting` permettent d'échanger des installations mutuelles de testeurs en moins de 48 heures.

---

### ⚡ 5. Alternatives Sans Friction & Immédiates (0 $ / 0 Attente)

Si vous souhaitez partager immédiatement l'application avec vos amis et utilisateurs à Montréal sans attendre la validation de Google :

#### 1. Partage Direct de l'APK (Instantané)
Envoyez directement `/sdcard/Download/BenchMap-v0.1.0-release.apk` par AirDrop/Nearby Share, Telegram, WhatsApp ou Google Drive.
L'APK est signé officiellement et s'installe en un clic.

#### 2. Publication sur GitHub Releases
1. Créez une release `v0.1.0` sur votre dépôt GitHub.
2. Attachez-y `BenchMap-v0.1.0-release.apk`.
3. N'importe quel utilisateur d'Android (ou via l'application open-source **Obtainium**) bénéficiera des mises à jour automatiques directes dès que vous publiez une nouvelle version.

#### 3. Soumission sur F-Droid (Boutique Open-Source)
BenchMap est le candidat idéal pour F-Droid :
- 100% open-source, 0 traqueur, 0 binaire propriétaire, 0 API propriétaire.
- Gratuit et sans contrainte de 20 testeurs.
- Référencé dans la plus grande bibliothèque d'applications libres au monde.

---

### 🛠️ 6. Commandes Rapides pour les Futures Mises à Jour

Pour compiler les prochaines versions (ex: v0.2.0) :
```bash
cd /root/apps/BenchMap

# 1. Mettre à jour versionCode et versionName dans app/build.gradle

# 2. Générer le bundle Play Store officiel (.aab)
./gradlew bundleRelease

# 3. Générer l'APK signé autonome (.apk)
./gradlew assembleRelease

# 4. Copier les artifacts vers /sdcard/Download/
cp app/build/outputs/bundle/release/app-release.aab /sdcard/Download/BenchMap-v0.2.0-release.aab
cp app/build/outputs/apk/release/app-release.apk /sdcard/Download/BenchMap-v0.2.0-release.apk
```
