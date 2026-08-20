/**
 * Kryptx Privacy-Preserving Lightweight Analytics
 * 100% Cookieless, Zero PII, Respects Do-Not-Track (DNT)
 */

(function(window) {
  'use strict';

  const OPT_OUT_KEY = 'kryptx_analytics_optout';

  const KryptxAnalytics = {
    optedOut: false,

    init: function() {
      // Respect user browser Do-Not-Track header
      if (navigator.doNotTrack === '1' || window.doNotTrack === '1') {
        this.optedOut = true;
        return;
      }

      if (localStorage.getItem(OPT_OUT_KEY) === 'true') {
        this.optedOut = true;
        return;
      }

      // Record initial pageview
      this.track('pageview', {
        path: window.location.pathname,
        title: document.title,
        referrer: document.referrer ? new URL(document.referrer).hostname : 'direct'
      });

      // Bind automatic download and link event tracking
      document.addEventListener('click', (e) => {
        const target = e.target.closest('a, button');
        if (!target) return;

        const href = target.getAttribute('href') || '';
        const downloadAttr = target.getAttribute('download');

        if (href.endsWith('.apk') || downloadAttr !== null || target.classList.contains('btn-download')) {
          this.track('download_apk', { version: 'v1.0.0', href: href });
        } else if (href.includes('github.com')) {
          this.track('github_link_click', { url: href });
        }
      });
    },

    track: function(eventName, properties) {
      if (this.optedOut) return;

      const payload = {
        event: eventName,
        timestamp: new Date().toISOString(),
        url: window.location.href,
        screen: `${window.innerWidth}x${window.innerHeight}`,
        ...properties
      };

      // In production, this dispatches to an endpoint or logs locally in dev
      if (window.console && window.console.info) {
        window.console.info(`[Kryptx Privacy Telemetry] Event: ${eventName}`, payload);
      }
    },

    setOptOut: function(optOut) {
      this.optedOut = Boolean(optOut);
      localStorage.setItem(OPT_OUT_KEY, String(this.optedOut));
    }
  };

  window.KryptxAnalytics = KryptxAnalytics;
  document.addEventListener('DOMContentLoaded', () => {
    KryptxAnalytics.init();
  });
})(window);
