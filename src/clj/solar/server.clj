(ns solar.server
  (:require
    [config.core :refer [env]]
    [ring.adapter.jetty :refer [run-jetty]]
    [solar.handler :refer [app]])
  (:gen-class))

(defn -main []
  (let [port (or (env :port) 3448)]
    (run-jetty app {:port port :join? false})))
