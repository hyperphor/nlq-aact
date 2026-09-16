(ns hyperphor.nlq-aact.frontend.core
  "The app shell: four pages (query / visualize / schema browser / about),
  Per design/ui-makeover.md, the home page is the query UI itself
   rather than static about text -- built from hyperphor.nlq.frontend.sql-
   query's public building blocks (query-card / sql-grid-view / inspector-
   pane / viz-card) instead of that ns's own monolithic `ui`, since `ui`
   bundles query+grid+viz into one fixed two-column layout with no layout
   knobs of its own."
  (:require [clojure.string :as str]
             [re-frame.core :as rf]
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

;; Column grouping mirrors sql-query/ag-column-defs logic:
;; - group key = (or :ref-kind :kind) from the columns-info map
;; - unresolved columns (absent from columns-info) form singleton groups keyed by the col kw
;; - group label = icon + capitalized kind name; bare :field name shown inside the group

(defn- col-effective-kind [col columns-info]
  (let [info (get columns-info col)]
    (or (:ref-kind info) (:kind info))))

(defn- id-col? [col info]
  (or (= (:field info) :id)
      (str/ends-with? (name col) "_id")))

(defn- col-groups
  "Returns ordered seq of [group-key [col ...]] pairs.
   Unresolved cols each get their own singleton group keyed by the col kw."
  [all-cols columns-info]
  (let [group-key   (fn [col] (or (col-effective-kind col columns-info) col))
        groups      (group-by group-key all-cols)
        first-seen  (distinct (map group-key all-cols))]
    (for [gk first-seen]
      [gk (->> (get groups gk)
               (sort-by #(if (id-col? % (get columns-info %)) 0 1)))])))

(defn- group-label [gk columns-info members]
  (let [icon (:icon (get columns-info (first members)))]
    (str (when icon (str icon " "))
         (-> (name gk) (str/replace "-" " ") str/capitalize))))

(defn- col-display-name [col columns-info]
  (if-let [field (:field (get columns-info col))]
    (name field)
    (name col)))

(defn viz-data-summary
  "Shows the current query context (NL query, row count, grouped columns).
   Uses a <details> element for native open/close — no JS state needed.
   Collapsed by default; summary line shows row/col counts.
   When there are no results, prompts the user to run a query first."
  []
  (let [{:keys [nl results columns]} @(rf/subscribe [:qbox-response :sql])
        all-cols (some-> results first keys)
        n-rows   (count results)
        n-cols   (count all-cols)]
    [:div.viz-data-summary
     (if (seq results)
       [:details.viz-details {:open true}
        [:summary.viz-summary-header
         [:span.viz-summary-counts (str n-rows " rows · " n-cols " cols")]
         (when nl [:span.viz-summary-nl (str " · " nl)])]
        [:div.viz-summary-detail
         [:table.viz-col-table
          [:thead
           [:tr
            [:th.viz-col-kind "Kind"]
            [:th.viz-col-fields "Fields"]]]
          [:tbody
           (for [[gk members] (col-groups all-cols columns)]
             (let [resolved? (contains? columns (first members))]
               [:tr {:key (name gk)}
                [:td.viz-col-kind (when resolved? (group-label gk columns members))]
                [:td.viz-col-fields
                 (str/join ", " (map #(col-display-name % columns) members))]]))]]]]
       [:div.viz-no-data
        [:span "No data — "]
        [:a {:href "#"
             :on-click (fn [e] (.preventDefault e) (rf/dispatch [:set-route [:home]]))}
         "run a query first"]])]))



(defn visualize
  []
  (let [{:keys [results]}                       @(rf/subscribe [:qbox-response :sql])
        {:keys [viz-spec viz-text error]}        @(rf/subscribe [:qbox-response :sql-vizq])]
    [:div.hstack.istack.m-3.gap-3 {:style {:height "90%"}}
     [:div.vstack {:style {:max-width "600px" :min-width "600px"}}
      [viz-data-summary]
      [sql-query/viz-card project results]
      [cards/cards :sql-vizq-cards
       [(when viz-spec {:name :vega :view (fn [] [qbox/query-editor :sql-vizq project "Vega"])})
        (when viz-text {:name :plan :view (fn [] [:div.m-3 (md/render viz-text)])})
        (when error    {:name :error :open? true
                        :view (fn [] [:div.alert.alert-warning [:pre {:style {:text-wrap "auto"}} error]])})]]]
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
