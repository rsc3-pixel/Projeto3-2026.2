package com.rotavital.api;

import com.rotavital.api.dto.HospitalRequest;
import com.rotavital.api.dto.HospitalResponse;
import com.rotavital.api.dto.RequisicaoResponse;
import com.rotavital.dominio.Hospital;
import com.rotavital.dominio.enums.StatusRequisicao;
import com.rotavital.servico.HospitalServico;
import com.rotavital.servico.RequisicaoServico;
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
 * Endpoints de hospital, conforme a secao 2.2 do docs/contrato_api.md.
 */
@Tag(name = "Hospitais")
@RestController
@RequestMapping("/api/v1/hospitais")
public class HospitalController {

    private final HospitalServico hospitais;
    private final RequisicaoServico requisicoes;

    public HospitalController(HospitalServico hospitais, RequisicaoServico requisicoes) {
        this.hospitais = hospitais;
        this.requisicoes = requisicoes;
    }

    @GetMapping
    public List<HospitalResponse> listar(
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String uf) {
        return hospitais.listar(cidade, uf).stream()
                .map(HospitalResponse::de)
                .toList();
    }

    @GetMapping("/{id}")
    public HospitalResponse buscar(@PathVariable String id) {
        return HospitalResponse.de(hospitais.buscar(id));
    }

    @PostMapping
    public ResponseEntity<HospitalResponse> criar(@Valid @RequestBody HospitalRequest dados) {
        Hospital criado = hospitais.criar(dados);
        return ResponseEntity
                .created(URI.create("/api/v1/hospitais/" + criado.getId()))
                .body(HospitalResponse.de(criado));
    }

    @PutMapping("/{id}")
    public HospitalResponse atualizar(@PathVariable String id,
                                      @Valid @RequestBody HospitalRequest dados) {
        return HospitalResponse.de(hospitais.atualizar(id, dados));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable String id) {
        hospitais.remover(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/requisicoes")
    public List<RequisicaoResponse> requisicoesDoHospital(
            @PathVariable String id,
            @RequestParam(required = false) StatusRequisicao status) {
        return requisicoes.listarPorHospital(id, status);
    }
}
