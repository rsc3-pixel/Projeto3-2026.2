/*
 * Atualizacao automatica das telas do painel (PI3-141).
 *
 * Sem isto a tela congela no momento em que foi aberta: uma bolsa alocada por
 * outra pessoa, ou um pedido novo, so apareceriam se alguem apertasse F5.
 *
 * A pagina busca a si mesma e troca apenas o conteudo interno. Recarregar a
 * pagina inteira perderia a posicao da rolagem e piscaria a tela a cada ciclo.
 */
(function () {
    'use strict';

    /* Trinta segundos: o estoque de um hemocentro nao muda a cada segundo, e a
     * VM e compartilhada com outras aplicacoes. Cada ciclo custa cerca de 80ms
     * de processamento, entao este intervalo mantem o custo perto de zero. */
    var INTERVALO = 30000;

    var alvo = document.querySelector('[data-atualizar]');
    if (!alvo) {
        return;
    }

    var timer = null;

    function buscar() {
        /* Uma aba em segundo plano nao precisa de dados frescos: ninguem esta
         * olhando. Isso evita que dez abas esquecidas abertas somem carga. */
        if (document.hidden) {
            return;
        }

        fetch(window.location.href, { headers: { 'X-Requested-With': 'fetch' } })
            .then(function (r) {
                if (!r.ok) {
                    throw new Error('HTTP ' + r.status);
                }
                return r.text();
            })
            .then(function (html) {
                var novo = new DOMParser()
                    .parseFromString(html, 'text/html')
                    .querySelector('[data-atualizar]');

                if (novo && novo.innerHTML !== alvo.innerHTML) {
                    alvo.innerHTML = novo.innerHTML;
                    marcarAtualizado();
                }
            })
            .catch(function () {
                /* Falha de rede nao derruba o ciclo: a proxima tentativa vem no
                 * intervalo seguinte, e a tela continua mostrando o ultimo
                 * estado conhecido em vez de um erro. */
            });
    }

    function marcarAtualizado() {
        var selo = document.querySelector('[data-selo-atualizacao]');
        if (!selo) {
            return;
        }
        selo.classList.add('piscou');
        setTimeout(function () {
            selo.classList.remove('piscou');
        }, 900);
    }

    function ligar() {
        if (timer === null) {
            timer = setInterval(buscar, INTERVALO);
        }
    }

    function desligar() {
        if (timer !== null) {
            clearInterval(timer);
            timer = null;
        }
    }

    /* Aba escondida nao consome: o ciclo para e volta junto com ela, e busca
     * uma vez na volta para a tela nao ficar com dados velhos. */
    document.addEventListener('visibilitychange', function () {
        if (document.hidden) {
            desligar();
        } else {
            buscar();
            ligar();
        }
    });

    ligar();
})();
