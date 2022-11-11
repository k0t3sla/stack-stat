(defproject stack-stat "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [spootnik/aleph-params "0.1.5"]
                 [ring/ring-codec "1.2.0"]
                 [cheshire "5.11.0"]
                 [http-kit "2.5.0"]
                 [org.clojure/core.async "1.6.673"]
                 [aleph "0.5.0"]]
  :main ^:skip-aot stack-stat.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
