# Competitive Analysis

Compare this offering against other clinical trial search tools, especially those that use natural language. Is the schema-based approach an advantage? What about MCPs, Claude Science, stuff like that?

## The incumbent: ClinicalTrials.gov itself

The baseline every alternative gets compared to. Its search UI is a faceted-filter form —
condition, status, phase, location, age — built for people who already know the vocabulary
(`RECRUITING`, `PHASE3`, MeSH terms) and are willing to click through pages of checkboxes.
It answers "show me trials matching these exact fields" well. It cannot answer "which
melanoma trials showed a significant survival benefit" or "who are the PIs running
diabetes trials at Stanford" — those need a join across `outcomes`/`outcome_analyses`
or `facilities`/`overall_officials` that no facet picker exposes. That gap — structured
data, unstructured questions — is the whole reason this class of tool exists.

## Direct competitors (as of 2026)

- **The AACT MCP server** ([mcpmarket.com/server/aact](https://mcpmarket.com/server/aact))
  — the closest thing to a direct competitor. Same underlying data source, same idea
  (let an AI assistant query AACT), different shape: it's a tool an LLM calls from
  *inside* a chat client, not a standalone app with its own UI, examples, grid, or
  visualizations.

Sample prompts they list:
"What are the most common types of interventions in breast cancer clinical trials?"
"How many phase 3 clinical trials were completed in 2023?"
"Show me the enrollment statistics for diabetes trials across different countries"
"What percentage of oncology trials have reported results in the last 5 years?"

- **Anthropic's own Clinical Trials Connector** ([claude.com/connectors/clinical-trials](https://claude.com/connectors/clinical-trials))
  — Anthropic went from 6 to 12+ life-sciences MCP connectors in three months
  (Oct 2025–Jan 2026), ClinicalTrials.gov among them, alongside Medidata, ChEMBL,
  bioRxiv/medRxiv, Open Targets. This is the big one to have an honest answer for —
  see below.
- **TrialGPT** (NIH) — a different problem entirely: given a patient's clinical
  narrative, rank which trials they're eligible for. Patient-to-trial matching, not
  general-purpose querying. Complementary, not competing.
- **Antidote, Triomics** — recruitment/enrollment tooling for sponsors and sites, not
  a query interface at all. Different market.

## Is the schema-based approach an advantage?

Yes, and it's the actual thesis of the whole `hyperphor/nlq` + Alzabo stack this app
demonstrates, not just an AACT detail. Two concrete things a raw "give the LLM
database access and let it write SQL" MCP tool doesn't get for free:

1. **A curated, FK-real schema constrains generation.** AACT's live `ctgov` schema is
   70+ tables of clinical-trials-registry-specific structure (`design_groups` vs.
   `result_groups`, `outcomes` vs. `outcome_analyses`, `ctgov_group_code` joins that
   aren't obviously named) — a generic text-to-SQL tool pointed at the whole thing has
   to *infer* which of 70 tables and their real join keys matter for a given question,
   from schema introspection alone. This app's Alzabo schema (26 tables, hand-curated
   in three passes — see `design/aact-tables.md`) is generated from real
   `pg_constraint` foreign keys, not naming guesses, and ships table docs, icons, and
   enum-detected columns straight into the LLM's prompt. Every join the LLM writes is
   one this app's own maintainer already vetted exists and means what it looks like it
   means.
2. **It's not just a chat answer — it's a real UI.** Results render in a real,
   sortable/filterable grid with semantic column grouping and drill-down (click an
   `nct_id`, get that study's full record), not a wall of text. The schema itself is
   browsable as a real generated doc site (entity list, relationship diagram, enum
   values) — useful on its own, independent of any one query.

## MCPs / Claude's own connectors: threat, or something else?

Worth being straight about this rather than pretending it isn't a question: if
Anthropic's Clinical Trials Connector already lets Claude query ClinicalTrials.gov
data conversationally, why does a standalone app matter?

Because they're not the same product shape. A connector lives inside *your* Claude
session — useful if you already live there and want one more tool. This app is a
dedicated, shareable, no-login URL: send it to a colleague who doesn't have (or want)
a Claude subscription, embed it in a project page, point a non-technical
collaborator at it without explaining what an MCP server is. It's also legibly scoped
— one database, one purpose, an about page that says exactly what it does — where a
general-purpose assistant with a dozen connectors attached is not.

The more honest long-term framing: the schema-driven approach this app proves out
(Alzabo schema + `hyperphor/nlq`'s generation/logging/visualization layer) is
infrastructure, not a one-off AACT product. It works over any Postgres/BigQuery
database with real FK constraints, which is the actual reusable asset — AACT is the
demo, not the ceiling. If anything, that generation layer is a plausible *backend*
for exactly the kind of MCP tool call Anthropic's connectors make, not something that
has to out-compete them head to head.

# Audience

## Patients

Looking for a clinical trial for their condition.

Realistically, not this app's best fit today, and worth saying so rather than
overselling it. AACT is aggregate, structurally-oriented registry data (arms,
outcomes, eligibility *criteria* as text) — it answers "what trials exist for
condition X, and what are the stated criteria" well, but doesn't do patient-specific
eligibility screening (matching *this* patient's labs/history/prior-treatment against
criteria text) the way TrialGPT-style tools are purpose-built for. A patient's family
member with some data literacy could reasonably use it to get oriented — "what does
the treatment landscape for my diagnosis even look like" — before taking real
questions to their oncologist or a purpose-built matching tool. Positioning it as a
research-and-orientation tool for patients, not a "find my trial" tool, is the honest
pitch.

## Researchers

The clearest, strongest-fit audience — the one the app's own system prompt ("a
computational biologist and clinical trials expert") is already written for. A
researcher scoping a new trial wants competitive-landscape questions answered fast:
who else is running trials in this space, what interventions and outcome measures are
common, which prior trials in this condition showed a real effect (not just "was
studied" — `outcome_analyses`' p-values now make that a first-class query, not a
literature-review project). The pitch here is speed and correctness: what used to be
"open ClinicalTrials.gov, export CSVs, write pandas" is now a plain-English question,
answered with real SQL a computational biologist could read and trust, against a
schema someone already checked.

## Investors

Meaning biotech/pharma industry analysts — equity research, hedge fund healthcare
teams, VC diligence — who read clinical trial data to inform company valuations, not
investors in this app itself. This is a real, well-established workflow (buy-side and
sell-side biotech analysts spend real hours a week on ClinicalTrials.gov and AACT-derived
data already) and arguably the audience with the most direct willingness-to-pay of the
three, because the underlying questions map straight onto trading/investment decisions:

- **Catalyst tracking.** "Which phase 3 trials for [company]'s lead asset have a primary
  completion date in the next two quarters" — trial readouts are stock-moving events;
  knowing exactly when one's due, and cross-referencing `calculated_values`' actual vs.
  estimated completion dates against what a company guided to publicly, is a direct
  earnings-call-fact-check.
- **Reading the readout itself.** Once results post, "did this trial hit its primary
  endpoint" is exactly `outcome_analyses`' p-value/confidence-interval data — the same
  query this app already demonstrates working live (see `design/aact-tables.md`'s Tier 2
  writeup). A press release says "positive topline results"; the underlying `p_value`
  and effect size are what an analyst actually needs to judge whether that's true and
  how positive.
- **Competitive/pipeline mapping.** "How many active trials exist for this target/
  indication, and who's running them" (`sponsors`, `conditions`, `interventions`) is
  pipeline density analysis — the first slide of any biotech sector research note. Also
  where `browse_conditions`' MeSH normalization pays off (see the earlier note on its
  `mesh_type` gotcha) — "melanoma" trials shouldn't undercount because half of them say
  "cutaneous melanoma" or "unresectable melanoma" instead.
- **Risk signals.** Terminated or withdrawn trials, especially late-phase ones, are
  exactly the kind of thing that craters a stock on the day it's disclosed and gets
  missed for weeks by anyone not actively monitoring `studies.overall_status` and
  `why_stopped`. A standing "alert me to phase 3 terminations in oncology" query is a
  realistic, high-value use case this schema already supports.
- **Diligence on trial quality, not just existence.** `designs` (randomized? blinded?
  parallel vs. crossover?) and `overall_officials` (which PIs/institutions are actually
  running this) let an analyst sanity-check whether a company's trial design is rigorous
  enough to be trusted, not just whether a trial with the right name exists — the kind
  of question that separates real diligence from a headline-reading bot.

The pitch to this audience specifically: everything above is currently manual —
reading press releases, cross-referencing ClinicalTrials.gov by hand, or paying for a
dedicated biotech-intelligence terminal. A natural-language interface over the same
public AACT data, with real joins already vetted, turns each of those into a single
plain-English question answered in seconds instead of an analyst's afternoon.

