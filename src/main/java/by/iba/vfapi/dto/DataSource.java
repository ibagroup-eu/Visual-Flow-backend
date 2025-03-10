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
package by.iba.vfapi.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum for Data Sources in stages.
 * Should be synchronized with UI.
 * Mainly is used for demo configuration
 * for projects.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum DataSource {
    AWS("s3"),
    DB2("db2"),
    CASSANDRA("cassandra"),
    ELASTIC("elastic"),
    COS("cos"),
    MONGO("mongo"),
    MYSQL("mysql"),
    MSSQL("mssql"),
    ORACLE("oracle"),
    POSTGRE("postgresql"),
    REDIS("redis"),
    REDSHIFT("redshift"),
    REDSHIFTJDBC("redshift-jdbc"),
    DATAFRAME("dataframe"),
    CLUSTER("cluster"),
    STDOUT("stdout"),
    CLICKHOUSE("clickhouse"),
    KAFKA("kafka"),
    API("request");

    private final String value;
}
