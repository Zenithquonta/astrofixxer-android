# AstroFixxer Handoff Log

A running log. **Add a new entry at the top every time code is implemented or changed**, using the template at the bottom. Anyone picking up the work (a person or an AI agent) should be able to continue from the latest entry without reading the chat history.

## Where things are

| Item | Location |
|---|---|
| Upstream web app (read-only reference) | `github.com/AadidevRaizada/AstroFixxer`, analysed at commit `6af76ce` |
| This planning repo | `zenithquonta/astrofixer-baby`, branch `claude/astrofixxter-analysis-xouhoi` |
| Local clone of upstream (not committed, 4.7 GB) | `AstroFixxer/` (gitignored) |
| Stitch UI prompt | `docs/STITCH_UI_PROMPT.md` |
| Implementation plan | `docs/IMPLEMENTATION_PLAN.md` |
| Codebase analysis (web page) | https://claude.ai/artifact/SMf5adtidjB5CsJt5ZrxNW |

## Current status

| Phase (see plan) | Status |
|---|---|
| 0. Analysis, UI prompt, plan | Done |
| 1. Web app bug fixes (B1–B13) | Not started |
| 2. Android project setup | Not started |
| 3. Astronomy core port | Not started |
| 4. Catalogue pipeline | Not started |
| 5. Compose UI | Waiting on Stitch designs |
| 6. AstroGuide | Not started |
| 7. Release | Not started |

---

## Entries

### 2026-09-24: Analysis and planning (no code changes)

**What was done**
- Cloned `AadidevRaizada/AstroFixxer` and analysed it: a single 10,557-line `astrofixxer.html` PWA forked from AstroHopper (GPLv3), with a Python build step (`create_data.py`, `po/tojson.py`) deployed on Vercel, and three data submodules (OpenNGC, vsop87-multilang, western_constellations_atlas_of_space).
- Wrote `docs/STITCH_UI_PROMPT.md`, which lists every existing screen and control (sky map, align flow, FOV, watch list, search, settings layers, user objects, GPS override, quick start, manual) plus the new guidance panel and AstroGuide screen, with Night-mode and RTL requirements.
- Wrote `docs/IMPLEMENTATION_PLAN.md`: 13 verified bugs (B1–B13) with fixes, and a 7-phase plan for the Kotlin/Compose port.
- Added `.gitignore` so the 4.7 GB upstream clone is never committed.

**Corrections to the earlier analysis page**
- The analysis said `cert.pem` was a committed certificate. It is actually an **empty 2-byte file**, so nothing is leaked, but it makes `pyserver.py` fail (bug B2).
- The analysis cited the `UT1-UTC` TODO at line 9102. The correct line is **7902**.

**Files changed**
- `docs/STITCH_UI_PROMPT.md` (new)
- `docs/IMPLEMENTATION_PLAN.md` (new)
- `docs/HANDOFF.md` (new)
- `.gitignore` (new)

**How to verify**
- Read the three docs. No build or tests exist yet.

**Decisions**
- Android port stays GPLv3 (required by the upstream license).
- VSOP87 tables will be generated into Kotlin (or loaded from an asset), not hand-translated.
- Golden-value tests from the existing JS are the merge gate for the astronomy port.
- AstroGuide proposal: LLM with tool calling behind a small backend, instead of Rasa. This still needs the team's agreement.

**Open questions for the team**
1. Do you want the web app fixes (Phase 1) sent as a PR to `AadidevRaizada/AstroFixxer`, or only applied in the new repo?
2. Where should the Android project live: in this repo under `android/`, or a new repo?
3. AstroGuide: LLM API or Rasa as originally planned?
4. Permission to reuse `images/qs_*.png` (© Maxim Tonkikh), or should onboarding be redrawn?

**Next step**
- Run the Stitch prompt and save the exported designs under `docs/design/`.
- Start Phase 1 (bug B1, service worker caching, has the most user impact) and Phase 2 in parallel.

---

## Entry template

```markdown
### YYYY-MM-DD: <short title>

**What was done**
- ...

**Files changed**
- `path/to/file`: what changed and why

**How to verify**
- Commands run and their results (tests, build, manual checks on a device)

**Decisions**
- ...

**Known issues / not done**
- ...

**Next step**
- ...
```
