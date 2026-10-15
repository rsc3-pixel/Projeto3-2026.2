package com.rotavital.api;

import com.rotavital.api.dto.AtualizarStatusRotaRequest;
import com.rotavital.api.dto.BolsaResponse;
import com.rotavital.api.dto.EmbarqueBolsaRequest;
import com.rotavital.api.dto.LeituraTemperaturaRequest;
import com.rotavital.api.dto.LeituraTemperaturaResponse;
import com.rotavital.api.dto.RotaRequest;
import com.rotavital.api.dto.RotaResponse;
import com.rotavital.dominio.enums.StatusRota;
import com.rotavital.servico.RotaServico;
import com.rotavital.servico.TemperaturaServico;
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
 * Endpoints de rota, conforme a secao 2.5 do contrato_api.md.
 */
@Tag(name = "Rotas")
@RestController
@RequestMapping("/api/v1/rotas")
public class RotaController {

    private final RotaServico rotas;
    private final TemperaturaServico temperaturas;

    public RotaController(RotaServico rotas, TemperaturaServico temperaturas) {
        this.rotas = rotas;
        this.temperaturas = temperaturas;
    }

    @GetMapping
    public List<RotaResponse> listar(
            @RequestParam(required = false) StatusRota status,
            @RequestParam(required = false) String hemocentroId,
            @RequestParam(required = false) String hospitalId) {
        return rotas.listar(status, hemocentroId, hospitalId);
    }

    @GetMapping("/{id}")
    public RotaResponse buscar(@PathVariable String id) {
        return rotas.detalhar(id);
    }

    @PostMapping
    public ResponseEntity<RotaResponse> criar(@Valid @RequestBody RotaRequest dados) {
        RotaResponse criada = rotas.criar(dados);
        return ResponseEntity
                .created(URI.create("/api/v1/rotas/" + criada.id()))
                .body(criada);
    }

    @PatchMapping("/{id}")
    public RotaResponse atualizarStatus(@PathVariable String id,
                                        @Valid @RequestBody AtualizarStatusRotaRequest dados) {
        return rotas.atualizarStatus(id, dados);
    }

    @GetMapping("/{id}/bolsas")
    public List<BolsaResponse> listarBolsas(@PathVariable String id) {
        return rotas.listarBolsas(id);
    }

    @PostMapping("/{id}/bolsas")
    public ResponseEntity<BolsaResponse> embarcar(@PathVariable String id,
                                                  @Valid @RequestBody EmbarqueBolsaRequest dados) {
        BolsaResponse bolsa = rotas.embarcar(id, dados);
        return ResponseEntity
                .created(URI.create("/api/v1/rotas/" + id + "/bolsas/" + bolsa.id()))
                .body(bolsa);
    }

    @DeleteMapping("/{id}/bolsas/{bolsaId}")
    public ResponseEntity<Void> desembarcar(@PathVariable String id,
                                            @PathVariable String bolsaId) {
        rotas.desembarcar(id, bolsaId);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------------
    // Telemetria de temperatura (HU-07)
    //
    // A telemetria e simulada nesta fase do projeto: estes endpoints recebem
    // o que um sensor reportaria, sem integracao com hardware.
    // -----------------------------------------------------------------------

    @PostMapping("/{id}/temperaturas")
    public ResponseEntity<LeituraTemperaturaResponse> registrarTemperatura(
            @PathVariable String id,
            @Valid @RequestBody LeituraTemperaturaRequest dados) {
        LeituraTemperaturaResponse leitura = temperaturas.registrar(id, dados);
        return ResponseEntity
                .created(URI.create("/api/v1/rotas/" + id + "/temperaturas/" + leitura.id()))
                .body(leitura);
    }

    @GetMapping("/{id}/temperaturas")
    public List<LeituraTemperaturaResponse> historicoTemperatura(@PathVariable String id) {
        return temperaturas.historico(id);
    }
}
