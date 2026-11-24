package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AvatarImageValidatorTest {
    private final AvatarImageValidator validator=new AvatarImageValidator();

    @Test
    void acceptsDecodedPngBasedOnContentRatherThanFilenameExtension() throws Exception {
        byte[] png;
        try(var output=new ByteArrayOutputStream()){
            ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB),"png",output);
            png=output.toByteArray();
        }
        var upload=new MockMultipartFile("file","avatar.bin","image/png",png);

        var result=validator.validate(upload);

        assertEquals("image/png",result.contentType());
        assertEquals(png.length,result.bytes().length);
    }

    @Test
    void rejectsDeclaredMimeThatDoesNotMatchDetectedImage() throws Exception {
        byte[] png;
        try(var output=new ByteArrayOutputStream()){
            ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB),"png",output);
            png=output.toByteArray();
        }

        ApiException error=assertThrows(ApiException.class,()->validator.validate(new MockMultipartFile("file","avatar.jpg","image/jpeg",png)));

        assertEquals("AVATAR_TYPE_INVALID",error.code());
    }

    @Test
    void rejectsRiffWebpHeaderWithoutAnImageThatCanBeDecoded() {
        byte[] fake={'R','I','F','F',0,0,0,0,'W','E','B','P','V','P','8',' '};

        ApiException error=assertThrows(ApiException.class,()->validator.validate(new MockMultipartFile("file","avatar.webp","image/webp",fake)));

        assertEquals("AVATAR_CONTENT_INVALID",error.code());
    }

    @Test
    void acceptsWebpOnlyAfterTheInstalledDecoderCanReadItsPixels() {
        byte[] webp=Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");

        var result=validator.validate(new MockMultipartFile("file","avatar.webp","image/webp",webp));

        assertEquals("image/webp",result.contentType());
    }

    @Test
    void rejectsFilesLargerThanTwoMibBeforeDecoding() {
        var file=new MockMultipartFile("file","avatar.png","image/png",new byte[2*1024*1024+1]);

        ApiException error=assertThrows(ApiException.class,()->validator.validate(file));

        assertEquals("AVATAR_TOO_LARGE",error.code());
    }
}
