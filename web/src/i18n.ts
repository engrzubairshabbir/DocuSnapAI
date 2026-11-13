export interface LanguageInfo {
  code: string;
  name: string;
  nativeName: string;
  flag: string;
  isRtl?: boolean;
}

export const SUPPORTED_LANGUAGES: LanguageInfo[] = [
  { code: 'en', name: 'English', nativeName: 'English', flag: '🇬🇧' },
  { code: 'ur', name: 'Urdu', nativeName: 'اردو', flag: '🇵🇰', isRtl: true },
  { code: 'ar', name: 'Arabic', nativeName: 'العربية', flag: '🇸🇦', isRtl: true },
  { code: 'es', name: 'Spanish', nativeName: 'Español', flag: '🇪🇸' },
  { code: 'fr', name: 'French', nativeName: 'Français', flag: '🇫🇷' },
  { code: 'de', name: 'German', nativeName: 'Deutsch', flag: '🇩🇪' },
  { code: 'zh', name: 'Chinese', nativeName: '中文', flag: '🇨🇳' },
  { code: 'hi', name: 'Hindi', nativeName: 'हिंदी', flag: '🇮🇳' },
];

export const TRANSLATIONS: Record<string, Record<string, string>> = {
  en: {
    appTitle: 'DocuSnap AI',
    appSubtitle: 'Bill & Rx Finder',
    askAi: 'Ask AI',
    smartVault: 'Smart Vault',
    cloudSync: 'Cloud Sync',
    pricing: 'Plans',
    addDoc: 'Add Document',
    searchPlaceholder: "Ask anything: 'What medicine did doctor write?' or 'Last electricity bill'...",
    searchBtn: 'Ask AI',
    aiAnswer: 'AI Answer',
    relevantDocs: 'Relevant Documents',
    allDocs: 'All',
    medical: 'Medical',
    receipts: 'Receipts',
    utility: 'Utility',
    bank: 'Bank',
    inspect: 'Inspect',
    uploadTitle: 'Add Bill or Prescription',
    takePhoto: 'Snap Camera Photo Now',
    chooseFile: 'Select image from Files / Photos',
  },
  ur: {
    appTitle: 'ڈاکیو سنیپ اے آئی',
    appSubtitle: 'بل اور نسخہ فائنڈر',
    askAi: 'اے آئی سے پوچھیں',
    smartVault: 'سمارٹ والٹ',
    cloudSync: 'کلاؤڈ سنک',
    pricing: 'پلانز',
    addDoc: 'دستاویز شامل کریں',
    searchPlaceholder: "کچھ بھی پوچھیں: 'ڈاکٹر نے پیٹ درد کی کونسی گولی لکھی تھی؟'",
    searchBtn: 'پوچھیں',
    aiAnswer: 'اے آئی جواب',
    relevantDocs: 'متعلقہ دستاویزات',
    allDocs: 'تمام',
    medical: 'ادویات و نسخے',
    receipts: 'رسیدیں',
    utility: 'یوٹیلٹی بلز',
    bank: 'بینک سلپس',
    inspect: 'تفصیل دیکھیں',
    uploadTitle: 'بل یا نسخہ شامل کریں',
    takePhoto: 'کیمرہ سے تصویر کھینچیں',
    chooseFile: 'گیلری سے تصویر منتخب کریں',
  },
  ar: {
    appTitle: 'DocuSnap AI',
    appSubtitle: 'مستكشف الفواتير والوصفات الطبية',
    askAi: 'اسأل الذكاء الاصطناعي',
    smartVault: 'الخزنة الذكية',
    cloudSync: 'المزامنة السحابية',
    pricing: 'الخطط',
    addDoc: 'إضافة مستند',
    searchPlaceholder: "اسأل أي شيء: 'ما هو الدواء الموصوف؟'",
    searchBtn: 'اسأل',
    aiAnswer: 'إجابة الذكاء الاصطناعي',
    relevantDocs: 'المستندات ذات الصلة',
    allDocs: 'الكل',
    medical: 'طبي',
    receipts: 'إيصالات',
    utility: 'فواتير الخدمات',
    bank: 'إيصالات البنك',
    inspect: 'معاينة',
    uploadTitle: 'إضافة فاتورة أو وصفة طبية',
    takePhoto: 'التقاط بالكاميرا الآن',
    chooseFile: 'اختيار من الملفات أو الصور',
  },
  es: {
    appTitle: 'DocuSnap AI',
    appSubtitle: 'Buscador de Facturas y Recetas',
    askAi: 'Preguntar a IA',
    smartVault: 'Bóveda Inteligente',
    cloudSync: 'Sincronización',
    pricing: 'Planes',
    addDoc: 'Añadir Documento',
    searchPlaceholder: "¿Qué medicamento me recetó el médico?",
    searchBtn: 'Preguntar',
    aiAnswer: 'Respuesta de IA',
    relevantDocs: 'Documentos Relevantes',
    allDocs: 'Todos',
    medical: 'Médico',
    receipts: 'Recibos',
    utility: 'Servicios',
    bank: 'Bancos',
    inspect: 'Inspeccionar',
    uploadTitle: 'Añadir Factura o Receta',
    takePhoto: 'Tomar foto con la cámara',
    chooseFile: 'Seleccionar imagen de archivos',
  }
};

export function getTranslation(key: string, lang: string = 'en'): string {
  const dict = TRANSLATIONS[lang] || TRANSLATIONS['en'];
  return dict[key] || TRANSLATIONS['en'][key] || key;
}

