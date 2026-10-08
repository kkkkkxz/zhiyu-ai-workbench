package com.zwskchat.zwskchat.persona.controller;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.persona.dto.PersonaResponse;
import com.zwskchat.zwskchat.persona.service.PersonaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "对话人设")
@RestController
@RequestMapping("/personas")
@RequiredArgsConstructor
public class PersonaController {

    private final PersonaService personaService;

    @Operation(summary = "内置对话人设列表")
    @GetMapping
    public Result<List<PersonaResponse>> list() {
        return Result.success(personaService.listEnabled());
    }
}
