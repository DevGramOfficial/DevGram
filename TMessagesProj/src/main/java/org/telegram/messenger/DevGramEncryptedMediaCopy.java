package org.telegram.messenger;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Copies Telegram's local .enc cache into the explicitly enabled retained-media folder. */
final class DevGramEncryptedMediaCopy {
    static void copy(File encrypted, File keyFile, File destination, long expectedSize) throws Exception {
        if (!encrypted.isFile() || encrypted.length() <= 0 || keyFile.length() != 48
                || expectedSize > 0 && encrypted.length() != expectedSize) {
            throw new IOException("Incomplete encrypted media or missing cache key");
        }
        byte[] key = new byte[32];
        byte[] iv = new byte[16];
        File temporary = null;
        try {
            try (DataInputStream input = new DataInputStream(new FileInputStream(keyFile))) {
                input.readFully(key);
                input.readFully(iv);
            }
            // FileLoadOperation uses the first 12 IV bytes and a big-endian block
            // offset in the final four bytes; the key file's original counter is random.
            Arrays.fill(iv, 12, 16, (byte) 0);
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            temporary = File.createTempFile(".retaining-", ".tmp", destination.getParentFile());
            long length = encrypted.length();
            try (CipherInputStream input = new CipherInputStream(new FileInputStream(encrypted), cipher);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (temporary.length() != length || !temporary.renameTo(destination)) {
                throw new IOException("Could not retain complete decrypted media");
            }
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            if (temporary != null) temporary.delete();
        }
    }

    private DevGramEncryptedMediaCopy() {}
}
