(ns cljc.repl-middleware
  (:require [cljc.core :as cljc]
            [cljc.rules :as rules]
            [cemerick.piggieback :as piggieback]
            cljs.closure
            [clojure.java.io :as io]
            [clojure.tools.nrepl.middleware :refer (set-descriptor!)]))

(defn- find-resource
  [name]
  (if-let [cl (clojure.lang.RT/baseLoader)]
    (.getResource cl name)
    (ClassLoader/getSystemResourceAsStream name)))

; maybe eventually allow different rules to be used by the cljc-load
; monkey-patch on a per-nREPL-session basis? Crazy.
(def ^:private cljc-load-rules (atom rules/clj-rules))

; clojure.core/load from ~Clojure 1.6.0
; clojure.core/load hasn't really changed since ~2009, so monkey patching here
; seems entirely reasonable/safe.
(defn- cljc-load
  "Loads Clojure code from resources in classpath. A path is interpreted as
classpath-relative if it begins with a slash or relative to the root
directory for the current namespace otherwise."
  {:added "1.0"}
  [& paths]
  (doseq [^String path paths]
    (let [^String path (if (.startsWith path "/")
                          path
                          (str (#'clojure.core/root-directory (ns-name *ns*)) \/ path))]
      (when @#'clojure.core/*loading-verbosely*
        (printf "(clojure.core/load \"%s\")\n" path)
        (flush))
      (#'clojure.core/check-cyclic-dependency path)
      (when-not (= path (first @#'clojure.core/*pending-paths*))
        (with-bindings {#'clojure.core/*pending-paths* (conj @#'clojure.core/*pending-paths* path)}
          (let [base-resource-path (.substring path 1)
                cljc-path (str base-resource-path ".cljc")]
            (if-let [cljc (find-resource cljc-path)]
              (do
                (when @#'clojure.core/*loading-verbosely*
                  (printf "Transforming cljc => clj from %s.cljc\n" base-resource-path))
                (-> (slurp cljc)
                    (cljc/transform (:clj @cljc-load-rules))
                    java.io.StringReader.
                    (clojure.lang.Compiler/load base-resource-path
                                                (last (re-find #"([^/]+$)" cljc-path)))))
              (clojure.lang.RT/load base-resource-path))))))))

(def ^:private clojure-load load)
(def ^:private clojure-resource io/resource)

(defn cljc-cljs-resource
  [& [^String resource-name :as resource-args]]
  (or (apply clojure-resource resource-args)
    (when-let [cljc (and (.endsWith resource-name ".cljs")
                      (apply clojure-resource (cons (.replaceAll resource-name ".cljs$" ".cljc")
                                                (rest resource-args))))]
      (let [tmp-cljs (java.io.File/createTempFile "cljctransform" ".cljs")]
        (.deleteOnExit tmp-cljs)
        (as-> (slurp cljc) %
          (cljc/transform % (:cljs @cljc-load-rules))
          (spit tmp-cljs %))
        (.toURL tmp-cljs)))))


(def ^:private install-cljc-load
  (delay (alter-var-root #'load (constantly cljc-load))
    ; I originally thought that this was a hack, and that replacing
    ; `cljs.closure/cljs-source-for-namespace` was going to be a much cleaner
    ; solution, but that puts the reference to a temp file within view of the
    ; CLJS compiler, as *cljs-file*; this makes compiler warnings _useless_.
    (alter-var-root #'cljs.closure/cljs-dependencies
      (fn [cljs-dependencies]
        (fn [& args]
          (with-redefs [io/resource cljc-cljs-resource]
            (apply cljs-dependencies args)))))))

(defn wrap-cljc
  ([h] (wrap-cljc h {:clj rules/clj-rules :cljs rules/cljs-rules}))
  ([h {:keys [clj cljs] :as rules}]
     ; essentially changing the rules each time a new nREPL endpoint is set up...
     ; generally will only ever happen once per JVM process
     (reset! cljc-load-rules rules)
     @install-cljc-load 
     (fn [{:keys [op code file file-name session] :as msg}]
       (let [rules (if (@session #'piggieback/*cljs-repl-env*)
                     cljs
                     clj)]
         (cond
          (and (= op "eval") code)
          (h (assoc msg :code (cljc/transform code rules)))
          
          (and (= op "load-file") file (re-matches #".+\.cljc$" file-name))
          (h (assoc msg :file (cljc/transform file rules)))
          
          :else (h msg))))))

(set-descriptor! #'wrap-cljc
  {:requires #{"clone"}
   :expects #{#'piggieback/wrap-cljs-repl}
   :handles {}})
