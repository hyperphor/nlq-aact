# AACT table coverage: what's used vs. what's available

`resources/config.edn`'s AACT `:nlq` entry only points `org.parkerici.okc.sources.postgres/gen-alz-schema`
at a subset of AACT's `ctgov` schema (~50 tables total). This is a survey of what's
in, what's out, and which of the unused tables are worth adding next — grounded in
AACT's live schema/data-dictionary pages (https://aact.ctti-clinicaltrials.org/schema,
.../data_dictionary), not a from-scratch guess.

## Currently wired in

`studies`, `sponsors`, `conditions`, `interventions`, `facilities`, `outcomes`,
`eligibilities`, `brief_summaries`, `keywords`, `design_groups` — plus, as of this
pass, `calculated_values`, `designs`, `study_references`, `reported_events`.

That's the "study identity + design skeleton" slice, now extended with derived
summary fields, methodology, publications, and safety data (see Tier 1 below).

## Tier 1 — added (small, 1:1 or trivial join off `nct_id`, same complexity as what was already there)

| Table | Why it matters here |
|---|---|
| `calculated_values` | 1 row/study, AACT-precomputed: actual enrollment, actual duration, min/max age in years, `were_results_reported`, months-to-results, number of arms/facilities. Turns queries that would otherwise need hand-derived SQL ("how long did this trial run", "does it have posted results") into a plain column lookup. |
| `designs` | 1 row/study: `allocation`, `intervention_model`, `masking`, `primary_purpose`, `observational_model`. Trial-methodology fields (randomized? blinded? parallel vs crossover?) that `studies`/`phase` don't cover — a natural sibling to the existing `design_groups`. |
| `study_references` | PubMed-linked publications per trial (PMID, citation, reference_type: result/background/derived). For a research-scientist audience, "show me the papers behind this trial" is an obvious ask this now answers. |
| `reported_events` | Adverse-event data per arm (serious vs. other, organ system, term, counts, subjects-at-risk). Thematically the best fit of anything on this list — the app already has an irAE-focused heatmap for RADIOHEAD's own patients; this is the AACT-wide equivalent (cross-trial immunotherapy safety landscape). |

Not yet added, but identified as the same tier:

| Table | Why it matters here |
|---|---|
| `design_outcomes` | The *planned* primary/secondary outcome measures (vs. `outcomes`, which is the *reported* results). Obvious pairing — you have the "actual" half, not the "as-designed" half. |
| `design_group_interventions` | Join table between `design_groups` (arms) and `interventions` — right now both tables exist but nothing ties "arm A got drug X" together except a shared `nct_id`. |
| `reported_event_totals` | Companion to `reported_events` — study-level adverse-event summary counts. |

## Tier 2 — good value, more schema/prompt weight (not added)

| Table | Why / caveat |
|---|---|
| `outcome_measurements`, `outcome_analyses`, `outcome_analysis_groups`, `result_groups` | The *actual posted numbers* (arm-level means/CIs) and statistical comparisons (p-values, CIs) for completed trials — richer than `outcomes`, which only holds outcome *definitions*. This is what "which trials showed a significant OS/PFS benefit" queries need. Wide, nullable-heavy schema (values differ by measurement type) — bigger NLQ prompt footprint, so worth a separate schema-gen pass rather than folding into Tier 1. |
| `browse_conditions`, `browse_interventions` (+ `mesh_terms`/`mesh_headings`) | MeSH-normalized condition/intervention terms. Today `conditions.name`/`interventions.name` are free text ("NSCLC" vs "Non-Small Cell Lung Cancer" vs "Lung Cancer, Non-Small Cell" as distinct strings) — this gives a controlled vocabulary, which matters a lot for "find trials like this one" or condition roll-ups. |
| `baseline_measurements`, `baseline_counts` | Actual enrolled-population demographics per arm (vs. `eligibilities`, which is just the planned criteria). Useful for population comparability across trials. |
| `overall_officials` | PI name/role/affiliation per study — KOL identification. |

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

Adding a table is just: extend `resources/config.edn`'s AACT `:tables` vector, then re-run
the dev-time `(comment ...)` block in `src/clj/org/parkerici/okc/sources/postgres.clj`
(`load-dictionary` + `gen-alz-schema` + `spit`) against a live AACT connection
(`AACT_USER`/`AACT_PASSWORD`) to regenerate `resources/aact/schema.alz.edn`. Real FK
constraints and low-cardinality columns become Alzabo relations/enums automatically;
AACT's own data-dictionary CSV fills in field `:doc` strings. `table-docs`/`table-icons`
in that same comment block need a manual entry per new table (there's no icon/doc source
to pull those from automatically).
