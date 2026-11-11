#!/usr/bin/env bash
# Confere as mensagens de commit contra a convenção do GeFinX: Conventional Commits, com o
# escopo sendo um bounded context da lista em escopos-de-commit.txt, e o assunto sem acento.
#
# O release-please monta o CHANGELOG e a seção "Versões" do README a partir dessas mensagens:
# um commit fora do padrão sumiria deles sem aviso. Commits de merge ficam de fora — quem os
# escreve é o GitHub —, e o "Revert ..." do botão do GitHub é aceito.
#
# Uso: conferir-commits.sh <base> <topo> [título do pull request]
#      Sem base (zeros, como no primeiro push de uma branch), confere só o topo.
set -euo pipefail

base="${1:-}"
topo="${2:?informe o commit do topo}"
titulo="${3:-}"
dir="$(cd "$(dirname "$0")" && pwd)"

escopos="$(grep -vE '^\s*(#|$)' "$dir/escopos-de-commit.txt" | tr -d '\r' | paste -sd '|')"
tipos='feat|fix|perf|refactor|docs|test|build|ci|style|chore|revert'
padrao="^(${tipos})(\((${escopos})\))?!?: [^ ]"

falhas=0
conferir() {
  local origem="$1" assunto="$2"
  if printf '%s' "$assunto" | grep -qE '^Revert "'; then
    return
  fi
  if ! printf '%s' "$assunto" | grep -qE "$padrao"; then
    echo "::error::${origem} fora do padrão: ${assunto}"
    falhas=$((falhas + 1))
    return
  fi
  if printf '%s' "$assunto" | LC_ALL=C grep -q '[^ -~]'; then
    echo "::error::${origem} com acento ou caractere fora do ASCII no assunto: ${assunto}"
    falhas=$((falhas + 1))
  fi
}

if [ -z "$base" ] || [[ "$base" =~ ^0+$ ]]; then
  intervalo=("-n" "1" "$topo")
else
  intervalo=("${base}..${topo}")
fi

quantos=0
while IFS= read -r linha; do
  [ -z "$linha" ] && continue
  conferir "commit ${linha%% *}" "${linha#* }"
  quantos=$((quantos + 1))
done < <(git log --no-merges --format='%h %s' "${intervalo[@]}")

if [ -n "$titulo" ]; then
  conferir "título do pull request" "$titulo"
fi

echo "Conferidos: ${quantos} commit(s)${titulo:+ e o título do pull request}. Fora da convenção: ${falhas}."
if [ "$falhas" -gt 0 ]; then
  echo "Formato: tipo(escopo): assunto sem acento — tipos: ${tipos//|/, }; escopos: ${escopos//|/, }."
  exit 1
fi
