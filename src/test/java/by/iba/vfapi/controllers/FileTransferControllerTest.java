package by.iba.vfapi.controllers;

import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.KubernetesService;
import by.iba.vfapi.services.auth.AuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FileTransferControllerTest {

    private static final String NAMESPACE = "vf-test";
    private static final String FILE_PATH = "path";
    private static final String POD_NAME = "vf-k8s-pvc";
    @Mock
    private KubernetesService kubernetesService;
    @Mock
    private AuthenticationService authenticationServiceMock;
    @Mock
    private FileTransferController controller;
    @BeforeEach
    void setUp() {
        controller = new FileTransferController(kubernetesService, authenticationServiceMock);
        UserInfo expected = new UserInfo();
        expected.setName("name");
        expected.setId("id");
        expected.setUsername("username");
        expected.setEmail("email");
        when(authenticationServiceMock.getUserInfo()).thenReturn(expected);
    }

    @Test
    void testUploadFile() {
        MockMultipartFile file = new MockMultipartFile(
                "data",
                "data.txt",
                "text/plain",
                "some text data".getBytes()
        );
        controller.uploadFile(NAMESPACE, FILE_PATH, file);
        verify(kubernetesService).uploadFile(NAMESPACE, FILE_PATH, POD_NAME, file);
    }

    @Test
    void testDownloadFile() {
        byte[] bytes = new byte[] {1, 2, 3, 4, 5};
        when(kubernetesService.downloadFile(NAMESPACE, FILE_PATH, POD_NAME)).thenReturn(bytes);
        ResponseEntity<byte[]> response = controller.downloadFile(NAMESPACE, "test.zip", FILE_PATH);
        assertEquals(bytes, response.getBody(), "bytes must be equal to response body");
    }
}