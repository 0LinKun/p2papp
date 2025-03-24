package sample.AllNeed;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.security.*;
import java.util.Arrays;

public class AESStreamCipher {
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int IV_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    private static final int HMAC_LENGTH = 32;

    // 生成AES密钥（256位）
    public static SecretKey generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(KEY_LENGTH);
        return keyGen.generateKey();
    }

    // 加密流（自动处理IV和HMAC）
    public static void encryptStream(SecretKey key, InputStream in, OutputStream out)
            throws GeneralSecurityException, IOException {

        try (DataOutputStream dos = new DataOutputStream(out)) {
            // 生成并写入IV
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            dos.write(iv);

            // 初始化加密器和HMAC
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,  key, new IvParameterSpec(iv));

            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            hmac.init(new  SecretKeySpec(key.getEncoded(),  HMAC_ALGORITHM));

            try (CipherOutputStream cos = new CipherOutputStream(dos, cipher)) {
                byte[] buffer = new byte[4096];
                int bytesRead;

                // 双缓冲设计：同时处理加密和HMAC计算
                ByteArrayOutputStream hmacBuffer = new ByteArrayOutputStream();
                while ((bytesRead = in.read(buffer))  != -1) {
                    cos.write(buffer,  0, bytesRead);
                    hmacBuffer.write(buffer,  0, bytesRead); // 原始明文用于HMAC
                    hmac.update(buffer,  0, bytesRead);
                }

                // 独立写入HMAC（避免流关闭影响）
                dos.write(hmac.doFinal());
            }
        }
    }

    // 解密流（带完整性验证）
    public static void decryptStream(SecretKey key, InputStream in, OutputStream out)
            throws GeneralSecurityException, IOException {

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(in))) {
            // 读取IV（强制校验长度）
            byte[] iv = new byte[IV_LENGTH];
            dis.readFully(iv);  // 精确读取16字节

            // 初始化HMAC
            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            hmac.init(new  SecretKeySpec(key.getEncoded(),  HMAC_ALGORITHM));

            // 分块读取加密数据
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,  key, new IvParameterSpec(iv));

            try (CipherInputStream cis = new CipherInputStream(dis, cipher)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                ByteArrayOutputStream hmacData = new ByteArrayOutputStream();

                // 第一层：解密数据并收集HMAC输入
                while ((bytesRead = cis.read(buffer))  != -1) {
                    out.write(buffer,  0, bytesRead);
                    hmacData.write(buffer,  0, bytesRead); // 收集解密后的明文用于HMAC验证
                }

                // 读取并验证HMAC
                byte[] storedHmac = new byte[HMAC_LENGTH];
                dis.readFully(storedHmac);  // 精确读取32字节HMAC

                if (!MessageDigest.isEqual(
                        hmac.doFinal(hmacData.toByteArray()),
                        storedHmac)) {
                    throw new SecurityException("HMAC校验失败：数据可能被篡改");
                }
            }
        }
    }

    // 示例用法
    public static void main(String[] args) throws Exception {
        SecretKey key = generateKey();

        // 加密示例
        try (FileInputStream fis = new FileInputStream("input.txt");
             FileOutputStream fos = new FileOutputStream("encrypted.bin"))  {
            encryptStream(key, fis, fos);
        }

        // 解密示例
        try (FileInputStream fis = new FileInputStream("encrypted.bin");
             FileOutputStream fos = new FileOutputStream("decrypted.txt"))  {
            decryptStream(key, fis, fos);
        }
    }
}