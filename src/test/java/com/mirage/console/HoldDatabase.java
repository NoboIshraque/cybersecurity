package com.mirage.console;

import com.mirage.console.db.Database;

import java.nio.file.Path;

/** Test helper run in a child JVM: opens the Console database in a folder and holds it open. */
public final class HoldDatabase {

    private HoldDatabase() {
    }

    /**
     * Starts a child JVM that holds the database in {@code dir}. Java prints a "Picked up
     * JAVA_TOOL_OPTIONS" banner before {@code main} runs when one of these variables is set (some
     * IDEs, CI images and cloud shells set them), which would come before our "open" line; the
     * child does not need them, so they are removed from its environment.
     */
    static Process start(Path dir) throws java.io.IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                HoldDatabase.class.getName(), dir.toString()).redirectErrorStream(true);
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        pb.environment().remove("_JAVA_OPTIONS");
        pb.environment().remove("JDK_JAVA_OPTIONS");
        return pb.start();
    }

    public static void main(String[] args) throws Exception {
        try (Database db = Database.openFile(Path.of(args[0]), null)) {
            System.out.println("open");
            System.out.flush();
            Thread.sleep(30_000); // the parent kills us when done
        }
    }
}
