# Really an NLQ feature but lets get it working herfe

There's already an :nlq-log setting in config, I want this to use dynamodb, copy the logic and code from pimento/logging.  Should function like the nlq bigquery logging. Need a story for AWS credentials 

**Status: DONE.** `:nlq-log` (`resources/config.edn`) now has a real value
(was a `{:type :dynamo :tbd nil}` placeholder):

```clojure
:nlq-log {:type :dynamo
          :table "nlq-log"
          :region "us-west-2"}
```

Implemented in the `hyperphor/nlq` library (`/opt/mt/repos/hyperphor/nlq`, not this
repo — "really an NLQ feature" per the title), not nlq-aact's own code, since it's
generic logging infrastructure any `nlq`-based app can use, exactly like the existing
BigQuery path:

- **`hyperphor.nlq.logging.dynamo`** (new ns) — `write-item`/`all-items`/
  `recent-items`. Adapted from `pici/pimento`'s `pimento.logging.dynamo` (same AWS
  client/credentials/tag-attribute pattern — `cognitect.aws`, `environment-
  credentials-provider`), generalized from pimento's single hardcoded `"pimento_log"`
  table to a config-supplied `table`/`region`, since this is a shared library used by
  more than one app. One correction from the pimento original: its datetime format
  string (`"YYYY-MM-dd HH:MM:SS"`) reused `MM` for both month *and* minutes (a real
  bug — `mm` is minutes) — fixed to `"yyyy-MM-dd HH:mm:ss"` here, matching
  bigquery.clj's own (correct) formatter.
- **`hyperphor.nlq.generate`** — `record`/`recent`/`all-log-rows` (previously
  BigQuery-only) now dispatch on `(:nlq-log config)`'s `:type`: `:dynamo` (new) or
  `:bigquery` (existing behavior, also the default when `:type` is absent, so okc's
  own `:nlq-log` config — no `:type` key — keeps working unchanged). Added new
  `com.cognitect.aws/dynamodb "871.2.39.3"` dependency. Bumped `com.hyperphor/nlq`
  0.3.3 → 0.3.4, `lein install`ed locally (not yet committed/pushed/released to
  Clojars — same as the 0.3.3 bump earlier this session).
- **Robustness fix, not just a dynamo-specific one:** `record` previously ran with no
  exception handling at all — a broken log target (bad credentials, unreachable
  table, anything) would throw *out of the request that triggered it*, 500-ing an
  otherwise-successful query response just because logging failed. Wrapped in
  try/catch + `log/warn`, verified live (see below): a query still returns its real
  results even when the configured DynamoDB target is completely unreachable. This
  protects the existing BigQuery path too, not just the new one.

## AWS credentials story

Same pattern as `AACT_USER`/`AACT_PASSWORD`: environment variables, nothing literal
in any file. `credentials/environment-credentials-provider` (cognitect.aws) reads
`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` (+ optional `AWS_SESSION_TOKEN`).
Documented in the README's new "Query logging (optional)" section, including the
recommended IAM scope: a user/role limited to `PutItem`+`Scan` on just the one log
table, not broad AWS access — this is a public demo app, keep its credential surface
as small as the AACT db account's.

**Not done / left for whoever sets this up for real:** provisioning the actual
DynamoDB table. Neither this app nor `hyperphor.nlq.logging.dynamo` creates it —
create `nlq-log` by hand in `us-west-2` with a String partition key named
`"uuid"` (matching what `write-item` assigns) before setting `AWS_ACCESS_KEY_ID`/
`AWS_SECRET_ACCESS_KEY` for real. No `create-table` helper exists (pimento's own
`dynamo.clj` doesn't have one either — table creation was always a manual/console
step there too).

## Verified (2026-08-21)

No AWS credentials in this sandbox, so the real DynamoDB write path (against a real
table) isn't exercised — but the failure-handling *is*, and thoroughly: booted a real
`lein run` with `:nlq-log {:type :dynamo ...}` configured and zero AWS env vars set,
ran a live NL query against real AACT — response came back with real results and no
`:error`, while the server log shows the expected non-fatal warning:

```
WARN [hyperphor.nlq.generate:314] - NLQ log write failed {:type :dynamo}
clojure.lang.ExceptionInfo: DynamoDB error
```

Also `lein check` clean (no compile errors, only pre-existing bigquery.clj/cirro.clj
reflection-warning noise) in both `nlq` and `nlq-aact`.

## Not built: a frontend log-viewer tab

`hyperphor.nlq.generate` already registers a generic `wd/data :nlq-log-full` method
(`(all-log-rows)`), so `/api/data?data-id=nlq-log-full` works automatically for
*any* configured `:nlq-log` target, dynamo included — no extra backend code needed.
okc has its own frontend viewer for this (`org.parkerici.okc.frontend.log`, a `:log`
tab gated on `config/config :dev-mode`), but that's app-specific cljs, not part of
`hyperphor/nlq` itself, and nlq-aact doesn't have an equivalent tab. Out of scope for
this pass ("get it working" read as the logging pipeline itself, not a UI for
browsing it) — straightforward to add later by porting okc's `log.cljs` if wanted.
