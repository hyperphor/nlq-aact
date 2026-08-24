(ns hyperphor.nlq-aact.handler
  (:require [compojure.core :refer [defroutes context GET]]
            [hyperphor.way.handler :as wh]
            [hyperphor.nlq.generate :as nlq]
            hyperphor.nlq.sources.postgres
            [hyperphor.nlq.visgen :as visgen]
            hyperphor.nlq.inspect))

;;; hyperphor.nlq.frontend.qbox's :qbox-query event -- what sql-query.cljs's
;;; whole UI (via qbox/ui) actually calls -- always hits /api/qbox/query
;;; with :id/:project/:query params, same shape as okc's real qbox-endpoint
;;; (handler.clj there). This app is scoped to exactly one project/query-
;;; type, so `project` is accepted but ignored (always "AACT") and only
;;; :sql is wired -- no :qgen/:sparql like okc's, and no :sql-vizq either
;;; (sql-query.cljs's Visualize card still renders and can still be
;;; clicked, since that ns isn't forked here, but gets a clean "unsupported"
;;; response rather than a route match -- see design/pg-aact-split-plan.md's
;;; Destination 2 scoping; wiring hyperphor.nlq.visgen/viz-endpoint here
;;; later is a small, self-contained follow-on if wanted).
(defn qbox-endpoint
  [id query _project]
  (case (keyword id)
    :sql              (nlq/endpoint "AACT" :sql query)
    (:vizq :sql-vizq) (visgen/viz-endpoint "AAcT" query)
    {:error (str "Unsupported query type for this app: " id)}))

(defroutes site-routes)

(defroutes api-routes
  (context "/api" []
    (context "/qbox" []
      (GET "/query" [id query project] (wh/content-response (qbox-endpoint id query project))))))

;;; Warning: do not `(def app ...)` -- config isn't necessarily loaded yet at
;;; compile time (same caveat as okc/nlq-demo's handler.clj).
(defn app
  []
  (wh/app site-routes api-routes))
