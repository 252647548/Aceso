package com.mogujie.instantrun;

import com.google.common.base.Preconditions;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Created by wangzhi on 16/12/5.
 */
class FileUtils {
    public static void deletePath(@NonNull final File path) throws IOException {
        if (!path.exists()) {
            return;
        }

        if (path.isDirectory()) {
            deleteDirectoryContents(path);
        }

        if (!path.delete()) {
            throw new IOException(String.format("Could not delete path '%s'.", path));
        }
    }

    public static void cleanOutputDir(@NonNull File path) throws IOException {
        if (!path.isDirectory()) {
            if (path.exists()) {
                deletePath(path);
            }

            if (!path.mkdirs()) {
                throw new IOException(String.format("Could not create empty folder %s", path));
            }

            return;
        }

        deleteDirectoryContents(path);
    }

    public static void deleteDirectoryContents(@NonNull final File directory) throws IOException {
        Preconditions.checkArgument(directory.isDirectory(), "!directory.isDirectory");

        File[] files = directory.listFiles();
        Preconditions.checkNotNull(files);
        for (File file : files) {
            deletePath(file);
        }
    }

    public static String relativePath(@NonNull File file, @NonNull File dir) {
        Preconditions.checkArgument(file.isFile() || file.isDirectory(), "%s is not a file nor a directory.",
                file.getPath());
        Preconditions.checkArgument(dir.isDirectory(), "%s is not a directory.", dir.getPath());
        return relativePossiblyNonExistingPath(file, dir);
    }
    @NonNull
    public static String relativePossiblyNonExistingPath(@NonNull File file, @NonNull File dir) {
        String path = dir.toURI().relativize(file.toURI()).getPath();
        return toSystemDependentPath(path);
    }

    /**
     * Converts a /-based path into a path using the system dependent separator.
     * @param path the system independent path to convert
     * @return the system dependent path
     */
    @NonNull
    public static String toSystemDependentPath(@NonNull String path) {
        if (File.separatorChar != '/') {
            path = path.replace('/', File.separatorChar);
        }
        return path;
    }


}
