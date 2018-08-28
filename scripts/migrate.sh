#!/usr/bin/env bash
docker run \
    --rm \
    -v $PWD/sql:/flyway/sql \
    -v $PWD/conf:/flyway/conf \
    -v $PWD/data:/flyway/data \
    boxfuse/flyway \
    migrate
