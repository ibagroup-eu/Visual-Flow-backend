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

package by.iba.vfapi.model.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.ObjectUtils;

/**
 * Java representation of user info from OAuth service.
 */
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@AllArgsConstructor
@Schema(description = "Describes application user")
public final class UserInfo {
    @Schema(description = "User id. The format is decided by OAuth server", example = "1")
    private String id;
    @Schema(description = "User's full name", example = "John Doe")
    private String name;
    @Schema(description = "User's nickname", example = "j0hn-d0e")
    private String username;
    @Schema(description = "User's email address", example = "john.doe@example.com")
    private String email;
    @Schema(description = "Whether superuser permission has been granted")
    private boolean superuser;

    /**
     * Identifies if all required fields are populated
     *
     * @return true if fields are populated
     */
    public boolean hasAllInformation() {
        return ObjectUtils.allNotNull(id, name, username, email);
    }
}
