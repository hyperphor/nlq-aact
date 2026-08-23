(defproject hyperphor/nlq-aact "0.1.0"
  :description "Standalone NL-query demo app over AACT (aggregate ClinicalTrials.gov data) --
                a small, real backend showing hyperphor/nlq's :postgres source
                working end to end, not a thin proxy like nlq-demo. See
                design/postgres.md and design/aact-tables.md."
  :url "https://github.com/hyperphor/nlq-aact"
  :plugins [[lein-shadow "0.4.1"]]
  ;; org.clojure/java.jdbc + org.postgresql/postgresql come transitively via
  ;; com.hyperphor/nlq's own sources/postgres.clj dependency -- no need to
  ;; redeclare them here (checked: nlq's project.clj already pins both).
  ;; reagent/re-frame/cljs-ajax/accountant/secretary/@mui-material-backing
  ;; requires similarly come transitively via com.hyperphor/way's own
  ;; project.clj -- only what nlq-aact's own cljs actually requires directly
  ;; (none, so far) would need adding here.
  :dependencies [[org.clojure/clojure "1.12.5"]
                 [com.hyperphor/way "0.2.7"]
                 [com.hyperphor/nlq "0.3.6"]
                 [com.taoensso/timbre "6.7.1"]
                 [environ "1.2.0"]
                 ;; Direct dep, NOT :dev-profile-only -- `lein uberjar`
                 ;; activates :uberjar, not :dev, so a :dev-scoped
                 ;; shadow-cljs is invisible to :uberjar's own
                 ;; ["shadow" "release" "app"] prep-task below (verified:
                 ;; that's exactly the FileNotFoundException a first attempt
                 ;; at this hit). Same reason okc keeps it direct too, per
                 ;; that repo's own identical comment. :exclusions
                 ;; ring/ring-core: shadow-cljs pins its own older ring-core,
                 ;; which can win Leiningen's nearest-wins tie-break over
                 ;; way's newer one once way is only a transitive (not
                 ;; direct) dependency, which it is here (arrives via
                 ;; com.hyperphor/nlq) -- see design/hyperphorization.md.
                 [thheller/shadow-cljs "3.1.8" :exclusions [ring/ring-core]]]
  :main ^:skip-aot hyperphor.nlq-aact.core
  :source-paths ["src/clj" "src/cljs"]
  :resource-paths ["resources"]
  :target-path "target/%s"
  :clean-targets ^{:protect false} [".shadow-cljs" "resources/public/cljs-out" "target" "shadow-cljs.edn"]
  :uberjar-name "aact-standalone.jar"
  :profiles {:uberjar {:aot :all
                       :omit-source true
                       :prep-tasks [["shadow" "release" "app"] "javac" "compile"] ;NOTE omitting javac/compile breaks :aot, per okc's own note
                       :resource-paths ["resources"]
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}
  :shadow-cljs {:lein true
                :builds
                {:app {:target :browser
                       :compiler-options {:infer-externs true}
                       :output-dir "resources/public/cljs-out"
                       :asset-path "/cljs-out"
                       :modules {:dev-main {:entries [hyperphor.nlq-aact.frontend.core]}}}}})
