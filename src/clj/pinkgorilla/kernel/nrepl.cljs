(ns pinkgorilla.kernel.nrepl
  "TODO : Fixme handle breaking websocket connection
"
  (:require-macros
   [cljs.core.async.macros :refer (go go-loop)])
  (:require
   [clojure.string :as str]
   [clojure.spec.alpha :as s]

   [cljs.reader :as rd]
   [cljs.core.async :as async :refer (<! >! put! chan timeout close!)]
   [taoensso.timbre :refer-macros (info warn)]
   [cljs-uuid-utils.core :as uuid]
   [reagent.core :as r]
   [re-frame.core :refer [dispatch]]
   ;; [system.components.sente :refer [new-channel-socket-client]]
   ;; [taoensso.sente :as sente :refer (cb-success?)]
   [chord.client :refer [ws-ch]] ; websockets with core.async
   ;PinkGorilla Notebook
   [pinkgorilla.notifications :refer [add-notification notification]]
   [pinkgorilla.kernel.cljs-helper :refer [send-value]]
   ; bring the specs into scope:
   [pinkgorilla.kernel.nrepl-specs]))


(defonce ws-repl
  (atom {:channel     nil ; created by start-ws-state!
         :session-id  nil ; sent from nrepl on connect, set by receive-msgs!
         :evaluations {}; callbacks of evaluations
         :ciders      {}
         :callback {}}))

(defn- send-message!
  "awb99: TODO: if websocket is nil, this will throw! (or not?).
   This could be the error of some of the notebooks not loading.
   Why dont we keep the core.async channels open all the time. And when we have a
   websocket connection, then the messages get sent. Or we just dump messages that happen
   before the socket gets opened. But in this case we have to LOG message dumping.
   I have seen situations where the first eval does not go through. Might be this issue.

   - generates request id
   - sends message to websocket (so nrepl/cider can process the request)
   - saves the callback function into a lookup map to an atom
   - returns the eval id.
   "
  ([key message callback]
   (let [eval-id (uuid/uuid-string (uuid/make-random-uuid))]
     (send-message! eval-id key message callback)
     eval-id))
  ([eval-id key message callback]
   (let [nrepl-msg  (merge message
                           {:id      eval-id
                            :session (:session-id @ws-repl)})]
     (swap! ws-repl assoc-in [key (keyword eval-id)] callback)  ;; Store eval-id value
     (info "ws sending ws message: " nrepl-msg)
     (put! (:channel @ws-repl) nrepl-msg))))

#_(defn eval!
    [segment-id code]
    (send-message! :evaluations
                   {:op   "eval"
                    :code code}
                   segment-id))

(defn send-cider-message!
  [message callback]
  (send-message! :ciders message callback))

(defn get-completions
  "Query the REPL server for autocompletion suggestions. 
   Relies on the cider-nrepl middleware.
   We call the given callback with the list of symbols once the REPL server replies."
  [symbol ns context callback]
  (send-cider-message! {:op "complete" :symbol symbol :ns ns :context context}
                       (fn [msg]
                         (callback (->> (:completions msg)
                                        (map #(:candidate %)))))))

(defn get-completion-doc
  "Queries the REPL server for docs for the given symbol. 
   Relies on the cider-nrepl middleware.
   Calls back with the documentation text"
  [symbol ns callback]
  (send-cider-message! {:op "complete-doc" :symbol symbol :ns ns}
                       (fn [msg]
                         (callback (:completion-doc msg)))))

(defn resolve-symbol
  "resolve a symbol to get its namespace takes the symbol and the namespace that should be used as context.
  Relies on the cider-nrepl middleware. Calls back with the symbol and the symbol's namespace"
  [symbol ns callback]
  (send-cider-message! {:op "info" :symbol symbol :ns ns}
                       (fn [msg]
                         (callback {:symbol (:name msg)
                                    :ns (:ns msg)}))))

(defn- parse-value
  "converts (:value message)
   nrepl has to serialize the value before it hits clojure.
   So this value gets formatted with edn.
   Since the message is packaged as edn too, we have edn within edn.
   "
  [value]
  (try
    (let [data (cljs.reader/read-string value)
          data2 (cljs.reader/read-string data)]
    ;(info "value: " value "type " (type value))
    ;(info "converted: " data "type " (type data))
    ; (info "converted2: " data2 "type " (type data2))
    ;(info "converted value-response" (:value-response data2))
      data2)
    (catch js/Error e (info "parse-value ex: " e " tried to parse: " value))))

(defn make-cider-stacktrace-request
  "The logic here is a little complicated as cider-nrepl will send the stacktrace information back to
   us in installments. So what we do is we register a handler for cider replies that accumulates the
   information into a single data structure, and when cider-nrepl sends us a done message, indicating
   it has finished sending stacktrace information, we fire an event which will cause the worksheet to
   render the stacktrace data in the appropriate place."
  [err segment-id]
  (let [error (atom {:error-text err
                     :segment-id segment-id})] ; awb99: does this preserve state ???
    (send-cider-message!
     {:op "stacktrace"}
     (fn [msg] ; CALLBACK THAT PROCESS CIDER MESSAGES
       (let [status (:status msg)]
         (info "err status: " status)
         (if (contains? status :done)
           (dispatch [:evaluator:error-response @error])
           (swap! error
                  (fn [err ex]
                    (if (:exception err)
                      (assoc-in err [:exception :cause] (:exception ex))
                      (merge ex err)))
                  {:exception msg})))))))


#_(defn process-msg-notebook-segment
    [{:keys [id out err root-ex ns value status] :as message}
     segment-id]
    (cond
      ns ;; value response
      (let [data (parse-value value)
              ;value-response (:value-response data)
              ;_ (info "ws value response: " value-response)
            ]
        (send-value segment-id data ns))

      out ;; console string
      (dispatch [:evaluator:console-response segment-id {:console-response out}])

      err ;; eval error
      (make-cider-stacktrace-request err segment-id)

      root-ex ;; root exception ?? what is this ?? where does it come from ? cider? nrepl?
      (info "Got root-ex" root-ex "for" segment-id)

      (contains? status :done) ;; eval status
      (do
        (swap! ws-repl dissoc [:evaluations id])
        (dispatch [:evaluator:done-response segment-id]))

      :else
      (info "rcvd unhandled notebook segment message: " message)) ;; end of messages that have segment-id
    )


(defn- process-msg
  "processes an incoming message from websocket that comes from nrepl (and has cider enhancements)
   dispatches events to reagent to update notebook state ui."
  [message]
  (let [; this logging needs to be off when working with notebooks (slows them down)
        ; _ (info "ws rcvd message: " (pr-str message))
        {:keys [id status]} message
        eval-id (keyword id)
        ;segment-id (get-in @ws-repl [:evaluations eval-id]) ; awb99: should clj evals be separate or not?
        cider-cb (get-in @ws-repl [:ciders eval-id])
        callback (get-in @ws-repl [:callback eval-id])]
    (info "ws rcvd message eval-id " id)
    (cond

      ;segment-id ; notebook eval results
     ; (process-msg-notebook-segment message segment-id)
      callback
      (callback message)

      cider-cb ; (cider does completions and docstring)
      (do
        (when (s/valid? :nrepl-msg/stacktrace-msg message)
          (info "rcvd valid stacktrace: " message))
        (cider-cb message)
        (when (contains? status :done)
          (swap! ws-repl dissoc [:ciders id])))

      :else
      (info "rcvd neither segment nor cider: " message))))

(defn set-clj-kernel-status [connected session-id]
  (dispatch [:kernel-clj-status-set connected session-id]))


(defn- receive-msgs!
  [ws-chan msg-chan]
  (go
    (let [{:keys [message error]} (<! ws-chan)
          fail-fn (fn [error]
                    (close! ws-chan)
                    (close! msg-chan)
                    (add-notification (notification :danger (str "clj-kernel Fatal Error: " error)))
                    (set-clj-kernel-status false nil))]
      (if message
        (do
          (info "Got initial message " message)
          (if-let [new-session (:new-session message)]
            (do
              (swap! ws-repl assoc :session-id new-session)
              (set-clj-kernel-status true new-session)
              (dispatch [:set-clj-secrets]))
            (warn "could not extract session id!!! "))
          (go-loop []
            (let [{:keys [message error]} (<! ws-chan)]
              (if message
                (do
                  (process-msg message)
                  (recur))
                (fail-fn error)))))
        (fail-fn error)))))

#_(defn- send-msgs!
    "Read messages and send them to server until the channel breaks"
    [msg-chan ws-chan]
    (go-loop []
      (when-let [msg (<! msg-chan)]
        (>! ws-chan msg)
        (recur))))

#_(defn init!
    [ws-url]
    (info "clj kernel starting at" ws-url)
    (go
      (let [{:keys [ws-channel error]} (<! (ws-ch ws-url {:format :json}))]
        (if error
          (add-notification (notification :danger (str "clj-kernel " error)))
          (let [new-msg-ch (doto (chan)
                             (send-msgs! ws-channel))]
            (swap! ws-repl assoc :channel new-msg-ch)
            (receive-msgs! ws-channel nil)
            (>! new-msg-ch #js {:op "clone"}))))))
#_(defn- init-repl!
    [ws-url]
    (let [feed (<! (ws-ch ws-url {:format :json}))]
      feed))

(defn start-repl! [ws-url]
  (go-loop [{:keys [ws-channel error]} (<! (ws-ch ws-url {:format :edn}))
            new-session true]
    (if-not error
      (let [msg-ch (chan)]
        (swap! ws-repl assoc :channel msg-ch)
        (when new-session
          (go (>! msg-ch {:op "clone"})))
        (receive-msgs! ws-channel msg-ch)
        (loop []
          (when-let [msg (<! msg-ch)]
            (>! ws-channel msg)
            (recur)))
        (<! (timeout 3000))
        (recur (<! (ws-ch ws-url {:format :edn}))
               false))
      (let [session-id (:session-id @ws-repl)]
        (add-notification (notification :danger (str "clj-kernel error: " error " - trying to recover with session " session-id)))
        (<! (timeout 3000))
        (recur (<! (ws-ch ws-url {:format :edn}))
               (nil? session-id))))))


;; nrepl evals

(defn on-nrepl-eval-response
  "nepl response parser processes one or moe messages for each evaluation.
   It is used to evaluate notebook segments, 
   and to do cystom evaluations"
  [handle eval-id {:keys [out err root-ex ns value status] :as message}]
  (cond
    ns ;; value response
    (let [data (parse-value value)]
      (handle :value {:data data :ns ns}))

    out ;; console string
    (handle :console out)

    err ;; eval error
    (handle :error err)

    root-ex ;; root exception ?? what is this ?? where does it come from ? cider? nrepl?
    (info "Got root-ex" root-ex "for" eval-id)

    (contains? status :done) ;; eval status
    (do
      (swap! ws-repl dissoc [:callback eval-id])
      (handle :done nil))

    :else
    (info "rcvd unhandled notebook segment message: " message) ;; end of messages that have segment-id
    ))

;; eval - notebook segment

(defn segment-eval-handler [segment-id type data]
  (case type
    :value (send-value segment-id (:data data) (:ns data))
    :console (dispatch [:evaluator:console-response segment-id {:console-response data}])
    :error (make-cider-stacktrace-request data segment-id)
    :done (dispatch [:evaluator:done-response segment-id])))

(defn eval!
  "evaluates a notebook segment"
  [segment-id code]
  (let [eval-id (uuid/uuid-string (uuid/make-random-uuid))
        handler (partial segment-eval-handler segment-id)
        on-response (partial on-nrepl-eval-response handler eval-id)
        _ (send-message! :callback {:op "eval" :code code} on-response)]))

;; eval - clj

(defn clj-eval-handler [context type data]
  (let [{:keys [result channel]} context]
    (case type
      :value (swap! result assoc :value (conj (:value data) data))
      :console (swap! result assoc :console (str (:console @result data) data))
      :error (swap! result assoc :error data)
      :done (do
              (info "clj-eval finished!")
              (if-not (nil? data)
                (put! channel data))))))

(defn ^:export clj-eval!
  "evaluates a clj-snippet"
  [code]
  (let [eval-id (uuid/uuid-string (uuid/make-random-uuid))
        atom-result (reagent.core/atom {:value []
                                        :console ""
                                        :error nil})
        result-chan (chan)
        context {:channel result-chan :result atom-result}
        handler (partial clj-eval-handler context)
        on-response (partial on-nrepl-eval-response handler eval-id)
        _ (send-message! :callback {:op "eval" :code code} on-response)]
    context))


(defn ^:export clj-eval-sync
  "executes a clojure expression
   and returns the result to the ```result-atom```"
  [snippet]
  (let [context (clj-eval! snippet)
        {:keys [channel result]} context]
    (go (<! channel)
        @result)))

(defn ^:export clj-eval-cb
  "executes a clojure expression
   and returns the result to the ```result-atom```
   
   execute this in browser console:
   pinkgorilla.kernel.nrepl.clj_eval_cb (\"(+ 7 9 )\", 
    (function (r) {console.log (\"result!!: \" +r);}))
   "
  [snippet cb]
  (let [context (clj-eval! snippet)
        {:keys [channel result]} context]
    (go (<! channel)
        (cb @result))))


#_(defn ^:export clj-eval
  ;"Eval CLJ snippet with callback"
    [snippet callback]
    (info (str "clj-eval: " snippet))
    (send-cider-message! {:op "eval" :code snippet}
                         (fn [message]
                           (let [{:keys [ns value]} message
                                 data (parse-value value)
                              ; data  (cljs.reader/read-string value)
                                 _ (info (str "clj-eval ns: " ns " data: " data))
                                 _ (info (str "data value:" (get-in data [:value-response :value])))]
                             (when ns (let [v2 (cljs.reader/read-string (get-in data [:value-response :value]))]
                                        (info "clj-eval result: " v2 " type: " (type v2))
                                        (callback v2)))))))

#_(defn ^:export clj-eval-sync
    "executes a clojure expression
   and returns the result to the ```result-atom```"
    [result-atom snippet]
    (let [result-chan (chan)]
      (go
        (clj-eval snippet (fn [result]
                            (info (str "async evalued result: " result))
                            (put! result-chan result))))
      (go (reset! result-atom (<! result-chan)))
      result-atom))

(defn ^:export clj
  "executes a clojure ```function-as-string``` (from clojurescript) 
   and stores the result in ```result-atom```"
  [result-atom function-as-string & params]
  (let [_ (info "params: " params)
        expr (concat ["(" function-as-string] params [")"]) ; params)
        str_eval (clojure.string/join " " expr)
        _ (info (str "Calling CLJ: " str_eval))
        context (clj-eval! str_eval)
        {:keys [channel result]} context]
    (go (_ (<! channel))
        (reset! result-atom @result))))

(defn clj-eval-ignore-result [function-as-string & params]
  (let [result-atom (r/atom {})]
    (if params
      (apply (partial clj result-atom function-as-string)  params)
      (clj result-atom function-as-string))))


(comment

  (eval! 15 "(+ 5 5)")

  (get-completion-doc 'print-table 'clojure.pprint #(println "docs: " %1))


  ;
  )
