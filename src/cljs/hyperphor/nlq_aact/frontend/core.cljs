(ns hyperphor.nlq-aact.frontend.core
  "The app shell: four pages (query / visualize / schema browser / about),
  Per design/ui-makeover.md, the home page is the query UI itself
   rather than static about text -- built from hyperphor.nlq.frontend.sql-
   query's public building blocks (query-card / sql-grid-view / inspector-
   pane / viz-card) instead of that ns's own monolithic `ui`, since `ui`
   bundles query+grid+viz into one fixed two-column layout with no layout
   knobs of its own."
  (:require [re-frame.core :as rf]
            [hyperphor.way.tabs :as tabs]
            [hyperphor.way.ui.init :as init]
            [hyperphor.way.cards :as cards]
            [hyperphor.way.markdown :as md]
            [hyperphor.nlq.frontend.qbox :as qbox]
            [hyperphor.nlq.frontend.nlq-viz :as nlqv]
            [hyperphor.nlq.frontend.sql-query :as sql-query]))

;; This app has exactly one project -- see CLAUDE.md.
(def project "AACT")

(def hyperphor-logo-url "https://hyperphor.com/hyperphor2.gif")

;; Gradient lifted from hyperphor.com itself (see .site-hero in
;; nlq-aact.css) -- reuses the actual Hyperphor brand rather than inventing
;; a new one for this one small demo app. Site-wide (app-ui), not just the
;; about page, so it's visible from every page.
(defn site-header
  []
  [:div.site-hero
   [:h2 "AACT NL Query"]
   [:a.site-hero-logo-link {:href "https://hyperphor.com"}
    [:img.site-hero-logo {:src hyperphor-logo-url :alt "Hyperphor"}]]])

(defn about
  []
  [:div.p-3 {:style {:max-width "800px"}}
   [:p "This site provides a natural-language interface to "
    [:a {:href "https://aact.ctti-clinicaltrials.org"} "AACT (Aggregate Analysis of ClinicalTrials.gov"]
    ", a public, regularly
        refreshed Postgres version of the ClinicalTrials.gov trial registry."
    "by the Clinical Trials Transformation Initiative. "
    ]
   [:p [:a {:href "https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0033677"} "The Database for Aggregate Analysis of ClinicalTrials.gov (AACT) and Subsequent Regrouping by Clinical Specialty"],
    ", Asba Tasneem et al, 2012, PLoS One" ]
   [:div.about-credits
    [:p [:img.credit-logo {:src hyperphor-logo-url :alt "Hyperphor"}]
     "Built by Mike Travers / " [:a {:href "https://hyperphor.com"} "Hyperphor"]]
    [:p "Powered by " [:a {:href "https://github.com/hyperphor/nlq"} "hyperphor/nlq"]
     ", a natural-language-to-SQL query engine."]
    [:p [:a {:href "https://github.com/hyperphor/nlq-aact"} "Source"] ]]])

(defn schema
  []
  ;; src must be an absolute path -- tabs-nav routes via accountant (real
  ;; pushState), so a relative src would resolve against the current route
  ;; instead of the site root (see okc's identical comment on its own
  ;; schema tab, the same fix for the same bug).
  [:iframe {:src "/AACT/schema/index.html" :style {:width "100%" :height "100%"}}])

;; ── Home page: query box front and center (design/ui-makeover.md) ─────────

(defn home-intro
  []
  [:p.home-intro
   "A natural language interface to clinical trial information. Based on "
   [:a {:href "clinicaltrials.gov"} "clinicaltrials.gov"] " via "
   [sql-query/source-link project]
   ". Ask a
    research question below.  "
   [:a {:href "#"
        :on-click (fn [e] (.preventDefault e) (rf/dispatch [:set-route [:about]]))}
    "More about this project →"]])

(defn query-section
  "The big, obvious NL query box -- sql-query/query-card wrapped in an
   app-level div (.query-hero in nlq-aact.css) for the bigger-box/bigger-
   font treatment."
  []
  [:div.query-hero
   [sql-query/query-card project]])

(defn results-section
  "Results grid (main) + side-cards (generated SQL, plan, error, inspector),
   below the query box. Same subscription/card set as sql-query/ui, just
   laid out grid-first/wide + cards-second/narrow instead of that ns's own
   cards-first/narrow + grid-second/wide."
  []
  (let [{:keys [results query text error columns]} @(rf/subscribe [:qbox-response :sql])]
    [:div.hstack.istack.gap-3.results-row
     [:div.results-grid
      [sql-query/sql-grid-view project results columns]]
     [:div.results-side
      [cards/cards :sql-cards
       [(when query {:name :sql :view (fn [] [qbox/query-editor :sql project "SQL"])})
        (when text {:name :plan :view (fn [] [:div.m-3 (md/render text)])})
        (when error {:name :error :open? true
                     :view (fn [] [:div.alert.alert-warning [:pre {:style {:text-wrap "auto"}} error]])})
        {:name :inspector :view sql-query/inspector-pane}]]]]))

(defn home
  []
  [:div
   [home-intro]
   [query-section]
   [results-section]])

;; ── Visualize page: split out of sql-query/ui per design/ui-makeover.md ──

(defn visualize
  []
  (let [{:keys [results]} @(rf/subscribe [:qbox-response :sql])]
    [:div.hstack.istack.m-3.gap-3 {:style {:height "90%"}}
     [:div {:style {:max-width "600px" :min-width "600px"}}
      [sql-query/viz-card project results]]
     [:div.vstack {:style {:min-width "800px"}}
      [nlqv/ui results :sql-vizq]]]))

(defn app-ui
  []
  [:div
   [site-header]
   [tabs/tabs-nav
    :tab
    (array-map
     :home home
     :visualize visualize
     :schema schema
     :about about)
    []]])

(defn ^:export init
  []
  (init/init app-ui nil))
