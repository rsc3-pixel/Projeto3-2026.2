#include "lista_estoque.hpp"

#include <iomanip>
#include <iostream>

Resultado ListaEstoque::inserir(const Bolsa& bolsa) {
    /*
     * DIFERENCA PARA A VERSAO EM C: la o malloc podia devolver NULL e cada
     * insercao verificava isso, devolvendo RV_ERRO. Aqui o new lanca
     * std::bad_alloc quando falta memoria, entao a verificacao desaparece do
     * caminho feliz. E o mesmo que Java faz com OutOfMemoryError.
     */
    No* novo = new No{bolsa, nullptr};

    if (ultimo_ == nullptr) {
        /*
         * Lista vazia: o no novo e, ao mesmo tempo, o primeiro e o ultimo.
         * Os dois ponteiros da lista passam a apontar para ele.
         */
        primeiro_ = novo;
        ultimo_   = novo;
    } else {
        /*
         * Lista com elementos: o antigo ultimo passa a apontar para o novo,
         * e so depois o ponteiro 'ultimo' da lista avanca. Nesta ordem, em
         * nenhum instante a cadeia fica interrompida.
         */
        ultimo_->proximo = novo;
        ultimo_ = novo;
    }

    tamanho_++;
    return Resultado::Ok;
}

Resultado ListaEstoque::remover(const std::string& codigo) {
    /*
     * Percorre guardando o no anterior, porque em lista simplesmente
     * encadeada nao ha como voltar: para desligar um no do meio e preciso
     * ter em maos quem aponta para ele.
     */
    No* atual    = primeiro_;
    No* anterior = nullptr;

    while (atual != nullptr && atual->bolsa.codigo_rastreio != codigo) {
        anterior = atual;
        atual    = atual->proximo;
    }

    if (atual == nullptr) {
        return Resultado::Erro;   /* codigo nao esta na lista */
    }

    if (anterior == nullptr) {
        /*
         * CASO 1 - primeiro no: nao existe anterior para religar, entao o
         * inicio da lista passa a ser o no seguinte.
         */
        primeiro_ = atual->proximo;
    } else {
        /*
         * CASO 2 - no do meio ou no final: o anterior passa a apontar para
         * o proximo do removido, saltando por cima dele e fechando a cadeia.
         */
        anterior->proximo = atual->proximo;
    }

    if (atual == ultimo_) {
        /*
         * CASO 3 - era o ultimo no: o ponteiro 'ultimo' precisa recuar para o
         * anterior, senao continuaria apontando para memoria liberada e a
         * proxima insercao escreveria em cima dela.
         *
         * Quando o removido era o unico no, 'anterior' e nullptr e 'ultimo'
         * fica nullptr, que e exatamente o estado de lista vazia.
         */
        ultimo_ = anterior;
    }

    delete atual;       /* libera o no, nunca a lista */
    tamanho_--;
    return Resultado::Ok;
}

Bolsa* ListaEstoque::buscar(const std::string& codigo) {
    /*
     * Busca sequencial: O(n). Indexar por codigo seria tabela hash, Unidade 2.
     *
     * DIFERENCA PARA A VERSAO EM C: la a comparacao era strcmp(a, b) == 0,
     * que compara byte a byte e devolve zero quando sao iguais, o que se le
     * ao contrario da intuicao. Com std::string o operador == faz o
     * esperado, igual ao equals do Java.
     */
    for (No* atual = primeiro_; atual != nullptr; atual = atual->proximo) {
        if (atual->bolsa.codigo_rastreio == codigo) {
            /* Devolve o endereco da bolsa dentro do no, sem copiar. */
            return &atual->bolsa;
        }
    }

    return nullptr;
}

void ListaEstoque::listar() const {
    if (primeiro_ == nullptr) {
        std::cout << "  (estoque vazio)\n";
        return;
    }

    /*
     * DIFERENCA PARA A VERSAO EM C: la o printf com "%-8s %-3s" alinhava as
     * colunas pela propria string de formato. Aqui os manipuladores
     * std::left e std::setw fazem o mesmo papel, com a vantagem de o
     * compilador conferir os tipos, o que o printf nao faz.
     */
    int posicao = 1;
    for (const No* atual = primeiro_; atual != nullptr; atual = atual->proximo) {
        const Bolsa& b = atual->bolsa;
        std::cout << "  " << posicao << ". "
                  << std::left
                  << std::setw(8)  << b.codigo_rastreio
                  << std::setw(4)  << b.grupo_sanguineo
                  << std::setw(24) << b.componente
                  << std::right << std::setw(4) << b.volume_ml << " ml"
                  << "  coleta:"   << b.data_coleta
                  << "  validade:" << b.data_validade
                  << "  "          << b.status
                  << '\n';
        posicao++;
    }
}

ListaEstoque::~ListaEstoque() {
    /*
     * DIFERENCA PARA A VERSAO EM C: la isto era lista_destruir, e quem usava
     * a lista precisava lembrar de chamar. Aqui o destrutor roda sozinho
     * quando o objeto sai de escopo, mesmo que uma excecao interrompa o
     * caminho. Esquecer de liberar deixou de ser possivel.
     */
    No* atual = primeiro_;
    while (atual != nullptr) {
        /*
         * Guarda o proximo ANTES do delete. Depois de liberar o no, ler
         * atual->proximo seria acesso a memoria ja devolvida ao sistema.
         */
        No* proximo = atual->proximo;
        delete atual;
        atual = proximo;
    }

    primeiro_ = nullptr;
    ultimo_   = nullptr;
    tamanho_  = 0;
}
