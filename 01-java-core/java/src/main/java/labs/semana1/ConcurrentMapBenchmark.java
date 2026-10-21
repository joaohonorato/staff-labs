package labs.semana1;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Lab 1.6 — synchronizedMap vs ConcurrentHashMap com 8 threads.
 *
 * synchronizedMap embrulha um HashMap e coloca um único lock (synchronized) em volta de
 * TODO método, inclusive get. Só uma thread usa o mapa por vez; as outras esperam na fila.
 *
 * ConcurrentHashMap (Java 8+):
 *   - get não trava nada: lê os campos com semântica volatile (sempre enxerga a última
 *     escrita publicada por outra thread);
 *   - put num bucket vazio usa CAS (compare-and-swap: instrução atômica da CPU que grava
 *     "só se o valor ainda for o que eu li"; se outra thread mudou antes, tenta de novo);
 *   - put num bucket ocupado trava só o primeiro nó DAQUELE bucket. Threads em buckets
 *     diferentes não se bloqueiam.
 *
 * O cenário (8 threads, 1.000 chaves): cada operação sorteia uma chave e faz put com
 * probabilidade percentualEscrita, senão get. Mede throughput: operações por microssegundo,
 * somando todas as threads. Quanto maior, melhor.
 *
 * Rodar (~40 s):
 *   docker compose run --rm jdk25 java -jar target/benchmarks.jar ConcurrentMapBenchmark
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(8)
@State(Scope.Benchmark) // um único mapa compartilhado pelas 8 threads
public class ConcurrentMapBenchmark {

    static final int CHAVES = 1_000;

    @Param({"synchronizedMap", "ConcurrentHashMap"})
    String implementacao;

    @Param({"10", "90"})
    int percentualEscrita;

    Map<Integer, Integer> mapa;

    @Setup
    public void preparar() {
        mapa = implementacao.equals("synchronizedMap")
                ? Collections.synchronizedMap(new HashMap<>())
                : new ConcurrentHashMap<>();
        for (int i = 0; i < CHAVES; i++) {
            mapa.put(i, i);
        }
    }

    @Benchmark
    public Integer operacao() {
        // ThreadLocalRandom: um gerador por thread. Um Random compartilhado viraria
        // ele mesmo o gargalo (todas as threads disputando a mesma semente).
        ThreadLocalRandom aleatorio = ThreadLocalRandom.current();
        int chave = aleatorio.nextInt(CHAVES);
        if (aleatorio.nextInt(100) < percentualEscrita) {
            return mapa.put(chave, chave);
        }
        return mapa.get(chave);
    }
}
