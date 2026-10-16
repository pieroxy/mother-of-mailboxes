# Refreshes the bundled Unicode security data (see LookalikeText) from unicode.org's latest
# release. Run before a release; review the diff before committing.
set -e

DIR=src/main/resources/unicode
curl -fsS -o "$DIR/confusables.txt" https://www.unicode.org/Public/security/latest/confusables.txt
curl -fsS -o "$DIR/LICENSE.txt" https://www.unicode.org/license.txt
grep -m1 '^# Version' "$DIR/confusables.txt"
