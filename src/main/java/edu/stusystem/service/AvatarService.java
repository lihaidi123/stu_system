package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import edu.stusystem.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class AvatarService {
    private final AvatarImageValidator validator;
    private final AvatarStorageRegistry storage;
    private final AvatarMetadataService metadata;

    public AvatarService(AvatarImageValidator validator,AvatarStorageRegistry storage,AvatarMetadataService metadata){this.validator=validator;this.storage=storage;this.metadata=metadata;}

    @Audited(action="AVATAR_REPLACE",targetType="account")
    public Map<String,Object> replace(CurrentUser owner,MultipartFile upload){
        var image=validator.validate(upload);AvatarStorage target=storage.active();String key=UUID.randomUUID()+extension(image.contentType());
        try{metadata.stage(target.provider(),key);}catch(RuntimeException failure){throw unavailable("头像元数据暂不可用");}
        try{target.put(key,image.bytes(),image.contentType());}catch(IOException|RuntimeException failure){throw unavailable("头像存储暂不可用");}
        AvatarMetadataService.CleanupItem old;
        try{old=metadata.replace(owner.id(),target.provider(),key,image.contentType(),image.bytes().length);}
        catch(RuntimeException failure){throw unavailable("头像更新未完成");}
        if(old!=null)cleanup(old);
        return Map.of("avatarUrl","/api/v1/me/avatar","updated",true);
    }

    @Audited(action="AVATAR_REMOVE",targetType="account")
    public Map<String,Object> remove(CurrentUser owner){
        AvatarMetadataService.CleanupItem old;
        try{old=metadata.remove(owner.id());}catch(RuntimeException failure){throw unavailable("头像移除未完成");}
        if(old!=null)cleanup(old);
        return Map.of("removed",true,"avatarUrl","");
    }

    public Download download(CurrentUser owner){
        var asset=metadata.find(owner.id());
        if(asset==null)throw new ApiException(HttpStatus.NOT_FOUND,"AVATAR_NOT_SET","当前账户未设置头像");
        try(var input=storage.byProvider(asset.provider()).open(asset.objectKey())){
            byte[] bytes=input.readNBytes(AvatarImageValidator.MAX_BYTES+1);
            if(bytes.length==0||bytes.length>AvatarImageValidator.MAX_BYTES)throw new IOException("invalid stored avatar size");
            return new Download(asset.contentType(),bytes);
        }catch(IOException|RuntimeException failure){throw unavailable("头像暂不可读取");}
    }

    private void cleanup(AvatarMetadataService.CleanupItem item){
        try{
            if(!metadata.isActive(item.provider(),item.objectKey()))storage.byProvider(item.provider()).delete(item.objectKey());
            metadata.deleteJob(item.id());
        }catch(IOException|RuntimeException failure){try{metadata.retryLater(item);}catch(RuntimeException ignored){}}
    }
    private static String extension(String contentType){return switch(contentType){case "image/jpeg"->".jpg";case "image/png"->".png";case "image/gif"->".gif";case "image/webp"->".webp";default->throw new IllegalArgumentException("unsupported avatar format");};}
    private static ApiException unavailable(String message){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AVATAR_STORAGE_UNAVAILABLE",message);}
    public record Download(String contentType,byte[] bytes){}
}
