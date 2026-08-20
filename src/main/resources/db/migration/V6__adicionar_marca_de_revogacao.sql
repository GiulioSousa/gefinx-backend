-- Instante a partir do qual as sessões do usuário valem. Um token emitido antes dele é
-- recusado, ainda que a assinatura e o prazo estejam corretos.
--
-- Sem isso não havia como invalidar sessão alguma: o "Sair" da tela apaga só a cópia
-- local do token, e quem o tivesse continuaria entrando até expirar, 24h depois.

ALTER TABLE usuarios ADD COLUMN sessoes_validas_apos TIMESTAMP;

-- As contas existentes recebem a própria data de criação: tudo que foi emitido depois
-- dela continua valendo, e ninguém é deslogado pela migration.
UPDATE usuarios SET sessoes_validas_apos = criado_em WHERE sessoes_validas_apos IS NULL;

-- Preenchida sempre. Permitir nulo obrigaria todo ponto de comparação a tratar um caso
-- de "nunca revogado", e é o tipo de ramo que se esquece justamente no lugar errado.
ALTER TABLE usuarios ALTER COLUMN sessoes_validas_apos SET NOT NULL;
