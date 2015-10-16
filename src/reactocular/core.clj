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
        extends-component (> (.indexOf src "extends React.Component") -1)
        create-class-component (> (.indexOf src "React.createClass") -1)
        stateless-function (> (.indexOf src " = (props) ->") -1)
        result (or extends-component create-class-component stateless-function)]
    result))

(defn file-contains-stateless-functional-component? [file name]
  (> (.indexOf (slurp file) (str name " = (props) ->")) -1))

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
     :file file
     :stateless (file-contains-stateless-functional-component? file name)}))

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
                   (sort-by #(.getName %)))
        components (map (partial file->component root) files)
        components (map (fn [component]
                          (let [edges (component->edges components component)]
                            (assoc component
                                   :edges edges
                                   :elementary (= (count edges) 0))))
                        components)]
    components))

(def component->id :full-name)

(defn set-flags [& flags]
  (into [] (remove nil? (for [[t fk] (partition 2 flags)] (when t fk)))))

(defn component->descriptor [component]
  (let [page (some #{"page"} (:module component))
        stereotypes (set-flags page :page
                               (:stateless component) :stateless
                               (:elementary component) :elementary)
        stereotypes (apply concat (for [stereotype stereotypes]
                                    [[:BR] (str "&laquo;" (name stereotype) "&raquo;")]))]
    (if (:label component)
      component
      {:label [:TABLE {:CELLSPACING 0}
               [:TR (into [:TD {:BGCOLOR (cond page "gray"
                                               (:elementary component) "lightgray"
                                               (:stateless component) "lightgray"
                                               :else "white")}
                     [:FONT {:COLOR (cond page "black"
                                          :else "black")}
                      [:B (:name component)]]]
                          stereotypes)]
               [:TR [:TD (str "/" (str/join "/" (:module component)))]]]
       :tooltip (:path component)})))

(defn render [components filename]
  (let [nodes components
        edges (mapcat :edges components)]
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
      (shutdown-agents))
    (println "Usage: reactocular <root-directory>")))
