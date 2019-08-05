#!/bin/bash

export SCRIPT_LOCT=$( cd $( dirname $0 ); pwd )
cd $SCRIPT_LOCT/../..

mvn clean
docker build -t github-api-mirror --file=resources/docker/Dockerfile .
