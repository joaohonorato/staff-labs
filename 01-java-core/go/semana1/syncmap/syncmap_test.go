// Lab 1.6 (Go) — sync.Map vs map + Mutex vs map + RWMutex.
//
// Equivalente do ConcurrentMapBenchmark do Java: 1.000 chaves, operações aleatórias, com
// 10% ou 90% de escritas.
//
//   - Mutex: um lock só, como o synchronizedMap.
//   - RWMutex: lock de leitura e escrita. Vários leitores entram juntos (RLock); um escritor
//     (Lock) espera todos saírem e entra sozinho. Brilha quando quase tudo é leitura.
//   - sync.Map: map concorrente da biblioteca padrão. Desde o Go 1.24 é uma hash-trie
//     concorrente (árvore de tabelas pequenas, travadas separadamente), parecida em espírito
//     com o ConcurrentHashMap.
//
// Rodar (-cpu=8 deixa o mesmo número de threads do JMH):
//
//	docker compose run --rm go go test -bench=. -cpu=8 ./semana1/syncmap/
package syncmap

import (
	"fmt"
	"math/rand/v2"
	"sync"
	"testing"
)

const chaves = 1_000

// armazem é a interface comum às três implementações, para o benchmark tratar todas igual.
type armazem interface {
	Load(k int) (int, bool)
	Store(k, v int)
}

type comMutex struct {
	mu sync.Mutex
	m  map[int]int
}

func (a *comMutex) Load(k int) (int, bool) {
	a.mu.Lock()
	defer a.mu.Unlock()
	v, ok := a.m[k]
	return v, ok
}

func (a *comMutex) Store(k, v int) {
	a.mu.Lock()
	defer a.mu.Unlock()
	a.m[k] = v
}

type comRWMutex struct {
	mu sync.RWMutex
	m  map[int]int
}

func (a *comRWMutex) Load(k int) (int, bool) {
	a.mu.RLock() // leitores não bloqueiam uns aos outros
	defer a.mu.RUnlock()
	v, ok := a.m[k]
	return v, ok
}

func (a *comRWMutex) Store(k, v int) {
	a.mu.Lock() // escritor exclusivo
	defer a.mu.Unlock()
	a.m[k] = v
}

// comSyncMap adapta o sync.Map (chave e valor any) para a interface tipada.
type comSyncMap struct {
	m sync.Map
}

func (a *comSyncMap) Load(k int) (int, bool) {
	v, ok := a.m.Load(k)
	if !ok {
		return 0, false
	}
	return v.(int), true
}

func (a *comSyncMap) Store(k, v int) {
	a.m.Store(k, v)
}

func BenchmarkMapas(b *testing.B) {
	implementacoes := []struct {
		nome string
		novo func() armazem
	}{
		{"Mutex", func() armazem { return &comMutex{m: map[int]int{}} }},
		{"RWMutex", func() armazem { return &comRWMutex{m: map[int]int{}} }},
		{"SyncMap", func() armazem { return &comSyncMap{} }},
	}

	for _, escrita := range []int{10, 90} {
		for _, impl := range implementacoes {
			b.Run(fmt.Sprintf("escrita%02d/%s", escrita, impl.nome), func(b *testing.B) {
				a := impl.novo()
				for k := range chaves {
					a.Store(k, k)
				}
				// RunParallel distribui as b.N iterações entre GOMAXPROCS goroutines
				// (8, com -cpu=8). É o equivalente do @Threads(8) do JMH.
				b.RunParallel(func(pb *testing.PB) {
					for pb.Next() {
						// Funções de math/rand/v2 são seguras e rápidas com concorrência
						// (cada thread tem seu estado), como o ThreadLocalRandom.
						k := rand.IntN(chaves)
						if rand.IntN(100) < escrita {
							a.Store(k, k)
						} else {
							a.Load(k)
						}
					}
				})
			})
		}
	}
}
