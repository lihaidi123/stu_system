package edu.stusystem.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
@ConditionalOnProperty(prefix="student.avatar",name="provider",havingValue="cos")
public class CosAvatarStorage implements AvatarStorage {
    private final String bucket;
    private final COSClient client;

    public CosAvatarStorage(AvatarStorageProperties properties){
        String id=properties.getCosSecretId(),key=properties.getCosSecretKey(),region=properties.getCosRegion();bucket=properties.getCosBucket();
        if(blank(id)||blank(key)||blank(region)||blank(bucket))throw new IllegalStateException("COS avatar storage configuration is incomplete");
        client=new COSClient(new BasicCOSCredentials(id,key),new ClientConfig(new Region(region)));
    }
    @Override public String provider(){return "COS";}
    @Override public void put(String objectKey,byte[] content,String contentType)throws IOException{
        validateKey(objectKey);var metadata=new ObjectMetadata();metadata.setContentLength(content.length);metadata.setContentType(contentType);
        try{client.putObject(new PutObjectRequest(bucket,objectKey,new ByteArrayInputStream(content),metadata));}
        catch(RuntimeException failure){throw new IOException("COS avatar upload failed",failure);}
    }
    @Override public InputStream open(String objectKey)throws IOException{
        validateKey(objectKey);
        try{COSObject object=client.getObject(bucket,objectKey);return object.getObjectContent();}
        catch(RuntimeException failure){throw new IOException("COS avatar read failed",failure);}
    }
    @Override public void delete(String objectKey)throws IOException{
        validateKey(objectKey);
        try{client.deleteObject(bucket,objectKey);}catch(RuntimeException failure){throw new IOException("COS avatar delete failed",failure);}
    }
    @PreDestroy public void close(){client.shutdown();}
    private static boolean blank(String value){return value==null||value.isBlank();}
    private static void validateKey(String key)throws IOException{
        if(key==null||!key.matches("[0-9a-fA-F-]{36}\\.(jpg|png|gif|webp)"))throw new IOException("invalid avatar object key");
    }
}
