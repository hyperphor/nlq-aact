(ns hyperphor.nlq-aact.handler
  (:require [compojure.core :refer [defroutes context GET]]
            [hyperphor.way.handler :as wh]
            [hyperphor.nlq.generate :as nlq]
            ;; Registers sql/query|project-tables|qualify-table-name :postgres
            ;; (side-effect-only require, same pattern okc uses for
            ;; hyperphor.nlq.sources.cirro) -- without this the :postgres
            ;; provider dispatch in hyperphor.nlq.sources.sql has no method
            ;; to fall through to.
            hyperphor.nlq.sources.postgres
            ;; Registers the :sql-inspect wd/data method the object
            ;; inspector's /api/data route (way's generic base-api-routes)
            ;; needs -- same require-for-side-effect pattern.
            hyperphor.nlq.inspect))

;;; This app is scoped to exactly one project/query-type -- no :qgen/
;;; :sparql/:vizq branches like okc's qbox-endpoint, since there's only ever
;;; one thing to ask for here.
(defn aact-query
  [nl]
  (nlq/endpoint "AACT" :sql nl))

(defroutes site-routes)

(defroutes api-routes
  (context "/api" []
    (GET "/query" [query] (wh/content-response (aact-query query)))))

;;; Warning: do not `(def app ...)` -- config isn't necessarily loaded yet at
;;; compile time (same caveat as okc/nlq-demo's handler.clj).
(defn app
  []
  (wh/app site-routes api-routes))
