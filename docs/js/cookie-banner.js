/**
 * Kryptx Privacy-First Cookie & Storage Consent Banner
 * Zero-Tracking Architecture
 */

(function() {
  const STORAGE_KEY = 'kryptx_cookie_consent';

  document.addEventListener('DOMContentLoaded', () => {
    // Check if consent has already been chosen
    const consent = localStorage.getItem(STORAGE_KEY);
    if (!consent) {
      setTimeout(showCookieBanner, 800);
    }
  });

  function showCookieBanner() {
    let banner = document.getElementById('kryptx-cookie-banner');
    if (!banner) {
      banner = document.createElement('div');
      banner.id = 'kryptx-cookie-banner';
      banner.className = 'cookie-banner';
      banner.setAttribute('role', 'region');
      banner.setAttribute('aria-label', 'Cookie and Privacy Settings');

      banner.innerHTML = `
        <div class="cookie-header">
          <span>🛡️</span>
          <span>Zero-Tracking Privacy Guarantee</span>
        </div>
        <div class="cookie-text">
          We believe privacy is a fundamental human right. Kryptx uses <strong>zero third-party tracking cookies, zero marketing beacons, and zero cross-site telemetry</strong>. We only use local session storage for essential preferences.
        </div>
        <div class="cookie-actions">
          <button type="button" class="btn btn-primary btn-sm" id="cookie-accept-all">Accept & Continue</button>
          <button type="button" class="btn btn-secondary btn-sm" id="cookie-essential-only">Essential Only</button>
          <a href="privacy.html" class="btn btn-outline btn-sm">Read Policy</a>
        </div>
      `;
      document.body.appendChild(banner);
    }

    // Trigger smooth slide in
    requestAnimationFrame(() => {
      banner.classList.add('is-active');
    });

    const acceptBtn = document.getElementById('cookie-accept-all');
    const essentialBtn = document.getElementById('cookie-essential-only');

    if (acceptBtn) {
      acceptBtn.addEventListener('click', () => {
        setConsent('all');
      });
    }

    if (essentialBtn) {
      essentialBtn.addEventListener('click', () => {
        setConsent('essential');
      });
    }
  }

  function setConsent(level) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        level: level,
        timestamp: new Date().toISOString()
      }));
    } catch (e) {
      console.warn('LocalStorage unavailable for cookie consent');
    }

    const banner = document.getElementById('kryptx-cookie-banner');
    if (banner) {
      banner.classList.remove('is-active');
      setTimeout(() => {
        banner.remove();
      }, 400);
    }
  }
})();
