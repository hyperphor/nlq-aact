(ns hyperphor.nlq-aact.core
  (:gen-class)
  (:require [hyperphor.way.server :as server]
            [hyperphor.way.config :as config]
            [hyperphor.nlq-aact.handler :as handler]
            [hyperphor.multitool.cljcore :as ju]
            [taoensso.timbre :as log]
            [environ.core :as env]))

(defn -main
  [& args]
  (config/read-config "config.edn")
  (let [port (or (first args) (env/env :port))]
    (log/info "Starting nlq-aact server on port" port)
    (server/start (Integer. port) (handler/app))
    ;; Smart enough to be a no-op on a real server (Heroku etc).
    (ju/open-url (format "http://localhost:%s" port))))
