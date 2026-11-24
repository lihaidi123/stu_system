package edu.stusystem.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
@ConditionalOnProperty(prefix="student.avatar",name="provider",havingValue="local",matchIfMissing=true)
public class LocalAvatarStorage implements AvatarStorage {
    private final Path root;
    public LocalAvatarStorage(AvatarStorageProperties properties){root=Path.of(properties.getLocalRoot()).toAbsolutePath().normalize();}
    @Override public String provider(){return "LOCAL";}
    @Override public void put(String objectKey,byte[] content,String contentType)throws IOException{
        Path target=resolve(objectKey);Files.createDirectories(root);Path temporary=Files.createTempFile(root,"avatar-",".pending");
        try{Files.write(temporary,content);try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(temporary);}
    }
    @Override public InputStream open(String objectKey)throws IOException{return Files.newInputStream(resolve(objectKey));}
    @Override public void delete(String objectKey)throws IOException{Files.deleteIfExists(resolve(objectKey));}
    private Path resolve(String key){
        if(key==null||key.isBlank()||!Path.of(key).getFileName().toString().equals(key))throw new IllegalArgumentException("invalid object key");
        Path target=root.resolve(key).normalize();if(!target.getParent().equals(root))throw new IllegalArgumentException("invalid object key");return target;
    }
}
