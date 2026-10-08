package com.zwskchat.zwskchat.contract.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.contract.dto.ContractCreateRequest;
import com.zwskchat.zwskchat.contract.dto.ContractUpdateRequest;
import com.zwskchat.zwskchat.contract.dto.MilestoneCreateRequest;
import com.zwskchat.zwskchat.contract.entity.BizContract;
import com.zwskchat.zwskchat.contract.entity.BizContractMilestone;
import com.zwskchat.zwskchat.contract.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Contracts")
@RestController
@RequestMapping("/contracts")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ContractController {

    private final ContractService contractService;

    @Operation(summary = "List contracts")
    @GetMapping
    public Result<IPage<BizContract>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(contractService.list(new Page<>(page, size), status, keyword));
    }

    @Operation(summary = "Get contract")
    @GetMapping("/{id}")
    public Result<BizContract> get(@PathVariable Long id) {
        return Result.success(contractService.getById(id));
    }

    @Operation(summary = "Create contract")
    @PostMapping
    public Result<BizContract> create(@RequestBody @Valid ContractCreateRequest request) {
        return Result.success(contractService.create(toContract(request)));
    }

    @Operation(summary = "Update contract")
    @PutMapping("/{id}")
    public Result<BizContract> update(@PathVariable Long id, @RequestBody @Valid ContractUpdateRequest request) {
        return Result.success(contractService.update(id, toContract(request)));
    }

    @Operation(summary = "Delete contract")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        contractService.delete(id);
        return Result.success();
    }

    @Operation(summary = "List milestones")
    @GetMapping("/{contractId}/milestones")
    public Result<List<BizContractMilestone>> listMilestones(@PathVariable Long contractId) {
        return Result.success(contractService.listMilestones(contractId));
    }

    @Operation(summary = "Create milestone")
    @PostMapping("/{contractId}/milestones")
    public Result<BizContractMilestone> createMilestone(@PathVariable Long contractId,
                                                        @RequestBody @Valid MilestoneCreateRequest request) {
        BizContractMilestone milestone = toMilestone(request);
        milestone.setContractId(contractId);
        return Result.success(contractService.createMilestone(milestone));
    }

    @Operation(summary = "Update milestone status")
    @PutMapping("/milestones/{milestoneId}/status")
    public Result<Void> updateMilestoneStatus(@PathVariable Long milestoneId, @RequestParam String status) {
        contractService.updateMilestoneStatus(milestoneId, status);
        return Result.success();
    }

    private BizContract toContract(ContractCreateRequest request) {
        BizContract contract = new BizContract();
        contract.setContractNo(request.getContractNo());
        contract.setTitle(request.getTitle());
        contract.setCustomerId(request.getCustomerId());
        contract.setType(request.getType());
        contract.setAmount(request.getAmount());
        contract.setCurrency(request.getCurrency());
        contract.setSignDate(request.getSignDate());
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setStatus(request.getStatus());
        contract.setOwnerId(request.getOwnerId());
        contract.setFileKey(request.getFileKey());
        contract.setRemark(request.getRemark());
        return contract;
    }

    private BizContract toContract(ContractUpdateRequest request) {
        BizContract contract = new BizContract();
        contract.setContractNo(request.getContractNo());
        contract.setTitle(request.getTitle());
        contract.setCustomerId(request.getCustomerId());
        contract.setType(request.getType());
        contract.setAmount(request.getAmount());
        contract.setCurrency(request.getCurrency());
        contract.setSignDate(request.getSignDate());
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setStatus(request.getStatus());
        contract.setOwnerId(request.getOwnerId());
        contract.setFileKey(request.getFileKey());
        contract.setRemark(request.getRemark());
        return contract;
    }

    private BizContractMilestone toMilestone(MilestoneCreateRequest request) {
        BizContractMilestone milestone = new BizContractMilestone();
        milestone.setName(request.getName());
        milestone.setAmount(request.getAmount());
        milestone.setDueDate(request.getDueDate());
        milestone.setStatus(request.getStatus());
        return milestone;
    }
}
