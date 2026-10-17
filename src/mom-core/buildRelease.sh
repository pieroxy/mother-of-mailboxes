set -e

# The first <version> after </parent>: the one before it is the parent POM's.
VERSION=$(sed -n '/<\/parent>/,$ s/.*<version>\(.*\)<\/version>.*/\1/p' pom.xml | head -1)

if echo "$VERSION" | grep -qi 'rc'; then
  echo "Refusing to build a release: pom.xml version \"$VERSION\" still contains \"rc\"." >&2
  exit 1
fi

mvn clean package
