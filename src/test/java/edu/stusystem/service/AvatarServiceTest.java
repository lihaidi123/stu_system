package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import edu.stusystem.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvatarServiceTest {
    private static final CurrentUser OWNER=new CurrentUser(12,"owner","Owner","TEACHER",0,null);
    private final AvatarImageValidator validator=mock(AvatarImageValidator.class);
    private final AvatarStorage storage=mock(AvatarStorage.class);
    private final AvatarStorageRegistry registry=mock(AvatarStorageRegistry.class);
    private final AvatarMetadataService metadata=mock(AvatarMetadataService.class);
    private final AvatarService service=new AvatarService(validator,registry,metadata);
    private final MockMultipartFile upload=new MockMultipartFile("file","avatar.png","image/png",new byte[]{1,2,3});

    @Test
    void failedReplacementUploadNeverChangesExistingMetadata() throws Exception {
        when(validator.validate(upload)).thenReturn(new AvatarImageValidator.ValidatedImage("image/png",new byte[]{1,2,3}));
        when(registry.active()).thenReturn(storage);
        when(registry.byProvider("LOCAL")).thenReturn(storage);
        when(storage.provider()).thenReturn("LOCAL");
        org.mockito.Mockito.doThrow(new IOException("offline")).when(storage).put(anyString(),any(byte[].class),anyString());

        ApiException error=assertThrows(ApiException.class,()->service.replace(OWNER,upload));

        assertEquals("AVATAR_STORAGE_UNAVAILABLE",error.code());
        verify(metadata,never()).replace(anyLong(),anyString(),anyString(),anyString(),org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void oldObjectCleanupFailureQueuesRetryWithoutFailingReplacement() throws Exception {
        when(validator.validate(upload)).thenReturn(new AvatarImageValidator.ValidatedImage("image/png",new byte[]{1,2,3}));
        when(registry.active()).thenReturn(storage);
        when(registry.byProvider("LOCAL")).thenReturn(storage);
        when(storage.provider()).thenReturn("LOCAL");
        when(metadata.replace(anyLong(),anyString(),anyString(),anyString(),org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new AvatarMetadataService.CleanupItem(8,"LOCAL","old-avatar.png",0));
        when(metadata.isActive("LOCAL","old-avatar.png")).thenReturn(false);
        org.mockito.Mockito.doThrow(new IOException("offline")).when(storage).delete("old-avatar.png");

        var result=service.replace(OWNER,upload);

        assertEquals("/api/v1/me/avatar",result.get("avatarUrl"));
        verify(metadata).retryLater(any(AvatarMetadataService.CleanupItem.class));
    }
}
