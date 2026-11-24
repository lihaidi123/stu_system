package edu.stusystem.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalAvatarStorageTest {
    @TempDir Path root;

    @Test
    void storesOpensAndRemovesGeneratedObjectKey() throws Exception {
        var properties=new AvatarStorageProperties();properties.setLocalRoot(root.toString());
        var storage=new LocalAvatarStorage(properties);byte[] bytes={1,2,3,4};

        storage.put("account-avatar.png",bytes,"image/png");
        try(var input=storage.open("account-avatar.png")){assertArrayEquals(bytes,input.readAllBytes());}
        storage.delete("account-avatar.png");

        assertFalse(Files.exists(root.resolve("account-avatar.png")));
    }

    @Test
    void rejectsPathTraversalObjectKeys() {
        var properties=new AvatarStorageProperties();properties.setLocalRoot(root.toString());
        var storage=new LocalAvatarStorage(properties);

        assertThrows(IllegalArgumentException.class,()->storage.delete("..\\outside.png"));
    }
}
