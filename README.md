# DocuSnap AI: Bill & Rx Finder
> **Dual-Ecosystem Product**: Native Android Production App + Cross-Platform Progressive Web App (PWA) + Firebase Cloud Sync & Auth + Production ASO & SEO Pack.

---

## 🌟 Executive Overview
**DocuSnap AI** is an intelligent document vault engineered to solve the everyday chaos of lost medical prescriptions, pharmacy slips, electricity/gas utility bills, and grocery receipts.

Rather than forcing users to manually open a scanner app, **DocuSnap AI** introduces **Zero-Click Media Ingestion**:
- Android `ContentObserver` monitors gallery additions (`MediaStore.Images.Media.EXTERNAL_CONTENT_URI`).
- Automatic resolution and aspect ratio filtering eliminates stickers, memes, and icons.
- SHA-256 deduplication ensures zero redundant processing.
- Bundled **Google ML Kit Text Recognition** extracts raw OCR text 100% on-device with zero latency and zero cloud cost.
- **Google Generative AI Client SDK (Gemini Flash)** performs deep semantic classification and entity extraction.
- **SQLite FTS5 (Room Database)** indexes all text for instant, sub-second offline search.
- **Permanent Internal Storage & Self-Healing Cache**: Documents and thumbnails are cached locally in internal app storage, permanently preventing permission expiration or disappearing thumbnails.
- **Live Camera Document Scanner**: Direct camera intake via `FileProvider` with automatic high-res capture and OCR.
- **Scalable Architecture**: FTS5 query token ranking, memory-efficient WebP thumbnails, and 50-item search chunks allowing seamless performance across 15,000+ documents.
- **In-App Google Play Subscriptions**: Tiered storage packages (Free Starter 500, Silver Vault 2,000, Gold Vault 5,000, Platinum Master 10,000, and Infinity Diamond 15,000).
- **International Multi-Language Support**: Full native UI and localized Gemini reasoning in 8 languages (English, Urdu, Arabic, Spanish, French, German, Chinese, and Hindi).
- **Dual-Ecosystem Mesh**: Firebase Firestore and Storage sync documents in real time with the **Cross-Platform Progressive Web App (PWA)** on iPhone, iPad, Mac, and Windows.

---

## 📱 Ecosystem Architecture

```
                               ┌──────────────────────────────────────────────┐
                               │           Android Gallery Photo / Snap       │
                               └──────────────────────┬───────────────────────┘
                                                      │
                                                      ▼
                                       ┌─────────────────────────────┐
                                       │   MediaObserverService      │
                                       │    (ContentObserver)        │
                                       └──────────────┬──────────────┘
                                                      │
                                                      ▼
                                       ┌─────────────────────────────┐
                                       │    MediaIngestionWorker     │
                                       │ (WorkManager Coroutine)     │
                                       └───────┬──────────────┬──────┘
                                               │              │
                   ┌───────────────────────────┘              └───────────────────────────┐
                   ▼                                                                      ▼
        ┌───────────────────────┐                                              ┌──────────────────────┐
        │  Google ML Kit OCR    │                                              │  Resolution & Hash   │
        │  (100% On-Device)     │                                              │  SHA-256 Dedupe      │
        └──────────┬────────────┘                                              └──────────────────────┘
                   │
                   ▼
        ┌───────────────────────┐
        │ Gemini Flash Reasoning│ ───► Structured Entities: Doctor, Medicines, PKR Amounts, Vendor
        └──────────┬────────────┘
                   │
                   ▼
        ┌───────────────────────┐                                              ┌──────────────────────┐
        │  Room DB (SQLite FTS5)│ ◄─────────────────────────────────────────── │  Conversational UI   │
        │  Offline Search Index │                                              │  "Ask Anything" Box  │
        └──────────┬────────────┘                                              └──────────────────────┘
                   │
                   ▼
        ┌───────────────────────┐
        │ Firebase Cloud Sync   │ ◄───► Real-Time Firestore & Storage
        └──────────┬────────────┘
                   │
                   ▼
        ┌────────────────────────────────────────────────────────┐
        │  Cross-Platform PWA (iOS Safari / Desktop / Mac / Win) │
        │  - HTML5 Canvas Downscaling                            │
        │  - Native iOS "Add to Home Screen" Bottom Sheet Modal   │
        │  - Zero-Cost Static Hosting on GitHub Pages            │
        └────────────────────────────────────────────────────────┘
```

---

## 📁 Repository Structure

```
DocuSnapAI/
├── android/                             # Native Android Production Application
│   ├── build.gradle.kts                # Project build script
│   ├── settings.gradle.kts             # Gradle settings
│   ├── gradle.properties               # JVM & AndroidX properties
│   └── app/
│       ├── build.gradle.kts            # Compose, Room FTS5, ML Kit, Gemini, Firebase BOM
│       ├── proguard-rules.pro          # ProGuard rules for Room, ML Kit & Gemini
│       └── src/main/
│           ├── AndroidManifest.xml     # Permissions (READ_MEDIA_IMAGES, FOREGROUND_SERVICE)
│           ├── java/com/docusnap/ai/
│           │   ├── DocuSnapApplication.kt
│           │   ├── ai/
│           │   │   └── GeminiReasoningEngine.kt     # Structured JSON & Conversational Search
│           │   ├── data/
│           │   │   ├── local/
│           │   │   │   ├── AppDatabase.kt          # Room Database with FTS virtual table
│           │   │   │   └── DocumentDao.kt          # FTS5 MATCH queries & CRUD
│           │   │   └── model/
│           │   │       ├── DocumentEntity.kt       # Primary entity schema
│           │   │       ├── DocumentFtsEntity.kt    # Room FTS4/5 entity
│           │   │       └── DocumentDto.kt          # DTOs & JSON serialization
│           │   ├── ocr/
│           │   │   └── OcrEngine.kt                # Bundled Google ML Kit Text Recognition
│           │   ├── service/
│           │   │   ├── BootReceiver.kt             # Restarts observer service on device boot
│           │   │   ├── MediaIngestionWorker.kt     # CoroutineWorker with aspect ratio filter
│           │   │   └── MediaObserverService.kt     # Foreground Service + ContentObserver
│           │   ├── sync/
│           │   │   └── FirebaseSyncRepository.kt   # Auth, Firestore & Storage sync
│           │   └── ui/
│           │       ├── MainActivity.kt             # Compose activity & permissions launcher
│           │       ├── DocuSnapNavHost.kt          # Navigation graph & bottom bar
│           │       ├── DocuSnapViewModel.kt        # StateFlow & conversational orchestrator
│           │       ├── screens/
│           │       │   ├── ConversationalSearchScreen.kt  # "Ask Anything" (Roman Urdu/English)
│           │       │   ├── DocumentInspectorScreen.kt     # Deep-zoomable viewer & copyable tags
│           │       │   ├── SettingsScreen.kt              # On-Device vs Gemini & battery settings
│           │       │   └── SmartVaultScreen.kt            # Categorized grid of documents
│           │       └── theme/
│           │           ├── Color.kt
│           │           ├── Theme.kt
│           │           └── Type.kt
│           └── res/                    # Drawables, colors, strings, extraction rules
│
├── web/                                 # Cross-Platform Progressive Web App (PWA)
│   ├── package.json                    # React 18, Vite, Tailwind CSS, Lucide, Firebase JS
│   ├── vite.config.ts                  # Vite + VitePWA configuration
│   ├── tailwind.config.js              # Theme colors matching Android Material 3
│   ├── tsconfig.json                   # TypeScript bundler configuration
│   ├── index.html                      # PWA meta tags & iOS Safari viewport fit
│   ├── public/
│   │   ├── 404.html                    # GitHub Pages SPA routing fallback script
│   │   ├── manifest.json               # Full PWA Web App Manifest
│   │   └── favicon.svg                 # DocuSnap vector brand asset
│   └── src/
│       ├── App.tsx                     # Main container, tabs, and real-time synchronization
│       ├── firebase.ts                 # Firebase Auth, Storage, & Firestore snapshot listeners
│       ├── gemini.ts                   # Gemini Flash multimodal vision & Roman Urdu reasoning
│       ├── types.ts                    # TypeScript models matching Room Entity
│       └── components/
│           ├── ConversationalSearch.tsx # "Ask Anything" search with animated AI answer
│           ├── SmartVault.tsx           # Categorized grid with filter chips & amounts
│           ├── DocumentInspectorModal.tsx # Deep-zoom image inspector with one-click copy
│           ├── UploadModal.tsx          # Client-side HTML5 Canvas downscaler & camera intake
│           ├── IosInstallPrompt.tsx     # Native iOS Safari "Add to Home Screen" bottom sheet
│           └── Navbar.tsx               # Brand header & navigation controls
│
├── firebase/                            # Cloud Backend Contracts & Rules
│   ├── firebase.json                   # Hosting & Firestore configuration
│   ├── firestore.rules                 # Security rules: users/{userId}/documents/{docId}
│   ├── firestore.indexes.json          # Composite query indexes
│   └── storage.rules                   # Rules for /images/ and /thumbs/
│
├── play_store_assets/                   # Google Play Store Production ASO
│   ├── title.txt                       # Title (29/30 characters)
│   ├── short_description.txt           # Short Description (79/80 characters)
│   ├── full_description.txt            # Keyword-rich 4000-character description
│   ├── listing_metadata.json           # Categories, ratings, contact info
│   └── release_notes.txt               # Release notes for v1.0.0
│
├── web_seo/                             # Web SEO & Search Engine Optimization Pack
│   ├── index.html                      # Complete OpenGraph, Twitter Card & JSON-LD
│   ├── robots.txt                      # Search engine crawler directives
│   └── sitemap.xml                     # XML Sitemap with daily change frequency
│
└── README.md                            # Comprehensive product documentation
```

---

## 🚀 Key Architectural Details

### 1. Zero-Click Background Ingestion & Filtering
In `android/.../service/MediaObserverService.kt`:
- Registers a `ContentObserver` on `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`.
- On change event, queries newly added media and triggers `MediaIngestionWorker` via `ExistingWorkPolicy.APPEND_OR_REPLACE`.
- `MediaIngestionWorker` decodes image bounds first without loading full bitmaps into RAM:
  - If `width < 380` or `height < 380`, discards (small stickers, low-res thumbnails).
  - If `aspectRatio > 4.5`, discards (thin banners, dividers).
  - Calculates SHA-256 hash to prevent duplicate entries.
- Runs **Google ML Kit Text Recognition** bundled on-device. If less than 15 characters of text are detected, the item is ignored (e.g. selfies or scenery photos).

### 2. Gemini Flash Deep Semantic Reasoning Schema
When Gemini Deep Reasoning is active, extracted text is analyzed using the following structured schema:
```json
{
  "document_type": "Medical Prescription | Expense Receipt | Utility Bill | Bank Slip | General Document",
  "title": "Short descriptive title (e.g., Blood Test Report - Chughtai Lab)",
  "summary": "1-2 sentence conversational summary",
  "key_entities": {
    "doctor_name": "string or null",
    "medicines": ["string"],
    "total_amount": 1250.00,
    "vendor_or_hospital": "string or null",
    "date": "YYYY-MM-DD"
  },
  "searchable_tags": ["tag1", "tag2"]
}
```

### 3. "Ask Anything" Conversational Search Flow
1. User enters natural language queries:
   - *"Doctor ne pet dard k liay konsi goli likhi thi?"*
   - *"Pichle month ka bijli ka bill kitna tha?"*
2. Search query is tokenized and executed against Room's **SQLite FTS5** virtual table (`documents_fts MATCH :query`).
3. Matching candidate documents are passed to **Gemini Flash**.
4. The model responds directly with the answer (e.g., *"Doctor Tariq Mahmood ne pet dard k liay Tab Risek 40mg nashte se pehle likhi thi."*), followed by clickable document cards.

### 4. iOS Safari Native-Feel Integration & Client-Side Canvas Downscaling
- PWA manifest (`display: "standalone"`, `viewport-fit=cover`, custom dark theme colors).
- Detects iOS Safari and displays `IosInstallPrompt`: step-by-step bottom sheet guiding the user to tap **Share** -> **Add to Home Screen**.
- Multi-file & camera intake uses client-side **HTML5 Canvas downscaling** (max dimension 1600px, 82% JPEG quality) before uploading to Firebase Cloud Storage, reducing upload times by up to 85%.

---

## 🛠️ Build & Deployment Instructions

### 1. Android Application Build
1. Open the `android/` directory in Android Studio.
2. Ensure you have Android SDK 34 and JDK 17 configured.
3. Configure your `GEMINI_API_KEY` in `android/app/build.gradle.kts` or `local.properties`.
4. Place your `google-services.json` from the Firebase console in `android/app/`.
5. Run:
   ```bash
   ./gradlew assembleDebug
   ```

### 2. Web PWA Build & GitHub Pages Deployment
1. Navigate to the `web/` directory:
   ```bash
   cd web
   npm install
   npm run build
   ```
2. The production bundle is output to `web/dist/`.
3. To deploy to GitHub Pages:
   ```bash
   # Push dist directory to gh-pages branch
   git subtree push --prefix web/dist origin gh-pages
   ```
   *Note: `public/404.html` ensures all deep SPA routes redirect seamlessly without 404 errors on GitHub Pages.*

### 3. Firebase Deployment
From the project root:
```bash
firebase deploy --only firestore:rules,storage:rules
```

---

## 📄 License
DocuSnap AI is released under the **MIT License**.

