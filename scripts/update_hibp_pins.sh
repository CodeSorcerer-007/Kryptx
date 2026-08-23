#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# update_hibp_pins.sh
# Extracts the current SHA-256 SPKI certificate pin for api.pwnedpasswords.com
# and prints the value to paste into network_security_config.xml.
#
# Usage:  chmod +x scripts/update_hibp_pins.sh && ./scripts/update_hibp_pins.sh
# Requires: openssl (any modern version)
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

HOST="api.pwnedpasswords.com"
PORT=443

echo ""
echo "Connecting to ${HOST}:${PORT} to extract certificate pins..."
echo ""

# ── Leaf certificate (index 0) ────────────────────────────────────────────────
LEAF_PIN=$(echo | openssl s_client -servername "$HOST" -connect "$HOST:$PORT" 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform DER \
  | openssl dgst -sha256 -binary \
  | openssl base64)

echo "┌─ LEAF CERTIFICATE SPKI PIN (Pin 1 — update when cert renews)"
echo "│  <pin digest=\"SHA-256\">${LEAF_PIN}</pin>"
echo ""

# ── Intermediate certificates (index 1, 2...) ─────────────────────────────────
echo "┌─ FULL CHAIN (all certs in order, leaf first)"
echo | openssl s_client -servername "$HOST" -connect "$HOST:$PORT" -showcerts 2>/dev/null \
  | awk '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/' \
  | awk 'BEGIN{n=0} /-----BEGIN CERTIFICATE-----/{n++; cert=""} {cert=cert $0 "\n"} /-----END CERTIFICATE-----/{
      cmd = "echo \"" cert "\" | openssl x509 -pubkey -noout | openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | openssl base64"
      cmd | getline pin
      close(cmd)
      printf "│  Cert %d pin: <pin digest=\"SHA-256\">%s</pin>\n", n, pin
  }'

echo ""
echo "─────────────────────────────────────────────────────────────"
echo "Paste Pin 1 (leaf) as the FIRST <pin> in network_security_config.xml."
echo "Keep Pin 2 (DigiCert G2) and Pin 3 (Cloudflare) as backup anchors."
echo "Update the expiration date to 6 months before the leaf cert's notAfter."
echo ""
echo "Check current leaf cert expiry:"
echo | openssl s_client -servername "$HOST" -connect "$HOST:$PORT" 2>/dev/null \
  | openssl x509 -noout -dates
