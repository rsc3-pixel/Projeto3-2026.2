package com.rotavital.servico;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.rotavital.api.dto.AlocacaoRequest;
import com.rotavital.api.dto.AlocacaoResponse;

/**
 * Repete a alocacao quando outra operacao chega primeiro (PI3-108).
 *
 * <p>O bloqueio otimista do {@code ItemRequisicao} impede que duas alocacoes
 * simultaneas percam a contagem, mas quem perde a disputa recebe um erro. Isso
 * e correto do ponto de vista dos dados e ruim do ponto de vista de quem usa: a
 * alocacao continua valida, so precisa ser refeita sobre o estado novo.
 *
 * <p>Esta classe existe separada de {@link RequisicaoServico} por um detalhe do
 * Spring: chamar um metodo {@code @Transactional} de dentro da propria classe
 * nao passa pelo proxy, e a transacao simplesmente nao acontece. Tentar repetir
 * dentro do servico fez exatamente isso, e nenhuma alocacao era gravada.
 */
@Service
public class AlocacaoComRepeticao {

    /**
     * Tentativas antes de desistir.
     *
     * <p>Tres cobre o caso comum, poucas alocacoes chegando ao mesmo item ao
     * mesmo tempo. Acima disso a espera incomoda mais que o erro, e insistir
     * indica disputa alta demais para resolver repetindo.
     */
    private static final int TENTATIVAS = 3;

    private final RequisicaoServico requisicaoServico;

    public AlocacaoComRepeticao(RequisicaoServico requisicaoServico) {
        this.requisicaoServico = requisicaoServico;
    }

    /**
     * Aloca, repetindo enquanto o conflito for de concorrencia.
     *
     * <p>A operacao e segura de repetir porque cada tentativa comeca lendo o
     * estado atual: nao existe efeito acumulado entre uma tentativa e outra.
     * Qualquer outro erro sobe na hora, porque repetir nao resolveria.
     */
    public AlocacaoResponse alocar(String requisicaoId, String itemId,
                                   AlocacaoRequest dados) {
        OptimisticLockingFailureException ultimo =
                new OptimisticLockingFailureException("conflito nao resolvido");

        for (int tentativa = 1; tentativa <= TENTATIVAS; tentativa++) {
            try {
                return requisicaoServico.alocar(requisicaoId, itemId, dados);
            } catch (OptimisticLockingFailureException e) {
                ultimo = e;
            }
        }
        throw ultimo;
    }
}
