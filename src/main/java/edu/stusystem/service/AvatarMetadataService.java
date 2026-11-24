package edu.stusystem.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class AvatarMetadataService {
    private final JdbcTemplate db;
    public AvatarMetadataService(JdbcTemplate db){this.db=db;}

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void stage(String provider,String objectKey){
        db.update("INSERT INTO avatar_cleanup_job(storage_provider,object_key,available_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE available_at=VALUES(available_at)",provider,objectKey,Timestamp.from(Instant.now().plusSeconds(3600)));
    }

    @Transactional
    public CleanupItem replace(long ownerId,String provider,String objectKey,String contentType,int sizeBytes){
        AvatarAsset previous=find(ownerId);
        if(previous==null){
            db.update("INSERT INTO avatar_asset(owner_account_id,storage_provider,object_key,content_type,size_bytes) VALUES(?,?,?,?,?)",ownerId,provider,objectKey,contentType,sizeBytes);
        }else{
            db.update("UPDATE avatar_asset SET storage_provider=?,object_key=?,content_type=?,size_bytes=? WHERE owner_account_id=?",provider,objectKey,contentType,sizeBytes,ownerId);
            if(!previous.objectKey().equals(objectKey)||!previous.provider().equals(provider))enqueue(previous.provider(),previous.objectKey(),Instant.now());
        }
        db.update("DELETE FROM avatar_cleanup_job WHERE storage_provider=? AND object_key=?",provider,objectKey);
        return previous==null?null:findJob(previous.provider(),previous.objectKey());
    }

    @Transactional
    public CleanupItem remove(long ownerId){
        AvatarAsset asset=find(ownerId);if(asset==null)return null;
        db.update("DELETE FROM avatar_asset WHERE owner_account_id=?",ownerId);
        enqueue(asset.provider(),asset.objectKey(),Instant.now());
        return findJob(asset.provider(),asset.objectKey());
    }

    public AvatarAsset find(long ownerId){
        List<AvatarAsset> rows=db.query("SELECT storage_provider,object_key,content_type,size_bytes FROM avatar_asset WHERE owner_account_id=?",(rs,n)->new AvatarAsset(rs.getString(1),rs.getString(2),rs.getString(3),rs.getInt(4)),ownerId);
        return rows.isEmpty()?null:rows.getFirst();
    }

    public boolean isActive(String provider,String objectKey){
        Integer count=db.queryForObject("SELECT COUNT(*) FROM avatar_asset WHERE storage_provider=? AND object_key=?",Integer.class,provider,objectKey);
        return count!=null&&count>0;
    }

    public List<CleanupItem> due(){
        return db.query("SELECT id,storage_provider,object_key,attempt_count FROM avatar_cleanup_job WHERE available_at<=CURRENT_TIMESTAMP(6) ORDER BY id LIMIT 100",
                (rs,n)->new CleanupItem(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getInt(4)));
    }

    public void deleteJob(long id){db.update("DELETE FROM avatar_cleanup_job WHERE id=?",id);}
    public void retryLater(CleanupItem item){
        long seconds=Math.min(3600L,30L<<Math.min(item.attempts(),7));
        db.update("UPDATE avatar_cleanup_job SET attempt_count=attempt_count+1,available_at=?,last_error_code='STORAGE_UNAVAILABLE' WHERE id=?",Timestamp.from(Instant.now().plusSeconds(seconds)),item.id());
    }

    private void enqueue(String provider,String objectKey,Instant availableAt){
        db.update("INSERT INTO avatar_cleanup_job(storage_provider,object_key,available_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE available_at=LEAST(available_at,VALUES(available_at))",provider,objectKey,Timestamp.from(availableAt));
    }
    private CleanupItem findJob(String provider,String objectKey){
        List<CleanupItem> rows=db.query("SELECT id,storage_provider,object_key,attempt_count FROM avatar_cleanup_job WHERE storage_provider=? AND object_key=?",(rs,n)->new CleanupItem(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getInt(4)),provider,objectKey);
        return rows.isEmpty()?null:rows.getFirst();
    }

    public record AvatarAsset(String provider,String objectKey,String contentType,int sizeBytes){}
    public record CleanupItem(long id,String provider,String objectKey,int attempts){}
}
