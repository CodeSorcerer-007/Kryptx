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

      const header = document.createElement('div');
      header.className = 'cookie-header';
      const shield = document.createElement('span');
      shield.textContent = '🛡️';
      const title = document.createElement('span');
      title.textContent = 'Zero-Tracking Privacy Guarantee';
      header.append(shield, title);

      const text = document.createElement('div');
      text.className = 'cookie-text';
      text.textContent = 'We believe privacy is a fundamental human right. Kryptx uses zero third-party tracking cookies, zero marketing beacons, and zero cross-site telemetry. We only use local session storage for essential preferences.';

      const actions = document.createElement('div');
      actions.className = 'cookie-actions';

      const acceptBtn = document.createElement('button');
      acceptBtn.type = 'button';
      acceptBtn.className = 'btn btn-primary btn-sm';
      acceptBtn.id = 'cookie-accept-all';
      acceptBtn.textContent = 'Accept & Continue';

      const essentialBtn = document.createElement('button');
      essentialBtn.type = 'button';
      essentialBtn.className = 'btn btn-secondary btn-sm';
      essentialBtn.id = 'cookie-essential-only';
      essentialBtn.textContent = 'Essential Only';

      const policyLink = document.createElement('a');
      policyLink.href = 'privacy.html';
      policyLink.className = 'btn btn-outline btn-sm';
      policyLink.textContent = 'Read Policy';

      actions.append(acceptBtn, essentialBtn, policyLink);
      banner.append(header, text, actions);
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
