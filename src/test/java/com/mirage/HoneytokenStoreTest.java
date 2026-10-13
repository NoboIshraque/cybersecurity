package com.mirage;

import com.mirage.common.Honeytoken;
import com.mirage.console.HoneytokenStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HoneytokenStoreTest {

    @Test
    void matchesPlantedCredentials() {
        HoneytokenStore store = new HoneytokenStore();
        store.plant(new Honeytoken("ht-1", "svc_backup:S3cr3tBackup!", "config/backup.cfg"));

        assertTrue(store.match("svc_backup:S3cr3tBackup!").isPresent());
        assertTrue(store.match("admin:password").isEmpty());
    }
}
