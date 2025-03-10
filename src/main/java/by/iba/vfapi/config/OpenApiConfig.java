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

package by.iba.vfapi.config;

import by.iba.vfapi.config.security.WebSecurityConfig;
import by.iba.vfapi.dto.jobs.StageType;
import by.iba.vfapi.services.DateTimeUtils;
import by.iba.vfapi.services.utils.K8sUtils;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.apache.commons.lang3.RandomUtils;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import static by.iba.vfapi.services.utils.K8sUtils.*;

@Configuration
public class OpenApiConfig {

    public static final String SCHEMA_TYPE_FLOAT = "float";
    public static final String SCHEMA_KUBE_UUID_ONE = "KUBE_UUID_ONE";
    public static final String SCHEMA_KUBE_UUID_TWO = "KUBE_UUID_TWO";
    public static final String SCHEMA_UUID_ONE = "UUID_ONE";
    public static final String SCHEMA_UUID_TWO = "UUID_TWO";
    public static final String SCHEMA_PROJECT_NAME = "PROJECT_NAME";
    public static final String SCHEMA_PROJECT_ID = "PROJECT_ID";
    public static final String SCHEMA_PROJECT_DESCRIPTION = "PROJECT_DESCRIPTION";
    public static final String SCHEMA_PROJECT_LOCK_STATUS = "PROJECT_LOCK_STATUS";
    public static final String SCHEMA_PROJECT_LIMIT_CPU = "PROJECT_LIMIT_CPU";
    public static final String SCHEMA_PROJECT_LIMIT_MEMORY = "PROJECT_LIMIT_MEMORY";
    public static final String SCHEMA_PROJECT_REQUIRE_CPU = "PROJECT_REQUIRE_CPU";
    public static final String SCHEMA_PROJECT_REQUIRE_MEMORY = "PROJECT_REQUIRE_MEMORY";
    public static final String SCHEMA_PROJECT_ACCESS_GRANTS = "PROJECT_ACCESS_GRANTS";
    public static final String DATASOURCES_FOR_DEMO = "DATASOURCES_FOR_DEMO";
    public static final String SCHEMA_USER = "USER";
    public static final String SCHEMA_USERS = "USERS";
    public static final String SCHEMA_ROLES = "ROLES";
    public static final String SCHEMA_DATETIME_FIRST = "DATETIME_FIRST";
    public static final String SCHEMA_DATETIME_SECOND = "DATETIME_SECOND";
    public static final String SCHEMA_JOB_STATUS = "JOB_STATUS";
    public static final String SCHEMA_JOB_HISTORY_LOG_ID = "SCHEMA_JOB_HISTORY_LOG_ID";
    public static final String SCHEMA_INSTANCE_UUID_ONE = "INSTANCE_UUID_ONE";
    public static final String SCHEMA_INSTANCE_UUID_TWO = "INSTANCE_UUID_TWO";
    public static final String SCHEMA_JOB_PARAMS = "JOB_PARAMS";
    public static final String SCHEMA_JOB_DEFINITION = "JOB_DEFINITION";
    public static final String SCHEMA_PIPELINE_DEFINITION = "PIPELINE_DEFINITION";
    public static final String SCHEMA_CONNECTIONS_DEFINITION = "CONNECTIONS_DEFINITION";
    public static final String SCHEMA_LOG_LEVELS = "LOG_LEVELS";
    public static final String SCHEMA_PIPELINE_STATUS = "PIPELINE_STATUS";
    public static final String SCHEMA_PIPELINE_NODES_HISTORY = "PIPELINE_NODES_HISTORY";
    public static final String SCHEMA_PIPELINE_STAGE_STATUSES = "PIPELINE_STAGE_STATUSES";
    public static final String SCHEMA_TYPE = "job";
    private static final String SECURITY_SCHEMA_NAME = "bearerAuth";
    private static final String OPERATION = "operation";
    private static final String JDBC_URL = "jdbcUrl";
    private static final String PASSWORD = "password";
    private static final String STORAGE = "storage";
    private static final long RANDOM_BOTTOM_BORDER = 100_000_000;
    private static final long RANDOM_UP_BORDER = 999_999_999;
    private static final int LIMIT_CPU_EXAMPLE = 2;
    private static final int LIMIT_MEMORY_EXAMPLE = 8;
    private static final int REQUIRE_MEMORY_EXAMPLE = 4;

    public static final String SCHEMA_PIPELINE_PARAMETERS = "PIPELINE_PARAMETERS";

    private static final UUID FIRST_UUID = UUID.randomUUID();
    private static final UUID SECOND_UUID = UUID.randomUUID();

    private static StringSchema getArgoModifiedIdAsSchema(String initialId) {
        StringSchema argoSchema = new StringSchema();
        argoSchema
            .example(initialId + "-" + RandomUtils.nextLong(RANDOM_BOTTOM_BORDER, RANDOM_UP_BORDER))
            .description("Pipeline child's id modified by Argo");
        return argoSchema;
    }

    private static Components getComponentsWithPreconfiguredSecurity() {
        return new Components().addSecuritySchemes(SECURITY_SCHEMA_NAME,
                                                   new SecurityScheme()
                                                       .name(SECURITY_SCHEMA_NAME)
                                                       .type(SecurityScheme.Type.HTTP)
                                                       .scheme("bearer")
                                                       .bearerFormat("JWT"));
    }

    private static ComposedSchema getGraphSchemaForJob() {
        ComposedSchema graph = new ComposedSchema();
        graph
            .addOneOfItem(buildNode(Map.of("name",
                                           new StringSchema()
                                               .description("Name of the stage")
                                               .example("read_from_db"),
                                           JDBC_URL,
                                           new StringSchema().example("jdbc:db2://example.com:3308/test"),
                                           OPERATION,
                                           new StringSchema().description("Type of operation").example("READ"),
                                           "user",
                                           new StringSchema().example("userName123"),
                                           PASSWORD,
                                           new StringSchema().example("test123tset"),
                                           "schema",
                                           new StringSchema().example("test_schema"),
                                           STORAGE,
                                           new StringSchema().example("DB2"),
                                           "table",
                                           new StringSchema().example("user_info")), "2"))
            .addOneOfItem(buildNode(Map.of(OPERATION,
                                           new StringSchema().example("FILTER"),
                                           "name",
                                           new StringSchema().example("dummy_filter"),
                                           "condition",
                                           new StringSchema()
                                               .description("Demo filter condition")
                                               .example("DEPT = 'II'")), "3"))
            .addOneOfItem(buildEdge(true, "", "2", "3"))
            .addOneOfItem(buildNode(Map.of("name",
                    new StringSchema()
                            .description("Name of the stage")
                            .example("write_to_another_db"),
                    JDBC_URL,
                    new StringSchema().example("jdbc:db2://example123.com:3308/another"),
                    OPERATION,
                    new StringSchema().description("Type of operation").example("WRITE"),
                    "user",
                    new StringSchema().example("user"),
                    PASSWORD,
                    new StringSchema().example("pw111wp"),
                    "schema",
                    new StringSchema().example("different_schema"),
                    STORAGE,
                    new StringSchema().example("DB2"),
                    "table",
                    new StringSchema().example("2deptUsers")), "5"))
            .addOneOfItem(buildEdge(true, "", "3", "5"));
        return graph;
    }

    private static ComposedSchema getGraphSchemaForPipeline() {
        ComposedSchema graph = new ComposedSchema();
        graph
            .addOneOfItem(buildNode(Map.of("jobId",
                                           new UUIDSchema()
                                               ._default(FIRST_UUID)
                                               .description(
                                                   "Id of a job. Do not confuse it with id pipeline instance"),
                                           "jobName",
                                           new StringSchema().example("test_Job1"),
                                           "name",
                                           new StringSchema()
                                               .description("Name of the Job stage")
                                               .example("example_stage"),
                                           OPERATION,
                                           new StringSchema()
                                               .example("JOB")
                                               .description("Indicates that it's a Job stage")), "2"))
            .addOneOfItem(buildNode(Map.of("addressees",
                                           new StringSchema().example("test@example.com"),
                                           "message",
                                           new StringSchema().example("Test message. The job has failed!"),
                                           "name",
                                           new StringSchema()
                                               .description("Name of the Notification stage")
                                               .example("notif_stg'"),
                                           OPERATION,
                                           new StringSchema()
                                               .example("NOTIFICATION")
                                               .description("Indicates that it's Slack Notification stage")), "3"))
            .addOneOfItem(buildEdge(false, "", "2", "3"));
        return graph;
    }

    private static Map<String, Schema> getGraphSchemaForConnections() {
        return Map.of(STORAGE,
                new StringSchema()
                        .example("db2")
                        .description("Type of connection"),
                "connectionName",
                new StringSchema()
                        .example("TestConnection1"),
                JDBC_URL,
                new StringSchema()
                        .example("jdbc:mysql://mysql.db.server:3306")
                        .description("JDBC URL"),
                "user",
                new StringSchema()
                        .example("userDB")
                        .description("Name of the user"),
                PASSWORD,
                new StringSchema()
                        .example("12345678")
                        .description("Password"));
    }

    private static ObjectSchema buildGraphItem(
        Map<String, Schema<?>> values) {
        ObjectSchema item = new ObjectSchema();
        MapSchema valueMap = new MapSchema();
        values.forEach(valueMap::addProperty);
        item.addProperty("value", valueMap);
        return item;
    }

    private static ObjectSchema buildNode(
        Map<String, Schema<?>> values, String id) {
        return (ObjectSchema) buildGraphItem(values)
            .addProperty("id", new StringSchema().example(id))
            .addProperty("vertex", new BooleanSchema().example(true).description("Identifier of node"));
    }

    private static ObjectSchema buildEdge(Boolean isSuccess, String text, String source, String target) {
        return (ObjectSchema) buildGraphItem(Map.of(OPERATION,
                                                    new StringSchema().example("EDGE"),
                                                    "successPath",
                                                    new StringSchema().example(isSuccess),
                                                    "text",
                                                    new StringSchema().example(text)))
            .addProperty("source", new StringSchema().example(source))
            .addProperty("target", new StringSchema().example(target));
    }

    // Note that this method has the following sonar error: java:S138.
    // This error has been added to the ignore list due to the current inability to solve this problem.
    @Bean
    public OpenAPI customOpenApi() {
        final ZonedDateTime firstTime = ZonedDateTime.now(ZoneId.of("UTC"));
        final ZonedDateTime secondTime = firstTime.plusMinutes(1);
        Components componentsWithSecurity = getComponentsWithPreconfiguredSecurity();
        return new OpenAPI()
            .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEMA_NAME))
            .components(componentsWithSecurity
                            .addSchemas(SCHEMA_UUID_ONE, new UUIDSchema()._default(FIRST_UUID))
                            .addSchemas(SCHEMA_UUID_TWO, new UUIDSchema()._default(SECOND_UUID))
                            .addSchemas(SCHEMA_KUBE_UUID_ONE,
                                        new StringSchema()
                                            .example(K8sUtils.getKubeCompatibleUUID())
                                            .format("uuid"))
                            .addSchemas(SCHEMA_KUBE_UUID_TWO,
                                        new StringSchema()
                                            .example(K8sUtils.getKubeCompatibleUUID())
                                            .format("uuid"))
                            .addSchemas(SCHEMA_PROJECT_ID,
                                        new StringSchema()
                                            .example("namespace-test-project-1")
                                            .description("Project's id. Must begin with k8s namespace name and " +
                                                             "be " +
                                                             "compatible to " +
                                                             "k8s"))
                            .addSchemas(SCHEMA_PROJECT_NAME,
                                        new StringSchema()
                                            .example("test project 1")
                                            .description("Name of the project"))
                            .addSchemas(SCHEMA_PROJECT_DESCRIPTION,
                                        new StringSchema()
                                            .example("Dummy project description for demonstration")
                                            .description("Project's description"))
                            .addSchemas(SCHEMA_PROJECT_LIMIT_CPU,
                                        new NumberSchema()
                                            .type(SCHEMA_TYPE_FLOAT)
                                            .example(LIMIT_CPU_EXAMPLE)
                                            .description(
                                                "Hard cap for CPU resources for the k8s Container,in number of " +
                                                    "cores"))
                            .addSchemas(SCHEMA_PROJECT_REQUIRE_CPU,
                                        new NumberSchema()
                                            .type(SCHEMA_TYPE_FLOAT)
                                            .example(1)
                                            .description(
                                                "Soft cap for CPU resources for the k8s Container,in number of " +
                                                    "cores"))
                            .addSchemas(SCHEMA_PROJECT_LIMIT_MEMORY,
                                        new NumberSchema()
                                            .type(SCHEMA_TYPE_FLOAT)
                                            .example(LIMIT_MEMORY_EXAMPLE)
                                            .description(
                                                "Hard cap for RAM resources for the k8s Container,in Gigabytes"))
                            .addSchemas(SCHEMA_PROJECT_REQUIRE_MEMORY,
                                        new NumberSchema()
                                            .type(SCHEMA_TYPE_FLOAT)
                                            .example(REQUIRE_MEMORY_EXAMPLE)
                                            .description(
                                                "Soft cap for RAM resources for the k8s Container,in Gigabytes"))
                            .addSchemas(SCHEMA_PROJECT_ACCESS_GRANTS,
                                        new MapSchema()
                                            .addProperty("testUser", new StringSchema().example("role-1"))
                                            .addProperty("another_user_",
                                                           new StringSchema().example("super-role"))
                                            .description("Map between user name and application role"))
                            .addSchemas(SCHEMA_USERS, new ArraySchema()
                                .items(new MapSchema()
                                           .addProperty("id",
                                                          new StringSchema().example("2"))
                                           .addProperty("name",
                                                          new StringSchema().example("Jane Doe"))
                                           .addProperty("username",
                                                          new StringSchema().example("d0e_jane"))
                                           .description("Map between ServiceAccount annotation name and " +
                                                            "value"))
                                .description("List of consolidated information about application users"))
                            .addSchemas(SCHEMA_ROLES,
                                        new ArraySchema()
                                            .items(new StringSchema().example("test_role"))
                                            .description("List of defined roles"))
                            .addSchemas(SCHEMA_DATETIME_FIRST,
                                        new StringSchema()
                                            .description("Specially formatted date")
                                            .example(DateTimeUtils.getFormattedDateTime(firstTime.toString())))
                            .addSchemas(SCHEMA_DATETIME_SECOND,
                                        new StringSchema()
                                            .description("Specially formatted date")
                                            .example(DateTimeUtils.getFormattedDateTime(secondTime.toString())))
                            .addSchemas(SCHEMA_JOB_STATUS,
                                        new StringSchema()
                                            .addEnumItem(PENDING_STATUS)
                                            .addEnumItem(RUNNING_STATUS)
                                            .addEnumItem(SUCCEEDED_STATUS)
                                            .addEnumItem(FAILED_STATUS)
                                            .addEnumItem(DRAFT_STATUS)
                                            .description("Status is determined based on Pod's phase"))
                            .addSchemas(SCHEMA_PIPELINE_NODES_HISTORY,
                                        new ObjectSchema()
                                                .addProperty("id", new StringSchema().example("1sadwqq-wesfe"))
                                                .addProperty("startedAt", new StringSchema()
                                                        .example(DateTimeUtils
                                                                .getFormattedDateTime(firstTime.toString())))
                                                .addProperty("finishedAt", new StringSchema()
                                                        .example(DateTimeUtils
                                                                .getFormattedDateTime(secondTime.toString())))
                                                .addProperty("status", new StringSchema()
                                                        .example(SUCCEEDED_STATUS))
                                                .addProperty("logId", new StringSchema().example("123123121"))
                                                .description("Pipeline nodes history Response dto list"))
                            .addSchemas(SCHEMA_INSTANCE_UUID_ONE, getArgoModifiedIdAsSchema(FIRST_UUID.toString()))
                            .addSchemas(SCHEMA_INSTANCE_UUID_TWO,
                                        getArgoModifiedIdAsSchema(SECOND_UUID.toString()))
                            .addSchemas(SCHEMA_JOB_PARAMS,
                                        new ObjectSchema()
                                            .addProperty("DRIVER_CORES", new StringSchema().example("1"))
                                            .addProperty("DRIVER_MEMORY", new StringSchema().example("1"))
                                            .addProperty("DRIVER_REQUEST_CORES",
                                                           new StringSchema().example("0.1"))
                                            .addProperty("EXECUTOR_CORES", new StringSchema().example("1"))
                                            .addProperty("EXECUTOR_INSTANCES", new StringSchema().example("2"))
                                            .addProperty("EXECUTOR_MEMORY", new StringSchema().example("1"))
                                            .addProperty("EXECUTOR_REQUEST_CORES",
                                                           new StringSchema().example("0.1"))
                                            .addProperty("SHUFFLE_PARTITIONS", new StringSchema().example("10"))
                                            .addProperty("TAGS",
                                                           new ArraySchema()
                                                                   .items(new StringSchema().example("VF-Demo")))
                                            .description("Job params that will be passed through in a ConfigMap"))
                            .addSchemas(SCHEMA_JOB_DEFINITION,
                                    new ObjectSchema().addProperty("graph", new ArraySchema()
                                            .items(getGraphSchemaForJob())
                                            .description("This represents a job that consists of 3 stages:READ, " +
                                                    "FILTER and WRITE. It also has all fronted-related data " +
                                                    "intentionally omitted for simplicity")))
                            .addSchemas(SCHEMA_LOG_LEVELS,
                                        new StringSchema()
                                            .addEnumItem("TRACE")
                                            .addEnumItem("DEBUG")
                                            .addEnumItem("INFO")
                                            .addEnumItem("WARNING")
                                            .addEnumItem("ERROR")
                                            .addEnumItem("FATAL")
                                            .description("Available log statuses"))
                            .addSchemas(SCHEMA_PIPELINE_DEFINITION,
                                    new ObjectSchema().addProperty("graph", new ArraySchema()
                                            .items(getGraphSchemaForPipeline())
                                            .description("This represents a pipeline that consists of 2 stages: a " +
                                                    "Job stage and a Notification stage(will be executed only " +
                                                    "upon job failure). It also has all fronted-related data " +
                                                    "intentionally omitted for simplicity")))
                    .addSchemas(SCHEMA_CONNECTIONS_DEFINITION,
                            new ObjectSchema().properties(getGraphSchemaForConnections()))
                    .addSchemas(SCHEMA_PIPELINE_PARAMETERS, new ObjectSchema()
                            .addProperty("NOTIFY_SUCCESS",
                                    new BooleanSchema().example(true).description("Identifier of success notify"))
                            .addProperty("NOTIFY_FAILURE",
                                    new BooleanSchema().example(false).description("Identifier of failure notify"))
                            .addProperty("RECIPIENTS",
                                    new ArraySchema()
                                            .items(new StringSchema().example("JaneDoe@email.com"))
                                            .description("List of notification recipients"))
                            .addProperty("TAGS",
                                    new ArraySchema()
                                            .items(new StringSchema().example("VF-Demo"))
                                            .description("List of tags for grouping pipelines")))
                    .addSchemas(SCHEMA_PIPELINE_STATUS,
                                        new StringSchema()
                                            .addEnumItem(PENDING_STATUS)
                                            .addEnumItem(RUNNING_STATUS)
                                            .addEnumItem(SUCCEEDED_STATUS)
                                            .addEnumItem(FAILED_STATUS)
                                            .addEnumItem(ERROR_STATUS)
                                            .addEnumItem("")
                                            .addEnumItem(DRAFT_STATUS)
                                            .addEnumItem(TERMINATED_STATUS)
                                            .addEnumItem(SUSPENDED_STATUS)
                                            .addEnumItem(STOPPED_STATUS)
                                            .description(
                                                "Status is determined based on Workflow's phase, plus there are " +
                                                    "couple custom ones"))
                            .addSchemas(SCHEMA_PIPELINE_STAGE_STATUSES,
                                        new MapSchema()
                                            .addProperty("2", new StringSchema().example("Failed"))
                                            .addProperty("3", new StringSchema().example("Succeeded")))
                    .addSchemas(SCHEMA_TYPE,
                            new StringSchema()
                                    .addEnumItem("job")
                                    .addEnumItem("pipeline")
                                    .description(
                                            "Type of history"))
                    .addSchemas(SCHEMA_JOB_HISTORY_LOG_ID,
                            new StringSchema().example("1665591306866"))
                    .addSchemas(SCHEMA_USER, new StringSchema().example("jane-doe"))
                    .addSchemas(DATASOURCES_FOR_DEMO, new MapSchema()
                            .addProperty(StageType.READ.name(), new ArraySchema()
                                    .items(new StringSchema().example("DATAFRAME")))
                            .addProperty(StageType.WRITE.name(), new ArraySchema()
                                    .items(new StringSchema().example("AWS"))))
            )
            .info(new Info().title("Visual Flow").description("Visual Flow backend API"));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi
            .builder()
            .group("public")
            .packagesToScan("by.iba.vfapi")
            .pathsToMatch(WebSecurityConfig.API_PATH, WebSecurityConfig.PUBLIC_API_PATH)
            .build();
    }


}
