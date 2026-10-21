package labs.semana1;

import java.lang.reflect.Field;
import java.util.HashMap;

/**
 * Lê os campos privados do HashMap por reflection, só para estudo.
 *
 * Desde o JDK 17 os internos do JDK são fortemente encapsulados (JEP 403): reflection em
 * java.util falha com InaccessibleObjectException. Por isso os labs que usam esta classe
 * rodam com {@code --add-opens java.base/java.util=ALL-UNNAMED}, que abre o pacote
 * java.util para o nosso código. Em produção, nunca dependa disso.
 *
 * Campos lidos (nomes do código-fonte do OpenJDK, java/util/HashMap.java):
 *   table      Node[]  o array de buckets; fica null até o primeiro put (alocação preguiçosa)
 *   threshold  int     quantas entradas cabem antes do próximo resize (capacidade × 0,75)
 *   Node.next  Node    o próximo nó do mesmo bucket (a lista ligada)
 */
final class HashMapSpy {

    private static final Field TABLE;
    private static final Field THRESHOLD;
    private static final Field NEXT;

    static {
        try {
            TABLE = HashMap.class.getDeclaredField("table");
            TABLE.setAccessible(true);
            THRESHOLD = HashMap.class.getDeclaredField("threshold");
            THRESHOLD.setAccessible(true);
            NEXT = Class.forName("java.util.HashMap$Node").getDeclaredField("next");
            NEXT.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException(
                    "Rode com --add-opens java.base/java.util=ALL-UNNAMED", e);
        }
    }

    private HashMapSpy() {
    }

    /** Mesma conta do HashMap.hash(): mistura os 16 bits altos nos 16 bits baixos. */
    static int espalhar(Object chave) {
        int h = chave.hashCode();
        return h ^ (h >>> 16);
    }

    /** Índice do bucket: os bits baixos do hash espalhado. Só funciona porque n é potência de 2. */
    static int indice(Object chave, int capacidade) {
        return espalhar(chave) & (capacidade - 1);
    }

    static int capacidade(HashMap<?, ?> mapa) {
        Object[] tabela = tabela(mapa);
        return tabela == null ? 0 : tabela.length;
    }

    static int threshold(HashMap<?, ?> mapa) {
        try {
            return THRESHOLD.getInt(mapa);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Quantos nós estão pendurados no bucket {@code indice}. */
    static int tamanhoDoBucket(HashMap<?, ?> mapa, int indice) {
        int total = 0;
        for (Object no = tabela(mapa)[indice]; no != null; no = proximo(no)) {
            total++;
        }
        return total;
    }

    /** "vazio", "lista" (HashMap$Node) ou "árvore" (HashMap$TreeNode). */
    static String tipoDoBucket(HashMap<?, ?> mapa, int indice) {
        Object primeiro = tabela(mapa)[indice];
        if (primeiro == null) {
            return "vazio";
        }
        return primeiro.getClass().getSimpleName().equals("TreeNode") ? "árvore" : "lista";
    }

    private static Object[] tabela(HashMap<?, ?> mapa) {
        try {
            return (Object[]) TABLE.get(mapa);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object proximo(Object no) {
        try {
            return NEXT.get(no);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
