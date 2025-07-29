#!/bin/bash

# Get secure files from GitLab
curl --silent "https://gitlab.com/gitlab-org/incubation-engineering/mobile-devops/download-secure-files/-/raw/main/installer" | bash

# Import keys
gpg --import .secure_files/maven.gpg
rm gradle.properties
mv .secure_files/gradle.properties gradle.properties

./gradlew clean
./graldew publish

if [ ! -d "build" ]; then
  mkdir -p build
else
  rm -rf build/*
fi

zip -r build/jmc-agent.zip agent/build/staging-deploy
zip -r build/jmc.zip core/build/staging-deploy
