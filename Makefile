TIMESTAMP ?= $(shell date -u +%FT%TZ)
VERSION ?= dev
REVISION ?= $(shell git rev-parse --short HEAD)

build:
	docker build --build-arg VCS_REF=${REVISION} --build-arg BUILD_DATE=${TIMESTAMP} --build-arg VERSION=${VERSION} -t pawabot/pawa:${VERSION} .

package:
	mvn.exe -Dtimestamp=${TIMESTAMP} -Dversion=${VERSION} -Drevision=${REVISION} package

clean:
	mvn.exe clean
