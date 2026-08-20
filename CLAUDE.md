# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A small standalone NL-query demo app over [AACT](https://aact.ctti-clinicaltrials.org)
(Aggregate Analysis of ClinicalTrials.gov), built on `com.hyperphor/nlq`'s `:postgres`
source. It has its own live Postgres connection to a public AACT reporting instance and
does not depend on OKC (`hyperphor/nlq-demo`, by contrast, is a thin proxy in front of
OKC). This app exists as a minimal, real end-to-end demonstration of `hyperphor/nlq`'s
Postgres source working stand-alone. See `design/postgres.md` and `design/aact-tables.md`
for background, and (in the separate `ParkerICI/okc` repo)
`design/pg-aact-split-plan.md` for the full history of how this was split out of okc.

Both `com.hyperphor/way` (the web app framework: server, config, frontend shell/tabs,
object inspector) and `com.hyperphor/nlq` (the NL-to-SQL engine, Postgres source,
Alzabo schema generation, the `sql-query` frontend UI) are external library
dependencies (Maven coordinates in `project.clj`), not part of this repo. Most of what
this app does is *configure and thinly wire up* those two libraries — expect to spend
more time reading `resources/config.edn` and the library's own behavior than writing
new Clojure.

## Commands

```
# Run the server (reads AACT_USER/AACT_PASSWORD from env; PORT as arg or env)
export AACT_USER=... AACT_PASSWORD=...   # AACT read-only reporting account
lein run 8090
# -> open http://localhost:8090/

# Frontend dev iteration (after `npm install` once)
lein shadow watch app       # hot-reload cljs
lein shadow release app     # optimized build (what :uberjar's prep-tasks also run)

# Smoke test: clean build + run on a random port
bin/smoke-test.sh

# Hit the API directly without the UI
curl 'http://localhost:8090/api/qbox/query?id=sql&project=AACT&query=Show+full+studies+table'

# Build a deployable uberjar (runs shadow release + AOT as prep-tasks)
lein uberjar
```

There is no Clojure test suite in this repo (`package.json`'s `test` script is a stub).
Verification is done by running the app and exercising the NL_query tab, or via
`bin/smoke-test.sh`.

## Architecture

Backend (`src/clj/hyperphor/nlq_aact/`):

- **`core.clj`** — `-main`: loads `resources/config.edn` via `hyperphor.way.config`,
  regenerates the schema HTML doc (non-fatal if it fails — see below), then starts the
  `hyperphor.way.server` with the handler's routes.
- **`handler.clj`** — defines `/api/qbox/query`, the single API route the frontend's
  `sql-query` UI hits. Requires `hyperphor.nlq.sources.postgres` and
  `hyperphor.nlq.inspect` purely for their multimethod-registration side effects (no
  direct use of those namespaces' vars) — don't remove these requires even though
  nothing references them by name. This app is scoped to exactly one project (`"AACT"`)
  and one query type (`:sql`); `project` is accepted in the query params but ignored.
- **`schema_gen.clj`** — AACT-specific half of schema generation that's deliberately
  *not* in the generic `hyperphor/nlq` library: loading AACT's pipe-delimited data
  dictionary CSV, and the table docs/icons/labels/link-templates that turn
  `hyperphor.nlq.sources.postgres/gen-alz-schema` into `resources/aact/schema.alz.edn`
  (the checked-in Alzabo schema). Two entry points with very different cost:
  - `regenerate-schema` — hits the live AACT DB (~60s, mostly per-column enum-candidate
    scans against `pg_stats`/`pg_constraint`). REPL-only, per the `(comment ...)` block
    at the bottom of the file; never called at app startup.
  - `generate-schema-doc` — local-file-only (reads the already-generated
    `schema.alz.edn`), fast, called from `core.clj`'s `-main` on every boot to produce
    `resources/public/AACT/schema/index.html` (the schema tab's iframe target).

Frontend (`src/cljs/hyperphor/nlq_aact/frontend/core.cljs`):

- Modeled directly on okc's own `frontend/core.cljs`, but a minimal slice: no
  header/modal/flash/login, no multi-project selector (exactly one project here).
- Three tabs via `hyperphor.way.tabs/tabs-nav`: `:home` (static about text), `:NL_query`
  (`hyperphor.nlq.frontend.sql-query/ui` — the real query-UI component the `nlq` library
  ships, called against the single `"AACT"` project), `:schema` (an iframe onto the
  generated Alzabo schema doc at `/AACT/schema/index.html`).
- The schema iframe `:src` must be an *absolute* path — `tabs-nav` routes via
  accountant's real `pushState`, so a relative src resolves against the current route
  rather than the site root.

Config (`resources/config.edn`) — read via `aero` (profiles `:dev`/`:server`/`:default`,
see `hyperphor.way.config`):

- `:app-main` is `"hyperphor.nlq_aact.frontend.core.init"` — note the **underscore** in
  `nlq_aact`, not the hyphen the namespace is written with. This is the compiled JS
  global path `window.onload` calls; cljs munges every namespace segment's `-` to `_`
  for JS-identifier safety, and a hyphenated form here silently fails to boot the app
  (parses as JS subtraction).
- The single `:nlq` project entry (`"AACT"`) carries: the Postgres connection (host,
  `pg-schema "ctgov"`, credentials via `#env AACT_USER`/`#env AACT_PASSWORD`), `:tables`
  (a curated ~14-table subset of AACT's 70+ table `ctgov` schema — see
  `design/aact-tables.md` for what's in/out and why), the `:schema` path, SQL dialect,
  canned `:examples` (NL → SQL pairs used both as prompt few-shots and UI suggestions),
  and the `:llm` provider/model/system-prompt.
- No credentials ever live in this file or repo — `AACT_USER`/`AACT_PASSWORD`/`PORT` all
  come from env vars via aero's `#env` tag. Never replace these with literal values.

## Known issue: `/api/config` ships credentials to the browser

`hyperphor.way.ui.config/init` (called by the frontend shell on every page load) fetches
`GET /api/config`, which returns the **entire** raw config map unredacted — including
`AACT_USER`/`AACT_PASSWORD`. There's a properly-redacted path
(`hyperphor.way.data`'s `:config` data-method via `/api/data?data-id=config`), but the
real frontend init path doesn't use it. This is a `hyperphor/way` bug affecting every
`way`-based frontend (not fixed here — out of scope for this repo). AACT's own exposure
is low-stakes (a read-only public-data account), but keep this in mind before wiring in
credentials that matter.

## Adding an AACT table

Extend `resources/config.edn`'s `:tables` vector and `schema_gen.clj`'s `tables` set /
`table-docs` / `table-icons` (no automatic source for icon/doc text — write them by
hand), then re-run `schema-gen/regenerate-schema` from a REPL against a live AACT
connection (see `resources/aact/schema.alz.edn`'s generation note in the README). Real
FK constraints and low-cardinality columns become Alzabo relations/enums automatically;
AACT's own data-dictionary CSV (`resources/aact/documentation_20260805.csv`) fills in
field `:doc` strings. See `design/aact-tables.md` for the full survey of which of
AACT's ~50+ other tables are worth adding next and why.
