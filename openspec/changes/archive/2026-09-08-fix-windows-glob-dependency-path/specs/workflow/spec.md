## ADDED Requirements

### Requirement: Generation prompt tolerates glob-valued dependency paths

When assembling the prompt to generate an artifact, the plugin inlines the content of each completed dependency. A dependency's declared path MAY be a glob pattern rather than a single file — the OpenSpec CLI declares the `specs` dependency's path as `specs/**/*.md`. The plugin SHALL treat a glob-valued dependency path (a path containing a wildcard such as `*`, `?`, or `[`) as a reference rather than a readable file, on every operating system, and SHALL degrade to a path-only reference instead of attempting to resolve it to a single filesystem path. Artifact generation SHALL NOT fail because a dependency path cannot be resolved to a filesystem path.

#### Scenario: Completed dependency has a glob-valued path

- **WHEN** an artifact is generated whose completed dependency declares a glob-valued path (e.g. `specs/**/*.md`)
- **THEN** the plugin SHALL NOT attempt to read that glob as a single file
- **AND** the generation prompt SHALL reference the dependency by its path
- **AND** generation SHALL succeed identically on Windows and on POSIX platforms, without a path-resolution error

#### Scenario: Completed dependency is a real file

- **WHEN** an artifact is generated whose completed dependency declares a concrete (non-glob) file path that exists on disk
- **THEN** the plugin SHALL inline that file's content into the generation prompt
