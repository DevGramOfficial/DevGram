package org.telegram.messenger.forkgram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/** Reads the actual signed archive's metadata, never its version-looking filename/tag.
 * Range requests cover only the ZIP directory and AndroidManifest.xml. Android's
 * package installer still verifies the APK signature before installation.
 */
public final class ReleaseApkInfo {
    private static final int MAX_DIRECTORY = 4 * 1024 * 1024;
    private static final int MAX_MANIFEST = 512 * 1024;
    private static final long MAX_APK = 2L * 1024 * 1024 * 1024;
    public final String packageName, versionName;
    public final long versionCode;
    public final boolean debuggable;

    private ReleaseApkInfo(String pkg, String name, long code, boolean debug) {
        packageName = pkg; versionName = name; versionCode = code; debuggable = debug;
    }

    public boolean isNewerThan(String installedPackage, long installedCode) {
        return !debuggable && packageName.equals(installedPackage) && versionCode > installedCode;
    }

    public interface Ranges { byte[] read(long start, int length) throws IOException; }

    public static ReleaseApkInfo read(String url, long size) throws IOException {
        return read(size, (start, length) -> httpRange(url, size, start, length));
    }

    public static ReleaseApkInfo read(long size, Ranges ranges) throws IOException {
        if (size < 22 || size > MAX_APK) throw invalid();
        int tailSize = (int) Math.min(size, 65557);
        byte[] tail = range(ranges, size - tailSize, tailSize);
        int end = -1;
        for (int at = tail.length - 22; at >= 0; at--) {
            if (u32(tail, at) == 0x06054b50L && at + 22 + u16(tail, at + 20) == tail.length) { end = at; break; }
        }
        if (end < 0 || u16(tail, end + 4) != 0 || u16(tail, end + 6) != 0) throw invalid();
        int entries = u16(tail, end + 10);
        long directorySize = u32(tail, end + 12), directoryAt = u32(tail, end + 16);
        if (entries < 1 || entries == 65535 || entries != u16(tail, end + 8)
                || directorySize < 46 || directorySize > MAX_DIRECTORY
                || directoryAt + directorySize > size - tailSize + end) throw invalid();
        byte[] directory = range(ranges, directoryAt, (int) directorySize);
        int at = 0;
        long localAt = -1, crc = 0;
        int packed = 0, unpacked = 0, method = -1;
        for (int i = 0; i < entries; i++) {
            require(directory, at, 46);
            if (u32(directory, at) != 0x02014b50L) throw invalid();
            int nameSize = u16(directory, at + 28), extraSize = u16(directory, at + 30), commentSize = u16(directory, at + 32);
            require(directory, at, 46 + nameSize + extraSize + commentSize);
            String name = new String(directory, at + 46, nameSize, StandardCharsets.UTF_8);
            if (name.equals("AndroidManifest.xml")) {
                if (localAt >= 0 || (u16(directory, at + 8) & 1) != 0 || u16(directory, at + 34) != 0) throw invalid();
                long compressed = u32(directory, at + 20), raw = u32(directory, at + 24);
                if (compressed < 1 || compressed > MAX_MANIFEST || raw < 8 || raw > MAX_MANIFEST) throw invalid();
                packed = (int) compressed; unpacked = (int) raw; method = u16(directory, at + 10);
                crc = u32(directory, at + 16); localAt = u32(directory, at + 42);
            }
            at += 46 + nameSize + extraSize + commentSize;
        }
        if (localAt < 0 || localAt + 30 > directoryAt || method != 0 && method != 8) throw invalid();
        byte[] header = range(ranges, localAt, 30);
        if (u32(header, 0) != 0x04034b50L || (u16(header, 6) & 1) != 0 || u16(header, 8) != method) throw invalid();
        int nameSize = u16(header, 26), extraSize = u16(header, 28), prefix = nameSize + extraSize;
        if (localAt + 30 + prefix + packed > directoryAt) throw invalid();
        byte[] payload = range(ranges, localAt + 30, prefix + packed);
        if (!new String(payload, 0, nameSize, StandardCharsets.UTF_8).equals("AndroidManifest.xml")) throw invalid();
        byte[] manifest;
        if (method == 0) {
            if (packed != unpacked) throw invalid();
            manifest = new byte[unpacked]; System.arraycopy(payload, prefix, manifest, 0, unpacked);
        } else {
            Inflater inflater = new Inflater(true);
            try (InputStream stream = new InflaterInputStream(new ByteArrayInputStream(payload, prefix, packed), inflater)) {
                manifest = bounded(stream, unpacked);
            } finally { inflater.end(); }
        }
        CRC32 checksum = new CRC32(); checksum.update(manifest);
        if (checksum.getValue() != crc) throw invalid();
        return manifest(manifest);
    }

    private static byte[] range(Ranges source, long start, int length) throws IOException {
        if (start < 0 || length < 1 || length > MAX_DIRECTORY) throw invalid();
        byte[] value = source.read(start, length);
        if (value == null || value.length != length) throw invalid();
        return value;
    }

    private static byte[] bounded(InputStream input, int length) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(length, 32768));
        byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer, 0, Math.min(buffer.length, length + 1 - output.size()))) != -1) {
            output.write(buffer, 0, count);
            if (output.size() > length) throw invalid();
        }
        if (output.size() != length) throw invalid();
        return output.toByteArray();
    }

    private static byte[] httpRange(String address, long size, long start, int length) throws IOException {
        URL url = new URL(address);
        for (int redirect = 0; redirect <= 5; redirect++) {
            if (!url.getProtocol().equals("https") || url.getUserInfo() != null || url.getPort() != -1 && url.getPort() != 443) throw invalid();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
            connection.setRequestProperty("Range", "bytes=" + start + "-" + (start + length - 1));
            connection.setRequestProperty("Accept-Encoding", "identity");
            try {
                int code = connection.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw invalid();
                    url = new URL(url, location); continue;
                }
                String expected = "bytes " + start + "-" + (start + length - 1) + "/" + size;
                if (code != 206 || !expected.equals(connection.getHeaderField("Content-Range"))) throw invalid();
                String encoding = connection.getContentEncoding();
                if (encoding != null && !encoding.equalsIgnoreCase("identity")) throw invalid();
                try (InputStream stream = connection.getInputStream()) { return bounded(stream, length); }
            } finally { connection.disconnect(); }
        }
        throw invalid();
    }

    private static ReleaseApkInfo manifest(byte[] data) throws IOException {
        if (u16(data, 0) != 3 || u16(data, 2) != 8 || u32(data, 4) != data.length) throw invalid();
        Strings strings = null; String pkg = null, name = ""; long low = -1, major = 0; boolean debug = false;
        for (int at = 8; at < data.length;) {
            require(data, at, 8); int type = u16(data, at), header = u16(data, at + 2); long length = u32(data, at + 4);
            if (header < 8 || length < header || length > data.length - at) throw invalid();
            if (type == 1) {
                if (strings != null) throw invalid();
                strings = new Strings(data, at, (int) length, header);
            } else if (type == 0x102) {
                if (strings == null || header < 16 || header + 20 > length) throw invalid();
                int ext = at + header; String element = strings.get(u32(data, ext + 4));
                int offset = u16(data, ext + 8), stride = u16(data, ext + 10), count = u16(data, ext + 12);
                if (offset < 20 || stride < 20 || count > 2048 || header + offset + (long) stride * count > length) throw invalid();
                for (int i = 0; i < count; i++) {
                    int attr = ext + offset + i * stride; String key = strings.get(u32(data, attr + 4));
                    String ns = strings.get(u32(data, attr)); int valueType = data[attr + 15] & 255; long value = u32(data, attr + 16);
                    if (u16(data, attr + 12) < 8) throw invalid();
                    if (element.equals("manifest") && key.equals("package") && ns.isEmpty()) {
                        if (pkg != null || valueType != 3) throw invalid(); pkg = strings.get(value);
                    } else if (ns.equals("http://schemas.android.com/apk/res/android")) {
                        if (element.equals("manifest") && key.equals("versionCode")) {
                            if (low >= 0 || valueType != 16 && valueType != 17) throw invalid(); low = value;
                        } else if (element.equals("manifest") && key.equals("versionCodeMajor")) {
                            if (valueType != 16 && valueType != 17 || value > Integer.MAX_VALUE) throw invalid(); major = value;
                        } else if (element.equals("manifest") && key.equals("versionName") && valueType == 3) name = strings.get(value);
                        else if (element.equals("application") && key.equals("debuggable")) debug = valueType != 18 || value != 0;
                    }
                }
            }
            at += (int) length;
        }
        if (pkg == null || pkg.isEmpty() || low <= 0) throw invalid();
        return new ReleaseApkInfo(pkg, name, major << 32 | low, debug);
    }

    private static final class Strings {
        private final byte[] data; private final int at, end, count, header, start; private final boolean utf8;
        Strings(byte[] data, int at, int length, int header) throws IOException {
            require(data, at, 28); this.data = data; this.at = at; this.header = header; end = at + length;
            long n = u32(data, at + 8), base = u32(data, at + 20), styles = u32(data, at + 12);
            if (header < 28 || n > 65536 || header + 4 * (n + styles) > base || base > length) throw invalid();
            count = (int) n; start = at + (int) base; utf8 = (u32(data, at + 16) & 256) != 0;
        }
        String get(long index) throws IOException {
            if (index == 0xffffffffL) return "";
            if (index < 0 || index >= count) throw invalid();
            long offset = u32(data, at + header + (int) index * 4); if (offset > end - start) throw invalid();
            int pos = start + (int) offset, length;
            if (utf8) {
                if (pos >= end) throw invalid(); int units = data[pos++] & 255;
                if ((units & 128) != 0) { if (pos >= end) throw invalid(); pos++; }
                if (pos >= end) throw invalid(); length = data[pos++] & 255;
                if ((length & 128) != 0) { if (pos >= end) throw invalid(); length = (length & 127) << 8 | data[pos++] & 255; }
                if (length > end - pos - 1 || data[pos + length] != 0) throw invalid();
                return new String(data, pos, length, StandardCharsets.UTF_8);
            }
            length = u16(data, pos); pos += 2;
            if ((length & 32768) != 0) { length = (length & 32767) << 16 | u16(data, pos); pos += 2; }
            if (length < 0 || length > (end - pos - 2) / 2 || u16(data, pos + length * 2) != 0) throw invalid();
            return new String(data, pos, length * 2, StandardCharsets.UTF_16LE);
        }
    }

    private static void require(byte[] data, int at, int size) throws IOException {
        if (at < 0 || size < 0 || at > data.length - size) throw invalid();
    }
    private static int u16(byte[] data, int at) throws IOException { require(data, at, 2); return (data[at] & 255) | (data[at + 1] & 255) << 8; }
    private static long u32(byte[] data, int at) throws IOException { return (long) u16(data, at) | (long) u16(data, at + 2) << 16; }
    private static IOException invalid() { return new IOException("Invalid APK update metadata or unsupported range response"); }
}
