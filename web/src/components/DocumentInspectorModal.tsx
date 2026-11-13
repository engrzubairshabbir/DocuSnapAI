import React, { useState } from 'react';
import { 
  X, ZoomIn, ZoomOut, Copy, Check, FileText, Pill, DollarSign, 
  UserCheck, ChevronDown, ChevronUp, Plus, Tag, Sparkles, Trash2 
} from 'lucide-react';
import { DocumentEntity, KeyEntities } from '../types.ts';
import { AdSenseBanner } from './AdSenseBanner.tsx';

interface Props {
  document: DocumentEntity | null;
  onClose: () => void;
  onDelete?: (id: string) => void;
  onUpdate?: (doc: DocumentEntity) => void;
}

export const DocumentInspectorModal: React.FC<Props> = ({ 
  document, 
  onClose, 
  onDelete,
  onUpdate 
}) => {
  const [zoom, setZoom] = useState(1);
  const [copiedKey, setCopiedKey] = useState<string | null>(null);
  const [showRawOcr, setShowRawOcr] = useState(false);
  const [showAddTagModal, setShowAddTagModal] = useState(false);
  const [newTagInput, setNewTagInput] = useState('');

  if (!document) return null;

  let entities: KeyEntities = {
    doctor_name: null,
    medicines: [],
    total_amount: null,
    vendor_or_hospital: null,
    date: ''
  };

  try {
    entities = JSON.parse(document.key_entities_json);
  } catch (e) {
    // Ignore
  }

  // Parse custom search tags
  let customTags: string[] = [];
  try {
    if (document.searchable_tags_json) {
      customTags = JSON.parse(document.searchable_tags_json);
    }
  } catch (e) {
    customTags = [];
  }

  const handleCopy = (text: string, key: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  const handleSaveTags = (updatedTags: string[]) => {
    const unique = Array.from(new Set(updatedTags.map(t => t.trim()))).filter(Boolean);
    const updatedDoc: DocumentEntity = {
      ...document,
      searchable_tags_json: JSON.stringify(unique)
    };
    onUpdate?.(updatedDoc);
  };

  const handleAddTag = () => {
    if (!newTagInput.trim()) return;
    handleSaveTags([...customTags, newTagInput.trim()]);
    setNewTagInput('');
    setShowAddTagModal(false);
  };

  const handleRemoveTag = (tagToRemove: string) => {
    handleSaveTags(customTags.filter(t => t !== tagToRemove));
  };

  const tagPresets = ['Shaheen Bill', 'SAFCO Payment', 'Dr Michael Rx', 'Clinic Slip', 'Utility Bill', 'Warranty Pass'];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-black/85 backdrop-blur-md animate-in fade-in duration-200">
      <div className="bg-[#0C1220]/95 border border-cyan-500/30 rounded-3xl w-full max-w-4xl max-h-[92vh] flex flex-col overflow-hidden shadow-[0_0_50px_rgba(0,245,255,0.2)]">
        {/* Glowing Neon Header */}
        <div className="p-4 sm:p-5 border-b border-cyan-500/20 flex items-center justify-between bg-gradient-to-r from-[#0C1220] via-[#141C33] to-[#0C1220]">
          <div className="flex items-center space-x-3 truncate">
            <span
              className={`text-xs font-black uppercase tracking-wider px-3 py-1 rounded-xl shadow-sm ${
                document.document_type === 'Medical Prescription'
                  ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40'
                  : document.document_type === 'Utility Bill'
                  ? 'bg-blue-500/20 text-blue-400 border border-blue-500/40'
                  : document.document_type === 'Expense Receipt'
                  ? 'bg-amber-500/20 text-amber-400 border border-amber-500/40'
                  : 'bg-purple-500/20 text-purple-400 border border-purple-500/40'
              }`}
            >
              {document.document_type}
            </span>
            <h2 className="text-base sm:text-lg font-bold text-white truncate">
              {document.title}
            </h2>
          </div>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-white p-2 rounded-xl hover:bg-gray-800/80 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body (Split View) */}
        <div className="flex-1 overflow-y-auto grid grid-cols-1 md:grid-cols-2 gap-6 p-4 sm:p-6">
          {/* Zoomable Image Container with Glowing Shimmer Border */}
          <div className="flex flex-col space-y-2">
            <div className="relative h-72 sm:h-96 bg-[#060A12] rounded-2xl border-2 border-cyan-500/40 hover:border-cyan-400 transition-colors shadow-[0_0_30px_rgba(0,245,255,0.15)] overflow-hidden flex items-center justify-center group">
              {document.cloud_storage_url ? (
                <div className="w-full h-full flex items-center justify-center overflow-auto cursor-grab active:cursor-grabbing p-2">
                  <img
                    src={document.cloud_storage_url}
                    alt={document.title}
                    style={{ transform: `scale(${zoom})`, transformOrigin: 'center center' }}
                    className="max-w-full max-h-full object-contain transition-transform duration-200"
                  />
                </div>
              ) : (
                <div className="text-center p-6 text-gray-500">
                  <FileText className="w-16 h-16 mx-auto mb-2 opacity-50 text-cyan-400" />
                  <p className="text-sm">No image preview available</p>
                </div>
              )}

              {/* Zoom Controls Overlay */}
              <div className="absolute bottom-3 right-3 flex items-center space-x-1 bg-black/75 backdrop-blur-md rounded-xl p-1.5 border border-cyan-500/40">
                <button
                  onClick={() => setZoom((z) => Math.min(z + 0.3, 3))}
                  className="p-1.5 text-cyan-300 hover:text-white hover:bg-cyan-950/60 rounded-lg"
                  title="Zoom In"
                >
                  <ZoomIn className="w-4 h-4" />
                </button>
                <span className="text-[11px] font-mono font-bold text-cyan-300 px-1">
                  {Math.round(zoom * 100)}%
                </span>
                <button
                  onClick={() => setZoom((z) => Math.max(z - 0.3, 0.8))}
                  className="p-1.5 text-cyan-300 hover:text-white hover:bg-cyan-950/60 rounded-lg"
                  title="Zoom Out"
                >
                  <ZoomOut className="w-4 h-4" />
                </button>
              </div>
            </div>
            <p className="text-[11px] text-gray-400 text-center font-medium">Use buttons to deep-zoom document</p>
          </div>

          {/* Details & Interactive Sections */}
          <div className="space-y-4">
            {/* AI Summary Card with Neon Glow */}
            <div className="bg-[#10192E] p-4 rounded-2xl border border-cyan-500/30 shadow-md">
              <span className="text-[11px] font-black uppercase tracking-wider text-cyan-400 flex items-center space-x-1 mb-1">
                <Sparkles className="w-3.5 h-3.5 text-cyan-400" />
                <span>AI Summary</span>
              </span>
              <p className="text-sm text-gray-200 leading-relaxed font-normal">
                {document.ai_summary}
              </p>
            </div>

            {/* CUSTOM SEARCH KEYWORDS & REMARKS (With Glowing '+' Button) */}
            <div className="bg-[#12162B] p-4 rounded-2xl border border-purple-500/40 shadow-[0_0_20px_rgba(157,78,221,0.15)] space-y-2.5">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-1.5 text-xs font-bold text-purple-300 uppercase tracking-wider">
                  <Tag className="w-3.5 h-3.5 text-purple-400" />
                  <span>Custom Keywords & Search Tags</span>
                </div>
                <button
                  onClick={() => setShowAddTagModal(true)}
                  className="flex items-center space-x-1 px-2.5 py-1 bg-purple-600/30 hover:bg-purple-600/50 border border-purple-400/80 rounded-xl text-purple-200 text-xs font-bold transition-all shadow-[0_0_12px_rgba(157,78,221,0.4)]"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Add Words</span>
                </button>
              </div>
              <p className="text-[11px] text-gray-400 leading-tight">
                Add manual keywords (e.g. 'Shaheen Bill', 'SAFCO Payment') to match this document instantly in AI search.
              </p>

              {customTags.length === 0 ? (
                <button
                  onClick={() => setShowAddTagModal(true)}
                  className="w-full py-2.5 px-3 border border-dashed border-purple-500/40 rounded-xl text-xs text-purple-300 hover:text-white hover:border-purple-400 flex items-center justify-center space-x-1.5 transition-colors"
                >
                  <Plus className="w-3.5 h-3.5 text-purple-400" />
                  <span>Click '+' to add your custom search words</span>
                </button>
              ) : (
                <div className="flex flex-wrap gap-2 pt-1">
                  {customTags.map((tag, idx) => (
                    <span
                      key={idx}
                      className="inline-flex items-center space-x-1.5 px-2.5 py-1 bg-purple-900/30 border border-purple-500/50 text-purple-200 text-xs font-semibold rounded-xl shadow-sm"
                    >
                      <span>{tag}</span>
                      <button
                        onClick={() => handleRemoveTag(tag)}
                        className="text-pink-400 hover:text-pink-200 p-0.5 rounded-md transition-colors"
                        title="Remove tag"
                      >
                        <X className="w-3 h-3" />
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>

            {/* Extracted Entities */}
            <div className="space-y-2">
              <h3 className="text-xs font-bold uppercase tracking-wider text-gray-400">
                Parsed Key Entities (Click to Copy)
              </h3>

              {entities.total_amount != null && (
                <div
                  onClick={() => handleCopy(`${entities.total_amount}`, 'amount')}
                  className="flex items-center justify-between p-3 bg-[#131D33] hover:bg-[#1A2644] border border-amber-500/30 rounded-xl cursor-pointer transition-colors group"
                >
                  <div className="flex items-center space-x-3">
                    <DollarSign className="w-4 h-4 text-amber-400" />
                    <div>
                      <div className="text-[10px] text-gray-400">Total Amount</div>
                      <div className="text-sm font-bold text-amber-400">
                        PKR {entities.total_amount.toLocaleString()}
                      </div>
                    </div>
                  </div>
                  {copiedKey === 'amount' ? (
                    <Check className="w-4 h-4 text-emerald-400" />
                  ) : (
                    <Copy className="w-4 h-4 text-gray-500 group-hover:text-gray-300" />
                  )}
                </div>
              )}

              {entities.doctor_name && (
                <div
                  onClick={() => handleCopy(entities.doctor_name!, 'doctor')}
                  className="flex items-center justify-between p-3 bg-[#131D33] hover:bg-[#1A2644] border border-cyan-500/30 rounded-xl cursor-pointer transition-colors group"
                >
                  <div className="flex items-center space-x-3">
                    <UserCheck className="w-4 h-4 text-cyan-400" />
                    <div>
                      <div className="text-[10px] text-gray-400">Doctor</div>
                      <div className="text-sm font-semibold text-gray-200">{entities.doctor_name}</div>
                    </div>
                  </div>
                  {copiedKey === 'doctor' ? (
                    <Check className="w-4 h-4 text-emerald-400" />
                  ) : (
                    <Copy className="w-4 h-4 text-gray-500 group-hover:text-gray-300" />
                  )}
                </div>
              )}

              {entities.medicines && entities.medicines.length > 0 && (
                <div className="space-y-1.5 pt-1">
                  <div className="text-[11px] font-semibold text-emerald-400 flex items-center space-x-1">
                    <Pill className="w-3.5 h-3.5" />
                    <span>Prescribed Medicines:</span>
                  </div>
                  <div className="grid grid-cols-1 gap-1.5">
                    {entities.medicines.map((med, idx) => (
                      <div
                        key={idx}
                        onClick={() => handleCopy(med, `med-${idx}`)}
                        className="flex items-center justify-between p-2.5 bg-emerald-950/25 hover:bg-emerald-950/50 border border-emerald-500/30 rounded-xl cursor-pointer transition-colors text-xs text-emerald-300 font-medium"
                      >
                        <span>{med}</span>
                        {copiedKey === `med-${idx}` ? (
                          <Check className="w-3.5 h-3.5 text-emerald-400" />
                        ) : (
                          <Copy className="w-3.5 h-3.5 text-emerald-600" />
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Raw OCR Text Accordion with Glowing '+' Button opposite to Header */}
            <div className="border border-cyan-500/30 rounded-2xl overflow-hidden bg-[#10192E]">
              <div className="w-full p-3.5 flex items-center justify-between bg-gradient-to-r from-[#10192E] to-[#141C33]">
                <button
                  onClick={() => setShowRawOcr(!showRawOcr)}
                  className="flex items-center space-x-2 text-left text-xs font-bold text-gray-200 hover:text-cyan-400 transition-colors"
                >
                  <span>Raw OCR Extracted Text</span>
                  {showRawOcr ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                </button>

                {/* Glowing Neon '+' Button on opposite side */}
                <button
                  onClick={() => setShowAddTagModal(true)}
                  className="p-1.5 bg-cyan-500/20 hover:bg-cyan-500/40 border border-cyan-400/80 rounded-xl text-cyan-300 hover:text-white transition-all shadow-[0_0_12px_rgba(0,245,255,0.4)]"
                  title="Add custom search keywords"
                >
                  <Plus className="w-4 h-4" />
                </button>
              </div>

              {showRawOcr && (
                <div className="p-3.5 border-t border-cyan-500/20 bg-[#060A12] text-xs font-mono text-gray-400 whitespace-pre-wrap max-h-48 overflow-y-auto">
                  {document.raw_ocr_text || 'No text detected.'}
                </div>
              )}
            </div>

            {/* AdSense In-Feed Ad Unit */}
            <AdSenseBanner slot="1062617259" format="auto" />
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-cyan-500/20 bg-[#0C1220] flex items-center justify-between">
          <span className="text-xs text-gray-400">
            Detected: {document.date_detected}
          </span>
          <div className="flex items-center space-x-2">
            {onDelete && (
              <button
                onClick={() => {
                  onDelete(document.id);
                  onClose();
                }}
                className="px-4 py-2 bg-red-950/40 hover:bg-red-900/60 text-red-400 border border-red-800/60 text-xs font-bold rounded-xl transition-colors"
              >
                Delete
              </button>
            )}
            <button
              onClick={onClose}
              className="px-5 py-2 bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-black font-bold text-xs rounded-xl shadow-[0_0_15px_rgba(0,245,255,0.3)] transition-all"
            >
              Done
            </button>
          </div>
        </div>
      </div>

      {/* MODAL: ADD CUSTOM KEYWORD / TAG */}
      {showAddTagModal && (
        <div className="fixed inset-0 z-60 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-150">
          <div className="bg-[#0C1220] border-2 border-purple-500/60 rounded-3xl p-5 w-full max-w-md shadow-[0_0_40px_rgba(157,78,221,0.3)]">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center space-x-2 text-purple-300 font-bold text-sm">
                <Tag className="w-4 h-4 text-purple-400" />
                <span>Add Custom Search Words</span>
              </div>
              <button
                onClick={() => setShowAddTagModal(false)}
                className="text-gray-400 hover:text-white p-1"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <p className="text-xs text-gray-400 mb-3">
              Type custom keywords (e.g. Shaheen Bill, SAFCO Payment) to easily find this receipt or bill later in search:
            </p>

            <input
              type="text"
              value={newTagInput}
              onChange={(e) => setNewTagInput(e.target.value)}
              placeholder="e.g. Shaheen Bill, SAFCO Payment..."
              className="w-full px-3.5 py-2.5 bg-[#141C33] border border-cyan-500/40 focus:border-cyan-400 rounded-xl text-sm text-white focus:outline-none mb-3 placeholder-gray-500"
              autoFocus
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleAddTag();
              }}
            />

            <div className="text-[11px] font-semibold text-gray-400 mb-2">
              Quick Suggestions (Click to insert):
            </div>
            <div className="flex flex-wrap gap-1.5 mb-5">
              {tagPresets.map((preset, idx) => (
                <button
                  key={idx}
                  type="button"
                  onClick={() => setNewTagInput(preset)}
                  className="px-2.5 py-1 bg-purple-950/40 hover:bg-purple-900/60 border border-purple-500/40 text-purple-300 rounded-lg text-[11px] transition-colors"
                >
                  + {preset}
                </button>
              ))}
            </div>

            <div className="flex items-center justify-end space-x-2">
              <button
                type="button"
                onClick={() => setShowAddTagModal(false)}
                className="px-4 py-2 text-xs text-gray-400 hover:text-white"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleAddTag}
                className="px-5 py-2 bg-gradient-to-r from-purple-600 to-pink-600 hover:from-purple-500 hover:to-pink-500 text-white font-bold text-xs rounded-xl shadow-[0_0_15px_rgba(157,78,221,0.4)] transition-all"
              >
                Add & Save
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
