(defproject time-warp "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure           "1.9.0-alpha15"]
                 [funcyard                      "0.1.1-SNAPSHOT"]
                 [orchestra                     "0.2.0"]
                 [org.clojure/data.priority-map "0.0.7"]]
  :main ^:skip-aot time-warp.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
