package edu.stusystem.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class AvatarCleanupService {
    private final AvatarMetadataService metadata;
    private final AvatarStorageRegistry storage;
    public AvatarCleanupService(AvatarMetadataService metadata,AvatarStorageRegistry storage){this.metadata=metadata;this.storage=storage;}

    @Scheduled(fixedDelayString="${student.avatar.cleanup-interval-ms:900000}",initialDelayString="${student.avatar.cleanup-initial-delay-ms:60000}")
    public void cleanupDue(){
        for(var item:metadata.due()){
            try{
                if(!metadata.isActive(item.provider(),item.objectKey()))storage.byProvider(item.provider()).delete(item.objectKey());
                metadata.deleteJob(item.id());
            }catch(IOException|RuntimeException failure){try{metadata.retryLater(item);}catch(RuntimeException ignored){}}
        }
    }
}
