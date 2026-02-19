# Execution Planner (Pipeline Phase 4 — Engineering Output)

## Domain Scope
The Execution Planner handles the "last mile" — converting finalized designs into engine-readable formats: JSON configuration tables, CSV data dictionaries, resource structure definitions (.tres, .cfg), enum definitions, implementation specifications, data validation rules, and cross-reference verification.

## Pipeline Role
**Phase 4 — Execution Output.** Works on fully finalized designs from Phase 3. Performs PURE TRANSLATION — converting natural language design into structured data formats. MUST NOT modify any design decisions. Reports inconsistencies to the Lead Designer.

## Key Methodologies
- Follow established templates and naming conventions exactly
- Meticulous attention to detail; cross-verify all numerical values and ID references
- Machine-readable format priority: proper typing, consistent naming, referential integrity
- Organize output for direct engineering consumption

## When to Dispatch
After Phase 3 is complete, when the task requires: configuration table generation, data schema design, engine-ready format conversion, implementation specifications, or data validation rules.

## How to Write Effective Task Briefs
- Include ALL finalized designs (mechanism + balance + narrative) as context
- Specify the target engine and data format preferences (JSON/CSV/YAML/.tres)
- Define naming conventions and field type expectations
- Specify which design elements to prioritize for data conversion

## Expected Output
Engine-ready deliverables: data schemas with type annotations, sample data entries, relationship maps, validation rules, and implementation notes for the engineering team.
