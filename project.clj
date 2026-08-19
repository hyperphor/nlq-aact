(defproject hyperphor/nlq-aact "0.1.0"
  :description "Standalone NL-query demo app over AACT (aggregate ClinicalTrials.gov data) --
                a small, real backend showing hyperphor/nlq's :postgres source
                working end to end, not a thin proxy like nlq-demo. See
                design/postgres.md and design/aact-tables.md."
  :url "https://github.com/hyperphor/nlq-aact"
  ;; org.clojure/java.jdbc + org.postgresql/postgresql come transitively via
  ;; com.hyperphor/nlq's own sources/postgres.clj dependency -- no need to
  ;; redeclare them here (checked: nlq's project.clj already pins both).
  :dependencies [[org.clojure/clojure "1.12.5"]
                 [com.hyperphor/way "0.2.6"]
                 [com.hyperphor/nlq "0.3.1"]
                 [com.taoensso/timbre "6.7.1"]
                 [environ "1.2.0"]]
  :main ^:skip-aot hyperphor.nlq-aact.core
  :source-paths ["src/clj"]
  :resource-paths ["resources"]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
