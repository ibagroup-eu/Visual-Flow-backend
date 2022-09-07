/*
 * Copyright (c) 2021 IBA Group, a.s. All rights reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package by.iba.vfapi.exceptions;

import by.iba.vfapi.controllers.ProjectController;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private ProjectController projectController;

    private MockMvc mockMvc;


    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
            .standaloneSetup(projectController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }


    @Test
    void testHandleKubernetesClientException() throws Exception {
        Status status = new StatusBuilder().withStatus("403").withCode(403).withMessage("message").build();
        KubernetesClientException clientException = new KubernetesClientException(status);
        when(projectController.getAll()).thenThrow(clientException);

        mockMvc
            .perform(get("/api/project"))
            .andExpect(status().isForbidden())
            .andExpect(content().string("message"));
    }

    @Test
    void testHandleResourceNotFoundException() throws Exception {
        ResourceNotFoundException resourceNotFoundException = new ResourceNotFoundException("message");
        when(projectController.getAll()).thenThrow(resourceNotFoundException);

        mockMvc.perform(get("/api/project")).andExpect(status().isNotFound());
    }

    @Test
    void testHandleConflictException() throws Exception {
        ConflictException exception = new ConflictException("message");
        when(projectController.getAll()).thenThrow(exception);

        mockMvc.perform(get("/api/project")).andExpect(status().isConflict());
    }

    @Test
    void testHandleBadRequestException() throws Exception {
        BadRequestException exception = new BadRequestException("message");
        when(projectController.getAll()).thenThrow(exception);

        mockMvc.perform(get("/api/project")).andExpect(status().isBadRequest());
    }

    @Test
    void testHandleInternalProcessingException() throws Exception {
        InternalProcessingException exception = new InternalProcessingException("message", new IOException());
        when(projectController.getAll()).thenThrow(exception);

        mockMvc.perform(get("/api/project")).andExpect(status().isInternalServerError());
    }
}
