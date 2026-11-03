# DevGram Android build rule

- Before every APK build requested for installation, testing on a device, or publication, increment `APP_VERSION_CODE` in `gradle.properties` by exactly 1.
- Never reuse a previously installed or published `versionCode` for a new APK.
- After packaging, verify the APK metadata with `aapt dump badging` and confirm that the resulting ABI-specific `versionCode` is newer before installing or publishing it.
- Every user-visible feature, behavior change, or bug fix must be committed immediately after its proportional verification. Use a separate commit for each logically independent update or fix; do not leave user-facing work only in the working tree.
- Administrative/server-only work and internal changes must also be committed. Prefix their commit subject with `[skip changelog]` so they remain in history but never appear in the user-facing beta changelog.
