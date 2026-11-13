package com.docusnap.ai.i18n

object AppStrings {

    fun get(key: String, lang: AppLanguage = AppLanguage.ENGLISH): String {
        val dict = when (lang) {
            AppLanguage.JAPANESE -> JAPANESE_STRINGS
            AppLanguage.KOREAN -> KOREAN_STRINGS
            AppLanguage.CHINESE -> CHINESE_STRINGS
            AppLanguage.SPANISH -> SPANISH_STRINGS
            AppLanguage.FRENCH -> FRENCH_STRINGS
            AppLanguage.GERMAN -> GERMAN_STRINGS
            AppLanguage.ARABIC -> ARABIC_STRINGS
            AppLanguage.HINDI -> HINDI_STRINGS
            AppLanguage.URDU -> URDU_STRINGS
            else -> ENGLISH_STRINGS
        }
        return dict[key] ?: ENGLISH_STRINGS[key] ?: key
    }

    fun getExampleQueries(lang: AppLanguage = AppLanguage.ENGLISH): List<String> {
        return listOf(
            get(EXAMPLE_Q1, lang),
            get(EXAMPLE_Q2, lang),
            get(EXAMPLE_Q3, lang),
            get(EXAMPLE_Q4, lang)
        )
    }

    // Keys
    const val APP_TITLE = "app_title"
    const val APP_SUBTITLE = "app_subtitle"
    const val NAV_ASK_AI = "nav_ask_ai"
    const val NAV_VAULT = "nav_vault"
    const val NAV_SETTINGS = "nav_settings"
    const val NAV_PREMIUM = "nav_premium"

    const val SEARCH_PLACEHOLDER = "search_placeholder"
    const val SEARCH_AI_ANSWER = "search_ai_answer"
    const val SEARCH_NO_DOCS = "search_no_docs"
    const val SEARCH_ASK_BTN = "search_ask_btn"
    const val BTN_PICK_PHOTOS = "btn_pick_photos"
    const val BTN_CAMERA_SCAN = "btn_camera_scan"
    const val BTN_SCAN_GALLERY = "btn_scan_gallery"

    const val EXAMPLE_Q1 = "example_q1"
    const val EXAMPLE_Q2 = "example_q2"
    const val EXAMPLE_Q3 = "example_q3"
    const val EXAMPLE_Q4 = "example_q4"

    const val VAULT_TITLE = "vault_title"
    const val VAULT_COUNT_SUBTITLE = "vault_count_subtitle"
    const val VAULT_EMPTY = "vault_empty"
    const val VAULT_EMPTY_DESC = "vault_empty_desc"
    const val CAT_ALL = "cat_all"
    const val CAT_MEDICAL = "cat_medical"
    const val CAT_RECEIPTS = "cat_receipts"
    const val CAT_UTILITY = "cat_utility"
    const val CAT_BANK = "cat_bank"

    const val ENTITY_DOCTOR = "entity_doctor"
    const val ENTITY_TOTAL_AMOUNT = "entity_total_amount"
    const val ENTITY_HOSPITAL = "entity_hospital"
    const val ENTITY_MEDICINES = "entity_medicines"
    const val ENTITY_RAW_OCR = "entity_raw_ocr"
    const val TAP_TO_COPY = "tap_to_copy"

    const val SETTINGS_TITLE = "settings_title"
    const val SETTINGS_AI_ENGINE = "settings_ai_engine"
    const val SETTINGS_DEEP_REASONING = "settings_deep_reasoning"
    const val SETTINGS_ON_DEVICE_ONLY = "settings_on_device_only"
    const val SETTINGS_BATTERY = "settings_battery"
    const val SETTINGS_LANGUAGE = "settings_language"
    const val SETTINGS_SUBSCRIPTION = "settings_subscription"
    const val LANGUAGE_TITLE = "language_title"
    const val AUTO_SCREENSHOT_BANNER = "auto_screenshot_banner"

    const val PREMIUM_TITLE = "premium_title"
    const val PREMIUM_SUBTITLE = "premium_subtitle"
    const val PREMIUM_USAGE = "premium_usage"
    const val PREMIUM_SUBSCRIBE_BTN = "premium_subscribe_btn"
    const val PREMIUM_ACTIVE = "premium_active"

    private val ENGLISH_STRINGS = mapOf(
        APP_TITLE to "DocuSnap AI",
        APP_SUBTITLE to "Bill & Rx Finder",
        NAV_ASK_AI to "Ask AI",
        NAV_VAULT to "Smart Vault",
        NAV_SETTINGS to "Settings",
        NAV_PREMIUM to "Upgrade",
        SEARCH_PLACEHOLDER to "Search in any language: 'Last prescription' or '領収書'",
        SEARCH_AI_ANSWER to "AI Answer",
        SEARCH_NO_DOCS to "No matching document found in your vault. Capture a photo, take a screenshot, or import documents.",
        SEARCH_ASK_BTN to "Ask AI",
        BTN_PICK_PHOTOS to "Pick Photos",
        BTN_CAMERA_SCAN to "Camera Scan",
        BTN_SCAN_GALLERY to "Scan Gallery",
        EXAMPLE_Q1 to "What medication was prescribed last month?",
        EXAMPLE_Q2 to "Show my latest electricity or utility bill total",
        EXAMPLE_Q3 to "Find recent pharmacy or supermarket receipt",
        EXAMPLE_Q4 to "Summarize my latest bank transfer slip",
        VAULT_TITLE to "Smart Vault",
        VAULT_COUNT_SUBTITLE to "documents auto-indexed offline",
        VAULT_EMPTY to "No documents found",
        VAULT_EMPTY_DESC to "Snap a bill, prescription, or screenshot to auto-index",
        CAT_ALL to "All",
        CAT_MEDICAL to "Medical",
        CAT_RECEIPTS to "Receipts",
        CAT_UTILITY to "Utility",
        CAT_BANK to "Bank",
        ENTITY_DOCTOR to "Doctor",
        ENTITY_TOTAL_AMOUNT to "Total Amount",
        ENTITY_HOSPITAL to "Hospital / Vendor",
        ENTITY_MEDICINES to "Prescribed Medicines",
        ENTITY_RAW_OCR to "Raw On-Device OCR Text",
        TAP_TO_COPY to "Tap to Copy",
        SETTINGS_TITLE to "Settings & Privacy",
        SETTINGS_AI_ENGINE to "AI ENGINE & PRIVACY",
        SETTINGS_DEEP_REASONING to "Global Multilingual AI Reasoning (5x)",
        SETTINGS_ON_DEVICE_ONLY to "On-Device OCR Only",
        SETTINGS_BATTERY to "Battery Optimization",
        SETTINGS_LANGUAGE to "App Language",
        SETTINGS_SUBSCRIPTION to "Subscription & Prepaid Balance",
        PREMIUM_TITLE to "DocuSnap Premium & Wallet",
        PREMIUM_SUBTITLE to "Scale your vault & load balance for Global Multilingual AI Search",
        PREMIUM_USAGE to "Documents Used",
        PREMIUM_SUBSCRIBE_BTN to "Subscribe via Google Play",
        PREMIUM_ACTIVE to "Active Plan",
        LANGUAGE_TITLE to "Select Language",
        AUTO_SCREENSHOT_BANNER to "Enable Auto-Screenshot Vault: Tap to allow full photo & screenshot access"
    )

    private val JAPANESE_STRINGS = mapOf(
        APP_TITLE to "DocuSnap AI",
        APP_SUBTITLE to "領収書・処方箋スマート検索",
        NAV_ASK_AI to "AI検索",
        NAV_VAULT to "スマート保管庫",
        NAV_SETTINGS to "設定",
        NAV_PREMIUM to "プラン",
        SEARCH_PLACEHOLDER to "日本語や多言語で検索: 「先月の処方箋」「電気代」",
        SEARCH_AI_ANSWER to "AI 回答",
        SEARCH_NO_DOCS to "一致する書類が見つかりませんでした。カメラで撮影するか画像を追加してください。",
        SEARCH_ASK_BTN to "検索",
        BTN_PICK_PHOTOS to "写真を選択",
        BTN_CAMERA_SCAN to "カメラ撮影",
        BTN_SCAN_GALLERY to "端末をスキャン",
        EXAMPLE_Q1 to "前回病院で処方された薬は何ですか？",
        EXAMPLE_Q2 to "先月の電気料金の請求額はいくらでしたか？",
        EXAMPLE_Q3 to "最近のスーパー・コンビニの領収書を探す",
        EXAMPLE_Q4 to "最新の銀行振込明細を要約して",
        VAULT_TITLE to "スマート保管庫",
        VAULT_COUNT_SUBTITLE to "件の書類を自動保存済み",
        VAULT_EMPTY to "書類がありません",
        VAULT_EMPTY_DESC to "領収書、処方箋、スクリーンショットを自動保存します",
        CAT_ALL to "すべて",
        CAT_MEDICAL to "医療・処方箋",
        CAT_RECEIPTS to "領収書",
        CAT_UTILITY to "公共料金",
        CAT_BANK to "銀行・振込",
        ENTITY_DOCTOR to "担当医",
        ENTITY_TOTAL_AMOUNT to "合計金額",
        ENTITY_HOSPITAL to "病院 / 店舗",
        ENTITY_MEDICINES to "処方薬",
        ENTITY_RAW_OCR to "抽出されたOCRテキスト",
        TAP_TO_COPY to "タップしてコピー",
        SETTINGS_TITLE to "設定・プライバシー",
        SETTINGS_AI_ENGINE to "AIエンジンとプライバシー",
        SETTINGS_DEEP_REASONING to "多言語AI検索・画像認識 (5x)",
        SETTINGS_ON_DEVICE_ONLY to "端末内OCRのみ使用",
        SETTINGS_BATTERY to "バッテリー最適化設定",
        SETTINGS_LANGUAGE to "表示言語",
        SETTINGS_SUBSCRIPTION to "プランとプリペイド残高",
        PREMIUM_TITLE to "DocuSnap プレミアム",
        PREMIUM_SUBTITLE to "多言語AI・無制限クラウド同期をご利用いただけます",
        PREMIUM_USAGE to "保存済み書類",
        PREMIUM_SUBSCRIBE_BTN to "Google Playで登録",
        PREMIUM_ACTIVE to "現在のプラン",
        LANGUAGE_TITLE to "言語を選択",
        AUTO_SCREENSHOT_BANNER to "自動スクリーンショット保存を有効化：タップして写真へのフルアクセスを許可"
    )

    private val KOREAN_STRINGS = mapOf(
        APP_TITLE to "DocuSnap AI",
        APP_SUBTITLE to "영수증·처방전 스마트 검색",
        NAV_ASK_AI to "AI 질문",
        NAV_VAULT to "스마트 보관함",
        NAV_SETTINGS to "설정",
        NAV_PREMIUM to "업그레이드",
        SEARCH_PLACEHOLDER to "모든 언어로 검색: '지난달 처방약' 또는 '전기요금'",
        SEARCH_AI_ANSWER to "AI 답변",
        SEARCH_NO_DOCS to "일치하는 문서를 찾을 수 없습니다. 문서를 촬영하거나 추가해 주세요.",
        SEARCH_ASK_BTN to "검색",
        BTN_PICK_PHOTOS to "사진 선택",
        BTN_CAMERA_SCAN to "카메라 스캔",
        BTN_SCAN_GALLERY to "갤러리 스캔",
        EXAMPLE_Q1 to "지난번 병원에서 처방받은 약이 무엇인가요?",
        EXAMPLE_Q2 to "지난달 전기요금 청구 금액을 알려줘",
        EXAMPLE_Q3 to "최근 마트 영수증 합계 찾기",
        EXAMPLE_Q4 to "최근 은행 이체 내역 요약",
        VAULT_TITLE to "스마트 보관함",
        VAULT_COUNT_SUBTITLE to "개 문서 자동 저장됨",
        VAULT_EMPTY to "문서가 없습니다",
        VAULT_EMPTY_DESC to "영수증, 처방전, 스크린샷을 촬영하면 자동 저장됩니다",
        CAT_ALL to "전체",
        CAT_MEDICAL to "의료/처방",
        CAT_RECEIPTS to "영수증",
        CAT_UTILITY to "공과금",
        CAT_BANK to "은행",
        ENTITY_DOCTOR to "의사",
        ENTITY_TOTAL_AMOUNT to "총 금액",
        ENTITY_HOSPITAL to "병원 / 매장",
        ENTITY_MEDICINES to "처방 약품",
        ENTITY_RAW_OCR to "추출된 OCR 텍스트",
        TAP_TO_COPY to "탭하여 복사",
        SETTINGS_TITLE to "설정 및 개인정보",
        SETTINGS_AI_ENGINE to "AI 엔진 및 개인정보",
        SETTINGS_DEEP_REASONING to "다국어 AI 비전 및 검색 (5x)",
        SETTINGS_ON_DEVICE_ONLY to "온디바이스 OCR만 사용",
        SETTINGS_BATTERY to "배터리 최적화",
        SETTINGS_LANGUAGE to "앱 언어",
        SETTINGS_SUBSCRIPTION to "구독 및 선불 잔액",
        PREMIUM_TITLE to "DocuSnap 프리미엄",
        PREMIUM_SUBTITLE to "문서 용량 확장 및 다국어 AI 검색 잠금 해제",
        PREMIUM_USAGE to "저장된 문서",
        PREMIUM_SUBSCRIBE_BTN to "Google Play로 구독",
        PREMIUM_ACTIVE to "현재 플랜",
        LANGUAGE_TITLE to "언어 선택",
        AUTO_SCREENSHOT_BANNER to "자동 스크린샷 저장 켜기: 탭하여 전체 사진 접근 허용"
    )

    private val URDU_STRINGS = mapOf(
        APP_TITLE to "ڈاکیو سنیپ اے آئی",
        APP_SUBTITLE to "بل اور نسخہ فائنڈر",
        NAV_ASK_AI to "اے آئی سے پوچھیں",
        NAV_VAULT to "سمارٹ والٹ",
        NAV_SETTINGS to "سیٹنگز",
        NAV_PREMIUM to "اپ گریڈ",
        SEARCH_PLACEHOLDER to "کسی بھی زبان میں تلاش کریں: 'پچھلے مہینے کی دوائی یا بل'",
        SEARCH_AI_ANSWER to "اے آئی جواب",
        SEARCH_NO_DOCS to "آپ کے والٹ میں اس سے متعلق کوئی دستاویز نہیں ملی۔ براہ کرم تصویر کھینچیں یا اسکرین شاٹ لیں۔",
        SEARCH_ASK_BTN to "پوچھیں",
        BTN_PICK_PHOTOS to "گیلری سے منتخب کریں",
        BTN_CAMERA_SCAN to "کیمرہ اسکین",
        BTN_SCAN_GALLERY to "گیلری اسکین کریں",
        EXAMPLE_Q1 to "ڈاکٹر نے پچھلے وزٹ میں کونسی ادویات لکھی تھیں؟",
        EXAMPLE_Q2 to "پچھلے مہینے کے بجلی کے بل کی کل رقم کیا تھی؟",
        EXAMPLE_Q3 to "حالیہ سپر مارکیٹ یا میڈیکل رسید تلاش کریں",
        EXAMPLE_Q4 to "تازہ ترین بینک ٹرانسفر سلپ کا خلاصہ دکھائیں",
        VAULT_TITLE to "سمارٹ والٹ",
        VAULT_COUNT_SUBTITLE to "دستاویزات آف لائن محفوظ ہیں",
        VAULT_EMPTY to "کوئی دستاویز نہیں ملی",
        VAULT_EMPTY_DESC to "بل، نسخہ یا رسید کی تصویر کھینچیں",
        CAT_ALL to "تمام",
        CAT_MEDICAL to "ادویات و نسخے",
        CAT_RECEIPTS to "رسیدیں",
        CAT_UTILITY to "یوٹیلٹی بلز",
        CAT_BANK to "بینک سلپس",
        ENTITY_DOCTOR to "ڈاکٹر",
        ENTITY_TOTAL_AMOUNT to "کل رقم",
        ENTITY_HOSPITAL to "ہسپتال / دکان",
        ENTITY_MEDICINES to "تجویز کردہ ادویات",
        ENTITY_RAW_OCR to "او سی آر سے نکالا گیا متن",
        TAP_TO_COPY to "کاپی کرنے کے لیے ٹیپ کریں",
        SETTINGS_TITLE to "سیٹنگز اور پرائیویسی",
        SETTINGS_AI_ENGINE to "اے آئی انجن اور پرائیویسی",
        SETTINGS_DEEP_REASONING to "ملٹی لنگوئل اے آئی سرچ (5x)",
        SETTINGS_ON_DEVICE_ONLY to "صرف آن ڈیوائس او سی آر",
        SETTINGS_BATTERY to "بیٹری آپٹیمائزیشن",
        SETTINGS_LANGUAGE to "ایپ کی زبان",
        SETTINGS_SUBSCRIPTION to "سبسکرپشن اور بیلنس",
        PREMIUM_TITLE to "ڈاکیو سنیپ پریمیم والٹ",
        PREMIUM_SUBTITLE to "دستاویزات کی گنجائش بڑھائیں اور بیلنس لوڈ کریں",
        PREMIUM_USAGE to "استعمال شدہ دستاویزات",
        PREMIUM_SUBSCRIBE_BTN to "گوگل پلے سے سبسکرائب کریں",
        PREMIUM_ACTIVE to "فعال پلان",
        LANGUAGE_TITLE to "زبان منتخب کریں",
        AUTO_SCREENSHOT_BANNER to "آٹو اسکرین شاٹ والٹ فعال کریں: مکمل فوٹو رسائی کے لیے ٹیپ کریں"
    )

    private val ARABIC_STRINGS = mapOf(
        APP_TITLE to "DocuSnap AI",
        APP_SUBTITLE to "مستكشف الفواتير والوصفات الطبية",
        NAV_ASK_AI to "اسأل الذكاء الاصطناعي",
        NAV_VAULT to "الخزنة الذكية",
        NAV_SETTINGS to "الإعدادات",
        NAV_PREMIUM to "ترقية",
        SEARCH_PLACEHOLDER to "ابحث بأي لغة: 'ما هو الدواء الموصوف؟'",
        SEARCH_AI_ANSWER to "إجابة الذكاء الاصطناعي",
        SEARCH_NO_DOCS to "لم يتم العثور على مستند مطابق في خزنتك. يُرجى التقاط صورة أو استيراد مستند.",
        SEARCH_ASK_BTN to "اسأل",
        BTN_PICK_PHOTOS to "اختيار صور",
        BTN_CAMERA_SCAN to "مسح بالكاميرا",
        BTN_SCAN_GALLERY to "فحص المعرض",
        EXAMPLE_Q1 to "ما هي الأدوية الموصوفة في زيارتي الأخيرة؟",
        EXAMPLE_Q2 to "كم كان مبلغ فاتورة الكهرباء الشهر الماضي؟",
        EXAMPLE_Q3 to "ابحث عن أحدث إيصال متجر أو صيدلية",
        EXAMPLE_Q4 to "لخص أحدث إيصال تحويل بنكي",
        VAULT_TITLE to "الخزنة الذكية",
        VAULT_COUNT_SUBTITLE to "مستندات مفهرسة دون إنترنت",
        VAULT_EMPTY to "لا توجد مستندات",
        VAULT_EMPTY_DESC to "التقط صورة لفاتورة أو وصفة طبية للفهرسة التلقائية",
        CAT_ALL to "الكل",
        CAT_MEDICAL to "طبي",
        CAT_RECEIPTS to "إيصالات",
        CAT_UTILITY to "فواتير الخدمات",
        CAT_BANK to "إيصالات البنك",
        ENTITY_DOCTOR to "الطبيب",
        ENTITY_TOTAL_AMOUNT to "المبلغ الإجمالي",
        ENTITY_HOSPITAL to "المستشفى / المتجر",
        ENTITY_MEDICINES to "الأدوية الموصوفة",
        ENTITY_RAW_OCR to "النص المستخرج محلياً",
        TAP_TO_COPY to "انقر للنسخ",
        SETTINGS_TITLE to "الإعدادات والخصوصية",
        SETTINGS_AI_ENGINE to "محرك الذكاء الاصطناعي والخصوصية",
        SETTINGS_DEEP_REASONING to "تحليل ذكي متعدد اللغات (5x)",
        SETTINGS_ON_DEVICE_ONLY to "التعرف على النصوص على الجهاز فقط",
        SETTINGS_BATTERY to "تحسين البطارية",
        SETTINGS_LANGUAGE to "لغة التطبيق",
        SETTINGS_SUBSCRIPTION to "الاشتراك والرصيد المسبق",
        PREMIUM_TITLE to "خزنة DocuSnap المميزة",
        PREMIUM_SUBTITLE to "زيادة سعة المستندات والبحث بالذكاء الاصطناعي",
        PREMIUM_USAGE to "المستندات المستخدمة",
        PREMIUM_SUBSCRIBE_BTN to "الاشتراك عبر Google Play",
        PREMIUM_ACTIVE to "الخطة الحالية",
        LANGUAGE_TITLE to "اختر اللغة",
        AUTO_SCREENSHOT_BANNER to "تفعيل الحفظ التلقائي للقطات الشاشة: انقر لمنح الوصول الكامل للصور"
    )

    private val SPANISH_STRINGS = mapOf(
        APP_TITLE to "DocuSnap AI",
        APP_SUBTITLE to "Buscador de Facturas y Recetas",
        NAV_ASK_AI to "Preguntar a IA",
        NAV_VAULT to "Bóveda Inteligente",
        NAV_SETTINGS to "Ajustes",
        NAV_PREMIUM to "Mejorar",
        SEARCH_PLACEHOLDER to "Busca en cualquier idioma: 'Última receta médica'",
        SEARCH_AI_ANSWER to "Respuesta de IA",
        SEARCH_NO_DOCS to "No se encontró ningún documento coincidente en su bóveda.",
        SEARCH_ASK_BTN to "Preguntar",
        BTN_PICK_PHOTOS to "Elegir fotos",
        BTN_CAMERA_SCAN to "Escanear Cámara",
        BTN_SCAN_GALLERY to "Escanear Galería",
        EXAMPLE_Q1 to "¿Qué medicamento me recetaron el mes pasado?",
        EXAMPLE_Q2 to "¿Cuánto fue mi última factura de electricidad?",
        EXAMPLE_Q3 to "Buscar último recibo del supermercado",
        EXAMPLE_Q4 to "Resumir mi último comprobante bancario",
        VAULT_TITLE to "Bóveda Inteligente",
        VAULT_COUNT_SUBTITLE to "documentos indexados offline",
        VAULT_EMPTY to "No hay documentos",
        VAULT_EMPTY_DESC to "Tome una foto de una factura o receta para indexarla",
        CAT_ALL to "Todos",
        CAT_MEDICAL to "Médico",
        CAT_RECEIPTS to "Recibos",
        CAT_UTILITY to "Servicios",
        CAT_BANK to "Bancos",
        ENTITY_DOCTOR to "Médico",
        ENTITY_TOTAL_AMOUNT to "Monto Total",
        ENTITY_HOSPITAL to "Hospital / Tienda",
        ENTITY_MEDICINES to "Medicamentos recetados",
        ENTITY_RAW_OCR to "Texto OCR en el dispositivo",
        TAP_TO_COPY to "Toca para copiar",
        SETTINGS_TITLE to "Ajustes y Privacidad",
        SETTINGS_AI_ENGINE to "MOTOR IA Y PRIVACIDAD",
        SETTINGS_DEEP_REASONING to "Búsqueda IA Multilingüe (5x)",
        SETTINGS_ON_DEVICE_ONLY to "Solo OCR en Dispositivo",
        SETTINGS_BATTERY to "Optimización de Batería",
        SETTINGS_LANGUAGE to "Idioma de la aplicación",
        SETTINGS_SUBSCRIPTION to "Plan y Saldo Prepago",
        PREMIUM_TITLE to "Bóveda Premium DocuSnap",
        PREMIUM_SUBTITLE to "Amplíe su capacidad de documentos y saldo IA global",
        PREMIUM_USAGE to "Documentos Usados",
        PREMIUM_SUBSCRIBE_BTN to "Suscribirse con Google Play",
        PREMIUM_ACTIVE to "Plan Activo",
        LANGUAGE_TITLE to "Seleccionar idioma",
        AUTO_SCREENSHOT_BANNER to "Activar bóveda de capturas automática: toque para acceso completo a fotos"
    )

    private val FRENCH_STRINGS = mapOf(
        APP_TITLE to "DocuSnap AI",
        APP_SUBTITLE to "Recherche de Factures & Ordonnances",
        NAV_ASK_AI to "Demander à l'IA",
        NAV_VAULT to "Coffre Intelligent",
        NAV_SETTINGS to "Paramètres",
        NAV_PREMIUM to "Passer Pro",
        SEARCH_PLACEHOLDER to "Cherchez dans toute langue : 'Dernière ordonnance'",
        SEARCH_AI_ANSWER to "Réponse de l'IA",
        SEARCH_NO_DOCS to "Aucun document correspondant trouvé dans votre coffre.",
        SEARCH_ASK_BTN to "Rechercher",
        BTN_PICK_PHOTOS to "Choisir photos",
        BTN_CAMERA_SCAN to "Scanner caméra",
        BTN_SCAN_GALLERY to "Scanner galerie",
        EXAMPLE_Q1 to "Quel médicament m'a été prescrit le mois dernier ?",
        EXAMPLE_Q2 to "Quel était le montant de ma dernière facture d'électricité ?",
        EXAMPLE_Q3 to "Trouver mon dernier ticket de supermarché",
        EXAMPLE_Q4 to "Résumer mon dernier virement bancaire",
        VAULT_TITLE to "Coffre Intelligent",
        VAULT_COUNT_SUBTITLE to "documents indexés hors ligne",
        VAULT_EMPTY to "Aucun document trouvé",
        VAULT_EMPTY_DESC to "Prenez en photo une facture ou une ordonnance",
        CAT_ALL to "Tous",
        CAT_MEDICAL to "Médical",
        CAT_RECEIPTS to "Reçus",
        CAT_UTILITY to "Factures",
        CAT_BANK to "Banque",
        ENTITY_DOCTOR to "Médecin",
        ENTITY_TOTAL_AMOUNT to "Montant Total",
        ENTITY_HOSPITAL to "Hôpital / Magasin",
        ENTITY_MEDICINES to "Médicaments prescrits",
        ENTITY_RAW_OCR to "Texte brut OCR sur l'appareil",
        TAP_TO_COPY to "Appuyer pour copier",
        SETTINGS_TITLE to "Paramètres et Confidentialité",
        SETTINGS_AI_ENGINE to "MOTEUR IA & CONFIDENTIALITÉ",
        SETTINGS_DEEP_REASONING to "Recherche IA Multilingue (5x)",
        SETTINGS_ON_DEVICE_ONLY to "OCR sur appareil uniquement",
        SETTINGS_BATTERY to "Optimisation de batterie",
        SETTINGS_LANGUAGE to "Langue de l'application",
        SETTINGS_SUBSCRIPTION to "Abonnement & Solde Prépayé",
        PREMIUM_TITLE to "DocuSnap Coffre Premium",
        PREMIUM_SUBTITLE to "Augmentez votre limite et votre solde IA multilingue",
        PREMIUM_USAGE to "Documents Utilisés",
        PREMIUM_SUBSCRIBE_BTN to "S'abonner via Google Play",
        PREMIUM_ACTIVE to "Forfait Actif",
        LANGUAGE_TITLE to "Choisir la langue",
        AUTO_SCREENSHOT_BANNER to "Activer la sauvegarde auto des captures : appuyez pour l'accès complet"
    )

    private val GERMAN_STRINGS = mapOf(
        APP_TITLE to "DocuSnap AI",
        APP_SUBTITLE to "Rechnungs- & Rezept-Finder",
        NAV_ASK_AI to "KI fragen",
        NAV_VAULT to "Smart-Tresor",
        NAV_SETTINGS to "Einstellungen",
        NAV_PREMIUM to "Upgrade",
        SEARCH_PLACEHOLDER to "In jeder Sprache suchen: 'Letztes Rezept' oder 'Beleg'",
        SEARCH_AI_ANSWER to "KI-Antwort",
        SEARCH_NO_DOCS to "Kein passendes Dokument im Tresor gefunden.",
        SEARCH_ASK_BTN to "Fragen",
        BTN_PICK_PHOTOS to "Fotos wählen",
        BTN_CAMERA_SCAN to "Kamera-Scan",
        BTN_SCAN_GALLERY to "Galerie scannen",
        EXAMPLE_Q1 to "Welches Medikament wurde mir zuletzt verschrieben?",
        EXAMPLE_Q2 to "Wie hoch war meine letzte Stromrechnung?",
        EXAMPLE_Q3 to "Letzten Supermarkt-Kassenbon finden",
        EXAMPLE_Q4 to "Letzten Kontoauszug zusammenfassen",
        VAULT_TITLE to "Smart-Tresor",
        VAULT_COUNT_SUBTITLE to "Dokumente offline indexiert",
        VAULT_EMPTY to "Keine Dokumente",
        VAULT_EMPTY_DESC to "Fotografieren Sie eine Rechnung oder ein Rezept",
        CAT_ALL to "Alle",
        CAT_MEDICAL to "Medizin",
        CAT_RECEIPTS to "Belege",
        CAT_UTILITY to "Nebenkosten",
        CAT_BANK to "Bank",
        ENTITY_DOCTOR to "Arzt",
        ENTITY_TOTAL_AMOUNT to "Gesamtbetrag",
        ENTITY_HOSPITAL to "Klinik / Händler",
        ENTITY_MEDICINES to "Verschriebene Medikamente",
        ENTITY_RAW_OCR to "Lokaler OCR-Text",
        TAP_TO_COPY to "Tippen zum Kopieren",
        SETTINGS_TITLE to "Einstellungen & Datenschutz",
        SETTINGS_AI_ENGINE to "KI-ENGINE & DATENSCHUTZ",
        SETTINGS_DEEP_REASONING to "Globale Mehrsprachige KI-Suche (5x)",
        SETTINGS_ON_DEVICE_ONLY to "Nur On-Device-OCR",
        SETTINGS_BATTERY to "Akku-Optimierung",
        SETTINGS_LANGUAGE to "App-Sprache",
        SETTINGS_SUBSCRIPTION to "Abo & Prepaid-Guthaben",
        PREMIUM_TITLE to "DocuSnap Premium-Tresor",
        PREMIUM_SUBTITLE to "Speicher erweitern & Guthaben für KI-Suche laden",
        PREMIUM_USAGE to "Verwendete Dokumente",
        PREMIUM_SUBSCRIBE_BTN to "Über Google Play abonnieren",
        PREMIUM_ACTIVE to "Aktiver Tarif",
        LANGUAGE_TITLE to "Sprache wählen",
        AUTO_SCREENSHOT_BANNER to "Auto-Screenshot-Tresor aktivieren: Tippen für vollen Fotozugriff"
    )

    private val CHINESE_STRINGS = mapOf(
        APP_TITLE to "DocuSnap AI",
        APP_SUBTITLE to "账单与处方查找器",
        NAV_ASK_AI to "向 AI 提问",
        NAV_VAULT to "智能保险库",
        NAV_SETTINGS to "设置",
        NAV_PREMIUM to "升级",
        SEARCH_PLACEHOLDER to "用任意语言搜索：'医生开了什么药？' 或 '电费账单'",
        SEARCH_AI_ANSWER to "AI 回答",
        SEARCH_NO_DOCS to "在您的保险库中未找到相关文档，请拍照或导入新文件。",
        SEARCH_ASK_BTN to "提问",
        BTN_PICK_PHOTOS to "选择照片",
        BTN_CAMERA_SCAN to "相机扫描",
        BTN_SCAN_GALLERY to "扫描图库",
        EXAMPLE_Q1 to "上次看病医生开了哪些处方药？",
        EXAMPLE_Q2 to "上个月的水电费账单总金额是多少？",
        EXAMPLE_Q3 to "查找最近的超市或药店小票",
        EXAMPLE_Q4 to "总结最近的一笔银行转账凭据",
        VAULT_TITLE to "智能保险库",
        VAULT_COUNT_SUBTITLE to "份文档已离线索引",
        VAULT_EMPTY to "暂无文档",
        VAULT_EMPTY_DESC to "拍摄账单、处方或截图即可自动保存",
        CAT_ALL to "全部",
        CAT_MEDICAL to "医疗处方",
        CAT_RECEIPTS to "消费收据",
        CAT_UTILITY to "水电煤账单",
        CAT_BANK to "银行账单",
        ENTITY_DOCTOR to "医生",
        ENTITY_TOTAL_AMOUNT to "总金额",
        ENTITY_HOSPITAL to "医院 / 商家",
        ENTITY_MEDICINES to "处方药物",
        ENTITY_RAW_OCR to "设备本地 OCR 文本",
        TAP_TO_COPY to "点击复制",
        SETTINGS_TITLE to "设置与隐私",
        SETTINGS_AI_ENGINE to "AI 引擎与隐私",
        SETTINGS_DEEP_REASONING to "全球多语言 AI 识图与搜索 (5x)",
        SETTINGS_ON_DEVICE_ONLY to "仅设备本地 OCR",
        SETTINGS_BATTERY to "电池优化",
        SETTINGS_LANGUAGE to "应用语言",
        SETTINGS_SUBSCRIPTION to "订阅套餐与预付余额",
        PREMIUM_TITLE to "DocuSnap 高级会员保险库",
        PREMIUM_SUBTITLE to "扩展文档上限，充值解锁多语言 AI 搜索",
        PREMIUM_USAGE to "已存文档数",
        PREMIUM_SUBSCRIBE_BTN to "通过 Google Play 订阅",
        PREMIUM_ACTIVE to "当前套餐",
        LANGUAGE_TITLE to "选择语言",
        AUTO_SCREENSHOT_BANNER to "开启自动截屏保存：点击授予完整照片与截屏权限"
    )

    private val HINDI_STRINGS = mapOf(
        APP_TITLE to "DocuSnap AI",
        APP_SUBTITLE to "बिल और प्रिस्क्रिप्शन खोजक",
        NAV_ASK_AI to "AI से पूछें",
        NAV_VAULT to "स्मार्ट वॉल्ट",
        NAV_SETTINGS to "सेटिंग्स",
        NAV_PREMIUM to "अपग्रेड",
        SEARCH_PLACEHOLDER to "किसी भी भाषा में खोजें: 'पिछला बिजली बिल' या 'दवाइयां'",
        SEARCH_AI_ANSWER to "AI उत्तर",
        SEARCH_NO_DOCS to "आपके वॉल्ट में कोई संबंधित दस्तावेज़ नहीं मिला। कृपया फोटो लें या स्क्रीनशॉट सेव करें।",
        SEARCH_ASK_BTN to "पूछें",
        BTN_PICK_PHOTOS to "फोटो चुनें",
        BTN_CAMERA_SCAN to "कैमरा स्कैन",
        BTN_SCAN_GALLERY to "गैलरी स्कैन करें",
        EXAMPLE_Q1 to "पिछली विजिट में डॉक्टर ने कौन सी दवाइयां लिखी थीं?",
        EXAMPLE_Q2 to "पिछले महीने का बिजली का बिल कितना था?",
        EXAMPLE_Q3 to "मेडिकल स्टोर या सुपरमार्केट की रसीद खोजें",
        EXAMPLE_Q4 to "ताज़ा बैंक ट्रांसफर रसीद का सारांश दिखाएं",
        VAULT_TITLE to "स्मार्ट वॉल्ट",
        VAULT_COUNT_SUBTITLE to "दस्तावेज़ ऑफलाइन इंडेक्स किए गए हैं",
        VAULT_EMPTY to "कोई दस्तावेज़ नहीं मिला",
        VAULT_EMPTY_DESC to "बिल या पर्ची की फोटो खींचें",
        CAT_ALL to "सभी",
        CAT_MEDICAL to "दवाइयां और पर्ची",
        CAT_RECEIPTS to "रसीदें",
        CAT_UTILITY to "बिजली/गैस बिल",
        CAT_BANK to "बैंक रसीद",
        ENTITY_DOCTOR to "डॉक्टर",
        ENTITY_TOTAL_AMOUNT to "कुल राशि",
        ENTITY_HOSPITAL to "अस्पताल / दुकान",
        ENTITY_MEDICINES to "निर्धारित दवाइयां",
        ENTITY_RAW_OCR to "डिवाइस पर निकाला गया टेक्स्ट",
        TAP_TO_COPY to "कॉपी करने के लिए टैप करें",
        SETTINGS_TITLE to "सेटिंग्स और गोपनीयता",
        SETTINGS_AI_ENGINE to "AI इंजन और गोपनीयता",
        SETTINGS_DEEP_REASONING to "ग्लोबल मल्टीलिंगुअल AI खोज (5x)",
        SETTINGS_ON_DEVICE_ONLY to "केवल ऑन-डिवाइस OCR",
        SETTINGS_BATTERY to "बैटरी अनुकूलन",
        SETTINGS_LANGUAGE to "ऐप की भाषा",
        SETTINGS_SUBSCRIPTION to "सब्सक्रिप्शन और प्रीपेड बैलेंस",
        PREMIUM_TITLE to "DocuSnap प्रीमियम वॉल्ट",
        PREMIUM_SUBTITLE to "दस्तावेज़ों की सीमा बढ़ाएं और मल्टीलिंगुअल AI अनलॉक करें",
        PREMIUM_USAGE to "उपयोग किए गए दस्तावेज़",
        PREMIUM_SUBSCRIBE_BTN to "Google Play से सब्सक्राइब करें",
        PREMIUM_ACTIVE to "सक्रिय प्लान",
        LANGUAGE_TITLE to "भाषा चुनें",
        AUTO_SCREENSHOT_BANNER to "ऑटो-स्क्रीनशॉट वॉल्ट चालू करें: पूर्ण फ़ोटो अनुमति के लिए टैप करें"
    )
}
