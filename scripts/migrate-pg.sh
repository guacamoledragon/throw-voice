#!/usr/bin/env bash

docker run \
    --rm \
    --link postgres:postgres \
    -v "$PWD/sql:/flyway/sql" \
    -v "$PWD/conf:/flyway/conf" \
    -v "$PWD/data:/flyway/data" \
    flyway/flyway:7.8-alpine \
    migrate
