#!/usr/bin/env bash
# Releases the library (org.mpi-sws.jmc:jmc) and the agent (org.mpi-sws.jmc:jmc-agent) to Maven Central through the
# Central Portal.
#
#   scripts/publish.sh [--dry-run]
#
# 1. Gradle publishes both modules, signed, into the local staging repository build/staging-deploy (Maven layout,
#    with .asc signatures and md5/sha1 checksums).
# 2. The staging repository is zipped into build/central-bundle.zip.
# 3. Without --dry-run, the bundle is uploaded to the Central Portal with publishingType=USER_MANAGED: the deployment
#    is validated there and waits for "Publish" in the Portal's web interface.
#
# Environment (CI/CD variables; never printed): SIGNING_KEY (the armored private key, base64-encoded in one line),
# SIGNING_PASSWORD, and for the upload MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_PASSWORD (a Central Portal user
# token). Under a tag, CI_COMMIT_TAG must be v<version>, the version in build.gradle.kts.
set -euo pipefail

dry_run=false
if [ "${1:-}" = "--dry-run" ]; then
  dry_run=true
elif [ $# -gt 0 ]; then
  echo "usage: scripts/publish.sh [--dry-run]" >&2
  exit 2
fi

version=$(./gradlew -q :core:properties | sed -n 's/^version: //p')
if [ -z "$version" ]; then
  echo "publish: cannot read the project version" >&2
  exit 1
fi
if [ -n "${CI_COMMIT_TAG:-}" ] && [ "$CI_COMMIT_TAG" != "v$version" ]; then
  echo "publish: tag $CI_COMMIT_TAG does not match the project version $version" >&2
  exit 1
fi
if [ -z "${SIGNING_KEY:-}" ]; then
  if [ "$dry_run" = false ]; then
    echo "publish: SIGNING_KEY is not set; Maven Central requires signed artifacts" >&2
    exit 1
  fi
  echo "publish: SIGNING_KEY is not set; the dry-run bundle is unsigned"
fi

rm -rf build/staging-deploy build/central-bundle.zip
./gradlew clean
./gradlew :core:publishAllPublicationsToStagingRepository :agent:publishAllPublicationsToStagingRepository

# The expected artifacts, each non-empty, with its checksums and (when signing) its signature.
expected=()
for a in jmc jmc-agent; do
  d=build/staging-deploy/org/mpi-sws/jmc/$a/$version
  expected+=("$d/$a-$version.jar" "$d/$a-$version-sources.jar" "$d/$a-$version-javadoc.jar" "$d/$a-$version.pom")
done
expected+=("build/staging-deploy/org/mpi-sws/jmc/jmc/$version/jmc-$version.module")
missing=0
for f in "${expected[@]}"; do
  [ -s "$f" ] || { echo "publish: missing or empty $f" >&2; missing=1; continue; }
  for ext in md5 sha1; do
    [ -f "$f.$ext" ] || { echo "publish: missing $f.$ext" >&2; missing=1; }
  done
  if [ -n "${SIGNING_KEY:-}" ]; then
    [ -f "$f.asc" ] || { echo "publish: missing $f.asc" >&2; missing=1; }
  fi
done
[ "$missing" = 0 ] || exit 1

# The bundle: the Maven layout under org/, without the repository's maven-metadata files.
(cd build/staging-deploy && zip -qr ../central-bundle.zip org -x '*maven-metadata*')
echo "publish: bundle build/central-bundle.zip for version $version:"
(cd build/staging-deploy && find org -type f ! -name '*maven-metadata*' | sort)

if [ "$dry_run" = true ]; then
  echo "publish: dry run, nothing uploaded"
  exit 0
fi

: "${MAVEN_CENTRAL_USERNAME:?publish: MAVEN_CENTRAL_USERNAME is not set}"
: "${MAVEN_CENTRAL_PASSWORD:?publish: MAVEN_CENTRAL_PASSWORD is not set}"
token=$(printf '%s:%s' "$MAVEN_CENTRAL_USERNAME" "$MAVEN_CENTRAL_PASSWORD" | base64 | tr -d '\n')
deployment=$(curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $token" \
  -F "bundle=@build/central-bundle.zip" \
  "https://central.sonatype.com/api/v1/publisher/upload?name=jmc-$version&publishingType=USER_MANAGED")
echo "publish: uploaded; Central Portal deployment $deployment"
echo "publish: once validated, press Publish for it at https://central.sonatype.com/publishing/deployments"
