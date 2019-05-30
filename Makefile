TIMESTAMP = $(shell date -u +%FT%TZ)
VERSION ?= dev
REVISION = $(shell git rev-parse --short HEAD)

package:
	mvn.exe -Dtimestamp=${TIMESTAMP} -Dversion=${VERSION} -Drevision=${REVISION} package

clean:
	mvn.exe clean
