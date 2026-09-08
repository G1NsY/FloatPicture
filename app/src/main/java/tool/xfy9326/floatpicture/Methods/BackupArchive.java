package tool.xfy9326.floatpicture.Methods;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.*;

/** Portable, versioned backup format. No installation path or application ID is stored. */
public final class BackupArchive {
    public static final long MAX_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_FILES = 10000;
    private static final int MAX_METADATA_BYTES = 4 * 1024 * 1024;
    private static final String MANIFEST = "backup.properties";
    private static final String[] DATA_FILES = {
            "PictureList.list", "PictureData.list", "PictureOrder.list"};

    private BackupArchive() { }

    public static void write(File root, File archive) throws IOException {
        Map<String, File> files = collect(root);
        Properties manifest = new Properties();
        manifest.setProperty("format", "FloatPictureBackup");
        manifest.setProperty("version", "1");
        manifest.setProperty("created", Long.toString(System.currentTimeMillis()));
        long total = 0;
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(
                new FileOutputStream(archive)))) {
            for (Map.Entry<String, File> item : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(item.getKey()));
                MessageDigest digest = digest();
                try (InputStream input = item.getValue() == null
                        ? new ByteArrayInputStream("{}".getBytes("UTF-8"))
                        : new FileInputStream(item.getValue())) {
                    total += copy(input, zip, digest, MAX_BYTES - total);
                }
                zip.closeEntry();
                manifest.setProperty("sha256." + item.getKey(), hex(digest.digest()));
            }
            // Reject a backup if editing changed the source while it was being read.
            Map<String, File> after = collect(root);
            if (!files.keySet().equals(after.keySet())) throw new IOException("Data changed");
            for (Map.Entry<String, File> item : after.entrySet()) {
                MessageDigest digest = digest();
                try (InputStream input = item.getValue() == null
                        ? new ByteArrayInputStream("{}".getBytes("UTF-8"))
                        : new FileInputStream(item.getValue())) {
                    copy(input, null, digest, MAX_BYTES);
                }
                if (!hex(digest.digest()).equals(manifest.getProperty("sha256." + item.getKey()))) {
                    throw new IOException("Data changed");
                }
            }
            zip.putNextEntry(new ZipEntry(MANIFEST));
            manifest.store(zip, "FloatPicture backup");
            zip.closeEntry();
        }
    }

    private static Map<String, File> collect(File root) throws IOException {
        Map<String, File> files = new TreeMap<>();
        for (String name : DATA_FILES) {
            File file = new File(root, "Data/" + name);
            if (file.exists()) {
                if (!file.isFile() || file.length() > MAX_METADATA_BYTES) {
                    throw new IOException("Invalid metadata");
                }
                files.put("Data/" + name, file);
            }
        }
        File pictures = new File(root, "Pictures");
        if (pictures.exists()) {
            File[] children = pictures.listFiles();
            if (children == null) throw new IOException("Cannot read pictures");
            for (File child : children) {
                if (child.getName().equals(".TEMP") || child.getName().equals(".nomedia")) continue;
                String name = "Pictures/" + child.getName();
                if (!allowed(name) || !child.isFile()
                        || !child.getCanonicalFile().getParentFile().equals(pictures.getCanonicalFile())) {
                    throw new IOException("Invalid picture file");
                }
                files.put(name, child);
            }
        }
        if (!files.containsKey("Data/PictureList.list") || !files.containsKey("Data/PictureData.list")) {
            if (!files.isEmpty()) throw new IOException("Missing metadata");
            files.put("Data/PictureList.list", null);
            files.put("Data/PictureData.list", null);
        }
        if (files.size() > MAX_FILES) throw new IOException("Too many files");
        return files;
    }

    /** Extract only into a newly created private staging directory, never into live data. */
    public static void extract(File archive, File stage) throws IOException {
        if (stage.exists() || !stage.mkdirs()) throw new IOException("Staging directory exists");
        mkdir(new File(stage, "Data"));
        mkdir(new File(stage, "Pictures"));
        try (ZipFile zip = new ZipFile(archive)) {
            if (zip.size() > MAX_FILES + 1) throw new IOException("Too many files");
            ZipEntry manifestEntry = zip.getEntry(MANIFEST);
            if (manifestEntry == null) throw new IOException("Not a FloatPicture backup");
            Properties manifest = new Properties();
            try (InputStream input = zip.getInputStream(manifestEntry)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                copy(input, bytes, null, MAX_METADATA_BYTES);
                manifest.load(new ByteArrayInputStream(bytes.toByteArray()));
            }
            if (!"FloatPictureBackup".equals(manifest.getProperty("format"))
                    || !"1".equals(manifest.getProperty("version"))) {
                throw new IOException("Unsupported backup version");
            }
            Set<String> names = new HashSet<>();
            long total = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!names.add(name)) throw new IOException("Duplicate entry");
                if (MANIFEST.equals(name)) continue;
                if (entry.isDirectory() || !allowed(name)) throw new IOException("Unsafe entry");
                File target = new File(stage, name);
                if (!target.getCanonicalPath().startsWith(stage.getCanonicalPath() + File.separator)) {
                    throw new IOException("Entry escapes staging directory");
                }
                MessageDigest digest = digest();
                long limit = name.startsWith("Data/")
                        ? Math.min(MAX_METADATA_BYTES, MAX_BYTES - total) : MAX_BYTES - total;
                try (InputStream input = zip.getInputStream(entry);
                     FileOutputStream output = new FileOutputStream(target)) {
                    total += copy(input, output, digest, limit);
                    output.getFD().sync();
                }
                if (!hex(digest.digest()).equals(manifest.getProperty("sha256." + name))) {
                    throw new IOException("Backup checksum mismatch");
                }
            }
            int hashes = 0;
            for (String key : manifest.stringPropertyNames()) {
                if (key.startsWith("sha256.")) {
                    hashes++;
                    if (!names.contains(key.substring(7))) throw new IOException("Missing file");
                }
            }
            if (hashes != names.size() - 1 || !names.contains("Data/PictureList.list")
                    || !names.contains("Data/PictureData.list")) throw new IOException("Incomplete backup");
        }
    }

    private static boolean allowed(String name) {
        if (name.startsWith("Data/")) {
            for (String data : DATA_FILES) if (name.equals("Data/" + data)) return true;
            return false;
        }
        return name.startsWith("Pictures/")
                && name.substring(9).matches("[A-Za-z0-9_-]+(?:\\.outline_source)?");
    }

    public static File rollbackDirectory(File root) {
        return new File(root.getParentFile(), root.getName() + ".restore-old");
    }

    /** A process killed between the two renames can recover its original library on startup. */
    public static void recover(File root) throws IOException {
        File old = rollbackDirectory(root);
        if (!old.exists()) return;
        if (!root.exists()) {
            if (!old.renameTo(root)) throw new IOException("Cannot recover previous library");
        } else {
            deleteTree(old);
        }
    }

    /** Call only after extraction and semantic validation, with UI writers paused. */
    public static void install(File stage, File root) throws IOException {
        recover(root);
        File old = rollbackDirectory(root);
        if (old.exists()) throw new IOException("Previous rollback still exists");
        boolean hadOriginal = root.exists();
        if (hadOriginal && !root.renameTo(old)) throw new IOException("Cannot preserve old library");
        if (!stage.renameTo(root)) {
            if (hadOriginal && !old.renameTo(root)) {
                throw new IOException("Restore failed; previous library retained in " + old);
            }
            throw new IOException("Cannot install backup");
        }
        // Leave old data intact until cleanup/startup; never delete it on a failed rollback.
    }

    public static long copy(InputStream input, OutputStream output, MessageDigest digest,
                            long limit) throws IOException {
        byte[] buffer = new byte[32768];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) throw new IOException("Backup size limit exceeded");
            if (output != null) output.write(buffer, 0, count);
            if (digest != null) digest.update(buffer, 0, count);
        }
        return total;
    }

    public static void deleteTree(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        // A failed cleanup can be retried; it must never delete the active data directory.
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static void mkdir(File directory) throws IOException {
        if (!directory.mkdirs()) throw new IOException("Cannot create directory");
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}
