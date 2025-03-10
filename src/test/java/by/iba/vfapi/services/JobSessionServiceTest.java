package by.iba.vfapi.services;

import by.iba.vfapi.config.ApplicationConfigurationProperties;
import by.iba.vfapi.dao.JobDefinitionRepository;
import by.iba.vfapi.dao.JobEventRepository;
import by.iba.vfapi.dao.JobMetadataRepository;
import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.dto.ListWithOffset;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobSessionServiceTest {

    @Mock
    private JobDefinitionRepository jobDefinitionRepository;

    @Mock
    private JobEventRepository jobEventRepository;

    @Mock
    private JobMetadataRepository jobMetadataRepository;

    @Mock
    private ApplicationConfigurationProperties applicationConfigurationProperties;

    @InjectMocks
    private JobSessionService jobSessionService;

    @Test
    void testUpdateSessionSuccessfully() {
        when(jobDefinitionRepository.findByKeyAndUpdateExpiry(anyString(), anyLong()))
                .thenReturn(Optional.of(mock(JsonNode.class)));
        String runId = "runId";
        JsonNode payload = mock(JsonNode.class);

        jobSessionService.updateSession(runId, payload);

        verify(jobDefinitionRepository).save(runId, payload);
        verify(jobDefinitionRepository).findByKeyAndUpdateExpiry(runId, -1L);
    }

    @Test
    void testFindSessionSuccessfully() {
        String runId = "runId";
        JsonNode expectedDefinition = mock(JsonNode.class);
        when(jobDefinitionRepository.findByKey(runId))
                .thenReturn(Optional.of(expectedDefinition));

        JsonNode actualDefinition = jobSessionService.findSession(runId).orElseThrow();

        assertEquals(expectedDefinition, actualDefinition, "result should match");
    }

    @Test
    void testCreateEventSuccessfully() {
        when(jobDefinitionRepository.findByKeyAndUpdateExpiry(anyString(), anyLong()))
                .thenReturn(Optional.of(mock(JsonNode.class)));
        String runId = "runId";
        JsonNode payload = mock(JsonNode.class);

        jobSessionService.createEvent(runId, payload);

        verify(jobEventRepository).save(runId, payload);
        verify(jobDefinitionRepository).findByKeyAndUpdateExpiry(runId, -1L);
    }

    @Test
    void testFindEventsSuccessfully() {
        String runId = "runId";
        ListWithOffset<JsonNode> expected = ListWithOffset.<JsonNode>builder()
                .data(List.of(mock(JsonNode.class)))
                .build();
        when(jobEventRepository.findAll(runId, 0L)).thenReturn(expected);

        ListWithOffset<JsonNode> actual = jobSessionService.findEvents(runId, null);

        assertEquals(expected, actual, "result should match");
    }

    @Test
    void testGetMetadataSuccessfully() {
        String runId = "runId";
        ListWithOffset<JsonNode> expected = ListWithOffset.<JsonNode>builder()
                .data(List.of(mock(JsonNode.class)))
                .build();
        when(jobMetadataRepository.findAll(runId, 0L)).thenReturn(expected);

        ListWithOffset<JsonNode> actualMetadata = jobSessionService.getMetadata(runId, null);

        assertEquals(expected, actualMetadata, "result should match");
    }

    @Test
    void testCreateMetadataSuccessfully() {
        String runId = "runId";
        when(jobDefinitionRepository.findByKey(runId))
                .thenReturn(Optional.of(mock(JsonNode.class)));
        JsonNode payload = mock(JsonNode.class);

        jobSessionService.createMetadata(runId, payload);

        verify(jobMetadataRepository).save(runId, payload);
        verify(jobDefinitionRepository).findByKey(runId);
    }

    @Test
    void testRemoveSessionSuccessfully() {
        String runId = "runId";

        jobSessionService.removeSession(runId);

        verify(jobDefinitionRepository).delete(runId);
        verify(jobEventRepository).delete(runId);
        verify(jobMetadataRepository).delete(runId);
    }

    @Test
    void testFindGraphDtoSuccessfully() throws JsonProcessingException {
        String runId = "runId";
        JsonNode session = new ObjectMapper().readTree("{\"graph\": [{\"id\": 1, \"vertex\": true, \"value\": {}}]}");

        when(jobDefinitionRepository.findByKey(runId)).thenReturn(Optional.of(session));

        Optional<GraphDto> result = jobSessionService.findGraphDto(runId);

        assertTrue(result.isPresent(), "result should be present");
    }

}