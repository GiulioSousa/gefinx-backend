// Atualiza a seção "Versões" do README a partir do CHANGELOG.md.
//
// Mostra as versões mais recentes por completo (descrição + itens) e, das mais
// antigas, só as que trouxeram mudanças incompatíveis. O histórico inteiro fica
// no CHANGELOG. Roda no workflow do release-please, dentro do PR de release, e
// também à mão: `node .github/scripts/versoes-readme.mjs`.
//
// Uso: node versoes-readme.mjs [CHANGELOG.md] [README.md]

import { readFileSync, writeFileSync } from 'node:fs';

const RECENTES = 3;
const MAX_ITENS = 8;
const INICIO = '<!-- versoes:inicio -->';
const FIM = '<!-- versoes:fim -->';
const INCOMPATIVEL = /BREAKING CHANGES|mudanças incompatíveis/i;

const [caminhoChangelog = 'CHANGELOG.md', caminhoReadme = 'README.md'] = process.argv.slice(2);

const changelog = readFileSync(caminhoChangelog, 'utf8').replace(/\r\n/g, '\n');
const readmeOriginal = readFileSync(caminhoReadme, 'utf8');
const quebra = readmeOriginal.includes('\r\n') ? '\r\n' : '\n';
const readme = readmeOriginal.replace(/\r\n/g, '\n');

function lerVersoes(texto) {
  const blocos = texto.split(/\n(?=## )/).filter((b) => b.startsWith('## '));
  return blocos.map((bloco) => {
    const [cabecalho, ...linhas] = bloco.split('\n');
    const m = cabecalho.match(/^##\s+\[?v?(\d+\.\d+\.\d+[^\]\s]*)\]?(?:\([^)]*\))?\s*\((\d{4})-(\d{2})-(\d{2})\)/);
    if (!m) throw new Error(`Cabeçalho de versão não reconhecido: ${cabecalho}`);
    const [, numero, ano, mes, dia] = m;

    const descricao = [];
    const secoes = [];
    let atual = null;
    for (const linha of linhas) {
      if (linha.startsWith('### ')) {
        atual = { titulo: linha.slice(4).trim(), itens: [] };
        secoes.push(atual);
      } else if (/^[*-]\s/.test(linha)) {
        if (atual) atual.itens.push(limparItem(linha));
      } else if (!atual) {
        descricao.push(linha);
      }
    }
    return {
      numero,
      data: `${dia}/${mes}/${ano}`,
      descricao: descricao.join('\n').trim(),
      secoes,
      incompativel: secoes.some((s) => INCOMPATIVEL.test(s.titulo)),
    };
  });
}

// Tira os links de commit e de issue que o release-please põe no fim de cada item.
function limparItem(linha) {
  return '- ' + linha
    .replace(/^[*-]\s+/, '')
    .replace(/\s*\(\[[0-9a-f]{7,40}\]\([^)]*\)\)/g, '')
    .replace(/,?\s*closes \[#\d+\]\([^)]*\)/gi, '')
    .trim();
}

function renderizar(versoes) {
  if (versoes.length === 0) return '_Nenhuma versão publicada ainda._';

  const partes = [
    `Versão atual: **${versoes[0].numero}**. Histórico completo no [CHANGELOG](CHANGELOG.md).`,
  ];

  for (const v of versoes.slice(0, RECENTES)) {
    partes.push(`### ${v.numero} — ${v.data}`);
    if (v.descricao) partes.push(v.descricao);

    let restantes = MAX_ITENS;
    let omitidos = 0;
    for (const s of v.secoes) {
      const visiveis = s.itens.slice(0, Math.max(restantes, 0));
      omitidos += s.itens.length - visiveis.length;
      restantes -= visiveis.length;
      const titulo = s.titulo.replace(/BREAKING CHANGES/i, 'Mudanças incompatíveis');
      if (visiveis.length) partes.push(`**${titulo}**\n\n${visiveis.join('\n')}`);
    }
    if (omitidos > 0) partes.push(`_E mais ${omitidos} ${omitidos === 1 ? 'item' : 'itens'} no [CHANGELOG](CHANGELOG.md)._`);
  }

  const antigasIncompativeis = versoes.slice(RECENTES).filter((v) => v.incompativel);
  if (antigasIncompativeis.length) {
    const lista = antigasIncompativeis.map((v) => `${v.numero} (${v.data})`).join(', ');
    partes.push(`Versões anteriores com mudanças incompatíveis: ${lista}. Detalhes no [CHANGELOG](CHANGELOG.md).`);
  }

  return partes.join('\n\n');
}

const ini = readme.indexOf(INICIO);
const fim = readme.indexOf(FIM);
if (ini === -1 || fim === -1 || fim < ini) {
  console.error(`README sem os marcadores ${INICIO} e ${FIM}.`);
  process.exit(1);
}

const novo =
  readme.slice(0, ini + INICIO.length) +
  '\n\n' + renderizar(lerVersoes(changelog)) + '\n\n' +
  readme.slice(fim);

if (novo === readme) {
  console.log('README já está atualizado.');
} else {
  writeFileSync(caminhoReadme, novo.replace(/\n/g, quebra));
  console.log('Seção "Versões" do README atualizada.');
}
