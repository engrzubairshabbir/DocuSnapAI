import React, { useState, useRef } from 'react';
import { X, Upload, Camera, Sparkles, CheckCircle2, AlertCircle } from 'lucide-react';
import { analyzeDocumentWithGemini } from '../gemini.ts';
import { uploadImageAndThumbnail, saveDocument } from '../firebase.ts';
import { DocumentEntity } from '../types.ts';

interface Props {
  userId: string;
  isOpen: boolean;
  onClose: () => void;
  onDocumentAdded: (doc: DocumentEntity) => void;
}

export const UploadModal: React.FC<Props> = ({ userId, isOpen, onClose, onDocumentAdded }) => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [processing, setProcessing] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string>('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const cameraInputRef = useRef<HTMLInputElement>(null);

  if (!isOpen) return null;

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      setSelectedFile(file);
      setPreviewUrl(URL.createObjectURL(file));
    }
  };

  /**
   * Client-side HTML5 Canvas downscaling before network transfer.
   * Maximizes speed and avoids blowing mobile bandwidth limits on iOS.
   */
  const downscaleImage = (
    imageFile: File,
    maxDim: number,
    quality: number
  ): Promise<{ blob: Blob; dataUrl: string }> => {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => {
        let width = img.width;
        let height = img.height;

        if (width > height) {
          if (width > maxDim) {
            height = Math.round((height * maxDim) / width);
            width = maxDim;
          }
        } else {
          if (height > maxDim) {
            width = Math.round((width * maxDim) / height);
            height = maxDim;
          }
        }

        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          reject(new Error('Canvas context failed'));
          return;
        }

        ctx.drawImage(img, 0, 0, width, height);
        const dataUrl = canvas.toDataURL('image/jpeg', quality);
        canvas.toBlob(
          (blob) => {
            if (blob) resolve({ blob, dataUrl });
            else reject(new Error('Canvas blob conversion failed'));
          },
          'image/jpeg',
          quality
        );
      };
      img.onerror = reject;
      img.src = URL.createObjectURL(imageFile);
    });
  };

  const handleUploadAndAnalyze = async () => {
    if (!selectedFile) return;

    try {
      setProcessing(true);
      setStatusMessage('Compressing on-device using HTML5 Canvas...');

      // 1. Client-side downscaling
      const full = await downscaleImage(selectedFile, 1600, 0.82);
      const thumb = await downscaleImage(selectedFile, 400, 0.70);

      setStatusMessage('Analyzing with Gemini Flash multimodal vision...');
      const base64Data = full.dataUrl.split(',')[1];
      const analysis = await analyzeDocumentWithGemini(base64Data, 'image/jpeg');

      setStatusMessage('Uploading to Firebase Cloud Storage...');
      const docId = `web-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`;
      const { fullUrl } = await uploadImageAndThumbnail(userId, docId, full.blob, thumb.blob);

      setStatusMessage('Indexing in DocuSnap Vault...');
      const newDoc: DocumentEntity = {
        id: docId,
        file_hash: `hash-${docId}`,
        cloud_storage_url: fullUrl,
        raw_ocr_text: analysis.raw_ocr,
        title: analysis.title,
        ai_summary: analysis.summary,
        document_type: analysis.document_type,
        key_entities_json: JSON.stringify(analysis.key_entities),
        searchable_tags_json: JSON.stringify(analysis.searchable_tags),
        date_detected: analysis.key_entities.date || new Date().toISOString().split('T')[0],
        sync_status: 'SYNCED',
        timestamp: Date.now()
      };

      await saveDocument(userId, newDoc);
      onDocumentAdded(newDoc);
      onClose();
    } catch (e) {
      console.error(e);
      setStatusMessage('Error processing document. Please try again.');
    } finally {
      setProcessing(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
      <div className="bg-[#111827] border border-gray-800 rounded-3xl w-full max-w-lg overflow-hidden shadow-2xl animate-in zoom-in-95 duration-200">
        <div className="p-5 border-b border-gray-800 flex items-center justify-between bg-[#161F33]">
          <h2 className="text-base font-semibold text-white flex items-center space-x-2">
            <Sparkles className="w-4 h-4 text-cyan-400" />
            <span>Add Bill or Prescription</span>
          </h2>
          <button
            onClick={onClose}
            disabled={processing}
            className="text-gray-400 hover:text-white p-1 rounded-lg"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 space-y-5">
          {/* File Selector / Camera Trigger */}
          {!previewUrl ? (
            <div className="space-y-3">
              <div
                onClick={() => fileInputRef.current?.click()}
                className="border-2 border-dashed border-gray-700 hover:border-blue-500 rounded-2xl p-8 text-center cursor-pointer transition-colors bg-[#0B0F19]/50 group"
              >
                <Upload className="w-10 h-10 mx-auto text-gray-500 group-hover:text-blue-400 transition-colors mb-2" />
                <p className="text-sm font-medium text-gray-200">
                  Select image from Files / Photos
                </p>
                <p className="text-xs text-gray-500 mt-1">PNG, JPG, WEBP up to 20MB</p>
              </div>

              <div className="text-center text-xs text-gray-600 font-semibold uppercase">Or</div>

              <button
                onClick={() => cameraInputRef.current?.click()}
                className="w-full py-3 bg-[#1F2937] hover:bg-[#253245] border border-gray-700 text-gray-200 rounded-xl text-sm font-medium flex items-center justify-center space-x-2 transition-colors"
              >
                <Camera className="w-4 h-4 text-cyan-400" />
                <span>Snap Camera Photo Now</span>
              </button>

              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                onChange={handleFileChange}
                className="hidden"
              />
              <input
                ref={cameraInputRef}
                type="file"
                accept="image/*"
                capture="environment"
                onChange={handleFileChange}
                className="hidden"
              />
            </div>
          ) : (
            <div className="space-y-4">
              <div className="relative h-60 bg-gray-950 rounded-2xl overflow-hidden border border-gray-800 flex items-center justify-center">
                <img
                  src={previewUrl}
                  alt="Selected Preview"
                  className="w-full h-full object-contain"
                />
                {!processing && (
                  <button
                    onClick={() => {
                      setSelectedFile(null);
                      setPreviewUrl(null);
                    }}
                    className="absolute top-2 right-2 p-1.5 rounded-full bg-black/70 text-gray-300 hover:text-white"
                  >
                    <X className="w-4 h-4" />
                  </button>
                )}
              </div>

              {processing && (
                <div className="bg-[#1F2937] p-3.5 rounded-xl border border-blue-500/30 flex items-center space-x-3 text-xs text-blue-300">
                  <div className="animate-spin">
                    <Sparkles className="w-4 h-4 text-cyan-400" />
                  </div>
                  <span>{statusMessage}</span>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-gray-800 bg-[#161F33] flex items-center justify-end space-x-3">
          <button
            onClick={onClose}
            disabled={processing}
            className="px-4 py-2 text-xs font-medium text-gray-400 hover:text-white rounded-xl"
          >
            Cancel
          </button>
          <button
            onClick={handleUploadAndAnalyze}
            disabled={!selectedFile || processing}
            className="px-5 py-2 bg-gradient-to-r from-blue-600 to-cyan-500 hover:from-blue-500 hover:to-cyan-400 disabled:opacity-50 text-white text-xs font-semibold rounded-xl flex items-center space-x-2 shadow-lg transition-all"
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>{processing ? 'Processing...' : 'Analyze & Save'}</span>
          </button>
        </div>
      </div>
    </div>
  );
};

