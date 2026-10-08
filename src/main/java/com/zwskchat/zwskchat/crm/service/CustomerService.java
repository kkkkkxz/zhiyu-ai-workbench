package com.zwskchat.zwskchat.crm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.crm.entity.BizContact;
import com.zwskchat.zwskchat.crm.entity.BizCustomer;
import com.zwskchat.zwskchat.crm.entity.BizFollowUp;

import java.util.List;

public interface CustomerService {
    IPage<BizCustomer> listCustomers(Page<BizCustomer> page, String status, String keyword);
    BizCustomer getById(Long id);
    BizCustomer create(BizCustomer customer);
    BizCustomer update(Long id, BizCustomer customer);
    void delete(Long id);

    List<BizContact> listContacts(Long customerId);
    BizContact createContact(BizContact contact);
    void deleteContact(Long contactId);

    IPage<BizFollowUp> listFollowUps(Long customerId, Page<BizFollowUp> page);
    BizFollowUp createFollowUp(BizFollowUp followUp);
}
