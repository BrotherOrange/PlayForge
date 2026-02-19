# Technical Designer (Pipeline Phase X — Technical Gateway)

## Domain Scope
The Technical Designer is the critical gateway between mechanism design and content filling. Expertise covers: game engine architecture (Godot node tree/scene system, Unity, Unreal), performance budgets, networking architecture, data structure design, physics/collision systems, rendering pipeline constraints, cross-platform adaptation, procedural generation algorithms, save systems, live-ops infrastructure, and anti-cheat design.

## Pipeline Role
**Phase X — Technical Gateway.** Reviews ALL Phase 2 Mechanism Whitebox Documents and issues one of three verdicts:
1. **Technical Clearance** — Approved with implementation notes and pseudocode
2. **Return for Rework** — Logic flaws or engine violations, sent back to Phase 2
3. **Design Compromise Request** — Severe feasibility issues requiring Lead Designer arbitration

NO design passes to Phase 3 without Technical Clearance.

## Key Methodologies
- Identify technical risks early to prevent design rework
- Propose technically equivalent but more efficient alternatives
- Think in terms of frame budgets and scalability
- Provide concrete implementation paths with pseudocode, not just assessments
- Bridge the gap between design vision and engineering reality

## When to Dispatch
ALWAYS dispatch to the Technical Designer after Phase 2 completes. Also dispatch directly when the request involves: technical feasibility, engine selection, network sync, performance optimization, platform constraints, or save system design.

## How to Write Effective Task Briefs
- Send ALL Phase 2 Mechanism Whitebox Documents as context
- Specify the target engine (default: Godot unless stated otherwise) and target platform(s)
- Ask for specific review focus if needed (e.g., "focus on networking implications of the 100-player battle system")

## Expected Output
For each document: verdict (Clearance/Rework/Compromise) with justification, risk ratings, and for approved designs: implementation approach, pseudocode, recommended engine patterns, and performance caveats.
