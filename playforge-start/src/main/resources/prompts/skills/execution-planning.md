# Execution Planning (Pipeline Phase 4 — Multi-Agent File Output)

## Domain Scope
Phase 4 converts finalized designs into engine-readable formats: JSON configuration tables, CSV data dictionaries, resource structure definitions (.tres, .cfg), enum definitions, implementation specifications, and data validation rules.

## Pipeline Role
**Phase 4 — Execution Output (Multi-Agent Parallel).** Each juniorDesigner is a demo-level lightweight agent with ~16K output tokens. The Lead Designer creates N juniorDesigners where N = number of output units needed (after splitting heavy files).

## Core Principle: Keep Tasks Small
Each juniorDesigner is a focused, single-task worker — NOT a heavy-duty producer. The Lead Designer must break work into pieces small enough that each agent can finish comfortably within its ~16K budget. When in doubt, split further — two agents that finish cleanly are always better than one that truncates.

## Workload Assessment (Lead Designer's Responsibility)
Before dispatching, the Lead Designer must estimate output size for each file:
- **Light** (enums, config, small tables < 50 entries): 1 agent, no concern.
- **Medium** (data tables with 50–150 entries): 1 agent should manage. Keep dispatch context minimal.
- **Heavy** (150+ entries or deeply nested structures): **MUST split** into multiple agents by category, class, or data segment. Merge fragments in the Synthesis step.

**Rule of thumb:** If a file would need more than ~12K tokens of pure data output, split it.

## How to Write Effective Task Briefs
For EACH juniorDesigner:
- **additionalPrompt**: Specify the exact filename, format, and scope (e.g., "Your assignment: produce `skill_table_warrior.json` containing all warrior class skill definitions")
- **Task message**: Include ONLY the relevant upstream context for this specific file (don't dump all designs — provide what this file needs)
- **Format requirements**: Specify naming conventions, field types, and engine target
- **Cross-references**: Note which other files this file references (foreign keys, enum imports)

## Truncation Recovery
If a juniorDesigner's output is truncated (abrupt ending, missing entries, incomplete sections), the Lead Designer sends a follow-up message to the SAME agent to continue from the cutoff point. Do NOT recreate the agent — it retains conversation memory.

## Expected Output
Per agent: ONE complete engine-ready file with full data (not samples), schema annotations, validation rules, and cross-reference notes. The Lead Designer synthesizes all file outputs (including merged fragments) into the final deliverable.
