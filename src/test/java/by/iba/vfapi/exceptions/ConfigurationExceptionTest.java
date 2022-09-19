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

import org.junit.jupiter.api.Test;

import static by.iba.vfapi.exceptions.ExceptionsConstants.MESSAGE;
import static by.iba.vfapi.exceptions.ExceptionsConstants.MESSAGE_CONDITION;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigurationExceptionTest {

    @Test
    void testConfigurationException() {
        ConfigurationException configurationException = new ConfigurationException(MESSAGE);
        assertEquals(MESSAGE, configurationException.getMessage(), MESSAGE_CONDITION);
    }

    @Test
    void testConfigurationExceptionWithParams() {
        Exception ex = new Exception();
        ConfigurationException configurationException = new ConfigurationException(MESSAGE, ex);

        assertEquals(MESSAGE, configurationException.getMessage(), MESSAGE_CONDITION);
        assertEquals(ex, configurationException.getCause(), "Cause must be equals to expected");
    }
}