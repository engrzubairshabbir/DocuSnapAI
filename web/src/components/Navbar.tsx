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
          {/* Google Play Store Redirect Button */}
          <a
            href="https://play.google.com/store/apps/details?id=com.docusnap.ai"
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center space-x-2 px-3 py-1.5 bg-[#111827] hover:bg-[#1a2333] border border-gray-700/80 hover:border-cyan-500/60 rounded-xl transition-all shadow-sm group active:scale-95"
            title="Download DocuSnap AI from Google Play Store"
          >
            <svg className="w-4 h-4 flex-shrink-0" viewBox="0 0 24 24" fill="none">
              <path d="M3.6 1.8L13.8 12 3.6 22.2C3.2 21.8 3 21.2 3 20.4V3.6C3 2.8 3.2 2.2 3.6 1.8Z" fill="#2196F3"/>
              <path d="M17.2 8.6L13.8 12L17.2 15.4L21.3 13.1C22.2 12.6 22.2 11.4 21.3 10.9L17.2 8.6Z" fill="#FFC107"/>
              <path d="M3.6 1.8C4.1 1.4 4.8 1.4 5.5 1.8L17.2 8.6L13.8 12L3.6 1.8Z" fill="#4CAF50"/>
              <path d="M13.8 12L17.2 15.4L5.5 22.2C4.8 22.6 4.1 22.6 3.6 22.2L13.8 12Z" fill="#F44336"/>
            </svg>
            <div className="flex flex-col text-left leading-none">
              <span className="text-[8px] text-gray-400 font-bold uppercase tracking-wider">GET IT ON</span>
              <span className="text-xs font-bold text-white group-hover:text-cyan-400 transition-colors">Google Play</span>
            </div>
          </a>

          {/* Sign in with Google / Logged-in profile badge */}
          {isAnonymous || !userEmail ? (
            <button
              onClick={onLoginGoogle}
              className="px-3.5 py-2 bg-white hover:bg-gray-100 text-gray-800 text-xs font-semibold rounded-xl flex items-center space-x-2 shadow-md transition-all border border-gray-200 active:scale-95"
              title="Sign in with Google to sync all bills & Rx across phone & web"
            >
              <svg className="w-4 h-4 flex-shrink-0" viewBox="0 0 24 24">
                <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
                <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
                <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
              </svg>
              <span className="hidden sm:inline font-medium">Sign in with Google</span>
              <span className="sm:hidden font-medium">Sign In</span>
            </button>
          ) : (
            <div 
              className="group relative flex items-center space-x-2 bg-[#172033] hover:bg-[#1E293B] border border-blue-500/30 px-3 py-1.5 rounded-xl transition-all cursor-pointer shadow-sm"
              title={`Logged in as: ${userEmail}`}
            >
              {/* User Avatar with Green Online Sync Dot */}
              <div className="relative">
                {userPhotoUrl ? (
                  <img 
                    src={userPhotoUrl} 
                    alt="Google Profile" 
                    className="w-7 h-7 rounded-full object-cover border-2 border-emerald-400 shadow-sm"
                    referrerPolicy="no-referrer"
                  />
                ) : (
                  <div className="w-7 h-7 rounded-full bg-gradient-to-tr from-blue-600 to-indigo-500 text-xs font-bold text-white flex items-center justify-center border-2 border-emerald-400">
                    {userEmail[0].toUpperCase()}
                  </div>
                )}
                <span className="absolute bottom-0 right-0 w-2 h-2 bg-emerald-400 border border-[#0B0F19] rounded-full" />
              </div>

              {/* Truncated Email Display */}
              <div className="flex flex-col text-left max-w-[130px] sm:max-w-[160px]">
                <span className="text-[10px] text-emerald-400 font-semibold tracking-wide uppercase leading-tight">
                  Google Sync
                </span>
                <span className="text-xs text-gray-200 font-medium truncate">
                  {userEmail}
                </span>
              </div>

              {/* Log Out Action */}
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onLogout();
                }}
                className="ml-1 text-gray-400 hover:text-red-400 hover:bg-red-500/10 p-1.5 rounded-lg transition-all"
                title="Log Out / Disconnect"
              >
                <LogOut className="w-4 h-4" />
              </button>

              {/* Hover Tooltip Popup showing Full Email */}
              <div className="absolute right-0 top-full mt-2 hidden group-hover:flex flex-col bg-[#111827] border border-gray-700 rounded-xl p-3 shadow-2xl z-50 min-w-[200px] pointer-events-none">
                <span className="text-[11px] text-gray-400">Connected Google Account:</span>
                <span className="text-xs font-semibold text-white break-all">{userEmail}</span>
                <span className="text-[10px] text-emerald-400 mt-1">● Vault Auto-Sync Active</span>
              </div>
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
