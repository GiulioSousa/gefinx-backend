-- A listagem de transações filtra por usuario_id e ordena por (data_transacao DESC, id DESC),
-- mas o único índice que a servia cobria apenas usuario_id: o banco usava o índice para
-- filtrar e ordenava o resultado inteiro depois, a cada leitura. O composto atende filtro e
-- ordenação na mesma passagem, e é ele que sustenta a paginação — sem ele, pedir a segunda
-- página continuaria custando a ordenação do histórico completo, que é justamente o que a
-- paginação existe para evitar.
--
-- O id entra como última coluna porque é o desempate da ordenação. Duas transações da mesma
-- data, sem desempate estável, podem trocar de posição entre uma consulta e a seguinte — e aí
-- a mesma linha aparece em duas páginas, ou some das duas. O índice acompanha a ordenação da
-- consulta para que o desempate custe o mesmo que o resto.
CREATE INDEX idx_transacoes_usuario_data ON transacoes (usuario_id, data_transacao DESC, id DESC);

-- idx_transacoes_usuario passa a ser redundante. Um índice composto atende qualquer consulta
-- que filtre por um prefixo à esquerda de suas colunas, e usuario_id é exatamente esse prefixo:
-- tudo que o índice simples respondia, o novo responde. Mantê-lo custaria escrita em toda
-- inserção e espaço em disco para não responder a nada de novo.
DROP INDEX idx_transacoes_usuario;
