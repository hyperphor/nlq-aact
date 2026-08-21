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



## Show full studies table

times out due to size, need paging or some better theory

Replaced with a limited one

**Status: DONE** (already fixed, prior to this pass — confirmed by re-reading
`resources/config.edn`'s current `:examples`: the example is now "Show the
1000 most recent studies, in full" / `... ORDER BY study_first_posted_date
DESC LIMIT 1000;`, with `:prompt? false` so it doesn't also bias the LLM's
few-shot prompt for unrelated queries).

