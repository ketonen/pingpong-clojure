(defproject server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :plugins [[lein-ring "0.12.5"]]
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [http-kit "2.3.0"]
                 [silasdavis/at-at "1.2.0"]
                 [org.clojure/data.json "0.2.6"]
                 [compojure "1.6.1"]
                 [ring/ring-devel "1.8.0"]
                 [ring/ring-core "1.8.0"]
                 [clj-time "0.15.2"]
                 [criterium "0.4.5"]
                 [cheshire "5.9.0"]]
  :main server.core
  :target-path "target/%s"
  :ring {:handler server.core/app}
  :profiles {:dev
             {:dependencies
              [[javax.servlet/servlet-api "2.5"]]}
             :uberjar {:aot :all}})
