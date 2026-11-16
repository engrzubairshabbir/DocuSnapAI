import React from 'react';
import { Sparkles, Folder, Plus, Smartphone, Cloud, User as UserIcon, LogOut } from 'lucide-react';

interface Props {
  activeTab: 'search' | 'vault' | 'sync';
  setActiveTab: (tab: 'search' | 'vault' | 'sync') => void;
  onOpenUpload: () => void;
  userId: string;
  isAnonymous: boolean;
  userEmail: string | null;
  userPhotoUrl?: string | null;
  onLoginGoogle: () => void;
  onLogout: () => void;
}

export const Navbar: React.FC<Props> = ({ 
  activeTab, 
  setActiveTab, 
  onOpenUpload, 
  userId,
  isAnonymous,
  userEmail,
  userPhotoUrl,
  onLoginGoogle,
  onLogout
}) => {
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

        {/* Action Button & Google Sign-In */}
        <div className="flex items-center space-x-2.5">
          {/* Sign in with Google / Logged-in badge */}
          {isAnonymous || !userEmail ? (
            <button
              onClick={onLoginGoogle}
              className="px-3 py-2 bg-white hover:bg-gray-100 text-gray-800 text-xs font-semibold rounded-xl flex items-center space-x-2 shadow-md transition-all border border-gray-200"
              title="Sign in with Google to sync all bills & Rx with your Android phone"
            >
              <svg className="w-4 h-4 flex-shrink-0" viewBox="0 0 24 24">
                <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
                <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
                <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
              </svg>
              <span className="hidden sm:inline">Sign in with Google</span>
              <span className="sm:hidden">Sign In</span>
            </button>
          ) : (
            <div className="flex items-center space-x-2 bg-[#1F2937]/90 border border-gray-700/80 px-2.5 py-1.5 rounded-xl">
              {userPhotoUrl ? (
                <img src={userPhotoUrl} alt="Avatar" className="w-6 h-6 rounded-full border border-blue-400" />
              ) : (
                <div className="w-6 h-6 rounded-full bg-blue-600 text-[11px] font-bold text-white flex items-center justify-center">
                  {userEmail[0].toUpperCase()}
                </div>
              )}
              <span className="text-xs text-gray-200 font-medium hidden md:inline max-w-[120px] truncate">
                {userEmail}
              </span>
              <button
                onClick={onLogout}
                className="text-[11px] text-gray-400 hover:text-red-400 transition-colors p-1"
                title="Sign Out"
              >
                <LogOut className="w-3.5 h-3.5" />
              </button>
            </div>
          )}

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
