(ns reactocular.core
  (:require [tangle.core :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:gen-class))

(defn interesting-file? [file]
  (let [name (.getName file)]
    (or (.endsWith name ".cjsx")
        (.endsWith name ".coffee")
        (.endsWith name ".jsx")
        (.endsWith name ".js"))))

(defn remove-suffix [name]
  (if (= -1 (.indexOf name "."))
    name
    (remove-suffix (.substring name 0 (.lastIndexOf name ".")))))

(defn capitalize-words [name]
  (str/join (->> (str/split name #"\-")
                (map str/capitalize))))

(defn file-contains-component? [file]
  (let [src (slurp file)
        regular-component (> (.indexOf src "extends React.Component") -1)
        stateless-function (> (.indexOf src " = (props) ->") -1)
        result (or regular-component stateless-function)]
    result))

(defn get-relative-path [root-path file]
  (str/replace (.getAbsolutePath file) (.getAbsolutePath root-path) ""))

(defn get-relative-path-parts [root-path file]
  (-> (get-relative-path root-path file)
      (str/split #"/")))

(defn file->module-name [root-path file]
  (let [parts (get-relative-path-parts root-path file)]
    (->> parts
         (rest)
         (drop-last)
         (vec))))

(defn file->component-name [file]
  (-> (.getName file)
      (remove-suffix)
      (capitalize-words)
      (str/replace "-" "")))

(defn file->component [root file]
  (let [name (file->component-name file)
        module (file->module-name root file)
        full-name (str/join "/" (conj module name))]
    {:module module
     :path (get-relative-path root file)
     :name name
     :full-name full-name
     :file file}))

(defn find-all [components src]
  (doall (filter (fn [component]
                   (let [s (str "<" (:name component) "([ \\n/>]+)")
                         pattern (re-pattern s)]
                     (or (re-find pattern src)
                         (> (.indexOf src (str "{" (:name component) "}")) -1))))
                 components)))

(defn component->edges [components component]
  (let [src (slurp (:file component))]
    (for [referred-component (find-all components src)]
      [(:full-name component) (:full-name referred-component)])))

(defn scan [path]
  (let [root (io/file path)
        files (->> (file-seq root)
                   (filter interesting-file?)
                   (filter file-contains-component?)
                   (sort-by #(.getName %)))]
    (map (partial file->component root) files)))

(def component->id :full-name)

(defn component->descriptor [component]
  (let [page (some #{"page"} (:module component))
        stereotype (if page "&laquo;page&raquo;" "")]
    (if (:label component)
      component
      {:label [:TABLE
               [:TR [:TD {:BGCOLOR (if page "lightgray" "white")} [:B (:name component)] [:BR] stereotype]]
               [:TR [:TD (str "/" (str/join "/" (:module component)))]]]
       :tooltip (:path component)})))

(defn render [components filename]
  (let [nodes components
        edges (mapcat (partial component->edges components) components)]
  (println "Generating graph from" (count nodes) "nodes and" (count edges) "edges")
  (let [dot (graph->dot nodes edges {:node {:shape :none :margin 0}
                                     :graph {:label filename :rankdir :LR}
                                     :directed? true
                                     :node->id component->id
                                     :node->descriptor component->descriptor})]
    (println "Writing DOT" (str filename ".dot"))
    (spit (str filename ".dot") dot)
    (println "Writing SVG" (str filename ".svg"))
    (spit (str filename ".svg") (dot->svg dot))
    )))

(defn -main [& args]
  (if (= (count args) 1)
    (let [components (scan (first args))]
      (render components "components")
      #_(shutdown-agents))
    (println "Usage: reactocular <root-directory>")))
