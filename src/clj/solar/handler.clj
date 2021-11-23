(ns solar.handler
  (:require
    [compojure.core :refer [GET defroutes]]
    [compojure.route :refer [not-found resources]]
    [config.core :refer [env]]
    [hiccup.page :refer [include-js include-css html5]]
    [solar.middleware :refer [wrap-middleware]]))

(def mount-target
  [:div#app
   [:div.container.justify-content-center
    [:div.card.text-center
     [:div.card-header {:style {:text-align "center"}}
      [:img {:src "logo.svg"}]]
     [:div.container.justify-content-center.p-3
      [:div.card.text-center
       [:div.card-header {:style {:text-align "center"}}
        [:div.spinner-border.text-success {:role "status"}]]]]]]])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1, shrink-to-fit=no"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css")
                "https://stackpath.bootstrapcdn.com/bootstrap/4.4.1/css/bootstrap.min.css"
                "/css/pikaday.css")])

(defn loading-page []
  (html5
    (head)
    [:body {:class "py-4"}
     mount-target
     (include-js
       "https://code.jquery.com/jquery-3.4.1.slim.min.js"
       "https://cdn.jsdelivr.net/npm/popper.js@1.16.0/dist/umd/popper.min.js"
       "https://stackpath.bootstrapcdn.com/bootstrap/4.4.1/js/bootstrap.min.js"
       "/js/app.js")]))

(defroutes routes
  (GET "/" [] (loading-page))
  (GET "/about" [] (loading-page))
  (resources "/")
  (not-found "Not Found"))

(def app (wrap-middleware #'routes))
