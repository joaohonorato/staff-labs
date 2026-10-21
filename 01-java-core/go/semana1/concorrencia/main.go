// Lab 1.3 (Go) — Escrever num map a partir de várias goroutines.
//
// Goroutine é uma função rodando de forma concorrente, gerenciada pelo runtime do Go (não é
// uma thread do sistema operacional; milhares delas dividem poucas threads). É o equivalente
// das virtual threads do Java.
//
// O map do Go não é thread-safe, igual ao HashMap. A diferença é o que acontece quando você
// erra: o runtime detecta duas escritas ao mesmo tempo e MATA O PROCESSO com
// "fatal error: concurrent map writes". Não é panic: recover não pega, e nada roda depois.
//
// Rodar:
//
//	docker compose run --rm go go run ./semana1/concorrencia                # map puro → fatal error
//	docker compose run --rm go go run ./semana1/concorrencia -modo=mutex    # protegido por mutex
//	docker compose run --rm go go run ./semana1/concorrencia -modo=syncmap  # sync.Map
//	docker compose run --rm go go run -race ./semana1/concorrencia          # com o race detector
package main

import (
	"flag"
	"fmt"
	"sync"
)

const (
	goroutines = 8
	porGorout  = 100_000
)

func main() {
	modo := flag.String("modo", "puro", "puro | mutex | syncmap")
	flag.Parse()

	switch *modo {
	case "puro":
		mapPuro()
	case "mutex":
		mapComMutex()
	case "syncmap":
		comSyncMap()
	default:
		fmt.Println("modo desconhecido:", *modo)
	}
}

// Mesmo cenário do RaceConditionDemo do Java: cada goroutine escreve chaves só dela.
func mapPuro() {
	fmt.Println("8 goroutines escrevendo num map comum...")
	m := map[int]int{}
	rodarEmParalelo(func(g int) {
		// Este recover não adianta: fatal error não é panic.
		defer func() {
			if r := recover(); r != nil {
				fmt.Println("recover pegou:", r)
			}
		}()
		for i := 0; i < porGorout; i++ {
			m[g*porGorout+i] = i
		}
	})
	fmt.Println("Se você está lendo isto, o runtime não pegou a corrida desta vez. len =", len(m))
}

// sync.Mutex: só uma goroutine por vez entra entre Lock e Unlock. É o equivalente do
// Collections.synchronizedMap do Java (um lock para o mapa inteiro).
func mapComMutex() {
	var mu sync.Mutex
	m := map[int]int{}
	rodarEmParalelo(func(g int) {
		for i := 0; i < porGorout; i++ {
			mu.Lock()
			m[g*porGorout+i] = i
			mu.Unlock()
		}
	})
	fmt.Printf("map + Mutex: len = %d (esperado %d)\n", len(m), goroutines*porGorout)
}

// sync.Map: o map concorrente da biblioteca padrão. Não é genérico (chaves e valores são any)
// e não tem len: para contar, é preciso percorrer com Range.
func comSyncMap() {
	var m sync.Map
	rodarEmParalelo(func(g int) {
		for i := 0; i < porGorout; i++ {
			m.Store(g*porGorout+i, i)
		}
	})
	total := 0
	m.Range(func(_, _ any) bool {
		total++
		return true
	})
	fmt.Printf("sync.Map: %d entradas (esperado %d)\n", total, goroutines*porGorout)
}

// sync.WaitGroup espera um grupo de goroutines terminar (parecido com um CountDownLatch).
func rodarEmParalelo(tarefa func(g int)) {
	var wg sync.WaitGroup
	for g := range goroutines {
		wg.Add(1)
		go func() {
			defer wg.Done()
			tarefa(g)
		}()
	}
	wg.Wait()
}
