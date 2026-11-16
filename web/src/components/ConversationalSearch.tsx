import React, { useState } from 'react';
import { Search, Sparkles, FileText, ArrowRight, CheckCircle2, Clock } from 'lucide-react';
import { DocumentEntity } from '../types.ts';
import { answerQueryWithGemini } from '../gemini.ts';
import { AdSenseBanner } from './AdSenseBanner.tsx';

interface Props {
  documents: DocumentEntity[];
  onSelectDocument: (doc: DocumentEntity) => void;
}

export const ConversationalSearch: React.FC<Props> = ({ documents, onSelectDocument }) => {
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [aiAnswer, setAiAnswer] = useState<string | null>(null);
  const [matchedDocs, setMatchedDocs] = useState<DocumentEntity[]>([]);

  const exampleQueries = [
    "What medicine was prescribed for stomach relief?",
    "東京マーケットの領収書の合計金額はいくらですか？",
    "How much was last month's electricity bill?",
    "Show total on latest grocery receipt"
  ];

  const handleSearch = async (searchQuery: string) => {
    if (!searchQuery.trim()) return;
    setLoading(true);
    setAiAnswer(null);

    // Filter documents locally across Latin and multi-script tokens
    const qLower = searchQuery.toLowerCase().trim();
    const tokens = qLower.split(/[\s、。?!！？]+/).filter((t) => t.length >= 2);
    const filtered = documents.filter((doc) => {
      const haystack = `${doc.title}\n${doc.raw_ocr_text}\n${doc.ai_summary}\n${doc.searchable_tags_json}\n${doc.key_entities_json}`.toLowerCase();
      if (haystack.includes(qLower)) return true;
      return tokens.some((tok) => haystack.includes(tok));
    });

    const candidates = filtered.length > 0 ? filtered : documents.slice(0, 6);
    setMatchedDocs(candidates);

    try {
      const answer = await answerQueryWithGemini(searchQuery, candidates);
      setAiAnswer(answer);
    } catch (e) {
      console.error(e);
      setAiAnswer("Could not retrieve AI response. Check the matched documents below.");
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      handleSearch(query);
    }
  };

  return (
    <div className="space-y-6">
      {/* Remote Phone Vault Connected Banner */}
      <div className="bg-gradient-to-r from-blue-900/30 via-cyan-950/20 to-purple-900/30 border border-cyan-500/30 rounded-2xl p-3.5 flex items-center justify-between shadow-[0_0_20px_rgba(0,245,255,0.1)]">
        <div className="flex items-center space-x-3">
          <div className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse shadow-[0_0_8px_rgba(52,211,153,0.8)]" />
          <div>
            <span className="text-xs font-bold text-white flex items-center space-x-1.5">
              <span>Remote Phone Smart Vault Connected</span>
              <span className="text-[10px] bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 px-1.5 py-0.5 rounded-md font-mono">LIVE SYNC</span>
            </span>
            <p className="text-[11px] text-gray-400">Search all OCR text, prescriptions & bills indexed on your phone from any browser.</p>
          </div>
        </div>
      </div>

      {/* Search Input Box */}
      <div className="relative">
        <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-blue-400">
          <Search className="w-5 h-5" />
        </div>
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Ask anything in any language: 'Prescription for stomach relief?' or '領収書の合計は？'..."
          className="w-full pl-11 pr-28 py-3.5 bg-[#111827] border border-gray-700/80 rounded-2xl text-gray-100 placeholder-gray-500 focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 text-sm md:text-base shadow-lg transition-all"
        />
        <button
          onClick={() => handleSearch(query)}
          disabled={loading || !query.trim()}
          className="absolute right-2 top-2 bottom-2 px-4 bg-gradient-to-r from-blue-600 to-cyan-500 hover:from-blue-500 hover:to-cyan-400 disabled:opacity-50 text-white rounded-xl text-xs font-semibold flex items-center space-x-1.5 transition-all shadow-md"
        >
          <Sparkles className="w-3.5 h-3.5" />
          <span>Ask AI</span>
        </button>
      </div>

      {/* Suggested prompts chips */}
      <div className="flex items-center space-x-2 overflow-x-auto pb-1 scrollbar-none">
        <span className="text-xs text-gray-500 font-medium whitespace-nowrap">Try:</span>
        {exampleQueries.map((ex, idx) => (
          <button
            key={idx}
            onClick={() => {
              setQuery(ex);
              handleSearch(ex);
            }}
            className="text-xs text-gray-300 hover:text-white bg-[#1F2937]/70 hover:bg-[#1F2937] border border-gray-700/60 px-3 py-1.5 rounded-full whitespace-nowrap transition-colors"
          >
            {ex}
          </button>
        ))}
      </div>

      {/* Loading state */}
      {loading && (
        <div className="bg-[#111827] border border-gray-800 rounded-2xl p-6 text-center space-y-3">
          <div className="inline-block animate-spin text-cyan-400">
            <Sparkles className="w-7 h-7" />
          </div>
          <p className="text-sm text-gray-300">Searching your vault with Gemini Reasoning...</p>
        </div>
      )}

      {/* AI Conversational Answer Box */}
      {aiAnswer && !loading && (
        <div className="relative overflow-hidden bg-gradient-to-br from-[#131D33] to-[#0E1526] border border-blue-500/40 rounded-2xl p-5 shadow-xl">
          <div className="absolute top-0 right-0 w-48 h-48 bg-cyan-500/10 rounded-full blur-3xl pointer-events-none" />
          <div className="flex items-center space-x-2 text-cyan-400 text-xs font-bold uppercase tracking-wider mb-2">
            <Sparkles className="w-4 h-4" />
            <span>AI Answer</span>
          </div>
          <div className="text-sm md:text-base text-gray-100 leading-relaxed whitespace-pre-line font-normal">
            {aiAnswer}
          </div>
        </div>
      )}

      {/* Matched Documents */}
      {matchedDocs.length > 0 && (
        <div className="space-y-3 pt-2">
          <h3 className="text-sm font-semibold text-gray-400 flex items-center justify-between">
            <span>Relevant Documents ({matchedDocs.length})</span>
            <span className="text-xs text-gray-500">Tap card to inspect details</span>
          </h3>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {matchedDocs.map((doc) => (
              <div
                key={doc.id}
                onClick={() => onSelectDocument(doc)}
                className="bg-[#111827] hover:bg-[#161F33] border border-gray-800 hover:border-blue-500/40 rounded-xl p-3.5 cursor-pointer transition-all flex space-x-3.5 items-center group"
              >
                <div className="w-16 h-16 rounded-lg bg-gray-800 flex-shrink-0 overflow-hidden flex items-center justify-center border border-gray-700/50">
                  {doc.cloud_storage_url ? (
                    <img
                      src={doc.cloud_storage_url}
                      alt={doc.title}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                    />
                  ) : (
                    <FileText className="w-7 h-7 text-blue-400" />
                  )}
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] font-semibold px-2 py-0.5 rounded-md bg-blue-500/15 text-blue-400">
                      {doc.document_type}
                    </span>
                    <span className="text-[11px] text-gray-500 flex items-center space-x-1">
                      <Clock className="w-3 h-3" />
                      <span>{doc.date_detected}</span>
                    </span>
                  </div>
                  <h4 className="text-sm font-medium text-gray-100 truncate mt-1">
                    {doc.title}
                  </h4>
                  <p className="text-xs text-gray-400 line-clamp-1 mt-0.5">
                    {doc.ai_summary}
                  </p>
                </div>

                <ArrowRight className="w-4 h-4 text-gray-600 group-hover:text-blue-400 transition-colors flex-shrink-0" />
              </div>
            ))}
          </div>
        </div>
      )}

      {/* AdSense Responsive Unit for Web & PWA */}
      <AdSenseBanner slot="1062617259" format="auto" />
    </div>
  );
};

