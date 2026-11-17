# Kryptx Accessibility (a11y) & WCAG Compliance

Kryptx is engineered to adhere strictly to the **Web Content Accessibility Guidelines (WCAG) 2.1 Level AA and select Level AAA criteria**, ensuring seamless access for all users, including those using assistive technologies like Google TalkBack, switch access, and display magnification.

---

## 1. Assistive Technology Support (TalkBack & Screen Readers)

- **Semantic Content Descriptions**: Every interactive element, icon-only button, and password generator control provides an informative, localized `contentDescription`.
- **Dynamic State Announcements**:
  - **Password Visibility**: The toggle button announces `"Show password"` or `"Hide password"` based on current visibility state.
  - **TOTP Counter**: The circular timer provides semantic announcements: `"TOTP code expires in X seconds"`.
  - **Security Health Gauge**: Announced as `"Security score: X out of 100"` via `clearAndSetSemantics`.
  - **Password Strength**: Strength score and entropy (bits) are grouped into a single accessible node to prevent confusing reading sequences.
- **Focus Order & Traversal**: Composables use structured `Modifier.semantics { isTraversalGroup = true }` to maintain a natural logical focus order from top-to-bottom and start-to-end.

---

## 2. Touch Target Sizing & Spacing

- **Minimum Dimensions**: All interactive touch targets (buttons, list item options, copy action chips) meet or exceed the **48 × 48 dp** standard specified by Android accessibility guidelines and WCAG Success Criterion 2.5.5 (Target Size).
- **Adequate Spacing**: Minimum 8 dp separation between adjacent clickable elements prevents accidental activation, especially critical on sensitive actions like vault deletion or panic password triggers.

---

## 3. Visual Contrast & Color Independence

- **High-Contrast Text**:
  - Regular text maintains a contrast ratio of **≥ 4.5:1** against backgrounds (WCAG 1.4.3).
  - Large text and primary UI components maintain a contrast ratio of **≥ 7.0:1** (WCAG 1.4.6 AAA).
- **Color Independence (WCAG 1.4.1)**:
  - Color is never used as the sole conveyor of information.
  - Password strength uses both distinct color tokens (red/yellow/green) AND explicit text indicators (`Weak`, `Good`, `Excellent`, plus entropy in bits).
  - Compromised password alerts provide warning icons in addition to red tinting.

---

## 4. Typography & Display Scaling

- **Dynamic Type**: All font sizes in Compose are defined using scalable pixels (`sp`).
- **Font Scaling Support**: Layouts adapt dynamically to OS font scaling up to **200%** without clipping text, overflowing containers, or obscuring master password input fields.
- **Line Length & Spacing**: Body copy is constrained to comfortable reading widths with appropriate line-height (`lineHeight = 20.sp` to `24.sp`) to prevent eye fatigue.

---

## 5. Motion & Vestibular Safety

- **Respect for Reduced Motion**: The app honors Android's system-wide `"Remove animations"` setting via `LocalWindowInfo` and Compose transition thresholds, replacing spring animations with instantaneous state updates.
- **No Rapid Flashing**: Zero elements flash or blink at frequencies between 3 Hz and 50 Hz, eliminating photosensitive seizure risks (WCAG 2.3.1).

---

## 6. Audit & Automated Verification

- Android Lint rule `AccessibilityLintCheck` runs continuously in CI with zero permitted violations.
- UI snapshot testing and TalkBack automated traversal runs verify that screen accessibility nodes remain complete across all releases.
