#!/bin/bash

export SCRIPT_LOCT=$( cd $( dirname $0 ); pwd )
cd $SCRIPT_LOCT

NUM_GAM_DATA_VOLUMES=`docker volume ls | grep "github-api-mirror-data-volume" | wc -l`

set -e

if [ "$NUM_ZAM_DATA_VOLUMES" != "1" ]; then
    docker volume create github-api-mirror-data-volume
fi
set +e

docker rm -f github-api-mirror-container > /dev/null 2>&1

set -e

# Erase the config volume after each start
docker volume rm -f github-api-mirror-config-volume
docker volume create github-api-mirror-config-volume

docker run  -d  -p 9443:9443 --name github-api-mirror-container \
    -v github-api-mirror-data-volume:/home/default/data \
    -v $SCRIPT_LOCT/../../GitHubApiMirrorLiberty/resources/github-settings.yaml:/config/github-settings.yaml \
    -v github-api-mirror-config-volume:/config \
    --restart always \
    --cap-drop=all \
    --tmpfs /opt/ol/wlp/output --tmpfs /logs \
    --read-only \
    github-api-mirror


