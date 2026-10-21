// Lab 1.1 (Go) — O map do Go, comparado com o HashMap do Java.
//
// Desde o Go 1.24 o map é implementado com Swiss Tables: os slots ficam em grupos de 8, e
// cada grupo tem uma palavra de controle com 7 bits do hash de cada slot. A busca compara
// os 8 de uma vez nessa palavra e só chama a comparação de chave nos candidatos. Não existe
// lista ligada nem árvore por bucket, como no Java.
//
// Rodar:
//
//	docker compose run --rm go go run ./semana1/mapbasics
package main

import (
	"fmt"
	"hash/maphash"
	"math"
)

func main() {
	ordemDeIteracao()
	valorZeroEVirgulaOk()
	mapEhReferencia()
	mapNil()
	chavesNaN()
	hashComSemente()
}

// O runtime sorteia o ponto de partida de cada range sobre um map, de propósito, para que
// ninguém escreva código que dependa da ordem. No Java a ordem do HashMap também não é
// garantida, mas é estável entre execuções, e muita gente acaba dependendo dela sem saber.
func ordemDeIteracao() {
	titulo("Ordem de iteração")
	m := map[string]int{"pix": 1, "boleto": 2, "cartao": 3, "ted": 4, "doc": 5}
	for rodada := 1; rodada <= 3; rodada++ {
		var chaves []string
		for k := range m {
			chaves = append(chaves, k)
		}
		fmt.Printf("  rodada %d: %v\n", rodada, chaves)
	}
}

// Buscar uma chave que não existe devolve o valor zero do tipo (0, "", nil), nunca um erro.
// Para distinguir "não existe" de "existe com valor zero", use a forma v, ok := m[k].
// No Java, get devolve null nos dois casos quando o valor pode ser null.
func valorZeroEVirgulaOk() {
	titulo("Valor zero e vírgula-ok")
	saldo := map[string]int{"zerado": 0}

	fmt.Printf("  saldo[\"inexistente\"] = %d\n", saldo["inexistente"])
	v, ok := saldo["zerado"]
	fmt.Printf("  saldo[\"zerado\"]      = %d, ok = %v\n", v, ok)
	v, ok = saldo["inexistente"]
	fmt.Printf("  saldo[\"inexistente\"] = %d, ok = %v\n", v, ok)

	// Não compila: o map move os elementos de lugar quando cresce, então um ponteiro
	// para dentro dele ficaria inválido.
	//   p := &saldo["zerado"]   // cannot take address of saldo["zerado"]
}

// Um valor do tipo map é um ponteiro para a estrutura do runtime. Passar o map para uma
// função não copia os dados: a função altera o mesmo map. (Parecido com uma referência no Java.)
func mapEhReferencia() {
	titulo("Map é passado por referência")
	m := map[string]int{}
	adicionar(m)
	fmt.Printf("  depois de adicionar(m): %v\n", m)
}

func adicionar(m map[string]int) {
	m["novo"] = 1
}

// Um map declarado e não inicializado é nil: ler funciona (devolve zero), escrever é panic.
// Panic é recuperável com recover. Guarde isso para comparar com o lab de concorrência,
// onde o erro é fatal e o recover não salva.
func mapNil() {
	titulo("Map nil")
	var m map[string]int
	fmt.Printf("  leitura: m[\"x\"] = %d, len = %d\n", m["x"], len(m))

	defer func() {
		if r := recover(); r != nil {
			fmt.Printf("  escrita: panic recuperado → %v\n", r)
		}
	}()
	m["x"] = 1
}

// NaN (not a number) não é igual a nada, nem a si mesmo: NaN != NaN. Como o map usa ==,
// cada NaN vira uma chave nova e nenhuma pode ser lida de volta. Só clear(m) remove.
// No Java, Double.equals compara os bits, então Double.NaN funciona como chave normal.
func chavesNaN() {
	titulo("NaN como chave")
	m := map[float64]string{}
	m[math.NaN()] = "a"
	m[math.NaN()] = "b"
	_, ok := m[math.NaN()]
	fmt.Printf("  dois puts com NaN: len = %d, achou NaN? %v\n", len(m), ok)
	clear(m)
	fmt.Printf("  depois de clear(m): len = %d\n", len(m))
}

// A função de hash do map usa uma semente aleatória, sorteada por map, por isso o mesmo
// texto tem hash diferente em cada map e em cada execução. O pacote hash/maphash expõe essa
// mesma ideia. Compare com o Java, onde "Aa".hashCode() é 2112 em qualquer JVM do mundo:
// lá a defesa contra hash flooding é o treeify; aqui é o atacante não conseguir prever o hash.
func hashComSemente() {
	titulo("Hash com semente aleatória")
	for i := 1; i <= 2; i++ {
		semente := maphash.MakeSeed()
		fmt.Printf("  semente %d: hash(\"Aa\") = %20d   hash(\"BB\") = %20d\n",
			i, maphash.String(semente, "Aa"), maphash.String(semente, "BB"))
	}
}

func titulo(t string) {
	fmt.Printf("\n=== %s ===\n", t)
}
