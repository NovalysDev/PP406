# PP406

PP406 is an Android diagnostic application for the Peugeot 406 Coupé, developed by **Novalys Apps**. It aims to support non-multiplexed, semi-multiplexed and multiplexed vehicles. Support depends on the engine, ECU and specific part number; some functions are still being developed or require vehicle validation.

**Application language: French.**

## About this release

This initial release contains the Java source code for version 0.12.55, Android resources and development tests. It does **not** include the signing key, private recordings, or Peugeot communication tables and catalogues extracted from PP2000. Some application features require those separately obtained data files; building this repository alone does not produce a complete diagnostic application. It is not a replacement for the version distributed by Novalys Apps.

Siemens D9 BSI identification is experimental and has not yet been validated on a vehicle. Simulated tests or the presence of a menu do not establish that an operation works on a vehicle. Do not use a development build for write, coding or safety-related operations on a vehicle without appropriate validation.

## Repository contents

- `src/`: Android application and diagnostic protocols.
- `res/`: Android resources and graphics.
- `research-tests/`: development tests, not run automatically by Gradle.
- `licenses/`: licences for the identified third-party references used by the corresponding components.

## Development build

Requirements: JDK 17, Android SDK 36, Gradle 8.14 and Android Gradle Plugin 8.13.

Run `./gradlew assembleDebug` (or `gradlew.bat assembleDebug` on Windows) to build a development APK. The missing diagnostic data must be obtained and added separately, with the appropriate rights. The development build has a different signing certificate from the distributed application; do not uninstall the existing application to bypass a signature mismatch.

## Rights

© Novalys Apps. Publishing the source code does not in itself grant a licence to reuse it. Peugeot, Citroën, Lexia and PP2000 are the property of their respective owners. Identified third-party references are accompanied by their licences in `licenses/`.
