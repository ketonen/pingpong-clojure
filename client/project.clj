(defproject pingpong-clojure "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.9.1"

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.597"
                  :exclusions [com.google.javascript/closure-compiler-unshaded
                               org.clojure/google-closure-library
                               org.clojure/google-closure-library-third-party]]
                 [org.clojure/core.async  "0.4.500"]
                 [cljs-ajax "0.7.5"]
                 [cljs-http "0.1.46"]
                 [reagent "0.9.0-rc3"]
                 [binaryage/devtools "0.9.10"]
                 [day8.re-frame/http-fx "v0.2.0"]
                 [re-frame "0.11.0-rc3"]
                 [thheller/shadow-cljs "2.8.76"]]

  :plugins [[lein-shadow "0.1.7"]
            [lein-kibit "0.1.8"]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} [:target-path
                                    "shadow-cljs.edn"
                                    "package.json"
                                    "package-lock.json"
                                    "resources/public/js"]

  :shadow-cljs {:nrepl {:port 8777}
                :builds {:client {:target :browser
                                  :output-dir "resources/public/js"
                                  :modules {:client {:init-fn pingpong-clojure.core/main}}
                                  :devtools {:http-root "resources/public"
                                             :http-port 8280}}}}

  :aliases {"dev-auto" ["shadow" "watch" "client"]})