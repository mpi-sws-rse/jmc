#!/bin/bash

echo $(pwd)

# Get secure files from GitLab
curl --silent "https://gitlab.com/gitlab-org/incubation-engineering/mobile-devops/download-secure-files/-/raw/main/installer" | bash

# Import keys
gpg --import .secure_files/private.gpg
mv .secure_files/gradle.properties gradle.properties

./gradlew clean
./gradlew publish

if [ ! -d "build" ]; then
  mkdir -p build
else
  rm -rf build/*
fi

mv agent/build/staging-deploy build/jmc-agent
mv core/build/staging-deploy build/jmc

cd build/jmc-agent && zip -r jmc-agent.zip org && cd -
cd build/jmc && zip -r jmc.zip org && cd -
