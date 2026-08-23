# needs some styling on the front page

And links to hyperphor, nlq

**Status: DONE.** Added an `about-credits` block to the `about` tab
(`frontend/core.cljs`) linking to hyperphor.com, `github.com/hyperphor/nlq`,
and this repo's own source — same pattern as okc's `home-credits`. Added
`resources/public/css/nlq-aact.css` (wired via `resources/config.edn`'s new
`:css ["/css/nlq-aact.css"]`, same mechanism okc uses for `okc.css`) with
light styling for the hero/credits blocks.

## Followup

Needs to have hyperphor logos or colors or something. Branding! Think like a marketing person, it's not something I'm good at.

**Status: DONE.** hyperphor.com has no logo image (checked its live HTML,
2026-08-20) — just a text wordmark and a distinctive header gradient
(`linear-gradient(135deg, #b08968 0%, #8b9a8e 50%, #9d8b88 100%)`, tan →
sage → dusty rose). Reused that gradient verbatim for a full-width hero
banner behind the "AACT NL Query" title (`.site-hero` as of Followup 2,
below — originally `.about-hero`, about-tab-only), and its sage accent
(`#7a8a7f`) for the credits-block links — real Hyperphor branding rather
than an invented palette, and it stays trivially in sync in spirit even if
it drifts in fact (hyperphor.com could restyle later without this needing
to match pixel-for-pixel).

## Followup 2

Title bar needs to go at head of every page, not just on home

For a logo, use https://hyperphor.com/hyperphor2.gif for lack of anything better. Should go on the hyperphor page aas well of course

**Status: DONE.** Pulled the hero banner out of `about` into a new
`site-header`, rendered from `app-ui` above `tabs-nav` — now shows on
`home`/`NL_query`/`schema` alike, not just the about tab (screenshot-
verified all three, via headless Chrome against a real `lein run` since the
browser extension wasn't connected this session). Added
`https://hyperphor.com/hyperphor2.gif` (hotlinked, not vendored — no local
asset exists, and hotlinking is consistent with every other reference to
hyperphor.com already in this app) as `.site-hero-logo` in the banner,
40px tall.

"Should go on the hyperphor page as well" — read as "wherever we reference
Hyperphor", since we don't control hyperphor.com's own page: also added a
smaller copy (`.credit-logo`, 16px) next to the "Hyperphor" credit link in
`about-credits`. Flagging the interpretation in case that's not what was
meant.

Only real wart: the gif's own pale-yellow background (not transparent)
shows as a visible rectangle against the header's gradient and against the
credits section's white background — looks intentional-ish (rounded
corners) but not seamless. Living with it per "for lack of anything
better"; a transparent-background version would clean this up if one shows
up later.

**Refinement (2026-08-21):** moved the header logo to the right edge (was
left, next to the title) via `margin-left: auto` on its flex-item wrapper,
title stays left; wrapped it in an `<a href="https://hyperphor.com">` so
it's clickable too (the credits-block copy already linked, this one
previously didn't). Screenshot-verified via the same Playwright session as
the inspector-grid fix below.


# Probably need better visualization examples

or something

**Status: PLAN (not done).** The `:sql-vizq` "Visualize" card
(`qbox/ui :sql-vizq` in `sql-query.cljs`'s `viz-card`) pulls its own
few-shot examples from `(:examples (project-config "Vegalite"))` — but
`resources/config.edn` has no `{:name "Vegalite" ...}` `:nlq` project entry
at all, only `"AACT"`. So `project-config` returns `nil` and the Visualize
card's example dropdown is always empty; visualization queries get no
few-shot guidance beyond whatever `hyperphor.nlq`'s generic `:sql-vizq`
system prompt supplies. Two independent pieces of work:

1. **Wire up examples** — add a `"Vegalite"` `:nlq` entry to
   `resources/config.edn` (schema/db can likely be minimal/reused from
   `"AACT"`, since the Visualize card only reads `:examples` off it) with a
   handful of AACT-specific NL → Vega-Lite pairs, eg "Bar chart of studies
   by phase", "Histogram of enrollment for completed trials", "Line chart of
   studies first-posted by year". Cheap, mechanical — the main cost is
   picking questions that are actually interesting over this schema and
   confirming the generated specs render sensibly via `nlqv/ui`.
2. **Confirm the underlying `:sql-vizq` endpoint actually works here first**
   — `handler.clj`'s `qbox-endpoint` only wires `:sql`; `:sql-vizq` isn't
   routed at all (`design/pg-aact-split-plan.md`'s "Destination 2 scoping"
   deliberately left it out). Right now clicking Visualize hits the
   `{:error "Unsupported query type for this app: sql-vizq"}` fallback, so
   item 1 above is dead weight until this is wired — see
   `hyperphor.nlq.visgen/viz-endpoint` (mentioned as a "small, self-contained
   follow-on" in `handler.clj`'s own comment). Do this first, then item 1.

# Conditions and interventions should be inspectible objects

Not sure that makes sense with current schema, but would be cool

**Status: PLAN (not done).** "Inspectible" here means the same drill-down
behavior `sql-query.cljs` already gives id/FK columns generally
(`inspectable-kind` / `:sql-inspect` event / `inspector-pane`) — clicking a
value opens a transposed single-row card via `/api/data?data-id=sql-inspect`.
That machinery is schema-driven (`:inspectable?` on `hyperphor.nlq.inspect`'s
side, set per-kind based on whether the kind has a queryable backing table),
not specific to any particular kind, so no new inspector code should be
needed — the question is just whether `conditions`/`interventions` are wired
in as inspectable kinds. Two complications specific to these two tables
(this is why the original TODO hedges "not sure that makes sense"):

1. **No natural single-entity identity.** Every other inspectable kind here
   (`studies`, `sponsors`, `facilities`, ...) has a real per-row primary key
   (`nct_id`, `id`, ...) that identifies one entity. `conditions.name` /
   `interventions.name` are free text repeated per-study
   (`design/aact-tables.md`'s Tier 2 section notes this same issue: "NSCLC"
   vs "Non-Small Cell Lung Cancer" vs "Lung Cancer, Non-Small Cell" are
   distinct rows, not one entity). Clicking "diabetes" would need to mean
   "show me the `conditions` row for *this* study", which the existing
   id/FK-based inspector model handles fine (the row already has its own
   surrogate `id` column) — but it doesn't give the "cool" cross-study
   rollup (all studies mentioning this condition) the TODO is really asking
   for, since that needs a GROUP BY on `name`, not a single-row lookup.
2. **What "inspecting" a condition/intervention should actually show** is
   really a design question, not a wiring one: a single `conditions`/
   `interventions` row (cheap, consistent with everything else, but not
   obviously useful on its own — it's just `{nct_id, name, id}`), vs. a
   rollup view ("studies mentioning this condition", more useful, but a
   different kind of view than the inspector currently renders anywhere).

**Recommended next step:** do the cheap thing first — add `conditions`/
`interventions` `:inspectable?` wiring (mirroring whatever
`hyperphor.nlq.inspect` does for the other kinds) so a condition/intervention
cell becomes a clickable link to its own row, same as everything else. Punt
the rollup-view idea; it's a different feature (closer to
`design/aact-tables.md`'s Tier 2 `browse_conditions`/MeSH-normalization
idea — a controlled vocabulary is what would actually make "show me all
studies like this one" meaningful) and worth its own TODO entry if wanted
later.

# Specific queries

## Show full studies table

times out due to size, need paging or some better theory

Replaced with a limited one

**Status: DONE** (already fixed, prior to this pass — confirmed by re-reading
`resources/config.edn`'s current `:examples`: the example is now "Show the
1000 most recent studies, in full" / `... ORDER BY study_first_posted_date
DESC LIMIT 1000;`, with `:prompt? false` so it doesn't also bias the LLM's
few-shot prompt for unrelated queries).

## Show facilities in California running studies for diabetes

Works but output is weirdly structured?

**Status: DIAGNOSED, PLAN (not done).** Reproduced live (2026-08-20, `lein
run` against real AACT): the generated SQL is

```sql
SELECT f.*
FROM ctgov.facilities f
JOIN ctgov.conditions c ON f.nct_id = c.nct_id
WHERE f.state = 'California'
  AND c.downcase_name LIKE '%diabetes%';
```

which returns **8954 rows** for only **7578 distinct facilities** (and 2014
distinct studies) — the "weirdly structured" complaint is row duplication: a
facility running a study with two condition rows matching `%diabetes%`
(eg "type 2 diabetes" *and* "diabetes mellitus" both on the same study) comes
back as two identical-looking facility rows. This isn't a bug in this app's
code — it's the LLM generating a plain `JOIN` for what's semantically a
filter ("studies that have *a* matching condition"), when the correct shape
is either `SELECT DISTINCT f.*` or an `EXISTS`/semi-join. This is a SQL-
generation quality issue in `hyperphor/nlq`'s prompting, not something
nlq-aact's own code can special-case per-query. Two things worth trying,
roughly cheapest-first:

1. **Add an explicit example to `resources/config.edn`'s `:examples`**
   demonstrating the `SELECT DISTINCT ... WHERE EXISTS (...)` shape for a
   "facilities/studies matching some condition" style question — few-shot
   examples are the one lever this app's own config already has over
   generation quality (see the four `:sql`-carrying examples already there).
   Cheap, no library changes, but only as reliable as few-shot ever is.
2. **File upstream against `hyperphor/nlq`**: its SQL-generation system
   prompt (`hyperphor.nlq.generate`) could default to steering the LLM
   toward `DISTINCT`/semi-joins whenever a one-to-many join is used purely
   as a filter — a general fix, not AACT-specific, but out of scope for this
   repo to implement directly.

# Inspector grid shows "Rows: N" but no rows visible

Reported: clicking an id/FK cell opens the inspector card, the row-count
footer reads correctly (eg "Rows: 35"), but the grid area above it is blank
— no data rows rendered.

**Status: DONE.** ag-grid needs an explicit height on its own container div
to render any rows — without one the row *count*/header still compute
correctly (that's independent of layout), but the scrollable viewport
collapses to 0px and nothing is visible. `hyperphor.nlq.frontend.sql-query
/inspector-grid` passes `:class "aggrid-inspector"` on its `ag/ag-table`
call specifically expecting the *consuming app* to size that class — this
app's `nlq-aact.css` never defined it (okc's own `okc.css` does: `.aggrid-
inspector { width: 100%; height: 60vh; }`, the exact same contract). Added
the identical rule here.

Verified end-to-end (2026-08-21) via a scripted real-Chrome session
(Playwright, since the browser extension wasn't connected this session —
`pip install`ed into a throwaway venv, removed after): ran a live NL query
("List the NCT ID and brief title of 10 phase III studies sponsored by
Pfizer") against real AACT, clicked an `nct_id` cell, confirmed the
inspector card now shows a fully populated transposed field/value grid
("studies NCT07578649" with `brief_title`/`completion_date`/... rows
visible), not a blank area.

Only the inspector grid was actually broken — the *main* results grid
(`sql-grid-view`) renders fine with no equivalent CSS rule, because its
wrapping divs (`sql-query.cljs`'s `ui`) carry explicit `:style {:height
"50%"/"90%"}` inline, which resolves against `html-frame-spa`'s `:body
{:style {:height 5000}}` hack; `inspector-grid`'s `.aggrid-inspector`
never gets an inline height from its caller (`inspector-pane`), so it was
depending entirely on this app's stylesheet, which didn't have it. Confirmed
by testing the main grid live too (screenshot: 1,134-row `SELECT s.*`
query rendered rows correctly) — not the same bug recurring there.

# Schema page "Entities" section is blank

**Status: DONE.** Real `alzabo` bug, not this app's. `hyperphor.alzabo.html/
index->html` renders the Entities section as `(if (> (count categories) 1)
(for [category-name (keys categories)] ...))` — no `else` clause. AACT's
schema has no `:categories` key of its own, so it falls back to alzabo's
own `default-graph-options` (`{:categories {:default {...}}}`), a single
entry — `(count categories)` is 1, the `if` is false, and the *entire* `for`
(the whole entities table, not just some header) evaluates to `nil`. Root
cause: a recent alzabo commit ("restore category headers", `57ff8b7`)
changed `(if true ...)` back to `(if (> (count categories) 1) ...)`, meaning
to suppress a redundant single-category `<h3>` heading, but wrapped the
*whole loop* in that condition instead of just the heading — so a
single-category schema (the common case) lost its entities list entirely,
not just its heading.

Fixed in `alzabo` (`/opt/mt/repos/hyperphor/alzabo`, `incorporates` branch —
same branch-is-really-trunk situation as `nlq`'s `infer-schema`, `main` is
still at 1.3.0): always run the `for` loop; only the `<h3>` category header
is now conditional on `(> (count categories) 1)`. 1.3.4 → 1.3.5,
`lein install`ed locally (uncommitted upstream, same as every other library
bump this repo's git log records). Propagated: `nlq`'s alzabo pin bumped to
1.3.5 (0.3.5 → 0.3.6, `lein install`ed), this app's `nlq` pin bumped to
0.3.6.

**Verified live (2026-08-23)**: deleted the stale generated `resources/
public/AACT/schema/index.html`, booted a real `lein run` (regenerates it at
startup), screenshotted the result — the Entities table now shows all 14
kinds with their docs, correctly with *no* redundant "Default" heading
(single category, as intended). Root-caused via `grep`+`git show` on the
exact alzabo commit that introduced the regression, not guessed.

