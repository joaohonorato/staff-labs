# Semana 1 — HashMap e coleções concorrentes

**Perguntas do banco que esta semana responde**

- ★ Como o HashMap funciona por dentro? Quando usar `ConcurrentHashMap`, `Collections.synchronizedMap` ou `LinkedHashMap`?
- ★ Qual é o contrato entre `equals` e `hashCode`, e o que acontece quando ele é quebrado?

**Como usar este guia.** Para cada lab: leia os conceitos que ele usa (Parte A), **escreva sua previsão** do
resultado antes de rodar, rode, compare com a previsão e anote o número no `NOTES.md`. Errar a previsão é a parte
que mais ensina. Os resultados de referência ficam escondidos em blocos "ver resultado" para você não olhar antes.

**Preparação** (uma vez por semana, ou sempre que mudar o código Java):

```bash
cd 01-java-core
docker compose run --rm mvn package
```

Todos os comandos abaixo partem da pasta `01-java-core`.

---

## Parte A — Conceitos

### A.1 Tabela hash: bucket, função hash e colisão

Uma **tabela hash** guarda pares chave → valor num array. A ideia é transformar a chave num número e usar esse
número como posição no array, para achar o valor direto, sem procurar.

- **Função hash**: transforma a chave num inteiro. No Java é o método `hashCode()` da chave.
- **Bucket** (balde): cada posição do array. No `HashMap` o array se chama `table`.
- **Colisão**: duas chaves diferentes caem no mesmo bucket. Acontece sempre: há bilhões de chaves possíveis e só
  algumas dezenas de buckets. O `HashMap` resolve pendurando as entradas do mesmo bucket numa lista ligada
  (cada nó aponta para o próximo).

Para falar de custo usamos a **notação O** ("big O"), que descreve como o tempo cresce quando o número de
elementos `n` cresce:

| Notação | Nome | Com 10 mil elementos | Exemplo |
|---|---|---|---|
| O(1) | constante | 1 passo | `get` num HashMap saudável |
| O(log n) | logarítmico | ~14 passos | busca numa árvore balanceada |
| O(n) | linear | 10 mil passos | percorrer uma lista |

O `HashMap` promete O(1) **em média**: isso só vale se as chaves se espalharem bem pelos buckets.

### A.2 Do `hashCode()` ao índice do bucket

O `HashMap` faz duas contas com bits. Antes, as três operações usadas:

| Operação | O que faz | Exemplo (8 bits) |
|---|---|---|
| `a >>> k` | desloca os bits de `a` `k` posições para a direita, entrando zeros | `1011_0000 >>> 4` = `0000_1011` |
| `a ^ b` | XOR: o bit é 1 se os dois bits forem diferentes | `1100 ^ 1010` = `0110` |
| `a & b` | AND: o bit é 1 só se os dois forem 1 | `1101 & 0011` = `0001` |

**Conta 1, o espalhamento:** `hash = h ^ (h >>> 16)`. Os 16 bits de cima do `hashCode` descem e se misturam
com os 16 de baixo.

**Conta 2, o índice:** `índice = hash & (n - 1)`, onde `n` é o tamanho da tabela. Como `n` é sempre
**potência de 2** (16, 32, 64...), `n - 1` é uma sequência de uns em binário (`15` = `1111`). O `&` com ela
mantém só os bits de baixo. É o mesmo que `hash % n` (resto da divisão), só que muito mais barato.

**Por que a conta 1 existe:** numa tabela de 16 posições, só os 4 bits de baixo decidem o bucket. Chaves cujo
`hashCode` só difere nos bits altos cairiam todas no mesmo bucket. O espalhamento faz os bits altos também
influenciarem. O Lab 1.1 mostra isso com números.

### A.3 Load factor e resize

**Load factor** (fator de carga) é a razão entradas ÷ tamanho da tabela. O padrão é **0,75**. Quando o número de
entradas passa de `capacidade × 0,75` (o **threshold**), a tabela **dobra** de tamanho e todas as entradas são
redistribuídas. Esse put específico custa O(n).

- Por que 0,75: é o meio-termo entre memória (tabela com muitos buckets vazios) e colisões (tabela cheia).
- Por que dobrar: com `n` potência de 2, cada entrada do bucket `i` vai para `i` ou `i + n_antigo`, dependendo
  de um único bit do hash. Não é preciso chamar `hashCode()` de novo.
- **Alocação preguiçosa**: `new HashMap<>()` não cria o array; ele nasce no primeiro `put`.
- **Armadilha**: `new HashMap<>(100)` não garante espaço para 100 entradas. O número é a capacidade inicial
  (arredondada para 128), e 128 × 0,75 = 96. Para caber `k` entradas, use `k / 0,75` ou, no Java 19+,
  `HashMap.newHashMap(k)`.

### A.4 Treeify e árvore rubro-negra

Se um bucket acumula muitos nós, a lista ligada (O(n)) vira uma **árvore rubro-negra**. Uma **árvore binária de
busca** guarda os menores à esquerda e os maiores à direita, então cada comparação descarta metade. A
**rubro-negra** é uma versão que se **rebalanceia** sozinha (pintando nós de vermelho ou preto e girando
subárvores), garantindo altura O(log n) mesmo no pior caso.

Constantes do `HashMap` (estão no código-fonte):

| Constante | Valor | Significado |
|---|---|---|
| `TREEIFY_THRESHOLD` | 8 | o bucket já tem 8 nós e chega o 9º → tenta virar árvore |
| `MIN_TREEIFY_CAPACITY` | 64 | só vira árvore se a tabela tiver ≥ 64 posições; senão, faz resize |
| `UNTREEIFY_THRESHOLD` | 6 | num resize, uma árvore que fica com ≤ 6 nós volta a ser lista |

**Como a árvore decide esquerda ou direita?** Primeiro pelo hash. Se os hashes forem iguais (a colisão que levou
até ali), usa `compareTo` **se a chave for `Comparable`**. Sem `Comparable`, na inserção ele usa um desempate
arbitrário (nome da classe e `System.identityHashCode`), que não ajuda na busca: o `get` precisa procurar nas
duas subárvores e volta a ser O(n). O Lab 1.5 mede isso.

**Por que o treeify existe:** defesa contra **hash flooding** (A.10). Um `TreeNode` ocupa cerca de 2× um `Node`,
por isso ele só entra em cena quando há colisão de verdade.

### A.5 O contrato `equals` / `hashCode`

O Javadoc de `Object.hashCode` define:

1. Se `a.equals(b)`, então **obrigatoriamente** `a.hashCode() == b.hashCode()`.
2. Se os hashCodes forem iguais, os objetos **podem** ser diferentes (é a colisão, e é permitido).
3. O `hashCode` deve continuar igual enquanto os campos usados no `equals` não mudarem.

O `HashMap` usa o `hashCode` para **escolher o bucket** e só depois chama `equals` dentro dele. Se dois objetos
"iguais" têm hashCodes diferentes (regra 1 quebrada), o `get` procura no bucket errado e nunca chega a chamar
`equals`. Se a chave muda depois do `put` (regra 3 quebrada), a entrada fica num bucket que ninguém mais procura.

Um `hashCode` constante (`return 42`) é **legal** pelo contrato, mas põe tudo num bucket só.

**Record** gera `equals`, `hashCode` e `toString` a partir dos componentes e deixa os campos `final`. A
imutabilidade é **rasa**: um record com uma `List` dentro ainda pode mudar, se a lista mudar.

### A.6 Concorrência: race condition, atomicidade e visibilidade

- **Thread-safe**: a classe funciona correta quando várias threads a usam ao mesmo tempo, sem sincronização extra.
  `HashMap` não é.
- **Race condition** (condição de corrida): o resultado depende da ordem em que as threads se intercalam. No
  `HashMap`, duas threads podem ler o mesmo bucket, cada uma pendurar seu nó, e a segunda escrita apagar a primeira.
- **Lost update** (atualização perdida): o caso clássico de race. A thread A lê 5, a B lê 5, as duas gravam 6:
  um incremento sumiu.
- **Atomicidade**: uma operação atômica acontece inteira ou não acontece, e nenhuma outra thread vê o meio dela.
- **Visibilidade**: sem sincronização, uma thread pode **não enxergar** o que outra escreveu (o valor fica no
  cache da CPU ou o compilador reordena instruções). `volatile` num campo garante que toda leitura vê a última
  escrita publicada.
- **Lock** (trava): só uma thread por vez entra no trecho protegido. Em Java, `synchronized` usa o **monitor**
  do objeto como lock.
- **CAS** (compare-and-swap): instrução atômica da CPU que diz "grave X **só se** o valor atual ainda for Y".
  Se outra thread mudou antes, a operação falha e você tenta de novo. Permite atualizar sem lock.

### A.7 As opções de mapa no Java

| Mapa | Como protege | Leituras | Quando usar |
|---|---|---|---|
| `HashMap` | não protege | — | uma thread só, ou mapa imutável depois de publicado |
| `Hashtable` | `synchronized` em todo método | travam | nunca (legado do Java 1.0) |
| `Collections.synchronizedMap` | um lock para o mapa inteiro | travam | pouca concorrência; precisa de lock em volta de iterações |
| `ConcurrentHashMap` | CAS + lock por bucket | **sem lock** | padrão para mapa compartilhado |
| `LinkedHashMap` | não protege | — | precisa de ordem de iteração ou de um LRU simples |

**Por dentro do `ConcurrentHashMap` (Java 8+):**

- `get` não trava: lê os nós com semântica `volatile`.
- `put` num bucket vazio usa CAS. `put` num bucket ocupado trava (`synchronized`) **só o primeiro nó daquele
  bucket**. Threads em buckets diferentes não se bloqueiam. (No Java 7 eram 16 "segmentos" fixos com um lock cada.)
- O resize é cooperativo: as threads que chegam durante o resize ajudam a migrar buckets.
- `size()` é uma soma de contadores espalhados (como um `LongAdder`), então sob concorrência é uma estimativa.
- Iteradores são **fracamente consistentes** (weakly consistent): nunca lançam `ConcurrentModificationException`
  e podem ou não refletir mudanças feitas durante a iteração.
- **Não aceita `null`** em chave nem em valor. Num mapa concorrente, `get(k) == null` precisa significar "não
  existe": não dá para separar "não existe" de "existe com null" com um `containsKey` depois, porque entre as duas
  chamadas outra thread pode mudar o mapa.
- Cada operação é atômica, mas **uma sequência de operações não é**. `get` seguido de `put` perde atualizações.
  Use as operações compostas atômicas: `merge`, `compute`, `computeIfAbsent`, `putIfAbsent`.

### A.8 `LinkedHashMap` e cache LRU

O `LinkedHashMap` é um `HashMap` que também liga todas as entradas numa **lista duplamente ligada** (cada nó
aponta para o anterior e o próximo). Essa lista define a ordem de iteração:

- **ordem de inserção** (padrão);
- **ordem de acesso** (`accessOrder = true` no construtor): cada `get` ou `put` move a entrada para o fim.

**LRU** (Least Recently Used): quando o cache enche, descarta quem foi usado há mais tempo. Em ordem de acesso, esse
é sempre o primeiro da lista. O `LinkedHashMap` chama `removeEldestEntry` depois de cada `put`; se devolver
`true`, ele remove a entrada mais antiga. Sobrescrevendo esse método você tem um LRU em 10 linhas.

Pegadinha: em ordem de acesso, **`get` é uma modificação estrutural**, porque mexe na lista. Logo, mesmo um cache
"só de leitura" usado por várias threads precisa de lock. Para cache em produção use **Caffeine**: é concorrente e
usa a política W-TinyLFU, que acerta mais que o LRU.

### A.9 O `map` do Go

- **Swiss Tables** (desde o Go 1.24): os slots ficam em grupos de 8, e cada grupo tem uma palavra de controle com
  7 bits do hash de cada slot. A busca compara os 8 de uma vez nessa palavra e só compara chaves nos candidatos.
  Não há lista ligada nem árvore. O map é dividido em várias tabelas de até 1.024 slots que crescem
  **separadamente**: o custo do crescimento se espalha, em vez de um resize único de O(n) como no Java.
- **Semente aleatória**: o hash de cada map usa uma semente sorteada. O mesmo texto tem hash diferente em cada map
  e em cada execução.
- **Ordem de iteração aleatória de propósito**: o runtime sorteia onde cada `range` começa, para ninguém depender
  da ordem.
- **Sem `equals`/`hashCode` customizados**: a chave precisa ser de um tipo **comparável** (números, strings,
  ponteiros, structs e arrays desses tipos). A igualdade é sempre `==`. Isso elimina os bugs do A.5, mas
  ponteiros como chave comparam **identidade**, e `NaN != NaN` gera chaves impossíveis de ler.
- **Não é thread-safe**, como o `HashMap`. A diferença é a falha: o runtime detecta escritas simultâneas e
  **mata o processo** com `fatal error: concurrent map writes`. Isso não é `panic`: `recover` não pega.
- **Goroutine**: função que roda de forma concorrente, gerenciada pelo runtime (milhares delas dividem poucas
  threads do sistema). Equivale às virtual threads do Java.
- **Proteções**: `sync.Mutex` (um lock, como o `synchronizedMap`); `sync.RWMutex` (vários leitores juntos com
  `RLock`, um escritor sozinho com `Lock`; quando um escritor espera, novos leitores também esperam, para o
  escritor não ficar na fila para sempre); `sync.Map` (mapa concorrente da biblioteca padrão; desde o Go 1.24 é
  uma hash-trie concorrente, parecida em espírito com o `ConcurrentHashMap`).
- **Race detector**: `go run -race` / `go test -race` instrumenta os acessos à memória e aponta as duas linhas
  que disputam o mesmo endereço. O Java não tem equivalente embutido.

### A.10 Hash flooding: as duas defesas

**Hash flooding** é um ataque de negação de serviço: mandar muitas chaves com o mesmo hash (por exemplo, nos
parâmetros de um formulário ou nas chaves de um JSON), para cada `get` virar uma busca linear. Mil requisições
pequenas passam a consumir CPU como milhões.

- No Java, `String.hashCode()` é uma fórmula **fixa e pública** (`s[0]×31^(n-1) + ... + s[n-1]`). `"Aa"` e
  `"BB"` colidem em qualquer JVM, e concatenando blocos dá para gerar 2^k strings que colidem. A defesa é o
  **treeify**: o pior caso cai de O(n) para O(log n), porque `String` é `Comparable`.
- No Go, a defesa é a **semente aleatória**: o atacante não consegue calcular colisões sem saber a semente.
- Defesa complementar, nos dois mundos: limitar o tamanho da entrada (número máximo de parâmetros, tamanho do JSON).

---

## Parte B — Labs

| Lab | Java | Go | O que prova |
|---|---|---|---|
| 1.1 Por dentro | `HashMapInternals` | `mapbasics` | espalhamento, resize, colisão e treeify, medidos no HashMap real |
| 1.2 Contrato | `EqualsHashCodeDemo` | (parte NaN do `mapbasics`) | os três bugs de quebrar o contrato |
| 1.3 Concorrência | `RaceConditionDemo` | `concorrencia` | Java perde dados e trava; Go derruba o processo |
| 1.4 LRU | `LruCacheDemo` | (exercício opcional) | ordem de acesso e `removeEldestEntry` |
| 1.5 Colisão (JMH) | `CollisionBenchmark` | — | O(1) vs O(log n) vs O(n), e por que `Comparable` importa |
| 1.6 Mapas concorrentes | `ConcurrentMapBenchmark` | `syncmap` | custo de um lock global vs lock por bucket |

### Lab 1.1 — HashMap por dentro

O programa lê os campos privados do `HashMap` (o array `table`, o `threshold` e a lista de cada bucket) usando
**reflection**: a API que permite inspecionar e acessar campos de uma classe em tempo de execução, inclusive os
privados. Desde o JDK 17 os internos do JDK são fortemente encapsulados e isso é bloqueado; o
`--add-opens java.base/java.util=ALL-UNNAMED` abre o pacote `java.util` só para este programa. (Rode uma vez sem a
flag para ver o `InaccessibleObjectException`: é o erro que muita lib antiga deu na migração para o 17.)

```bash
docker compose run --rm jdk25 java --add-opens java.base/java.util=ALL-UNNAMED -cp target/benchmarks.jar labs.semana1.HashMapInternals
docker compose run --rm go go run ./semana1/mapbasics
```

**Preveja antes de rodar:**

1. Em quais `put`s um `new HashMap<>()` faz resize, inserindo de 1 a 100?
2. `new HashMap<>(100)` com 100 entradas faz resize?
3. Com `hashCode()` sempre 42, em qual `put` o bucket vira árvore? O que acontece no 9º e no 10º?
4. No Go, a ordem do `range` muda entre as três rodadas?

**O que observar:**

- Parte 1: as quatro chaves `0x10000`...`0x40000` iriam para o bucket 0 sem espalhamento.
- Parte 4: o 9º e o 10º `put` fazem resize (16 → 32 → 64) em vez de treeify, porque a tabela é menor que 64.
  O bucket muda de posição (10 → 42) porque o índice depende do tamanho da tabela.
- Go: a semente muda o hash de `"Aa"` e `"BB"` a cada execução; no Java os dois são sempre 2112.

<details><summary>Ver resultado de referência</summary>

- Resizes nos puts 1 (0 → 16), 13, 25, 49 e 97. O resize acontece ao **passar** do threshold (13º put com threshold 12).
- `new HashMap<>(100)`: nasce com 128, faz 1 resize para 256. `new HashMap<>(134)`: nasce com 256, nenhum resize.
- Treeify: put 9 → resize para 32; put 10 → resize para 64; **put 11 → árvore**.
- Go: a ordem mudou na terceira rodada (`[boleto cartao ted doc pix]`).
</details>

**Anote no `NOTES.md`:** a sequência de capacidades e o put em que o treeify aconteceu.

### Lab 1.2 — O contrato `equals` / `hashCode`

```bash
docker compose run --rm jdk25 java -cp target/benchmarks.jar labs.semana1.EqualsHashCodeDemo
```

**Preveja:** uma classe com `equals` mas sem `hashCode`: o `HashMap.get` acha? E o `ArrayList.contains`? Um
`HashSet` aceita os dois objetos "iguais"? Uma chave que muda depois do `put` é encontrada pela chave nova ou pela antiga?

**O que observar:**

- O `ArrayList` "funciona" porque só usa `equals`. Esse é o motivo de o bug passar nos testes e explodir em produção
  quando alguém troca a lista por um `Set`.
- Na chave mutável, a entrada continua no mapa (`size() = 1`) mas nenhuma busca chega nela: é **vazamento de
  memória**. Ela só sai com `clear()` ou iterando.
- No Go esse bug não existe (A.9), mas veja a parte "NaN como chave" do `mapbasics`: é o equivalente Go de uma
  chave que nunca é igual a si mesma.

**Anote:** em uma frase, por que o `get` não chega a chamar `equals` no caso 1.

### Lab 1.3 — Concorrência

```bash
docker compose run --rm jdk25 java -cp target/benchmarks.jar labs.semana1.RaceConditionDemo
docker compose run --rm go go run ./semana1/concorrencia
docker compose run --rm go go run ./semana1/concorrencia -modo=mutex
docker compose run --rm go go run ./semana1/concorrencia -modo=syncmap
docker compose run --rm go go run -race ./semana1/concorrencia
```

Recursos usados no código Java: `ExecutorService` (um **pool de threads**: um conjunto fixo de threads que
executam as tarefas enviadas) e `CountDownLatch` (um contador que bloqueia as threads até chegar a zero; aqui
funciona como "largada": todas saem juntas para maximizar a disputa).

**Preveja:** quantas chaves o `HashMap` perde com 8 threads? O contador com `get` + `put` no
`ConcurrentHashMap` chega a 800.000?

**O que observar:**

- `synchronizedMap` e `ConcurrentHashMap`: zero perdas. O CHM é cerca de 2–3× mais rápido.
- Contador: `get` + `put` perde cerca de 80% dos incrementos, **mesmo no `ConcurrentHashMap`**. `merge` acerta.
- `HashMap`, Parte 3a: perde milhares de chaves **sem nenhuma exceção**. Repare que `size()` nem bate com o número
  de chaves realmente presentes: o próprio contador interno sofreu race.
- `HashMap`, Parte 3b: com mais resizes, **uma thread entra em loop infinito** e o programa mostra o stack trace dela,
  como um `jstack` mostraria: preso em `TreeNode.putTreeVal`, `root` ou `balanceInsertion`. A corrupção criou um
  ciclo nos ponteiros da árvore. (No Java 7 o famoso loop era na lista ligada, durante o resize; o Java 8 corrigiu
  aquele caso, e este lab mostra que o problema continua existindo na árvore.)
- Go sem proteção: `fatal error: concurrent map writes`, e o `recover` dentro da goroutine não imprime nada.
- Go com `-race`: `WARNING: DATA RACE` aponta as duas linhas que escreveram no mesmo endereço.

<details><summary>Ver resultado de referência</summary>

```
Collections.synchronizedMap    size() =  800000   chaves perdidas =      0    213 ms
ConcurrentHashMap              size() =  800000   chaves perdidas =      0    116 ms
get + put  →  170063
merge      →  800000
HashMap (rodada 1)             size() =    9145   chaves perdidas =   2689      2 ms   (esperado 16000)
HashMap (rodada 1, 800 mil)    TRAVOU, threads presas em HashMap$TreeNode.putTreeVal / root
```
</details>

**Anote:** quantas chaves o HashMap perdeu, onde a thread travou, e a mensagem exata do Go.

### Lab 1.4 — LinkedHashMap e LRU

```bash
docker compose run --rm jdk25 java -cp target/benchmarks.jar labs.semana1.LruCacheDemo
```

**Preveja:** num LRU de capacidade 3, depois de `put a, b, c`, `get a`, `put d`, quem sai?

**O que observar:** o `get a` muda a ordem; e iterar chamando `get` lança `ConcurrentModificationException`, a prova
de que `get` modifica a estrutura.

**Exercício opcional em Go:** o Go não tem `LinkedHashMap`. Monte um LRU com `map[K]*list.Element` +
`container/list` (lista duplamente ligada da biblioteca padrão). Compare o número de linhas com a versão Java.

### Lab 1.5 — Benchmark de colisão (JMH)

```bash
docker compose run --rm jdk25 java -jar target/benchmarks.jar CollisionBenchmark
```

Leva cerca de 1 minuto. `@Param` faz o JMH rodar o benchmark para cada combinação de parâmetros (2 tamanhos × 3 tipos
de chave = 6 execuções). Não há versão Go: no Go você não escolhe a função de hash da chave, então não dá para
forçar colisões.

**Preveja:** de 100 para 10.000 chaves (100× mais), quantas vezes mais lento fica cada tipo de chave?

<details><summary>Ver resultado de referência</summary>

| Chave | n = 100 | n = 10.000 | Crescimento | Complexidade |
|---|---|---|---|---|
| BOA | 3,6 ns | 3,6 ns | 1× | O(1) |
| CONSTANTE_COMPARABLE | 26 ns | 65 ns | 2,5× | O(log n) |
| CONSTANTE_SEM_COMPARABLE | 89 ns | 17.448 ns | 196× | O(n) |
</details>

**Anote:** a tabela com os seus números e a frase "Comparable transformou X em Y".

### Lab 1.6 — Mapas concorrentes (JMH e Go)

```bash
docker compose run --rm jdk25 java -jar target/benchmarks.jar ConcurrentMapBenchmark
docker compose run --rm go go test -bench=. -cpu=8 ./semana1/syncmap/
```

**Throughput** é a vazão: quantas operações por unidade de tempo, somando todas as threads. O JMH mostra em
`ops/us` (maior é melhor). O Go mostra `ns/op` (menor é melhor), que no `RunParallel` é o tempo de relógio
dividido pelo total de operações. Para comparar, converta: **ops/µs = 1000 ÷ ns/op**.

**Preveja:** com 90% de leitura, quantas vezes o CHM ganha do `synchronizedMap`? E com 90% de escrita? O
`RWMutex` ganha do `Mutex` com 90% de escrita?

<details><summary>Ver resultado de referência (8 threads)</summary>

| Implementação | 10% escrita (ops/µs) | 90% escrita (ops/µs) |
|---|---|---|
| Java `synchronizedMap` | 9,5 | 9,9 |
| Java `ConcurrentHashMap` | 188 | 77 |
| Go `map` + `Mutex` | 12,9 | 11,9 |
| Go `map` + `RWMutex` | 30,0 | 16,3 |
| Go `sync.Map` | 103 | 23,9 |

O lock global não escala: o `synchronizedMap` faz o mesmo número de operações com 10% ou 90% de escrita, porque
nos dois casos só uma thread trabalha por vez. O `RWMutex` só ajuda quando quase tudo é leitura.
</details>

**Anote:** a tabela convertida para ops/µs e qual implementação você escolheria para cada cenário.

---

## Parte C — Ângulo staff

**1. Falhar em silêncio ou falhar alto.** Com o mesmo erro (mapa não protegido), o Java perdeu dados sem exceção e
às vezes travou uma thread a 100% de CPU; o Go derrubou o processo na hora. Saiba defender os dois lados:

- Falhar alto (Go): o bug aparece no primeiro teste de carga, o stack trace aponta a linha, e não existe dado
  corrompido gravado no banco. O custo é derrubar uma réplica em produção.
- Falhar em silêncio (Java): o serviço continua de pé, mas com dados errados ou uma thread presa, e o bug pode levar
  meses para ser notado. Para uma plataforma, a maioria dos times prefere falhar alto: dado corrompido é pior que
  indisponibilidade, porque indisponibilidade o load balancer e o retry resolvem.

**2. Hash flooding.** Java e Go resolveram o mesmo ataque de formas diferentes (A.10). O Java não pode randomizar o
`String.hashCode()`, porque ele é especificado e código no mundo inteiro depende do valor. Exemplo de decisão de
design limitada por compatibilidade.

**3. Guia de escolha para os times.** Um staff transforma a resposta em padrão:

| Situação | Escolha |
|---|---|
| Mapa local de um método ou de uma thread | `HashMap` |
| Mapa compartilhado, montado uma vez e só lido | `Map.copyOf` (imutável) e publicação segura |
| Mapa compartilhado com escritas | `ConcurrentHashMap`, com `merge`/`compute` para operações compostas |
| Precisa de ordem de inserção | `LinkedHashMap` |
| Cache | Caffeine (não `LinkedHashMap` + `synchronized`) |

**4. Perguntas de code review que saem desta semana:**

- Este `HashMap` é acessado por mais de uma thread (um campo de um `@Service` do Spring é compartilhado por todas as requisições)?
- Há `get` seguido de `put` num `ConcurrentHashMap`? Deveria ser `merge` ou `compute`.
- A chave deste mapa é imutável? É um record?
- O mapa é pré-dimensionado com a conta certa?

---

## Parte D — Respostas de 2 minutos

Grave você mesmo, no formato **pergunta → cenário de falha → solução em produção → trade-off**. O roteiro abaixo é
um esqueleto. Use os **seus** números dos labs.

**★ HashMap por dentro e quando usar cada mapa**

- *Como funciona:* array de buckets, espalhamento `h ^ (h >>> 16)`, índice `hash & (n - 1)`, resize em 0,75,
  treeify com 8 nós se a tabela tiver ≥ 64.
- *Falha:* um `HashMap` num singleton do Spring, acessado por várias requisições. Seu Lab 1.3: perdeu N chaves sem
  exceção e travou uma thread em `TreeNode.putTreeVal`.
- *Solução:* `ConcurrentHashMap`, com `merge` para os contadores (o seu `get` + `put` perdeu X% dos incrementos).
- *Trade-off:* o CHM não aceita `null`, tem `size()` aproximado e iteração fracamente consistente.
  `synchronizedMap` só serve com pouca concorrência (seu Lab 1.6: ~20× mais lento com leitura pesada).
  `LinkedHashMap` quando a ordem importa, Caffeine para cache.

**★ Contrato equals/hashCode**

- *Contrato:* as três regras do A.5.
- *Falha:* entidade com `equals` sem `hashCode` num `HashSet`: duplicatas. Ou chave mutável: entrada inalcançável e
  vazamento de memória.
- *Solução:* chaves imutáveis, de preferência records. Em entidades JPA, `equals`/`hashCode` pelo id de negócio,
  nunca pelo id gerado no banco (que muda de null para valor no `persist`).
- *Trade-off:* `hashCode` constante é legal mas degrada para O(n) sem `Comparable` (seu Lab 1.5: 196× mais lento
  com 100× mais chaves).

---

## Parte E — Checklist da semana

- [ ] Li a Parte A e consigo explicar sem olhar: espalhamento, índice, resize, treeify, CAS
- [ ] Fiz a previsão antes de rodar cada lab
- [ ] Rodei os labs 1.1 a 1.6 e anotei os números no `NOTES.md`
- [ ] Desenhei no papel um HashMap durante um resize e durante um treeify
- [ ] (Opcional) Fiz o LRU em Go com `container/list`
- [ ] Gravei as duas respostas ★ em menos de 2 minutos cada
