package labs.semana1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Lab 1.1 — HashMap por dentro.
 *
 * Mostra, com o HashMap de verdade (lido por reflection), as quatro mecânicas que caem
 * em entrevista: espalhamento do hash, cálculo do índice, resize e treeify.
 *
 * Rodar:
 *   docker compose run --rm jdk25 java --add-opens java.base/java.util=ALL-UNNAMED \
 *       -cp target/benchmarks.jar labs.semana1.HashMapInternals
 */
public class HashMapInternals {

    public static void main(String[] args) {
        parte1Espalhamento();
        parte2Resize();
        parte3Colisao();
        parte4Treeify();
    }

    // ------------------------------------------------------------------------------------
    // Parte 1: de hashCode() a índice do bucket.
    //
    // O HashMap guarda as entradas num array (a "tabela"). Cada posição do array é um bucket.
    // Para saber em qual bucket uma chave vai, ele faz duas contas:
    //   1. hash   = h ^ (h >>> 16)   "espalha" os bits altos sobre os baixos
    //   2. índice = hash & (n - 1)   pega só os bits baixos (n = tamanho da tabela)
    //
    // A conta 2 é um "resto da divisão" barato: como n é sempre potência de 2, (n - 1) é uma
    // máscara de bits só com uns (16 - 1 = 0b1111). O & com ela mantém os 4 bits de baixo.
    // O problema: com tabela pequena, só os bits baixos importam. Chaves cujo hashCode difere
    // apenas nos bits altos cairiam todas no mesmo bucket. A conta 1 evita isso.
    // ------------------------------------------------------------------------------------
    static void parte1Espalhamento() {
        titulo("Parte 1: hashCode → hash espalhado → índice");

        String chave = "staff";
        int h = chave.hashCode();
        int espalhado = h ^ (h >>> 16);
        int n = 16;
        System.out.printf("chave \"%s\"%n", chave);
        System.out.printf("  hashCode()        %s  (%d)%n", bits(h), h);
        System.out.printf("  h >>> 16          %s  os 16 bits altos descem para baixo%n", bits(h >>> 16));
        System.out.printf("  h ^ (h >>> 16)    %s  XOR mistura os dois%n", bits(espalhado));
        System.out.printf("  n - 1 (n = 16)    %s%n", bits(n - 1));
        System.out.printf("  índice = hash & (n - 1) = %d%n%n", espalhado & (n - 1));

        // Integer.hashCode() é o próprio valor. Estes quatro só diferem nos bits altos
        // (0x10000 = 1 seguido de 16 zeros), então os 4 bits baixos são todos 0000.
        System.out.println("Chaves que só diferem nos bits altos:");
        int[] chaves = {0x10000, 0x20000, 0x30000, 0x40000};
        HashMap<Integer, String> mapa = new HashMap<>();
        for (int c : chaves) {
            mapa.put(c, "x");
        }
        for (int c : chaves) {
            System.out.printf("  %-7d sem espalhar → bucket %d   com espalhar → bucket %d%n",
                    c, c & (n - 1), HashMapSpy.indice(c, n));
        }
        System.out.printf("  Buckets ocupados no HashMap real: %s%n",
                bucketsOcupados(mapa));
        System.out.println("  Sem o espalhamento, as 4 chaves estariam todas no bucket 0.");
    }

    // ------------------------------------------------------------------------------------
    // Parte 2: resize.
    //
    // Load factor (fator de carga) = entradas / tamanho da tabela. O padrão é 0,75: quando o
    // número de entradas passa de capacidade × 0,75 (o "threshold"), a tabela dobra e TODAS
    // as entradas são redistribuídas. Isso é O(n) naquele put específico.
    //
    // Por que dobrar? Com n potência de 2, cada entrada do bucket i vai para i ou i + n_antigo,
    // dependendo de um único bit do hash. O resize não precisa recalcular hashCode().
    // ------------------------------------------------------------------------------------
    static void parte2Resize() {
        titulo("Parte 2: resize");

        HashMap<Integer, String> mapa = new HashMap<>();
        System.out.printf("new HashMap<>(): capacidade %d (a tabela só é criada no primeiro put)%n",
                HashMapSpy.capacidade(mapa));

        int anterior = 0;
        for (int i = 1; i <= 100; i++) {
            mapa.put(i, "v" + i);
            int atual = HashMapSpy.capacidade(mapa);
            if (atual != anterior) {
                System.out.printf("  put nº %3d: capacidade %3d → %3d   (próximo resize ao passar de %d)%n",
                        i, anterior, atual, HashMapSpy.threshold(mapa));
                anterior = atual;
            }
        }

        // Armadilha comum: new HashMap<>(100) NÃO garante espaço para 100 entradas sem resize.
        // O argumento é a capacidade inicial (arredondada para 128), e 128 × 0,75 = 96.
        // A conta certa é esperado / 0,75. O Java 19 trouxe HashMap.newHashMap(100), que faz isso.
        System.out.println();
        inserir100("new HashMap<>(100)", new HashMap<>(100));
        inserir100("new HashMap<>(134)", new HashMap<>((int) Math.ceil(100 / 0.75)));
    }

    static void inserir100(String nome, HashMap<Integer, String> mapa) {
        int inicial = 0;
        int resizes = 0;
        for (int i = 1; i <= 100; i++) {
            int antes = HashMapSpy.capacidade(mapa);
            mapa.put(i, "v" + i);
            int depois = HashMapSpy.capacidade(mapa);
            if (antes == 0) {
                inicial = depois; // a tabela nasce no primeiro put
            } else if (depois != antes) {
                resizes++;
            }
        }
        System.out.printf("%s + 100 puts: tabela nasce com %d, termina com %d → %d resize(s)%n",
                nome, inicial, HashMapSpy.capacidade(mapa), resizes);
    }

    // ------------------------------------------------------------------------------------
    // Parte 3: colisão.
    //
    // Colisão é quando duas chaves diferentes caem no mesmo bucket. O HashMap guarda as duas
    // numa lista ligada naquele bucket e, no get, percorre a lista comparando com equals().
    //
    // String.hashCode() é uma fórmula fixa e pública: s[0]×31^(n-1) + s[1]×31^(n-2) + ...
    // "Aa" e "BB" dão o mesmo valor: 'A'×31 + 'a' = 65×31 + 97 = 2112 = 66×31 + 66 = 'B'×31 + 'B'.
    // Concatenando blocos "Aa"/"BB" dá para gerar 2^k strings diferentes com o MESMO hashCode.
    // Esse é o ataque de hash flooding: mandar milhares dessas chaves num JSON ou num form
    // e transformar cada get num percorrer de lista.
    // ------------------------------------------------------------------------------------
    static void parte3Colisao() {
        titulo("Parte 3: colisão");

        System.out.printf("\"Aa\".hashCode() = %d, \"BB\".hashCode() = %d%n",
                "Aa".hashCode(), "BB".hashCode());

        HashMap<String, Integer> mapa = new HashMap<>();
        mapa.put("Aa", 1);
        mapa.put("BB", 2);
        int i = HashMapSpy.indice("Aa", HashMapSpy.capacidade(mapa));
        System.out.printf("As duas no bucket %d, que agora tem %d nós (%s)%n",
                i, HashMapSpy.tamanhoDoBucket(mapa, i), HashMapSpy.tipoDoBucket(mapa, i));

        List<String> colidindo = gerarColisoes(4);
        System.out.printf("%nCombinando 4 blocos \"Aa\"/\"BB\": %d strings, %d hashCode distinto(s)%n",
                colidindo.size(), colidindo.stream().mapToInt(String::hashCode).distinct().count());
        System.out.println("  exemplos: " + colidindo.subList(0, 4) + " ... todas com hashCode "
                + colidindo.get(0).hashCode());
    }

    // ------------------------------------------------------------------------------------
    // Parte 4: treeify.
    //
    // Se um bucket acumula muitos nós, a lista vira uma árvore rubro-negra (árvore de busca
    // binária que se mantém balanceada). Busca na lista é O(n); na árvore, O(log n).
    // Duas condições (constantes do HashMap):
    //   TREEIFY_THRESHOLD    = 8   o bucket já tem 8 nós e chega mais um
    //   MIN_TREEIFY_CAPACITY = 64  a tabela tem pelo menos 64 posições
    // Se a tabela é menor que 64, o HashMap prefere dobrar a tabela (resize), apostando que
    // as chaves vão se separar. Com hashCode constante elas nunca se separam, então você vê
    // dois resizes "inúteis" antes da árvore aparecer.
    // ------------------------------------------------------------------------------------
    static void parte4Treeify() {
        titulo("Parte 4: treeify");

        HashMap<ChaveRuim, Integer> mapa = new HashMap<>();
        System.out.println("ChaveRuim tem hashCode() sempre 42: todas caem no mesmo bucket.");
        System.out.println(" put  capacidade  bucket  nós no bucket  estrutura");
        for (int id = 1; id <= 12; id++) {
            mapa.put(new ChaveRuim(id), id);
            int capacidade = HashMapSpy.capacidade(mapa);
            int i = HashMapSpy.indice(new ChaveRuim(id), capacidade);
            System.out.printf(" %3d  %10d  %6d  %13d  %s%n", id, capacidade, i,
                    HashMapSpy.tamanhoDoBucket(mapa, i), HashMapSpy.tipoDoBucket(mapa, i));
        }
    }

    /** Record com hashCode constante: é LEGAL pelo contrato, mas destrói o desempenho. */
    record ChaveRuim(int id) {
        @Override
        public int hashCode() {
            return 42;
        }
    }

    // --- utilitários de impressão ---

    static List<String> gerarColisoes(int blocos) {
        List<String> resultado = new ArrayList<>();
        resultado.add("");
        for (int b = 0; b < blocos; b++) {
            List<String> proxima = new ArrayList<>();
            for (String prefixo : resultado) {
                proxima.add(prefixo + "Aa");
                proxima.add(prefixo + "BB");
            }
            resultado = proxima;
        }
        return resultado;
    }

    static List<Integer> bucketsOcupados(HashMap<?, ?> mapa) {
        List<Integer> ocupados = new ArrayList<>();
        for (int i = 0; i < HashMapSpy.capacidade(mapa); i++) {
            if (!HashMapSpy.tipoDoBucket(mapa, i).equals("vazio")) {
                ocupados.add(i);
            }
        }
        return ocupados;
    }

    /** 32 bits em grupos de 8, para ler o que cada operação de bit faz. */
    static String bits(int valor) {
        String b = String.format("%32s", Integer.toBinaryString(valor)).replace(' ', '0');
        return b.substring(0, 8) + " " + b.substring(8, 16) + " " + b.substring(16, 24) + " " + b.substring(24);
    }

    static void titulo(String texto) {
        System.out.println();
        System.out.println("=== " + texto + " ===");
    }
}
