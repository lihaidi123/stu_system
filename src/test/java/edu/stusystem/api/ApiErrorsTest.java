package edu.stusystem.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiErrorsTest {
    @Test
    void frameworkUploadLimitUsesStablePayloadTooLargeResponse() {
        var response=new ApiErrors().uploadTooLarge(new MaxUploadSizeExceededException(10L*1024*1024));

        assertEquals(HttpStatusCode.valueOf(413),response.getStatusCode());
        assertEquals("FILE_TOO_LARGE",((java.util.Map<?,?>)response.getBody()).get("code"));
    }
}
