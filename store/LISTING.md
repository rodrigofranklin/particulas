# Ficha da Google Play — Partículas

Tudo o que você precisa colar no Play Console. Os arquivos gráficos estão nesta pasta.

## Arquivos

| Item | Arquivo | Requisito da Play |
|---|---|---|
| Pacote do app | `Particulas-release.aab` (raiz do projeto, gerado por `./gradlew bundleRelease`) | `.aab` assinado |
| Ícone | `icon-512.png` | 512×512 PNG |
| Gráfico de destaque | `feature-graphic-1024x500.png` | 1024×500 |
| Capturas (telefone) | `screenshots/01..05-*.png` | 2 a 8, 9:16, ≥320 px |
| Política de privacidade | https://github.com/rodrigofranklin/particulas/blob/main/PRIVACY.md | URL pública |

> As capturas foram renderizadas pelo protótipo (mesma física). Se quiser,
> substitua por capturas reais do seu celular (botão liga/desliga + volume −).

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

Partículas é um brinquedo visual feito para crianças (e para quem gosta de ver coisas bonitas): milhares de partículas coloridas que flutuam em correntes suaves e reagem a cada toque.

• Use até 10 dedos ao mesmo tempo — cada um vira um redemoinho com a sua própria cor.
• Segure o dedo parado: a cor gira como um arco-íris, o redemoinho "respira", solta pulsos de choque e inverte o giro, enquanto um chafariz de faíscas sai em espiral.
• Arraste: uma fita de luz segue o dedo e faíscas voam pelo caminho.
• Solte: as partículas explodem com a cor do dedo.

Feito para as mãos pequenas:
• Tela cheia, sem menus, sem botões, sem nada para apertar por engano.
• Fixação de tela: com a opção "Fixar tela" ativada nas configurações do Android, os botões Início e Recentes ficam bloqueados enquanto a criança brinca. Para sair, basta apertar qualquer tecla física (voltar ou volume).
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
