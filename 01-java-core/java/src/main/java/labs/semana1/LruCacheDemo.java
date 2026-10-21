package labs.semana1;

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lab 1.4 — LinkedHashMap e o cache LRU em 10 linhas.
 *
 * O LinkedHashMap é um HashMap que também liga todas as entradas numa lista duplamente
 * ligada. Essa lista dá uma ordem previsível de iteração, que pode ser:
 *   - ordem de inserção (padrão): a ordem em que as chaves entraram;
 *   - ordem de acesso (accessOrder = true): cada get ou put move a entrada para o fim.
 *
 * LRU (Least Recently Used): quando o cache enche, descarta quem foi usado há mais tempo.
 * Com accessOrder = true, o "usado há mais tempo" é sempre o primeiro da lista. O
 * LinkedHashMap chama removeEldestEntry(maisAntiga) depois de cada put: se devolver true,
 * ele remove essa entrada.
 *
 * Rodar:
 *   docker compose run --rm jdk25 java -cp target/benchmarks.jar labs.semana1.LruCacheDemo
 */
public class LruCacheDemo {

    public static void main(String[] args) {
        parte1Ordem();
        parte2Lru();
        parte3GetModificaOMapa();
    }

    static void parte1Ordem() {
        titulo("Parte 1: ordem de iteração");

        List<String> chaves = List.of("pix", "boleto", "cartao", "ted", "doc");
        Map<String, Integer> hash = new HashMap<>();
        Map<String, Integer> linked = new LinkedHashMap<>();
        for (String c : chaves) {
            hash.put(c, 0);
            linked.put(c, 0);
        }
        System.out.println("inseridas:      " + chaves);
        System.out.println("HashMap:        " + hash.keySet() + "   ← ordem dos buckets");
        System.out.println("LinkedHashMap:  " + linked.keySet() + "   ← ordem de inserção");
    }

    static void parte2Lru() {
        titulo("Parte 2: cache LRU com capacidade 3");

        CacheLru<String, String> cache = new CacheLru<>(3);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        System.out.println("put a, b, c   → " + cache.keySet());
        cache.get("a");
        System.out.println("get a         → " + cache.keySet() + "   ← a foi para o fim (mais recente)");
        cache.put("d", "4");
        System.out.println("put d         → " + cache.keySet() + "   ← b era o mais antigo e saiu");
    }

    // ------------------------------------------------------------------------------------
    // Pegadinha: no modo de acesso, get() MUDA a estrutura (move o nó na lista).
    // Consequências:
    //   - chamar get enquanto itera lança ConcurrentModificationException;
    //   - mesmo um cache "só de leitura" usado por várias threads precisa de lock em cada get.
    // Para cache em produção, use Caffeine: concorrente, com política melhor que LRU (W-TinyLFU).
    // ------------------------------------------------------------------------------------
    static void parte3GetModificaOMapa() {
        titulo("Parte 3: get() é uma modificação estrutural");

        CacheLru<String, String> cache = new CacheLru<>(3);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        try {
            for (String chave : cache.keySet()) {
                cache.get(chave);
            }
            System.out.println("Iterou sem erro.");
        } catch (ConcurrentModificationException e) {
            System.out.println("Iterar e chamar get() → " + e.getClass().getSimpleName());
        }
    }

    /** Cache LRU: LinkedHashMap em ordem de acesso que remove o mais antigo ao passar do limite. */
    static final class CacheLru<K, V> extends LinkedHashMap<K, V> {
        private final int capacidade;

        CacheLru(int capacidade) {
            // capacidade inicial 16, load factor 0,75, accessOrder = true
            super(16, 0.75f, true);
            this.capacidade = capacidade;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> maisAntiga) {
            return size() > capacidade;
        }
    }

    static void titulo(String texto) {
        System.out.println();
        System.out.println("=== " + texto + " ===");
    }
}
