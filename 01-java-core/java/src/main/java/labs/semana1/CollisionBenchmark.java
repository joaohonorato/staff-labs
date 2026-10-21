package labs.semana1;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Lab 1.5 — Quanto custa um get quando TODAS as chaves colidem.
 *
 * Três tipos de chave, todos records com um id inteiro:
 *   BOA                       hashCode do id: chaves espalhadas, ~1 por bucket → O(1)
 *   CONSTANTE_COMPARABLE      hashCode 42, mas implementa Comparable → árvore ordenada, O(log n)
 *   CONSTANTE_SEM_COMPARABLE  hashCode 42 e sem Comparable → árvore sem ordem útil, O(n)
 *
 * Por que Comparable importa: depois do treeify, o bucket é uma árvore de busca. Para descer
 * nela o HashMap compara primeiro o hash (aqui todos iguais) e depois, se a classe for
 * Comparable, usa compareTo para decidir esquerda ou direita. Sem compareTo ele não sabe
 * para que lado ir e precisa procurar nas DUAS subárvores: vira uma busca em tudo.
 *
 * Notação: O(1) = custo constante; O(log n) = cresce devagar (10 mil chaves ≈ 14 passos);
 * O(n) = cresce junto com o número de chaves (10 mil chaves ≈ 10 mil comparações).
 *
 * Rodar (~1 min):
 *   docker compose run --rm jdk25 java -jar target/benchmarks.jar CollisionBenchmark
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class CollisionBenchmark {

    @Param({"100", "10000"})
    int n;

    @Param({"BOA", "CONSTANTE_COMPARABLE", "CONSTANTE_SEM_COMPARABLE"})
    String tipo;

    Object[] chaves;
    HashMap<Object, Integer> mapa;
    int proxima;

    @Setup
    public void preparar() {
        chaves = new Object[n];
        mapa = new HashMap<>();
        for (int id = 0; id < n; id++) {
            Object chave = switch (tipo) {
                case "BOA" -> new ChaveBoa(id);
                case "CONSTANTE_COMPARABLE" -> new ChaveConstanteComparable(id);
                default -> new ChaveConstante(id);
            };
            chaves[id] = chave;
            mapa.put(chave, id);
        }
    }

    /** Busca uma chave diferente a cada chamada, percorrendo todas em ordem. */
    @Benchmark
    public Integer get() {
        Object chave = chaves[proxima];
        proxima = proxima + 1 == n ? 0 : proxima + 1;
        return mapa.get(chave);
    }

    record ChaveBoa(int id) {
    }

    record ChaveConstante(int id) {
        @Override
        public int hashCode() {
            return 42;
        }
    }

    record ChaveConstanteComparable(int id) implements Comparable<ChaveConstanteComparable> {
        @Override
        public int hashCode() {
            return 42;
        }

        @Override
        public int compareTo(ChaveConstanteComparable outra) {
            return Integer.compare(id, outra.id);
        }
    }
}
