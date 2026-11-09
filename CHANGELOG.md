# Changelog

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
