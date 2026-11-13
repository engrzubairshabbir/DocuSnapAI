import React, { useState } from 'react';
import { Shield, FileText, CheckCircle, ExternalLink } from 'lucide-react';

export const Footer: React.FC = () => {
  const [showPrivacy, setShowPrivacy] = useState(false);
  const [showTerms, setShowTerms] = useState(false);

  return (
    <footer className="mt-12 border-t border-gray-800/80 bg-[#070B14] py-8 px-4 text-center">
      <div className="max-w-4xl mx-auto space-y-4">
        {/* Brand & Subdomain Info */}
        <div className="flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-gray-400">
          <div className="flex items-center space-x-2">
            <span className="w-2 h-2 rounded-full bg-cyan-400"></span>
            <span className="font-semibold text-gray-300">DocuSnap AI</span>
            <span>&bull; docusnap.consultways.com</span>
          </div>

          <div className="flex items-center space-x-4">
            <button
              onClick={() => setShowPrivacy(true)}
              className="hover:text-cyan-400 transition-colors underline-offset-4 hover:underline"
            >
              Privacy Policy &amp; Cookies
            </button>
            <button
              onClick={() => setShowTerms(true)}
              className="hover:text-cyan-400 transition-colors underline-offset-4 hover:underline"
            >
              Terms of Service
            </button>
            <a
              href="https://consultways.com"
              target="_blank"
              rel="noreferrer"
              className="hover:text-cyan-400 transition-colors flex items-center space-x-1"
            >
              <span>consultways.com</span>
              <ExternalLink className="w-3 h-3" />
            </a>
          </div>
        </div>

        {/* AdSense Compliance Notice */}
        <div className="text-[11px] text-gray-400 max-w-2xl mx-auto leading-relaxed">
          Google AdSense Verified Publisher: <code className="text-cyan-400 font-mono">pub-8661538987218500</code>.
          Third-party vendors, including Google, use cookies to serve personalized ads based on a user&apos;s prior visits.
          All document processing and OCR indexing adhere to strict zero-retention privacy standards.
        </div>
      </div>

      {/* PRIVACY POLICY MODAL */}
      {showPrivacy && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
          <div className="bg-[#0E1526] border border-cyan-500/40 rounded-3xl p-6 w-full max-w-xl max-h-[85vh] overflow-y-auto text-left shadow-[0_0_50px_rgba(0,245,255,0.2)]">
            <div className="flex items-center justify-between pb-3 border-b border-gray-800">
              <h3 className="text-base font-bold text-white flex items-center space-x-2">
                <Shield className="w-5 h-5 text-cyan-400" />
                <span>Privacy Policy &amp; Cookie Disclosure</span>
              </h3>
              <button onClick={() => setShowPrivacy(false)} className="text-gray-400 hover:text-white font-bold text-lg">
                ✕
              </button>
            </div>

            <div className="space-y-4 text-xs text-gray-300 mt-4 leading-relaxed">
              <p>
                <strong className="text-white">1. Information We Process:</strong> DocuSnap AI processes your receipts, prescriptions, and utility bills. Text extraction (OCR) is performed directly on your device. Syncing to Firebase is protected under your authenticated Google/anonymous UID.
              </p>
              <p>
                <strong className="text-white">2. Google AdSense &amp; Advertising Cookies:</strong> Google uses cookies to serve ads on our site. Google&apos;s use of advertising cookies enables it and its partners to serve ads to our users based on their visits to our sites and/or other sites on the Internet.
              </p>
              <p>
                <strong className="text-white">3. User Controls:</strong> You may opt out of personalized advertising by visiting Google&apos;s Ads Settings (<a href="https://www.google.com/settings/ads" target="_blank" rel="noreferrer" className="text-cyan-400 underline">google.com/settings/ads</a>).
              </p>
              <p>
                <strong className="text-white">4. Publisher Identity:</strong> This service is hosted under the verified domain <code className="text-cyan-300">consultways.com</code> with Publisher ID <code className="text-cyan-300">ca-pub-8661538987218500</code>.
              </p>
            </div>

            <div className="mt-6 flex justify-end">
              <button
                onClick={() => setShowPrivacy(false)}
                className="px-5 py-2 bg-gradient-to-r from-cyan-500 to-blue-600 text-black font-bold text-xs rounded-xl"
              >
                Close &amp; Accept
              </button>
            </div>
          </div>
        </div>
      )}

      {/* TERMS MODAL */}
      {showTerms && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
          <div className="bg-[#0E1526] border border-cyan-500/40 rounded-3xl p-6 w-full max-w-xl max-h-[85vh] overflow-y-auto text-left shadow-[0_0_50px_rgba(0,245,255,0.2)]">
            <div className="flex items-center justify-between pb-3 border-b border-gray-800">
              <h3 className="text-base font-bold text-white flex items-center space-x-2">
                <FileText className="w-5 h-5 text-cyan-400" />
                <span>Terms of Service</span>
              </h3>
              <button onClick={() => setShowTerms(false)} className="text-gray-400 hover:text-white font-bold text-lg">
                ✕
              </button>
            </div>

            <div className="space-y-4 text-xs text-gray-300 mt-4 leading-relaxed">
              <p>
                <strong className="text-white">1. Service Purpose:</strong> DocuSnap AI is designed to help users organize, search, and parse receipt and prescription documents using on-device OCR and AI summaries.
              </p>
              <p>
                <strong className="text-white">2. Medical &amp; Financial Disclaimer:</strong> DocuSnap AI does not provide medical or financial advice. Extracted text should always be cross-checked with the original document.
              </p>
              <p>
                <strong className="text-white">3. Intellectual Property:</strong> All content, logos, and algorithms are proprietary to DocuSnap AI and consultways.com.
              </p>
            </div>

            <div className="mt-6 flex justify-end">
              <button
                onClick={() => setShowTerms(false)}
                className="px-5 py-2 bg-gradient-to-r from-cyan-500 to-blue-600 text-black font-bold text-xs rounded-xl"
              >
                I Understand
              </button>
            </div>
          </div>
        </div>
      )}
    </footer>
  );
};

