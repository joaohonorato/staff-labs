# staff-labs

Labs do plano de estudos Staff Engineer (Java + Go). Um diretório por módulo,
cada um com seu `docker-compose.yml`: nada depende de JDK ou Go instalado na máquina.

| Módulo | Pasta | Status |
|---|---|---|
| 1 — Java Core | `01-java-core/` | em andamento |

Cada sessão: conceito (fonte primária) → lab Java → lab Go → resposta de 2 min
(pergunta → falha → solução → trade-off). Cada módulo fecha com um ADR no `NOTES.md`.

## Rodar o Módulo 1

```bash
cd 01-java-core
docker compose run --rm mvn package
docker compose run --rm jdk25 java -jar target/benchmarks.jar Hello
docker compose run --rm go go test -bench=. -benchmem ./hello/
```
