(ns ^:figwheel-no-load solar.dev
  (:require
    [devtools.core :as devtools]
    [solar.core :as core]))

(devtools/install!)

(enable-console-print!)

(core/init!)
