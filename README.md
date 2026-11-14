# F1Pocket

App Android (Kotlin + Jetpack Compose) de Fórmula 1. Não depende do site: fala direto com as APIs.

| Fonte | Para quê |
|---|---|
| Jolpica-F1 (`api.jolpi.ca`) | Calendário, classificação, resultados, histórico, recordes |
| OpenF1 (`api.openf1.org`) | Replay, rádios, stints, pit stops, direção de prova (bloqueia o acesso grátis de 30 min antes a 30 min depois de cada sessão) |
| Feed oficial da F1 (`livetiming.formula1.com`) | Tela Ao vivo, direto do celular |
| TracingInsights (GitHub) | Mapas das pistas e telemetria |
| Open-Meteo | Previsão do tempo |

## Compilar pelo GitHub (sem instalar nada)

1. Crie um repositório novo no GitHub (pode ser privado).
2. Envie **todo** o conteúdo desta pasta, inclusive a pasta oculta `.github`:
   ```
   git init
   git add .
   git commit -m "F1Pocket"
   git branch -M main
   git remote add origin https://github.com/SEU_USUARIO/f1pocket.git
   git push -u origin main
   ```
   No upload pelo navegador a pasta `.github` costuma ficar de fora. Nesse caso, crie o arquivo `.github/workflows/build.yml` pelo botão "Add file" e cole o conteúdo.
3. Abra a aba **Actions**. O build "Build APK" começa sozinho e leva de 5 a 10 minutos.
4. Quando ficar verde, abra a execução e baixe o artefato **F1Pocket-N** (N é o número do build) em "Artifacts". Ele vem em .zip, com o `.apk` dentro.
5. Passe o `.apk` para o celular e instale. O Android vai pedir para permitir a instalação de fontes desconhecidas.

Se o build falhar, abra o passo "Build" na execução, copie as linhas com `e:` (erros do Kotlin) e me mande.

## Observações

- **Assinatura:** o arquivo `app/f1pocket.jks` (senha `f1pocket123`) está no repositório para que todo build saia com a mesma assinatura e o app atualize por cima sem desinstalar. Se o repositório for público e você quiser trocar a chave, gere outra com `keytool` e ajuste `app/build.gradle.kts`. Ao trocar a chave, o app precisa ser desinstalado uma vez.
- **Onde assistir:** a tabela de emissoras por país fica em `app/src/main/java/com/f1pocket/app/data/Static.kt` (`BROADCAST`). Revise a cada temporada.
- **Transcrição dos rádios:** em Ajustes › "URL das transcrições", aponte para a pasta de JSONs que o workflow do seu site já gera, por exemplo
  `https://raw.githubusercontent.com/SEU_USUARIO/SEU_REPO_DO_SITE/main/data/radios/`
  Rádios que não estiverem lá podem ser transcritos na hora com uma chave da OpenAI (Whisper). Isso é opcional e pago por uso, e a chave fica só no celular.
- **Mapas:** cada pista é montada a partir da telemetria da classificação e fica salva no aparelho. Em Circuitos há um botão para baixar todas de uma vez.
- **Versão:** o `versionCode` é o número da execução do Actions, então cada build novo instala por cima do anterior.
- **Idiomas:** português, inglês e espanhol estão traduzidos. Os outros idiomas da lista aparecem em inglês.

## Estrutura

```
app/src/main/java/com/f1pocket/app/
  data/        APIs, cache, modelos, tabelas fixas, mapas, feed ao vivo
  notify/      alarmes das sessões e notificações
  ui/          tema, componentes, mapa da pista, navegação
  ui/screens/  Home, Ao vivo/Replay, Classificação/Temporada/Circuitos/Corrida, Mais/Ajustes/Recordes, Ferramentas
```
