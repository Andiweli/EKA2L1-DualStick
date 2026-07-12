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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.github.eka2l1.emu.Emulator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class AppExportManager {
    private static final String EXPORT_FORMAT = "eka2l1-app-export";
    private static final int EXPORT_FORMAT_VERSION = 1;
    private static final String METADATA_ENTRY = "eka2l1-app-export.json";
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    private AppExportManager() {
    }

    static String exportApp(Context context, Activity activity, AppItem appItem,
                            String destinationPath) throws IOException {
        if (destinationPath == null || destinationPath.trim().isEmpty()) {
            throw new IOException("No destination directory selected");
        }

        String[] pathPairs = Emulator.getAppExportPaths((int) appItem.getUid());
        if (pathPairs == null || pathPairs.length == 0 || (pathPairs.length % 2) != 0) {
            throw new IOException("No exportable app files were found");
        }

        LinkedHashMap<String, File> exportRoots = new LinkedHashMap<>();
        ArrayList<String> virtualPaths = new ArrayList<>();

        for (int i = 0; i < pathPairs.length; i += 2) {
            String virtualPath = pathPairs[i];
            String rawPath = pathPairs[i + 1];
            String zipEntryPath = virtualPathToZipEntry(virtualPath);
            File source = resolveRawPath(rawPath);

            if (zipEntryPath == null || source == null || !source.exists()) {
                continue;
            }

            if (!exportRoots.containsKey(zipEntryPath)) {
                exportRoots.put(zipEntryPath, source);
                virtualPaths.add(virtualPath);
            }
        }

        String uidHex = String.format(Locale.US, "%08X", appItem.getUid());
        File configDirectory = new File(Emulator.getConfigsDir(), uidHex);
        if (configDirectory.exists()) {
            exportRoots.put("eka2l1/configs/" + uidHex, configDirectory);
        }

        if (exportRoots.isEmpty()) {
            throw new IOException("No exportable app files were found");
        }

        String metadataJson = createMetadataJson(
                uidHex,
                appItem.getTitle(),
                appItem.getOriginalTitle(),
                createUtcTimestamp(),
                Emulator.getCurrentDevice(),
                configDirectory.exists(),
                virtualPaths);

        String fileName = createExportFileName(appItem, uidHex);
        try (OutputStream destinationStream = openDestinationStream(
                context, activity, destinationPath, fileName);
             BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(destinationStream);
             ZipOutputStream zipOutputStream = new ZipOutputStream(bufferedOutputStream)) {

            writeTextEntry(zipOutputStream, METADATA_ENTRY, metadataJson);

            Set<String> writtenEntries = new HashSet<>();
            writtenEntries.add(METADATA_ENTRY);

            for (Map.Entry<String, File> root : exportRoots.entrySet()) {
                addFileOrDirectory(zipOutputStream, root.getValue(), root.getKey(), writtenEntries);
            }

            zipOutputStream.finish();
        }

        return fileName;
    }

    private static File resolveRawPath(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return null;
        }

        File file = new File(rawPath);
        if (!file.isAbsolute()) {
            file = new File(Emulator.getEmulatorDir(), rawPath);
        }
        return file;
    }

    private static String virtualPathToZipEntry(String virtualPath) {
        if (virtualPath == null || virtualPath.length() < 3 || virtualPath.charAt(1) != ':') {
            return null;
        }

        char drive = Character.toLowerCase(virtualPath.charAt(0));
        if (drive < 'a' || drive > 'y' || drive == 'z') {
            return null;
        }

        String relativePath = virtualPath.substring(2)
                .replace('\\', '/')
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");

        if (relativePath.isEmpty() || relativePath.contains("../") || relativePath.equals("..")) {
            return null;
        }

        return "data/drives/" + drive + "/" + relativePath;
    }

    private static OutputStream openDestinationStream(Context context, Activity activity,
                                                       String destinationPath, String fileName)
            throws IOException {
        if (destinationPath.startsWith("/")) {
            File destinationDirectory = new File(destinationPath);
            if (!destinationDirectory.exists() && !destinationDirectory.mkdirs()) {
                throw new IOException("Unable to create export directory");
            }
            if (!destinationDirectory.isDirectory()) {
                throw new IOException("Export destination is not a directory");
            }
            return new FileOutputStream(new File(destinationDirectory, fileName), false);
        }

        Uri destinationUri = Uri.parse(destinationPath);
        activity.getContentResolver().takePersistableUriPermission(destinationUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        DocumentFile destinationDirectory = DocumentFile.fromTreeUri(context, destinationUri);
        if (destinationDirectory == null || !destinationDirectory.isDirectory()) {
            throw new IOException("Unable to access export directory");
        }

        DocumentFile existingFile = destinationDirectory.findFile(fileName);
        if (existingFile != null && !existingFile.delete()) {
            throw new IOException("Unable to replace existing export file");
        }

        DocumentFile exportFile = destinationDirectory.createFile("application/zip", fileName);
        if (exportFile == null) {
            throw new IOException("Unable to create export file");
        }

        OutputStream outputStream = activity.getContentResolver()
                .openOutputStream(exportFile.getUri(), "w");
        if (outputStream == null) {
            throw new IOException("Unable to open export file");
        }
        return outputStream;
    }

    private static void writeTextEntry(ZipOutputStream zipOutputStream, String entryName,
                                       String content) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(System.currentTimeMillis());
        zipOutputStream.putNextEntry(entry);
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private static void addFileOrDirectory(ZipOutputStream zipOutputStream, File source,
                                           String requestedEntryName,
                                           Set<String> writtenEntries) throws IOException {
        String entryName = normalizeZipEntryName(requestedEntryName);
        if (entryName == null) {
            return;
        }

        if (source.isDirectory()) {
            String directoryEntryName = entryName.endsWith("/") ? entryName : entryName + "/";
            if (writtenEntries.add(directoryEntryName)) {
                ZipEntry directoryEntry = new ZipEntry(directoryEntryName);
                directoryEntry.setTime(source.lastModified());
                zipOutputStream.putNextEntry(directoryEntry);
                zipOutputStream.closeEntry();
            }

            File[] children = source.listFiles();
            if (children == null) {
                throw new IOException("Unable to read directory: " + source.getAbsolutePath());
            }

            Arrays.sort(children, Comparator.comparing(File::getName,
                    String.CASE_INSENSITIVE_ORDER));
            for (File child : children) {
                addFileOrDirectory(zipOutputStream, child,
                        directoryEntryName + child.getName(), writtenEntries);
            }
            return;
        }

        if (!source.isFile() || !writtenEntries.add(entryName)) {
            return;
        }

        ZipEntry fileEntry = new ZipEntry(entryName);
        fileEntry.setTime(source.lastModified());
        zipOutputStream.putNextEntry(fileEntry);

        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        try (BufferedInputStream inputStream =
                     new BufferedInputStream(new FileInputStream(source))) {
            int count;
            while ((count = inputStream.read(buffer)) != -1) {
                zipOutputStream.write(buffer, 0, count);
            }
        } finally {
            zipOutputStream.closeEntry();
        }
    }

    private static String normalizeZipEntryName(String entryName) {
        if (entryName == null) {
            return null;
        }

        String normalized = entryName.replace('\\', '/')
                .replaceAll("^/+", "")
                .replaceAll("/{2,}", "/");

        if (normalized.isEmpty() || normalized.equals("..")
                || normalized.startsWith("../") || normalized.contains("/../")) {
            return null;
        }
        return normalized;
    }

    private static String createMetadataJson(String uidHex,
                                             String title,
                                             String originalTitle,
                                             String exportedAtUtc,
                                             int sourceDeviceIndex,
                                             boolean includesAppConfiguration,
                                             List<String> virtualPaths) throws IOException {
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("format", EXPORT_FORMAT);
            metadata.put("version", EXPORT_FORMAT_VERSION);
            metadata.put("appUid", uidHex);
            metadata.put("title", title != null ? title : "");
            metadata.put("originalTitle", originalTitle != null ? originalTitle : "");
            metadata.put("exportedAtUtc", exportedAtUtc);
            metadata.put("sourceDeviceIndex", sourceDeviceIndex);
            metadata.put("includesAppConfiguration", includesAppConfiguration);

            JSONArray paths = new JSONArray();
            for (String virtualPath : virtualPaths) {
                paths.put(virtualPath);
            }
            metadata.put("virtualPaths", paths);

            return metadata.toString(2);
        } catch (JSONException exception) {
            throw new IOException("Unable to create the backup manifest", exception);
        }
    }

    private static String createExportFileName(AppItem appItem, String uidHex) {
        String title = appItem.getTitle();
        if (title == null || title.trim().isEmpty()) {
            title = "app";
        }

        String safeTitle = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        if (safeTitle.isEmpty()) {
            safeTitle = "app";
        }
        if (safeTitle.length() > 60) {
            safeTitle = safeTitle.substring(0, 60).trim();
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        return "eka2l1-app-" + safeTitle + "-" + uidHex + "-"
                + dateFormat.format(new Date()) + ".zip";
    }

    private static String createUtcTimestamp() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(new Date());
    }

}
