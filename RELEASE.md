# ICyou 0.3.0 release checklist

## Before tagging

- [ ] Confirm the release commit is on `main` and all seven CI jobs passed.
- [ ] Confirm `mod_version=0.3.0` in `gradle.properties`.
- [ ] Run `gradlew clean test build nativeSmokeTest` with Java 21.
- [ ] Open `build/libs/icyou-0.3.0.jar` and confirm it is the main release JAR.
- [ ] Test a copy of a 0.2.0 world and review its migration report.
- [ ] Confirm README, changelog, requirements, known limits, and repository links.
- [ ] Confirm Fabric API is listed as a required dependency on CurseForge.

## Publish

Create and push the signed-off version tag:

```bash
git tag v0.3.0
git push origin v0.3.0
```

The workflow builds from the tag, verifies the package, creates the GitHub
release, and uploads to CurseForge only when both CurseForge secrets are set.

## After publishing

- [ ] Confirm all release workflow jobs passed.
- [ ] Download the GitHub release JAR and confirm its name is
  `icyou-0.3.0.jar`.
- [ ] Install that downloaded JAR on a clean client and dedicated server.
- [ ] Confirm the GitHub release notes and CurseForge changelog are readable.
- [ ] Publish the checksum and keep the tested 0.2.0 migration backup.
