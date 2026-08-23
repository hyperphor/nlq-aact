**Status (2026-08-23): Tier 1 and Tier 2 both fully added** (`mesh_terms`/
`mesh_headings` excepted — see that entry below for why), 26 tables total.
`resources/config.edn`'s `:tables` and `schema_gen.clj`'s `tables`/
`table-docs`/`table-icons`/`table-labels` updated; `resources/aact/
schema.alz.edn` regenerated against live AACT (all real FK constraints
picked up automatically — `design_group_interventions` correctly links
`design_groups`↔`interventions`, `outcome_analyses`/`outcome_measurements`/
`baseline_measurements`/`baseline_counts` all correctly link to
`result_groups`, etc). Verified with 7 live NL queries spanning every new
table (principal investigators, statistically-significant outcome
comparisons via `p_value`, planned-vs-reported outcomes, MeSH-normalized
condition frequency, baseline demographics, arm/intervention mapping,
adverse-event totals) — all generated correct SQL and returned real
results on the first try. Two of the best became new `:examples`.

# AACT table coverage: what's used vs. what's available

`resources/config.edn`'s AACT `:nlq` entry only points `org.parkerici.okc.sources.postgres/gen-alz-schema`
at a subset of AACT's `ctgov` schema (~50 tables total). This is a survey of what's
in, what's out, and which of the unused tables are worth adding next — grounded in
AACT's live schema/data-dictionary pages (https://aact.ctti-clinicaltrials.org/schema,
.../data_dictionary), not a from-scratch guess.

## Currently wired in

`studies`, `sponsors`, `conditions`, `interventions`, `facilities`, `outcomes`,
`eligibilities`, `brief_summaries`, `keywords`, `design_groups`, `calculated_values`,
`designs`, `study_references`, `reported_events` — plus, as of 2026-08-23, all of
Tier 1 and Tier 2 below (`mesh_terms`/`mesh_headings` excepted).

That's the "study identity + design skeleton" slice (Tier 0), extended with derived
summary fields/methodology/publications/safety data (Tier 1), and now further
extended with real posted results/statistics, MeSH-normalized vocabulary, baseline
demographics, and investigator info (Tier 2).

## Tier 1 — added (small, 1:1 or trivial join off `nct_id`, same complexity as what was already there)

| Table | Why it matters here |
|---|---|
| `calculated_values` | 1 row/study, AACT-precomputed: actual enrollment, actual duration, min/max age in years, `were_results_reported`, months-to-results, number of arms/facilities. Turns queries that would otherwise need hand-derived SQL ("how long did this trial run", "does it have posted results") into a plain column lookup. |
| `designs` | 1 row/study: `allocation`, `intervention_model`, `masking`, `primary_purpose`, `observational_model`. Trial-methodology fields (randomized? blinded? parallel vs crossover?) that `studies`/`phase` don't cover — a natural sibling to the existing `design_groups`. |
| `study_references` | PubMed-linked publications per trial (PMID, citation, reference_type: result/background/derived). For a research-scientist audience, "show me the papers behind this trial" is an obvious ask this now answers. |
| `reported_events` | Adverse-event data per arm (serious vs. other, organ system, term, counts, subjects-at-risk). Thematically the best fit of anything on this list — the app already has an irAE-focused heatmap for RADIOHEAD's own patients; this is the AACT-wide equivalent (cross-trial immunotherapy safety landscape). |

Added 2026-08-23 (see status note at top of file):

| Table | Why it matters here |
|---|---|
| `design_outcomes` | The *planned* primary/secondary outcome measures (vs. `outcomes`, which is the *reported* results). Obvious pairing — you have the "actual" half, not the "as-designed" half. |
| `design_group_interventions` | Join table between `design_groups` (arms) and `interventions` — right now both tables exist but nothing ties "arm A got drug X" together except a shared `nct_id`. |
| `reported_event_totals` | Companion to `reported_events` — study-level adverse-event summary counts. |

## Tier 2 — good value, more schema/prompt weight — added 2026-08-23

| Table | Why / caveat |
|---|---|
| `outcome_measurements`, `outcome_analyses`, `outcome_analysis_groups`, `result_groups` | The *actual posted numbers* (arm-level means/CIs) and statistical comparisons (p-values, CIs) for completed trials — richer than `outcomes`, which only holds outcome *definitions*. This is what "which trials showed a significant OS/PFS benefit" queries need. Wide, nullable-heavy schema (values differ by measurement type) — the added prompt footprint this pass warned about turned out fine in practice; a live "significant OS benefit, p<0.05" query generated correct SQL and real results on the first try (see status note). |
| `browse_conditions`, `browse_interventions` | MeSH-normalized condition/intervention terms — added. `mesh_terms`/`mesh_headings` (the actual controlled-vocabulary/taxonomy tables `browse_conditions.mesh_term` matches against) deliberately **not** added — see "`mesh_terms`/`mesh_headings`: why skipped" below. |
| `baseline_measurements`, `baseline_counts` | Actual enrolled-population demographics per arm (vs. `eligibilities`, which is just the planned criteria). Useful for population comparability across trials. |
| `overall_officials` | PI name/role/affiliation per study — KOL identification. |

### `mesh_terms`/`mesh_headings`: why skipped

Sampled both live (2026-08-23) before deciding, at the user's request — 10 rows each:
pure MeSH vocabulary/taxonomy (`id`, `qualifier`, `tree_number`/`heading`,
`mesh_term`/`subcategory`), **no `nct_id` column, no FK from or to anywhere** —
confirmed against `pg_constraint`, not just column-naming guesswork. The only thing
that could join them to a study is a text match on `browse_conditions.mesh_term` /
`browse_interventions.mesh_term`, and `gen-alz-schema`'s relation-detection is
FK-only, so there'd be no way to express that join in the generated schema anyway.
Skipping both; `browse_conditions.mesh_term`/`.mesh_type` already carry the
normalized term text directly on the row, which is the part that's actually
reachable from a study.

**Real gotcha found while sampling `browse_conditions`/`browse_interventions`
themselves, worth knowing before writing queries against them:** `mesh_type` isn't
just descriptive metadata — it has exactly two values, and most rows are the *less*
useful one:

| `mesh_type` | meaning | `browse_conditions` rows | `browse_interventions` rows |
|---|---|---|---|
| `mesh-list` | the actual term(s) assigned to the study | 836,005 | 471,756 |
| `mesh-ancestor` | every broader parent category in the MeSH tree, auto-expanded | 3,514,210 | 2,074,095 |

So each real term comes with ~4-5 auto-added ancestor rows (eg a thalassemia study's
`browse_conditions` also gets rows for "Hematologic Diseases", "Genetic Diseases,
Inborn", ...). An early test query here ("most common MeSH-normalized conditions",
no `mesh_type` filter) returned `"Pathological Conditions, Signs and Symptoms"` as
the #1 result with 141,521 hits — that's ancestor-inflation, not a real finding; a
correct version needs `WHERE mesh_type = 'mesh-list'`. Not fixed here (the user
asked for a documentation note, not a code change) — `schema_gen.clj`'s
`table-docs`/dictionary-sourced field `:doc` for `mesh_type` doesn't currently warn
about this, so the LLM has no way to know unless a future example query models the
filter or a `:doc` override is added by hand.

## Tier 3 — situational, lower priority for this app

`countries` (mostly redundant with `facilities`); `milestones`/`participant_flows`/`drop_withdrawals`
(CONSORT-style attrition — valuable but three coupled tables); `facility_contacts`/
`facility_investigators`/`central_contacts` (operational, not analytical); `id_information`;
`provided_documents`/`documents`/`detailed_descriptions` (protocol text/links, not really
tabular-queryable); and the long tail of admin/metadata tables (`intervention_other_names`,
`links`, `tagged_terms`, `search_results`, `study_searches`, `retractions`, `pending_results`,
`result_agreements`, `result_contacts`, `responsible_parties`, `ipd_information_types`) — low
analytical value for a "computational biologist writing SQL" persona.

## Mechanics

(Updated 2026-08-23 to match this repo's actual post-split layout — the paths below
were stale, pre-dating the `hyperphor/nlq` extraction.) Adding a table: extend
`resources/config.edn`'s AACT `:tables` vector *and* `src/clj/hyperphor/nlq_aact/
schema_gen.clj`'s `tables` set (both must list it, config.edn feeds the live DB
query, `schema_gen.clj`'s set gates what `gen-alz-schema` includes); add a
`table-docs`/`table-icons` entry there too (no automatic source for those — pick an
unused icon, write a one-line doc). Then re-run `schema_gen.clj`'s `regenerate-schema`
(the `(comment ...)` block at the bottom of that file, or from a `lein run -m
clojure.main -e '...'` one-liner if not at a REPL) against a live AACT connection
(`AACT_USER`/`AACT_PASSWORD`) to regenerate `resources/aact/schema.alz.edn`. Real FK
constraints and low-cardinality columns become Alzabo relations/enums automatically;
AACT's own data-dictionary CSV (`resources/aact/documentation_20260805.csv`) fills in
field `:doc` strings with no extra work. Restart (or just wait for the next `lein
run`) to pick up the regenerated schema — `generate-schema-doc` (the HTML schema-tab
doc) runs automatically at boot, no separate step needed.
