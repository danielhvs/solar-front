(ns solar.events
  (:require
    [adzerk.env :as env]
    [ajax.core :as ajax]
    [ajax.protocols :as protocol]
    [clojure.string :as str]
    [re-frame.core :as rf]
    [solar.db :as db]))

(defonce iniciou (atom false))
(defonce TIMEOUT 60000)

;; Variavel de ambiente
(env/def SERVIDOR :required)
(def servidor SERVIDOR)

(defn- obtem-parametro
  ([parametros chave] (chave (first (:parametros parametros))))
  ([parametros] (obtem-parametro parametros :percentual-solar)))

(defn- ->int [dado]
  (js/parseInt dado))

(defn- ->float [dado]
  (js/parseFloat
    (str/replace dado "," ".")))

(defn- scroll-top []
  (set! (.. js/document -body -scrollTop) 0)
  (set! (.. js/document -documentElement -scrollTop) 0))

(defn- kwh->kwp [kwh]
  (/ (Math/round (* 100 (/ kwh (* 30 4.03)))) 100))

(defn- registra-feedback [db valor]
  (assoc db :feedback valor))

(defn- fim-processamento [db msg]
  (assoc (registra-feedback db msg)
    :processando false))

(defn- remover-um-do-carrinho [coll fn-filtro]
  (let [a-remover (filter fn-filtro coll)
        sem-os-removidos (remove fn-filtro coll)]
    (into sem-os-removidos (rest a-remover))))

(defn- multi-geradores? [db]
  (let [{:keys [marca painel telhado]} (:filtros-orcamento db)]
    (and (not= "todos" marca)
         (not= "todos" painel)
         (not= "todos" telhado))))

(defn- abre-pdf [arquivo-pdf nome-arquivo]
  (let [file (js/Blob. (clj->js [arquivo-pdf])
                       (clj->js {:type "application/pdf"}))
        link (.createElement js/document "a")]
    (set! (.-href link) (.createObjectURL js/URL file))
    (.setAttribute link "download" nome-arquivo)
    (.appendChild (.-body js/document) link)
    (.click link)
    (.removeChild (.-body js/document) link)))

(defn- processando [db msg]
  (assoc (registra-feedback db msg)
    :processando true))

(defn- encode [e]
  (js/encodeURIComponent e))

(defn- monta-url [& ops]
  (str "/"
       (reduce (fn [a b]
                 (str a "/" b))
               (map encode ops))))

(defn operacao [nome-api & ops]
  (let [api (str servidor "/" nome-api)
        result (if-not ops
                 api
                 (apply str api (apply monta-url ops)))]
    result))

(rf/reg-event-db
  ::valor-kwp
  (fn [db _]
    (assoc db :valor-kwp (kwh->kwp (:cache-kwh db)))))

(rf/reg-event-db
  ::falha-http
  (fn [db [_ result]]
    (fim-processamento db (str "Erro: " (:status-text result)))))

(rf/reg-event-db
  ::add-produto-carrinho
  (fn [db [_ novo-produto]]
    (let [carrinho (:carrinho db)]
      (assoc db :carrinho
          (conj carrinho novo-produto)))))

(rf/reg-event-db
  ::remove-produto-carrinho
  (fn [db [_ {:keys [gerador transformador so-gerador]}]]
    (assoc db :carrinho
        (let [carrinho (:carrinho db)]
          (if so-gerador
            (remover-um-do-carrinho carrinho
                                    #(and (= (:codigo gerador) (:codigo %))
                                          (not transformador)))
            (if transformador
              (remover-um-do-carrinho carrinho
                                      #(= (:codigo transformador)
                                          (:codigo (:transformador %))))
              (remover-um-do-carrinho carrinho
                                      #(= (:codigo gerador)
                                          (:codigo %)))))))))

(rf/reg-event-db
  ::altera-view
  (fn [db [_ novo & resto]]
    (scroll-top)
    (let [novo-db (assoc db :view-id novo :view-param resto)]
      (if (= novo :view-filtros)
        (rf/dispatch [::consulta-paineis])
        (when (= novo :view-gestao)
          (rf/dispatch [::consulta-pendencias-paineis])))
      novo-db)))

(rf/reg-event-db
  ::sucesso
  (fn [db _]
    (fim-processamento db "")))

(defn reg-sucesso-db [evento chave-db]
  (rf/reg-event-db
    evento
    (fn [db [_ result]]
      (assoc (fim-processamento db "")
        chave-db result))))

(reg-sucesso-db ::sucesso-consulta-transformadores :transformadores)
(reg-sucesso-db ::sucesso-consulta-pendencias-paineis :pendencias-paineis)
(reg-sucesso-db ::sucesso-consulta-paineis :paineis)

(rf/reg-event-db
  ::sucesso-consulta-parametros
  (fn [db [_ result]]
    (rf/dispatch [::consulta-transformadores])
    (assoc (fim-processamento db "")
      :parametros result)))

(rf/reg-event-db
  ::sucesso-consulta-produtos
  (fn [db [_ result]]
    (let [vai-ser-multigeradores? (multi-geradores? db)]
      (when (and (not (:multi-geradores? db))
                 vai-ser-multigeradores?)
        (rf/dispatch [::carrinho []]))
      (assoc
        (fim-processamento db "")
        :produtos result
        :multi-geradores? vai-ser-multigeradores?))))

(rf/reg-event-db
  ::sucesso-consulta-orcamentos
  (fn [db [_ result]]
    (rf/dispatch [::altera-view :view-manutencao-orcamentos])
    (assoc
      (fim-processamento db "")
      :orcamentos result)))

(rf/reg-event-db
  ::sucesso-consulta-detalhes
  (fn [db [_ result]]
    (rf/dispatch [::altera-view :view-detalhes])
    (assoc
      (fim-processamento db "")
      :detalhes result)))

(rf/reg-event-db
  ::por-pagina
  (fn [db [_ result]]
    (assoc db :por-pagina (result (:por-pagina db)))))

(rf/reg-event-db
  ::set-filtro
  (fn [db [_ result]]
    (rf/dispatch [::por-pagina #(int 5)])
    (assoc db :filtros-orcamento (conj (:filtros-orcamento db) result))))

(rf/reg-event-db
  ::set-filtro-orcado
  (fn [db [_ result]]
    (assoc db :filtros-orcado (conj (:filtros-orcado db) result))))

(rf/reg-event-db
  ::set-date
  (fn [db [_ chave valor]]
    (let [atomo (chave db)]
      (reset! atomo valor)
      db)))

(rf/reg-event-db
  ::sucesso-remover-orcamento
  (fn [db _]
    (rf/dispatch [::consulta-orcamentos])
    (fim-processamento db "")))

(rf/reg-event-db
  ::sucesso-orcamento
  (fn [db [_ result __]]
    (abre-pdf result (:nome-arquivo db))
    (fim-processamento db "")))

(rf/reg-event-db
  ::sucesso-edicao
  (fn [db _]
    (rf/dispatch [::consulta-parametros])
    (fim-processamento db "")))

(rf/reg-event-db
  ::sucesso-edicao-selo
  (fn [db _]
    (let [api (operacao "selos"
                        (-> db :nome-painel)
                        (.getTime (js/Date.)))]
      (assoc (fim-processamento db "")
        :url-painel api
        :preview-selo []))))

(defn- usuario-logado [db result chave valor]
  (rf/dispatch [::consulta-parametros])
  (assoc db
    chave valor
    :nome-vendedor "VENDEDOR TESTE"))

(rf/reg-event-db
  ::sucesso-login
  (fn [db [_ result]]
    (usuario-logado db result :gestor? true)))

(rf/reg-event-db
  ::obtem-url-painel
  (fn [db _]
    (let [api (operacao "selos"
                        (-> db :nome-painel)
                        (.getTime (js/Date.)))]
      (assoc db :url-painel api))))

(rf/reg-event-fx
  ::remover-orcamento
  (fn [{:keys [db]} v]
    (let [id (last v)]
      {:db (processando db (str "Removendo orcamento..."))
       :http-xhrio {:method :post
                    :uri (operacao "remove")
                    :timeout TIMEOUT
                    :params {:orcamentos id}
                    :format (ajax/json-request-format)
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success [::sucesso-remover-orcamento]
                    :on-failure [::falha-http]}})))

(rf/reg-event-fx
  ::ver-orcamento
  (fn [{:keys [db]} v]
    (scroll-top)
    {:db (processando db (str "Buscando orcamento..."))
     :http-xhrio {:method :get
                  :uri (operacao "ver-orcamento" (last v))
                  :timeout TIMEOUT
                  :response-format {:content-type "application/pdf"
                                    :description "pdf"
                                    :read protocol/-body
                                    :type :arraybuffer}
                  :on-success [::sucesso-orcamento]
                  :on-failure [::falha-http]}}))

(rf/reg-event-fx
  ::init-server
  (fn [{:keys [db]} _]
    {:db (processando db "Iniciando servidor...")
     :http-xhrio {:method :get
                  :uri (operacao "init")
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::sucesso-login]
                  :on-failure [::falha-http]}}))

(rf/reg-event-fx
  ::edita-parametros
  (fn [{:keys [db]} _]
    {:db (processando db "Salvando...")
     :http-xhrio {:method :post
                  :uri (operacao "edita")
                  :timeout TIMEOUT
                  :params {:coll :parametros
                           :_id (:id db)
                           :reg {:percentual-solar (-> db :cache-percentual-solar ->float)
                                 :ultima-carga (:ultima-carga (first (get-in (:parametros db) [:parametros])))
                                 :aumento-anual (-> db :cache-aumento-anual ->float)}}
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::sucesso-edicao]
                  :on-failure [::falha-http]}}))

(rf/reg-event-fx
  ::edita-poupanca
  (fn [{:keys [db]} v]
    (let [edita-ou-insere (last v)]
      {:db (processando db "Salvando...")
       :http-xhrio {:method :post
                    :uri (operacao (name edita-ou-insere))
                    :timeout TIMEOUT
                    :params {:coll :poupanca
                             :_id (:id db)
                             :reg {:ano (->int (:cache-poupanca-ano db))
                                   :valor (->float (:cache-poupanca db))}}
                    :format (ajax/json-request-format)
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success [::sucesso-edicao]
                    :on-failure [::falha-http]}})))

(rf/reg-event-fx
  ::edita-painel
  (fn [{:keys [db]} v]
    (let [edita-ou-insere (last v)]
      {:db (processando db "Salvando...")
       :http-xhrio {:method :post
                    :uri (operacao (name edita-ou-insere))
                    :timeout TIMEOUT
                    :params {:coll :painel
                             :_id (:id db)
                             :reg {:nome (:cache-painel db)
                                   :texto (:cache-texto-painel db)}}
                    :format (ajax/json-request-format)
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success [::sucesso-edicao]
                    :on-failure [::falha-http]}})))

(rf/reg-event-fx
  ::edita-selo
  (fn [{:keys [db]} _]
    (scroll-top)
    {:db (processando db "Salvando...")
     :http-xhrio {:method :post
                  :uri (operacao "selo")
                  :timeout TIMEOUT
                  :body (let [form-data
                                (doto (js/FormData.)
                                  (.append "_id" (:id db))
                                  (.append "file" (:preview-selo db)))]
                          form-data)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::sucesso-edicao-selo]
                  :on-failure [::falha-http]}}))

(rf/reg-event-fx
  ::edita-login
  (fn [{:keys [db]} v]
    (let [edita-ou-insere (last v)]
      {:db (processando db "Salvando...")
       :http-xhrio {:method :post
                    :uri (operacao (name edita-ou-insere))
                    :timeout TIMEOUT
                    :params {:coll :logins
                             :_id (:id db)
                             :reg {:cpf (:cache-cpf-vendedor db)
                                   :nome (:cache-nome-vendedor db)}}
                    :format (ajax/json-request-format)
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success [::sucesso-edicao]
                    :on-failure [::falha-http]}})))

(rf/reg-event-fx
  ::remove
  (fn [{:keys [db]} v]
    (let [coll (second v)
          id (last v)]
      {:db (processando db "Removendo...")
       :http-xhrio {:method :post
                    :uri (operacao "remove")
                    :timeout TIMEOUT
                    :params {coll id}
                    :format (ajax/json-request-format)
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success [::sucesso-edicao]
                    :on-failure [::falha-http]}})))

(rf/reg-event-db
  ::sucesso-nome-arquivo
  (fn [db [_ result]]
    (assoc db :nome-arquivo (:nome-arquivo result))))

(rf/reg-event-fx
  ::nome-arquivo
  (fn [{:keys [db]} [_ cpf tel percentual]]
    (scroll-top)
    {:db db
     :http-xhrio {:method :get
                  :uri (str servidor "/nome-arquivo/?cpf=" cpf "&tel=" tel "&perc=" percentual)
                  :timeout TIMEOUT
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::sucesso-nome-arquivo]
                  :on-failure [::falha-http]}}))

(rf/reg-event-fx
  ::orcamento
  (fn [{:keys [db]} v]
    (scroll-top)
    {:db (processando db (str "Fazendo orcamento..."))
     :http-xhrio {:method :post
                  :uri (operacao "orcamento")
                  :timeout TIMEOUT
                  :params (last v)
                  :format (ajax/json-request-format)
                  :response-format {:content-type "application/pdf"
                                    :description "pdf"
                                    :read protocol/-body
                                    :type :arraybuffer}
                  :on-success [::sucesso-orcamento]
                  :on-failure [::falha-http]}}))

(rf/reg-event-fx
  ::consulta-orcamentos
  (fn [{:keys [db]} _]
    (scroll-top)
    {:db (processando db (str "Consultando orcamentos..."))
     :http-xhrio {:method :post
                  :uri (operacao "orcamentos")
                  :timeout TIMEOUT
                  :format (ajax/json-request-format)
                  :params (:filtros-orcado db)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::sucesso-consulta-orcamentos]
                  :on-failure [::falha-http]}}))

(rf/reg-event-fx
  ::consulta-detalhes
  (fn [{:keys [db]} v]
    (scroll-top)
    {:db (processando db (str "Consultando detalhes..." (last v)))
     :http-xhrio {:method :get
                  :uri (operacao "detalhes" (last v))
                  :timeout TIMEOUT
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::sucesso-consulta-detalhes]
                  :on-failure [::falha-http]}}))

(rf/reg-event-fx
  ::consulta-parametros
  (prn "Conultad........")
  (fn [{:keys [db]} _]
    {:db (processando db "Consultando parametros...")
     :http-xhrio {:method :get
                  :uri (operacao "parametros")
                  :timeout TIMEOUT
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::sucesso-consulta-parametros]
                  :on-failure [::falha-http]}}))

(rf/reg-event-fx
  ::consulta-pendencias-paineis
  (fn [{:keys [db]} _]
    {:db (processando db "Consultando pendencias paineis...")
     :http-xhrio {:method :get
                  :uri (operacao "pendencias-paineis")
                  :timeout TIMEOUT
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::sucesso-consulta-pendencias-paineis]
                  :on-failure [::falha-http]}}))

(rf/reg-event-fx
  ::consulta-paineis
  (fn [{:keys [db]} _]
    {:db (processando db "Consultando paineis...")
     :http-xhrio {:method :get
                  :uri (operacao "paineis")
                  :timeout TIMEOUT
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::sucesso-consulta-paineis]
                  :on-failure [::falha-http]}}))

(rf/reg-event-fx
  ::consulta-transformadores
  (fn [{:keys [db]} _]
    {:db (processando db "Consultando transformadores...")
     :http-xhrio {:method :post
                  :uri (operacao "transformadores")
                  :timeout TIMEOUT
                  :format (ajax/json-request-format)
                  :params {:percentual-solar (obtem-parametro (:parametros db))}
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::sucesso-consulta-transformadores]
                  :on-failure [::falha-http]}}))

(rf/reg-event-fx
  ::consulta-produtos
  (fn [{:keys [db]} _]
    {:db (processando db "Consultando produtos...")
     :http-xhrio {:method :post
                  :uri (operacao "produtos")
                  :timeout TIMEOUT
                  :format (ajax/json-request-format)
                  :params {:per-page (:por-pagina db)
                           :filtros (:filtros-orcamento db)
                           :percentual-solar (obtem-parametro (:parametros db))}
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::sucesso-consulta-produtos]
                  :on-failure [::falha-http]}}))

(rf/reg-event-db
  ::erro-cpf
  (fn [db [_ msg]]
    (scroll-top)
    (fim-processamento db msg)))

; eventos para apenas atualizar o valor do dado
(defn- reg-event-db-entrada [chave]
  (rf/reg-event-db
    chave
    (fn [db [_ nova-cache]]
      (assoc db (keyword (name chave)) nova-cache))))

(let [entradas [::cache-custo
                ::cache-percent-servico
                ::cache-poupanca
                ::cache-poupanca-ano
                ::preview-selo
                ::nome-cliente
                ::cpf-cliente
                ::tel-cliente
                ::cache-cpf-cliente
                ::cache-telefone-cliente
                ::cache-palavra
                ::carrinho
                ::cache-percentual-solar
                ::cache-kwp-max
                ::cache-kwp-min
                ::cache-valor-filtro
                ::cache-cpf-vendedor
                ::id
                ::cache-aumento-anual
                ::multi-geradores?
                ::cache-cidade
                ::nome-painel
                ::cache-nome-cliente
                ::cache-nome-vendedor
                ::cache-painel
                ::cache-texto-painel
                ::cache-kwh
                ::produtos
                ::nome-vendedor
                ::cache-login]]
  (doall
    (map reg-event-db-entrada entradas)))

(rf/reg-event-db ::cache-selo
                 (fn [db [_ chave valor]]
                   (assoc db chave valor)))

(rf/reg-event-db ::initialize
                 (fn [_ _]
                   (reset! iniciou true)
                   (rf/dispatch [::init-server])
                   db/db-inicial))
