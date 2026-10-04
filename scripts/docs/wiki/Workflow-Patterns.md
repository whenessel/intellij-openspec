# Workflow Patterns

OpenSpec supports three workflow patterns depending on your setup and preferences.

## Pattern 1: Clipboard Workflow

**Best for:** Teams without API keys, users who prefer their own AI tools.

```mermaid
flowchart LR
    A[Generate Artifact] --> B[Clipboard]
    B --> C[Paste into AI tool]
    C --> D[Copy response]
    D --> E[Paste into artifact file]
```

**Steps:**
1. Select an artifact in the tree
2. **Generate Artifact → Clipboard**
3. Paste the prompt into Claude, ChatGPT, Cursor, etc.
4. Copy the AI response back into the artifact file

**Pros:** No API key needed, use any AI tool, full control over the interaction.
**Cons:** Manual copy-paste, slower iteration.

## Pattern 2: Integrated Generation Workflow

**Best for:** Solo developers, rapid prototyping, automated pipelines.

```mermaid
flowchart LR
    A[Generate Artifact] --> B[Review context]
    B --> C[REST or installed Codex]
    C --> D[Review validated artifact batch]
    D --> E[Apply approved edits]
```

**Steps:**
1. Configure REST credentials or supported installed Codex login (see [[AI-Configuration]])
2. Select an artifact or use **Generate All Artifacts**
3. Review context, send to the selected backend, then review and apply validated artifact edits

**Pros:** Integrated generation and reviewed batches through either backend.
**Cons:** Requires online inference; API charges or subscription limits apply. Review steps remain interactive.

## Pattern 3: Mixed Workflow

**Best for:** Most teams — use integrated generation for routine artifacts, clipboard for critical ones.

**Example flow:**
1. **Generate All** to auto-generate `design.md` and `tasks.md`
2. Review generated content
3. For delta specs, use **Clipboard** mode to have a detailed conversation with AI
4. Manually refine as needed

## Choosing a Pattern

| Factor | Clipboard | Direct API | Mixed |
|--------|-----------|------------|-------|
| API key required | No | Yes | Yes |
| Speed | Slow | Fast | Medium |
| Control | Full | Limited | Balanced |
| Batch generation | No | Yes | Partial |
| Cost | Free | Per-call | Per-call |

## CLI vs. Built-in Mode

Both workflow patterns work in either mode:

| Feature | With CLI | Built-in Only |
|---------|----------|---------------|
| Init | CLI scaffolding | Built-in scaffolding |
| Propose | CLI + templates | Dialog + ScaffoldingService |
| Validate | Merged results | Built-in rules only |
| Archive | CLI moves files | Built-in file operations |
| Artifact DAG | Full DAG from CLI | File-based detection |
| Generate | Full prompt context | Basic prompt building |

The CLI provides richer artifact dependency information and validation rules. Built-in mode covers core workflows without external dependencies.

---

**Previous:** [[Validation]] | **Next:** [[Troubleshooting]]
