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
sage → dusty rose). Reused that gradient verbatim for a full-width
`.about-hero` banner behind the "AACT NL Query" title, and its sage accent
(`#7a8a7f`) for the credits-block links — real Hyperphor branding rather
than an invented palette, and it stays trivially in sync in spirit even if
it drifts in fact (hyperphor.com could restyle later without this needing
to match pixel-for-pixel).

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

## What's the average enrollment for completed trials by phase?

Works but has weird  "tagged value" outputs

**Status: DONE.** Confirmed root cause (my first pass at this, guessing from
the clean-looking raw JSON, wrongly blamed ag-grid column grouping —
superseded by this): `AVG(enrollment)` comes back from Postgres as
`java.math.BigDecimal`. transit-clj tags a `BigDecimal` `~f` (bigdec); the
frontend's transit reader (`hyperphor.way.api`'s plain `:transit` ajax
config — no custom read handlers registered) has no handler for `~f`, so the
value lands in the UI as a raw, unrendered `Transit$TaggedValue` instead of
a number — exactly the `#object[Transit$TaggedValue [TaggedValue: f,
70.4358781496803310]]` observed live. (My earlier curl-based repro missed
this because plain `curl` sends no `Accept` header, so `wrap-restful-format`
fell back to JSON, where cheshire serializes `BigDecimal` as an ordinary
number — the bug only shows up on the real transit path the browser
actually uses.)

Not AACT-specific — any Postgres source hitting an aggregate query has this
problem — so, per feedback on this pass, moved out of nlq-aact entirely and
fixed at the actual query boundary instead: `hyperphor.nlq.sources.postgres`
(the `hyperphor/nlq` library, `/opt/mt/repos/hyperphor/nlq`)'s
`sql/query :postgres` method now runs every row through a new
`untag-numerics` (`clojure.walk/postwalk`) that coerces `BigDecimal` →
`double` (and `BigInteger` → `long`, same reasoning, tag `~n`) — so results
round-trip as plain transit `~d` doubles, which every transit reader,
including the frontend's unmodified one, handles natively. Released as
`com.hyperphor/nlq` 0.3.3 (bumped from 0.3.2, `lein install`ed to local
`~/.m2` — not yet pushed/tagged upstream, that commit is still local to that
repo, on its `infer-schema` branch); nlq-aact's own `project.clj` bumped to
match, and `handler.clj`'s local `untag-numerics` copy removed. Verified
end-to-end again against the new dependency (2026-08-20): same clean
`Accept: application/transit+json` response, zero `~f` tags.

Only a partial general fix, even at this new location — any *other*
BigDecimal/BigInteger-shaped result is covered, since `untag-numerics`
walks every row, but the same class of problem could in principle recur for
some other transit extension type AACT/Postgres produces that has no
frontend reader (eg `~t` instant, `~u` uuid) if a query ever surfaces one —
none seen so far, not preemptively handled.



