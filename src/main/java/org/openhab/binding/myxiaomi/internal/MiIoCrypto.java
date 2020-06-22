/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.myxiaomi.internal;

import org.apache.commons.net.util.Base64;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.Test;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;
/**
 * The {@link MiIoCrypto} is responsible for creating Xiaomi messages.
 *
 * @author zaoweiceng
 */
@NonNullByDefault
public class MiIoCrypto {
    public static byte[] md5(byte[] source) throws NoSuchAlgorithmException {
        MessageDigest m = MessageDigest.getInstance("MD5");
        return m.digest(source);
    }

    public static byte[] iv(byte[] token) throws NoSuchAlgorithmException {
        MessageDigest m = MessageDigest.getInstance("MD5");
        byte[] ivbuf = new byte[32];
        System.arraycopy(m.digest(token), 0, ivbuf, 0, 16);
        System.arraycopy(token, 0, ivbuf, 16, 0);
        return m.digest(ivbuf);
    }

    public static byte[] encrypt(byte[] cipherText, byte[] key, byte[] iv) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        IvParameterSpec vector = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, vector);
        byte[] encryted = cipher.doFinal(cipherText);
        return encryted;
    }

    public static byte[] encrypt(byte[] text, byte[] token) throws NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {
        return encrypt(text,md5(token), iv(token));
    }

    public static byte[] decrypt(byte[] cipherText, byte[] key, byte[] iv) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        IvParameterSpec vector = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, vector);
        byte[] crypted = cipher.doFinal(cipherText);
        return (crypted);
    }
    public static byte[] decrypt(byte[] cipherText, byte[] token) throws  NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {
        return decrypt(cipherText, md5(token), iv(token));
    }
    public static String decryptToken(byte[] cipherText) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

            SecretKeySpec keySpec = new SecretKeySpec(new byte[16], "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(cipherText);
            try {
                return new String(decrypted, "UTF-8").trim();
            } catch (UnsupportedEncodingException e) {
                return new String(decrypted).trim();
            }
        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException
                | BadPaddingException e) {
        }
        return "";
    }

//    @Test
//    public void Test(){
//        long l = TimeUnit.MILLISECONDS.toSeconds(Calendar.getInstance().getTime().getTime());
//        System.out.println(l);
//        System.out.println(Calendar.getInstance());
//        System.out.println(TimeUnit.MILLISECONDS.toDays(Calendar.getInstance().getTime().getTime()));
//    }


//    @Test
//    public void test() throws Exception {
//        String pwd = "1234";
////        String s = encrypt(pwd, "smkldospdosldaaa", "0392039203920300");
////        System.out.println(decrypt(s, "smkldospdosldaaa",  "0392039203920300"));
//        byte[] encrypt = encrypt(pwd.getBytes(), "smkldospdosldaaa".getBytes(), "0392039203920300".getBytes());
//        System.out.println(Base64.encodeBase64String(encrypt));
//    }

    public String encrypt(String content, String slatKey, String vectorKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKey secretKey = new SecretKeySpec(slatKey.getBytes(), "AES");
        IvParameterSpec iv = new IvParameterSpec(vectorKey.getBytes());
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
        byte[] encrypted = cipher.doFinal(content.getBytes());
        return Base64.encodeBase64String(encrypted);
    }

    public String decrypt(String base64Content, String slatKey, String vectorKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKey secretKey = new SecretKeySpec(slatKey.getBytes(), "AES");
        IvParameterSpec iv = new IvParameterSpec(vectorKey.getBytes());
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
        byte[] content = Base64.decodeBase64(base64Content);
        byte[] encrypted = cipher.doFinal(content);
        return new String(encrypted);
    }

}
