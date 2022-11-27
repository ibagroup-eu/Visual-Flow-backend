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

import java.time.format.DateTimeFormatter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Constants class.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Constants {
    public static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
    public static final String NAME_PATTERN = "[A-Za-z0-9 \\-_]{3,40}";
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    public static final String PARAM_KEY_PATTERN = "[A-Za-z0-9\\-_]{1,50}";
    public static final String CONNECTION_KEY_PATTERN = "[A-Za-z0-9\\-_]{1,50}";
    public static final String JOB_CONFIG_FIELD = "JOB_CONFIG";
    public static final String NAME = "name";
    public static final String TYPE = "type";
    public static final String TYPE_JOB = "job";
    public static final String TYPE_PIPELINE = "pipeline";
    public static final String DEFINITION = "definition";
    public static final String LAST_MODIFIED = "lastModified";

    public static final String DESCRIPTION_FIELD = "description";
    public static final String NAME_FIELD = "projectName";

    public static final String JOB_ID_LABEL = "jobId";
    public static final String SPARK_ROLE_LABEL = "spark-role";
    public static final String SPARK_ROLE_EXEC = "executor";
    public static final String WORKFLOW_POD_LABEL = "workflows.argoproj.io/workflow";
    public static final String CRON_WORKFLOW_POD_LABEL = "workflows.argoproj.io/cron-workflow";
    public static final String PIPELINE_JOB_ID_LABEL = "pipelineJobId";
    public static final String PIPELINE_ID_LABEL = "pipelineId";
    public static final String NOT_PIPELINE_FLAG = "notPipeline";

    public static final String LIMITS_CPU = "limits.cpu";
    public static final String REQUESTS_CPU = "requests.cpu";
    public static final String LIMITS_MEMORY = "limits.memory";
    public static final String REQUESTS_MEMORY = "requests.memory";
    public static final String QUOTA_NAME = "quota";
    public static final String GIGABYTE_QUANTITY = "G";
    public static final int USAGE_ACCURACY = 2;
    public static final String EXECUTOR_CORES = "EXECUTOR_CORES";
    public static final String EXECUTOR_INSTANCES = "EXECUTOR_INSTANCES";
    public static final String EXECUTOR_MEMORY = "EXECUTOR_MEMORY";
    public static final String EXECUTOR_REQUEST_CORES = "EXECUTOR_REQUEST_CORES";
    public static final String SHUFFLE_PARTITIONS = "SHUFFLE_PARTITIONS";
    public static final String TAGS = "TAGS";
    public static final String CPU_FIELD = "cpu";
    public static final String MEMORY_FIELD = "memory";
    public static final String DRIVER_CORES = "DRIVER_CORES";
    public static final String DRIVER_CORES_VALUE = "300m";
    public static final String DRIVER_MEMORY = "DRIVER_MEMORY";
    public static final String DRIVER_MEMORY_VALUE = "300Mi";
    public static final String DRIVER_REQUEST_CORES = "DRIVER_REQUEST_CORES";
    public static final String DRIVER_REQUEST_CORES_VALUE = "100m";
    public static final double MEMORY_OVERHEAD_FACTOR = 1.1;
    public static final String DAG_TEMPLATE_NAME = "dagTemplate";

    public static final String UPDATE_ACTION = "update";
    public static final String CREATE_ACTION = "create";

    public static final String EDGE_SUCCESS_PATH = "successPath";
    public static final String EDGE_SUCCESS_PATH_POSITIVE = "true";
    public static final String EDGE_SUCCESS_PATH_NEGATIVE = "false";

    public static final String NODE_TYPE_POD = "Pod";

    public static final String STARTED_AT = "STARTED_AT";
    public static final String FINISHED_AT = "FINISHED_AT";
    public static final String PROGRESS = "PROGRESS";
    public static final String ANNOTATION_JOB_STATUSES = "AnnotationForJobStatuses";
    public static final String CONTAINER_STAGE = "CONTAINER_STAGE";
    public static final String NODE_OPERATION = "operation";
    public static final String NODE_OPERATION_EDGE = "EDGE";
    public static final String NODE_OPERATION_JOB = "JOB";
    public static final String NODE_OPERATION_PIPELINE = "PIPELINE";
    public static final String NODE_OPERATION_NOTIFICATION = "NOTIFICATION";
    public static final String NODE_OPERATION_CONTAINER = "CONTAINER";
    public static final String NODE_OPERATION_WAIT = "WAIT";
    public static final String NODE_NAME = "name";
    public static final String NODE_JOB_ID = "jobId";
    public static final String NODE_PIPELINE_ID = "pipelineId";
    public static final String NODE_IMAGE_LINK = "image";
    public static final String NODE_REGISTRY_LINK = "registry";
    public static final String NODE_USERNAME = "username";
    public static final String NODE_PASSWORD = "password";
    public static final String NODE_IMAGE_PULL_POLICY = "imagePullPolicy";
    public static final String NODE_START_COMMAND = "command";
    public static final String NODE_MOUNT_PROJECT_PARAMS = "mountProjectParams";
    public static final String NODE_NOTIFICATION_RECIPIENTS = "addressees";
    public static final String NODE_NOTIFICATION_MESSAGE = "message";
    public static final String KIND_JOB = "Job";
    public static final String KIND_PIPELINE = "Pipeline";
    public static final String NODE_IMAGE_PULL_SECRET_TYPE = "imagePullSecretType";
    public static final String NODE_IMAGE_PULL_SECRET_NAME = "imagePullSecretName";
    public static final String CONTAINER_NODE_ID = "containerNodeId";
    public static final String SECRETS = "secrets";
    public static final String TOKEN = "token";
    public static final String DOCKERCFG = "dockercfg";
    public static final String STARTED_BY = "startedBy";
    public static final String PIPELINE_HISTORY = "pipeline_history";
    public static final String PIPELINE_NODE_HISTORY = "pipeline_node_history";
    public static final String LOGS = "logs";
}
