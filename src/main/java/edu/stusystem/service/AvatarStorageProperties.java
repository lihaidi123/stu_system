package edu.stusystem.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix="student.avatar")
public class AvatarStorageProperties {
    private String provider="local";
    private String localRoot=Path.of(System.getProperty("java.io.tmpdir"),"stu-system","avatars").toString();
    private String cosRegion="";
    private String cosBucket="";
    private String cosSecretId="";
    private String cosSecretKey="";
    public String getProvider(){return provider;}
    public void setProvider(String provider){this.provider=provider;}
    public String getLocalRoot(){return localRoot;}
    public void setLocalRoot(String localRoot){this.localRoot=localRoot;}
    public String getCosRegion(){return cosRegion;}
    public void setCosRegion(String cosRegion){this.cosRegion=cosRegion;}
    public String getCosBucket(){return cosBucket;}
    public void setCosBucket(String cosBucket){this.cosBucket=cosBucket;}
    public String getCosSecretId(){return cosSecretId;}
    public void setCosSecretId(String cosSecretId){this.cosSecretId=cosSecretId;}
    public String getCosSecretKey(){return cosSecretKey;}
    public void setCosSecretKey(String cosSecretKey){this.cosSecretKey=cosSecretKey;}
}
