package com.zwskchat.zwskchat.template.service;

import com.zwskchat.zwskchat.template.entity.TplInstallRecord;
import com.zwskchat.zwskchat.template.entity.TplPackage;

import java.util.List;

public interface TemplateService {

    List<TplPackage> listPackages();

    List<TplInstallRecord> listInstalled();

    TplInstallRecord install(String packageCode);
}
