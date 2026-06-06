# Java — Syntax Highlighting Sample

This document exercises all the token types that **highlight.js** colors in
Java code: keywords, types, strings, numbers, comments, annotations,
literals, operators, and more. Useful for checking the light/dark theme and the
width of long code blocks.

---

## 1. Structure, imports and comments

```java
// Single-line comment
/*
 * Multi-line block comment.
 * Documents the intent of the file.
 */
/**
 * Javadoc with {@code inline code}, {@link java.util.List} and tags.
 *
 * @author  Lucas Favaro
 * @version 1.0
 * @since   17
 */
package com.example.highlight;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static java.lang.Math.PI;
import static java.lang.Math.max;
```

---

## 2. Keywords, modifiers and primitive types

```java
public abstract class Vehicle implements Comparable<Vehicle> {

    // Constants (numeric literals in various formats)
    public static final int    MAX_WHEELS     = 18;
    private static final long   ID_BASE        = 9_223_372_036_854_775_807L;
    protected static final double GRAVITY      = 9.81d;
    private static final float  FACTOR         = 1.5f;
    private static final int    HEX            = 0xFF_EC_DE;
    private static final int    OCTAL          = 0_777;
    private static final int    BINARY         = 0b1010_0101;
    private static final char   INITIAL        = 'V';
    private static final boolean ACTIVE        = true;

    // Primitive types and wrappers
    private byte   level;
    private short  year;
    private int    wheels;
    private long   chassis;
    private float  consumption;
    private double price;
    private boolean running;
    private volatile transient String internalState;

    public abstract void move();
}
```

---

## 3. Strings, text blocks and escapes

```java
public class Texts {

    String simple    = "Hello, \"world\"!\n\tTabbed.";
    String unicode   = "Accented characters and emoji 🚀";
    char   backslash = '\\';

    // Text block (multi-line) — Java 15+
    String json = """
            {
                "name": "Markdown Reader",
                "version": 1.0,
                "offline": true,
                "themes": ["light", "dark"]
            }
            """;

    String sql = """
            SELECT id, name, price
            FROM   products
            WHERE  price > 100.0
            ORDER  BY name ASC;
            """;
}
```

---

## 4. Annotations, generics and methods

```java
@FunctionalInterface
interface Transformer<T, R> {
    R apply(T input);
}

@Deprecated(since = "2.0", forRemoval = true)
@SuppressWarnings({"unchecked", "rawtypes"})
public class Repository<T extends Comparable<? super T>> {

    private final List<T> items;

    @SafeVarargs
    public Repository(T... initial) {
        this.items = new java.util.ArrayList<>(List.of(initial));
    }

    @Override
    public String toString() {
        return "Repository" + items;
    }

    public <R> List<R> map(Transformer<? super T, ? extends R> fn) {
        return items.stream()
                    .map(fn::apply)
                    .collect(Collectors.toList());
    }
}
```

---

## 5. Control flow, switch expression and operators

```java
public final class Flow {

    enum Day { MON, TUE, WED, THU, FRI, SAT, SUN }

    static String dayType(Day day) {
        // switch expression with arrow and yield (Java 14+)
        return switch (day) {
            case SAT, SUN -> "weekend";
            case FRI      -> {
                String s = "almost ";
                yield s + "Friday";
            }
            default       -> "weekday";
        };
    }

    static int classify(int n) {
        if (n > 0 && n % 2 == 0) {
            return 1;
        } else if (n < 0 || n == Integer.MIN_VALUE) {
            return -1;
        }

        int sum = 0;
        for (int i = 0; i <= n; i++) {
            sum += i << 1;       // shift
        }
        while (sum > 100) {
            sum >>= 1;
        }
        do {
            sum--;
        } while (sum > 0);

        boolean test = (sum == 0) ? true : false;
        return test ? 0 : sum & 0xF;
    }
}
```

---

## 6. Records, sealed, pattern matching and lambdas

```java
public sealed interface Shape permits Circle, Rectangle {}

public record Circle(double radius)             implements Shape {}
public record Rectangle(double base, double alt) implements Shape {}

public class Geometry {

    public static double area(Shape shape) {
        // Pattern matching in switch (Java 21)
        return switch (shape) {
            case Circle c              -> Math.PI * c.radius() * c.radius();
            case Rectangle(var b, var h) -> b * h;        // record pattern
        };
    }

    public static void main(String[] args) {
        var shapes = List.of(new Circle(2.0), new Rectangle(3.0, 4.0));

        shapes.stream()
              .map(Geometry::area)
              .filter(a -> a > 5.0)
              .sorted()
              .forEach(a -> System.out.printf("Area: %.2f%n", a));

        // var, lambda and method reference
        Runnable task = () -> System.out.println("Running...");
        task.run();
    }
}
```

---

## 7. Exceptions, try-with-resources and concurrency

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class Resources {

    public String readFile(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read: " + path, e);
        } finally {
            System.out.println("Attempt completed.");
        }
    }

    public synchronized CompletableFuture<Integer> computeAsync(int base) {
        return CompletableFuture
                .supplyAsync(() -> base * base)
                .thenApply(sq -> sq + 1)
                .exceptionally(ex -> -1);
    }
}
```

---

## 8. Inline code

You can also highlight short snippets: the class `ArrayList<String>`, the method
`Collectors.toList()`, the keyword `synchronized`, or the literal `0xFF_EC_DE`.
