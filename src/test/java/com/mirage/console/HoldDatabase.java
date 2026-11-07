package com.mirage.console;

import com.mirage.console.db.Database;

import java.nio.file.Path;

/** Test helper run in a child JVM: opens the Console database in a folder and holds it open. */
public final class HoldDatabase {

    private HoldDatabase() {
    }

    public static void main(String[] args) throws Exception {
        try (Database db = Database.openFile(Path.of(args[0]), null)) {
            System.out.println("open");
            System.out.flush();
            Thread.sleep(30_000); // the parent kills us when done
        }
    }
}
