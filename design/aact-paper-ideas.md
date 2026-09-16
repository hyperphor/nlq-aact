**Status (2026-09-06): items 1-3 done.** Added to `resources/config.edn`'s
`:examples` and verified live against the AACT Postgres instance: masking
completeness by year (1999 36.6% -> 2005 85.7% -> 2010 77.1%, matching the
paper's own Figure 3 trend), results-reporting compliance by lead sponsor (real
sponsors found at 0% among 20+ completed, >2-year-old trials), and an
evidence-rigor query for melanoma (4,274 interventional trials, 7.8% both
randomized and blinded). Items 4 and 5 (approximate/real specialty
classification) not started.

# Ideas from the AACT paper (Tasneem et al. 2012, `design/aact-paper.pdf`)

Read at the user's request looking for inspiration for new functionality/examples.
The paper is really about two things: (1) how AACT itself was built (ETL from
ClinicalTrials.gov XML, normalized to 2NF) — not relevant, we consume the finished
DB — and (2) a methodology for **regrouping studies by clinical specialty** using
MeSH + free-text condition terms, validated against manual review. That second half,
plus a couple of the paper's own figures, suggest some concrete example queries and
one bigger optional feature. Ranked cheapest-to-biggest.

## 1. Replicate Figure 3: data-completeness-over-time, tied to real regulatory dates

The paper's headline chart plots % of studies with complete data (enrollment,
masking, allocation, intervention model, gender, lead sponsor, ...) by registration
year, with vertical lines at the 2005 ICMJE registration policy and the 2007 FDAAA
mandate — completeness visibly jumps at both. Every field in that chart lives in
`studies`/`designs`/`calculated_values`, which are already in our schema. This is a
compelling demo precisely *because* it's a real historical/regulatory story, not a
made-up example:

- "What fraction of studies had enrollment recorded, by year, from 2000 to 2010?"
- "Show masking completeness by year — did it change after the 2007 FDAAA mandate?"

## 2. Results-reporting compliance (`calculated_values.were_results_reported`)

FDAAA also mandated *posting results*, not just registering — still a live
compliance topic today (ClinicalTrials.gov's own noncompliance-notice enforcement).
`calculated_values.were_results_reported` + `months_to_results_reported` make this a
plain aggregate query, no new tables:

- "What fraction of completed trials have posted results, by year?"
- "Which sponsors have the worst results-reporting compliance rate?"

## 3. Approximate specialty/therapeutic-area buckets

The paper's actual contribution — MeSH-tree-based clinical-specialty regrouping —
isn't cheaply replicable: it needed `mesh_terms`/`mesh_headings` (no FK to anything,
already excluded per `design/aact-tables.md`) plus manual clinician annotation, and
even then had **4–22% misclassification** against manual review (their Table 7).
Full replication is out of scope. But a *lightweight, honestly-caveated* version is
just a `CASE WHEN condition.downcase_name ILIKE ...` bucket over a handful of
specialties (oncology, cardiology, mental health, ...) — the same
keyword-heuristic pattern already used for eligibility-criteria examples
(`design/in-exclusion.md`). Useful for:

- "Compare trial counts for oncology vs. cardiology vs. mental health over time"
  (paper's Discussion §2, "comparative evolution ... by clinical specialties")
- Would need a few-shot example (or system-prompt nudge) spelling out the keyword
  buckets and an explicit caveat that this is an approximation, not the paper's
  validated taxonomy — same spirit as the `mesh_type` ancestor-inflation warning in
  `design/aact-tables.md`.

## 4. Evidence-level / trial-rigor queries

Also explicitly named in the paper's Discussion as future work ("comparative
distribution of evidence levels across specialties/conditions"). Fully answerable
today with `designs.allocation`/`.masking`/`studies.phase` — no new tables, no
approximation needed, unlike #3:

- "What percentage of trials for [condition] are randomized and double-blind?"
- "Which conditions have the most phase 3 trials relative to phase 1?" (crude
  evidence-maturity proxy)

## 5. Bigger optional feature: a real locally-owned specialty classification

If specialty-grouping turns out to be a recurring ask, the paper's own method is a
blueprint for a proper (not keyword-hack) version: use `browse_conditions`/
`browse_interventions` (`mesh_type = 'mesh-list'` only, per the ancestor-inflation
gotcha already documented) against a small hand-maintained MeSH-tree-number-prefix →
specialty mapping (the paper's Table 2 top-level disease categories, e.g. `C14` =
Cardiovascular), computed once into a locally-owned sidecar table and wired into the
Alzabo schema like any other table — same pattern as `design/in-exclusion.md`'s
option 3 for eligibility criteria (this app has no write access to `ctgov` itself,
so it'd have to be storage *this app* owns). Bigger lift; only worth it if #3's
cheap version proves genuinely useful first.

## Not pursued

- Geographic distribution vs. regional disease burden (paper's Discussion §1) —
  needs an external burden-of-disease dataset, out of scope for this app.
- MeSH-tree multi-parent ambiguity (paper's Acromegaly example, Figure 5) — already
  effectively documented as the `mesh_type` ancestor-inflation gotcha in
  `design/aact-tables.md`; nothing new to add.
