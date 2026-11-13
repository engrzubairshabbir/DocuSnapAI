import React, { useEffect, useRef, useState } from 'react';

declare global {
  interface Window {
    adsbygoogle?: any[];
  }
}

interface AdSenseBannerProps {
  slot?: string;
  format?: 'auto' | 'horizontal' | 'rectangle';
  responsive?: boolean;
  className?: string;
}

export const AdSenseBanner: React.FC<AdSenseBannerProps> = ({
  slot = '1062617259',
  format = 'auto',
  responsive = true,
  className = ''
}) => {
  const adRef = useRef<HTMLModElement | null>(null);
  const [adLoaded, setAdLoaded] = useState(false);
  const [adBlocked, setAdBlocked] = useState(false);

  useEffect(() => {
    try {
      if (typeof window !== 'undefined') {
        (window.adsbygoogle = window.adsbygoogle || []).push({});
        setAdLoaded(true);
      }
    } catch (e) {
      console.warn('AdSense ad push notice:', e);
      setAdBlocked(true);
    }
  }, []);

  return (
    <div
      className={`my-4 p-3 rounded-2xl bg-[#111827]/80 border border-gray-800/80 shadow-lg shadow-cyan-950/20 backdrop-blur-sm transition-all overflow-hidden ${className}`}
    >
      {/* Official AdSense Compliant Label */}
      <div className="flex items-center justify-between pb-2 mb-2 border-b border-gray-800/60">
        <div className="flex items-center space-x-1.5">
          <span className="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-pulse"></span>
          <span className="text-[10px] font-bold tracking-wider uppercase text-gray-400">
            Advertisement &bull; Google AdSense
          </span>
        </div>
        <span className="text-[9px] text-gray-400">Sponsored</span>
      </div>

      {/* Ad Container */}
      <div className="min-h-[90px] flex items-center justify-center bg-[#0B0F19]/60 rounded-xl overflow-hidden py-1">
        <ins
          ref={adRef}
          className="adsbygoogle"
          style={{ display: 'block', minWidth: '250px', width: '100%' }}
          data-ad-client="ca-pub-8661538987218500"
          data-ad-slot={slot}
          data-ad-format={format}
          data-full-width-responsive={responsive ? 'true' : 'false'}
        ></ins>
      </div>
    </div>
  );
};

