# Markdown Reader 📝

Apresentador estilizado de documentos **Markdown**, escrito em **Java 21** + **JavaFX 21**.
Converte Markdown (GitHub Flavored) em HTML bonito e o exibe num `WebView`, com tema
claro/escuro, sumário navegável, zoom e recarregamento automático.

![Java](https://img.shields.io/badge/Java-21-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue)
![Maven](https://img.shields.io/badge/Maven-3.9-red)

## ✨ Recursos

| Recurso | Descrição |
|---|---|
| **GFM completo** | tabelas, listas de tarefas, strikethrough, notas de rodapé, autolinks, emojis, tipografia |
| **Tema claro/escuro** | alternável em tempo real (`Ctrl+T`), preferência persistida |
| **Sumário navegável** | extraído dos títulos; clique para rolar até a seção |
| **Zoom** | `Ctrl + scroll`, `Ctrl++`, `Ctrl+-`, `Ctrl+0` (reset) |
| **Recarregamento automático** | reabre o arquivo ao detectar alteração no disco |
| **Arrastar e soltar** | solte um `.md` na janela para abrir |
| **Syntax highlighting** | via highlight.js embutido (funciona offline) |
| **Botão "voltar ao topo"** | aparece ao rolar |

## 🚀 Como executar

Requer **JDK 21** e **Maven**. A forma mais simples:

```bash
./run.sh
```

O script detecta automaticamente um JDK 21 instalado via SDKMAN. Alternativamente:

```bash
# Aponte o JAVA_HOME para um JDK 21 e rode:
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.9-amzn"
mvn clean javafx:run
```

Abrir um arquivo direto pela linha de comando:

```bash
mvn javafx:run -Djavafx.args="caminho/para/arquivo.md"
```

## 📦 Gerar JAR executável

```bash
mvn clean package
java -jar target/markdown-reader-1.0.0.jar
```

> O JAR "fat" inclui as dependências Java, mas **não** os binários nativos do
> JavaFX. Para rodar o JAR é necessário um JDK que inclua o JavaFX, ou apontar o
> `--module-path` para o SDK do JavaFX. Para uso geral, prefira `mvn javafx:run`.

## ⌨️ Atalhos

| Atalho | Ação |
|---|---|
| `Ctrl+O` | Abrir documento |
| `Ctrl+R` | Recarregar |
| `Ctrl+T` | Alternar tema |
| `Ctrl+B` | Mostrar/ocultar sumário |
| `Ctrl++` / `Ctrl+-` | Zoom in / out |
| `Ctrl+0` | Redefinir zoom |
| `Ctrl + scroll` | Zoom |

## 🏗️ Estrutura

```
src/main/java/com/markdownreader/
├── Launcher.java                  ponto de entrada
├── App.java                       Application JavaFX (Scene/Stage)
├── markdown/
│   ├── MarkdownRenderer.java      Markdown -> HTML (flexmark) + extração de títulos
│   ├── HtmlPageBuilder.java       embrulha o HTML com CSS + highlight.js
│   ├── RenderResult.java          html + títulos
│   └── Heading.java               título do sumário
└── ui/
    ├── MainView.java              toolbar, sidebar, WebView, ações
    ├── FileWatcher.java           recarregamento automático
    └── Theme.java                 tema claro/escuro

src/main/resources/
├── css/app.css                    estilo da interface JavaFX
├── web/markdown.css               estilo do conteúdo renderizado
└── sample/welcome.md              documento de boas-vindas
```

## 🛠️ Tecnologias

- [JavaFX](https://openjfx.io/) 21 — interface e `WebView`
- [flexmark-java](https://github.com/vsch/flexmark-java) — parser/render Markdown
- [highlight.js](https://highlightjs.org/) — realce de sintaxe (embutido, offline)
- [twemoji](https://github.com/jdecked/twemoji) — emojis SVG coloridos (embutidos, offline)

## 📄 Licença

MIT
