import { initializeApp, getApps, getApp } from 'firebase/app';
import { 
  getAuth, 
  signInAnonymously, 
  GoogleAuthProvider, 
  signInWithPopup, 
  onAuthStateChanged,
  User 
} from 'firebase/auth';
import { 
  getFirestore, 
  collection, 
  doc, 
  setDoc, 
  deleteDoc, 
  onSnapshot, 
  query, 
  orderBy 
} from 'firebase/firestore';
import { 
  getStorage, 
  ref, 
  uploadBytes, 
  getDownloadURL 
} from 'firebase/storage';
import { DocumentEntity } from './types.ts';

// Configured to match the user's Firebase project (webproject-58306)
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyCEtQx7ci2_0hmTlog8VbXZoBKkCnWz7D8",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "webproject-58306.firebaseapp.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "webproject-58306",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "webproject-58306.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "638866510492",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:638866510492:web:2baa12937b598dcfb2d359"
};

const app = !getApps().length ? initializeApp(firebaseConfig) : getApp();
export const auth = getAuth(app);
export const db = getFirestore(app);
export const storage = getStorage(app);

// Initial international demo items (including English, Utility & Japanese receipt) for instant preview
export const DEMO_DOCUMENTS: DocumentEntity[] = [
  {
    id: 'demo-rx-001',
    file_hash: 'hash-demo-prescription-1',
    cloud_storage_url: 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=800&auto=format&fit=crop&q=80',
    title: 'Prescription - Dr. Elena Vance (Metro Clinic)',
    raw_ocr_text: 'DR. ELENA VANCE, MD\nMetro General Medical Center\nPatient: Jordan Smith | Date: 2026-11-04\nRx:\n1. Omeprazole 40mg - 1 capsule before breakfast (stomach relief)\n2. Amoxicillin 500mg - 1x twice daily for 5 days\n3. Acetaminophen 500mg as needed for pain\nConsultation fee: $95.00',
    ai_summary: 'Prescription by Dr. Elena Vance for stomach acid relief and mild infection. Prescribed Omeprazole 40mg, Amoxicillin 500mg, and Acetaminophen 500mg.',
    document_type: 'Medical Prescription',
    key_entities_json: JSON.stringify({
      doctor_name: 'Dr. Elena Vance',
      medicines: ['Omeprazole 40mg', 'Amoxicillin 500mg', 'Acetaminophen 500mg'],
      total_amount: 95,
      vendor_or_hospital: 'Metro General Medical Center',
      date: '2026-11-04'
    }),
    searchable_tags_json: JSON.stringify(['prescription', 'stomach', 'omeprazole', 'amoxicillin', 'medical']),
    date_detected: '2026-11-04',
    sync_status: 'SYNCED',
    timestamp: Date.now() - 86400000 * 3
  },
  {
    id: 'demo-bill-002',
    file_hash: 'hash-demo-electric-bill-2',
    cloud_storage_url: 'https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?w=800&auto=format&fit=crop&q=80',
    title: 'Pacific Grid Electricity Bill - Oct 2026',
    raw_ocr_text: 'PACIFIC GRID ENERGY STATEMENT\nAccount No: 0400018938210\nBilling Period: OCTOBER 2026\nUsage: 382 kWh\nTotal Amount Due: $148.20 USD\nDue Date: 2026-11-18',
    ai_summary: 'Pacific Grid Energy residential electricity statement for October 2026. Total amount due is $148.20 USD (382 kWh).',
    document_type: 'Utility Bill',
    key_entities_json: JSON.stringify({
      doctor_name: null,
      medicines: [],
      total_amount: 148.20,
      vendor_or_hospital: 'Pacific Grid Energy',
      date: '2026-10-28'
    }),
    searchable_tags_json: JSON.stringify(['electricity', 'utility bill', 'pacific grid', 'kwh', '148.20']),
    date_detected: '2026-10-28',
    sync_status: 'SYNCED',
    timestamp: Date.now() - 86400000 * 7
  },
  {
    id: 'demo-receipt-jp-003',
    file_hash: 'hash-demo-japanese-receipt-3',
    cloud_storage_url: 'https://images.unsplash.com/photo-1556742049-0a67c5574f73?w=800&auto=format&fit=crop&q=80',
    title: '領収書 - 株式会社東京マーケット (Tokyo Market)',
    raw_ocr_text: '領収書\n株式会社東京マーケット 渋谷店\n日付: 2026-11-08 19:42\n1. 有機緑茶 500ml x 6: ¥980\n2. コシヒカリ 5kg: ¥2,450\n3. オリーブオイル: ¥1,020\n小計: ¥4,450\n消費税 (10%): ¥400\n合計金額: ¥4,850',
    ai_summary: 'Japanese grocery receipt (領収書) from Tokyo Market Shibuya (株式会社東京マーケット). Total paid is ¥4,850 JPY including consumption tax.',
    document_type: 'Expense Receipt',
    key_entities_json: JSON.stringify({
      doctor_name: null,
      medicines: [],
      total_amount: 4850,
      vendor_or_hospital: '株式会社東京マーケット (Tokyo Market)',
      date: '2026-11-08'
    }),
    searchable_tags_json: JSON.stringify(['領収書', 'receipt', '東京マーケット', 'grocery', 'jpy', '4850']),
    date_detected: '2026-11-08',
    sync_status: 'SYNCED',
    timestamp: Date.now() - 86400000 * 1
  }
];

export const FUNCTIONS_BASE_URL = 'https://us-central1-webproject-58306.cloudfunctions.net';

export async function getWalletBalanceCents(): Promise<number> {
  try {
    const user = await ensureAuthUser();
    if (!user) return 0;
    const token = await user.getIdToken();
    const res = await fetch(`${FUNCTIONS_BASE_URL}/getWalletStatus`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    if (!res.ok) return 0;
    const data = await res.json();
    return typeof data.balanceCents === 'number' ? data.balanceCents : 0;
  } catch {
    return 0;
  }
}

export async function ensureAuthUser(): Promise<User | null> {
  if (auth.currentUser) return auth.currentUser;
  try {
    const cred = await signInAnonymously(auth);
    return cred.user;
  } catch (e) {
    console.warn('Anonymous auth failed or offline mode:', e);
    return null;
  }
}

export async function loginWithGoogle(): Promise<User | null> {
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });
  try {
    const res = await signInWithPopup(auth, provider);
    return res.user;
  } catch (e: any) {
    console.error('Google sign in error:', e);
    if (e?.code === 'auth/unauthorized-domain') {
      alert("Firebase Domain Error: Please ensure 'docusnap.consultways.com' is saved in Firebase Console -> Authentication -> Settings -> Authorized Domains.");
    } else if (e?.code === 'auth/operation-not-allowed') {
      alert("Firebase Setup Needed: Google Sign-in provider is disabled in Firebase! Go to Firebase Console -> Authentication -> Sign-in method -> Click Google -> Enable -> Choose Support Email -> Save.");
    } else if (e?.code !== 'auth/popup-closed-by-user') {
      alert(`Google Sign-In Notice [${e?.code || 'error'}]: ${e?.message || 'Authentication window closed or blocked.'}`);
    }
    return null;
  }
}

export async function logoutUser(): Promise<void> {
  try {
    await auth.signOut();
    await signInAnonymously(auth);
  } catch (e) {
    console.error('Sign out error:', e);
  }
}

export function subscribeToDocuments(
  userId: string, 
  onUpdate: (docs: DocumentEntity[]) => void
) {
  try {
    const q = query(
      collection(db, 'users', userId, 'documents'),
      orderBy('timestamp', 'desc')
    );
    return onSnapshot(q, (snapshot) => {
      const docs: DocumentEntity[] = [];
      snapshot.forEach((d) => {
        docs.push(d.data() as DocumentEntity);
      });
      if (docs.length > 0) {
        onUpdate(docs);
      } else {
        onUpdate(DEMO_DOCUMENTS);
      }
    }, (error) => {
      console.warn('Firestore snapshot error, using cached demo data:', error);
      onUpdate(DEMO_DOCUMENTS);
    });
  } catch (e) {
    console.warn('Firestore subscription fallback to demo:', e);
    onUpdate(DEMO_DOCUMENTS);
    return () => {};
  }
}

export async function saveDocument(userId: string, document: DocumentEntity): Promise<void> {
  try {
    const docRef = doc(db, 'users', userId, 'documents', document.id);
    const savePromise = setDoc(docRef, document, { merge: true });
    // 4-second timeout to prevent network stalls
    const timeout = new Promise((_, reject) => setTimeout(() => reject(new Error('Firestore timeout')), 4000));
    await Promise.race([savePromise, timeout]);
  } catch (e) {
    console.warn('Could not save document to Firestore directly (offline or cached):', e);
  }
}

export async function deleteDocument(userId: string, documentId: string): Promise<void> {
  try {
    const docRef = doc(db, 'users', userId, 'documents', documentId);
    await deleteDoc(docRef);
  } catch (e) {
    console.warn('Could not delete from Firestore:', e);
  }
}

export async function uploadImageAndThumbnail(
  userId: string,
  docId: string,
  fullBlob: Blob,
  thumbBlob: Blob
): Promise<{ fullUrl: string; thumbUrl: string }> {
  const tryUpload = async () => {
    const isPdf = fullBlob.type === 'application/pdf';
    const ext = isPdf ? 'pdf' : 'jpg';
    const fullRef = ref(storage, `users/${userId}/images/${docId}.${ext}`);
    const thumbRef = ref(storage, `users/${userId}/thumbs/${docId}.webp`);

    await uploadBytes(fullRef, fullBlob);
    await uploadBytes(thumbRef, thumbBlob);

    const fullUrl = await getDownloadURL(fullRef);
    const thumbUrl = await getDownloadURL(thumbRef);

    return { fullUrl, thumbUrl };
  };

  // 5-second race timeout: prevents hanging when custom domain CORS or storage rules take time
  const timeoutPromise = new Promise<{ fullUrl: string; thumbUrl: string }>((_, reject) => {
    setTimeout(() => reject(new Error('Storage upload timeout')), 5000);
  });

  try {
    return await Promise.race([tryUpload(), timeoutPromise]);
  } catch (e) {
    console.warn('Storage upload timeout or CORS fallback to client-side persistent storage:', e);
    const dataUrl = await blobToDataUrl(fullBlob);
    const thumbUrl = await blobToDataUrl(thumbBlob);
    return { fullUrl: dataUrl, thumbUrl: thumbUrl };
  }
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve) => {
    if (blob.size > 5 * 1024 * 1024) {
      resolve(URL.createObjectURL(blob));
      return;
    }
    const reader = new FileReader();
    reader.onloadend = () => resolve((reader.result as string) || URL.createObjectURL(blob));
    reader.onerror = () => resolve(URL.createObjectURL(blob));
    reader.readAsDataURL(blob);
  });
}

/**
 * On-demand media fetch: Tells the mobile phone to upload the full-resolution photo for docId.
 */
export function requestMediaFromPhone(
  userId: string,
  docId: string,
  onResolved: (url: string | null, error?: string) => void
): () => void {
  try {
    const reqRef = doc(db, 'users', userId, 'media_requests', docId);
    setDoc(reqRef, {
      docId,
      status: 'PENDING',
      requestedAt: Date.now()
    }, { merge: true });

    const unsubscribe = onSnapshot(reqRef, (snapshot) => {
      if (!snapshot.exists()) return;
      const data = snapshot.data();
      if (data?.status === 'COMPLETED' && data.cloud_storage_url) {
        onResolved(data.cloud_storage_url);
      } else if (data?.status === 'FAILED') {
        onResolved(null, data.error || 'Failed to retrieve image from phone.');
      }
    });

    return unsubscribe;
  } catch (err: any) {
    onResolved(null, err?.message || 'Request failed');
    return () => {};
  }
}

