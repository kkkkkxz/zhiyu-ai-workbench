package com.zwskchat.zwskchat.crm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.crm.dto.ContactCreateRequest;
import com.zwskchat.zwskchat.crm.dto.CustomerCreateRequest;
import com.zwskchat.zwskchat.crm.dto.CustomerUpdateRequest;
import com.zwskchat.zwskchat.crm.dto.FollowUpCreateRequest;
import com.zwskchat.zwskchat.crm.entity.BizContact;
import com.zwskchat.zwskchat.crm.entity.BizCustomer;
import com.zwskchat.zwskchat.crm.entity.BizFollowUp;
import com.zwskchat.zwskchat.crm.service.CustomerService;
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

@Tag(name = "CRM")
@RestController
@RequestMapping("/crm")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CustomerController {

    private final CustomerService customerService;

    @Operation(summary = "List customers")
    @GetMapping("/customers")
    public Result<IPage<BizCustomer>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(customerService.listCustomers(new Page<>(page, size), status, keyword));
    }

    @Operation(summary = "Get customer")
    @GetMapping("/customers/{id}")
    public Result<BizCustomer> get(@PathVariable Long id) {
        return Result.success(customerService.getById(id));
    }

    @Operation(summary = "Create customer")
    @PostMapping("/customers")
    public Result<BizCustomer> create(@RequestBody @Valid CustomerCreateRequest request) {
        return Result.success(customerService.create(toCustomer(request)));
    }

    @Operation(summary = "Update customer")
    @PutMapping("/customers/{id}")
    public Result<BizCustomer> update(@PathVariable Long id, @RequestBody @Valid CustomerUpdateRequest request) {
        return Result.success(customerService.update(id, toCustomer(request)));
    }

    @Operation(summary = "Delete customer")
    @DeleteMapping("/customers/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return Result.success();
    }

    @Operation(summary = "List contacts")
    @GetMapping("/customers/{customerId}/contacts")
    public Result<List<BizContact>> listContacts(@PathVariable Long customerId) {
        return Result.success(customerService.listContacts(customerId));
    }

    @Operation(summary = "Create contact")
    @PostMapping("/customers/{customerId}/contacts")
    public Result<BizContact> createContact(@PathVariable Long customerId,
                                            @RequestBody @Valid ContactCreateRequest request) {
        BizContact contact = toContact(request);
        contact.setCustomerId(customerId);
        return Result.success(customerService.createContact(contact));
    }

    @Operation(summary = "Delete contact")
    @DeleteMapping("/contacts/{contactId}")
    public Result<Void> deleteContact(@PathVariable Long contactId) {
        customerService.deleteContact(contactId);
        return Result.success();
    }

    @Operation(summary = "List follow-ups")
    @GetMapping("/customers/{customerId}/follow-ups")
    public Result<IPage<BizFollowUp>> listFollowUps(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(customerService.listFollowUps(customerId, new Page<>(page, size)));
    }

    @Operation(summary = "Create follow-up")
    @PostMapping("/customers/{customerId}/follow-ups")
    public Result<BizFollowUp> createFollowUp(@PathVariable Long customerId,
                                              @RequestBody @Valid FollowUpCreateRequest request) {
        BizFollowUp followUp = toFollowUp(request);
        followUp.setCustomerId(customerId);
        return Result.success(customerService.createFollowUp(followUp));
    }

    private BizCustomer toCustomer(CustomerCreateRequest request) {
        BizCustomer customer = new BizCustomer();
        customer.setName(request.getName());
        customer.setShortName(request.getShortName());
        customer.setType(request.getType());
        customer.setIndustry(request.getIndustry());
        customer.setScale(request.getScale());
        customer.setSource(request.getSource());
        customer.setOwnerId(request.getOwnerId());
        customer.setStatus(request.getStatus());
        customer.setGrade(request.getGrade());
        customer.setAiScore(request.getAiScore());
        customer.setLifecycleStage(request.getLifecycleStage());
        customer.setAiNextAction(request.getAiNextAction());
        customer.setAiValuePredict(request.getAiValuePredict());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        customer.setAddress(request.getAddress());
        customer.setRemark(request.getRemark());
        return customer;
    }

    private BizCustomer toCustomer(CustomerUpdateRequest request) {
        BizCustomer customer = new BizCustomer();
        customer.setName(request.getName());
        customer.setShortName(request.getShortName());
        customer.setType(request.getType());
        customer.setIndustry(request.getIndustry());
        customer.setScale(request.getScale());
        customer.setSource(request.getSource());
        customer.setOwnerId(request.getOwnerId());
        customer.setStatus(request.getStatus());
        customer.setGrade(request.getGrade());
        customer.setAiScore(request.getAiScore());
        customer.setLifecycleStage(request.getLifecycleStage());
        customer.setAiNextAction(request.getAiNextAction());
        customer.setAiValuePredict(request.getAiValuePredict());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        customer.setAddress(request.getAddress());
        customer.setRemark(request.getRemark());
        return customer;
    }

    private BizContact toContact(ContactCreateRequest request) {
        BizContact contact = new BizContact();
        contact.setName(request.getName());
        contact.setPosition(request.getPosition());
        contact.setPhone(request.getPhone());
        contact.setEmail(request.getEmail());
        contact.setIsPrimary(request.getIsPrimary());
        return contact;
    }

    private BizFollowUp toFollowUp(FollowUpCreateRequest request) {
        BizFollowUp followUp = new BizFollowUp();
        followUp.setType(request.getType());
        followUp.setContent(request.getContent());
        followUp.setNextPlan(request.getNextPlan());
        followUp.setNextTime(request.getNextTime());
        return followUp;
    }
}
