package by.iba.vfapi.services;

import by.iba.vfapi.dto.projects.ConnectDto;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.dto.projects.ParamsDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DatabasesServiceTest {

    @Mock
    private ProjectService projectService;
    private DatabasesService databasesService;

    @BeforeEach
    public void setUp() {
        databasesService = new DatabasesService(projectService);
    }

    @SneakyThrows
    @Test
    public void testGetConnection() {
        String projectId = "test";
        String connectionName = "con";
        String conStrRepresentation = "{\"db\":\"#db#\"}";
        ParamDto paramDto = ParamDto.builder().key("db").value("value").secret(false).build();
        ParamsDto mockParam = mock(ParamsDto.class);
        when(projectService.getParams(projectId)).thenReturn(mockParam);
        when(mockParam.getParams()).thenReturn(List.of(paramDto));
        ConnectDto connectDto = ConnectDto.builder().key("db2")
                .value(new ObjectMapper().readTree(conStrRepresentation)).build();
        when(projectService.getConnection(projectId, connectionName)).thenReturn(connectDto);
        ConnectDto result = databasesService.getConnection(projectId, connectionName);
        String expectedConnection = "{\"db\":\"value\"}";
        assertEquals(expectedConnection, result.getValue().toString(),
                "The expected connection should be equal to actual one!");
    }
}
