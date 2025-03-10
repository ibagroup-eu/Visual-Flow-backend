package by.iba.vfapi.controllers;

import by.iba.vfapi.dto.ListWithOffset;
import by.iba.vfapi.services.JobSessionService;
import by.iba.vfapi.services.auth.AuthenticationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InteractiveSessionControllerTest {
    @InjectMocks
    private InteractiveSessionController controller;
    @Mock
    private JobSessionService jobService;
    @Mock
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        lenient().doReturn("test").when(authenticationService).getFormattedUserInfo();
    }

    @Test
    void testUpdateInteractive() {
        String projectId = "project";
        String jobId = "job";
        String runId = "run";
        JsonNode payload = Mockito.mock(JsonNode.class);
        controller.updateSession(projectId, jobId, runId, payload);
        verify(jobService).updateSession(runId, payload);
    }

    @Test
    void testGetDefinition() {
        String projectId = "project";
        String jobId = "job";
        String runId = "run";
        JsonNode expectedDefinition = mock(JsonNode.class);
        when(jobService.findSession(runId)).thenReturn(Optional.of(expectedDefinition));
        JsonNode actual = controller.getSession(projectId, jobId, runId).getBody();
        assertEquals(expectedDefinition, actual, "result should match");
    }

    @Test
    void testAddEvent() {
        String projectId = "project";
        String jobId = "job";
        String runId = "run";
        JsonNode payload = Mockito.mock(JsonNode.class);
        controller.addEvent(projectId, jobId, runId, payload);
        verify(jobService).createEvent(runId, payload);
    }

    @Test
    void testGetEvents() {
        String projectId = "project";
        String jobId = "job";
        String runId = "run";
        ListWithOffset<JsonNode> expectedDefinition = ListWithOffset.<JsonNode>builder()
                .data(List.of(mock(JsonNode.class)))
                .build();
        when(jobService.findEvents(runId, null)).thenReturn(expectedDefinition);
        ListWithOffset<JsonNode> actual = controller.getEvents(projectId, jobId, runId, null).getBody();
        assertEquals(expectedDefinition, actual, "result should match");
    }

    @Test
    void testGetMetadata() {
        String projectId = "project";
        String jobId = "job";
        String runId = "run";
        ListWithOffset<JsonNode> expected = ListWithOffset.<JsonNode>builder()
                .data(List.of(mock(JsonNode.class)))
                .build();
        when(jobService.getMetadata(runId, null)).thenReturn(expected);
        ListWithOffset<JsonNode> actual = controller.getMetadata(projectId, jobId, runId, null).getBody();
        assertEquals(expected, actual, "result should match");
    }

    @Test
    void testUpdateMetadata() {
        String projectId = "project";
        String jobId = "job";
        String runId = "run";
        JsonNode payload = Mockito.mock(JsonNode.class);
        controller.updateMetadata(projectId, jobId, runId, payload);
        verify(jobService).createMetadata(runId, payload);
    }
}