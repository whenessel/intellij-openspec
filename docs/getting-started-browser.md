# Getting Started: Spec Browser

*For reviewers, team leads, and PMs who want to browse specs — no AI setup required.*

> **Maintenance: Reference** — stable; updated only when the described setup flow changes (see the [documentation index](README.md)).

---

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| **IntelliJ IDEA 2024.2+** | Community or Ultimate edition |
| **OpenSpec plugin** | Install from [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/30678-openspec) |

That's it — no CLI, no API keys, no AI tools needed.

---

## Step 1: Open the Tool Window

After installing the plugin and restarting IntelliJ, open the OpenSpec tool window:

- Click the **OpenSpec** icon in the right sidebar, or
- Go to **View > Tool Windows > OpenSpec**

If the project already has an `openspec/` directory, the Browse tab populates automatically with your **Changes** and their per-artifact status. Your **specs** and **archived changes** live under `openspec/` in the standard **Project View**, where they open in the editor.

## Step 2: Browse Specs

Your specs live under `openspec/specs/` in the standard **Project View** — expand it to see each capability, and open a `spec.md` to read it with the editor's Markdown preview:

```
openspec/
└── specs/
    ├── user-auth/
    │   └── spec.md
    └── data-export/
        └── spec.md
```

To jump straight to a requirement by something it says — "where is rate-limiting specified?" — use **Search Everywhere** (double-tap Shift) and type the phrase: matching requirements surface by name and open their spec at that spot, with no setup or indexing required.

## Step 3: Track Changes

The **Browse** tab shows active work. Each change reads name-first with a dimmed `X/Y` task count, and displays its artifact pipeline status:

```
Changes
└── add-greeting  2/4
    ├── ✓ proposal       ← completed
    ├── ○ design         ← ready for generation
    ├── − specs          ← waiting on dependencies
    └── − tasks
```

This gives you visibility into where each change stands without asking the developer.

## Step 4: Review with Inspections

The plugin provides real-time [editor inspections](feature-reference.md#inspections) as you read spec files:

- Spec format validation highlights structural issues
- Delta spec validation catches incomplete ADDED/MODIFIED/REMOVED sections
- Config validation flags problems in `openspec/config.yaml`

These inspections appear as yellow/red underlines in the editor, just like any other IntelliJ inspection.

---

## You Might Also Want to Explore

- **[IDE-First Developer Guide](getting-started-copilot.md)** — If you want to start proposing and generating changes yourself using Copilot, Cursor, or another IDE-based AI tool
- **[CLI Companion Guide](getting-started-cli-companion.md)** — If you use Claude Code or another terminal-based AI alongside IntelliJ
- **[Feature Reference](feature-reference.md)** — Complete reference for all plugin features
