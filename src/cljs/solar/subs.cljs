(ns solar.subs
  (:require
    [re-frame.core :as rf]))

;; SUBS
(def subss [::cache-custo
            ::cache-cidade
            ::paineis
            ::pendencias-paineis
            ::cache-aumento-anual
            ::nome-cliente
            ::cpf-cliente
            ::tel-cliente
            ::transformadores
            ::preview-selo
            ::multi-geradores?
            ::carrinho
            ::erro-cpf
            ::orcamentos
            ::gestor?
            ::filtros-orcado
            ::nome-vendedor
            ::processando
            ::start-date
            ::end-date
            ::cache-login
            ::cache-poupanca
            ::cache-kwp-max
            ::cache-kwp-min
            ::cache-poupanca-ano
            ::parametros
            ::valor-kwp
            ::cache-cpf-cliente
            ::cache-telefone-cliente
            ::cache-palavra
            ::cache-valor-filtro
            ::cache-cpf-vendedor
            ::cache-kwh
            ::id
            ::cache-nome-vendedor
            ::cache-painel
            ::nome-painel
            ::cache-texto-painel
            ::cache-nome-cliente
            ::cache-percent-servico
            ::cache-percentual-solar
            ::view-id
            ::view-param
            ::nome-consultado
            ::feedback
            ::historico
            ::url-painel
            ::produtos
            ::detalhes
            ::filtros-orcamento
            ::por-pagina])

(doall (map
         #(rf/reg-sub
            %
            (fn [db _]
              ((keyword (name %)) db)))
         subss))
