(ns leiningen.cljc
  (:require cljc.plugin
            cljc.core
            [watchtower.core :as wt]))

(def no-opts-warning "You need a :cljc entry in your project.clj! See the cljc docs for more info.")

(defn- once
  "Transform .cljc files once and then exit."
  [project builds]
  (cljc.core/cljc-compile builds))

(defn- auto
  "Watch .cljc files and transform them after any changes."
  [project builds]
  (let [dirs (set (flatten (map :source-paths builds)))]
    (println "Watching" (vec dirs) "for changes.")
    (-> (wt/watcher* dirs)
        (wt/file-filter (wt/extensions :cljc))
        (wt/rate 250)
        (wt/on-change (fn [files] 
                        (cljc.core/cljc-compile builds :files files)))
        (wt/watch))))

(defn cljc
  "Statically transform .cljc files into Clojure and ClojureScript sources."
  {:subtasks [#'once #'auto]}
  ([project] (cljc project "once"))
  ([project subtask]
   (if-let [opts (:cljc project)]
     (if-let [{builds :builds} opts]
       (case subtask
         "once" (once project builds)
         "auto" (auto project builds)))
     (println no-opts-warning))))
