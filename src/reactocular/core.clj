(ns reactocular.core
  (:require [tangle.core :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:use [medley.core])
  (:gen-class))

(def react-color "#00d8ff")

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
    {:module-id module
     :path (get-relative-path root file)
     :name name
     :full-name full-name
     :file file
     :stateless (file-contains-stateless-functional-component? file name)}))

(defn find-all-uses [components src]
  (doall (filter (fn [component]
                   (let [s (str "<" (:name component) "([ \\n/>]+)")
                         pattern (re-pattern s)
                         provide-context (re-pattern (str "provideContext " (:name component) ","))]
                     (or (re-find pattern src)
                         (re-find provide-context src)
                         (> (.indexOf src (str "{" (:name component) "}")) -1))))
                 components)))

(defn component->references [components component]
  (let [src (slurp (:file component))]
    (find-all-uses components src)))

(defn component->edges [component]
  (for [referred-component (:references component)]
    [(:full-name component) (:full-name referred-component)]))

(defn module-id->full-name [module]
  (str/join "/" module))

(def component->id :full-name)
(def module->id :full-name)

(defn component->module [component]
  (let [id (or (:module-id component) ["root"])
        name (last id)
        full-name (module-id->full-name id)]
    {:module-id id
     :name name
     :full-name full-name}))

(defn index-by [key-fn coll]
  (into {} (map (juxt key-fn identity) coll)))

(defn add-parent-ids [modules]
  (map (fn [module]
         (let [id (:module-id module)
               parent-id (when (> (count id) 1) (vec (drop-last 1 id)))]
           (assoc module :parent-id parent-id)))
       modules))

(defn add-parent-ids [modules]
  (map (fn [module]
         (let [id (:module-id module)
               parent-id (when (> (count id) 1) (vec (drop-last 1 id)))]
           (assoc module :parent-id parent-id)))
       modules))

(defn add-parent-ids [modules]
  (map (fn [module]
         (let [id (:module-id module)
               parent-id (when (> (count id) 1) (vec (drop-last 1 id)))]
           (assoc module :parent-id parent-id)))
       modules))

(defn module-references [components module]
  (->> components
       (mapcat (fn [c] (for [r (:references c)] [(:module-id c) (:module-id r)])))
       (filter (fn [[from to]] (= (:module-id module) from)))
       (distinct)))

(defn expand-module-path [module-id]
  (if (empty? module-id) []
      (conj (expand-module-path (vec (drop-last module-id)))
            module-id)))

(defn create-module-from-module-id [module-id]
  {:module-id module-id
   :full-name (module-id->full-name module-id)
   :name (last module-id)
   :parent-id (drop-last module-id)})

(defn full-parent-tree [modules]
  (let [all-module-ids (distinct (sort (mapcat expand-module-path (map :module-id modules))))
        by-id (index-by :module-id modules)]
    (for [module-id all-module-ids]
      (if-let [m (by-id module-id)]
        m
        (create-module-from-module-id module-id)
        ))))

(defn self-reference? [[f t & opts]]
  (= f t))

(defn add-module-references [components modules]
  (map (fn [m] (assoc m :references (module-references components m)))  modules))

(defn add-module-edges [modules]
  (let [by-id (index-by :module-id modules)]
    (map (fn [m]
           (let [parent (by-id (:parent-id m))
                 rc (count (:references m))]
             (assoc m :edges (remove self-reference? (concat (when parent
                                                               [[(module->id parent) (module->id m) {:penwidth 3 :weight 1}]])
                                                             (map (fn [[from to]] [(module-id->full-name from) (module-id->full-name to) {:label rc :style :solid :weight 1 :color react-color :penwidth rc}]) (:references m)))))))
         modules)))

(defn gather-modules [components]
  (let [modules (distinct-by :full-name (map component->module components))
        modules (add-parent-ids modules)
        modules (full-parent-tree modules)
        modules (add-module-references components modules)
        modules (add-module-edges modules)]
    modules))

(defn scan [path]
  (let [root (io/file path)
        files (->> (file-seq root)
                   (filter interesting-file?)
                   (filter file-contains-component?)
                   (sort-by #(.getName %)))
        components (doall (map (partial file->component root) files))
        components (doall (map (fn [component]
                                 (let [references (component->references components component)]
                                   (assoc component :references references)))
                               components))
        components (doall (map (fn [component]
                                 (let [edges (component->edges component)]
                                   (assoc component
                                          :edges edges
                                          :elementary (= (count edges) 0))))
                               components))]
    components))

(defn set-flags [& flags]
  (into [] (remove nil? (for [[t fk] (partition 2 flags)] (when t fk)))))

(defn component->descriptor [component]
  (let [page (some #{"page"} (:module-id component))
        stereotypes (set-flags page :page
                               (:stateless component) :stateless
                               (:elementary component) :elementary)
        stereotypes (apply concat (for [stereotype stereotypes]
                                    [[:BR] (str "&laquo;" (name stereotype) "&raquo;")]))]
    (if (:label component)
      component
      {:label [:TABLE {:CELLSPACING 0}
               [:TR (into [:TD {:BGCOLOR (cond page "gray"
                                               (:stateless component) "lightgray"
                                               (:elementary component) react-color
                                               :else "white")}
                     [:FONT {:COLOR (cond page "black"
                                          :else "black")}
                      [:B (:name component)]]]
                          stereotypes)]
               [:TR [:TD (str "/" (str/join "/" (:module-id component)))]]]
       :tooltip (:path component)})))

(defn module->descriptor [module]
  {:label [:TABLE {:CELLSPACING 0} [:TR [:TD (:name module)]]]})

(defn render [nodes edges options filename]
  (println "Generating graph from" (count nodes) "nodes and" (count edges) "edges")
  (let [dot (graph->dot nodes edges options)]
    (println "Writing DOT" (str filename ".dot"))
    (spit (str filename ".dot") dot)
    (println "Writing SVG" (str filename ".svg"))
    (spit (str filename ".svg") (dot->svg dot))
    ))

(defn render-components [components filename]
  (let [nodes components
        edges (mapcat :edges components)
        options {:node {:shape :none :margin 0}
                 :graph {:label filename :rankdir :LR}
                 :directed? true
                 :node->id component->id
                 :node->descriptor component->descriptor}]
    (render nodes edges options filename)))

(defn render-modules [modules filename]
  (let [nodes modules
        edges (mapcat :edges modules)
        options {:node {:shape :none :margin 0}
                 :graph {:label filename :rankdir :LR :layout :circo}
                 :directed? true
                 :node->id module->id
                 :node->descriptor module->descriptor}]
    (render nodes edges options filename)))

(defn -main [& args]
  (if (= (count args) 1)
    (let [components (scan (first args))
          modules (gather-modules components)]
      (render-components components "components")
      (render-modules modules "modules")
      #_(shutdown-agents))
    (println "Usage: reactocular <root-directory>")))
