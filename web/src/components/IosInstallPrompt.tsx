import React, { useState, useEffect } from 'react';
import { Share, PlusSquare, X, Smartphone } from 'lucide-react';

export const IosInstallPrompt: React.FC = () => {
  const [showPrompt, setShowPrompt] = useState(false);

  useEffect(() => {
    // Detect iOS device (iPhone/iPad/iPod)
    const isIos = /iPad|iPhone|iPod/.test(navigator.userAgent) && !(window as unknown as { MSStream: unknown }).MSStream;
    // Check if app is already running in standalone PWA mode
    const isStandalone = window.matchMedia('(display-mode: standalone)').matches || 
      (navigator as unknown as { standalone?: boolean }).standalone === true;

    // Check if dismissed before
    const isDismissed = localStorage.getItem('docusnap_ios_prompt_dismissed');

    if (isIos && !isStandalone && !isDismissed) {
      setShowPrompt(true);
    }
  }, []);

  if (!showPrompt) return null;

  const handleDismiss = () => {
    localStorage.setItem('docusnap_ios_prompt_dismissed', 'true');
    setShowPrompt(false);
  };

  return (
    <div className="fixed inset-x-0 bottom-0 z-50 p-4 pb-6 bg-[#111827]/95 backdrop-blur-md border-t border-gray-800 shadow-2xl animate-in slide-in-from-bottom duration-300">
      <div className="max-w-md mx-auto">
        <div className="flex items-start justify-between">
          <div className="flex items-center space-x-3">
            <img
              src="./logo.png"
              alt="DocuSnap AI"
              className="w-10 h-10 rounded-xl object-cover shadow-lg shadow-cyan-500/20 border border-gray-700/60"
            />
            <div>
              <h3 className="font-semibold text-white text-sm">Install DocuSnap AI on iPhone</h3>
              <p className="text-xs text-gray-400">Add to Home Screen for native offline experience</p>
            </div>
          </div>
          <button 
            onClick={handleDismiss} 
            className="text-gray-400 hover:text-white p-1 rounded-lg"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="mt-4 space-y-2.5 text-xs text-gray-300 bg-[#1F2937]/80 p-3 rounded-xl border border-gray-700/60">
          <div className="flex items-center space-x-2">
            <span className="flex-shrink-0 w-5 h-5 rounded-full bg-blue-500/20 text-blue-400 flex items-center justify-center font-bold text-[11px]">1</span>
            <span>Tap the <strong>Share</strong> button <Share className="w-3.5 h-3.5 inline mx-1 text-blue-400" /> at bottom bar</span>
          </div>
          <div className="flex items-center space-x-2">
            <span className="flex-shrink-0 w-5 h-5 rounded-full bg-blue-500/20 text-blue-400 flex items-center justify-center font-bold text-[11px]">2</span>
            <span>Scroll down and select <strong>Add to Home Screen</strong> <PlusSquare className="w-3.5 h-3.5 inline mx-1 text-emerald-400" /></span>
          </div>
        </div>

        <button 
          onClick={handleDismiss}
          className="mt-3 w-full py-2 bg-blue-600 hover:bg-blue-500 text-white text-xs font-medium rounded-lg transition-colors"
        >
          Got it
        </button>
      </div>
    </div>
  );
};

