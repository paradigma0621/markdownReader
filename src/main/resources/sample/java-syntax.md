# Java — Amostra de Realce de Sintaxe

Este documento exercita todos os tipos de token que o **highlight.js** colore em
código Java: palavras-chave, tipos, strings, números, comentários, anotações,
literais, operadores e muito mais. Útil para conferir o tema claro/escuro e a
largura de blocos de código longos.

---

## 1. Estrutura, imports e comentários

```java
// Comentário de linha única
/*
 * Comentário de bloco em várias linhas.
 * Documenta a intenção do arquivo.
 */
/**
 * Javadoc com {@code código inline}, {@link java.util.List} e tags.
 *
 * @author  Lucas Favaro
 * @version 1.0
 * @since   17
 */
package com.exemplo.realce;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static java.lang.Math.PI;
import static java.lang.Math.max;
```

---

## 2. Palavras-chave, modificadores e tipos primitivos

```java
public abstract class Veiculo implements Comparable<Veiculo> {

    // Constantes (literais numéricos de vários formatos)
    public static final int    MAX_RODAS      = 18;
    private static final long   ID_BASE        = 9_223_372_036_854_775_807L;
    protected static final double GRAVIDADE    = 9.81d;
    private static final float  FATOR          = 1.5f;
    private static final int    HEX            = 0xFF_EC_DE;
    private static final int    OCTAL          = 0_777;
    private static final int    BINARIO        = 0b1010_0101;
    private static final char   INICIAL        = 'V';
    private static final boolean ATIVO         = true;

    // Tipos primitivos e wrappers
    private byte   nivel;
    private short  ano;
    private int    rodas;
    private long   chassi;
    private float  consumo;
    private double preco;
    private boolean ligado;
    private volatile transient String estadoInterno;

    public abstract void mover();
}
```

---

## 3. Strings, text blocks e escapes

```java
public class Textos {

    String simples   = "Olá, \"mundo\"!\n\tTabulado.";
    String unicode   = "Acentuação e emoji 🚀";
    char   barra     = '\\';

    // Text block (multi-linha) — Java 15+
    String json = """
            {
                "nome": "Markdown Reader",
                "versao": 1.0,
                "offline": true,
                "temas": ["claro", "escuro"]
            }
            """;

    String sql = """
            SELECT id, nome, preco
            FROM   produtos
            WHERE  preco > 100.0
            ORDER  BY nome ASC;
            """;
}
```

---

## 4. Anotações, genéricos e métodos

```java
@FunctionalInterface
interface Transformacao<T, R> {
    R aplicar(T entrada);
}

@Deprecated(since = "2.0", forRemoval = true)
@SuppressWarnings({"unchecked", "rawtypes"})
public class Repositorio<T extends Comparable<? super T>> {

    private final List<T> itens;

    @SafeVarargs
    public Repositorio(T... iniciais) {
        this.itens = new java.util.ArrayList<>(List.of(iniciais));
    }

    @Override
    public String toString() {
        return "Repositorio" + itens;
    }

    public <R> List<R> mapear(Transformacao<? super T, ? extends R> fn) {
        return itens.stream()
                    .map(fn::aplicar)
                    .collect(Collectors.toList());
    }
}
```

---

## 5. Controle de fluxo, switch expression e operadores

```java
public final class Fluxo {

    enum Dia { SEG, TER, QUA, QUI, SEX, SAB, DOM }

    static String tipoDeDia(Dia dia) {
        // switch expression com seta e yield (Java 14+)
        return switch (dia) {
            case SAB, DOM -> "fim de semana";
            case SEX      -> {
                String s = "quase ";
                yield s + "sexta";
            }
            default       -> "dia útil";
        };
    }

    static int classificar(int n) {
        if (n > 0 && n % 2 == 0) {
            return 1;
        } else if (n < 0 || n == Integer.MIN_VALUE) {
            return -1;
        }

        int soma = 0;
        for (int i = 0; i <= n; i++) {
            soma += i << 1;       // deslocamento
        }
        while (soma > 100) {
            soma >>= 1;
        }
        do {
            soma--;
        } while (soma > 0);

        boolean teste = (soma == 0) ? true : false;
        return teste ? 0 : soma & 0xF;
    }
}
```

---

## 6. Records, sealed, pattern matching e lambdas

```java
public sealed interface Forma permits Circulo, Retangulo {}

public record Circulo(double raio)              implements Forma {}
public record Retangulo(double base, double alt) implements Forma {}

public class Geometria {

    public static double area(Forma forma) {
        // Pattern matching em switch (Java 21)
        return switch (forma) {
            case Circulo c              -> Math.PI * c.raio() * c.raio();
            case Retangulo(var b, var h) -> b * h;        // record pattern
        };
    }

    public static void main(String[] args) {
        var formas = List.of(new Circulo(2.0), new Retangulo(3.0, 4.0));

        formas.stream()
              .map(Geometria::area)
              .filter(a -> a > 5.0)
              .sorted()
              .forEach(a -> System.out.printf("Área: %.2f%n", a));

        // var, lambda e referência de método
        Runnable tarefa = () -> System.out.println("Executando...");
        tarefa.run();
    }
}
```

---

## 7. Exceções, try-with-resources e concorrência

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class Recursos {

    public String lerArquivo(Path caminho) throws IOException {
        try (BufferedReader leitor = Files.newBufferedReader(caminho)) {
            return leitor.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler: " + caminho, e);
        } finally {
            System.out.println("Tentativa concluída.");
        }
    }

    public synchronized CompletableFuture<Integer> calcularAsync(int base) {
        return CompletableFuture
                .supplyAsync(() -> base * base)
                .thenApply(quad -> quad + 1)
                .exceptionally(ex -> -1);
    }
}
```

---

## 8. Código inline

Você também pode realçar trechos curtos: a classe `ArrayList<String>`, o método
`Collectors.toList()`, a palavra-chave `synchronized` ou o literal `0xFF_EC_DE`.
