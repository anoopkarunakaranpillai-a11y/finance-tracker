# Daily learning content — instructions for the 9 AM scheduled run

Every morning, before 9:00 AM Kuwait time (Asia/Kuwait, UTC+3), new content is added for:

- **Navamika** (KG1, about 4 years old): at least 5 new learning games.
- **Arya** (Kerala PSC LDC aspirant): exactly 2 new mock tests of exactly 25 questions each (50 questions/day).

The content lives in this repository (`daily/`) and is mirrored into the tracker's Claude artifact database.

## Steps (idempotent: running twice must not create duplicates)

1. Kuwait date: `TZ=Asia/Kuwait date +%F` → `DATE` (e.g. `2026-10-06`). Key: `DAILY_LEARNING_YYYY_MM_DD`.
2. If `daily/DATE.json` already exists **and** `node tools/daily-validate.mjs daily/DATE.json` passes **and** `DATE` is in `daily/index.json`:
   skip generation. Go straight to step 7 (make sure the database copy exists), then finish.
3. Read the last 7 daily files (`daily/*.json`) to see recent topics, and avoid repeating them. Never reuse a question: the validator compares against `daily/hashes.json` (built-in bank + every earlier day).
4. Write `daily/DATE.json` in the format below.
5. Run `node tools/daily-validate.mjs daily/DATE.json`. If it fails, fix **only** the items listed in `problems` (replace that one question or game), then validate again. Up to 6 rounds. Never publish a file that fails validation.
6. `node tools/daily-publish.mjs daily/DATE.json`, then `git add daily && git commit -m "Daily learning content DATE" && git push origin HEAD:main` (fetch + rebase first if the push is rejected).
7. Mirror to the tracker artifact `https://claude.ai/artifact/TeNqAQT1owgtE6u2CBEaQX` with the ArtifactData tool:
   - First `get` documents `DATE` and `_index` in collection `daily` (a missing document is fine). Writes to an existing document need its `version` as `if_version`.
   - In one `batch`: `set` document `DATE` in collection `daily` with `file_path` = `daily/DATE.json` (pin `if_version` if it existed), and `set` document `_index` in collection `daily` with `{"dates": [...all dates from daily/index.json, newest first, max 120], "latest": DATE, "updated": "<ISO time>"}`.
   - Read `_index` back once to confirm it lists DATE.
8. If anything fails after retries: append `{"date":DATE,"status":"failed","error":"<short reason>"}` to `daily/log.json`, commit and push it, and end with a short message that says what failed. Yesterday's content stays available automatically.

## File format (`daily/DATE.json`)

```json
{
 "date": "2026-10-06",
 "key": "DAILY_LEARNING_2026_10_06",
 "generatedAt": "<ISO time>",
 "source": "scheduled",
 "navamika": [ GAME, GAME, GAME, GAME, GAME ],
 "arya": [
  {"n": 1, "title": "Daily Mock Test 1", "subject": "Mixed (LDC pattern)", "difficulty": "medium", "questions": [ Q1 … Q25 ]},
  {"n": 2, "title": "Daily Mock Test 2", "subject": "Mixed (LDC pattern)", "difficulty": "medium", "questions": [ Q1 … Q25 ]}
 ]
}
```

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

### Arya question Q (exactly 25 per test, numbered 1–25)

```json
{"n": 1, "q": "ദണ്ഡി യാത്ര ആരംഭിച്ച വർഷം:", "o": ["1920","1930","1942","1919"], "a": 1,
 "e": "1930 മാർച്ച് 12-ന് സബർമതി ആശ്രമത്തിൽ നിന്നാണ് ദണ്ഡി യാത്ര ആരംഭിച്ചത്.",
 "s": "ih", "t": "National movement", "d": "easy", "marks": 1, "lang": "ml"}
```

- Language: **Malayalam for every question except English-language questions** (`s: "eng"`, written in English, `lang: "en"`). Other questions must be written in Malayalam with `lang: "ml"`.
- Exactly 4 different options and exactly one correct answer (`a` = 0–3). In each test, the correct answers must be spread over A–D (each position 3–9 times).
- `e`: a short explanation that names the correct answer.
- `s` (subject) must be one of: gk, kh (Kerala history), ih (Indian history), geo, con (constitution), kga (Kerala governance), ca (current affairs), sci, math, ment (mental ability), eng, mal (Malayalam language), comp, psc, econ, ph (public health), law.
- `d`: easy | medium | hard. Aim for about 40% easy, 45% medium, 15% hard.
- Suggested mix per test (LDC pattern): history 3–4 (ih/kh), geography 2–3, constitution/kga 3, science 3, public health 1–2, economics 1–2, computer 1, important laws 0–1, arithmetic/mental ability 3, English 2, Malayalam language 1–2, GK 1–2, current affairs 0–3.
- **Accuracy matters more than anything**: use only facts you are certain of. Skip anything disputed or with more than one accepted answer.
- **Current affairs** (`s: "ca"`): only include an item if you have confirmed it with a web search today and it happened in the last 60 days. Put the source date in the explanation. If you can't verify, use other subjects instead.
- Never present questions as actual previous PSC questions.
