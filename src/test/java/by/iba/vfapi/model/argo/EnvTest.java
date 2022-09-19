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

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class EnvTest {

    @Test
    public void testEquals() {
        Env first_obj = new Env();
        first_obj.setName("first");
        first_obj.setValue("val");
        first_obj.setValueFrom(new ValueFrom());
        Env second_obj = new Env();
        second_obj.setName("second");
        second_obj.setValue("val");
        second_obj.setValueFrom(new ValueFrom());
        assertNotEquals(first_obj, second_obj, "Objects must NOT be equal!");
    }
}
