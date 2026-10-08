package com.zwskchat.zwskchat.finetune;

/**
 * 一条监督微调（SFT）样本：system / user / assistant 三元组。
 * 对应工程计划书“模型、微调与规则融合”章节的训练数据产出。
 */
public record SftSample(String system, String user, String assistant) {
}
