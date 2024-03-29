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
import io.argoproj.workflow.ApiException;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import redis.clients.jedis.exceptions.JedisConnectionException;

import javax.validation.ConstraintViolationException;

import static by.iba.vfapi.exceptions.ExceptionsConstants.API_PATH;
import static by.iba.vfapi.exceptions.ExceptionsConstants.MESSAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
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
        Status status = new StatusBuilder().withStatus("403").withCode(403).withMessage(MESSAGE).build();
        KubernetesClientException clientException = new KubernetesClientException(status);
        when(projectController.getAll()).thenThrow(clientException);

        mockMvc
            .perform(get(API_PATH))
            .andExpect(status().isForbidden())
            .andExpect(content().string(MESSAGE));
    }

    @Test
    void testHandleKubernetesClientExceptionNullStatus() throws Exception {
        KubernetesClientException clientException = new KubernetesClientException(MESSAGE);
        when(projectController.getAll()).thenThrow(clientException);

        mockMvc
                .perform(get(API_PATH))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(MESSAGE));
    }

    @Test
    void testHandleResourceNotFoundException() throws Exception {
        ResourceNotFoundException resourceNotFoundException = new ResourceNotFoundException(MESSAGE);
        when(projectController.getAll()).thenThrow(resourceNotFoundException);

        mockMvc.perform(get(API_PATH)).andExpect(status().isNotFound());
    }

    @Test
    void testHandleConflictException() throws Exception {
        ConflictException exception = new ConflictException(MESSAGE);
        when(projectController.getAll()).thenThrow(exception);

        mockMvc.perform(get(API_PATH)).andExpect(status().isConflict());
    }

    @Test
    void testHandleBadRequestException() throws Exception {
        BadRequestException exception = new BadRequestException(MESSAGE);
        when(projectController.getAll()).thenThrow(exception);

        mockMvc.perform(get(API_PATH)).andExpect(status().isBadRequest());
    }

    @Test
    void testHandleInternalProcessingException() throws Exception {
        InternalProcessingException exception = new InternalProcessingException(MESSAGE, new IOException());
        when(projectController.getAll()).thenThrow(exception);

        mockMvc.perform(get(API_PATH)).andExpect(status().isInternalServerError());
    }

    @Test
    void testHandleConstraintViolationException() throws Exception {
        ConstraintViolationException exception = new ConstraintViolationException(MESSAGE, null);
        when(projectController.getAll()).thenThrow(exception);

        mockMvc.perform(get(API_PATH)).andExpect(status().isBadRequest());
    }

    @Test
    void testHandleArgoClientException() throws Exception {
        ApiException cause = new ApiException(500, null, "An internal error has been occurred!");
        ArgoClientException exception = new ArgoClientException(cause);
        when(projectController.getAll()).thenThrow(exception);

        mockMvc.perform(get(API_PATH))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("An internal error has been occurred!"));
    }

    @Test
    void testHandleJedisConnectionException() throws Exception {
        JedisConnectionException exception = new JedisConnectionException("An internal error has been occurred!");
        when(projectController.getAll()).thenThrow(exception);
        MvcResult result = mockMvc.perform(get(API_PATH))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andReturn();
        assertEquals("Oops, Redis seems to be down. Contact your environment admin or try again later",
                result.getResponse().getContentAsString(),
                "Exception messages should be equal!");
    }
}
