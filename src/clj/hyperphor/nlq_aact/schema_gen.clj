(ns hyperphor.nlq-aact.schema-gen
  "AACT-specific schema generation -- the half of pg-aact's original
   sources/postgres.clj that was deliberately left out of the generic
   hyperphor/nlq library (see okc's design/pg-aact-split-plan.md): the AACT
   data-dictionary CSV loader, and the table names/docs/icons/labels/link
   templates that turn hyperphor.nlq.sources.postgres/gen-alz-schema (fully
   generic) into AACT's actual resources/aact/schema.alz.edn.

   `regenerate-schema` (DB-hitting) is NOT run at startup -- call it from a
   REPL against a live AACT connection whenever AACT's schema changes or the
   data dictionary CSV is refreshed. `generate-schema-doc` (HTML-doc-only,
   no DB access, just reads the already-generated schema.alz.edn) IS called
   at startup, from core.clj -- see that fn's docstring."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [hyperphor.nlq.sources.postgres :as pg]
            [hyperphor.nlq.schema :as schema]
            [hyperphor.nlq.config :as nlqc]))

;;; The dictionary's Description column mixes real semantic docs in with
;;; structural boilerplate ("primary key", "foreign key referencing X") that's
;;; already implied by :unique-id/the field's :type override in gen-alz-schema
;;; -- keep only the former.
(defn load-dictionary
  "table|field -> doc string, from AACT's own pipe-delimited data dictionary
   CSV (download it: curl https://aact.ctti-clinicaltrials.org/documentation/download_csv)."
  [csv-path]
  (with-open [r (io/reader csv-path)]
    (let [rows (doall (map #(str/split % #"\|" -1) (line-seq r)))
          [_header & rows] rows]
      (into {}
            (keep (fn [[table field _type _nullable doc]]
                    (when (and (seq doc) (not (re-find #"(?i)^(primary|foreign) key" doc)))
                      [[table field] doc])))
            rows))))

;; Tier 1/2 per design/aact-tables.md -- mesh_terms/mesh_headings
;; deliberately excluded (no real FK, see that doc's Tier 2 section and this
;; set's own comment in config.edn).
(def tables
  #{"studies" "sponsors" "conditions" "interventions" "facilities"
    "outcomes" "eligibilities" "brief_summaries" "keywords" "design_groups"
    "calculated_values" "designs" "study_references" "reported_events"
    "design_outcomes" "design_group_interventions" "reported_event_totals"
    "outcome_measurements" "outcome_analyses" "outcome_analysis_groups" "result_groups"
    "browse_conditions" "browse_interventions"
    "baseline_measurements" "baseline_counts"
    "overall_officials"})

(def table-docs
  {"studies"           "A single registered clinical trial (one row per NCT ID)"
   "sponsors"          "An organization sponsoring or collaborating on a study"
   "conditions"        "A condition/disease a study targets"
   "interventions"     "A treatment, drug, device, or procedure being studied"
   "facilities"        "A site where a study is conducted"
   "outcomes"          "An outcome measure defined for a study"
   "eligibilities"     "A study's eligibility criteria (age, sex, inclusion/exclusion)"
   "brief_summaries"   "A study's short public-facing summary"
   "keywords"          "A free-text keyword tag associated with a study"
   "design_groups"     "An arm/group in a study's design (eg treatment vs placebo)"
   "calculated_values" "AACT-precomputed summary values for a study (actual enrollment/duration, results-reported status, age range)"
   "designs"           "A study's design methodology (allocation, masking/blinding, intervention model, primary purpose)"
   "study_references"  "A publication (PubMed-linked) associated with a study"
   "reported_events"   "An adverse event reported for a study, by event type (serious/other) and arm"
   "design_outcomes"           "A study's planned primary/secondary outcome measure (vs. outcomes, the reported results)"
   "design_group_interventions" "Join table linking a study's arms (design_groups) to the interventions each arm received"
   "reported_event_totals"     "Study-level adverse-event summary counts (subjects affected/at risk), by event type and arm"
   "outcome_measurements"      "The actual posted numeric result (per-arm count/mean/etc) for a defined outcome measure"
   "outcome_analyses"          "A statistical comparison (p-value, confidence interval) between arms for a defined outcome measure"
   "outcome_analysis_groups"   "Join table linking an outcome_analysis to the result_groups (arms) it compares"
   "result_groups"             "An arm/group as reported in a study's posted results (vs. design_groups, the as-designed arms)"
   "browse_conditions"         "A MeSH-normalized condition term for a study (controlled vocabulary, vs. conditions' free text)"
   "browse_interventions"      "A MeSH-normalized intervention term for a study (controlled vocabulary, vs. interventions' free text)"
   "baseline_measurements"     "A baseline (enrolled-population) characteristic measurement for a study arm, as posted in results"
   "baseline_counts"           "Baseline participant counts per study arm, as posted in results"
   "overall_officials"         "A principal investigator or study official (name, role, affiliation) for a study"})

(def table-icons
  {"studies"           "🧪"
   "sponsors"          "🏢"
   "conditions"        "🩺"
   "interventions"     "💊"
   "facilities"        "🏥"
   "outcomes"          "📊"
   "eligibilities"     "✅"
   "brief_summaries"   "📄"
   "keywords"          "🏷️"
   "design_groups"     "👥"
   "calculated_values" "🧮"
   "designs"           "📐"
   "study_references"  "📚"
   "reported_events"   "⚠️"
   "design_outcomes"            "🎯"
   "design_group_interventions" "🔗"
   "reported_event_totals"      "🧾"
   "outcome_measurements"       "📈"
   "outcome_analyses"           "🔬"
   "outcome_analysis_groups"    "🧩"
   "result_groups"              "🗂️"
   "browse_conditions"          "📇"
   "browse_interventions"       "🗃️"
   "baseline_measurements"      "📏"
   "baseline_counts"            "🔢"
   "overall_officials"          "📛"})

(def table-labels
  {"studies"              "brief_title"
   "result_groups"        "title"
   "outcome_measurements" "title"
   "baseline_measurements" "title"
   "overall_officials"    "name"})

;; {{value}}-templated, matching hyperphor.nlq.schema/external-link-template's
;; generic mustache shape -- NOT pg-aact's original bare-prefix :external-url
;; (that mechanism was reconciled away during the library port, see
;; design/pg-aact-split-plan.md's "Key reconciliation finding").
(def table-external-link-templates
  {"studies" "https://clinicaltrials.gov/study/{{value}}"})

(def schema-out-path "resources/aact/schema.alz.edn")

(defn regenerate-schema
  "Reverse-engineer AACT's Alzabo schema from a live connection and spit it
   to `out-path` (default resources/aact/schema.alz.edn). `dictionary-csv-path`
   defaults to the checked-in documentation_20260805.csv. `db` defaults to
   this app's own \"AACT\" :nlq config entry (must already be loaded, see
   hyperphor.way.config/read-config) -- pass your own to point at a
   different connection/credentials.

   gen-alz-schema has no special case for AACT's own primary key naming
   (studies' is nct_id, not a plain \"id\" column, so it isn't auto-detected
   the way every other table's is) -- set here by hand afterward, same as
   pg-aact's original REPL block did inline."
  [& {:keys [db dictionary-csv-path out-path]
      :or {dictionary-csv-path "resources/aact/documentation_20260805.csv"
           out-path schema-out-path}}]
  (let [db (or db (:db (nlqc/project-named "AACT")))
        dictionary (load-dictionary dictionary-csv-path)
        schema (pg/gen-alz-schema db tables dictionary table-docs table-icons
                                  table-labels table-external-link-templates)
        schema (assoc schema :title "AACT (ClinicalTrials.gov) schema, generated subset")
        schema (assoc-in schema [:kinds :studies :unique-id] :studies/nct_id)]
    (spit out-path (with-out-str (pprint/pprint schema)))
    schema))

(defn generate-schema-doc
  "Generate the Alzabo HTML schema doc for the AACT project --
   resources/public/AACT/schema/index.html, what the frontend's :schema
   tab's iframe points at (see hyperphor.nlq-aact.frontend.core/schema).
   Reads the already-generated resources/aact/schema.alz.edn (no DB access,
   no network) via hyperphor.nlq.schema/init, same mechanism okc uses for
   its own per-project schema tabs. Safe to call on every boot (fast, local-
   file-only); called from core.clj's -main, non-fatally -- a failure here
   shouldn't take down the whole app, just leave the schema tab broken
   until fixed (same caution as okc's own commented-out startup call)."
  []
  (schema/init [(nlqc/project-named "AACT")]))

(comment
  ;; Dev-time invocation. Requires config.edn loaded (AACT_USER/AACT_PASSWORD
  ;; env vars set) and network access to aact-db.ctti-clinicaltrials.org.
  (require '[hyperphor.way.config :as config])
  (config/read-config "config.edn")
  (regenerate-schema))
