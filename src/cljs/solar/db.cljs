(ns solar.db)

(defonce KWP_MIN_INIT 0)
(defonce KWP_MAX_INIT 900)
(defn- ranges-kwp [min max]
  (str min "-" max))

(def db-inicial
  {:feedback {}
   :por-pagina 5
   :nome-cliente ""
   :cpf-cliente ""
   :cache-custo "0,82"
   :nome-vendedor "VENDEDOR TESTE"
   :carrinho []
   :start-date (atom false)
   :end-date (atom false)
   :gestor? false
   :cache-kwp-min KWP_MIN_INIT
   :cache-kwp-max KWP_MAX_INIT
   :cache-percent-servico "10,0"
   :view-id :view-gestao
   :cache-cidade "Campo Grande"
   :data-figura (.getTime (js/Date.))
   :cache-nome-cliente "Nome do Cliente"
   :cache-cpf-cliente nil
   :cache-palavra ""
   :filtros-orcado {:logins "todos"
                    :telhado "todos"
                    :marca "todos"
                    :painel "todos"}
   :filtros-orcamento {:telhado "todos"
                       :painel "todos"
                       :palavra ""
                       :filtro-kwp (ranges-kwp KWP_MIN_INIT KWP_MAX_INIT)
                       :marca "todos"}
   :orcamentos []})
