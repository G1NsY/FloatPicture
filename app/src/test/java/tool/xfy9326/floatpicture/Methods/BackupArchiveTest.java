package tool.xfy9326.floatpicture.Methods;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.*;
import static org.junit.Assert.*;

public class BackupArchiveTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File library() throws IOException {
        File root = temp.newFolder();
        put(root, "Data/PictureList.list", "{\"abc_123\":\"参考图\"}");
        put(root, "Data/PictureData.list", "{\"abc_123\":{\"POSITION_X\":-12,\"ZOOM_X\":1.234,\"DEGREE\":12.34,\"ALPHA\":0.42}}");
        put(root, "Data/PictureOrder.list", "[\"abc_123\"]");
        put(root, "Pictures/abc_123", "image bytes");
        put(root, "Pictures/abc_123.outline_source", "original image bytes");
        put(root, "Pictures/.TEMP/disposable", "ignore");
        return root;
    }

    private static void put(File root, String name, String data) throws IOException {
        File file = new File(root, name);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), data.getBytes("UTF-8"));
    }

    private File archive(File root) throws IOException {
        File zip = temp.newFile();
        BackupArchive.write(root, zip);
        return zip;
    }

    @Test public void roundTripPreservesExactParametersImagesOriginalsAndOrder() throws Exception {
        File root = library();
        File extracted = new File(temp.getRoot(), "restore");
        BackupArchive.extract(archive(root), extracted);
        for (String name : new String[]{"Data/PictureList.list", "Data/PictureData.list",
                "Data/PictureOrder.list", "Pictures/abc_123", "Pictures/abc_123.outline_source"}) {
            assertArrayEquals(Files.readAllBytes(new File(root, name).toPath()),
                    Files.readAllBytes(new File(extracted, name).toPath()));
        }
        assertFalse(new File(extracted, "Pictures/.TEMP").exists());
    }

    @Test public void emptyLibraryCanBeBackedUp() throws Exception {
        File extracted = new File(temp.getRoot(), "restore");
        BackupArchive.extract(archive(temp.newFolder()), extracted);
        assertEquals("{}", new String(Files.readAllBytes(new File(extracted,
                "Data/PictureList.list").toPath()), "UTF-8"));
        assertTrue(new File(extracted, "Pictures").isDirectory());
    }

    private File altered(File zip, String target, String replacement, String extra) throws IOException {
        File result = temp.newFile();
        try (ZipFile input = new ZipFile(zip);
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(result))) {
            Enumeration<? extends ZipEntry> entries = input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().equals(target) && replacement == null) continue;
                out.putNextEntry(new ZipEntry(entry.getName()));
                if (entry.getName().equals(target)) out.write(replacement.getBytes("UTF-8"));
                else try (InputStream bytes = input.getInputStream(entry)) {
                    BackupArchive.copy(bytes, out, null, BackupArchive.MAX_BYTES);
                }
                out.closeEntry();
            }
            if (extra != null) {
                out.putNextEntry(new ZipEntry(extra));
                out.write(1);
                out.closeEntry();
            }
        }
        return result;
    }

    @Test public void changedPictureFailsChecksum() throws Exception {
        File zip = altered(archive(library()), "Pictures/abc_123", "damaged", null);
        assertThrows(IOException.class, () -> BackupArchive.extract(zip, new File(temp.getRoot(), "restore")));
    }

    @Test public void missingPictureIsRejected() throws Exception {
        File zip = altered(archive(library()), "Pictures/abc_123", null, null);
        assertThrows(IOException.class, () -> BackupArchive.extract(zip, new File(temp.getRoot(), "restore")));
    }

    @Test public void unsupportedVersionIsRejected() throws Exception {
        File zip = altered(archive(library()), "backup.properties",
                "format=FloatPictureBackup\nversion=99\n", null);
        assertThrows(IOException.class, () -> BackupArchive.extract(zip, new File(temp.getRoot(), "restore")));
    }

    @Test public void pathTraversalCannotWriteOutsideStaging() throws Exception {
        File zip = altered(archive(library()), "unused", null, "../escaped");
        assertThrows(IOException.class, () -> BackupArchive.extract(zip, new File(temp.getRoot(), "restore")));
        assertFalse(new File(temp.getRoot(), "escaped").exists());
    }

    @Test public void truncatedZipIsRejected() throws Exception {
        File zip = archive(library());
        try (RandomAccessFile file = new RandomAccessFile(zip, "rw")) {
            file.setLength(file.length() / 2);
        }
        assertThrows(IOException.class, () -> BackupArchive.extract(zip, new File(temp.getRoot(), "restore")));
    }

    @Test public void failedInstallRestoresOriginalFiles() throws Exception {
        File root = library();
        byte[] before = Files.readAllBytes(new File(root, "Data/PictureData.list").toPath());
        assertThrows(IOException.class, () -> BackupArchive.install(new File(temp.getRoot(), "missing"), root));
        assertArrayEquals(before, Files.readAllBytes(new File(root, "Data/PictureData.list").toPath()));
    }

    @Test public void interruptedRestoreRecoversOriginalOnStartup() throws Exception {
        File root = library();
        assertTrue(root.renameTo(BackupArchive.rollbackDirectory(root)));
        BackupArchive.recover(root);
        assertTrue(new File(root, "Pictures/abc_123").isFile());
    }

    @Test public void successfulInstallReplacesWholeLibraryAndPreservesRollbackUntilCleanup() throws Exception {
        File root = library();
        File stage = temp.newFolder();
        put(stage, "Data/PictureList.list", "{}");
        BackupArchive.install(stage, root);
        assertFalse(new File(root, "Pictures/abc_123").exists());
        assertTrue(new File(BackupArchive.rollbackDirectory(root), "Pictures/abc_123").isFile());
        BackupArchive.recover(root);
        assertFalse(BackupArchive.rollbackDirectory(root).exists());
        assertTrue(new File(root, "Data/PictureList.list").isFile());
    }

    @Test public void streamLimitIsEnforced() {
        assertThrows(IOException.class, () -> BackupArchive.copy(
                new ByteArrayInputStream(new byte[33]), new ByteArrayOutputStream(), null, 32));
    }
}
