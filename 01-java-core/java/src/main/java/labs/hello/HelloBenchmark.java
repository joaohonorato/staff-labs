package labs.hello;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Hello World do JMH, mostrando a armadilha que ele existe para evitar:
 * dead-code elimination. Se o resultado não é usado, o JIT apaga o cálculo
 * e o benchmark mede o vazio.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class HelloBenchmark {

    // Campo não-final: impede que o JIT trate o valor como constante.
    double x = Math.PI;

    @Benchmark
    public void baseline() {
        // Método vazio: o custo mínimo de uma chamada de benchmark.
    }

    @Benchmark
    public void deadCode() {
        // Resultado descartado: o JIT pode eliminar o cálculo inteiro.
        Math.log(x);
    }

    @Benchmark
    public double returned() {
        // Retornar o valor faz o JMH consumi-lo num Blackhole implícito.
        return Math.log(x);
    }

    @Benchmark
    public void blackhole(Blackhole bh) {
        bh.consume(Math.log(x));
    }

    // Math.log vira chamada a uma rotina nativa que o JIT não prova pura,
    // então nem sempre é eliminada. Math.sqrt vira uma única instrução (sqrtsd)
    // e o JIT sabe que ela não tem efeito colateral.
    @Benchmark
    public void deadCodeSqrt() {
        Math.sqrt(x);
    }

    @Benchmark
    public double returnedSqrt() {
        return Math.sqrt(x);
    }
}
