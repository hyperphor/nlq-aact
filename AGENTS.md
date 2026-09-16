# AGENTS.md

Companion to `CLAUDE.md`, which covers architecture and config in depth. This file adds
agent-specific gotchas that are easy to miss.

## Active branch

Development happens on `ui-makeover`, not `main`. Always check which branch you're on.

## Verification commands

```bash
# Verify ClojureScript compiles (fast, ~4s — do this after every cljs edit)
lein shadow compile app

# Verify Clojure compiles (slower, ~30s — after clj edits)
lein check

# Full smoke test: clean build + run on a random port
bin/smoke-test.sh
```

There is no test suite. Correctness is verified by running the app.

## `checkouts/` are symlinks to sibling repos — not copies

```
checkouts/nlq -> ../../nlq   (/opt/mt/repos/hyperphor/nlq)
checkouts/way -> ../../way   (/opt/mt/repos/hyperphor/way)
```

`checkouts/` is gitignored in nlq-aact, but edits there ARE edits to the real library
repos. This means:
- Changes to `checkouts/nlq/src/...` take effect immediately in `lein shadow compile`
  (Leiningen checkouts override the Maven jar during local dev).
- To persist changes: commit in the library repo, then `lein install` there to publish
  the updated jar locally, then bump the version pin in `nlq-aact/project.clj`.
- Current pins: `com.hyperphor/nlq "0.3.25"`, `com.hyperphor/way "0.2.7"`.

## Library change workflow

```bash
# 1. Edit source in /opt/mt/repos/hyperphor/nlq (or /way)
# 2. Verify it compiles in nlq-aact (checkouts override the jar)
lein shadow compile app
# 3. Commit in the library repo
git -C /opt/mt/repos/hyperphor/nlq commit ...
# 4. Install the updated jar locally (version stays the same, jar is overwritten)
cd /opt/mt/repos/hyperphor/nlq && lein install
# 5. Bump the pin in nlq-aact/project.clj only if the library version changed
```

## Four source files, ~550 LOC total

The app is thin wiring. Most behavior lives in the libraries. When something doesn't
work, look in the library source first:

| File | Role |
|---|---|
| `src/clj/.../core.clj` | `-main`: config → schema doc → server |
| `src/clj/.../handler.clj` | Single API route `/api/qbox/query` |
| `src/clj/.../schema_gen.clj` | AACT-specific schema generation |
| `src/cljs/.../frontend/core.cljs` | App shell: tabs, home, visualize, schema, about |

## handler.clj: side-effect requires must not be removed

```clojure
hyperphor.nlq.sources.postgres   ; registers :postgres source multimethod
hyperphor.nlq.inspect            ; registers inspect multimethods
```

These are required purely for side effects (multimethod registration). Nothing
references their vars by name. Removing them silently breaks query execution.

## ClojureScript paren discipline

The frontend is all Hiccup/Reagent. Bracket mismatches in `core.cljs` produce an
`Unexpected EOF` at the file's last line — the real error is always further up.
After any edit, run `lein shadow compile app` immediately to catch parse errors before
they accumulate.

## MUI Autocomplete: `disablePortal true` is required

The app runs inside an iframe (way's `html-frame-spa`). Without `disablePortal`, MUI
Autocomplete portals its dropdown to `document.body` and miscalculates position. All
`qbox/ui` Autocomplete instances use `{:disablePortal true}`.

## Visualize tab data flow

```
[:qbox-response :sql]       → results, columns, nl (SQL query results)
[:qbox-response :sql-vizq]  → viz-spec, viz-text, error (viz generation results)
[:form :sql-vizq :query-code] → editable Vega-Lite JSON in the Vega card
```

"Run Vega" (`qbox-requery` for `:sql-vizq`) parses the edited JSON client-side and
writes directly to `[:qbox :sql-vizq :response :viz-spec]` — no server call.

The `"Vegalite"` project entry in `config.edn` is required for viz generation — it's
looked up by exact name in `visgen/viz-endpoint`. Without it, the Vega card has no
examples and `*project-conf*` is nil.

## Schema regeneration is REPL-only (~60s, requires live DB)

```clojure
(require '[hyperphor.way.config :as config])
(config/read-config "config.edn")
(require '[hyperphor.nlq-aact.schema-gen :as sg])
(sg/regenerate-schema)
```

`generate-schema-doc` (HTML only, fast, no DB) is called automatically at boot.
`regenerate-schema` (hits live AACT DB, rewrites `schema.alz.edn`) is never called
at startup.

## Design docs are living documents

`design/TODO.md` — active work items with root-cause analyses.  
`design/DONE.md` — completed items, preserved for context on past decisions.  
Both are worth reading before starting any non-trivial change.
