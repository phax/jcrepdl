# jcrepdl

A Java 17 implementation of **ISO/IEC 19757-7** &mdash; the *Character Repertoire
Description Language* (CREPDL).

This is a from-scratch Java port of the F# reference implementation by
**MURATA Makoto / CITPC** (https://github.com/CITPCSHARE/CREPDL, branch
`Version2`).  The library can parse CREPDL XML scripts, expand them, and
evaluate arbitrary characters / grapheme clusters / strings / streams against
them, returning a three-valued result (`TRUE`, `FALSE`, `UNKNOWN`).

---

## Table of contents

1.  [What is CREPDL?](#1-what-is-crepdl)
2.  [The CREPDL XML vocabulary](#2-the-crepdl-xml-vocabulary)
3.  [The three-valued result](#3-the-three-valued-result)
4.  [Character vs. grapheme-cluster mode](#4-character-vs-grapheme-cluster-mode)
5.  [Registries of predefined repertoires](#5-registries-of-predefined-repertoires)
6.  [What this project does](#6-what-this-project-does)
7.  [Project architecture](#7-project-architecture)
8.  [Bundled data](#8-bundled-data)
9.  [Build, install, dependency](#9-build-install-dependency)
10. [Public Java API](#10-public-java-api)
11. [Example CREPDL scripts](#11-example-crepdl-scripts)
12. [The benchmark](#12-the-benchmark)
13. [Testing and the corpus suite](#13-testing-and-the-corpus-suite)
14. [Limitations](#14-limitations)
15. [Security considerations](#15-security-considerations)
16. [Differences from the F# reference](#16-differences-from-the-f-reference)
17. [License](#17-license)

---

## 1. What is CREPDL?

**CREPDL** is part 7 of ISO/IEC 19757 *Document Schema Definition Languages
(DSDL)*.  It is an XML vocabulary for describing a **character repertoire**:
the set of Unicode characters (or grapheme clusters) that a class of
documents is allowed to contain.

A CREPDL script answers one question for any given input character `c`:

> *Is `c` a member of the described repertoire?*

The answer is one of three values:

| Result    | Meaning                                                                                          |
| --------- | ------------------------------------------------------------------------------------------------ |
| `TRUE`    | `c` is definitely in the repertoire                                                              |
| `FALSE`   | `c` is definitely not in the repertoire                                                          |
| `UNKNOWN` | The script's lower bound (kernel) does not include `c` and the upper bound (hull) does include `c` |

Why three values? Because CREPDL scripts can describe repertoires
approximately, using a tight lower bound (the **kernel**, e.g. a precise regex
of allowed characters) and a loose upper bound (the **hull**, e.g. a wider set
that surely contains the kernel). Characters that fall in the gap between
kernel and hull can neither be confirmed nor rejected; they are *unknown*.

CREPDL is XML namespace `http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0`.
This library handles **version 2.0 only**; v1.0 documents are rejected at
parse time with an "Illegal namespace" error.

---

## 2. The CREPDL XML vocabulary

A CREPDL script's root element must be one of six concrete elements. Every
element accepts three optional common attributes that govern its scope.

### 2.1 Common attributes

| Attribute       | On every CREPDL element | Meaning                                                                                                  |
| --------------- | :---------------------: | -------------------------------------------------------------------------------------------------------- |
| `mode`          | yes                     | `"character"` (default) or `"graphemeCluster"`. Only the root mode is honoured; nested ones are advisory. |
| `minUcsVersion` | yes                     | Encoded version like `"5.2"`. The lower bound of the Unicode version window.                              |
| `maxUcsVersion` | yes                     | Encoded version like `"17.0"`. The upper bound of the Unicode version window.                             |

Versions are internally encoded as `major*10000 + minor*100 + patch`
(see `CCREPDL.versionString2Int(String)`). The defaults are `2.0` and `17.0`.

### 2.2 Element reference

| Element                 | Children                                            | Purpose                                                                                                                                                                                                                            |
| ----------------------- | --------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `<union>`               | zero or more CREPDL elements                        | Set union of children.  Empty union matches nothing.                                                                                                                                                                              |
| `<intersection>`        | zero or more CREPDL elements                        | Set intersection.  Empty intersection matches everything.                                                                                                                                                                          |
| `<difference>`          | one or more CREPDL elements                         | First child minus the union of the remaining children.                                                                                                                                                                            |
| `<ref href="URL"/>`     | none in source; one after expansion                 | Reference to another CREPDL script. The expander loads the script (relative to the document URI) and inlines its root. Self-reference / cycles raise a parse error.                                                                |
| `<repertoire registry=… name=… number=… version=…/>` | no CREPDL children                                  | Names a predefined collection from one of four registries: `10646`, `IANA`, `IVD`, `CLDR`.                                                                                                                                       |
| `<char>` *regex source* | optional `<kernel>`+`<hull>` sub-elements           | An ICU-flavoured regex (treated as a Java `Pattern` with `COMMENTS \| UNICODE_CHARACTER_CLASS` flags). Plain text content sets both kernel and hull to the same pattern; otherwise the children supply kernel and/or hull separately. |

### 2.3 Set-operation semantics

The set operations are three-valued. Letting *X, Y* be three-valued
membership of an element in two child repertoires, the rules are:

`union(X, Y)`

| X \ Y | T | F | U |
|-------|---|---|---|
| T     | T | T | T |
| F     | T | F | U |
| U     | T | U | U |

`intersection(X, Y)`

| X \ Y | T | F | U |
|-------|---|---|---|
| T     | T | F | U |
| F     | F | F | F |
| U     | U | F | U |

`difference(first, ∪(rest))`: only `TRUE` if the first child says `TRUE`
and the rest says `FALSE`. Other combinations collapse to `FALSE` or
`UNKNOWN` per the standard table.

### 2.4 The `<char>` element

`<char>` describes a regex (or a set of code-point literals; see [&sect;2.5](#25-char-content-dialects-regex-vs-code-point-literal))
over a single character or grapheme cluster. There are four legal shapes:

```xml
<!-- (1) Plain regex: both kernel and hull are this pattern -->
<char xmlns="http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0">[A-Z]</char>

<!-- (2) Kernel only: matching characters are TRUE, others UNKNOWN -->
<char xmlns="http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0">
  <kernel>[A-Z]</kernel>
</char>

<!-- (3) Hull only: matching characters are UNKNOWN, others FALSE -->
<char xmlns="http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0">
  <hull>[A-Z]</hull>
</char>

<!-- (4) Both: classical three-valued shape. Matching kernel -> TRUE;
     not matching hull -> FALSE; in between -> UNKNOWN. -->
<char xmlns="http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0">
  <kernel>[A-Z]</kernel>
  <hull>[A-Za-z]</hull>
</char>
```

### 2.5 `<char>` content dialects: regex vs. code-point literal

Two ISO/IEC 19757-7 revisions disagree on what the text inside `<char>`,
`<kernel>`, and `<hull>` means while sharing the same XML namespace:

| Revision | `<char>` content syntax | Example |
| --- | --- | --- |
| 2014-era v2.0 (F# reference) | ICU regular expression                                           | `[A-Z]`, `\p{Mn}`, `[\x{0020}-\x{007E}]` |
| ISO/IEC 19757-7:2020         | Whitespace-separated code-point literals and ranges, *no regex* | `U+0020`, `U+0020-U+007E`, `U+0009 U+000A U+0020-U+007E` |

This library transparently supports **both dialects**:

- If the trimmed text content matches the code-point-literal pattern
  (one or more whitespace-separated tokens, each either `U+HEX` or
  `U+HEX-U+HEX` with values in `0..0x10FFFF`, ranges non-reversed), it is
  translated at parse time into the equivalent Java regex character class
  (e.g. `U+0020-U+007E` &rarr; `[\x{0020}-\x{007E}]`).
- Otherwise, the content is used verbatim as a Java regex (the original
  v2.0 behaviour).

The detection is unambiguous: a literal regex containing only `U+XXXX`
tokens cannot match a single Unicode character anyway (it would try to
match the 6-character string `"U+0020"`), so the new interpretation is
strictly better. All existing regex scripts continue to work unchanged.

Detection is implemented by `CodePointSyntax.toRegexOrNull(String)`.

Decision table for `check(s, char)`:

| kernel | hull | kernel matches `s` | hull matches `s` | result    |
| ------ | ---- | ------------------ | ---------------- | --------- |
| set    | -    | yes                | -                | `TRUE`    |
| set    | -    | no                 | -                | `UNKNOWN` |
| -      | set  | -                  | yes              | `UNKNOWN` |
| -      | set  | -                  | no               | `FALSE`   |
| set    | set  | yes                | yes              | `TRUE`    |
| set    | set  | no                 | yes              | `UNKNOWN` |
| set    | set  | no                 | no               | `FALSE`   |
| set    | set  | yes                | no               | `TRUE` (kernel implies hull was supposed to match too) |

---

## 3. The three-valued result

Internally the library carries an `EThreeValuedBoolean` (package-private
helpers `union`, `intersection`) and the API surface returns the equivalent
`ECREPDLValidationResult` (`TRUE` / `FALSE` / `UNKNOWN`) with
`isTrue()` / `isFalse()` / `isUnknown()` convenience checks.

For *stream* validation (`validateString`, `validateTextStream`,
`validateFile`), the library returns a `CREPDLStreamValidationResult` record
with two `String[]` arrays:

- `unknowns()` &mdash; units that evaluated to `UNKNOWN`
- `notIncluded()` &mdash; units that evaluated to `FALSE`

Units that evaluated to `TRUE` are not collected.

---

## 4. Character vs. grapheme-cluster mode

The CREPDL root element's `mode` attribute determines how the input stream
is **broken into evaluation units**:

| Mode               | Unit                                                                                                                                  |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------- |
| `character`        | A single Unicode code point (one Java `char`, or a surrogate pair as a two-`char` string). Implemented by `CharacterEnumerator`.       |
| `graphemeCluster`  | A user-perceived "character" per Unicode UAX #29, e.g. base + combining marks + emoji ZWJ sequences. Uses `java.text.BreakIterator`. |

If a validator is built for one mode and the wrong API is called
(`validateCharacter` on a grapheme-cluster script, or vice versa),
`IllegalStateException` is thrown.

---

## 5. Registries of predefined repertoires

`<repertoire>` references one of four named registries:

### 5.1 `registry="10646"` &mdash; ISO/IEC 10646 collections

Selected by `@name` (e.g. `"BASIC LATIN"`, `"BMP"`, `"Age10-0"`,
`"IICORE"`) or by `@number` (e.g. `1`, `300`, `1000`, `370`).

The library ships with three internal indexes derived from the upstream
collection definitions:

| Index           | Storage                                                                                                       | Count |
| --------------- | ------------------------------------------------------------------------------------------------------------- | ----: |
| Inline          | One line per collection in `iso10646-inline.txt`, range list                                                  |   323 |
| Out-of-line     | A separate bundled `.txt` file of hex code points per range (e.g. `IICORE.txt`, `Age10-0.txt`, `Age17-0.txt`) |    44 |
| CREPDL-script   | A bundled XML fragment that defines the collection via `<union>`/`<intersection>`/`<difference>` of others    |    35 |

Lookups go through `ISO10646Collections.lookupInlineByName/Number`,
`lookupOutOfLineResource`, `lookupCREPDLScriptResource`.

### 5.2 `registry="IANA"` &mdash; IANA charsets

`<repertoire registry="IANA" name="ISO-8859-1"/>` is implemented by a
round-trip through `java.nio.charset.Charset`: a candidate string is in
the repertoire iff `decode(encode(s))` equals `s` exactly (no
unmappable / malformed input).  The `name` is the JVM charset name; the
IANA *MIB enumeration* (`number`) is **not** supported (matching the F#
reference).

### 5.3 `registry="IVD"` &mdash; Ideographic Variation Database

`<repertoire registry="IVD" name="Adobe-Japan1"/>` validates 3-or-4-char
strings of the form *base + variation selector* against the bundled
`IVD_Sequences.txt`.  Valid variation selectors are
`U+E0100..U+E0120`.

### 5.4 `registry="CLDR"`

Recognised by the parser but **not implemented**.  Building a validator
that references CLDR throws `CREPDLParseException("CLDR is not supported
yet")`, matching the F# reference.

---

## 6. What this project does

A Java 17 implementation that reads CREPDL XML, expands it (resolving
`<ref>` chains and CREPDL-defined ISO 10646 collections), pre-builds
repertoire dictionaries, and answers per-character / per-string queries
with three-valued results.

What was ported, broken down by concern:

| Concern                                  | Java type                                                                                                                       |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| Namespace, attribute, mode literals      | `CCREPDL`                                                                                                                       |
| Modes (CHARACTER / GRAPHEME_CLUSTER)     | `EMode`                                                                                                                         |
| Internal 3-valued logic                  | `EThreeValuedBoolean` (with `union` / `intersection` lazy ops)                                                                  |
| Public 3-valued result                   | `ECREPDLValidationResult`                                                                                                       |
| Sealed CREPDL node hierarchy             | `ICREPDLNode` + records `CREPDLUnion`, `CREPDLIntersection`, `CREPDLDifference`, `CREPDLRef`, `CREPDLRepertoire`, `CREPDLChar` |
| Sealed registry hierarchy                | `IRegistry` + records `RegistryISO10646`, `RegistryCLDR`, `RegistryIANA`, `RegistryIVD`                                         |
| XML parser                               | `CREPDLReader` (validates namespace, element/attribute set, content model)                                                      |
| Ref + ISO10646-collection expander       | `RefAndRepertoireExpander` (with cycle detection on both axes)                                                                  |
| Registry &rarr; Repertoire dictionary    | `RegistryRepertoireDictionary`                                                                                                  |
| Code-point range repertoire              | `CodePointRangeRepertoire` (sorted binary search; hex/decimal parser, BOM-strip)                                                |
| ISO 10646 collection lookup tables       | `ISO10646Collections`                                                                                                           |
| IANA charset round-trip                  | `IANARepertoires`                                                                                                               |
| IVD bench against `IVD_Sequences.txt`    | `IVDRepertoires`                                                                                                                |
| Regex evaluator                          | `CharMatcher` (Java `Pattern` with `COMMENTS \| UNICODE_CHARACTER_CLASS`)                                                       |
| 2020 code-point-literal `<char>` content | `CodePointSyntax` (translates `U+XXXX` / `U+XXXX-U+YYYY` to a Java regex character class)                                       |
| Three-valued evaluator                   | `StringChecker`                                                                                                                 |
| Character iterator                       | `CharacterEnumerator`                                                                                                           |
| Grapheme-cluster iterator                | `GraphemeClusterEnumerator` (`java.text.BreakIterator`)                                                                         |
| Public facade                            | `CREPDLValidator`                                                                                                               |

---

## 7. Project architecture

```
com.helger.crepdl
├── CCREPDL                          // namespace, element names, attribute names, default versions
├── EMode                            // CHARACTER, GRAPHEME_CLUSTER
├── ECREPDLValidationResult          // TRUE, FALSE, UNKNOWN  (public)
├── EThreeValuedBoolean              // TRUE, FALSE, UNKNOWN  (internal, with union/intersection helpers)
│
├── model/                           // immutable sealed AST records
│   ├── ICREPDLNode  (sealed)
│   ├── CREPDLUnion, CREPDLIntersection, CREPDLDifference
│   ├── CREPDLRef, CREPDLRepertoire, CREPDLChar
│   ├── IRegistry  (sealed)
│   └── RegistryISO10646, RegistryCLDR, RegistryIANA, RegistryIVD
│
├── parse/                           // XML -> AST
│   ├── CREPDLReader                 // DOM-based, namespace + attribute + element validation
│   └── CREPDLParseException
│
├── repertoire/                      // concrete repertoires
│   ├── IRepertoire                  // String -> EThreeValuedBoolean
│   ├── CodePointRangeRepertoire     // binary search over sorted ranges
│   ├── ISO10646Collections          // lookup tables (inline / out-of-line / CREPDL-script)
│   ├── IANARepertoires              // round-trip via java.nio.charset
│   └── IVDRepertoires               // IVD_Sequences.txt
│
├── validate/                        // pipeline + facade
│   ├── RefAndRepertoireExpander     // <ref> + CREPDL-script collection expansion (cycle-safe)
│   ├── RegistryRepertoireDictionary // pre-builds one IRepertoire per IRegistry in the tree
│   ├── CharMatcher                  // compiles + evaluates a CREPDLChar against a candidate
│   ├── StringChecker                // three-valued tree walk
│   ├── CharacterEnumerator          // BMP + surrogate-pair iteration
│   ├── GraphemeClusterEnumerator    // UAX #29 cluster iteration
│   ├── CREPDLStreamValidationResult // (unknowns[], notIncluded[]) record
│   └── CREPDLValidator              // PUBLIC FACADE
│
└── benchmark/
    └── CREPDLBenchmark              // runnable main: lookups + verifications + coverage report
```

Validation pipeline:

```
   parse XML  ->  expand <ref> and CREPDL-script <repertoire>  ->  build registry-repertoire cache
       |                          |                                            |
       v                          v                                            v
CREPDLReader            RefAndRepertoireExpander         RegistryRepertoireDictionary
       \________________________ |  _____________________________/
                                 v
                          CREPDLValidator
                                 |
                                 v
                          StringChecker (3-valued tree walk)
                                 |
                                 v
                          CharMatcher / IRepertoire
```

---

## 8. Bundled data

The bundled resources are organised by **registry** under
`src/main/resources/external/crepdl/repo/`:

```
external/crepdl/repo/
├── 10646/                            ISO/IEC 10646 collections
│   ├── inline.txt                    323 collections as one number|name|ranges line each
│   ├── outofline.txt                 44-entry index of out-of-line range files
│   ├── crepdl-index.txt              35-entry index of CREPDL-script collections
│   ├── crepdl/                       35 small CREPDL XML scripts
│   │   └── crepdl_*.xml              (e.g. UNICODE 3.1 = union of Age1-1..Age3-1)
│   ├── Age*.txt                      Code points first assigned in each Unicode version
│   │                                  (Age1-1, Age2-0, …, Age16-0, Age17-0)
│   ├── IICORE.txt                    Bundled hex-range files for IICORE,
│   ├── JIExt.txt                     Japanese ideographics extensions
│   ├── JAPANESE *.txt                etc., one file per collection number
│   ├── JapaneseCoreKanji.txt
│   ├── 281.txt / 282.txt             MES-1, MES-2
│   ├── 286.txt / 288.txt             other numbered collections
│   ├── 301.txt / 302.txt             BMP-AMD.7, BMP SECOND EDITION
│   ├── -100285.txt / -200285.txt     synthetic sub-pieces of BASIC JAPANESE
│   ├── -287.txt / -340.txt           synthetic sub-pieces
└── IVD/                              Ideographic Variation Database
    └── IVD_Sequences.txt             (1.3 MB) base + variation selector pairs
```

| File / directory                  | Source                                                  | Purpose                                                                  |
| --------------------------------- | ------------------------------------------------------- | ------------------------------------------------------------------------ |
| `10646/inline.txt`                | extracted from the upstream collection definitions      | 323 collections as one `number\|name\|ranges` line each                  |
| `10646/outofline.txt`             | extracted index                                         | 44 collections mapped to bundled hex range files                         |
| `10646/crepdl-index.txt`          | extracted index                                         | 35 collections mapped to bundled CREPDL XML fragment files (incl. UNICODE 12.0 &hellip; 17.0) |
| `10646/crepdl/`                   | extracted XML fragments                                 | 35 small CREPDL XML scripts (e.g. UNICODE 3.1 = union of Age1-1..Age3-1) |
| `10646/Age*.txt`                  | derived from `DerivedAge.txt`                           | Code points first assigned in each Unicode age, hex `0x...,0x...`        |
| `10646/IICORE.txt`, `JIExt.txt`, `JAPANESE *.txt`, etc. | copied verbatim from upstream `CREPDL/*.txt` | The actual code-point ranges in hex                          |
| `IVD/IVD_Sequences.txt` (1.3 MB)  | copied verbatim from upstream `CREPDL/IVD_Sequences.txt` | IVD base + variation-selector pairs per collection                       |

Total: 83 bundled resource files (47 directly under `10646/`, 35 under
`10646/crepdl/`, 1 under `IVD/`).

The extraction from the upstream collection definitions is mechanical
&mdash; the upstream source has three list literals
(`inLineCollections`, `outOfLineCollections`, `collectionsInCREPDL`) and
an offline Python script walks them, normalising hex ranges to
decimal-pair semicolon lists for the inline file and copying the rest
verbatim.

---

## 9. Build, install, dependency

### Requirements

- **JDK 17** or newer (uses sealed interfaces, records, pattern matching for `instanceof`). CI matrix: 17, 21, 25.
- **Maven 3.9+**.

### Building

```bash
mvn clean install
```

This runs the test suite (145 tests, see [&sect;13](#13-testing-and-the-corpus-suite))
and also runs the `forbiddenapis` Maven plugin (`check` + `testCheck` goals)
to forbid `jdk-unsafe`, `jdk-deprecated`, `jdk-internal`, `jdk-non-portable`,
`jdk-system-out`, and `jdk-reflection` API usage.

### Maven coordinates

```xml
<dependency>
  <groupId>com.helger.crepdl</groupId>
  <artifactId>jcrepdl</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Runtime dependencies

| Artifact                              | Why                                                  |
| ------------------------------------- | ---------------------------------------------------- |
| `com.helger.commons:ph-base`          | `IHasID`, `EnumHelper`, `NonBlockingBufferedReader`, `StringHelper` |
| `com.helger.commons:ph-annotations`   | (transitive of `ph-base`) `com.helger.annotation.concurrent.Immutable` |
| `org.jspecify:jspecify`               | `@NonNull` / `@Nullable` nullness annotations         |
| `org.slf4j:slf4j-api`                 | (transitive) logging                                  |

XML parsing uses only the JDK (`javax.xml.parsers.DocumentBuilderFactory`)
&mdash; no `ph-xml` / `ph-commons` dependency.

---

## 10. Public Java API

The single entry point is **`CREPDLValidator`**. It accepts a script from
any of five sources, expands it eagerly, and pre-builds repertoire
dictionaries. Subsequent per-string validations are O(1) lookups against a
3000-entry cache.

### 10.1 Constructing a validator

```java
import com.helger.crepdl.validate.CREPDLValidator;
import com.helger.crepdl.model.ICREPDLNode;
import com.helger.crepdl.parse.CREPDLReader;

import java.io.File;
import java.net.URI;
import java.io.Reader;
import java.io.InputStream;

// From a parsed AST
ICREPDLNode aRoot = CREPDLReader.readScriptFromString (sXml, null);
CREPDLValidator a1 = CREPDLValidator.create (aRoot);

// From a local file
CREPDLValidator a2 = CREPDLValidator.create (new File ("scripts/BasicLatin.crepdl"));

// From a URI (http: or file:)
CREPDLValidator a3 = CREPDLValidator.create (URI.create ("file:///path/to/script.crepdl"));

// From an in-memory string
CREPDLValidator a4 = CREPDLValidator.createFromString (sXml, URI.create ("base:/"));

// From a Reader or InputStream (use CREPDLReader directly)
CREPDLValidator a5 = CREPDLValidator.create (CREPDLReader.readScript (aReader, null));
```

### 10.2 Validating a single character

```java
import com.helger.crepdl.ECREPDLValidationResult;

ECREPDLValidationResult e = aValidator.validateCharacter ("A");
switch (e)
{
  case TRUE    -> System.out.println ("in the repertoire");
  case FALSE   -> System.out.println ("not in the repertoire");
  case UNKNOWN -> System.out.println ("kernel and hull disagree");
}
```

Pass a 2-`char` string for supplementary code points: e.g.
`aValidator.validateCharacter (new String (Character.toChars (0x1F30D)))`
&mdash; the earth-globe emoji U+1F30D.

`validateCharacter` throws `IllegalStateException` if the script's root mode
is `graphemeCluster`. Use `validateGraphemeCluster(String)` in that case.

### 10.3 Validating an entire string or stream

```java
import com.helger.crepdl.validate.CREPDLStreamValidationResult;

CREPDLStreamValidationResult res = aValidator.validateString ("Hello, World!");
System.out.println ("unknowns:     " + java.util.Arrays.toString (res.unknowns ()));
System.out.println ("not included: " + java.util.Arrays.toString (res.notIncluded ()));

// or for any Reader (StringReader, FileReader, etc.)
res = aValidator.validateTextStream (aReader);

// or a file with an explicit charset name
res = aValidator.validateFile (new File ("input.txt"), "UTF-8");
```

The mode of stream iteration is governed by the root mode of the script.

### 10.4 Inspecting the parsed model

If you need to walk the AST yourself (e.g. for analysis or transformation):

```java
ICREPDLNode aNode = CREPDLReader.readScriptFromString (sXml, null);
if (aNode instanceof CREPDLUnion aU)
{
  for (ICREPDLNode aChild : aU.children ())
    ...
}
```

The model is fully sealed; the compiler will warn if a `switch` over
`ICREPDLNode` misses a permitted subtype.

---

## 11. Example CREPDL scripts

All examples use the v2 namespace prefix `xmlns="http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0"`.

### 11.1 A plain regex over BASIC LATIN

```xml
<char xmlns="http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0">
  [\p{Alpha}]
</char>
```

### 11.1b The same script in ISO/IEC 19757-7:2020 code-point-literal syntax

```xml
<!-- Equivalent to <char>[\x{0020}-\x{007E}]</char> -->
<char xmlns="http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0">
  U+0020-U+007E
</char>
```

A multi-range example (the SignalArc sample schema, accepted verbatim by
this library):

```xml
<union xmlns="http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0">
  <char>U+0009</char>
  <char>U+000A</char>
  <char>U+000D</char>
  <char>U+0020-U+007E</char>
  <char>U+00A0</char>
  <char>U+00C0-U+00FF</char>
  <char>U+2013-U+2014</char>
  <char>U+2018-U+201E</char>
  <char>U+2026</char>
</union>
```

### 11.2 ISO 8859-1 by union

```xml
<union xmlns="http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0">
  <repertoire registry="10646" number="1"/>     <!-- BASIC LATIN -->
  <repertoire registry="10646" number="2"/>     <!-- LATIN-1 SUPPLEMENT -->
</union>
```

Or equivalently via IANA round-trip:

```xml
<repertoire xmlns="http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0"
            registry="IANA" name="ISO-8859-1"/>
```

### 11.3 BMP minus uppercase ASCII

```xml
<difference xmlns="http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0">
  <repertoire registry="10646" number="300"/>   <!-- BMP -->
  <char>[A-Z]</char>
</difference>
```

### 11.4 Kernel/hull approximation

```xml
<char xmlns="http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0">
  <kernel>[\p{IsHan}]</kernel>
  <hull>[一-鿿]</hull>
</char>
```

### 11.5 Adobe-Japan1 IVD collection, grapheme-cluster mode

```xml
<repertoire xmlns="http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0"
            mode="graphemeCluster"
            registry="IVD" name="Adobe-Japan1"/>
```

### 11.6 Composing via `<ref>`

```xml
<!-- Top-level script -->
<union xmlns="http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0">
  <ref href="basic-latin.crepdl"/>
  <ref href="hiragana.crepdl"/>
  <ref href="katakana.crepdl"/>
</union>
```

`<ref>` cycles are detected at expansion time and raise a parse error.

---

## 12. The benchmark

`com.helger.crepdl.benchmark.CREPDLBenchmark` is a runnable main with three
sections:

### 12.1 Section 1 &mdash; Lookups

Throughput of `ISO10646Collections` lookups: by name, by number, for
inline / out-of-line / CREPDL-script collections.

### 12.2 Section 2 &mdash; Verifications

`validateCharacter` and `validateString` throughput for three
representative scripts (BASIC LATIN, BMP, Unicode 8.0), including
chars/sec for a 13-char short string and a ~3.6 KB long string.

### 12.3 Section 3 &mdash; Coverage assessment for an arbitrary text

For the input text (default: `"Hello, &#19990;&#30028; &#x1f30d; &mdash; Caf&eacute; d&eacute;j&agrave;"`), the
benchmark tries three "scales" of progressively larger predefined
repertoires:

- **Block / plane scale**: BASIC LATIN &rarr; ISO-8859-1 &rarr; BMP &rarr; BMP+SMP
  &rarr; BMP+SMP+SIP &rarr; full UCS.
- **Unicode age scale**: Unicode 3.1 &rarr; 3.2 &rarr; ... &rarr; 17.0 (using the
  bundled `Age*-*.txt` collections; covers every released Unicode
  version up to and including 17.0).
- **IANA charset scale**: US-ASCII &rarr; ISO-8859-1 &rarr; Shift_JIS &rarr; UTF-8.

For each scale the benchmark reports per-candidate whether it covers the
text fully, and at the end prints the smallest covering candidate from
each scale. This answers the question *"what CREPDL range is needed for
this text?"*.

### 12.4 Running it

```bash
mvn -q exec:java \
  -Dexec.mainClass=com.helger.crepdl.benchmark.CREPDLBenchmark \
  -Dexec.args="your arbitrary text"
```

Sample output (default input):

```
=== [3] Coverage assessment for arbitrary text ===

  Input: "Hello, 世界 🌍 — Café déjà"
  Code-point count: 23, UTF-16 length: 24

  Block / plane scale:
    BASIC LATIN (U+0020..U+007E)         : not covered (7 char(s) outside)
    ISO-8859-1 (Basic Latin + Latin-1)   : not covered (4 char(s) outside)
    BMP (Plane 0)                        : not covered (1 char(s) outside)
    BMP + SMP (Planes 0-1)               : COVERED
    BMP + SMP + SIP (Planes 0-2)         : COVERED
    Full UCS (Planes 0-2 + SSP)          : COVERED
  ...
  Recommendation:
    Smallest block scale:        BMP + SMP (Planes 0-1)
    Smallest Unicode age:        Unicode 6.0
    Smallest IANA charset:       UTF-8
```

---

## 13. Testing and the corpus suite

The library ships with **145 tests** across six test classes:

| Suite                      | Tests | What it exercises                                                                                                                       |
| -------------------------- | ----: | --------------------------------------------------------------------------------------------------------------------------------------- |
| `CCREPDLTest`              |     1 | The version-string encoding helper.                                                                                                     |
| `CREPDLValidatorTest`      |    16 | All 6 element kinds, three-valued semantics, IANA, grapheme-cluster mode, stream validation, surrogate pairs, CREPDL-script collections. |
| `CodePointLiteralCharTest` |     8 | The ISO/IEC 19757-7:2020 code-point-literal `<char>` dialect (`U+XXXX`, `U+XXXX-U+YYYY`, regex fall-through).                            |
| `CodePointSyntaxTest`      |     7 | The `CodePointSyntax` detection and translation helper in isolation.                                                                    |
| `CREPDLBenchmarkTest`      |     4 | End-to-end smoke for the benchmark main, plus sanity assertions on three sample inputs.                                                 |
| `CREPDLScriptsCorpusTest`  |   109 | Walks the bundled `CREPDLScripts` corpus and runs one parameterised test per file.                                                      |

### 13.1 The CREPDL Scripts corpus

`src/test/resources/CREPDLScripts/examples/` is a byte-identical copy of
the upstream
[CREPDLScripts](https://github.com/CITPCSHARE/CREPDLScripts)
repository, used as a real-world test corpus.

| Corpus directory                       | Files | Expected outcome                                                                                |
| -------------------------------------- | ----: | ----------------------------------------------------------------------------------------------- |
| `version1/`                            |   46  | All rejected with `CREPDLParseException` ("Illegal namespace") &mdash; v1 unsupported by design |
| `version2/characterMode/`              |   46  | All parsed; 41 build a working validator                                                        |
| `version2/graphemeClusterMode/`        |    9  | All parsed; 5 build a working validator                                                         |
| `cjkvi/cjkvi-tables-master/`           |    4  | All rejected (v1)                                                                               |

#### 13.2 Known v2 files that do not build a validator (documented)

| File                  | Reason                                                                                                          |
| --------------------- | --------------------------------------------------------------------------------------------------------------- |
| `bmp.crepdl`          | Upstream XML is malformed: literal `mode="character""` (stray double quote). JDK rejects; .NET XLinq tolerated. |
| `namedSequenceTest.crepdl` | Same XML bug; also uses ICU `\N{HIRAGANA LETTER A}` which `java.util.regex` does not support.              |
| `8859-15b.crepdl`     | Uses `<repertoire registry="IANA" number="111"/>` (MIB enumeration) &mdash; not supported by F# either.        |
| `RefLoop.crepdl`      | Deliberate self-reference &mdash; the cycle detector correctly throws.                                          |
| `IVDfoo.crepdl`       | References a bogus IVD collection (upstream negative test).                                                     |

The corpus test explicitly enumerates these as expected failures and would
fail if their behaviour changed.

### 13.3 Running the tests

```bash
mvn test               # tests only
mvn verify             # tests + forbidden-apis check + testCheck
```

---

## 14. Limitations

| Limitation                                                                                       | Workaround                                                       |
| ------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------- |
| Only CREPDL v2.0 namespace (`http://purl.oclc.org/dsdl/crepdl/ns/structure/2.0`).                | No v1 docs in scope; rewrite as v2.                              |
| Inside the v2.0 namespace, both the F# v2.0 (regex) and the ISO/IEC 19757-7:2020 (code-point literal) `<char>` content dialects are supported. | If a script uses ICU-only regex features, rewrite or use code-point literal `U+XXXX[-U+YYYY]`. |
| Regex syntax is `java.util.regex.Pattern` (with `COMMENTS \| UNICODE_CHARACTER_CLASS`).          | Stick to features common to ICU and Java regex.                  |
| ICU-only regex features not supported: `\N{...}` Unicode-name escapes, `[set1 && set2]` intersection inside char classes, ICU-specific properties. | Re-express with JDK-compatible regex.                            |
| `CLDR` registry not implemented.                                                                 | Use ISO 10646 collections or `<char>` regex instead.             |
| IANA `miBenum` (numeric lookup) not implemented.                                                 | Use the IANA `name`.                                              |
| `<repertoire registry="IVD"/>` requires the `IVD_Sequences.txt` resource on the classpath. The bundled copy is from an older Unicode revision. | Replace `src/main/resources/external/crepdl/repo/IVD/IVD_Sequences.txt` with an up-to-date copy from <https://www.unicode.org/Public/UCD/latest/ucd/IVD_Sequences.txt> and rebuild. |
| External `<ref href="https://..."/>` is dereferenced unconditionally and synchronously at validator-build time, with no scheme allow-list, host filter, time-out, or fetch-size cap. See [&sect;15](#15-security-considerations). | Pre-fetch and use local file URIs; or do not call `CREPDLValidator.create` on documents from untrusted sources. |
| CREPDL nesting / `<ref>`-follow / ISO-10646-collection expansion is bounded at 100 levels.       | Restructure scripts that exceed it; the limit is `MAX_NESTING_DEPTH` in `CREPDLReader` and `MAX_EXPANSION_DEPTH` in `RefAndRepertoireExpander`. |

---

## 15. Security considerations

### 15.1 Threat model

`jcrepdl` was written for **trusted CREPDL documents** &mdash; scripts
authored in-house or vetted before processing. If you process CREPDL XML
or candidate strings that originate from untrusted sources (HTTP request
bodies, file uploads, third-party feeds), read the rest of this section
before wiring it into a production path.

### 15.2 What is hardened

The DOM parser in `CREPDLReader._createBuilder()` runs with:

- `javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING = true`
- `disallow-doctype-decl = true` &mdash; no `<!DOCTYPE>` declarations allowed; XXE is blocked at the gate
- `external-general-entities = false`
- `external-parameter-entities = false`
- `nonvalidating/load-external-dtd = false`
- `XIncludeAware = false`
- `setValidating(false)` &mdash; no DTD/XSD is supplied by the project, so validating mode is intentionally off

A SAX `ErrorHandler` routes warnings through `LOGGER.warn` and errors
(recoverable + fatal) through `LOGGER.error`. Errors and fatal errors
are rethrown, so XML well-formedness violations always surface as a
`CREPDLParseException` rather than being silently swallowed.

Recursion is bounded in two places to prevent `StackOverflowError` on
pathological input:

- `CREPDLReader._parseElement` &mdash; max 100 nested CREPDL elements
  (`MAX_NESTING_DEPTH`).
- `RefAndRepertoireExpander._expandRecursive` &mdash; max 100 combined
  structural children + `<ref>`-follow + ISO-10646-collection expansions
  (`MAX_EXPANSION_DEPTH`).

`<ref>` cycles and CREPDL-script-defined ISO 10646 collection cycles are
detected independently and raise `CREPDLParseException` (see
[&sect;16](#16-differences-from-the-f-reference)).

`forbiddenapis` blocks `jdk-unsafe`, `jdk-internal`, `jdk-reflection`,
`jdk-system-out`, and `jdk-deprecated` API surface at build time.

### 15.3 Known open issues

These are not mitigated by the library; callers handling untrusted input
must address them out-of-band:

1.  **Unrestricted `<ref href="…">` fetch (SSRF / local-file read).**
    `RefAndRepertoireExpander` calls `CREPDLReader.readScript(URI)` for
    every `<ref>`, with no scheme allow-list, host filter, time-out, or
    fetch-size cap. A hostile script can read local files
    (`file:///etc/passwd`), probe internal HTTP services or cloud
    metadata endpoints, or hang the parsing thread on a slow URL. A
    pluggable `ICREPDLRefResolver` SPI is planned. Until then, **do not
    call `CREPDLValidator.create` on documents from untrusted sources**.

2.  **ReDoS via `<char>` regex.** `CharMatcher.compile` runs the
    `<char>` content through `java.util.regex.Pattern`, which has no
    timeout. Adversarial regex sources (e.g. `(a+)+$`) combined with an
    attacker-controlled candidate string can hang the JVM via
    catastrophic backtracking. Bound input length and run matching on
    an interruptible thread if either side is untrusted.

3.  **In-memory DOM, no input-size cap.** The whole document is loaded
    before structural checks. Pass `CREPDLReader` a pre-bounded
    `InputStream` if document size matters.

4.  **Exception messages disclose paths and URIs.** `CREPDLParseException`
    embeds full file paths, URIs, attribute values, and the SAX
    `systemId` (also logged by the error handler). Do not surface raw
    exception messages to untrusted clients.

---

## 16. Differences from the F# reference

The Java port is a faithful translation with three intentional changes:

1.  **Cycle detection on ISO 10646 CREPDL-script collections.**
    Collection 283 ("MODERN EUROPEAN SCRIPTS") is defined recursively in
    the F# data and includes `<repertoire number="283"/>` inside its own
    union body.  The F# reference tracks `<ref>` cycles
    but *not* CREPDL-script-collection cycles, so it would stack-overflow
    on a script that uses collection 283. This Java port also tracks
    ISO 10646 collection expansion chains; re-entry to the same collection
    yields the algebraic union identity (empty union &equiv; `FALSE` for
    every input), which is correct because `union(X, ..., X) = union(X, ...)`.

2.  **Regex engine is `java.util.regex.Pattern`** with the `COMMENTS` and
    `UNICODE_CHARACTER_CLASS` flags, instead of ICU regex. This means
    `\p{Lu}`, `\p{Mn}`, etc. work but `\N{...}` Unicode-name escapes and
    ICU-specific set operations inside character classes do not.

3.  **Grapheme cluster iteration uses `java.text.BreakIterator`**
    instead of ICU's `BreakIterator`. Both implement UAX #29 but the
    JDK's evolution lags ICU; very recent emoji ZWJ sequences may break
    differently.

4.  **Code-point-literal `<char>` content is accepted in addition to
    regex.**  The F# reference treats `<char>` content as ICU regex
    exclusively. This port additionally accepts the ISO/IEC 19757-7:2020
    code-point-literal dialect (`U+XXXX`, `U+XXXX-U+YYYY`, whitespace-
    separated unions) inside `<char>`, `<kernel>`, and `<hull>`. The
    detection is unambiguous and the regex path is preserved unchanged
    for any content that is not pure code-point-literal syntax. See
    [&sect;2.5](#25-char-content-dialects-regex-vs-code-point-literal).
    The implementation has been cross-validated against the SignalArc
    CREPDL&middot;CHECK service
    (`apps.signalarc.com/crepdl-validator`); the bundled
    `CodePointLiteralCharTest` runs SignalArc's own sample schema
    through our validator and asserts the same two violating code points
    that SignalArc reports.

Otherwise the algorithm, the public method names, and the three-valued
semantics are 1:1.

---

## 17. License

jcrepdl is released under the **Apache License 2.0**.  See `LICENSE` (or
the headers in every source file) for the full text.

The original F# implementation by MURATA Makoto / CITPC is licensed under
the **MIT License** (https://github.com/CITPCSHARE/CREPDL/blob/Version2/LICENSE).

The bundled corpus under `src/test/resources/CREPDLScripts/` is licensed
under the **MIT License** by its upstream author.

Bundled data files derived from
[`IVD_Sequences.txt`](https://www.unicode.org/Public/UCD/latest/ucd/IVD_Sequences.txt)
and ISO/IEC 10646 collection definitions are subject to their respective
upstream terms (Unicode Consortium License and ISO/IEC respectively); they
are redistributed here in the form they were shipped in the F# project.

---

My personal [Coding Styleguide](https://github.com/phax/meta/blob/master/CodingStyleguide.md) |
It is appreciated if you star the GitHub project if you like it.
