(defproject solar "1.0.0"
  :description "Cliente para orcamento solar"
  :url "http://localhost:3449"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [dk.ative/docjure "1.12.0"]
                 [org.clojure/data.csv "0.1.4"]
                 [ring-server "0.5.0"]
                 [re-frame "0.10.5"]
                 [day8.re-frame/http-fx "0.1.6"]
                 [cljsjs/moment "2.24.0-0"]
                 [reagent "0.8.1"]
                 [cljs-pikaday "0.1.4"]
                 [adzerk/env "0.4.0"]
                 [re-com "2.1.0"]
                 [reagent-utils "0.3.1"]
                 [ring "1.6.3"]
                 [ring/ring-defaults "0.3.1"]
                 [compojure "1.6.1"]
                 [hiccup "1.0.5"]
                 [yogthos/config "1.1.1"]
                 [org.clojure/clojurescript "1.10.339"
                  :scope "provided"]
                 [secretary "1.2.3"]
                 [venantius/accountant "0.2.4"
                  :exclusions [org.clojure/tools.reader]]]

  :plugins [[lein-environ "1.1.0"]
            [lein-cljsbuild "1.1.7"]]

  :ring {:handler solar.handler/app
         :uberwar-name "solar.war"}

  :min-lein-version "2.5.0"
  :uberjar-name "solar-frontend.jar"
  :main solar.server
  :clean-targets ^{:protect false}
    [:target-path
     [:cljsbuild :builds :app :compiler :output-dir]
     [:cljsbuild :builds :app :compiler :output-to]]

  :source-paths ["src/clj" "src/cljc"]
  :resource-paths ["resources" "target/cljsbuild"]

  :cljsbuild
    {:builds {:min
                {:source-paths ["src/cljs" "src/cljc" "env/prod/cljs"]
                 :compiler
                   {:output-to "target/cljsbuild/public/js/app.js"
                    :output-dir "target/cljsbuild/public/js"
                    :source-map "target/cljsbuild/public/js/app.js.map"
                    :optimizations :advanced
                    :pretty-print false}}
              :app
                {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
                 :figwheel {:on-jsload "solar.core/mount-root"
                            :open-urls ["http://localhost:3449"]}
                 :compiler
                   {:main "solar.dev"
                    :asset-path "/js/out"
                    :output-to "target/cljsbuild/public/js/app.js"
                    :output-dir "target/cljsbuild/public/js/out"
                    :source-map true
                    :optimizations :none
                    :pretty-print true}}}}

  :figwheel
    {:http-server-root "public"
     :server-port 3449
     :nrepl-port 7002
     :nrepl-middleware [cider.piggieback/wrap-cljs-repl]
     :css-dirs ["resources/public/css"]
     :ring-handler solar.handler/app}

  :profiles {:dev {:repl-options {:init-ns solar.repl
                                  :nrepl.middleware [cider.piggieback/wrap-cljs-repl]}
                   :dependencies [[cider/piggieback "0.4.2"]
                                  [binaryage/devtools "0.9.10"]
                                  [ring/ring-mock "0.3.2"]
                                  [ring/ring-devel "1.6.3"]
                                  [prone "1.5.2"]
                                  [figwheel-sidecar "0.5.19"]
                                  [nrepl "0.4.4"]
                                  [pjstadig/humane-test-output "0.8.3"]]

                   :source-paths ["env/dev/clj"]
                   :plugins [[lein-figwheel "0.5.19"]]

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :env {:dev true}}

             :uberjar {:source-paths ["env/prod/clj"]
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :env {:production true}
                       :aot :all
                       :omit-source true}})
