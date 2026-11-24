package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

@Component
public class AvatarImageValidator {
    public static final int MAX_BYTES=2*1024*1024;
    private static final long MAX_PIXELS=20_000_000;
    private static final Map<String,String> FORMATS=Map.of("image/jpeg","JPEG","image/png","PNG","image/gif","GIF","image/webp","WEBP");

    public ValidatedImage validate(MultipartFile file){
        if(file==null||file.isEmpty())throw invalid("AVATAR_EMPTY","请选择非空图片");
        if(file.getSize()>MAX_BYTES)throw invalid("AVATAR_TOO_LARGE","头像不能超过2 MiB");
        String declared=file.getContentType()==null?"":file.getContentType().toLowerCase(java.util.Locale.ROOT);
        if(!FORMATS.containsKey(declared))throw invalid("AVATAR_TYPE_INVALID","头像类型不受支持");
        byte[] bytes;
        try{bytes=file.getBytes();}catch(IOException e){throw invalid("AVATAR_READ_FAILED","头像文件无法读取");}
        if(bytes.length==0||bytes.length>MAX_BYTES)throw invalid(bytes.length==0?"AVATAR_EMPTY":"AVATAR_TOO_LARGE",bytes.length==0?"请选择非空图片":"头像不能超过2 MiB");
        String detected=detect(bytes);
        if(!declared.equals(detected))throw invalid("AVATAR_TYPE_INVALID","声明类型与图片内容不匹配");
        decode(bytes,FORMATS.get(declared));
        return new ValidatedImage(declared,bytes);
    }

    private String detect(byte[] data){
        if(data.length>=3&&(data[0]&255)==0xff&&(data[1]&255)==0xd8&&(data[2]&255)==0xff)return "image/jpeg";
        if(data.length>=8&&(data[0]&255)==0x89&&data[1]=='P'&&data[2]=='N'&&data[3]=='G'&&data[4]==13&&data[5]==10&&data[6]==26&&data[7]==10)return "image/png";
        if(data.length>=6&&data[0]=='G'&&data[1]=='I'&&data[2]=='F'&&data[3]=='8'&&(data[4]=='7'||data[4]=='9')&&data[5]=='a')return "image/gif";
        if(data.length>=16&&data[0]=='R'&&data[1]=='I'&&data[2]=='F'&&data[3]=='F'&&data[8]=='W'&&data[9]=='E'&&data[10]=='B'&&data[11]=='P')return "image/webp";
        return null;
    }

    private void decode(byte[] bytes,String expectedFormat){
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            if(input==null)throw invalid("AVATAR_CONTENT_INVALID","图片内容无法解析");
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw invalid("AVATAR_CONTENT_INVALID","图片内容无法解析");
            ImageReader reader=readers.next();
            try{
                reader.setInput(input,true,true);
                if(!expectedFormat.equalsIgnoreCase(reader.getFormatName()))throw invalid("AVATAR_CONTENT_INVALID","图片实际格式无效");
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||(long)width*height>MAX_PIXELS)throw invalid("AVATAR_DIMENSIONS_INVALID","图片尺寸超过限制");
                if(reader.read(0)==null)throw invalid("AVATAR_CONTENT_INVALID","图片内容无法解析");
            }finally{reader.dispose();}
        }catch(ApiException e){throw e;}catch(IOException|RuntimeException e){throw invalid("AVATAR_CONTENT_INVALID","图片内容无法解析");}
    }

    private ApiException invalid(String code,String message){return new ApiException(HttpStatus.BAD_REQUEST,code,message);}
    public record ValidatedImage(String contentType,byte[] bytes){}
}
