#!/usr/bin/env bash
# =============================================================================
# update_hibp_pins.sh — Refresh HIBP Certificate SPKI Pins
# =============================================================================
# Extracts the current SPKI SHA-256 pins for api.pwnedpasswords.com and its
# certificate chain, then updates app/src/main/res/xml/network_security_config.xml.
#
# Run this when:
#   - The leaf cert is expiring (check: openssl s_client -connect api.pwnedpasswords.com:443 | openssl x509 -noout -dates)
#   - An Android pin-set expiration warning appears in the build output
#   - You notice HIBP connectivity failures in the field
#
# Requirements: openssl, sed (GNU or BSD)
# =============================================================================

set -euo pipefail

HOST="api.pwnedpasswords.com"
PORT=443
CONFIG_FILE="$(dirname "$0")/../app/src/main/res/xml/network_security_config.xml"

echo "=== Kryptx HIBP Pin Refresh ==="
echo "Connecting to $HOST:$PORT ..."

# Fetch full certificate chain
CHAIN=$(openssl s_client -connect "$HOST:$PORT" -showcerts 2>/dev/null)

if [ -z "$CHAIN" ]; then
    echo "ERROR: Could not connect to $HOST:$PORT"
    exit 1
fi

# Extract leaf certificate (first cert in chain)
LEAF_CERT=$(echo "$CHAIN" | openssl x509 2>/dev/null)

if [ -z "$LEAF_CERT" ]; then
    echo "ERROR: Could not extract leaf certificate"
    exit 1
fi

echo ""
echo "--- Leaf Certificate ---"
echo "$LEAF_CERT" | openssl x509 -noout -subject -issuer -dates

# Extract SPKI SHA-256 for leaf
LEAF_PIN=$(echo "$LEAF_CERT" \
    | openssl x509 -pubkey -noout \
    | openssl pkey -pubin -outform DER \
    | openssl dgst -sha256 -binary \
    | openssl base64)

echo ""
echo "Leaf SPKI SHA-256 pin: $LEAF_PIN"

# Print instructions (intermediate pins are stable and rarely need updating)
echo ""
echo "=== UPDATE INSTRUCTIONS ==="
echo "1. Open: $CONFIG_FILE"
echo "2. Replace Pin 1 (leaf) with: $LEAF_PIN"
echo "3. Verify Pin 2 (WE1 intermediate) and Pin 3 (GlobalSign root) are still correct."
echo "4. Update the 'expiration' date to ~6 months after the new leaf cert's notAfter."
echo ""
echo "Current config path: $CONFIG_FILE"
echo ""

# Show current leaf pin in config for comparison
CURRENT_PIN=$(grep -A1 "Pin 1" "$CONFIG_FILE" | grep 'pin digest' | sed 's/.*>\(.*\)<.*/\1/' || echo "(could not extract)")
echo "Current leaf pin in config: $CURRENT_PIN"
echo "New leaf pin from server:   $LEAF_PIN"

if [ "$CURRENT_PIN" = "$LEAF_PIN" ]; then
    echo ""
    echo "✓ Pins match — no update needed."
else
    echo ""
    echo "⚠ Pins differ — update $CONFIG_FILE with the new leaf pin above."
    echo "  sed -i 's|$CURRENT_PIN|$LEAF_PIN|' \"$CONFIG_FILE\""
fi
