# Repository Guidelines

## Project Structure & Module Organization
RestFB ships as a single Maven module. Runtime sources live in `src/main/java/com/restfb/**`; Lombok-decorated definitions sit in `src/main/lombok` and are delomboked during `generate-sources`. Tests mirror the package tree inside `src/test/java` with JSON fixtures and `integration-test.properties` under `src/test/resources`. Helper scripts live in `misc/` and release metadata in `release/`; Maven writes build output to `target/`.

## Build, Test, and Development Commands
`mvn clean package` performs a full compile/test/package cycle and leaves consumable JARs in `target/`. Use `mvn test` for the fast JUnit 5 suite, and only fall back to `mvn -DskipTests package` when you need a quick binary and have already run tests elsewhere. `mvn jacoco:report` regenerates coverage for SonarCloud under `target/site/jacoco`, and maintainers can drive publishing with `./release.sh <version>`.

## Coding Style & Naming Conventions
Target Java 11 and UTF-8 encoding as defined in `pom.xml`. Stick to 4-space indentation, braces on the same line, UpperCamelCase for types, lowerCamelCase for members, and UPPER_SNAKE for constants. Mutable Graph models should remain package-private helpers; exported DTOs favor Lombok builders or value annotations—always edit the `src/main/lombok` versions so the delomboked copies stay current. Mirror Graph API field names with `@JsonProperty` whenever casing diverges.

## Testing Guidelines
JUnit 5, Mockito, and AssertJ are available out of the box, while JaCoCo already hooks into the lifecycle to capture coverage. Unit tests follow `<ClassName>Test` naming and should assert both success and failure paths. Integration tests in `com.restfb.integration` require copying `src/test/resources/integration-test.sample.properties` to `integration-test.properties`, filling app/user/page tokens, and optionally setting `writeToFacebook=true`; without that file they self-skip via JUnit assumptions.

## Commit & Pull Request Guidelines
Commits follow the conventional prefix style seen in history (`build:`, `fix:`, `feat:`, `doc:`) with imperative subjects and optional scopes. Pull requests should explain intent, list verification steps (`mvn test`, screenshots, manual Graph calls), and link issues. Update `CHANGELOG.md` and docs whenever public types or API mappings change, and consult `SECURITY.md` for any vulnerability disclosures to avoid leaking credentials or tokens.
