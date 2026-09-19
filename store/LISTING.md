# Ficha da Google Play — Partículas

Tudo o que você precisa colar no Play Console. Os arquivos gráficos estão nesta pasta.

## Arquivos

| Item | Arquivo | Requisito da Play |
|---|---|---|
| Pacote do app | `Particulas-release.aab` (raiz do projeto, gerado por `./gradlew bundleRelease`) | `.aab` assinado |
| Ícone | `icon-512.png` | 512×512 PNG |
| Gráfico de destaque | `feature-graphic-1024x500.png` | 1024×500 |
| Capturas (telefone) | `screenshots/01..07-*.png` (capturadas no emulador Pixel 6, 1080×2400) | 2 a 8, 9:16, ≥320 px |
| Política de privacidade | https://github.com/rodrigofranklin/particulas/blob/main/PRIVACY.md | URL pública |

> As capturas são do app real rodando no emulador. Se quiser, substitua por
> capturas do seu celular (botão liga/desliga + volume −).

## Textos

**Nome do app** (30 caracteres máx.)

```
Partículas
```

**Descrição breve** (80 caracteres máx.)

```
Partículas de luz que reagem aos seus dedos. Sem anúncios, sem internet.
```

**Descrição completa** (4000 caracteres máx.)

```
Encoste os dedos na tela e brinque com a luz.

Partículas é um brinquedo visual feito para crianças (e para quem gosta de ver coisas bonitas). Abre num menu com três botões grandes e três modos:

✨ PARTÍCULAS — milhares de pontinhos de luz flutuando em correntes suaves. Use até 10 dedos ao mesmo tempo: cada um vira um redemoinho com a sua própria cor. Segure o dedo parado e a cor gira como um arco-íris, o redemoinho "respira", solta pulsos e um chafariz de faíscas. Arraste e uma fita de luz segue o dedo. Solte e tudo explode.

🌊 FLUIDO — um líquido colorido de verdade (simulação de fluido). Arraste e o dedo empurra o líquido, deixando rastros que viram cogumelos e redemoinhos. Segure e nasce uma espiral de cor girando. Cores fortes, saturadas, intensas.

🌙 LUZ NO ESCURO — a tela fica preta. Cada dedo solta um monte de coisas brilhantes: faíscas, fumaça colorida, rajadas de vento, estrelas, bolhas, borboletas batendo asa e corações. Quanto mais rápido o dedo, mais coisas saem. Quando os toques param, tudo se apaga e a tela volta ao escuro.

Feito para as mãos pequenas:
• Tela cheia, sem botões dentro da brincadeira, nada para apertar por engano. Não gira.
• Fixação de tela: com a opção "Fixar tela" ativada nas configurações do Android, os botões Início e Recentes ficam bloqueados enquanto a criança brinca. O botão Voltar leva ao menu e, no menu, fecha o app.
• A tela não apaga sozinha.

Sem anúncios. Sem compras. Sem internet. Sem coleta de dados. Nenhuma permissão.

Código aberto (MIT): github.com/rodrigofranklin/particulas
```

## Configurações no Play Console

| Pergunta | Resposta sugerida |
|---|---|
| Tipo | App |
| Gratuito ou pago | Gratuito |
| Categoria | Entretenimento |
| Tags | Simulação, Casual |
| Anúncios | Não contém anúncios |
| Classificação de conteúdo (IARC) | Questionário: responda "não" para tudo → **Livre / L** |
| Público-alvo | Marque as faixas infantis que quiser (ex.: 5 e menos, 6–8, 9–12) **e** as adultas. Como não há coleta de dados nem anúncios, o app cumpre a Política para Famílias. |
| Segurança dos dados | "Não coleta nem compartilha dados do usuário". Nenhuma criptografia necessária (nada é transmitido). |
| Política de privacidade | URL do PRIVACY.md acima |
| Permissões | Nenhuma (o formulário não deve pedir justificativas) |
| App governamental / Notícias / Saúde / Financeiro | Não |
| Acessibilidade / Apps para crianças ("Aprovado por professores") | Opcional |

## Passo a passo

1. **Conta de desenvolvedor**: https://play.google.com/console → taxa única de
   US$ 25 → verificação de identidade (documento, alguns dias). Conta pessoal
   exige também verificar telefone e e-mail.
2. **Criar app** → nome "Partículas", idioma padrão Português (Brasil), App,
   Gratuito. Aceite as declarações.
3. **Painel → "Configurar o app"**: preencha cada item da tabela acima
   (privacidade, anúncios, acesso ao app = "todo o acesso está disponível sem
   restrição", classificação, público-alvo, segurança dos dados, categoria,
   detalhes de contato).
4. **Presença na loja → Ficha principal**: cole os textos, envie
   `icon-512.png`, `feature-graphic-1024x500.png` e as capturas.
5. **Teste fechado obrigatório** (contas pessoais criadas após nov/2023):
   Teste → Teste fechado → criar versão → enviar o `.aab` → adicionar uma lista
   de e-mails com **pelo menos 12 testadores** → publicar. Os testadores
   precisam aceitar o convite e manter o app instalado por **14 dias**.
   Depois disso, aparece "Solicitar acesso à produção" no painel.
6. **Produção**: Produção → criar versão → o mesmo `.aab` → notas da versão →
   revisar → "Iniciar lançamento". A análise costuma levar de horas a alguns
   dias.

### Assinatura pelo Google (Play App Signing)

Ao enviar o primeiro `.aab`, o Console ativa o *Play App Signing*: o Google
gera a chave definitiva e a sua `particulas.jks` vira a **chave de upload**.
Guarde o arquivo `app/keystore/particulas.jks` com segurança — sem ele você
não consegue enviar atualizações (é possível pedir redefinição, mas dá
trabalho). Ele **não** está no repositório de propósito.

### Atualizações futuras

Em `app/build.gradle.kts`, aumente `versionCode` (+1) e ajuste `versionName`,
rode `./gradlew bundleRelease` e envie o novo `.aab`.
