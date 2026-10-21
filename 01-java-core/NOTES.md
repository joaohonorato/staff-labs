# Módulo 1 — Java Core

Respostas de 2 min (pergunta → falha → solução → trade-off), números de cada lab e o ADR do módulo.

## Setup — Hello World de benchmark (2026-10-21)

Ambiente: Docker Desktop 29.6.2, Intel i7-12700H (20 threads), 8 GB para a VM.
JDK 25 (`eclipse-temurin:25-jdk`), Go 1.25.14 (`golang:1.25`). JMH 1.37, 1 fork, 3 warmups e 5 medições de 1 s.

**Hipótese:** se o resultado não é usado, o compilador elimina o cálculo e o benchmark mede o vazio.

| Caso | Java (JMH) | Go (`go test -bench`) |
|---|---|---|
| baseline (vazio) | 0,25 ns | 1,00 ns (`b.Loop`) |
| `log(x)` descartado | 4,86 ns | 4,91 ns |
| `log(x)` consumido (return / Blackhole / sink / `b.Loop`) | 4,58 ns | 5,06 ns |
| `sqrt(x)` descartado | **0,25 ns** (igual ao baseline) | **0,13 ns** |
| `sqrt(x)` consumido | 1,37 ns | 1,35 ns |

**O que os números provaram:**
- A hipótese só vale para código que o compilador consegue provar puro. `log` virou chamada a rotina
  nativa (stub da JVM / assembly no Go) e **não** foi eliminado, nem descartado. `sqrt` vira uma única instrução
  `sqrtsd`, então o compilador sabe que ela não tem efeito colateral e apagou o cálculo nas duas linguagens.
- O benchmark ingênuo de `sqrt` reportaria cerca de 0,2 ns, quando o custo real é ~1,4 ns: erro de mais de 5×.
- Não dá para confiar que "neste caso o JIT não elimina": isso muda com a versão do JDK, a arquitetura e o inlining.
  Por isso a regra é sempre consumir o resultado (return ou `Blackhole` no JMH; `b.Loop` ou `sink` no Go).
- O baseline do Go com `b.Loop` custa ~1 ns, contra ~0,25 ns do JMH. Compare sempre com o baseline da própria
  ferramenta, nunca números absolutos entre as duas.
- O JMH 1.37 no JDK 25 usa *compiler blackholes* (experimental): o resultado é consumido sem custo de chamada.
  Para comparar JVMs, mantenha o mesmo modo de Blackhole.

**Ângulo staff:** um número de benchmark sem baseline e sem teste negativo (o caso que deveria dar zero) não serve
para decisão. Num review de PR com "otimização medida", a primeira pergunta é: qual era o baseline e o resultado foi consumido?

## Semana 1 — HashMap e coleções concorrentes

Guia: [semana-01.md](semana-01.md). Para cada lab: previsão antes de rodar, número medido e o que aprendi.

### Lab 1.1 — HashMap por dentro
- Previsão:
- Capacidades em cada resize:
- Put em que o treeify aconteceu:

### Lab 1.2 — Contrato equals/hashCode
- Por que o `get` não chama `equals` quando falta `hashCode`:

### Lab 1.3 — Concorrência
- Chaves perdidas pelo HashMap:
- Onde a thread travou:
- Incrementos perdidos com get + put:
- Mensagem do Go:

### Lab 1.4 — LRU
- Previsão de quem sai:

### Lab 1.5 — Colisão (JMH)
| Chave | n = 100 | n = 10.000 | Crescimento |
|---|---|---|---|
| BOA | | | |
| CONSTANTE_COMPARABLE | | | |
| CONSTANTE_SEM_COMPARABLE | | | |

### Lab 1.6 — Mapas concorrentes (ops/µs)
| Implementação | 10% escrita | 90% escrita |
|---|---|---|
| Java synchronizedMap | | |
| Java ConcurrentHashMap | | |
| Go Mutex | | |
| Go RWMutex | | |
| Go sync.Map | | |

### Respostas de 2 min
- ★ HashMap por dentro:
- ★ equals/hashCode:
