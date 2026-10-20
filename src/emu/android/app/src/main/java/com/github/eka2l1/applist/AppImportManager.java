/*
 * Copyright (c) 2020 EKA2L1 Team
 *
 * This file is part of EKA2L1 project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.github.eka2l1.applist;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.github.eka2l1.emu.Emulator;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class AppImportManager {
    private static final String EXPORT_FORMAT = "eka2l1-app-export";
    private static final int EXPORT_FORMAT_VERSION = 1;
    private static final String METADATA_ENTRY = "eka2l1-app-export.json";
    private static final String DATA_PREFIX = "data/drives/";
    private static final String CONFIG_PREFIX = "eka2l1/configs/";
    private static final int COPY_BUFFER_SIZE = 64 * 1024;
    private static final int MAX_ENTRY_COUNT = 100000;
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 8L * 1024L * 1024L * 1024L;
    private static final int MAX_METADATA_BYTES = 1024 * 1024;
    private static final Pattern UID_PATTERN = Pattern.compile("[0-9A-Fa-f]{8}");
    private static final Pattern RELEASE_BACKUP_FILE_PATTERN = Pattern.compile(
            "^eka2l1-app-(.+)-([0-9A-Fa-f]{8})-(\\d{8}-\\d{6})\\.zip$",
            Pattern.CASE_INSENSITIVE);

    private AppImportManager() {
    }

    static ImportInfo inspectBackup(Context context, String sourcePath) throws IOException {
        ArchiveValidation validation = validateArchive(context, sourcePath, null, false);
        return new ImportInfo(validation.metadata.title,
                validation.metadata.appUid.toUpperCase(Locale.US),
                validation.metadata.exportedAtUtc,
                validation.fileCount,
                validation.totalBytes,
                validation.hasConfigurationEntries);
    }

    static ImportInfo importBackup(Context context, String sourcePath,
                                   ImportInfo expectedInfo) throws IOException {
        if (expectedInfo == null) {
            throw new IOException("Backup information is missing");
        }

        File emulatorDirectory = new File(Emulator.getEmulatorDir());
        File temporaryRoot = new File(emulatorDirectory,
                "app_import_temp_" + System.currentTimeMillis());
        File stagingRoot = new File(temporaryRoot, "staging");
        File rollbackRoot = new File(temporaryRoot, "rollback");

        if (!stagingRoot.mkdirs()) {
            throw new IOException("Unable to create the temporary import directory");
        }

        try {
            ArchiveValidation validation = validateArchive(context, sourcePath,
                    stagingRoot, true);
            String importedUid = validation.metadata.appUid.toUpperCase(Locale.US);
            if (!importedUid.equals(expectedInfo.appUid)) {
                throw new IOException("The selected backup changed during import");
            }

            applyStagedImport(stagingRoot, rollbackRoot);

            return new ImportInfo(validation.metadata.title,
                    importedUid,
                    validation.metadata.exportedAtUtc,
                    validation.fileCount,
                    validation.totalBytes,
                    validation.hasConfigurationEntries);
        } finally {
            deleteRecursively(temporaryRoot);
        }
    }

    private static ArchiveValidation validateArchive(Context context, String sourcePath,
                                                       File stagingRoot,
                                                       boolean extract) throws IOException {
        if (sourcePath == null || sourcePath.trim().isEmpty()) {
            throw new IOException("No backup file was selected");
        }

        ExportMetadata metadata = null;
        long totalBytes = 0;
        int entryCount = 0;
        int fileCount = 0;
        boolean hasDataFile = false;
        boolean hasConfigurationEntries = false;
        Set<String> normalizedEntries = new HashSet<>();
        Set<String> configurationUids = new HashSet<>();
        byte[] buffer = new byte[COPY_BUFFER_SIZE];

        try (InputStream source = openSourceStream(context, sourcePath);
             ZipInputStream zipInput = new ZipInputStream(new BufferedInputStream(source))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw new IOException("The backup contains too many entries");
                }

                String entryName = validateEntryName(entry.getName());
                String duplicateKey = entryName.toLowerCase(Locale.US);
                if (!normalizedEntries.add(duplicateKey)) {
                    throw new IOException("The backup contains a duplicate path");
                }

                boolean directory = entry.isDirectory() || entryName.endsWith("/");
                EntryDestination destination = classifyEntry(entryName, directory);
                if (destination.configurationUid != null) {
                    configurationUids.add(destination.configurationUid);
                    hasConfigurationEntries = true;
                }

                if (METADATA_ENTRY.equals(entryName)) {
                    if (directory) {
                        throw new IOException("The backup manifest is invalid");
                    }
                    if (metadata != null) {
                        throw new IOException("The backup contains more than one manifest");
                    }

                    byte[] metadataBytes = readEntryBytes(zipInput, MAX_METADATA_BYTES);
                    totalBytes = checkedTotal(totalBytes, metadataBytes.length);
                    metadata = parseMetadata(context, sourcePath, metadataBytes);
                    zipInput.closeEntry();
                    continue;
                }

                if (directory) {
                    if (extract) {
                        File stageDirectory = safeChild(stagingRoot, destination.stagingPath);
                        if (!stageDirectory.exists() && !stageDirectory.mkdirs()) {
                            throw new IOException("Unable to create a temporary directory");
                        }
                    }
                    zipInput.closeEntry();
                    continue;
                }

                if (destination.dataEntry) {
                    hasDataFile = true;
                }

                OutputStream output = null;
                try {
                    if (extract) {
                        File stageFile = safeChild(stagingRoot, destination.stagingPath);
                        File parent = stageFile.getParentFile();
                        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
                            throw new IOException("Unable to create a temporary directory");
                        }
                        if (stageFile.exists() && stageFile.isDirectory()) {
                            throw new IOException("The backup contains a path conflict");
                        }
                        output = new BufferedOutputStream(new FileOutputStream(stageFile, false));
                    }

                    int count;
                    long entryBytes = 0;
                    while ((count = zipInput.read(buffer)) != -1) {
                        entryBytes += count;
                        totalBytes = checkedTotal(totalBytes, count);
                        if (output != null) {
                            output.write(buffer, 0, count);
                        }
                    }
                    if (output != null) {
                        output.flush();
                    }
                    fileCount++;

                    if (extract) {
                        File stageFile = safeChild(stagingRoot, destination.stagingPath);
                        if (entry.getTime() > 0) {
                            stageFile.setLastModified(entry.getTime());
                        }
                    }
                } finally {
                    if (output != null) {
                        output.close();
                    }
                    zipInput.closeEntry();
                }
            }
        }

        if (metadata == null) {
            throw new IOException("This is not an EKA2L1 app backup");
        }
        if (!hasDataFile) {
            throw new IOException("The backup contains no app files");
        }

        String metadataUid = metadata.appUid.toUpperCase(Locale.US);
        for (String configurationUid : configurationUids) {
            if (!metadataUid.equals(configurationUid.toUpperCase(Locale.US))) {
                throw new IOException("The backup configuration UID does not match the app UID");
            }
        }

        return new ArchiveValidation(metadata, fileCount, totalBytes,
                hasConfigurationEntries);
    }

    private static InputStream openSourceStream(Context context, String sourcePath)
            throws IOException {
        if (sourcePath.startsWith("/")) {
            return new FileInputStream(new File(sourcePath));
        }

        InputStream inputStream = context.getContentResolver()
                .openInputStream(Uri.parse(sourcePath));
        if (inputStream == null) {
            throw new IOException("Unable to open the selected backup");
        }
        return inputStream;
    }

    private static ExportMetadata parseMetadata(Context context,
                                                String sourcePath,
                                                byte[] metadataBytes) throws IOException {
        String metadataText = new String(metadataBytes, StandardCharsets.UTF_8).trim();

        try {
            JSONObject json = new JSONObject(metadataText);
            if (json.length() == 0) {
                return recoverReleaseBackupMetadata(context, sourcePath);
            }

            ExportMetadata metadata = new ExportMetadata();
            metadata.format = json.optString("format", null);
            metadata.version = json.optInt("version", -1);
            metadata.appUid = json.optString("appUid", null);
            metadata.title = json.optString("title", null);
            metadata.originalTitle = json.optString("originalTitle", null);
            metadata.exportedAtUtc = json.optString("exportedAtUtc", null);
            metadata.sourceDeviceIndex = json.optInt("sourceDeviceIndex", -1);
            metadata.includesAppConfiguration =
                    json.optBoolean("includesAppConfiguration", false);
            metadata.virtualPaths = Collections.emptyList();

            validateMetadata(metadata);
            return metadata;
        } catch (JSONException exception) {
            throw new IOException("The backup manifest could not be read", exception);
        }
    }

    private static ExportMetadata recoverReleaseBackupMetadata(Context context,
                                                               String sourcePath)
            throws IOException {
        String displayName = getSourceDisplayName(context, sourcePath);
        java.util.regex.Matcher matcher = RELEASE_BACKUP_FILE_PATTERN.matcher(displayName);
        if (!matcher.matches()) {
            throw new IOException("The backup manifest is empty and the file name is unsupported");
        }

        ExportMetadata metadata = new ExportMetadata();
        metadata.format = EXPORT_FORMAT;
        metadata.version = EXPORT_FORMAT_VERSION;
        metadata.title = matcher.group(1).trim();
        metadata.originalTitle = metadata.title;
        metadata.appUid = matcher.group(2).toUpperCase(Locale.US);
        metadata.exportedAtUtc = null;
        metadata.sourceDeviceIndex = -1;
        metadata.includesAppConfiguration = false;
        metadata.virtualPaths = Collections.emptyList();

        validateMetadata(metadata);
        return metadata;
    }

    private static void validateMetadata(ExportMetadata metadata) throws IOException {
        if (metadata == null
                || !EXPORT_FORMAT.equals(metadata.format)
                || metadata.version != EXPORT_FORMAT_VERSION
                || metadata.appUid == null
                || !UID_PATTERN.matcher(metadata.appUid).matches()) {
            throw new IOException("The backup manifest is unsupported or invalid");
        }

        metadata.appUid = metadata.appUid.toUpperCase(Locale.US);
        if (metadata.title == null || metadata.title.trim().isEmpty()) {
            metadata.title = metadata.appUid;
        } else {
            metadata.title = metadata.title.trim();
        }
    }

    private static String getSourceDisplayName(Context context, String sourcePath)
            throws IOException {
        if (sourcePath.startsWith("/")) {
            return new File(sourcePath).getName();
        }

        Uri sourceUri = Uri.parse(sourcePath);
        try (Cursor cursor = context.getContentResolver().query(
                sourceUri,
                new String[] { OpenableColumns.DISPLAY_NAME },
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int displayNameColumn =
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (displayNameColumn >= 0) {
                    String displayName = cursor.getString(displayNameColumn);
                    if (displayName != null && !displayName.trim().isEmpty()) {
                        return displayName.trim();
                    }
                }
            }
        } catch (RuntimeException exception) {
            throw new IOException("Unable to read the selected backup file name", exception);
        }

        String lastPathSegment = sourceUri.getLastPathSegment();
        if (lastPathSegment == null || lastPathSegment.trim().isEmpty()) {
            throw new IOException("Unable to determine the selected backup file name");
        }
        return Uri.decode(lastPathSegment);
    }

    private static byte[] readEntryBytes(ZipInputStream inputStream, int maximumBytes)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        int total = 0;
        while ((count = inputStream.read(buffer)) != -1) {
            total += count;
            if (total > maximumBytes) {
                throw new IOException("The backup manifest is too large");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static long checkedTotal(long currentTotal, long additionalBytes)
            throws IOException {
        if (additionalBytes < 0
                || currentTotal > MAX_TOTAL_UNCOMPRESSED_BYTES - additionalBytes) {
            throw new IOException("The backup is too large");
        }
        return currentTotal + additionalBytes;
    }

    private static String validateEntryName(String rawName) throws IOException {
        if (rawName == null || rawName.isEmpty()
                || rawName.indexOf('\0') >= 0
                || rawName.indexOf('\\') >= 0
                || rawName.startsWith("/")) {
            throw new IOException("The backup contains an unsafe path");
        }

        String[] components = rawName.split("/", -1);
        for (int i = 0; i < components.length; i++) {
            String component = components[i];
            boolean allowedTrailingEmpty = i == components.length - 1
                    && component.isEmpty() && rawName.endsWith("/");
            if (allowedTrailingEmpty) {
                continue;
            }
            if (component.isEmpty() || component.equals(".") || component.equals("..")
                    || component.indexOf(':') >= 0) {
                throw new IOException("The backup contains an unsafe path");
            }
        }
        return rawName;
    }

    private static EntryDestination classifyEntry(String entryName, boolean directory)
            throws IOException {
        if (METADATA_ENTRY.equals(entryName)) {
            return new EntryDestination(METADATA_ENTRY, false, null);
        }

        if (entryName.startsWith(DATA_PREFIX)) {
            String remainder = entryName.substring(DATA_PREFIX.length());
            int separator = remainder.indexOf('/');
            if (separator != 1) {
                throw new IOException("The backup contains an invalid drive path");
            }

            char drive = Character.toLowerCase(remainder.charAt(0));
            if (drive < 'a' || drive > 'y' || drive == 'z') {
                throw new IOException("The backup contains a forbidden drive");
            }

            String relativePath = remainder.substring(separator + 1);
            if (!directory && relativePath.isEmpty()) {
                throw new IOException("The backup contains an invalid drive path");
            }
            return new EntryDestination(entryName, true, null);
        }

        if (entryName.startsWith(CONFIG_PREFIX)) {
            String remainder = entryName.substring(CONFIG_PREFIX.length());
            int separator = remainder.indexOf('/');
            String uid = separator >= 0 ? remainder.substring(0, separator) : remainder;
            if (!UID_PATTERN.matcher(uid).matches()) {
                throw new IOException("The backup contains an invalid configuration path");
            }
            if (!directory && separator < 0) {
                throw new IOException("The backup contains an invalid configuration path");
            }
            return new EntryDestination(entryName, false, uid);
        }

        throw new IOException("The backup contains an unsupported path");
    }

    private static File safeChild(File root, String relativePath) throws IOException {
        File child = new File(root, relativePath.replace('/', File.separatorChar));
        String rootPath = root.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        if (!childPath.equals(rootPath)
                && !childPath.startsWith(rootPath + File.separator)) {
            throw new IOException("The backup contains an unsafe path");
        }
        return child;
    }

    private static void applyStagedImport(File stagingRoot, File rollbackRoot)
            throws IOException {
        File stagedDataRoot = new File(stagingRoot, "data/drives");
        File stagedConfigRoot = new File(stagingRoot, "eka2l1/configs");
        File targetDataRoot = new File(Emulator.getEmulatorDir(), "data/drives");
        File targetConfigRoot = new File(Emulator.getConfigsDir());

        ArrayList<PlannedFile> files = new ArrayList<>();
        ArrayList<File> directories = new ArrayList<>();
        collectImportPlan(stagedDataRoot, targetDataRoot, files, directories);
        collectImportPlan(stagedConfigRoot, targetConfigRoot, files, directories);

        if (files.isEmpty()) {
            throw new IOException("The backup contains no files to import");
        }

        Collections.sort(directories, Comparator.comparingInt(
                file -> file.getAbsolutePath().length()));

        ArrayList<File> createdDirectories = new ArrayList<>();
        try {
            if (!rollbackRoot.mkdirs()) {
                throw new IOException("Unable to create the rollback directory");
            }

            int backupIndex = 0;
            for (PlannedFile file : files) {
                if (file.target.exists()) {
                    if (!file.target.isFile()) {
                        throw new IOException("An imported file conflicts with an existing directory");
                    }
                    file.originalExisted = true;
                    File backup = new File(rollbackRoot,
                            String.format(Locale.US, "%08d.bak", backupIndex++));
                    copyFile(file.target, backup);
                    // Only a complete copy may be restored. If this copy fails, nothing has
                    // been overwritten yet, so the rollback must leave the original alone.
                    file.backup = backup;
                }
            }

            for (File directory : directories) {
                if (directory.exists()) {
                    if (!directory.isDirectory()) {
                        throw new IOException("An imported directory conflicts with an existing file");
                    }
                    continue;
                }
                if (!directory.mkdirs() && !directory.isDirectory()) {
                    throw new IOException("Unable to create an app directory");
                }
                createdDirectories.add(directory);
            }

            for (PlannedFile file : files) {
                File parent = file.target.getParentFile();
                if (parent == null || (!parent.exists() && !parent.mkdirs())) {
                    throw new IOException("Unable to create an app directory");
                }
                copyFile(file.staged, file.target);
                if (file.staged.lastModified() > 0) {
                    file.target.setLastModified(file.staged.lastModified());
                }
            }
        } catch (IOException exception) {
            rollbackImport(files, createdDirectories);
            throw exception;
        }
    }

    private static void collectImportPlan(File stagedRoot, File targetRoot,
                                          List<PlannedFile> files,
                                          List<File> directories) throws IOException {
        if (!stagedRoot.exists()) {
            return;
        }
        collectImportPlanRecursive(stagedRoot, stagedRoot, targetRoot, files, directories);
    }

    private static void collectImportPlanRecursive(File baseRoot, File current,
                                                   File targetRoot,
                                                   List<PlannedFile> files,
                                                   List<File> directories) throws IOException {
        String relativePath = relativePath(baseRoot, current);
        File target = relativePath.isEmpty()
                ? targetRoot : safeChild(targetRoot, relativePath);

        if (current.isDirectory()) {
            directories.add(target);
            File[] children = current.listFiles();
            if (children == null) {
                throw new IOException("Unable to read the temporary import directory");
            }
            for (File child : children) {
                collectImportPlanRecursive(baseRoot, child, targetRoot, files, directories);
            }
        } else if (current.isFile()) {
            files.add(new PlannedFile(current, target));
        }
    }

    private static String relativePath(File root, File child) throws IOException {
        String rootPath = root.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        if (childPath.equals(rootPath)) {
            return "";
        }
        if (!childPath.startsWith(rootPath + File.separator)) {
            throw new IOException("The temporary import path is invalid");
        }
        return childPath.substring(rootPath.length() + 1)
                .replace(File.separatorChar, '/');
    }

    private static void rollbackImport(List<PlannedFile> files,
                                       List<File> createdDirectories) {
        for (PlannedFile file : files) {
            try {
                if (file.originalExisted && file.backup != null && file.backup.isFile()) {
                    copyFile(file.backup, file.target);
                } else if (!file.originalExisted && file.target.exists()) {
                    file.target.delete();
                }
            } catch (IOException ignored) {
            }
        }

        createdDirectories.sort((left, right) -> Integer.compare(
                right.getAbsolutePath().length(), left.getAbsolutePath().length()));
        for (File directory : createdDirectories) {
            File[] children = directory.listFiles();
            if (children != null && children.length == 0) {
                directory.delete();
            }
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw new IOException("Unable to create the destination directory");
        }

        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        try (InputStream input = new BufferedInputStream(new FileInputStream(source));
             OutputStream output = new BufferedOutputStream(
                     new FileOutputStream(destination, false))) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    static final class ImportInfo {
        final String title;
        final String appUid;
        final String exportedAtUtc;
        final int fileCount;
        final long totalBytes;
        final boolean includesAppConfiguration;

        ImportInfo(String title, String appUid, String exportedAtUtc,
                   int fileCount, long totalBytes,
                   boolean includesAppConfiguration) {
            this.title = title;
            this.appUid = appUid;
            this.exportedAtUtc = exportedAtUtc;
            this.fileCount = fileCount;
            this.totalBytes = totalBytes;
            this.includesAppConfiguration = includesAppConfiguration;
        }
    }

    private static final class ExportMetadata {
        String format;
        int version;
        String appUid;
        String title;
        String originalTitle;
        String exportedAtUtc;
        int sourceDeviceIndex;
        boolean includesAppConfiguration;
        List<String> virtualPaths;
    }

    private static final class ArchiveValidation {
        final ExportMetadata metadata;
        final int fileCount;
        final long totalBytes;
        final boolean hasConfigurationEntries;

        ArchiveValidation(ExportMetadata metadata, int fileCount,
                          long totalBytes, boolean hasConfigurationEntries) {
            this.metadata = metadata;
            this.fileCount = fileCount;
            this.totalBytes = totalBytes;
            this.hasConfigurationEntries = hasConfigurationEntries;
        }
    }

    private static final class EntryDestination {
        final String stagingPath;
        final boolean dataEntry;
        final String configurationUid;

        EntryDestination(String stagingPath, boolean dataEntry,
                         String configurationUid) {
            this.stagingPath = stagingPath;
            this.dataEntry = dataEntry;
            this.configurationUid = configurationUid;
        }
    }

    private static final class PlannedFile {
        final File staged;
        final File target;
        boolean originalExisted;
        File backup;

        PlannedFile(File staged, File target) {
            this.staged = staged;
            this.target = target;
        }
    }
}
