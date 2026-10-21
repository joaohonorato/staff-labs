package labs.semana1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lab 1.2 — O contrato equals/hashCode e os bugs de quando ele é quebrado.
 *
 * O contrato (Javadoc de Object.hashCode):
 *   1. Se a.equals(b), então a.hashCode() == b.hashCode().        ← obrigatório
 *   2. Se a.hashCode() == b.hashCode(), a e b podem ser diferentes. ← colisão, é permitido
 *   3. hashCode() deve devolver o mesmo valor enquanto os campos usados no equals não mudarem.
 *
 * Por que a regra 1 importa: o HashMap usa o hashCode para ESCOLHER O BUCKET e só depois usa
 * equals para comparar dentro dele. Se dois objetos "iguais" têm hashCodes diferentes, o get
 * procura no bucket errado e nunca chega a chamar equals.
 *
 * Rodar:
 *   docker compose run --rm jdk25 java -cp target/benchmarks.jar labs.semana1.EqualsHashCodeDemo
 */
public class EqualsHashCodeDemo {

    public static void main(String[] args) {
        parte1SoEquals();
        parte2ChaveMutavel();
        parte3Record();
    }

    // ------------------------------------------------------------------------------------
    // Parte 1: sobrescreveu equals e esqueceu hashCode.
    // O hashCode herdado de Object é a "identidade" do objeto (cada new dá um valor diferente).
    // ------------------------------------------------------------------------------------
    static void parte1SoEquals() {
        titulo("Parte 1: equals sem hashCode");

        SoEquals a = new SoEquals("pix");
        SoEquals b = new SoEquals("pix");
        System.out.printf("a.equals(b) = %b, mas a.hashCode() = %d e b.hashCode() = %d%n",
                a.equals(b), a.hashCode(), b.hashCode());

        Map<SoEquals, String> mapa = new HashMap<>();
        mapa.put(a, "valor");
        System.out.printf("HashMap.get(b)       = %s   ← procurou no bucket errado%n", mapa.get(b));

        // ArrayList não usa hashCode: percorre tudo chamando equals. Por isso "funciona".
        List<SoEquals> lista = new ArrayList<>(List.of(a));
        System.out.printf("ArrayList.contains(b) = %b   ← lista só usa equals%n", lista.contains(b));

        Set<SoEquals> conjunto = new HashSet<>();
        conjunto.add(a);
        conjunto.add(b);
        System.out.printf("HashSet com a e b: size = %d   ← um Set com duplicata%n", conjunto.size());
    }

    // ------------------------------------------------------------------------------------
    // Parte 2: chave mutável. equals e hashCode estão certos, mas o campo muda DEPOIS do put.
    // A entrada continua no bucket calculado com o hash antigo. Ela fica inalcançável:
    // nem a chave nova nem a antiga encontram. Em produção isso é vazamento de memória.
    // ------------------------------------------------------------------------------------
    static void parte2ChaveMutavel() {
        titulo("Parte 2: chave mutável");

        ChaveMutavel chave = new ChaveMutavel("pix");
        Map<ChaveMutavel, String> mapa = new HashMap<>();
        mapa.put(chave, "valor");

        chave.id = "boleto";   // muda o campo usado no hashCode
        System.out.printf("get(mesmo objeto)           = %s   ← hash novo, bucket novo, vazio%n",
                mapa.get(chave));
        System.out.printf("get(new ChaveMutavel(\"pix\")) = %s   ← bucket certo, mas equals falha%n",
                mapa.get(new ChaveMutavel("pix")));
        System.out.printf("size() = %d, e a entrada ainda está lá: %s%n", mapa.size(), mapa);
    }

    // ------------------------------------------------------------------------------------
    // Parte 3: record. O compilador gera equals, hashCode e toString a partir dos componentes,
    // e os campos são final. Resolve as partes 1 e 2 de uma vez (desde que os componentes
    // também sejam imutáveis: um record com uma List dentro ainda pode mudar).
    // ------------------------------------------------------------------------------------
    static void parte3Record() {
        titulo("Parte 3: record");

        Map<ChaveCorreta, String> mapa = new HashMap<>();
        mapa.put(new ChaveCorreta("pix"), "valor");
        System.out.printf("get(new ChaveCorreta(\"pix\")) = %s%n", mapa.get(new ChaveCorreta("pix")));
        System.out.printf("hashCodes: %d e %d%n",
                new ChaveCorreta("pix").hashCode(), new ChaveCorreta("pix").hashCode());
    }

    /** Sobrescreve só equals. Os IDEs e o SpotBugs avisam disso; o compilador não. */
    static final class SoEquals {
        final String id;

        SoEquals(String id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof SoEquals outro && id.equals(outro.id);
        }
    }

    /** equals e hashCode corretos, mas o campo não é final. */
    static final class ChaveMutavel {
        String id;

        ChaveMutavel(String id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ChaveMutavel outra && id.equals(outra.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "ChaveMutavel(" + id + ")";
        }
    }

    record ChaveCorreta(String id) {
    }

    static void titulo(String texto) {
        System.out.println();
        System.out.println("=== " + texto + " ===");
    }
}
