package in.wangziq.fitnessrecorder.utils;

import android.support.annotation.Nullable;

import java.util.Random;

public final class BytesUtil {

    public static byte[] combine(byte[] a, byte[] b) {
        if (b == null) return a;

        byte[] bytes = new byte[a.length + b.length];
        System.arraycopy(a, 0, bytes, 0, a.length);
        System.arraycopy(b, 0, bytes, a.length, b.length);
        return bytes;
    }

    public static String toHexStr(byte[] data) {
//        if (data == null) return "";
        if (data == null) return null;

        StringBuilder str = new StringBuilder(data.length);
        for (byte b: data)
            str.append(String.format("%02x", b));
        return str.toString();
    }

    public static byte[] hexStrToBytes(@Nullable String hex) {
        if (hex == null) return null;

        final int LENGTH = hex.length();
        if (LENGTH % 2 != 0) throw new IllegalArgumentException("String length should be even!");
        final int HALF_LENGTH = LENGTH / 2;

        char[] hexArr = hex.toCharArray();
        byte[] bytes = new byte[HALF_LENGTH];
        for (int i = 0; i < HALF_LENGTH; ++i) {
            char l = hexArr[i * 2], r = hexArr[i * 2 + 1];
            bytes[i] = (byte)(('0'<=l&&l<='9' ? l-'0' : l-'a'+0xa) << 4 |
                              ('0'<=r&&r<='9' ? r-'0' : r-'a'+0xa));
        }
        return bytes;
    }

    public static byte[] random(int size) {
        if (size < 0)
            throw new IllegalArgumentException("size should be greater than 0!");

        byte[] bytes = new byte[size];
        new Random().nextBytes(bytes);
        return bytes;
    }

}
