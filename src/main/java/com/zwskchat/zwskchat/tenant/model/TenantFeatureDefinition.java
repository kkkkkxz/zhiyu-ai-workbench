package com.zwskchat.zwskchat.tenant.model;

public record TenantFeatureDefinition(String code,
                                      String name,
                                      String category,
                                      String description,
                                      boolean enabledByDefault,
                                      int sort) {
}
