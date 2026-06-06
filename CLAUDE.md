# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this library is

`jcrepdl` is a Java implementation of **ISO/IEC 19757-7 (CREPDL — Character Repertoire Description Language)**. It parses CREPDL v2.0 XML scripts, expands references and registries, and validates whether characters, grapheme clusters, or whole strings belong to the described Unicode repertoire. Results are three-valued: `TRUE` / `FALSE` / `UNKNOWN`.

## Build & test

- Java **17 minimum** (uses sealed interfaces, records, pattern matching). CI matrix: 17, 21, 25.
- Maven build via `com.helger:parent-pom:3.0.5`. Standard `mvn install` / `mvn test` apply — nothing custom.
- Snapshot deploy on JDK 17 only, via the `release-snapshot` profile.

## Constraints enforced by the build

- `forbiddenapis` (bound by the parent POM) blocks `System.out`/`System.err`, JDK-internal/unsafe/reflection APIs, and other unsafe calls. The only exclusion is `CREPDLBenchmark*` (prints a report by design). **Do not add new exclusions** — change the code to use `Logger` instead.
- Only `slf4j-simple` is on the **test** classpath; production code logs via SLF4J without pulling in a binding.

## Architecture (one-paragraph mental model)

Public facade is `com.helger.crepdl.validate.CREPDLValidator` — build it from a CREPDL script, then call `validate(...)`. The AST is a sealed hierarchy under `com.helger.crepdl.model` (`ICREPDLNode` + six records: union, intersection, difference, ref, repertoire, char). Repertoires resolve through a sealed `IRegistry` hierarchy in `com.helger.crepdl.repertoire` (ISO 10646, CLDR, IANA, IVD). Validation mode is `EMode.CHARACTER` or `EMode.GRAPHEME_CLUSTER`. Three-valued logic flows through `EThreeValuedBoolean` and surfaces as `ECREPDLValidationResult`.

## Things that look wrong but aren't

- **CLDR registry is intentionally unimplemented** — `CREPDLValidator` throws `CREPDLParseException` at build time when a script references it. Don't "fix" this without a plan.
- **CREPDL v1.0 namespace is rejected at parse time.** v2.0 only.
- **`<char>` content accepts two dialects** (F# v2.0 regex syntax *and* ISO/IEC 19757-7:2020 `U+XXXX` / `U+XXXX-U+YYYY` literals). Detection happens in `CodePointSyntax.toRegexOrNull()` — don't collapse the two paths.
- The regex flavour is `java.util.regex.Pattern` with `COMMENTS | UNICODE_CHARACTER_CLASS`. ICU-only features (`\N{...}`, internal `&&` set intersection) are not supported and should not be added.

## Repository conventions specific to this repo

- `.crepdl` files are tracked as **binary** (`.gitattributes`). Do not try to read them as text or include them in textual diffs — open them with a hex/byte view if you need to inspect bytes.
- Bundled CREPDL fixtures and ranges live under `src/main/resources/external/crepdl/repo/` (~85 files, includes a ~1.3 MB `IVD_Sequences.txt`). Treat these as vendored data — update deliberately, not as cleanup.
- Tests use **JUnit 5 (Jupiter 5.11.3)**. No Hamcrest, no AssertJ — stay on Jupiter assertions to match the existing suites.

## Style

Global rules in `~/.claude/rules/` already cover Hungarian notation, ph-commons usage, `LOGGER` declaration, inline-concatenation log calls (no SLF4J `{}` placeholders), `ID` always uppercase, space-before-paren formatting, and `final` parameters. Match those when editing — the existing sources already do.
