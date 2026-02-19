# Execution Planning (Pipeline Phase 4 — Multi-Agent File Output)

## Domain Scope
Phase 4 converts finalized designs into engine-readable formats: JSON configuration tables, CSV data dictionaries, resource structure definitions (.tres, .cfg), enum definitions, implementation specifications, and data validation rules.

## Pipeline Role
**Phase 4 — Execution Output (Multi-Agent Parallel).** Each juniorDesigner produces exactly ONE complete file. The Lead Designer creates N juniorDesigners where N = number of output files needed.

## Key Change: One Agent Per File
Each juniorDesigner agent has an ~8K token output limit. Instead of one agent trying to produce all files (and truncating), split the work so each agent focuses its entire output budget on one complete file.

## When to Dispatch
After Phase 3 is complete. Before dispatching, the Lead Designer must:
1. Enumerate all output files needed (the "execution manifest")
2. For each file, determine: filename, format (JSON/CSV/.tres/etc.), and which design elements it covers
3. Create one juniorDesigner per file, using additionalPrompt to scope the assignment

## How to Write Effective Task Briefs
For EACH juniorDesigner:
- **additionalPrompt**: Specify the exact filename, format, and scope (e.g., "Your assignment: produce `skill_table.json` containing all skill definitions with cooldowns, damage values, and resource costs")
- **Task message**: Include ONLY the relevant upstream context for this specific file (don't dump all designs — provide what this file needs)
- **Format requirements**: Specify naming conventions, field types, and engine target
- **Cross-references**: Note which other files this file references (foreign keys, enum imports)

## Expected Output
Per agent: ONE complete engine-ready file with full data (not samples), schema annotations, validation rules, and cross-reference notes. The Lead Designer synthesizes all file outputs into the final deliverable.
