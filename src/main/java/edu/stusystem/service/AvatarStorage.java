package edu.stusystem.service;

import java.io.IOException;
import java.io.InputStream;

public interface AvatarStorage {
    String provider();
    void put(String objectKey,byte[] content,String contentType) throws IOException;
    InputStream open(String objectKey) throws IOException;
    void delete(String objectKey) throws IOException;
}
