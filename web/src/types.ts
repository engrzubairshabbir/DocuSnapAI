export interface KeyEntities {
  doctor_name: string | null;
  medicines: string[];
  total_amount: number | null;
  vendor_or_hospital: string | null;
  date: string;
}

export interface DocumentEntity {
  id: string;
  file_hash: string;
  cloud_storage_url: string;
  local_image_uri?: string;
  raw_ocr_text: string;
  title: string;
  ai_summary: string;
  document_type: 'Medical Prescription' | 'Expense Receipt' | 'Utility Bill' | 'Bank Slip' | 'General Document' | 'PDF Document';
  key_entities_json: string;
  searchable_tags_json: string;
  date_detected: string;
  sync_status: 'LOCAL_ONLY' | 'PENDING_SYNC' | 'SYNCED';
  timestamp: number;
}

