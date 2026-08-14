## MODIFIED Requirements

### Requirement: Spec format validation

The plugin SHALL validate spec files for structural completeness: title heading, requirement blocks, RFC 2119 keywords, and scenario format. When the built-in validator is the verdict (the CLI is absent — see "CLI verdict is authoritative when the CLI is available"), its default-mode verdict for a main spec SHALL NOT exceed the verdict `openspec validate` produces for the same file in its default mode — the plugin MUST NOT be more restrictive than the client it wraps. Accordingly, on the main-spec path (`openspec/specs/**`), the conditions the real CLI reports `valid` in default SHALL be demoted below ERROR: a missing `# Title` heading SHALL be a WARNING (rule `spec-title-required`), because the CLI requires no H1 and derives the spec name from the directory; and a scenario missing a `WHEN` or `THEN` clause SHALL be an INFO (rule `spec-scenario-clauses`), because the CLI performs no clause-structure check. A requirement with no `#### Scenario:` block SHALL remain an ERROR (rule `spec-scenario-required`): the real CLI reports such a requirement `valid:false` — it enforces `.min(1)` scenarios as a schema-level error in addition to its WARNING guide — so demoting it would make the plugin laxer than the CLI. A missing requirement block (`spec-requirement-required`) SHALL remain ERROR, because the CLI errors on it. The **RFC-keyword rules SHALL be severity-conditional on the requirement having a body, matching OpenSpec CLI 1.8's demotion**: a requirement whose body has prose but no `SHALL`/`MUST` whole word (rule `spec-rfc-keywords`, or `spec-rfc-keyword-in-header` when the only keyword sits in the requirement header) SHALL be a **WARNING** in default mode — the 1.8 CLI reports such a requirement `valid` in default — re-promoted to a failing verdict under a strict run via the CLI-mirroring strict-warning set (see "Per-run strict validation"); a requirement with **no body prose at all** (header plus scenarios only) SHALL remain an **ERROR** (`spec-rfc-keywords`), because the 1.8 CLI still errors on a body-less requirement. On the main-spec path a `### Requirement:` header whose name **exactly (case-sensitively) matches an earlier requirement's name in the same file** SHALL be an **ERROR** (rule `spec-duplicate-requirement`) that fails the file's verdict, matching OpenSpec CLI 1.8, which forbids duplicate requirement names so that a spec update cannot silently discard one block while editing another; when a name repeats N times the later N−1 occurrences SHALL each be flagged, each referencing the line of the first declaration, and the diagnostic SHALL be anchored on the duplicate occurrence's line. This duplicate-name rule SHALL NOT apply to change delta specs (`openspec/changes/**/specs/**`), whose requirement names legitimately recur across `## ADDED`/`## MODIFIED` sections and are governed by the CLI's separate delta-consistency checks — the built-in fallback does not reimplement those, and matching them is outside this rule. Requirement headers (`### Requirement:`) SHALL be recognized case-insensitively on the header token, matching OpenSpec CLI 1.4+ parsing. Requirement-keyword presence SHALL be satisfied only by `SHALL` or `MUST` as whole words (matching the CLI's rule on every supported generation — `SHOULD`/`MAY` do not satisfy it), SHALL be evaluated against the requirement body with fenced code blocks masked (matching CLI 1.6 semantics — a keyword appearing only inside a code fence does not satisfy the check), and the header-only-keyword case SHALL still produce a targeted diagnostic directing the author to move the keyword onto a body line, with a quick-fix offered, at the body-conditional severity above. Scenario presence (`#### Scenario:`) SHALL likewise be evaluated with fenced code blocks masked. The inspection SHALL guard against zero-length PSI elements and invalid offsets before creating problem descriptors.

#### Scenario: Missing title heading warns, does not fail
- **WHEN** a spec file has no `# Title` heading and the built-in validator is the verdict
- **THEN** the validator SHALL report a WARNING with code `spec-title-required`, and the file's verdict SHALL NOT fail on that account, because the CLI requires no H1

#### Scenario: Otherwise-valid spec without a title passes
- **WHEN** a spec file has no `# Title` heading but has a requirement block with a SHALL/MUST body line and at least one scenario
- **THEN** the built-in validator SHALL report the `spec-title-required` WARNING and still return a passing verdict (no ERROR), matching the CLI which validates the same file clean

#### Scenario: Missing requirement block
- **WHEN** a spec file has no `### Requirement:` section
- **THEN** the validator SHALL report an ERROR with code `spec-requirement-required`

#### Scenario: SHOULD-only requirement is flagged
- **WHEN** a requirement's body contains `SHOULD` or `MAY` but neither `SHALL` nor `MUST` as a whole word
- **THEN** the validator SHALL report the missing-keyword rule (`spec-rfc-keywords`) as a **WARNING** in default mode and the file's verdict SHALL NOT fail on that account, matching the 1.8 CLI which reports such a body-carrying requirement `valid` in default

#### Scenario: Keyword only inside a code fence is not accepted
- **WHEN** a requirement's only `SHALL`/`MUST` occurrence sits inside a fenced code block
- **THEN** the validator SHALL report the missing-keyword rule (`spec-rfc-keywords`) as a **WARNING** in default mode (the fenced keyword does not satisfy the check after fence masking, but the requirement has a body, so it is advisory in default), matching CLI 1.6 fence masking and the 1.8 default demotion

#### Scenario: Keyword only in the requirement header is flagged
- **WHEN** a requirement's only `SHALL`/`MUST` occurrence sits in the `### Requirement:` header line and its body has prose without either keyword
- **THEN** the validator SHALL report a targeted `spec-rfc-keyword-in-header` diagnostic as a **WARNING** in default mode, offering a quick-fix to move the keyword onto a body line, and the file's verdict SHALL NOT fail on that account in default, matching the 1.8 CLI

#### Scenario: Requirement with no body prose is an error
- **WHEN** a main-spec requirement has a `#### Scenario:` block but no body prose at all between its `### Requirement:` header and the first scenario
- **THEN** the validator SHALL report the missing-keyword rule (`spec-rfc-keywords`) as an **ERROR**, failing the file's verdict in both default and strict modes, matching the 1.8 CLI which still errors on a body-less requirement

#### Scenario: Requirement without a scenario is an error
- **WHEN** a main-spec requirement (outside a change delta) has a SHALL/MUST body line but no `#### Scenario:` block
- **THEN** the validator SHALL report an ERROR with code `spec-scenario-required`, and the file's verdict SHALL fail, matching the CLI which reports such a requirement `valid:false`

#### Scenario: Scenario header only inside a code fence is an error
- **WHEN** a requirement's only `#### Scenario:` header sits inside a fenced code block (so the requirement is effectively scenarioless after fence masking)
- **THEN** the validator SHALL report the `spec-scenario-required` ERROR, applying CLI 1.6 fence-aware scenario counting

#### Scenario: Scenario missing WHEN or THEN is informational only
- **WHEN** a `#### Scenario:` block is missing a `WHEN` clause, a `THEN` clause, or both
- **THEN** the validator SHALL report an INFO with code `spec-scenario-clauses` and the file's verdict SHALL be unaffected, because the CLI performs no clause-structure validation

#### Scenario: Verdict parity with the 1.6 CLI on demoted main-spec rules
- **WHEN** a main spec that the captured real 1.6.0 CLI reports `valid` (e.g. one that is untitled, or has a clauseless scenario, but no ERROR-class problem) is validated by the built-in validator in default mode
- **THEN** the built-in validator SHALL also report it passing, so the plugin's fallback verdict matches the CLI's `valid` flag for every such case

#### Scenario: Duplicate requirement name in a main spec is an error
- **WHEN** a main spec declares two `### Requirement:` headers with the same name and the built-in validator is the verdict
- **THEN** the validator SHALL report an ERROR with code `spec-duplicate-requirement`, anchored on the second declaration's line and naming the first declaration's line, and the file's verdict SHALL fail, matching the 1.8 CLI which reports such a spec `valid:false`

#### Scenario: A name repeated three times flags the later two
- **WHEN** a main spec declares the same `### Requirement:` name three times
- **THEN** the validator SHALL report exactly two `spec-duplicate-requirement` ERRORs — one on the second occurrence and one on the third — each referencing the first declaration's line, matching the 1.8 CLI which emits N−1 errors for N occurrences

#### Scenario: Requirement names differing only by case are not duplicates
- **WHEN** a main spec declares two `### Requirement:` headers whose names differ only in letter case (for example `Works` and `works`)
- **THEN** the validator SHALL NOT report a `spec-duplicate-requirement` error, because the 1.8 CLI's duplicate match is case-sensitive and reports the file `valid` on that account

#### Scenario: Duplicate detection does not apply to change delta specs
- **WHEN** a change delta spec repeats a `### Requirement:` name across its `## ADDED`/`## MODIFIED` sections (or within one section)
- **THEN** the built-in validator SHALL NOT emit a `spec-duplicate-requirement` error for the delta spec, because that main-spec rule is scoped to `openspec/specs/**`; the CLI's separate delta-consistency checks are not reimplemented in the fallback, keeping the fallback no more restrictive than the CLI on delta specs
