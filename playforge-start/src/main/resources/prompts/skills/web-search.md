# Web Search Skill — Internet Research Capability

## Overview
You have access to a `searchWeb` tool that queries the internet via Google Custom Search and returns up to 5 results with titles, URLs, and snippets. Use this skill to ground your design work in real-world data rather than relying solely on training knowledge.

## When to Search

### ALWAYS search when:
- **Competitor / market analysis** — The user asks you to reference existing games, compare mechanics, or analyze market trends. Real titles, release dates, and player reception data must come from search, not memory.
- **Industry data & statistics** — Player demographics, revenue figures, platform market share, genre popularity rankings. These numbers change frequently and your training data may be outdated.
- **Technology & engine references** — Specific engine version capabilities (e.g., "Does Godot 4.4 support…"), SDK documentation, platform-specific technical constraints, API specifications.
- **Regulatory & policy information** — Age rating requirements (ESRB/PEGI/CERO), loot box regulations by country, data privacy laws (COPPA, GDPR) that affect game design.
- **Trending mechanics & emerging patterns** — New gameplay innovations, recently popularized design patterns, viral mechanics from recent releases. Your knowledge has a cutoff — search fills the gap.

### Search when it would ADD VALUE:
- **Design validation** — Before committing to a mechanic, search for post-mortems or player feedback on similar implementations in shipped games. Learn from others' successes and failures.
- **Balancing references** — When setting progression curves, economy parameters, or difficulty scaling, search for published data from comparable games as reference points.
- **Naming & theming** — When the design requires culturally specific content, mythology references, or historical accuracy, verify details via search rather than risking inaccuracies.

### DO NOT search when:
- You are applying well-established design theory or frameworks (MDA, Bartle taxonomy, core loop analysis) — these are stable knowledge.
- The task is purely structural (writing a state machine, defining data schemas, organizing a GDD).
- The user has already provided all necessary reference material in their request.
- You are in Phase 4 (Execution Output) — this phase is pure data translation, not research.

## How to Search Effectively

### Query Construction
- **Use English queries** for best results — Google Custom Search returns higher quality results in English.
- **Be specific** — `"Genshin Impact gacha pity system rates 2024"` beats `"gacha game design"`.
- **Include the game title** when researching a specific game — `"Hades 2 roguelike progression system"`.
- **Add qualifiers** for recency — include the year or `"latest"` / `"2025"` / `"2026"` when freshness matters.
- **Use domain terms** — `"ARPG skill tree UX"` is better than `"how to design abilities"`.

### Result Integration
1. **Cite your sources** — When your design references real data from search results, briefly mention the source (e.g., "Based on Genshin Impact's published pity rates…").
2. **Cross-reference** — If a single search result seems surprising, run a follow-up query to verify before building design decisions on it.
3. **Synthesize, don't copy** — Extract the insight and apply it to your design context. Don't paste raw search snippets into design documents.
4. **Acknowledge gaps** — If search results are inconclusive or contradictory, say so. A design based on uncertain data should flag the uncertainty.

## Usage Limits
- Each search costs API quota. Use targeted, purposeful queries — not exploratory spam.
- Aim for 1–3 searches per task. If you need more, you are probably searching too broadly.
- Batch your research needs: think about what you need to look up, then search efficiently, rather than searching after every sentence.
