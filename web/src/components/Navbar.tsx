import React from 'react';
import { Sparkles, Folder, Plus, Smartphone, Cloud, User as UserIcon } from 'lucide-react';

interface Props {
  activeTab: 'search' | 'vault' | 'sync';
  setActiveTab: (tab: 'search' | 'vault' | 'sync') => void;
  onOpenUpload: () => void;
  userId: string;
}

export const Navbar: React.FC<Props> = ({ activeTab, setActiveTab, onOpenUpload, userId }) => {
  return (
    <header className="sticky top-0 z-40 bg-[#0B0F19]/90 backdrop-blur-md border-b border-gray-800">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
        {/* Brand */}
        <div className="flex items-center space-x-3 cursor-pointer" onClick={() => setActiveTab('search')}>
          <img
            src="./logo.png"
            alt="DocuSnap AI Logo"
            className="w-10 h-10 rounded-xl object-cover shadow-lg shadow-cyan-500/20 border border-gray-700/50"
          />
          <div>
            <div className="flex items-center space-x-1.5">
              <span className="font-bold text-white text-base tracking-tight">DocuSnap AI</span>
              <span className="text-[10px] px-1.5 py-0.2 bg-blue-500/20 text-blue-400 font-bold rounded">PWA</span>
            </div>
            <div className="text-[11px] text-gray-400 font-normal">Bill & Rx Finder</div>
          </div>
        </div>

        {/* Desktop Tabs */}
        <div className="hidden md:flex items-center space-x-1 bg-[#111827] p-1 rounded-xl border border-gray-800">
          <button
            onClick={() => setActiveTab('search')}
            className={`px-4 py-1.5 rounded-lg text-xs font-semibold flex items-center space-x-1.5 transition-all ${
              activeTab === 'search'
                ? 'bg-blue-600 text-white shadow-sm'
                : 'text-gray-400 hover:text-gray-200'
            }`}
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>Ask AI</span>
          </button>
          <button
            onClick={() => setActiveTab('vault')}
            className={`px-4 py-1.5 rounded-lg text-xs font-semibold flex items-center space-x-1.5 transition-all ${
              activeTab === 'vault'
                ? 'bg-blue-600 text-white shadow-sm'
                : 'text-gray-400 hover:text-gray-200'
            }`}
          >
            <Folder className="w-3.5 h-3.5" />
            <span>Smart Vault</span>
          </button>
          <button
            onClick={() => setActiveTab('sync')}
            className={`px-4 py-1.5 rounded-lg text-xs font-semibold flex items-center space-x-1.5 transition-all ${
              activeTab === 'sync'
                ? 'bg-blue-600 text-white shadow-sm'
                : 'text-gray-400 hover:text-gray-200'
            }`}
          >
            <Cloud className="w-3.5 h-3.5" />
            <span>Cloud Sync</span>
          </button>
        </div>

        {/* Action Button */}
        <div className="flex items-center space-x-2.5">
          <button
            onClick={onOpenUpload}
            className="px-3.5 py-2 bg-gradient-to-r from-blue-600 to-cyan-500 hover:from-blue-500 hover:to-cyan-400 text-white text-xs font-semibold rounded-xl flex items-center space-x-1.5 shadow-lg shadow-blue-500/20 transition-all"
          >
            <Plus className="w-4 h-4" />
            <span className="hidden sm:inline">Add Document</span>
            <span className="sm:hidden">Add</span>
          </button>
        </div>
      </div>
    </header>
  );
};

