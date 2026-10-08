package com.zwskchat.zwskchat.common.crypto;

public interface SecretCryptoService {

    String encrypt(String plainText);

    String decrypt(String cipherText);
}
