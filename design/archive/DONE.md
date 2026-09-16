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
