import React, { useEffect, useState } from 'react';
import { X, Check, Crown, Wallet, Sparkles } from 'lucide-react';
import { getWalletBalanceCents } from '../firebase.ts';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  totalDocs: number;
}

export const PricingModal: React.FC<Props> = ({ isOpen, onClose, totalDocs }) => {
  const [activeTab, setActiveTab] = useState<'monthly' | 'onetime'>('monthly');
  const [customUsd, setCustomUsd] = useState<number>(299);
  const [balanceCents, setBalanceCents] = useState<number>(0);

  useEffect(() => {
    if (isOpen) {
      getWalletBalanceCents().then(setBalanceCents);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const tiers = [
    {
      name: 'Free Starter',
      price: '$0.00',
      period: 'forever',
      maxDocs: '500 documents',
      isPopular: false,
      features: ['Up to 500 offline documents', 'On-device ML Kit OCR', 'SQLite FTS5 instant search', 'Standard categories']
    },
    {
      name: 'Silver Vault (Pro Organizer)',
      price: '$2.99',
      period: 'monthly recurring',
      maxDocs: '2,000 documents',
      isPopular: false,
      features: ['Up to 2,000 documents', 'Unlimited Gemini Deep Reasoning', 'Automatic Firebase Cloud Backup', 'Auto-extract medicine dosages & totals']
    },
    {
      name: 'Gold Vault (Clinic & Business)',
      price: '$9.99',
      period: 'monthly recurring',
      maxDocs: '5,000 documents',
      isPopular: true,
      features: ['Up to 5,000 documents', 'High-priority Gemini Flash vision', 'Real-time multi-device sync', 'Priority customer support']
    },
    {
      name: 'Platinum Master (Enterprise VIP)',
      price: '$29.99',
      period: 'monthly recurring',
      maxDocs: '10,000 documents',
      isPopular: false,
      features: ['Up to 10,000 documents', 'Bulk ingestion engine', 'Multi-device cloud collaboration', 'Dedicated Firestore storage vault']
    },
    {
      name: 'Infinity Diamond (Corporate Suite)',
      price: '$49.99',
      period: 'monthly recurring',
      maxDocs: '15,000 documents',
      isPopular: false,
      features: ['Up to 15,000 documents', 'Max capacity for clinics & hospitals', 'Zero AI rate limits', 'Full CSV / JSON data export']
    }
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-black/80 backdrop-blur-md">
      <div className="bg-[#111827] border border-gray-800 rounded-3xl w-full max-w-4xl max-h-[90vh] flex flex-col overflow-hidden shadow-2xl">
        <div className="p-5 border-b border-gray-800 flex items-center justify-between bg-[#161F33]">
          <div className="flex items-center space-x-2.5">
            <Crown className="w-5 h-5 text-amber-400" />
            <h2 className="text-base sm:text-lg font-bold text-white">DocuSnap Premium Storage Plans</h2>
          </div>
          <button onClick={onClose} className="p-1.5 text-gray-400 hover:text-white rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 sm:p-6 space-y-6">
          <div className="bg-[#1F2937]/70 p-4 rounded-2xl border border-blue-500/30 flex flex-wrap items-center justify-between gap-3">
            <div>
              <span className="text-xs text-gray-400 block">Current Vault Usage</span>
              <span className="text-sm font-semibold text-white">{totalDocs} documents stored</span>
            </div>
            <div className="flex items-center space-x-2">
              <span className="text-xs font-bold px-3 py-1 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 flex items-center space-x-1">
                <Wallet className="w-3.5 h-3.5" />
                <span>5x AI Wallet: ${(balanceCents / 100).toFixed(2)} USD</span>
              </span>
              <span className="text-xs font-bold px-3 py-1 rounded-full bg-blue-500/20 text-blue-400">
                Free Starter (500 limit)
              </span>
            </div>
          </div>

          <div className="flex p-1 bg-[#0B0F19] rounded-xl border border-gray-800">
            <button
              onClick={() => setActiveTab('monthly')}
              className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all ${
                activeTab === 'monthly' ? 'bg-blue-600 text-white shadow' : 'text-gray-400 hover:text-white'
              }`}
            >
              Monthly Storage Tiers
            </button>
            <button
              onClick={() => setActiveTab('onetime')}
              className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all ${
                activeTab === 'onetime' ? 'bg-amber-500 text-black font-bold shadow' : 'text-gray-400 hover:text-white'
              }`}
            >
              One-Time Prepaid AI Wallet ($99–$999)
            </button>
          </div>

          {activeTab === 'monthly' ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {tiers.map((t, idx) => (
              <div
                key={idx}
                className={`relative rounded-2xl p-5 border flex flex-col justify-between transition-all ${
                  t.isPopular
                    ? 'bg-[#16223D] border-amber-500/80 shadow-lg shadow-amber-500/10'
                    : 'bg-[#111827] border-gray-800 hover:border-gray-700'
                }`}
              >
                {t.isPopular && (
                  <span className="absolute -top-3 left-4 px-2.5 py-0.5 rounded-full bg-gradient-to-r from-amber-500 to-yellow-400 text-black font-extrabold text-[10px] tracking-wider uppercase">
                    Most Popular
                  </span>
                )}

                <div>
                  <h3 className="font-bold text-white text-sm">{t.name}</h3>
                  <div className="mt-2 flex items-baseline space-x-1">
                    <span className="text-2xl font-extrabold text-white">{t.price}</span>
                    <span className="text-xs text-gray-400">/{t.period}</span>
                  </div>
                  <span className="text-xs font-semibold text-cyan-400 block mt-1">{t.maxDocs}</span>

                  <ul className="mt-4 space-y-2 text-xs text-gray-300">
                    {t.features.map((f, fIdx) => (
                      <li key={fIdx} className="flex items-center space-x-2">
                        <Check className="w-3.5 h-3.5 text-emerald-400 flex-shrink-0" />
                        <span>{f}</span>
                      </li>
                    ))}
                  </ul>
                </div>

                <button
                  onClick={() => alert(`Pre-Release Notice: Verified Google Play Store checkout for "${t.name}" unlocks in the DocuSnap Android app upon Play Console publishing.`)}
                  className={`mt-6 w-full py-2.5 rounded-xl text-xs font-semibold transition-all ${
                    t.isPopular
                      ? 'bg-gradient-to-r from-amber-500 to-yellow-400 hover:opacity-90 text-black font-bold'
                      : 'bg-blue-600 hover:bg-blue-500 text-white'
                  }`}
                >
                  {t.price === '$0.00' ? 'Current Plan' : `Subscribe ${t.price}/mo`}
                </button>
              </div>
            ))}
          </div>
          ) : (
            <div className="bg-[#161229] border border-amber-500/40 rounded-2xl p-6 space-y-5">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <h3 className="text-base font-bold text-white flex items-center space-x-2">
                    <Sparkles className="w-4 h-4 text-amber-400" />
                    <span>VIP Lifetime Vault + Global 5x AI Balance</span>
                  </h3>
                  <p className="text-xs text-gray-300 mt-1">
                    Load any amount from $99 to $999 USD once via Google Play Billing. Includes Global Any-Language AI Search & Vision OCR (charged at 5x rate = $0.05/call deducted atomically from your balance).
                  </p>
                </div>
                <span className="text-2xl font-extrabold text-amber-400 whitespace-nowrap">${customUsd}</span>
              </div>

              <div>
                <input
                  type="range"
                  min={99}
                  max={999}
                  value={customUsd}
                  onChange={(e) => setCustomUsd(Number(e.target.value))}
                  className="w-full accent-amber-400"
                />
                <div className="flex flex-wrap gap-2 mt-3">
                  {[99, 199, 299, 499, 750, 999].map((amt) => (
                    <button
                      key={amt}
                      onClick={() => setCustomUsd(amt)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-bold border transition-all ${
                        customUsd === amt
                          ? 'bg-amber-500/20 border-amber-400 text-amber-300'
                          : 'bg-[#111827] border-gray-700 text-gray-300'
                      }`}
                    >
                      ${amt}
                    </button>
                  ))}
                </div>
              </div>

              <button
                onClick={() => alert(`Pre-Release Notice: Open DocuSnap AI on Android after Google Play launch to top up $${customUsd} USD into your Prepaid AI Wallet via Google Play Billing.`)}
                className="w-full py-3 rounded-xl bg-gradient-to-r from-amber-500 to-yellow-400 text-black font-extrabold text-xs hover:opacity-95 transition-all"
              >
                Load ${customUsd} USD One-Time via Play Store
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

