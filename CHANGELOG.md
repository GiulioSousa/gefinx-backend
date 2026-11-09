# Changelog

## [0.2.0](https://github.com/GiulioSousa/gefinx-backend/compare/v0.1.0...v0.2.0) (2026-11-09)

Custo diário na meta, e uma meta que acompanha cada lançamento. A meta diária passa a somar o
gasto do dia a dia: a média dos últimos 30 dias de cada categoria escolhida, como alimentação e
combustível, dividida pelos dias em que houve gasto nela. Ela deixa de ficar parada durante o dia e
é recalculada a cada transação, e o plano passa a informar as receitas de hoje no lugar do ganho do
dia. Traz a migration `V18` e muda a resposta de `/api/planejamento`: requer o frontend 0.2.0.

### ⚠ BREAKING CHANGES

* **planejamento:** GET /api/planejamento deixa de ter saldoDeOntem, ganhoDeHoje e restanteHoje, e ganha receitasDeHoje e custoDiario.

### Funcionalidades

* **db:** cria a tabela das categorias do custo diario ([87d14e1](https://github.com/GiulioSousa/gefinx-backend/commit/87d14e177be9de197bd20294bd2ef66ed7173f37))
* **planejamento:** calcula a media do custo diario pelos dias com gasto ([75e30b1](https://github.com/GiulioSousa/gefinx-backend/commit/75e30b192873e12403a239620a5590381d5b140d))
* **planejamento:** soma o custo diario a meta e a faz acompanhar cada lancamento ([d347f25](https://github.com/GiulioSousa/gefinx-backend/commit/d347f2580f5c7d3fcc602faf9f1edd7bbaf4bae5))

## [0.1.0](https://github.com/GiulioSousa/gefinx-backend/releases/tag/v0.1.0) (2026-11-09)

Primeira versão marcada do GeFinX. Reúne a base construída até aqui: API REST multiusuário em
Spring Boot 4 organizada por bounded context, PostgreSQL com credenciais separadas para migração e
execução, e o planejamento de despesas com meta diária de ganhos.

### Funcionalidades

* **usuarios:** login por nome de usuário com JWT, troca de senha, encerramento de todas as sessões e limite de tentativas por conta e por origem
* **financas:** contas com saldo individual e consolidado, transferência entre contas e bloqueio de saldo negativo em qualquer dia
* **transacoes:** listagem paginada e filtrável, com o tipo da transação amarrado ao da categoria
* **saldo:** recorte do saldo por período com `dataInicio` e `dataFim`
* **planejamento:** despesas planejadas com meta diária de ganhos, pagamento que lança a transação, recorrência semanal ou mensal, parcelamento, etiquetas, feriados e dias sem trabalho
* **financas:** relatório mensal de receitas, despesas e categorias
* **db:** roles separadas para o Flyway e para a aplicação, índices e restrições de integridade no banco
* **operacao:** health check em porta própria, trilha de auditoria e formato uniforme de erro
