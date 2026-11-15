import React, { useState, useMemo } from 'react';
import { FileText, Filter, Pill, DollarSign, Calendar, Eye } from 'lucide-react';
import { DocumentEntity, KeyEntities } from '../types.ts';
import { AdSenseBanner } from './AdSenseBanner.tsx';

interface Props {
  documents: DocumentEntity[];
  onSelectDocument: (doc: DocumentEntity) => void;
}

export const SmartVault: React.FC<Props> = ({ documents, onSelectDocument }) => {
  const [selectedCat, setSelectedCat] = useState<string>('All');
  const [searchTerm, setSearchTerm] = useState<string>('');

  const categories = ['All', 'Medical', 'Receipts', 'Utility', 'Bank'];

  const filteredDocs = useMemo(() => {
    return documents.filter((doc) => {
      const matchCat =
        selectedCat === 'All' ||
        (selectedCat === 'Medical' && doc.document_type === 'Medical Prescription') ||
        (selectedCat === 'Receipts' && doc.document_type === 'Expense Receipt') ||
        (selectedCat === 'Utility' && doc.document_type === 'Utility Bill') ||
        (selectedCat === 'Bank' && doc.document_type === 'Bank Slip');

      const matchSearch =
        !searchTerm ||
        doc.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
        doc.ai_summary.toLowerCase().includes(searchTerm.toLowerCase()) ||
        doc.key_entities_json.toLowerCase().includes(searchTerm.toLowerCase());

      return matchCat && matchSearch;
    });
  }, [documents, selectedCat, searchTerm]);

  return (
    <div className="space-y-5">
      {/* Search & Category Filter Row */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div className="flex items-center space-x-1.5 overflow-x-auto pb-1 scrollbar-none">
          {categories.map((cat) => {
            const active = selectedCat === cat;
            return (
              <button
                key={cat}
                onClick={() => setSelectedCat(cat)}
                className={`px-3.5 py-1.5 rounded-xl text-xs font-medium transition-all whitespace-nowrap ${
                  active
                    ? 'bg-blue-600 text-white shadow-md shadow-blue-500/20'
                    : 'bg-[#1F2937]/70 text-gray-400 hover:text-gray-200 hover:bg-[#1F2937]'
                }`}
              >
                {cat}
              </button>
            );
          })}
        </div>

        <input
          type="text"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          placeholder="Filter by title, medicine or amount..."
          className="px-3.5 py-1.5 bg-[#111827] border border-gray-700/70 rounded-xl text-xs text-gray-200 placeholder-gray-500 focus:outline-none focus:border-blue-500 w-full md:w-64"
        />
      </div>

      {/* Grid of Documents */}
      {filteredDocs.length === 0 ? (
        <div className="text-center py-16 bg-[#111827]/40 border border-gray-800 rounded-2xl">
          <FileText className="w-12 h-12 mx-auto text-gray-600 mb-3" />
          <p className="text-gray-400 font-medium text-sm">No documents found in this category</p>
          <p className="text-gray-600 text-xs mt-1">Upload a photo or sync from your Android device</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredDocs.map((doc) => {
            let entities: KeyEntities = {
              doctor_name: null,
              medicines: [],
              total_amount: null,
              vendor_or_hospital: null,
              date: ''
            };
            try {
              entities = JSON.parse(doc.key_entities_json);
            } catch (e) {
              // Ignore
            }

            const isPdfDoc = doc.document_type === 'PDF Document' ||
              doc.title?.toLowerCase().endsWith('.pdf') ||
              doc.cloud_storage_url?.toLowerCase().includes('.pdf') ||
              doc.cloud_storage_url?.startsWith('data:application/pdf');

            return (
              <div
                key={doc.id}
                onClick={() => onSelectDocument(doc)}
                className="bg-[#111827] border border-gray-800 hover:border-blue-500/50 rounded-2xl overflow-hidden group cursor-pointer transition-all hover:shadow-xl flex flex-col"
              >
                {/* Image / PDF Banner */}
                <div className="relative h-44 bg-gray-900 overflow-hidden">
                  {isPdfDoc && doc.cloud_storage_url?.startsWith('data:application/pdf') ? (
                    <div className="w-full h-full flex flex-col items-center justify-center bg-gradient-to-br from-red-950/40 via-[#111827] to-red-950/20 p-4 text-center">
                      <div className="w-12 h-12 rounded-xl bg-red-500/20 border border-red-500/40 flex items-center justify-center mb-2">
                        <FileText className="w-6 h-6 text-red-400" />
                      </div>
                      <span className="text-xs font-bold text-gray-200 line-clamp-1">{doc.title}</span>
                      <span className="text-[10px] text-red-400 font-mono mt-0.5">PDF Document</span>
                    </div>
                  ) : doc.cloud_storage_url ? (
                    <img
                      src={doc.cloud_storage_url}
                      alt={doc.title}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                    />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center bg-gray-800/80">
                      <FileText className="w-10 h-10 text-gray-600" />
                    </div>
                  )}

                  <div className="absolute top-2.5 left-2.5 flex items-center space-x-1">
                    {isPdfDoc && (
                      <span className="text-[10px] font-black px-1.5 py-0.5 rounded-md bg-red-600 text-white shadow-sm">
                        PDF
                      </span>
                    )}
                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded-md backdrop-blur-md ${
                        doc.document_type === 'Medical Prescription'
                          ? 'bg-emerald-500/80 text-white'
                          : doc.document_type === 'Utility Bill'
                          ? 'bg-blue-500/80 text-white'
                          : doc.document_type === 'Expense Receipt'
                          ? 'bg-amber-500/80 text-white'
                          : 'bg-gray-700/80 text-white'
                      }`}
                    >
                      {doc.document_type}
                    </span>
                  </div>

                  <div className="absolute top-2.5 right-2.5 p-1 rounded-full bg-black/50 text-emerald-400">
                    <span title="Synced with Cloud" className="text-xs">✓</span>
                  </div>
                </div>

                {/* Details Section */}
                <div className="p-4 flex-1 flex flex-col justify-between space-y-3">
                  <div>
                    <h3 className="font-semibold text-gray-100 text-sm group-hover:text-blue-400 transition-colors line-clamp-1">
                      {doc.title}
                    </h3>
                    <p className="text-xs text-gray-400 line-clamp-2 mt-1 leading-relaxed">
                      {doc.ai_summary}
                    </p>
                  </div>

                  {/* Highlights Bar */}
                  <div className="pt-2 border-t border-gray-800/80 flex items-center justify-between text-xs">
                    {entities.total_amount ? (
                      <span className="font-bold text-amber-400 flex items-center space-x-1">
                        <span>Total: {entities.total_amount.toLocaleString()}</span>
                      </span>
                    ) : entities.medicines.length > 0 ? (
                      <span className="font-medium text-emerald-400 flex items-center space-x-1 truncate max-w-[180px]">
                        <Pill className="w-3.5 h-3.5 inline mr-1 flex-shrink-0" />
                        <span className="truncate">{entities.medicines[0]}</span>
                      </span>
                    ) : (
                      <span className="text-gray-500 flex items-center space-x-1">
                        <Calendar className="w-3.5 h-3.5" />
                        <span>{doc.date_detected}</span>
                      </span>
                    )}

                    <span className="text-blue-400 font-medium group-hover:underline flex items-center space-x-1">
                      <Eye className="w-3 h-3" />
                      <span>Inspect</span>
                    </span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* AdSense Responsive Display Unit */}
      <AdSenseBanner slot="1062617259" format="auto" />
    </div>
  );
};

