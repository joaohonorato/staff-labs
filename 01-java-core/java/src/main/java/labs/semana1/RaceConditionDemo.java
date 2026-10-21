package labs.semana1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Lab 1.3 — HashMap com várias threads, e por que ConcurrentHashMap sozinho não basta.
 *
 * Race condition (condição de corrida): o resultado depende da ordem em que as threads
 * intercalam. No HashMap, duas threads fazendo put ao mesmo tempo podem ler o mesmo bucket,
 * cada uma pendurar seu nó, e a segunda escrita apagar a primeira. Durante um resize é pior:
 * uma thread copia a tabela antiga enquanto outra ainda escreve nela.
 * O HashMap não avisa: não lança exceção, só perde dados em silêncio.
 *
 * Rodar:
 *   docker compose run --rm jdk25 java -cp target/benchmarks.jar labs.semana1.RaceConditionDemo
 */
public class RaceConditionDemo {

    static final int THREADS = 8;
    static final int POR_THREAD = 100_000;
    static final int POUCAS = 2_000;

    public static void main(String[] args) throws Exception {
        titulo("Parte 1: " + THREADS + " threads, " + POR_THREAD + " chaves distintas cada");
        System.out.printf("Esperado: %d entradas%n%n", THREADS * POR_THREAD);
        putsConcorrentes("Collections.synchronizedMap", Collections.synchronizedMap(new HashMap<>()), POR_THREAD);
        putsConcorrentes("ConcurrentHashMap", new ConcurrentHashMap<>(), POR_THREAD);

        titulo("Parte 2: contador compartilhado no ConcurrentHashMap");
        contadorConcorrente();

        // O HashMap fica por último: se uma thread entrar em loop infinito, ela continua
        // girando a 100% de CPU e atrapalharia as medições dos outros mapas.
        titulo("Parte 3a: HashMap comum, " + POUCAS + " chaves por thread");
        System.out.printf("Esperado: %d entradas. Falha típica: perder dados em silêncio.%n%n",
                THREADS * POUCAS);
        for (int rodada = 1; rodada <= 5; rodada++) {
            if (!putsConcorrentes("HashMap (rodada " + rodada + ")", new HashMap<>(), POUCAS)) {
                break;
            }
        }

        titulo("Parte 3b: HashMap comum, " + POR_THREAD + " chaves por thread");
        System.out.printf("Esperado: %d entradas. Com mais resizes, a corrupção pode virar loop.%n%n",
                THREADS * POR_THREAD);
        for (int rodada = 1; rodada <= 3; rodada++) {
            if (!putsConcorrentes("HashMap (rodada " + rodada + ")", new HashMap<>(), POR_THREAD)) {
                break;
            }
        }
        // Threads daemon presas em loop não impedem a JVM de sair.
        System.exit(0);
    }

    /**
     * Cada thread escreve chaves que só ela usa (thread t: t × 100 mil até t × 100 mil + 99.999).
     * Não há disputa pela mesma chave, então qualquer entrada faltando é culpa do mapa.
     */
    static boolean putsConcorrentes(String nome, Map<Integer, Integer> mapa, int porThread) throws Exception {
        long inicio = System.nanoTime();
        String erro = executarEmParalelo(t -> {
            for (int i = 0; i < porThread; i++) {
                mapa.put(t * porThread + i, i);
            }
        });
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
        if (TRAVOU.equals(erro)) {
            System.out.printf("%-30s TRAVOU: uma thread não terminou em %d s%n", nome, TIMEOUT_S);
            return false;
        }

        // size() é só um contador interno, e no HashMap ele também sofre race.
        // Por isso conferimos chave por chave.
        int perdidas = 0;
        for (int k = 0; k < THREADS * porThread; k++) {
            if (!mapa.containsKey(k)) {
                perdidas++;
            }
        }
        System.out.printf("%-30s size() = %7d   chaves perdidas = %6d   %4d ms%s%n",
                nome, mapa.size(), perdidas, ms, erro == null ? "" : "   ERRO: " + erro);
        return true;
    }

    // ------------------------------------------------------------------------------------
    // Cada operação do ConcurrentHashMap é atômica (indivisível). Mas "ler, somar 1 e gravar"
    // são DUAS operações: entre o get e o put outra thread pode gravar, e o incremento dela
    // se perde. Isso se chama lost update. A correção é uma operação composta atômica:
    // merge, compute ou computeIfAbsent, que o mapa executa travando aquele bucket.
    // ------------------------------------------------------------------------------------
    static void contadorConcorrente() throws Exception {
        System.out.printf("Esperado: %d%n", THREADS * POR_THREAD);

        Map<String, Integer> comGetPut = new ConcurrentHashMap<>();
        executarEmParalelo(t -> {
            for (int i = 0; i < POR_THREAD; i++) {
                Integer atual = comGetPut.get("total");
                comGetPut.put("total", atual == null ? 1 : atual + 1);
            }
        });
        System.out.printf("get + put  → %7d   ← cada operação é segura, a sequência não%n",
                comGetPut.get("total"));

        Map<String, Integer> comMerge = new ConcurrentHashMap<>();
        executarEmParalelo(t -> {
            for (int i = 0; i < POR_THREAD; i++) {
                comMerge.merge("total", 1, Integer::sum);
            }
        });
        System.out.printf("merge      → %7d   ← ler e gravar numa operação atômica só%n",
                comMerge.get("total"));
    }

    interface Tarefa {
        void rodar(int thread) throws Exception;
    }

    /**
     * Roda a tarefa em THREADS threads ao mesmo tempo. O CountDownLatch funciona como uma
     * largada: todas as threads ficam esperando e saem juntas, para maximizar a disputa.
     * Devolve a mensagem de erro se alguma thread lançou exceção.
     */
    static String executarEmParalelo(Tarefa tarefa) throws Exception {
        // Threads daemon: se uma travar em loop infinito, não impedem a JVM de terminar.
        List<Thread> threads = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS, r -> {
            Thread th = new Thread(r, "corrida-" + threads.size());
            th.setDaemon(true);
            threads.add(th);
            return th;
        });
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            int id = t;
            futuros.add(pool.submit(() -> {
                largada.await();
                tarefa.rodar(id);
                return null;
            }));
        }
        largada.countDown();

        String erro = null;
        for (Future<?> f : futuros) {
            try {
                f.get(TIMEOUT_S, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                erro = e.getCause().toString();
            } catch (TimeoutException e) {
                // No Java 7 o resize concorrente podia criar um ciclo na lista do bucket, e o
                // próximo get girava para sempre a 100% de CPU. O Java 8 mudou o resize e esse
                // ciclo específico sumiu, mas duas threads mexendo na mesma árvore (TreeNode)
                // ainda podem criar um ciclo nos ponteiros dela.
                mostrarOndeTravou(threads);
                return TRAVOU;
            }
        }
        pool.shutdownNow();
        return erro;
    }

    static final String TRAVOU = "TRAVOU";
    static final int TIMEOUT_S = 10;

    /**
     * Mesmo que um jstack (ou jcmd PID Thread.print) faria: o stack trace das threads ainda
     * vivas. Mostra só os frames de java.util.HashMap, onde o loop acontece.
     */
    static void mostrarOndeTravou(List<Thread> threads) {
        for (Thread th : threads) {
            if (!th.isAlive() || th.getState() == Thread.State.WAITING) {
                continue;
            }
            System.out.printf("  %s (%s) presa em:%n", th.getName(), th.getState());
            for (StackTraceElement frame : th.getStackTrace()) {
                if (frame.getClassName().startsWith("java.util.HashMap")) {
                    System.out.println("      at " + frame);
                }
            }
        }
    }

    static void titulo(String texto) {
        System.out.println();
        System.out.println("=== " + texto + " ===");
    }
}
