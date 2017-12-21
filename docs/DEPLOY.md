## Deploy Checklist

- [ ] Verify that project builds
- [ ] Merge work to `master` branch
- [ ] Test build artifacts
  - [ ] Docker:
    - `build-docker.bat`
  - [ ] standalone jar
    - `mvn clean package; source .env; java -cp "throw-voice.jar:lib/*" tech.gdragon.App`
- [ ] Update README.md
- [ ] Create tag
- [ ] Push tag to Github
- [ ] Update docs/HUB-README.md
- [ ] Rename standalone jar with proper version
- [ ] Tag Docker image with proper version
- [ ] Push `latest` and tagged Docker image and update README on Docker Hub
  - `docker tag gdragon/throw-voice:<version> gdragon/throw-voice:latest`
- [ ] Create a new "Release" on Github and upload standalone jar
