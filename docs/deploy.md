## Release Checklist
- [ ] Create `release` branch
- [ ] Update version in all source and documentation
  - [ ] Update README.md
  - [ ] Update docs/docker-hub.md
- [ ] Verify that project builds
  - [ ] standalone jar
      - `mvn clean package`
  - [ ] Docker:
      - `set VERSION=x.y.z`
      - `build-docker.bat`
- [ ] Test build artifacts
  - [ ] standalone jar
    - `cd target/`
    - `unzip -d throw-voice`
    - _set all variables_
    - `./start.bat`
  - [ ] Docker:
    - `docker run --rm -it --env-file dev-b2.env --env-file dev-bot.env --env-file dev-rollbar.env gdragon/throw-voice:%version%`
- [ ] `git merge --squash release` _on master_
- [ ] Create tag
- [ ] Push tag to Github
- [ ] Generate builds by redoing verify project
- [ ] Push `latest` and tagged Docker image and update README on Docker Hub
  - `docker tag gdragon/throw-voice:<version> gdragon/throw-voice:latest`
  - `docker push gdragon/throw-voice:latest`
  - `docker push gdragon/throw-voice:%VERSION%`


## Deploy Checklist

- [ ] `docker-compose down`
- [ ] `scp` updated env files, nginx.conf, and docker-compose.yml
- [ ] `docker-compose up -d`
- [ ] `docker-compose logs --follow --tail 200 pawa` _just to make sure it's all going well_
