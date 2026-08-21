(ns hyperphor.nlq-aact.frontend.core
  "The app shell: three tabs (about / NL query / schema browser), modeled
   directly on okc's own frontend.core -- see that ns for the fuller
   version this is a minimal slice of (no header/modal/flash/login here,
   no multi-project selector since this app has exactly one project)."
  (:require [hyperphor.way.tabs :as tabs]
            [hyperphor.way.ui.init :as init]
            [hyperphor.nlq.frontend.sql-query :as sql-query]))

;; hyperphor.com's own wordmark gif -- no local logo asset exists, and this
;; is what the app-hosting linked to instead (see design/TODO.md). Hotlinked
;; rather than vendored, same as any other external link to hyperphor.com.
(def hyperphor-logo-url "https://hyperphor.com/hyperphor2.gif")

;; Gradient lifted from hyperphor.com itself (see .site-hero in
;; nlq-aact.css) -- reuses the actual Hyperphor brand rather than inventing
;; a new one for this one small demo app. Site-wide (app-ui), not just the
;; about tab, so it's visible from every tab.
(defn site-header
  []
  [:div.site-hero
   [:img.site-hero-logo {:src hyperphor-logo-url :alt "Hyperphor"}]
   [:h2 "AACT NL Query"]])

(defn about
  []
  [:div.p-3 {:style {:max-width "800px"}}
   [:p "AACT (Aggregate Analysis of ClinicalTrials.gov) is a public, regularly
        refreshed copy of the full ClinicalTrials.gov trial registry, published
        as a queryable Postgres database by the Clinical Trials Transformation
        Initiative. Ask a question in plain English on the "
    [:b "NL_query"] " tab and it's translated to SQL and run live against it."]
   [:p [:a {:href "https://aact.ctti-clinicaltrials.org"} "aact.ctti-clinicaltrials.org"]]
   [:div.about-credits
    [:p [:img.credit-logo {:src hyperphor-logo-url :alt "Hyperphor"}]
     "Built by Mike Travers / " [:a {:href "https://hyperphor.com"} "Hyperphor"]]
    [:p "Powered by " [:a {:href "https://github.com/hyperphor/nlq"} "hyperphor/nlq"]
     ", a natural-language-to-SQL query engine."]
    [:p [:a {:href "https://github.com/hyperphor/nlq-aact"} "Source"] " for this app."]]])

(defn schema
  []
  ;; src must be an absolute path -- tabs-nav routes via accountant (real
  ;; pushState), so a relative src would resolve against the current route
  ;; instead of the site root (see okc's identical comment on its own
  ;; schema tab, the same fix for the same bug).
  [:iframe {:src "/AACT/schema/index.html" :style {:width "100%" :height "100%"}}])

(defn nl-query
  []
  [sql-query/ui "AACT"])

(defn app-ui
  []
  [:div
   [site-header]
   [tabs/tabs-nav
    :tab
    (array-map
     :home about
     :NL_query nl-query
     :schema schema)
    []]])

(defn ^:export init
  []
  (init/init app-ui nil))
