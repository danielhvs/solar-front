(ns solar.views
  (:require
    [cljs-pikaday.reagent :as pd]
    [cljsjs.moment]
    [clojure.string :as str]
    [day8.re-frame.http-fx]
    [re-frame.core :as rf]
    [solar.events :as events]
    [solar.subs :as subs]))

(def LABEL_MARCA "Inversor")
(def MSG_ERRO_NAO_MULTI (str "Não é permitido inserir mais de um gerador usando filtros \"todos\". Para fazer orçamento com vários geradores, é necessário selecionar os 3 filtros: " LABEL_MARCA ", Painel e Telhado"))

;; Tratamento CFP
(def length 11)

(defn- control-digit
  [mask digits]
  (let [sum (apply + (map * mask digits))
        x (- 11 (rem sum 11))]
    (if (> x 9) 0 x)))

(defn- shared-control-digits
  [mask1 mask2 digits]
  (let [c1 (control-digit mask1 digits)
        c2 (control-digit mask2 (conj (vec digits) c1))]
    [c1 c2]))

(def repeated
  "A set of cpfs with repeated digits that are
  considered valid by the algorithm, but normally shouldn't count as valid."
  (conj (set (for [i (range 10)]
               (repeat length i))) [0 1 2 3 4 5 6 7 8 9 0]))

(def control-digits
  (partial shared-control-digits (range 10 1 -1) (range 11 1 -1)))

(defn- digits "Returns a seq of the digits of x"
  [x]
  (mapv #(js/parseInt %) (re-seq #"[0-9]" (str x))))

(defn- shared-parse [code] (digits code))

(defn- shared-split-control
  "Returns a tuple of [code control-digits],
  where control-digits are the last 2 digits."
  [coll]
  (split-at (- (count coll) 2) coll))

(defn- valid?
  "Takes a string, seq of digits or a cpf. Returns true if valid, else false.
  A cpf
  Does not validate formatting."
  ([cpf]
   (let [cpf (shared-parse cpf)
         [digits control] (shared-split-control cpf)]
     (and (= length (count cpf))
          (not (repeated cpf))
          (= control (control-digits digits))))))
;; FIM Tratamento CFP

;; utils
(defn- nome-padrao [nome]
  (str/upper-case nome))

(defn- ranges-kwp [min max]
  (str min "-" max))

(defn- scroll-top []
  (set! (.. js/document -body -scrollTop) 0)
  (set! (.. js/document -documentElement -scrollTop) 0))

;; Componentes

(defn- btn-subir []
  [:button.btn.btn-primary
   {:style {:position "fixed"
            :right "20px"
            :bottom "20px"}
    :on-click #(scroll-top)}
   "^"])

(defn- elemento-isolado [elemento]
  [:div.card.text-center.p-2
   [:ul.list-group
    [:div.list-group-item
     elemento]]])

(defn- btn-detalhar [fn]
  [:input {:type "image" :src "detalhar.svg"
           :on-click fn}])

(defn- btn-remove [fn]
  [:input {:type "image" :src "remove.svg"
           :on-click fn}])

;; -------------------------
;; Funcoes
(defn- um-elemento [e]
  (if (:transformador e)
    {:gerador (-> e :codigo)
     :transformador (-> e :transformador :codigo)}
    {:gerador (-> e :codigo)}))

(defn- carrinho->produtos [carrinho]
  (map um-elemento carrinho))

(defn- float->brasil [dado]
  (str/replace (str dado) "." ","))

(defn- arredonda [f]
  (float->brasil
    (/ (.round js/Math (* 100 f)) 100)))

(defn- is-nan [valor]
  (js/Number.isNaN valor))

(defn- ->float [dado]
  (js/parseFloat
    (str/replace dado "," ".")))

(defn- ->float-ou-zero [dado]
  (let [n (->float dado)]
    (if (is-nan n)
      (->float "0")
      n)))

(defn- oferecer-transformador? [gerador]
  (str/ends-with? (str/trim (:descricao gerador))
                  "TRIF 380V"))

(defn- obtem-parametro
  ([parametros chave] (chave (first (:parametros parametros))))
  ([parametros] (obtem-parametro parametros :percentual-solar)))

;; VIEW

(defn- label-mostra-kwp []
  (let [kwh (rf/subscribe [::subs/cache-kwh])
        valor-raw (rf/subscribe [::subs/valor-kwp])
        valor (cond (is-nan @valor-raw) "?"
                    (str/blank? @valor-raw) "0kWp"
                    :else (str (float->brasil @valor-raw) "kWp"))]
    [:span
     (str @kwh "kWh = " valor)]))

(defn- text-area [texto subscricao evento]
  (let [valor (rf/subscribe [subscricao])
        processando (rf/subscribe [::subs/processando])]
    [:div {:class "input-group mb-1"}
     [:div {:class "input-group-prepend"}
      [:span {:class "input-group-text"} texto]]
     [:textarea {:class "form-control" :value @valor :rows "8"
                 :disabled @processando
                 :on-change #(rf/dispatch [evento (-> % .-target .-value)])}]]))

(defn- parametro [texto subscricao evento]
  (let [valor (rf/subscribe [subscricao])
        processando (rf/subscribe [::subs/processando])]
    [:div {:class "input-group mb-1"}
     [:div {:class "input-group-prepend"}
      [:span {:class "input-group-text"} texto]]
     [:input {:type "text"
              :autocomplete "off"
              :class "form-control" :value @valor :disabled @processando
              :on-change #(rf/dispatch [evento (-> % .-target .-value)])}]]))

(defn- parametro-kwh [texto subscricao evento]
  (let [valor (rf/subscribe [subscricao])
        processando (rf/subscribe [::subs/processando])]
    [:div {:class "input-group mb-1"}
     [:div {:class "input-group-prepend"}
      [:span {:class "input-group-text"} texto]]
     [:input {:type "text"
              :autocomplete "off"
              :class "form-control" :value @valor :disabled @processando
              :on-change #(do (rf/dispatch [evento (-> % .-target .-value)])
                              (rf/dispatch [::events/valor-kwp]))}]]))

(defn- botao-submit
  ([texto on-click opts]
   (let [processando (rf/subscribe [::subs/processando])]
     [:button
      (merge {:type "submit" :on-click on-click :disabled @processando} opts)
      texto]))
  ([texto on-click]
   (botao-submit texto on-click
                 {:class "list-group-item list-group-item-action btn-primary"
                  :style {:color "blue"}})))

(defn- botao-mostra
  ([texto on-click opts]
   (let [processando (rf/subscribe [::subs/processando])]
     [:button
      (merge {:type "button" :on-click on-click :disabled @processando} opts)
      texto]))
  ([texto on-click]
   (botao-mostra texto on-click
                 {:class "list-group-item list-group-item-action"})))

(defn- botao-principal [texto on-click]
  [botao-mostra texto on-click {:class "btn btn-primary"}])

(defn- botao-secundario [texto on-click]
  [botao-mostra texto on-click {:class "btn btn-secondary"}])

(defn- salvar [on-click]
  [botao-principal "Salvar" on-click])

(defn- voltar [on-click]
  [botao-mostra "Voltar" on-click {:class "btn btn-secondary"}])

(defn- label-solar [texto]
  [:label.texto texto])

(defn- feedback []
  (let [feedback (rf/subscribe [::subs/feedback])]
    (when (seq @feedback)
      [:label.texto @feedback])))

;; Componentes
(defn- card-info [titulo & componentes]
  [:div.card.text-center
   [:div.card-header {:style {:text-align "center"}}
    [label-solar titulo]]
   (reduce #(into %1 %2)
           [:ul.list-group]
           (map (fn [e] [[:div.list-group-item e]]) componentes))])

(defn- card-info-sem-header [& componentes]
  [:div.card.text-center
   (reduce #(into %1 %2)
           [:ul.list-group]
           (map (fn [e] [[:div.list-group-item e]]) componentes))])

; -------------------------
; Views

(defn- filtro-kwp []
  (let [cache-kwp-min @(rf/subscribe [::subs/cache-kwp-min])
        cache-kwp-max @(rf/subscribe [::subs/cache-kwp-max])
        processando (rf/subscribe [::subs/processando])]
    [:div
     [:div {:class "input-group mb-1"}
      [:div {:class "input-group-prepend"}
       [:span {:class "input-group-text"} "Mínimo (kWp)"]]
      [:input {:type "text"
               :class "form-control"
               :autocomplete "off"
               :value cache-kwp-min
               :disabled @processando
               :on-change (fn [evt]
                            (let [valor (-> evt .-target .-value)]
                              (rf/dispatch-sync [::events/cache-kwp-min valor])
                              (rf/dispatch [::events/set-filtro {:filtro-kwp (ranges-kwp
                                                                               valor
                                                                               cache-kwp-max)}])))}]]
     [:div {:class "input-group mb-1"}
      [:div {:class "input-group-prepend"}
       [:span {:class "input-group-text"} "Máximo (kWp)"]]
      [:input {:type "text"
               :autocomplete "off"
               :value cache-kwp-max
               :class "form-control"
               :disabled @processando
               :on-change (fn [evt]
                            (let [valor (-> evt .-target .-value)]
                              (rf/dispatch-sync [::events/cache-kwp-max valor])
                              (rf/dispatch [::events/set-filtro {:filtro-kwp (ranges-kwp
                                                                               cache-kwp-min
                                                                               valor)}])))}]]]))

(defn- filtro-palavra []
  (let [valor (rf/subscribe [::subs/cache-palavra])
        processando (rf/subscribe [::subs/processando])]
    [:div {:class "input-group mb-1"}
     [:div {:class "input-group-prepend"}
      [:span {:class "input-group-text"} "Palavra"]]
     [:input {:type "text"
              :autocomplete "off"
              :class "form-control" :value @valor :disabled @processando
              :on-change #(let [v (-> % .-target .-value)]
                            (rf/dispatch [::events/cache-palavra v])
                            (rf/dispatch [::events/set-filtro {:palavra v}]))}]]))

(defn- filtro-marca []
  (let [selecionado (:marca @(rf/subscribe [::subs/filtros-orcamento]))
        parametros (rf/subscribe [::subs/parametros])
        processando (rf/subscribe [::subs/processando])
        multi-geradores? (rf/subscribe [::subs/multi-geradores?])]
    [:div {:class "input-group mb-1"}
     [:div {:class "input-group-prepend"}
      [:span {:class "input-group-text"} LABEL_MARCA]]
     [:select {:disabled (or @processando @multi-geradores?)
               :class "form-control"
               :on-change (fn [evt]
                            (let [v (-> evt .-target .-value)]
                              (rf/dispatch [::events/set-filtro {:marca v}])))}
      [:option {:selected (= selecionado "todos")
                :value "todos"} "todos"]
      (for [valor (map :nome (:marca @parametros))]
        [:option {:selected (= selecionado valor)
                  :value valor
                  :key valor} valor])]]))

(defn- filtro
  ([label valores chave funcao]
   (let [selecionado (chave @(rf/subscribe [::subs/filtros-orcamento]))
         processando (rf/subscribe [::subs/processando])
         multi-geradores? (rf/subscribe [::subs/multi-geradores?])]
     [:div {:class "input-group mb-1"}
      [:div {:class "input-group-prepend"}
       [:span {:class "input-group-text"} label]]
      [:select {:disabled (or @processando @multi-geradores?)
                :class "form-control"
                :on-change (fn [evt]
                             (rf/dispatch [::events/set-filtro {chave (-> evt .-target .-value)}]))}
       [:option {:selected (= selecionado "todos")
                 :value "todos"} "todos"]
       (for [f valores]
         (let [valor (str (funcao f))]
           [:option {:selected (= selecionado valor)
                     :value valor
                     :key valor} valor]))]]))

  ([label valores chave]
   (filtro label valores chave identity)))

(defn- logo []
  [:div.card-header {:style {:text-align "center"}}
   [:img {:src "logo.svg"}]])

(defn- template [titulo elementos]
  [:div.container.justify-content-center
   [:div.card.text-center
    [logo]
    [:div.container.justify-content-center.p-3
     [:div.card.text-center
      [:div.card-header {:style {:text-align "center"}}
       [label-solar titulo]]
      (reduce #(into %1 %2) [:ul.list-group (when (feedback) [:div.list-group-item [feedback]])] elementos)]]]])

(defn- template-sem-logo [elemento-titulo elementos]
  [:div.container.justify-content-center
   [:div.card.text-center
    [:div.container.justify-content-center.p-3
     [:div.card.text-center
      [:div.card-header {:style {:text-align "center"}}
       elemento-titulo]
      (reduce #(into %1 %2) [:ul.list-group (when (feedback) [:div.list-group-item [feedback]])] elementos)]]]])

(defn- view-detalhes []
  (let [detalhes (rf/subscribe [::subs/detalhes])]
    [template "Detalhes"
     (into [[[voltar #(rf/dispatch [::events/altera-view :view-solar])]]
            [[:form {:target "_blank"
                     :action (str "https://www.aldo.com.br/produto/" (:codigo @detalhes) "/")}
              [:div {:class "list-group-item list-group-item-action"}
               [botao-submit "Mais detalhes" #(.log js/console (:codigo @detalhes))
                {:class "btn btn-primary"}]]]]]
           (for [k (keys @detalhes)]
             [[:div.list-group-item
               [label-solar (str (name k) ": ")]
               [:div {:dangerouslySetInnerHTML {:__html (k @detalhes)}}]]]))]))

(defn- view-edicao []
  (let [view-param (rf/subscribe [::subs/view-param])
        funcao (first @view-param)]
    [template "Vendedor"
     [[[voltar #(rf/dispatch [::events/altera-view :view-logins])]]
      [[parametro "CPF" ::subs/cache-cpf-vendedor ::events/cache-cpf-vendedor]]
      [[parametro "Nome" ::subs/cache-nome-vendedor ::events/cache-nome-vendedor]]
      [[salvar #(rf/dispatch [::events/edita-login funcao])]]]]))

(defn- altera-figura [file]
  (let [file-reader (js/FileReader.)]
    (set! (.-onload file-reader)
          (fn [file-load-event]
            (rf/dispatch [::events/preview-selo (-> file-load-event .-target .-result)])))
    (.readAsDataURL file-reader file)))

(defn- view-edicao-painel []
  (let [view-param (rf/subscribe [::subs/view-param])
        url-painel (rf/subscribe [::subs/url-painel])
        preview-selo (rf/subscribe [::subs/preview-selo])
        edita-ou-insere (first @view-param)]
    [template "Painel"
     [[[voltar #(rf/dispatch [::events/altera-view :view-filtros])]]
      [[parametro "Painel" ::subs/cache-painel ::events/cache-painel]]
      [[text-area "Texto Garantia" ::subs/cache-texto-painel ::events/cache-texto-painel]]
      [[botao-principal "Salvar texto" #(rf/dispatch [::events/edita-painel edita-ou-insere])]]
      [[label-solar "Figura do selo atual"]]
      [[:img {:src @url-painel}]]
      [[label-solar "Atualizar figura do selo para (arquivo jpg):"]]
      [[:input {:type "file" :id "id-painel" :on-change #(altera-figura (-> % .-target .-files (aget 0)))}]]
      [[:img {:src @preview-selo}]]
      [[botao-principal "Salvar selo" #(rf/dispatch [::events/edita-selo])]]]]))

(defn- gestao-painel [valores]
  [template-sem-logo [label-solar "Painel"]
   [[[:div.container
      [:div.row
       [:div.col-12.p-3 [:input {:type "image" :src "adiciona.svg"
                                 :on-click #(do (rf/dispatch [::events/cache-painel ""])
                                                (rf/dispatch [::events/cache-texto-painel ""])
                                                (rf/dispatch [::events/obtem-url-painel ""])
                                                (rf/dispatch [::events/nome-painel ""])
                                                (rf/dispatch [::events/preview-selo []])
                                                (rf/dispatch [::events/altera-view :view-edicao-painel :insere]))}]]
       [:div.col-12
        [:table.table.table-bordered
         [:thead
          [:tr [:th {:scope "col"} "Painel"] [:th {:scope "col"} "Ações"]]]
         [:tbody
          (for [v valores] ^{:key v}
            [:tr
             [:td (:nome v)]
             [:td
              [:div.container
               [:div.row
                [:div.col-6 [:input {:type "image" :src "edita.svg"
                                     :on-click #(do (rf/dispatch [::events/id (:_id v)])
                                                    (rf/dispatch [::events/cache-painel (:nome v)])
                                                    (rf/dispatch [::events/nome-painel (:nome v)])
                                                    (rf/dispatch [::events/obtem-url-painel (:nome v)])
                                                    (rf/dispatch [::events/cache-texto-painel (:texto v)])
                                                    (rf/dispatch [::events/preview-selo []])
                                                    (rf/dispatch [::events/altera-view :view-edicao-painel :edita]))}]]
                [:div.col-6 [:input {:type "image" :src "remove.svg"
                                     :on-click #(rf/dispatch-sync [::events/remove :painel (:_id v)])}]]]]]])]]]]]]]])

(defn- view-edicao-poupanca []
  (let [view-param (rf/subscribe [::subs/view-param])
        funcao (last @view-param)]
    [template "Rendimento da Poupança"
     [[[voltar #(rf/dispatch [::events/altera-view :view-poupanca])]]
      [[parametro "Ano" ::subs/cache-poupanca-ano ::events/cache-poupanca-ano]]
      [[parametro "Rendimento (%)" ::subs/cache-poupanca ::events/cache-poupanca]]
      [[salvar #(rf/dispatch [::events/edita-poupanca funcao])]]]]))

(defn- view-edicao-parametros []
  [template "Parâmetros"
   [[[voltar #(rf/dispatch [::events/altera-view :view-parametros])]]
    [[parametro "Percentual solar" ::subs/cache-percentual-solar ::events/cache-percentual-solar]]
    [[parametro "Aumento Médio Anual" ::subs/cache-aumento-anual ::events/cache-aumento-anual]]
    [[salvar #(rf/dispatch [::events/edita-parametros])]]]])

(defn- view-parametros []
  (let [parametros (first (:parametros @(rf/subscribe [::subs/parametros])))]
    [template "Gestão de parâmetros de orçamento"
     (into
       (into
         [[[voltar #(rf/dispatch [::events/altera-view :view-gestao])]]
          [[:div.container
            [:div.row
             [:div.col-12
              [:table.table.table-bordered
               [:thead
                [:tr [:th {:scope "col"} "Parâmetros"]
                 [:td
                  [:div.container
                   [:div.row
                    [:div.col-12 [:input {:type "image" :src "edita.svg"
                                          :on-click #(do (rf/dispatch [::events/cache-percentual-solar (-> parametros :percentual-solar float->brasil)])
                                                         (rf/dispatch [::events/cache-aumento-anual (-> parametros :aumento-anual float->brasil)])
                                                         (rf/dispatch [::events/id (:_id parametros)])
                                                         (rf/dispatch [::events/altera-view :view-edicao-parametros]))}]]]]]]]
               [:tbody
                [:tr
                 [:td {:colSpan "2"} (str "Aumento médio anual: " (-> parametros :aumento-anual float->brasil))]]
                [:tr
                 [:td {:colSpan "2"} (str "Percentual solar: " (-> parametros :percentual-solar float->brasil))]]]]]]]]]))]))

(defn- view-poupanca []
  (let [parametros (rf/subscribe [::subs/parametros])
        poupancas (:poupanca @parametros)]
    [template "Rendimento da Poupança"
     (into
       (into
         [[[voltar #(rf/dispatch [::events/altera-view :view-gestao])]]
          [[:div.container
            [:div.row
             [:div.col-12.p-3 [:input {:type "image" :src "adiciona.svg"
                                       :on-click #(do (rf/dispatch [::events/cache-poupanca-ano ""])
                                                      (rf/dispatch [::events/cache-poupanca ""])
                                                      (rf/dispatch [::events/altera-view :view-edicao-poupanca :insere]))}]]
             [:div.col-12
              [:table.table.table-bordered
               [:thead
                [:tr [:th {:scope "col"} "Poupança"] [:th {:scope "col"} "Valor"] [:th {:scope "col"} "Ações"]]]
               [:tbody
                (for [p poupancas] ^{:key p}
                  [:tr
                   [:td (:ano p)]
                   [:td (float->brasil (:valor p))]
                   [:td
                    [:div.container
                     [:div.row
                      [:div.col-6 [:input {:type "image" :src "edita.svg"
                                           :on-click #(do (rf/dispatch [::events/cache-poupanca-ano (:ano p)])
                                                          (rf/dispatch [::events/id (:_id p)])
                                                          (rf/dispatch [::events/cache-poupanca (float->brasil (:valor p))])
                                                          (rf/dispatch [::events/altera-view :view-edicao-poupanca :edita]))}]]
                      [:div.col-6 [:input {:type "image" :src "remove.svg"
                                           :on-click #(rf/dispatch-sync [::events/remove :poupanca (:_id p)])}]]]]]])]]]]]]]))]))

(defn- view-escolher-transformador []
  (let [transformadores (rf/subscribe [::subs/transformadores])
        gerador (rf/subscribe [::subs/view-param])]
    [template "Adicionar transformador?"
     (-> [[[botao-secundario "Não" #(do (rf/dispatch [::events/add-produto-carrinho (first @gerador)])
                                         (rf/dispatch [::events/altera-view :view-solar]))]]]
         (into (for [t @transformadores]
                 [[:div.card.text-center.p-3
                   [:ul.list-group
                    [botao-principal (str (:preco t) " - " (:descricao t))
                     #(do (rf/dispatch [::events/add-produto-carrinho (assoc (first @gerador) :transformador t)])
                          (rf/dispatch [::events/altera-view :view-solar]))]]]])))]))

;https://codepen.io/cristinaconacel/pen/dgxqxj
;https://feathericons.com/

(defn- view-logins []
  (let [parametros (rf/subscribe [::subs/parametros])]
    [template "Vendedores"
     [[[voltar #(rf/dispatch [::events/altera-view :view-gestao])]]
      [[:div.container
        [:div.row
         [:div.col-12.p-3 [:input {:type "image" :src "adiciona.svg"
                                   :on-click #(do (rf/dispatch [::events/cache-cpf-vendedor ""])
                                                  (rf/dispatch [::events/cache-nome-vendedor ""])
                                                  (rf/dispatch [::events/altera-view :view-edicao :insere]))}]]
         [:div.col-12
          [:table.table.table-bordered
           [:thead
            [:tr [:th {:scope "col"} "Login"] [:th {:scope "col"} "Ações"]]]
           [:tbody
            (for [l (:logins @parametros)] ^{:key l}
              [:tr
               [:td (:nome l)]
               [:td
                [:div.container
                 [:div.row

                  [:div.col-6 [:input {:type "image" :src "edita.svg"
                                       :on-click #(do (rf/dispatch [::events/id (:_id l)])
                                                      (rf/dispatch [::events/cache-cpf-vendedor (:cpf l)])
                                                      (rf/dispatch [::events/cache-nome-vendedor (:nome l)])
                                                      (rf/dispatch [::events/altera-view :view-edicao :edita]))}]]
                  [:div.col-6 [:input {:type "image" :src "remove.svg"
                                       :on-click #(rf/dispatch-sync [::events/remove :logins (:_id l)])}]]]]]])]]]]]]]]))

(defn- view-filtros []
  (let [paineis @(rf/subscribe [::subs/paineis])]
    [template "Filtros"
     [[[voltar #(rf/dispatch [::events/altera-view :view-gestao])]]
      [[gestao-painel paineis]]]]))

(defn- elemento-pendente [texto]
  [:span {:style {:color "red"}} texto])

(defn- formata-pendencia [painel]
  (let [falta-selo? (not (:selo? painel))
        falta-texto? (not (:texto? painel))
        os-dois? (and falta-selo? falta-texto?)]
    [:label
     (str (:nome painel) ": ")
     (cond os-dois? [elemento-pendente "selo e texto"]
           falta-selo? [elemento-pendente "selo"]
           falta-texto? [elemento-pendente "texto"])]))

(defn- formata-orcamento-produto [o]
  (let [um-gerador (first (map :gerador (:produtos o)))
        um-transformador (first (map :transformador (:produtos o)))]
    (reduce #(str %1 " - " %2)
            [(str "Inversor " (:MARCA_INVERSOR um-gerador))
             (str "Painel " (:MARCA_PAINEL um-gerador))
             (str "Telhado " (:TIPO_ESTRUTURA um-gerador))
             (if um-transformador "Com transformador" "Sem transformador")])))

(defn- zero-a-esquerda [dado]
  (let [n dado]
    (if (= 1 (count (str n)))
      (str "0" n)
      n)))

(defn- formata-data [data]
  (let [date (js/Date. data)]
    (str
      (reduce #(str %1 "/" %2)
              [(zero-a-esquerda (.getDate date))
               (zero-a-esquerda (inc (.getMonth date)))
               (.getFullYear date)])
      " "
      (reduce #(str %1 ":" %2)
              [(zero-a-esquerda (.getHours date))
               (zero-a-esquerda (.getMinutes date))
               (zero-a-esquerda (.getSeconds date))]))))

(defn- filtro-orcado [chave nome]
  (let [selecionado (chave @(rf/subscribe [::subs/filtros-orcado]))
        parametros (rf/subscribe [::subs/parametros])
        processando (rf/subscribe [::subs/processando])]
    [:div {:class "input-group mb-1"}
     [:div {:class "input-group-prepend"}
      [:span {:class "input-group-text"} nome]]
     [:select {:disabled @processando
               :class "form-control"
               :on-change (fn [evt]
                            (let [v (-> evt .-target .-value)]
                              (rf/dispatch [::events/set-filtro-orcado {chave v}])))}
      [:option {:selected (= selecionado "todos")
                :value "todos"} "todos"]
      (for [valor (map :nome (chave @parametros))]
        [:option {:selected (= selecionado valor)
                  :value valor
                  :key valor} valor])]]))

(defn- filtro-vendedor [nome-vendedor gestor?]
  (if gestor?
    [filtro-orcado :logins "Vendedor"]
    (do
      (rf/dispatch [::events/set-filtro-orcado {:logins nome-vendedor}])
      [:div])))

(def today (js/Date.))

(defn- filtro-data [nome funcao opts]
  [:div {:class "input-group mb-1"}
   [:div {:class "input-group-prepend"}
    [:span {:class "input-group-text"} nome]]
   [pd/date-selector opts]
   [btn-remove funcao]])

;; NECESSARIO DEVIDO A BUG VISUAL! Duplicado mesmo...
(defn- filtro-data-2 [nome funcao opts]
  [:div {:class "input-group mb-1"}
   [:div {:class "input-group-prepend"}
    [:span {:class "input-group-text"} nome]]
   [pd/date-selector opts]
   [btn-remove funcao]])

(defn- filtro-orcado-req [titulo subscricao evento]
  (let [valor (rf/subscribe [subscricao])
        processando (rf/subscribe [::subs/processando])]
    [:div {:class "input-group mb-1"}
     [:div {:class "input-group-prepend"}
      [:span {:class "input-group-text"} titulo]]
     [:input {:type "text"
              :autocomplete "off"
              :class "form-control" :value @valor :disabled @processando
              :on-change #(let [v (-> % .-target .-value)]
                            (rf/dispatch [evento v])
                            (rf/dispatch [::events/set-filtro-orcado
                                          {(str "request." (name evento)) ;; fixme
                                             v}]))}]]))

(defn- ou-vazio [palavra]
  (if (str/blank? palavra) "Não preenchido" palavra))

(defn- view-manutencao-orcamentos []
  (let [orcamentos (rf/subscribe [::subs/orcamentos])
        start-date @(rf/subscribe [::subs/start-date])
        gestor? @(rf/subscribe [::subs/gestor?])
        nome-vendedor @(rf/subscribe [::subs/nome-vendedor])
        end-date @(rf/subscribe [::subs/end-date])]
    [template (str "Consultar Orçamentos: " (nome-padrao nome-vendedor))
     (-> [[[voltar #(rf/dispatch [::events/altera-view :view-gestao])]]]
         (into [[[filtro-data "Início Orçamento"
                  (fn []
                    (rf/dispatch-sync [::events/set-date :start-date nil])
                    (rf/dispatch-sync [::events/set-filtro-orcado {:start-date false}]))
                  {:date-atom start-date
                   :pikaday-attrs {:max-date today
                                   :format "DD/MM/YYYY"
                                   :on-select #(rf/dispatch [::events/set-date :start-date %])
                                   :on-close #(rf/dispatch [::events/set-filtro-orcado {:start-date (.getTime @start-date)}])}
                   :input-attrs {:class "form-control" :autocomplete "off" :id "start"}}]]])
         (into [[[filtro-data-2 "Fim Orçamento"
                  (fn []
                    (rf/dispatch-sync [::events/set-date :end-date nil])
                    (rf/dispatch-sync [::events/set-filtro-orcado {:end-date false}]))
                  {:date-atom end-date
                   :pikaday-attrs {:max-date today
                                   :format "DD/MM/YYYY"
                                   :on-select #(rf/dispatch [::events/set-date :end-date %])
                                   :on-close #(rf/dispatch [::events/set-filtro-orcado {:end-date (.getTime @end-date)}])}
                   :input-attrs {:class "form-control" :autocomplete "off" :id "end"}}]]])
         (into [[[filtro-vendedor nome-vendedor gestor?]]])
         (into [[[filtro-orcado-req "Nome do Cliente" ::subs/nome-cliente ::events/nome-cliente]]])
         (into [[[filtro-orcado-req "CPF do Cliente" ::subs/cpf-cliente ::events/cpf-cliente]]])
         (into [[[filtro-orcado-req "Telefone do Cliente" ::subs/tel-cliente ::events/tel-cliente]]])
         (into [[[filtro-orcado :telhado "Telhado"]]])
         (into [[[filtro-orcado :marca "Inversor"]]])
         (into [[[filtro-orcado :painel "Painel"]]])
         (into [[[botao-principal "Filtrar" #(rf/dispatch [::events/consulta-orcamentos])]]])
         (into [[(for [o @orcamentos] ^{:key o}
                   (let [r (:request o)]
                     [card-info-sem-header
                      [label-solar (formata-orcamento-produto o)]
                      [label-solar (str "Vendedor: " (:nome-vendedor r))]
                      [label-solar (str "Cliente: " (:nome-cliente r) " CPF: " (ou-vazio (:cpf-cliente r)) " Telefone: " (ou-vazio (:tel-cliente r)))]
                      [label-solar (str "Data: " (formata-data (:data o)))]
                      [:ul.list-group.list-group-horizontal.justify-content-center
                       [:div.row.align-items-center
                        [:div.col.m-0.p-2
                         [btn-detalhar #(do (rf/dispatch [::events/nome-arquivo (:cpf-cliente r) (:tel-cliente r) (:percentual r)])
                                            (rf/dispatch [::events/ver-orcamento (:_id o)]))]]
                        [:div.col.m-0.p-2
                         [btn-remove #(rf/dispatch [::events/remover-orcamento (:_id o)])
                          {:class "btn btn-primary"}]]]]]))]]))]))

(defn- ttem-permissao? [gestor? componente]
  (if gestor? componente [:div]))

(defn- tem-permissao? [gestor? componente]
  (if gestor? componente [[:div]]))

(defn- view-gestao []
  (let [nome-vendedor @(rf/subscribe [::subs/nome-vendedor])
        parametros (rf/subscribe [::subs/parametros])
        paineis @(rf/subscribe [::subs/pendencias-paineis])
        gestor? @(rf/subscribe [::subs/gestor?])
        ultima-carga (:ultima-carga (first (get-in @parametros [:parametros])))
        paineis (filter (fn [e] (or (not (:selo? e))
                                    (not (:texto? e)))) paineis)]
    [template
     (str (if gestor? "Administrador: " "Vendedor(a): ") (nome-padrao nome-vendedor))
     [(tem-permissao? gestor? [[:div.card-header {:style {:text-align "center"}}
                                [label-solar (str "Data da última atualização com Aldo: " ultima-carga)]]])
      (tem-permissao? gestor?
                      (if (empty? paineis)
                        []
                        [[:table.table.table-bordered
                          [:thead
                           [:tr [:th {:scope "col"} "Pendências (Filtros)"]]]
                          [:tbody
                           (for [p paineis] ^{:key p}
                             [:tr
                              [:td {:style {:color "orange"}} (formata-pendencia p)]])]]]))

      [(ttem-permissao? gestor? [botao-mostra "Poupança" #(rf/dispatch [::events/altera-view :view-poupanca])])]
      [(ttem-permissao? gestor? [botao-mostra "Logins" #(rf/dispatch [::events/altera-view :view-logins])])]
      [(ttem-permissao? gestor? [botao-mostra "Parâmetros" #(rf/dispatch [::events/altera-view :view-parametros])])]
      [(ttem-permissao? gestor? [botao-mostra "Filtros"
                                 #(rf/dispatch [::events/altera-view :view-filtros])])]
      [[botao-mostra "Fazer Orçamento" #(do
                                           (rf/dispatch [::events/altera-view :view-solar])
                                           (rf/dispatch [::events/consulta-produtos])
                                           (rf/dispatch [::events/consulta-parametros]))]]]]))

(defn- um-produto [p]
  (if (:transformador p)
    [card-info (:descricao p)
     [label-solar (-> p :transformador :descricao)]
     [label-solar (str "Transformador: " (-> p :transformador :preco))]
     [label-solar (str "Gerador: " (:preco p))]
     [label-solar (str (:qtd-placas p) " painéis de " (:w p) "W")]
     [label-solar (str (arredonda (:kwp p)) "kWp")]]
    [card-info (:descricao p)
     [label-solar (str (:preco p))]
     [label-solar (str (:qtd-placas p) " painéis de " (:w p) "W")]
     [label-solar (str (arredonda (:kwp p)) "kWp")]]))

(defn- preenchido? [valor]
  (not (str/blank? valor)))

(defn- view-orcamento []
  (let [custo (rf/subscribe [::subs/cache-custo])
        parametros (rf/subscribe [::subs/parametros])
        carrinho (rf/subscribe [::subs/carrinho])
        percentual (rf/subscribe [::subs/cache-percent-servico])
        cpf (rf/subscribe [::subs/cache-cpf-cliente])
        telefone (rf/subscribe [::subs/cache-telefone-cliente])
        nome-cliente (rf/subscribe [::subs/cache-nome-cliente])
        cidade (rf/subscribe [::subs/cache-cidade])
        nome-vendedor (rf/subscribe [::subs/nome-vendedor])]
    [:div
     [template "Orçamento"
      [[[voltar #(do
                   (rf/dispatch [::events/erro-cpf ""])
                   (rf/dispatch [::events/altera-view :view-carrinho]))]]
       [(reduce #(into %1 %2)
                [card-info "Geradores"]
                (for [c @carrinho]
                  [[um-produto c]]))]
       [[parametro "Custo do kWh (R$)" ::subs/cache-custo ::events/cache-custo]]
       [[parametro "% de Servico" ::subs/cache-percent-servico ::events/cache-percent-servico]]
       [[parametro "Nome do Cliente" ::subs/cache-nome-cliente ::events/cache-nome-cliente]]
       [[parametro "CPF do Cliente" ::subs/cache-cpf-cliente ::events/cache-cpf-cliente]]
       [[parametro "Telefone do Cliente" ::subs/cache-telefone-cliente ::events/cache-telefone-cliente]]
       [[parametro "Cidade" ::subs/cache-cidade ::events/cache-cidade]]
       [[botao-mostra "Gerar Orçamento" #(if (or (preenchido? @telefone) (valid? @cpf))
                                            (let [percentual (->float-ou-zero @percentual)]
                                              (rf/dispatch [::events/erro-cpf ""])
                                              (rf/dispatch [::events/nome-arquivo (if (valid? @cpf) @cpf "") @telefone percentual])
                                              (rf/dispatch [::events/orcamento
                                                            {:produtos (carrinho->produtos @carrinho)
                                                             :nome-cliente @nome-cliente
                                                             :nome-vendedor @nome-vendedor
                                                             :aumento-anual (obtem-parametro @parametros :aumento-anual)
                                                             :custo (->float @custo)
                                                             :percentual percentual
                                                             :cidade @cidade
                                                             :percentual-solar (obtem-parametro @parametros)
                                                             :tel-cliente @telefone
                                                             :cpf-cliente (if (valid? @cpf) @cpf "")}]))
                                            (rf/dispatch [::events/erro-cpf "CPF do Cliente Inválido"]))
         {:class "btn btn-primary"
          :disabled (and (str/blank? @telefone)
                         (or (is-nan @cpf)
                             (str/blank? @cpf)))}]]]]
     [btn-subir]]))

(defn- um-produto-carrinho [p]
  (if (:transformador p)
    [card-info-sem-header
     [label-solar (-> p :transformador :descricao)]
     [label-solar (str "Transformador: " (-> p :transformador :preco))]
     [label-solar (str "Gerador: " (:preco p))]
     [label-solar (str (:qtd-placas p) " painéis")]
     [label-solar (str (arredonda (:kwp p)) "kWp")]
     [btn-remove #(rf/dispatch [::events/remove-produto-carrinho {:gerador p :transformador
                                                                    (:transformador p)}])]]
    [card-info-sem-header
     [label-solar (str (:preco p))]
     [label-solar (str (:qtd-placas p) " painéis")]
     [label-solar (str (arredonda (:kwp p)) "kWp")]
     [btn-remove #(rf/dispatch [::events/remove-produto-carrinho {:gerador p
                                                                  :so-gerador true}])]]))

(defn- view-carrinho []
  (let [produtos @(rf/subscribe [::subs/carrinho])]
    [:div
     (if (seq produtos)
       (let [um-gerador (first produtos)]
         [template
          [:ul.list-group.p-2
           [:div.container
            [:div.col
             [:div.row [label-solar (str "Painéis " (:MARCA_PAINEL um-gerador))]]
             [:div.row [label-solar (str "Telhado " (:TIPO_ESTRUTURA um-gerador))]]
             [:div.row [label-solar (str "Inversor " (:MARCA_INVERSOR um-gerador))]]]]]
          (-> [[[voltar #(rf/dispatch [::events/altera-view :view-solar])]]]
              (into [[[elemento-isolado [botao-principal "Fazer Orçamento"
                                         #(rf/dispatch [::events/altera-view :view-orcamento {}])]]]])
              (into (for [p produtos]
                      [[:div.card.text-center.p-3
                        [:ul.list-group
                         [um-produto-carrinho p]]]])))])
       [template
        [:ul.list-group.p-2
         [:div.container
          [:div.col
           [:div.row [label-solar "Nenhum gerador selecionado"]]]]]
        (-> [[[voltar #(rf/dispatch [::events/altera-view :view-solar])]]])])
     [btn-subir]]))

(defn- btn-carrinho []
  (let [carrinho (rf/subscribe [::subs/carrinho])]
    [:button.btn.btn-primary {:type "button"
                              :on-click #(rf/dispatch [::events/altera-view :view-carrinho])}
     "Geradores "
     [:span.badge.badge-pill.badge-light (count @carrinho)]]))

(defn- quantidade-no-carrinho [carrinho p]
  (->> carrinho
       (filter #(= (:codigo p) (:codigo %)))
       count
       str))

(defn- view-solar []
  (let [parametros (rf/subscribe [::subs/parametros])
        produtos (rf/subscribe [::subs/produtos])
        carrinho (rf/subscribe [::subs/carrinho])
        multi-geradores? (rf/subscribe [::subs/multi-geradores?])
        so-um-gerador? (and (= 1 (count @carrinho)) (not @multi-geradores?))
        nome-vendedor (rf/subscribe [::subs/nome-vendedor])]
    [:div
     [template (str "Vendedor(a): " (nome-padrao @nome-vendedor))
      (->
        [[[botao-secundario "Voltar"
           #(rf/dispatch [::events/altera-view :view-gestao])]]
         [[elemento-isolado [btn-carrinho]]]
         [[:div.card.text-center.p-2
           [:div.card-header {:style {:text-align "center"}}
            [label-solar "Conversão"]]
           [:ul.list-group.p-2
            [:div.container
             [:div.col
              [:div.row-6 [parametro-kwh "kWh" ::subs/cache-kwh ::events/cache-kwh]]
              [:div.row-6 [label-mostra-kwp]]]]]]]
         (when @multi-geradores?
           [[elemento-isolado [botao-principal "Habilitar Filtros e Remover Geradores Escolhidos"
                               #(do (rf/dispatch [::events/multi-geradores? false])
                                    (rf/dispatch [::events/carrinho []]))]
             {:class "btn btn-primary"}]])
         [[filtro-palavra]]
         [[filtro-marca]]
         [[filtro (str/capitalize (name :painel)) (map :nome (:painel @parametros)) :painel]]
         [[filtro (str/capitalize (name :telhado)) (map :nome (:telhado @parametros)) :telhado]]]
        (into [[[filtro-kwp]]
               [[botao-mostra "Filtrar" #(do
                                           (rf/dispatch [::events/produtos {}])
                                           (rf/dispatch [::events/consulta-produtos])) {:class "btn btn-primary"}]]])
        (into (for [p @produtos]
                [[:div.card.text-center.p-3
                  [:ul.list-group
                   [card-info (:descricao p)
                    [label-solar (str (:preco p))]
                    [label-solar (str (:qtd-placas p) " painéis de " (:w p) "W")]
                    [label-solar (str (arredonda (:kwp p)) "kWp")]
                    [:ul.list-group.list-group-horizontal.justify-content-center
                     [:div.row.align-items-center
                      [:div.col.m-0.p-2
                       [btn-detalhar #(rf/dispatch [::events/consulta-detalhes (:codigo p)])]]
                      [:div.col.m-0.p-2
                       [botao-mostra "-" #(rf/dispatch [::events/remove-produto-carrinho {:gerador p :so-gerador false}])
                        {:class "btn btn-primary"}]]
                      [:div.col.m-0.p-2 [label-solar (quantidade-no-carrinho @carrinho p)]]
                      [:div.col.m-0.p-2 [botao-mostra "+" #(if (oferecer-transformador? p)
                                                             (rf/dispatch [::events/altera-view :view-escolher-transformador p])
                                                             (rf/dispatch [::events/add-produto-carrinho p]))
                                         {:class "btn btn-primary"
                                          :disabled so-um-gerador?
                                          :title (if so-um-gerador?
                                                   MSG_ERRO_NAO_MULTI
                                                   "Adicionar gerador")}]]]]]]]]))
        (into [[[botao-mostra "+"
                 #(do (rf/dispatch [::events/por-pagina (fn [e] (+ 5 e))])
                      (rf/dispatch [::events/consulta-produtos]))
                 {:class "btn btn-secondary"}]]]))]
     [btn-subir]]))

(defonce views
  {:view-detalhes view-detalhes
   :view-edicao view-edicao
   :view-edicao-painel view-edicao-painel
   :view-edicao-poupanca view-edicao-poupanca
   :view-edicao-parametros view-edicao-parametros
   :view-parametros view-parametros
   :view-poupanca view-poupanca
   :view-escolher-transformador view-escolher-transformador
   :view-logins view-logins
   :view-filtros view-filtros
   :view-manutencao-orcamentos view-manutencao-orcamentos
   :view-gestao view-gestao
   :view-orcamento view-orcamento
   :view-carrinho view-carrinho
   :view-solar view-solar})

(defn pagina-toda []
  (let [view-key @(rf/subscribe [::subs/view-id])
        view (view-key views)]
    [view]))
