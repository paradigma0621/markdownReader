# Markdown Reader 📝

Bem-vindo ao **Markdown Reader** — um apresentador de documentos Markdown
escrito em **Java 21** + **JavaFX**, com renderização totalmente estilizada.

> Abra um arquivo com `Ctrl+O`, arraste um `.md` para a janela, ou alterne o
> tema com `Ctrl+T`. Este próprio documento é um exemplo dos recursos suportados.

---

## ✨ Recursos

- Conversão **GitHub Flavored Markdown** → HTML estilizado
- Tema **claro** e **escuro** (`Ctrl+T`)
- **Sumário** navegável na barra lateral (`Ctrl+B`)
- **Zoom** com `Ctrl + scroll`, `Ctrl++` / `Ctrl+-` / `Ctrl+0`
- **Recarregamento automático** ao salvar o arquivo no editor
- Arrastar e soltar arquivos

## 🧩 Formatação básica

Texto em *itálico*, **negrito**, ***ambos***, ~~riscado~~, `código inline`
e até <kbd>Ctrl</kbd> + <kbd>O</kbd> com teclas.

Links: [site do projeto](https://example.com) e autolinks https://github.com.

## ✅ Listas de tarefas

- [x] Renderizar Markdown
- [x] Aplicar estilo
- [ ] Conquistar o mundo

## 🔢 Lista ordenada

1. Primeiro passo
2. Segundo passo
   1. Sub-item A
   2. Sub-item B
3. Terceiro passo

## 💬 Citação

> "Qualquer tecnologia suficientemente avançada é indistinguível de mágica."
> — Arthur C. Clarke

## 📊 Tabela

| Recurso            | Atalho      | Status |
|--------------------|-------------|:------:|
| Abrir arquivo      | `Ctrl+O`    |   ✅   |
| Alternar tema      | `Ctrl+T`    |   ✅   |
| Mostrar sumário    | `Ctrl+B`    |   ✅   |
| Aumentar zoom      | `Ctrl++`    |   ✅   |

## 💻 Bloco de código

```java
public record Greeting(String name) {
    public String message() {
        return "Olá, %s! Bem-vindo ao Markdown Reader.".formatted(name);
    }
}
```

```bash
# Executar a aplicação
mvn clean javafx:run
```

## 📝 Nota de rodapé

O flexmark suporta notas de rodapé.[^1]

[^1]: Esta é a nota de rodapé renderizada no fim do documento.

---

Feito com ☕ e JavaFX.
