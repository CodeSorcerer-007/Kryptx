/**
 * Kryptx Main Frontend Scripts
 * Zero-Knowledge Offline Password Fortress
 */

document.addEventListener('DOMContentLoaded', () => {
  initMobileNav();
  initStickyCTA();
  initEntropyCalculator();
  initFormValidation();
  initCopyButtons();
});

/* ==========================================================================
   1. Mobile Navigation Toggle
   ========================================================================== */
function initMobileNav() {
  const hamburger = document.querySelector('.hamburger');
  const mobileNav = document.querySelector('.mobile-nav');

  if (hamburger && mobileNav) {
    hamburger.addEventListener('click', () => {
      const isOpen = mobileNav.classList.toggle('is-open');
      hamburger.setAttribute('aria-expanded', isOpen);
      hamburger.innerHTML = isOpen ? '✕' : '☰';
    });

    // Close menu when clicking outside or on a link
    document.addEventListener('click', (e) => {
      if (!hamburger.contains(e.target) && !mobileNav.contains(e.target)) {
        mobileNav.classList.remove('is-open');
        hamburger.setAttribute('aria-expanded', 'false');
        hamburger.innerHTML = '☰';
      }
    });

    mobileNav.querySelectorAll('.nav-link').forEach(link => {
      link.addEventListener('click', () => {
        mobileNav.classList.remove('is-open');
        hamburger.innerHTML = '☰';
      });
    });
  }
}

/* ==========================================================================
   2. Sticky Mobile CTA (Requirement 11)
   ========================================================================== */
function initStickyCTA() {
  const stickyCTA = document.querySelector('.sticky-mobile-cta');
  const heroSection = document.querySelector('.hero');

  if (!stickyCTA) return;

  if (heroSection) {
    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        // Show sticky CTA when scrolled past the hero section
        if (!entry.isIntersecting && window.innerWidth <= 768) {
          stickyCTA.classList.add('is-visible');
        } else {
          stickyCTA.classList.remove('is-visible');
        }
      });
    }, { threshold: 0.1 });

    observer.observe(heroSection);
  } else {
    // If on subpage without hero, show sticky CTA on scroll down
    window.addEventListener('scroll', () => {
      if (window.scrollY > 200 && window.innerWidth <= 768) {
        stickyCTA.classList.add('is-visible');
      } else {
        stickyCTA.classList.remove('is-visible');
      }
    });
  }
}

/* ==========================================================================
   3. Interactive Password Entropy & Strength Tester Demo
   ========================================================================== */
function initEntropyCalculator() {
  const input = document.getElementById('entropy-demo-input');
  const meterFill = document.getElementById('entropy-meter-fill');
  const scoreVal = document.getElementById('entropy-score-val');
  const entropyBits = document.getElementById('entropy-bits-val');
  const crackTime = document.getElementById('entropy-crack-time');
  const strengthLabel = document.getElementById('entropy-strength-label');

  if (!input || !meterFill) return;

  input.addEventListener('input', (e) => {
    const pass = e.target.value;
    if (!pass) {
      meterFill.className = 'meter-fill';
      meterFill.style.width = '0%';
      if (scoreVal) scoreVal.textContent = '0 / 100';
      if (entropyBits) entropyBits.textContent = '0 bits';
      if (crackTime) crackTime.textContent = 'Instant';
      if (strengthLabel) strengthLabel.textContent = 'Enter a master password...';
      return;
    }

    let pool = 0;
    if (/[a-z]/.test(pass)) pool += 26;
    if (/[A-Z]/.test(pass)) pool += 26;
    if (/[0-9]/.test(pass)) pool += 10;
    if (/[^a-zA-Z0-9]/.test(pass)) pool += 32;

    const bits = Math.round(pass.length * Math.log2(pool || 1));
    const score = Math.min(100, Math.round((bits / 128) * 100));

    meterFill.className = 'meter-fill';
    let label = 'Weak';
    let time = '12 seconds';

    if (bits < 40) {
      meterFill.classList.add('weak');
      meterFill.style.width = '25%';
      label = 'Vulnerable';
      time = '< 1 millisecond';
    } else if (bits < 65) {
      meterFill.classList.add('medium');
      meterFill.style.width = '50%';
      label = 'Moderate';
      time = '3 days';
    } else if (bits < 90) {
      meterFill.classList.add('strong');
      meterFill.style.width = '75%';
      label = 'Strong';
      time = '4,500 years';
    } else {
      meterFill.classList.add('fortress');
      meterFill.style.width = '100%';
      label = 'Cryptographic Fortress';
      time = '3.8 trillion centuries';
    }

    if (scoreVal) scoreVal.textContent = `${score} / 100`;
    if (entropyBits) entropyBits.textContent = `${bits} bits`;
    if (crackTime) crackTime.textContent = time;
    if (strengthLabel) strengthLabel.textContent = label;
  });
}

/* ==========================================================================
   4. Form Validation & Error / Loading States (Requirements 12 & 13)
   ========================================================================== */
function initFormValidation() {
  const forms = document.querySelectorAll('form[data-validate="true"]');

  forms.forEach(form => {
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      let isValid = true;

      const inputs = form.querySelectorAll('input[required], textarea[required], select[required]');
      inputs.forEach(input => {
        const formGroup = input.closest('.form-group');
        const errorText = formGroup ? formGroup.querySelector('.error-text') : null;
        
        let fieldValid = true;
        let errorMessage = 'This field is required.';

        if (!input.value.trim()) {
          fieldValid = false;
          errorMessage = 'Please complete this required field.';
        } else if (input.type === 'email' && !validateEmail(input.value.trim())) {
          fieldValid = false;
          errorMessage = 'Please enter a valid email address.';
        }

        if (!fieldValid) {
          isValid = false;
          if (formGroup) {
            formGroup.classList.add('has-error');
            formGroup.classList.remove('is-valid');
            input.setAttribute('aria-invalid', 'true');
          }
          if (errorText) {
            errorText.textContent = errorMessage;
            errorText.style.display = 'flex';
          }
        } else {
          if (formGroup) {
            formGroup.classList.remove('has-error');
            formGroup.classList.add('is-valid');
            input.setAttribute('aria-invalid', 'false');
          }
          if (errorText) {
            errorText.style.display = 'none';
          }
        }
      });

      if (isValid) {
        const submitBtn = form.querySelector('button[type="submit"]');
        if (submitBtn) {
          submitBtn.classList.add('is-loading');
          submitBtn.disabled = true;
        }

        // Track custom analytics event if analytics is active
        if (window.KryptxAnalytics) {
          window.KryptxAnalytics.track('contact_submit', { form: form.id || 'contact_form' });
        }

        // Simulate secure on-device cryptographic transmission & redirect
        setTimeout(() => {
          window.location.href = 'thank-you.html';
        }, 900);
      }
    });

    // Real-time live error removal on user input
    form.querySelectorAll('input, textarea').forEach(input => {
      input.addEventListener('input', () => {
        const formGroup = input.closest('.form-group');
        if (formGroup && formGroup.classList.contains('has-error')) {
          formGroup.classList.remove('has-error');
          const errorText = formGroup.querySelector('.error-text');
          if (errorText) errorText.style.display = 'none';
          input.removeAttribute('aria-invalid');
        }
      });
    });
  });
}

function validateEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

/* ==========================================================================
   5. Clipboard Copy Utility
   ========================================================================== */
function initCopyButtons() {
  document.querySelectorAll('.copy-btn, [data-copy]').forEach(btn => {
    btn.addEventListener('click', () => {
      const targetId = btn.getAttribute('data-copy');
      const targetEl = targetId ? document.getElementById(targetId) : null;
      const textToCopy = targetEl ? targetEl.textContent.trim() : btn.getAttribute('data-text');

      if (textToCopy) {
        navigator.clipboard.writeText(textToCopy).then(() => {
          const originalText = btn.textContent;
          btn.textContent = '✓ Copied!';
          btn.classList.add('copied');
          setTimeout(() => {
            btn.textContent = originalText;
            btn.classList.remove('copied');
          }, 2000);
        }).catch(err => {
          console.warn('Clipboard copy failed:', err);
        });
      }
    });
  });
}
