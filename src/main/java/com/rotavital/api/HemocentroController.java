package com.rotavital.api;

import com.rotavital.api.dto.BolsaResponse;
import com.rotavital.api.dto.HemocentroRequest;
import com.rotavital.api.dto.HemocentroResponse;
import com.rotavital.api.dto.RotaResponse;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.StatusRota;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.servico.BolsaServico;
import com.rotavital.servico.HemocentroServico;
import com.rotavital.servico.RotaServico;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Endpoints de hemocentro, conforme a secao 2.1 do docs/contrato_api.md.
 *
 * <p>O controlador so cuida de HTTP: le parametro, chama o servico e escolhe
 * o codigo de resposta. Regra de negocio nenhuma mora aqui.</p>
 */
@Tag(name = "Hemocentros")
@RestController
@RequestMapping("/api/v1/hemocentros")
public class HemocentroController {

    private final HemocentroServico hemocentros;
    private final BolsaServico bolsas;
    private final RotaServico rotas;

    public HemocentroController(HemocentroServico hemocentros,
                                BolsaServico bolsas,
                                RotaServico rotas) {
        this.hemocentros = hemocentros;
        this.bolsas = bolsas;
        this.rotas = rotas;
    }

    @GetMapping
    public List<HemocentroResponse> listar(
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String uf) {
        return hemocentros.listar(cidade, uf).stream()
                .map(HemocentroResponse::de)
                .toList();
    }

    @GetMapping("/{id}")
    public HemocentroResponse buscar(@PathVariable String id) {
        return HemocentroResponse.de(hemocentros.buscar(id));
    }

    /**
     * 201 com o cabecalho Location apontando para o recurso criado, como manda
     * o contrato e a convencao REST.
     */
    @PostMapping
    public ResponseEntity<HemocentroResponse> criar(@Valid @RequestBody HemocentroRequest dados) {
        Hemocentro criado = hemocentros.criar(dados);
        return ResponseEntity
                .created(URI.create("/api/v1/hemocentros/" + criado.getId()))
                .body(HemocentroResponse.de(criado));
    }

    @PutMapping("/{id}")
    public HemocentroResponse atualizar(@PathVariable String id,
                                        @Valid @RequestBody HemocentroRequest dados) {
        return HemocentroResponse.de(hemocentros.atualizar(id, dados));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable String id) {
        hemocentros.remover(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/bolsas")
    public List<BolsaResponse> bolsasDoHemocentro(
            @PathVariable String id,
            @RequestParam(required = false) StatusBolsa status,
            @RequestParam(required = false) TipoHemocomponente tipoHemocomponente,
            @RequestParam(required = false) GrupoSanguineo grupoSanguineo) {
        return bolsas.listarPorHemocentro(id, status, tipoHemocomponente, grupoSanguineo).stream()
                .map(BolsaResponse::de)
                .toList();
    }

    @GetMapping("/{id}/rotas")
    public List<RotaResponse> rotasDoHemocentro(
            @PathVariable String id,
            @RequestParam(required = false) StatusRota status) {
        return rotas.listarPorHemocentro(id, status);
    }
}
