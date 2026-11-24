package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AvatarStorageRegistry {
    private final Map<String,AvatarStorage> stores;
    private final AvatarStorageProperties properties;
    public AvatarStorageRegistry(List<AvatarStorage> stores,AvatarStorageProperties properties){
        this.stores=stores.stream().collect(Collectors.toUnmodifiableMap(AvatarStorage::provider,Function.identity()));this.properties=properties;
    }
    public AvatarStorage active(){
        String provider=properties.getProvider().toUpperCase(java.util.Locale.ROOT);
        AvatarStorage storage=stores.get(provider);
        if(storage==null)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AVATAR_STORAGE_UNAVAILABLE","头像存储未配置");
        return storage;
    }
    public AvatarStorage byProvider(String provider){
        AvatarStorage storage=stores.get(provider);
        if(storage==null)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AVATAR_STORAGE_UNAVAILABLE","头像存储未配置");
        return storage;
    }
}
