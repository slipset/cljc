(defproject net.assum/cljx "0.0.1"
  :description "Static Clojure code rewriting"
  :url "http://github.com/slipset/cljc"
  :license {:name "BSD"
            :url "http://www.opensource.org/licenses/BSD-3-Clause"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.match "0.2.0"]
                 [net.cgrand/sjacket "0.1.1" :exclusions [org.clojure/clojure]]
                 [com.cemerick/piggieback "0.1.5"]
                 [watchtower "0.1.1"]]

  :cljx {:builds [{:source-paths ["test"]
                   :output-path "target/test-output"
                   :rules :clj}
                  {:source-paths ["test"]
                   :output-path "target/test-output"
                   :rules :cljs}]}

  :profiles {
             ; self-reference and chained `lein install; lein cleantest` invocation
             ; needed to use the project as its own plugin. Leiningen :-(
             :self-plugin [:default {:plugins [[net.assum/cljc "0.0.1"]
                                               [com.cemerick/clojurescript.test "0.3.1"]]}]}

  :aliases {"cleantest" ["with-profile" "self-plugin" "do" "clean," "cljc" "once," "test"]}

  :eval-in :leiningen)
