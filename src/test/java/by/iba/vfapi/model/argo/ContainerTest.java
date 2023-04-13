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

import io.fabric8.kubernetes.api.model.ResourceRequirements;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ContainerTest {

    @Test
    public void testEquals() {
        Container first_obj = new Container();
        first_obj.setImage("image");
        first_obj.setName("first");
        first_obj.setEnvFrom(Collections.emptyList());
        first_obj.setEnv(Collections.emptyList());
        first_obj.setCommand(Collections.emptyList());
        first_obj.setArgs(Collections.emptyList());
        first_obj.setImagePullPolicy("image_pull");
        first_obj.setResources(new ResourceRequirements());
        first_obj.setVolumeMounts(Collections.emptyList());
        Container second_obj = new Container();
        assertNotEquals(first_obj, second_obj, "Objects must NOT be equal!");
    }
}
