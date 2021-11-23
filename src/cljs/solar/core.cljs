(ns solar.core
  (:require
    [accountant.core :as accountant]
    [re-frame.core :as rf]
    [reagent.core :as reagent :refer [atom]]
    [secretary.core :as secretary :include-macros true]
    [solar.events :as events]
    [solar.views :as views]))

;; Routes
(defonce page (atom #'views/pagina-toda))

(defn- current-page []
  [@page])

;; Initialize app
(defn- mount-root []
  (when-not @events/iniciou
    (rf/dispatch-sync [::events/initialize])) ;; puts a value into application state
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!
    {:nav-handler
       (fn [path]
         (secretary/dispatch! path))
     :path-exists?
       (fn [path]
         (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))
