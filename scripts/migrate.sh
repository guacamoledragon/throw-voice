#!/usr/bin/env bash

cp "$PWD/data/database/settings.db" "$PWD/data/database/settings-$(date +%Y-%m-%dT%H_%M%::z).db"

docker run \
    --rm \
    -v "$PWD/sql:/flyway/sql" \
    -v "$PWD/conf:/flyway/conf" \
    -v "$PWD/data:/flyway/data" \
    boxfuse/flyway \
    migrate
