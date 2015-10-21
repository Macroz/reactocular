# reactocular [![Build Status](https://travis-ci.org/Macroz/reactocular.svg?branch=master)](https://travis-ci.org/Macroz/reactocular)

/ˈrɪˈaktɒkjʊlə/<br>
_noun_

1. of or connected to React and vision.<br>
_"project anxiety caused by reactocular trauma"_<br>

_adjective_

1. visual, brutish, inelegant<br>

Visual brutish component scan of React projects. reactocular visualizes your React component hierarcy using [tangle](https://github.com/Macroz/tangle) and [Graphviz](http://www.graphviz.org/)

1 Minute Version
----------------

You can use reactocular from command-line if you have [Graphviz](http://www.graphviz.org) and [Leiningen](http://leiningen.org) like this:

```
lein run ../hsl/digitransit-ui/app
```

There is also a convenient [uberjar](uberjar/reactocular.jar?raw=true) available to download.
You can use it like a regular Java app like this:

```
java -jar reactocular.jar ../hsl/digitransit-ui/app
```

The result is a set of files with various visualiations of your components. You can see that there is still work to do, in this project as well as the example :)

Component diagram with references between components. Shows also which components are &laquo;elementary&raquo; building blocks (blue), as well as &laquo;stateless&raquo; functional components (gray). A dark gray signifies &laquo;page&raquo;.

![Example components.svg](https://rawgit.com/Macroz/reactocular/master/examples/digitransit-components.svg) (from [Digitransit](http://digitransit.fi))

Module diagram with folder structure (solid black) and component references (solid blue)

![Example modules.svg](https://rawgit.com/Macroz/reactocular/master/examples/digitransit-modules.svg) (from [Digitransit](http://digitransit.fi))

Also if you want to use the code from Clojure, add to your project.clj:

[![Clojars Project](http://clojars.org/macroz/reactocular/latest-version.svg)](http://clojars.org/macroz/reactocular)

## Backlog

- Stylize the graph
- Add overall statistics
- Add simple list view
- Check against other projects and improve

=======
## License

Copyright © 2015 Markku Rontu

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
