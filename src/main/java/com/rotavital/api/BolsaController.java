package com.rotavital.api;

import com.rotavital.api.dto.AtualizarStatusBolsaRequest;
import com.rotavital.api.dto.BolsaRequest;
import com.rotavital.api.dto.BolsaResponse;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.servico.BolsaServico;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Endpoints de bolsa, conforme a secao 2.3 do docs/contrato_api.md.
 *
 * <p>Nao ha PUT: bolsa nao se edita por inteiro. O que muda ao longo da vida
 * dela e o status, e para isso existe o PATCH.</p>
 */
@Tag(name = "Bolsas")
@RestController
@RequestMapping("/api/v1/bolsas")
public class BolsaController {

    private final BolsaServico bolsas;

    public BolsaController(BolsaServico bolsas) {
        this.bolsas = bolsas;
    }

    @GetMapping
    public List<BolsaResponse> listar(
            @RequestParam(required = false) StatusBolsa status,
            @RequestParam(required = false) TipoHemocomponente tipoHemocomponente,
            @RequestParam(required = false) GrupoSanguineo grupoSanguineo,
            @RequestParam(required = false) String hemocentroId) {
        return bolsas.listar(status, tipoHemocomponente, grupoSanguineo, hemocentroId).stream()
                .map(BolsaResponse::de)
                .toList();
    }

    @GetMapping("/{id}")
    public BolsaResponse buscar(@PathVariable String id) {
        return BolsaResponse.de(bolsas.buscar(id));
    }

    @PostMapping
    public ResponseEntity<BolsaResponse> criar(@Valid @RequestBody BolsaRequest dados) {
        Bolsa criada = bolsas.criar(dados);
        return ResponseEntity
                .created(URI.create("/api/v1/bolsas/" + criada.getCodigoRastreio()))
                .body(BolsaResponse.de(criada));
    }

    @PatchMapping("/{id}")
    public BolsaResponse atualizarStatus(@PathVariable String id,
                                         @Valid @RequestBody AtualizarStatusBolsaRequest dados) {
        return BolsaResponse.de(bolsas.atualizarStatus(id, dados.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable String id) {
        bolsas.remover(id);
        return ResponseEntity.noContent().build();
    }
}
