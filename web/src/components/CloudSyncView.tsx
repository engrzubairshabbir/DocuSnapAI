import React from 'react';
import { Cloud, Smartphone, Monitor, ShieldCheck, CheckCircle2, RefreshCw, LogIn } from 'lucide-react';
import { loginWithGoogle } from '../firebase.ts';

interface Props {
  userId: string;
  isAnonymous: boolean;
  userEmail?: string | null;
  totalDocs: number;
}

export const CloudSyncView: React.FC<Props> = ({ userId, isAnonymous, userEmail, totalDocs }) => {
  return (
    <div className="max-w-2xl mx-auto space-y-6">
      {/* Overview Card */}
      <div className="bg-[#111827] border border-gray-800 rounded-3xl p-6 shadow-xl space-y-5">
        <div className="flex items-center space-x-3.5">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-purple-500 to-blue-500 flex items-center justify-center shadow-lg shadow-purple-500/20">
            <Cloud className="w-6 h-6 text-white" />
          </div>
          <div>
            <h2 className="text-lg font-bold text-white">Dual-Ecosystem Cloud Sync</h2>
            <p className="text-xs text-gray-400">Firebase Firestore + Storage Real-Time Mesh</p>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3 pt-2">
          <div className="bg-[#1F2937]/70 p-4 rounded-2xl border border-gray-700/60">
            <span className="text-[11px] text-gray-400 block mb-1">Indexed Vault Items</span>
            <span className="text-2xl font-bold text-white">{totalDocs}</span>
          </div>

          <div className="bg-[#1F2937]/70 p-4 rounded-2xl border border-gray-700/60">
            <span className="text-[11px] text-gray-400 block mb-1">Auth Identity</span>
            <span className="text-xs font-semibold text-emerald-400 truncate block">
              {isAnonymous ? `Anonymous (${userId.slice(0, 8)}...)` : userEmail || 'Authenticated'}
            </span>
          </div>
        </div>

        {isAnonymous && (
          <div className="bg-[#161F33] p-4 rounded-2xl border border-blue-500/30 flex flex-col sm:flex-row items-center justify-between gap-3">
            <div className="text-xs text-gray-300">
              <span className="font-semibold text-white block">Sign in with Google to Link Devices</span>
              Keeps Android, iPhone Safari PWA, and Desktop in sync automatically.
            </div>
            <button
              onClick={() => loginWithGoogle()}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-xl text-xs font-semibold flex items-center space-x-1.5 whitespace-nowrap transition-colors"
            >
              <LogIn className="w-3.5 h-3.5" />
              <span>Link Google</span>
            </button>
          </div>
        )}
      </div>

      {/* Cross-Platform Ecosystem Diagram */}
      <div className="bg-[#111827] border border-gray-800 rounded-3xl p-6 space-y-4">
        <h3 className="text-sm font-semibold text-white flex items-center space-x-2">
          <ShieldCheck className="w-4 h-4 text-emerald-400" />
          <span>Ecosystem Synchronization Architecture</span>
        </h3>

        <div className="space-y-3 text-xs text-gray-300 leading-relaxed">
          <div className="flex items-start space-x-3 p-3 bg-[#1F2937]/40 rounded-xl border border-gray-700/40">
            <Smartphone className="w-5 h-5 text-blue-400 flex-shrink-0 mt-0.5" />
            <div>
              <strong className="text-white">Native Android App:</strong> Runs zero-click background gallery observer via WorkManager, extracts text with local ML Kit, reasons with Gemini, and writes to SQLite FTS5 + Firestore.
            </div>
          </div>

          <div className="flex items-start space-x-3 p-3 bg-[#1F2937]/40 rounded-xl border border-gray-700/40">
            <Monitor className="w-5 h-5 text-cyan-400 flex-shrink-0 mt-0.5" />
            <div>
              <strong className="text-white">Web / iOS PWA:</strong> Listens via real-time Firestore snapshots. Documents captured on your Android phone appear on your iPhone Safari / Desktop browser immediately.
            </div>
          </div>

          <div className="flex items-start space-x-3 p-3 bg-[#1F2937]/40 rounded-xl border border-gray-700/40">
            <CheckCircle2 className="w-5 h-5 text-emerald-400 flex-shrink-0 mt-0.5" />
            <div>
              <strong className="text-white">Encrypted &amp; Private:</strong> Firestore security rules guarantee strictly authorized per-user collections at <code className="text-blue-300 bg-gray-900 px-1 py-0.5 rounded">users/{'{userId}'}/documents</code>.
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

