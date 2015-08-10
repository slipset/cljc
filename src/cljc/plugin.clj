(ns cljc.plugin
  (:require [robert.hooke :as hooke]
            leiningen.repl
            [clojure.java.io :as io]))

(def cljc-coordinates
  (-> (io/resource "META-INF/leiningen/net.assum/cljc/project.clj")
      slurp
      read-string
      ((fn [[_ artifact version]] [artifact version]))))

(assert (and (symbol? (first cljc-coordinates))
          (string? (second cljc-coordinates)))
  (str "Something went wrong, cljc coordinates are invalid: "
    cljc-coordinates))

(defn middleware
  [project]
  (if (or (-> project :cljc :disable-repl-integration)
        (not (-> project meta :included-profiles set (contains? :repl))))
    project
    (-> project
      (update-in [:repl-options :nrepl-middleware]
        (fnil into [])
        '[cljc.repl-middleware/wrap-cljc cemerick.piggieback/wrap-cljs-repl])
      (update-in [:dependencies]
        (fnil conj [])
        cljc-coordinates))))
