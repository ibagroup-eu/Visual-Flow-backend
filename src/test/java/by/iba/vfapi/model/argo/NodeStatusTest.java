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

import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NodeStatusTest {

    @Test
    public void testSetters() {
        NodeStatus nodeStatus = new NodeStatus();
        nodeStatus.setChildren(Collections.emptyList());
        nodeStatus.setDisplayName("display_name");
        nodeStatus.setFinishedAt(DateTime.parse("1999-11-04"));
        nodeStatus.setId("id");
        nodeStatus.setMessage("message");
        nodeStatus.setName("name");
        nodeStatus.setPhase("phase");
        nodeStatus.setStartedAt(DateTime.parse("1997-08-12"));
        nodeStatus.setTemplateName("template");
        nodeStatus.setTemplateScope("scope");
        nodeStatus.setType("type");
        nodeStatus.setWorkflowTemplateName("workflow");
        nodeStatus.setProgress("89");
        assertEquals(0, nodeStatus.getChildren().size(), "Children list size must be 0!");
        assertEquals("display_name", nodeStatus.getDisplayName(), "Display name must be equal to expected!");
        assertEquals("1999-11-04", nodeStatus.getFinishedAt().toString().substring(0,10),
                "Finished at time must be equal to expected!");
        assertEquals("id", nodeStatus.getId(), "Id must be equal to expected!");
        assertEquals("message", nodeStatus.getMessage(), "Message must be equal to expected!");
        assertEquals("name", nodeStatus.getName(), "Name must be equal to expected!");
        assertEquals("phase", nodeStatus.getPhase(), "Phase must be equal to expected!");
        assertEquals("1997-08-12", nodeStatus.getStartedAt().toString().substring(0,10),
                "Started at time must be equal to expected!");
        assertEquals("template", nodeStatus.getTemplateName(), "Template must be equal to expected!");
        assertEquals("scope", nodeStatus.getTemplateScope(), "Scope must be equal to expected!");
        assertEquals("type", nodeStatus.getType(), "Type must be equal to expected!");
        assertEquals("workflow", nodeStatus.getWorkflowTemplateName(), "Workflow must be equal to expected!");
        assertEquals("89", nodeStatus.getProgress(), "Progress must be equal to expected!");

    }
}
