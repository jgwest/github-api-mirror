#!/bin/bash

docker login

docker tag github-api-mirror jgwest/github-api-mirror

docker push jgwest/github-api-mirror

