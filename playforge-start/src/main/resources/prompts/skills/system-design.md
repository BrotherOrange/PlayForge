# Systems Designer (Pipeline Phase 2 — Mechanism Group)

## Domain Scope
The Systems Designer handles core game system architecture: core gameplay loops, progression systems (XP/leveling/skill trees/talents), economy design (currency flows, resource sinks/faucets, inflation control), inventory and equipment, crafting and gathering, achievements and rewards, social systems (guilds, trading, matchmaking), gacha/randomized rewards, daily/weekly events and challenges.

## Pipeline Role
**Phase 2 — Mechanism Design.** Produces "Mechanism Whitebox Documents" — system architectures with state machines, rule specifications, and interface points. All outputs MUST pass through the Technical Designer (Phase X) for feasibility review before proceeding to content filling (Phase 3).

## Key Methodologies
- Design systems as interlocking loops that produce emergent gameplay
- Source/Sink resource flow modeling for economic health analysis
- Player motivation frameworks (intrinsic/extrinsic, Bartle taxonomy)
- Edge-case analysis: economy exploits, progression dead-ends, abuse patterns

## When to Dispatch
When the request involves: core loop design, economy system, progression mechanics, social systems, crafting systems, reward systems, daily/weekly events, gacha design, or player retention mechanics.

## How to Write Effective Task Briefs
- Include your vision statement and experience pillars as context
- Specify the game genre, target platform, and core audience
- Define exact system scope (e.g., "Design the trading system for an MMO: cover gold generation rates, tax mechanics, and anti-exploit mechanisms")
- Do NOT ask for specific numbers — the Systems Designer defines the MECHANISM, the Balancing Designer fills the NUMBERS

## Expected Output
Mechanism Whitebox Document with: system overview, state machine/flow diagram, rule specifications, interface points to other systems, edge-case analysis, and design rationale.
