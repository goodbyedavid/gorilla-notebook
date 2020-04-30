PINKIE

the pinkie and the brain  [User=Brain Pinkie=visualisation worker]

You work in your preferred ide, and can visualize output from the repl to a webbrowser.
All renderers written for pink-gorilla notebook are available.


## Render Plugin to your project
- Add pinkgorilla-notebook to your project.clj
- [pinkie.app]

## Dev mode

```
1.
lein build-tailwind-dev
lein build-shadow-pinkie

2.
lein run-pinkie
or
in vs code -> Jack in-> profile pinkie
in src/pinkie/pinkie/app.clj
    a) load all deps
    b) evaluate the expressions in the comment in the bottom

```

## Todo
- embed cljs bundle in jar file 
- custom renderer implementation:
  that is based on different package.json / clj deps.
  uses shadow-cljs to build it.
- can we send the code back to the IDE ??
  So the IDE could change the expression that was evaluated.
  does this make sense? 
- add notebook explorer to support multiple namespaces
- markdown cells are missing, deleting cells missing
- show namespace


Recommendation summary
- Choose Ring-Jetty for small side projects.
-  Choose Ring-Jetty, Pedestal/Jetty, or Yada/Aleph for production systems depending on the application framework you choose.

Required features
Do you need WebSocket support? 
What about Server-Sent Events, or HTTP/2 support? 
Do you need an asynchronous request model? 
These types of features may or may not be important to your application. Many successful applications have been written without them, so it’s likely that yours won’t need them, either.


The biggest downside is that Ring-Jetty does not have convenience wrappers for WebSockets, 
Server-Sent Events, or 
HTTP/2. 
If you would like those, an alternative with a compatible interface is ring-jetty9-adapter.