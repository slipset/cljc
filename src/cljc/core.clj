(ns cljc.core
  (:require [net.cgrand.sjacket :as sj]
            [net.cgrand.sjacket.parser :as p]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.zip :as z])
  (:import java.io.File))

(def ^:private warning-str ";;;;;;;;;;;; This file autogenerated from ")

;;Taken from clojure.tools.namespace
(defn- cljc-source-file?
  "Returns true if file is a normal file with a .cljx extension."
  [^File file]
  (and (.isFile file)
       (.endsWith (.getName file) ".cljc")))

(defn- find-cljc-sources-in-dir
  "Searches recursively under dir for CLJC files.
Returns a sequence of File objects, in breadth-first sort order."
  [^File dir]
  ;; Use sort by absolute path to get breadth-first search.
  (sort-by #(.getAbsolutePath ^File %)
           (filter cljc-source-file? (file-seq (io/file dir)))))

(defn- walk
  [zloc {:keys [features transforms] :as rules}]
  (let [zloc (reduce #(%2 %) (rules/apply-features zloc features) transforms)]
    (if-not (z/branch? zloc)
      zloc
      (->> (z/down zloc)
           ; I feel like I've done this kind of zipper walk in a simpler way
           ; before...
           (iterate #(let [loc (walk % rules)]
                       (or (z/right loc) {::last loc})))
           (some ::last)
           z/up))))

(defn transform
  [code rules]
  (if (empty? (str/trim code))
    code
    (-> (p/parser code)
        z/xml-zip
        (walk rules)
        z/root
        sj/str-pt)))

(defn- relativize
  [f root]
  (-> root io/file .toURI (.relativize (-> f io/file .toURI))))

(defn- destination-path
  [source source-path output-dir]
  (.getAbsolutePath (io/file output-dir (str (relativize source source-path)))))

(defn generate
  ([options]
   (generate options (find-cljc-sources-in-dir (:source-path options))))
  ([{:keys [source-path output-path rules] :as options} files]
   (println "Rewriting" source-path "to" output-path
            (str "(" (:filetype rules) ")")
            "with features" (:features rules) "and"
            (count (:transforms rules)) "transformations.")
   (doseq [f files 
           :let [result (transform (slurp f) rules)
                 destination (str/replace (destination-path f source-path output-path)
                                          #"\.[^\.]+$" (str "." (:filetype rules)))]]
     (doto destination
       io/make-parents
       (spit (with-out-str
               (println result)
               (print warning-str)
               (println (.getPath f))))))))

(defn cljc-compile 
  ([builds & {:keys [files]}]
   "The actual static transform, separated out so it can be called repeatedly."
   (doseq [{:keys [source-paths output-path rules] :as build} builds]
     (let [rules (cond
                   (= :clj rules) rules/clj-rules
                   (= :cljs rules) rules/cljs-rules
                   (symbol? rules) (do
                                     (require (symbol (namespace rules)))
                                     @(resolve rules))
                   :default (eval rules))]
       (doseq [p source-paths
               :let [abs-path (.getAbsolutePath (io/file p))] ]
         (if files
           (when-let [files (->> files
                                 (filter #(.startsWith (.getAbsolutePath (io/file %)) abs-path))
                                 seq)]
             (generate (assoc build :rules rules :source-path p) files)) 
           (generate (assoc build :rules rules :source-path p))))))))
