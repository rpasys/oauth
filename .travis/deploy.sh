#!/usr/bin/env bash
set -ev

echo "GPG_OWNERTRUST: $GPG_OWNERTRUST";
echo "GPG_SECRET_KEY: $GPG_SECRET_KEY";
echo "GPG_EXECUTABLE: $GPG_EXECUTABLE";

if [[ "$TRAVIS_TAG" =~ ^oauth-parent-[[:digit:].]+$ ]]; then
  echo "RELEASE TAG -> publish $TRAVIS_TAG to mvn central";
  #mvn deploy javadoc:javadoc gpg:sign -Prelease -DskipTests -B -U -Pwildfly;
else
  echo "NO RELEASE TAG -> don't publish to mvn central";
  #mvn package -U -Pwildfly;
fi


