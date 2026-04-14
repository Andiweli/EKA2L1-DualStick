package com.github.eka2l1.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipUtils {
    // https://stackoverflow.com/questions/3382996/how-to-unzip-files-programmatically-in-android
    // Thanks a lot from the StackOverflow answerers!
    public static void unzip(InputStream zipFile, File targetDirectory) throws IOException {
        ZipInputStream zis = new ZipInputStream(zipFile);
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                } finally {
                    fout.close();
                }
                long time = ze.getTime();
                if (time > 0)
                    file.setLastModified(time);
            }
        } finally {
            zis.close();
        }
    }

    public static void zipDirectoryToStream(File sourceDirectory, OutputStream outputStream,
                                            String rootDirectoryName) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(outputStream);
        try {
            if ((sourceDirectory == null) || !sourceDirectory.exists() || !sourceDirectory.isDirectory()) {
                throw new FileNotFoundException("Source directory is invalid: " + sourceDirectory);
            }

            String normalizedRootName = rootDirectoryName;
            if ((normalizedRootName == null) || normalizedRootName.isEmpty()) {
                normalizedRootName = sourceDirectory.getName();
            }

            if (!normalizedRootName.endsWith("/")) {
                normalizedRootName += "/";
            }

            ZipEntry rootEntry = new ZipEntry(normalizedRootName);
            rootEntry.setTime(sourceDirectory.lastModified());
            zos.putNextEntry(rootEntry);
            zos.closeEntry();

            zipDirectoryRecursive(sourceDirectory, normalizedRootName, zos);
        } finally {
            zos.finish();
            zos.flush();
        }
    }

    private static void zipDirectoryRecursive(File directory, String entryPrefix,
                                              ZipOutputStream zos) throws IOException {
        File[] children = directory.listFiles();
        if (children == null || children.length == 0) {
            return;
        }

        byte[] buffer = new byte[8192];
        for (File child : children) {
            String childEntryName = entryPrefix + child.getName();
            if (child.isDirectory()) {
                ZipEntry dirEntry = new ZipEntry(childEntryName + "/");
                dirEntry.setTime(child.lastModified());
                zos.putNextEntry(dirEntry);
                zos.closeEntry();
                zipDirectoryRecursive(child, childEntryName + "/", zos);
                continue;
            }

            ZipEntry fileEntry = new ZipEntry(childEntryName);
            fileEntry.setTime(child.lastModified());
            zos.putNextEntry(fileEntry);

            InputStream fis = new java.io.FileInputStream(child);
            try {
                int count;
                while ((count = fis.read(buffer)) != -1) {
                    zos.write(buffer, 0, count);
                }
            } finally {
                fis.close();
                zos.closeEntry();
            }
        }
    }
}
