/*
 * Copyright (c) 2022 IBA Group, a.s. All rights reserved.
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

package by.iba.vfapi.model.argo;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WorkflowSpecTest {

    @Test
    void testPipelineSetters() {
        WorkflowSpec workflowSpec = new WorkflowSpec()
                .serviceAccountName("service_test")
                .entrypoint("entrypoint")
                .templates(Collections.singleton(new Template()))
                .imagePullSecrets(Collections.singleton(new ImagePullSecret()));
        assertEquals("service_test", workflowSpec.getServiceAccountName(),
                "Service account name must be equal to expected!");
        assertEquals("entrypoint", workflowSpec.getEntrypoint(), "Entrypoint must be equal to expected!");
        assertEquals(1, workflowSpec.getTemplates().size(), "Template size must be 1!");
        assertEquals(1, workflowSpec.getImagePullSecrets().size(), "ImagePullSecrets size must be 1!");
    }

}
