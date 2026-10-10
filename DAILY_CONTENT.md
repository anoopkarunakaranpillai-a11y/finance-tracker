# Daily learning content — procedure for the scheduled run (v2)

Runs every day at the time in the settings (default **08:48 Asia/Kuwait**, retry **11:48**) as the Claude scheduled task
"Daily learning content (Navamika games + Arya mock tests)". It does not depend on anyone opening the website.

Daily output:
- **Arya's Study Window:** at least **100 new, unique, validated questions** (the target is in the settings), published as one batch,
  plus **4 or more new mock tests** built from them, current-affairs items, and a check of official previous-paper sites.
- **Navamika:** at least 5 new learning games.

Everything is traceable: `daily/DATE.json` (published batch + report), `daily/logs/DATE.json` (outcome of every candidate),
`daily/work/DATE/` (raw candidates, independent answers, run history), `daily/log.json` (execution history), `daily/index.json` (reports per day).

## Settings

`daily/config.default.json` holds the defaults. The admin page (Arya › Admin › Automation) saves changes to the tracker database,
document `_auto` in collection `qbank`; step 1 copies them to `daily/config.json`, which overrides the defaults.
Settings: enabled, run time, timezone, daily target, category allocation, language per category, difficulty mix, exam list,
official and current-affairs sources, validation switches, retry limits (`retry.maxFixRounds`, `retry.maxCandidates`), test plan, `retry.dates` (days the admin asked to re-run) and `dismissed` (review items the admin handled).

## Steps

1. **Date and settings.** `DATE=$(TZ=Asia/Kuwait date +%F)`. Load ArtifactData (ToolSearch "select:ArtifactData") and `get` collection `qbank`, doc `_auto`
   on `https://claude.ai/artifact/TeNqAQT1owgtE6u2CBEaQX`. If it exists, parse its `j` field (JSON text) and write it to `daily/config.json`.
   - If `enabled` is false: record a run in `daily/work/DATE/run.json` with `"kind":"skipped"`, commit, and finish with "Skipped: automation is switched off".
   - If the settings' `time`/`retryTime` differ from this scheduled task's schedule, update the task with `update_trigger`
     (cron `CRON_TZ=<timezone> <mm> <hh>,<retry hh> * * *`) and note it in the summary.
   - Also process any dates listed in `retry.dates` (the admin's "Retry batch" button): re-run steps 2–12 for each, then write `_auto` back with `retry.dates` emptied (pin `if_version`, keep every other field).
2. **Run record.** Create or update `daily/work/DATE/run.json`: `{"started": first start time, "runs": [{"at": now, "kind": "scheduled|retry|manual"}], "errors": [], "retries": n}`.
3. **Idempotency.** If `daily/DATE.json` has `"v": 2`, its `report.status` is `Completed`, the database already has document `daily/DATE` with the same `report.published`, stop here and report "already done".
   Otherwise continue; everything below **resumes**: earlier candidate files are kept, published questions keep their IDs and are never counted twice.
4. **What is still needed.** `node tools/daily-build.mjs DATE --dry` prints `perCat` (published/target per category). Generate only for categories that are short,
   about 20% more than the shortfall (some candidates will be rejected), never more than `retry.maxCandidates` in total for the day.
5. **Write candidates** to new files `daily/work/DATE/candidates-<category>-<run>.json` (arrays of objects, format below). Use new keys `k` (e.g. `quant-017`); never reuse a key.
6. **Current affairs.** Search the web for 5–8 important items from the last 30 days from the configured sources. Save them to `daily/work/DATE/ca.json`
   (`[{"d":"YYYY-MM-DD event date","pub":"YYYY-MM-DD","cat":"India|Kerala|World|Economy|Science & Technology|Sports|Awards|Appointments|Environment","t":"one sentence","src":"source name","url":"https://…","ver":"DATE"}]`).
   Current-affairs questions (`s:"ca"`) must cite one of these with `ref` (URL) and `evd` (event date). Never publish news you could not confirm today.
7. **Official previous papers (optional).** Only if `sources.official` in the settings lists URLs: WebFetch each and save `daily/work/DATE/official.json` = `{"checked":[…],"found":[…]}` with papers not listed before. If a fetch is blocked or needs approval, record it and move on; it never affects the question target. Never import papers and never call generated questions official.
8. **Independent answer check.** Give the new candidates **without `a`, `e`, `calc`** to a separate agent (Agent tool), asking it to solve each one and return only JSON `{"k": answer index}`.
   Save its answer as `daily/work/DATE/verify-<run>.json`. If the Agent tool is unavailable, solve them yourself again from a copy with answers removed, in a separate step, and note `"selfcheck": true` in run.json.
   The builder rejects any question where the independent answer differs from the key.
9. **Build.** `node tools/daily-build.mjs DATE`. It rejects invalid questions, exact and near duplicates (against the built-in bank and every earlier day),
   wrong calculations, failed independent checks and old news; it puts fixable ones in the review queue; publishes the rest; builds the 4 tests; writes the report.
   If `published` is below the target, repeat steps 4–9 for the short categories (up to `retry.maxFixRounds` rounds). Do not loosen the checks to reach the number.
10. **Navamika.** If `daily/DATE.json` has no `navamika` games yet, write 5–7 games to `daily/work/DATE/navamika.json` (format below) and run the build again.
11. **Validate and publish.** `node tools/daily-validate.mjs daily/DATE.json` must pass (fix only the reported items). Then `node tools/daily-publish.mjs daily/DATE.json`,
    `git add daily && git commit -m "Daily learning content DATE (batch …)" && git push origin HEAD:main` (fetch + rebase first if rejected).
12. **Mirror to the tracker database** (`https://claude.ai/artifact/TeNqAQT1owgtE6u2CBEaQX`, collection `daily`): `get` docs `DATE` and `_index` for their versions, then one `batch`:
    `set` `DATE` with `file_path` = `daily/DATE.json`, and `set` `_index` with `{"dates": [all dates from daily/index.json, newest first, max 120], "latest", "updated", "reports", "totals", "next", "config"}` copied from `daily/index.json`.
    Read `_index` back once to confirm. If the day file is over 240 KB, say so (the database limit is 256 KB).
13. **Report and alert.** Finish with one summary line: `STATUS · DATE · batch · published/target new questions · tests · review · rejected · duplicates · official papers found · push ok/failed · database ok/failed`.
    If the status is not **Completed**, start the line with `⚠ ACTION NEEDED:` and say why. If anything fails after retries, add the error to run.json `errors`, rebuild (the report then shows it), commit and push.

Statuses: **Completed** (target reached) · **Awaiting Review** (target reachable once the admin approves review items) · **Partially Completed** (some published) · **Failed** (none).

## Candidate question format

```json
{"k": "quant-007", "cat": "quant", "s": "math", "t": "Profit and loss", "lvl": "recruit", "exam": "Kerala PSC", "d": "medium",
 "q": "800 രൂപയ്ക്ക് വാങ്ങിയ സാധനം 1000 രൂപയ്ക്ക് വിറ്റാൽ ലാഭശതമാനം എത്ര?", "o": ["20%", "25%", "15%", "30%"], "a": 1,
 "e": "ലാഭം 200; 200 ÷ 800 × 100 = 25%.", "lang": "ml", "calc": "(1000-800)/800*100"}
```

- `cat` and the allowed subjects `s`:
  `gkca` (gk, ca, kh, ih, geo, con, kga, econ, law, psc, mal = Malayalam language) · `eng` (eng, veng) · `quant` (math, quant) · `reas` (ment, reas) · `sci` (sci, ph) · `comp` (comp) ·
  `deg` (acc, dcs, dmath, dphy, dchem, dbio, decon, mgmt, hum, engg) · `compx` (quant, reas, veng, gk, comp, math, ment, eng, sci, con, econ; must name an `exam` from the settings).
- `lvl`: school · hsec · diploma · degree · entrance · recruit (degree questions: degree or entrance).
- `psc`: Kerala PSC preliminary level the question suits: `sslc` (10th-level prelims, LDC/LGS style), `plus2` (Plus Two-level prelims) or `degree` (degree-level prelims, Secretariat/University Assistant style). Required for the categories in `pscTag`; follow `pscMix` (default 40% sslc, 30% plus2, 30% degree). Match difficulty and depth to the level. These are model questions in the Kerala PSC style, never official questions.
- `d`: easy · medium · hard · advanced, following the difficulty mix in the settings.
- `lang` per category from the settings (default: Malayalam for gkca, quant, reas, sci, comp; English for eng, deg, compx). Malayalam questions are written in Malayalam script.
- Exactly 4 different options, one correct answer `a` (0–3). No "all/none/both of the above" and no "A and B" options. Spread answers over A–D.
- `e`: a short explanation that names the correct answer.
- `calc`: required for numeric maths questions: a plain arithmetic expression (+ − × as *, ÷ as /, ^, sqrt) whose value equals the correct option. Use `"nocalc": true` only for non-numeric maths questions.
- Current affairs: `ref` (https URL from ca.json) and `evd` (event date). They retire automatically after the configured number of days.
- Accuracy first: only facts you are certain of; standard textbook facts for degree topics; work out every number. Never claim a question comes from a real exam paper.

### Navamika GAME (5–7 per day)

```json
{"id": "animal-homes", "type": "logic", "title": "Animal Homes", "desc": "Who lives where?",
 "difficulty": "easy", "instructions": "Listen and tap the right picture.",
 "questions": [
  {"q": "Who lives in the water?", "show": "🌊", "options": [{"e":"🐟","t":"Fish"},{"e":"🐄","t":"Cow"},{"e":"🐓","t":"Rooster"}], "answer": 0, "good": "Fish live in the water!"}
 ]}
```

- `type`: alphabet, numbers, counting, colors, shapes, animals, fruits, matching, sounds, vocabulary, puzzles, observation, logic, rhymes (quiz style, 5–10 questions each), or
  - `memory` with `"pairs": ["🍎","🍌","🍇"]` (2–6 different emoji, no `questions`)
  - `tracing` with `"chars": ["A","B","C"]` (1–8 of A–Z, a–z or 1–20, no `questions`)
- `difficulty`: easy | medium | challenge. Mostly easy/medium for KG1.
- Options: 2–4 each. An option is any of `e` (one emoji), `t` (a short word or number), `s` (shape: circle, square, triangle, rectangle, star, oval, heart, diamond, hexagon, balloon, flower, fish) and `c` (colour `#RRGGBB`). Options with `s` or `c` show only the picture, so the word does not give the answer away. Text-only options (`t` only) are fine for numbers.
- `show` (optional): one emoji or a short text like `"3, 4, ?"` shown big above the options.
- `answer`: index of the correct option. **Spread correct answers across positions.**
- Use simple words a 4-year-old understands, cheerful `good` lines, everyday objects, animals, fruit, colours, shapes, numbers to 20, letters. Nothing scary, violent or unsuitable. Use widely supported emoji (avoid emoji newer than 2021).
- Vary game types day to day and mix at least 4 different types each day. Keep at most one memory and one tracing game per day.
