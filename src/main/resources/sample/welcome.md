# Markdown Reader 📝

Welcome to **Markdown Reader** — a styled Markdown document viewer
written in **Java 21** + **JavaFX**, with fully styled rendering.

> Open a file with `Ctrl+O`, drag a `.md` onto the window, or toggle the
> theme with `Ctrl+T`. This document itself is an example of the supported features.

---

## ✨ Features

- **GitHub Flavored Markdown** → styled HTML conversion
- **Light** and **dark** theme (`Ctrl+T`)
- Navigable **Table of Contents** in the sidebar (`Ctrl+B`)
- **Zoom** with `Ctrl + scroll`, `Ctrl++` / `Ctrl+-` / `Ctrl+0`
- **Auto-reload** when saving the file in your editor
- Drag and drop files

## 🧩 Basic formatting

Text in *italic*, **bold**, ***both***, ~~strikethrough~~, `inline code`
and even <kbd>Ctrl</kbd> + <kbd>O</kbd> with key tags.

Links: [project site](https://example.com) and autolinks https://github.com.

## ✅ Task lists

- [x] Render Markdown
- [x] Apply styling
- [ ] Conquer the world

## 🔢 Ordered list

1. First step
2. Second step
   1. Sub-item A
   2. Sub-item B
3. Third step

## 💬 Blockquote

> "Any sufficiently advanced technology is indistinguishable from magic."
> — Arthur C. Clarke

## 📊 Table

| Feature            | Shortcut    | Status |
|--------------------|-------------|:------:|
| Open file          | `Ctrl+O`    |   ✅   |
| Toggle theme       | `Ctrl+T`    |   ✅   |
| Show TOC           | `Ctrl+B`    |   ✅   |
| Zoom in            | `Ctrl++`    |   ✅   |

## 💻 Code block

```java
public record Greeting(String name) {
    public String message() {
        return "Hello, %s! Welcome to Markdown Reader.".formatted(name);
    }
}
```

```bash
# Run the application
mvn clean javafx:run
```

## 📝 Footnote

flexmark supports footnotes.[^1]

[^1]: This is the footnote rendered at the end of the document.

---

Made with ☕ and JavaFX.
