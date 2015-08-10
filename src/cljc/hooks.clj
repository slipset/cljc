(ns cljc.hooks
  (:require [leiningen.core.main :as lmain]))

(defn activate []
  (lmain/warn "cljc no longer provides Leiningen hooks; please use :prep-tasks in your project.clj instead, e.g.:")
  (lmain/warn "    :prep-tasks [[\"cljx\" \"once\"] \"javac\" \"compile\"]"))
