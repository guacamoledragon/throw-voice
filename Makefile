TIMESTAMP ?= $(shell date -u +%FT%TZ)
VERSION ?= dev
REVISION ?= $(shell git rev-parse --short HEAD)

# Source: https://stackoverflow.com/a/43566158/358059
SHELL := /bin/bash

build:
	docker build --build-arg VCS_REF=${REVISION} --build-arg BUILD_DATE=${TIMESTAMP} --build-arg VERSION=${VERSION} -t pawabot/pawa:${VERSION} .

package:
	mvn.exe -Dtimestamp=${TIMESTAMP} -Dversion=${VERSION} -Drevision=${REVISION} package

clean:
	mvn.exe clean

download-db-backup:
	source restic/b2_env.sh
	restic dump latest '/opt/pawa/data/database/settings.db.backup' > data/database/settings.db

minio:
	docker run --name minio --rm \
	       --env-file=${PWD}/conf/prod.env \
	       -e TZ=America/Los_Angeles \
	       -p 9000:9000 \
	       minio/minio:RELEASE.2020-04-10T03-34-42Z gateway b2

