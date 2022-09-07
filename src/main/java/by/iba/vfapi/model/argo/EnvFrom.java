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

package by.iba.vfapi.model.argo;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * EnvFrom in container.
 */
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class EnvFrom implements Serializable {
    private static final long serialVersionUID = 1;

    private ConfigMapRef configMapRef;
    private SecretRef secretRef;

    /**
     * Setter for configMapRef.
     *
     * @param configMapRef configMapRef
     * @return this
     */
    public EnvFrom configMapRef(ConfigMapRef configMapRef) {
        this.configMapRef = configMapRef;
        return this;
    }

    /**
     * Setter for secretRef.
     *
     * @param secretRef secretRef
     * @return this
     */
    public EnvFrom secretRef(SecretRef secretRef) {
        this.secretRef = secretRef;
        return this;
    }
}
