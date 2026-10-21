// Package hello é o equivalente em Go do HelloBenchmark do JMH.
// O compilador do Go também pode eliminar código cujo resultado não é usado.
package hello

import (
	"math"
	"testing"
)

// Variável de pacote: impede que o compilador trate o valor como constante.
var x = math.Pi

// sink faz o papel do Blackhole: um resultado escrito aqui não pode ser descartado.
var sink float64

func BenchmarkBaseline(b *testing.B) {
	for b.Loop() {
	}
}

func BenchmarkDeadCode(b *testing.B) {
	for i := 0; i < b.N; i++ {
		_ = math.Log(x)
	}
}

func BenchmarkSink(b *testing.B) {
	for i := 0; i < b.N; i++ {
		sink = math.Log(x)
	}
}

// b.Loop (Go 1.24) mantém vivos os resultados das chamadas dentro do loop,
// então não precisa de sink: é a forma recomendada hoje.
func BenchmarkLoop(b *testing.B) {
	for b.Loop() {
		math.Log(x)
	}
}

// math.Log é assembly no amd64 e o compilador não elimina a chamada.
// math.Sqrt é intrínseco (uma instrução SQRTSD) e pode ser eliminado.
func BenchmarkDeadCodeSqrt(b *testing.B) {
	for i := 0; i < b.N; i++ {
		_ = math.Sqrt(x)
	}
}

func BenchmarkSinkSqrt(b *testing.B) {
	for i := 0; i < b.N; i++ {
		sink = math.Sqrt(x)
	}
}
