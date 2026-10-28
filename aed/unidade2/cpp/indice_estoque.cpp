#include "indice_estoque.hpp"

IndiceEstoque::IndiceEstoque() {
    /*
     * O vetor de baldes e alocado uma vez, com tamanho fixo, e todos comecam
     * vazios. Tamanho fixo porque o espaco de chaves e fechado: nao existe a
     * 33a combinacao, entao a tabela nunca precisa crescer.
     *
     * DIFERENCA PARA A VERSAO EM JAVA: la o HashMap aloca a tabela sozinho e a
     * dobra quando passa de 75% de ocupacao. Aqui a alocacao e explicita e nao
     * ha redimensionamento nenhum.
     */
    baldes_ = new No*[CAPACIDADE];
    for (int i = 0; i < CAPACIDADE; ++i) {
        baldes_[i] = nullptr;
    }
}

IndiceEstoque::~IndiceEstoque() {
    limpar();
    /* Depois das cadeias, o proprio vetor de baldes. */
    delete[] baldes_;
    baldes_ = nullptr;
}

int IndiceEstoque::hashDaChave(GrupoSanguineo grupo, TipoHemocomponente tipo) {
    /*
     * Hash perfeito por aritmetica posicional: o grupo escolhe o bloco de
     * TOTAL_TIPOS posicoes, e o tipo escolhe a posicao dentro do bloco. As 32
     * combinacoes recebem valores distintos de 0 a 31.
     *
     * O valor e o mesmo em toda execucao, porque depende so da posicao
     * declarada dos enums - nao de endereco nem de estado da maquina virtual.
     */
    return static_cast<int>(grupo) * TOTAL_TIPOS + static_cast<int>(tipo);
}

Resultado IndiceEstoque::adicionar(const Bolsa& bolsa,
                                   GrupoSanguineo grupo,
                                   TipoHemocomponente tipo) {
    const int balde = hashDaChave(grupo, tipo);
    if (balde < 0 || balde >= CAPACIDADE) {
        return Resultado::Erro;
    }

    /*
     * Sem duplicata: bolsa de mesmo codigo de rastreio tem a entrada
     * substituida, em vez de entrar de novo. O codigo de rastreio e a
     * identidade da bolsa, como o @Id no Java.
     */
    for (No* atual = baldes_[balde]; atual != nullptr; atual = atual->proxima) {
        if (atual->bolsa.codigo_rastreio == bolsa.codigo_rastreio) {
            atual->bolsa = bolsa;
            return Resultado::Ok;
        }
    }

    /*
     * Insercao no inicio da cadeia: O(1), e a ordem entre bolsas do mesmo
     * balde nao tem significado - quem ordena por validade e a FilaFefo.
     */
    baldes_[balde] = new No{bolsa, baldes_[balde]};
    tamanho_++;
    return Resultado::Ok;
}

std::vector<Bolsa> IndiceEstoque::buscar(GrupoSanguineo grupo,
                                         TipoHemocomponente tipo) const {
    std::vector<Bolsa> encontradas;

    const int balde = hashDaChave(grupo, tipo);
    if (balde < 0 || balde >= CAPACIDADE) {
        return encontradas;   /* fora da faixa devolve vazio, nunca erro */
    }

    for (No* atual = baldes_[balde]; atual != nullptr; atual = atual->proxima) {
        encontradas.push_back(atual->bolsa);
    }
    return encontradas;
}

bool IndiceEstoque::remover(const std::string& codigoRastreio,
                            GrupoSanguineo grupo,
                            TipoHemocomponente tipo) {
    const int balde = hashDaChave(grupo, tipo);
    if (balde < 0 || balde >= CAPACIDADE) {
        return false;
    }

    /*
     * Percorre guardando o anterior: em cadeia simplesmente ligada nao ha como
     * voltar, e desligar um no exige ter em maos quem aponta para ele.
     */
    No* anterior = nullptr;
    No* atual = baldes_[balde];

    while (atual != nullptr && atual->bolsa.codigo_rastreio != codigoRastreio) {
        anterior = atual;
        atual = atual->proxima;
    }
    if (atual == nullptr) {
        return false;
    }

    if (anterior == nullptr) {
        baldes_[balde] = atual->proxima;   /* era a primeira do balde */
    } else {
        anterior->proxima = atual->proxima;
    }

    delete atual;
    tamanho_--;
    return true;
}

void IndiceEstoque::limpar() {
    if (baldes_ == nullptr) {
        return;
    }

    for (int i = 0; i < CAPACIDADE; ++i) {
        No* atual = baldes_[i];
        while (atual != nullptr) {
            /* Guarda a proxima antes do delete: depois dele o no nao existe. */
            No* proxima = atual->proxima;
            delete atual;
            atual = proxima;
        }
        baldes_[i] = nullptr;
    }
    tamanho_ = 0;
}

int IndiceEstoque::ocupacaoDoBalde(int balde) const {
    if (baldes_ == nullptr || balde < 0 || balde >= CAPACIDADE) {
        return 0;
    }

    int quantidade = 0;
    for (No* atual = baldes_[balde]; atual != nullptr; atual = atual->proxima) {
        quantidade++;
    }
    return quantidade;
}
