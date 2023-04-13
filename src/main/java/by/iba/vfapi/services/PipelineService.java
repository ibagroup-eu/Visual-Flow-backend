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

package by.iba.vfapi.services;

import by.iba.vfapi.config.MailSenderConfig;
import by.iba.vfapi.dao.PipelineHistoryRepository;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.dto.history.PipelineHistoryResponseDto;
import by.iba.vfapi.dto.history.PipelineNodesHistoryResponseDto;
import by.iba.vfapi.dto.pipelines.CronPipelineDto;
import by.iba.vfapi.dto.pipelines.PipelineOverviewDto;
import by.iba.vfapi.dto.pipelines.PipelineOverviewListDto;
import by.iba.vfapi.dto.pipelines.PipelineResponseDto;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.dto.projects.ParamsDto;
import by.iba.vfapi.exceptions.ArgoClientException;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.exceptions.InternalProcessingException;
import by.iba.vfapi.model.ContainerStageConfig;
import by.iba.vfapi.model.argo.Arguments;
import by.iba.vfapi.model.argo.ConfigMapRef;
import by.iba.vfapi.model.argo.Container;
import by.iba.vfapi.model.argo.CronWorkflow;
import by.iba.vfapi.model.argo.CronWorkflowSpec;
import by.iba.vfapi.model.argo.DagTask;
import by.iba.vfapi.model.argo.DagTemplate;
import by.iba.vfapi.model.argo.Env;
import by.iba.vfapi.model.argo.EnvFrom;
import by.iba.vfapi.model.argo.FieldRef;
import by.iba.vfapi.model.argo.ImagePullSecret;
import by.iba.vfapi.model.argo.Inputs;
import by.iba.vfapi.model.argo.NodeStatus;
import by.iba.vfapi.model.argo.Parameter;
import by.iba.vfapi.model.argo.PipelineParams;
import by.iba.vfapi.model.argo.Resource;
import by.iba.vfapi.model.argo.RuntimeData;
import by.iba.vfapi.model.argo.SecretRef;
import by.iba.vfapi.model.argo.Step;
import by.iba.vfapi.model.argo.Template;
import by.iba.vfapi.model.argo.TemplateMeta;
import by.iba.vfapi.model.argo.ValueFrom;
import by.iba.vfapi.model.argo.VolumeMounts;
import by.iba.vfapi.model.argo.Volumes;
import by.iba.vfapi.model.argo.PersistentVolumeClaim;
import by.iba.vfapi.model.argo.Workflow;
import by.iba.vfapi.model.argo.WorkflowSpec;
import by.iba.vfapi.model.argo.WorkflowStatus;
import by.iba.vfapi.model.argo.WorkflowTemplate;
import by.iba.vfapi.model.argo.WorkflowTemplateRef;
import by.iba.vfapi.model.argo.WorkflowTemplateSpec;
import by.iba.vfapi.model.history.AbstractHistory;
import by.iba.vfapi.model.history.PipelineHistory;
import by.iba.vfapi.model.history.PipelineNodeHistory;
import by.iba.vfapi.services.auth.AuthenticationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.argoproj.workflow.ApiException;
import io.argoproj.workflow.apis.WorkflowServiceApi;
import io.argoproj.workflow.models.WorkflowResumeRequest;
import io.argoproj.workflow.models.WorkflowRetryRequest;
import io.argoproj.workflow.models.WorkflowStopRequest;
import io.argoproj.workflow.models.WorkflowSuspendRequest;
import io.argoproj.workflow.models.WorkflowTerminateRequest;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.ResourceNotFoundException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static by.iba.vfapi.dto.Constants.CONTAINER_NODE_ID;
import static by.iba.vfapi.dto.Constants.NODE_JOB_ID;
import static by.iba.vfapi.dto.Constants.NODE_PIPELINE_ID;
import static by.iba.vfapi.dto.Constants.NODE_OPERATION;
import static by.iba.vfapi.dto.Constants.NODE_OPERATION_CONTAINER;
import static by.iba.vfapi.dto.Constants.NODE_OPERATION_JOB;
import static by.iba.vfapi.dto.Constants.NODE_OPERATION_PIPELINE;
import static by.iba.vfapi.dto.Constants.NODE_OPERATION_NOTIFICATION;
import static by.iba.vfapi.dto.Constants.NODE_OPERATION_WAIT;
import static by.iba.vfapi.dto.Constants.PIPELINE_ID_LABEL;
import static by.iba.vfapi.dto.Constants.NODE_NAME;
import static by.iba.vfapi.dto.Constants.PIPELINE_HISTORY;
import static by.iba.vfapi.dto.Constants.PIPELINE_NODE_HISTORY;
import static by.iba.vfapi.dto.Constants.PIPELINE_NAME_LABEL;

/**
 * PipelineService class.
 */
@Slf4j
@Service
@Getter
public class PipelineService {
    public static final String LIMITS_CPU = "limitsCpu";
    public static final String REQUESTS_CPU = "requestsCpu";
    public static final String LIMITS_MEMORY = "limitsMemory";
    public static final String REQUESTS_MEMORY = "requestsMemory";
    public static final String IMAGE_LINK = "imageLink";
    public static final String IMAGE_PULL_POLICY = "imagePullPolicy";
    public static final String COMMAND = "command";
    public static final String SPARK_TEMPLATE_NAME = "sparkTemplate";
    public static final String NOTIFICATION_TEMPLATE_NAME = "notificationTemplate";
    public static final String PIPELINE_TEMPLATE_NAME = "pipelineTemplate";
    public static final String PIPELINE_ID = "pipelineId";
    public static final String CONTAINER_WITH_CMD_TEMPLATE_NAME = "containerTemplateWithCmd";
    public static final String CONTAINER_WITH_CMD_AND_PROJECT_PARAMS_TEMPLATE_NAME =
        "containerTemplateWithCmdAndProjectParams";
    public static final String CONTAINER_TEMPLATE_NAME = "containerTemplate";
    public static final String CONTAINER_TEMPLATE_WITH_PROJECT_PARAMS_NAME = "containerTemplateWithProjectParams";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEPENDS_OPERATOR_LENGTH = 4;
    private static final String GRAPH_ID = "graphId";
    private static final String INPUT_PARAMETER_PATTERN = "{{inputs.parameters.%s}}";
    private final String host;
    private final String slackToken;
    private final MailSenderConfig mailSenderConfig;
    private final String sparkImage;
    private final ArgoKubernetesService argoKubernetesService;
    private final ProjectService projectService;
    private final WorkflowServiceApi apiInstance;
    private final String jobMaster;
    private final String serviceAccount;
    private final String imagePullSecret;
    private final String notificationImage;
    private final String pvcMountPath;
    private final WorkflowService workflowService;
    private final AuthenticationService authenticationService;
    private final DependencyHandlerService dependencyHandlerService;
    private final PipelineHistoryRepository<? extends AbstractHistory> pipelineHistoryRepository;

    public PipelineService(
        @Value("${job.spark.image}") String sparkImage,
        @Value("${job.spark.master}") final String jobMaster,
        @Value("${job.spark.serviceAccount}") final String serviceAccount,
        @Value("${job.imagePullSecret}") final String imagePullSecret,
        @Value("${pvc.mountPath}") final String pvcMountPath,
        @Value("${server.host}") final String host,
        @Value("${notifications.image}") final String notificationImage,
        @Value("${notifications.slack.token}") final String slackToken,
        MailSenderConfig mailSenderConfig,
        ArgoKubernetesService argoKubernetesService,
        ProjectService projService,
        WorkflowServiceApi apiInstance,
        final WorkflowService workflowService,
        final AuthenticationService authenticationService,
        final DependencyHandlerService dependencyHandlerService,
        final PipelineHistoryRepository<? extends AbstractHistory> pipelineHistoryRepository) {
        this.host = host;
        this.notificationImage = notificationImage;
        this.slackToken = slackToken;
        this.mailSenderConfig = mailSenderConfig;
        this.sparkImage = sparkImage;
        this.jobMaster = jobMaster;
        this.serviceAccount = serviceAccount;
        this.imagePullSecret = imagePullSecret;
        this.argoKubernetesService = argoKubernetesService;
        this.projectService = projService;
        this.apiInstance = apiInstance;
        this.pvcMountPath = pvcMountPath;
        this.workflowService = workflowService;
        this.authenticationService = authenticationService;
        this.dependencyHandlerService = dependencyHandlerService;
        this.pipelineHistoryRepository = pipelineHistoryRepository;
    }

    /**
     * Creating DAGTask for spark job.
     *
     * @param name           task name
     * @param depends        String of dependencies
     * @param parameterValue value of parameter 'configMap'
     * @param graphId        value of node id from graph
     * @return new DAGTask
     */
    private static DagTask createSparkDagTask(
        String name,
        String depends,
        @NotNull String parameterValue,
        String graphId,
        String pipelineId,
        String jobName,
        String operation
    ) {
        return new DagTask()
            .name(name)
            .template(SPARK_TEMPLATE_NAME)
            .depends(depends)
            .arguments(new Arguments()
                           .addParametersItem(new Parameter().name(K8sUtils.CONFIGMAP).value(parameterValue))
                           .addParametersItem(new Parameter().name(NODE_NAME).value(jobName))
                           .addParametersItem(new Parameter().name(NODE_OPERATION).value(operation))
                           .addParametersItem(new Parameter().name(PIPELINE_ID_LABEL).value(pipelineId))
                           .addParametersItem(new Parameter().name(GRAPH_ID).value(graphId)));
    }

    /**
     * Creating DAGTask for container stage.
     *
     * @param name    task name
     * @param depends task's depends
     * @param config  container stage config
     * @param nodeId  node id
     * @return task
     */
    private static DagTask createContainerDagTask(
        String name,
        String depends,
        ContainerStageConfig config,
        String nodeId,
        String pipelineId,
        String jobName,
        String operation) {
        Arguments arguments = new Arguments()
            .addParametersItem(new Parameter().name(IMAGE_PULL_POLICY).value(config.getImagePullPolicy()))
            .addParametersItem(new Parameter().name(IMAGE_LINK).value(config.getImageLink()))
            .addParametersItem(new Parameter()
                                   .name(LIMITS_CPU)
                                   .value(Quantity.parse(config.getLimitsCpu()).toString()))
            .addParametersItem(new Parameter()
                                   .name(LIMITS_MEMORY)
                                   .value(Quantity.parse(config.getLimitsMemory()).toString()))
            .addParametersItem(new Parameter()
                                   .name(REQUESTS_CPU)
                                   .value(Quantity.parse(config.getRequestCpu()).toString()))
            .addParametersItem(new Parameter()
                                   .name(REQUESTS_MEMORY)
                                   .value(Quantity.parse(config.getRequestMemory()).toString()))
            .addParametersItem(new Parameter().name(Constants.CONTAINER_NODE_ID).value(nodeId))
            .addParametersItem(new Parameter().name(Constants.PIPELINE_ID_LABEL).value(pipelineId))
            .addParametersItem(new Parameter().name(GRAPH_ID).value(nodeId))
            .addParametersItem(new Parameter().name(NODE_NAME).value(jobName))
            .addParametersItem(new Parameter().name(NODE_OPERATION).value(operation));
        boolean withCustomCommand = config.getStartCommand() != null && !config.getStartCommand().isEmpty();
        String template = composeContainerTemplateName(withCustomCommand, config.isMountProjectParams());
        if (withCustomCommand) {
            arguments.addParametersItem(new Parameter().name(COMMAND).value(config.getStartCommand()));
        }
        return new DagTask().name(name).template(template).depends(depends).arguments(arguments);
    }

    /**
     * Creating DAGTask for slack job.
     *
     * @param name       task name
     * @param depends    String of dependencies
     * @param addressees value of parameter 'addressees'
     * @param message    value of parameter 'message'
     * @param graphId    value of node id from graph
     * @return new DAGTask
     */
    private static DagTask createNotificationDagTask(
        String name,
        String depends,
        String addressees,
        String message,
        String graphId,
        String jobName,
        String operation) {
        return new DagTask()
            .name(name)
            .template(NOTIFICATION_TEMPLATE_NAME)
            .depends(depends)
            .arguments(new Arguments()
                           .addParametersItem(new Parameter()
                                                  .name(Constants.NODE_NOTIFICATION_RECIPIENTS)
                                                  .value(Arrays
                                                             .stream(addressees.split(" "))
                                                             .map(StringEscapeUtils::escapeXSI)
                                                             .collect(Collectors.joining(" "))))
                           .addParametersItem(new Parameter()
                                                  .name(Constants.NODE_NOTIFICATION_MESSAGE)
                                                  .value(StringEscapeUtils.escapeXSI(message)))
                           .addParametersItem(new Parameter().name(GRAPH_ID).value(graphId))
                           .addParametersItem(new Parameter().name(NODE_NAME).value(jobName))
                           .addParametersItem(new Parameter().name(NODE_OPERATION).value(operation)));
    }

    /**
     * Creating DAGTask for pipeline.
     *
     * @param name       task name
     * @param depends    String of dependencies
     * @param pipelineId value of parameter 'pipelineId'
     * @param graphId    value of node id from graph
     * @return new DAGTask
     */
    private static DagTask createPipelineDagTask(
            String name, String depends, String pipelineId, String graphId, String jobName, String operation) {
        return new DagTask()
                .name(name)
                .template(PIPELINE_TEMPLATE_NAME)
                .depends(depends)
                .arguments(new Arguments()
                        .addParametersItem(new Parameter().name(PIPELINE_ID).value(pipelineId))
                        .addParametersItem(new Parameter().name(GRAPH_ID).value(graphId))
                        .addParametersItem(new Parameter().name(NODE_NAME).value(jobName))
                        .addParametersItem(new Parameter().name(NODE_OPERATION).value(operation)));
    }

    /**
     * Set metadata to workflowTemplate.
     *
     * @param workflowTemplate     workflowTemplate
     * @param workflowTemplateId   workflowTemplate id
     * @param workflowTemplateName workflowTemplate name
     * @param definition           definition for workflowTemplate
     */
    static void setMeta(
        WorkflowTemplate workflowTemplate,
        String workflowTemplateId,
        String workflowTemplateName,
        JsonNode definition) {
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName(workflowTemplateId)
                                         .addToLabels(Constants.NAME, workflowTemplateName)
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(definition
                                                                                         .toString()
                                                                                         .getBytes(StandardCharsets.UTF_8)))
                                         .addToAnnotations(Constants.LAST_MODIFIED,
                                                           ZonedDateTime
                                                               .now()
                                                               .format(Constants.DATE_TIME_FORMATTER))
                                         .build());
    }

    /**
     * Replacing ids in pipelines nodes.
     *
     * @param nodes list of GraphDto.NodeDto
     * @param edges list of GraphDto.EdgeDto
     */
    private static void replaceIds(
        Iterable<GraphDto.NodeDto> nodes, Iterable<GraphDto.EdgeDto> edges) {
        for (GraphDto.NodeDto node : nodes) {
            String id = node.getId();
            String generatedId = K8sUtils.getKubeCompatibleUUID();
            node.setId(generatedId);
            node.getValue().put(generatedId, id);

            for (GraphDto.EdgeDto edge : edges) {
                if (edge.getSource().equals(id)) {
                    edge.setSource(generatedId);
                    continue;
                }
                if (edge.getTarget().equals(id)) {
                    edge.setTarget(generatedId);
                }
            }
        }
    }

    /**
     * Create dag flow in templates.
     *
     * @param graphDto             nodes and edges
     * @param containerStageConfig configuration for container stage nodes
     * @return dag template
     */
    private static DagTemplate createDagFlow(
        GraphDto graphDto,
        Map<GraphDto.NodeDto, ContainerStageConfig> containerStageConfig,
        WorkflowTemplate workflowTemplate) {
        List<GraphDto.NodeDto> nodes = graphDto.getNodes();
        List<GraphDto.EdgeDto> edges = graphDto.getEdges();
        replaceIds(nodes, edges);
        validateNodes(nodes);
        List<GraphDto.NodeDto> waitNodes = nodes
                .stream()
                .filter((GraphDto.NodeDto n) -> n.getValue().get(NODE_OPERATION).equals(NODE_OPERATION_WAIT))
                .collect(Collectors.toList());
        nodes = nodes
                .stream()
                .filter((GraphDto.NodeDto n) -> !n.getValue().get(NODE_OPERATION).equals(NODE_OPERATION_WAIT))
                .collect(Collectors.toList());

        checkSourceArrows(edges);

        DagTemplate dagTemplate = new DagTemplate();
        for (GraphDto.NodeDto node : nodes) {
            String id = node.getId();
            String nodeDependencies = accumulateDepends(edges, id);
            StringBuilder waitNodeDependencies = new StringBuilder();
            for (String nodeDependency: parseDependenciesAsList(nodeDependencies)) {
                String waitNodeID = checkDependencyForWaitNode(waitNodes, nodeDependency);
                if(waitNodeID != null) {
                    waitNodeDependencies.append(formatDependencies(
                                waitNodeDependencies,
                                accumulateDepends(edges, waitNodeID)));
                    nodeDependencies = waitNodeDependencies.toString();
                }
            }
            DagTask dagTask;
            String operation = node.getValue().get(NODE_OPERATION);
            String stageName = node.getValue().get(NODE_NAME);
            switch (operation) {
                case NODE_OPERATION_JOB:
                    dagTask = createSparkDagTask(id, nodeDependencies,
                                                 node.getValue().get(NODE_JOB_ID),
                                                 node.getValue().get(id),
                                                 workflowTemplate.getMetadata().getName(),
                                                 stageName,
                                                 operation);
                    break;
                case NODE_OPERATION_PIPELINE:
                    dagTask = createPipelineDagTask(id, nodeDependencies,
                                                 node.getValue().get(NODE_PIPELINE_ID),
                                                 node.getValue().get(id),
                                                 stageName,
                                                 operation);
                    break;
                case NODE_OPERATION_NOTIFICATION:
                    dagTask = createNotificationDagTask(id, nodeDependencies,
                                                        node.getValue()
                                                            .get(Constants.NODE_NOTIFICATION_RECIPIENTS),
                                                        node.getValue().get(Constants.NODE_NOTIFICATION_MESSAGE),
                                                        node.getValue().get(id),
                                                        stageName,
                                                        operation);
                    break;
                case NODE_OPERATION_CONTAINER:
                    Optional<ContainerStageConfig> nodeConfig = containerStageConfig
                        .entrySet()
                        .stream()
                        .filter(e -> e.getKey().equals(node))
                        .findFirst()
                        .map(Map.Entry::getValue);
                    if (nodeConfig.isEmpty()) {
                        throw new InternalProcessingException("Cannot find container config for a node " +
                                                                  node.getId());
                    }
                    dagTask = createContainerDagTask(id, nodeDependencies,
                                                     nodeConfig.get(),
                                                     node.getValue().get(id),
                                                     workflowTemplate.getMetadata().getName(),
                                                     stageName,
                                                     operation);
                    break;
                default:
                    throw new BadRequestException("Unknown operation type");
            }
            dagTemplate.addTasksItem(dagTask);
        }
        return dagTemplate;
    }

    /**
     * Validate graph nodes list.
     *
     * @param nodes list of graph nodes
     */
    private static void validateNodes(List<GraphDto.NodeDto> nodes) {
        nodes
            .stream()
            .filter((GraphDto.NodeDto n) -> n.getValue().get(NODE_JOB_ID) != null)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .filter((Map.Entry<GraphDto.NodeDto, Long> e) -> e.getValue() > 1)
            .findAny()
            .ifPresent((Map.Entry<GraphDto.NodeDto, Long> e) -> {
                throw new BadRequestException("Job can't be used more than once in pipeline");
            });
    }

    /**
     * Format dependencies result for wait node.
     *
     * @param waitNodeDependencies  final result wait dependencies
     * @param dependencies dependencies for some pipeline stage id
     * @return String formated dependencies result
     */
    private static String formatDependencies(
            StringBuilder waitNodeDependencies,
            String dependencies) {
        if(waitNodeDependencies.toString().isEmpty()) {
            return dependencies;
        } else {
            return String.format(" && %s", dependencies);
        }
    }

    /**
     * Accumulate dependencies for node.
     *
     * @param edges  edges
     * @param nodeId node id
     * @return String with dependencies
     */
    private static String accumulateDepends(Iterable<GraphDto.EdgeDto> edges, String nodeId) {
        StringBuilder depends = new StringBuilder();
        for (GraphDto.EdgeDto edge : edges) {
            if (edge.getTarget().equals(nodeId)) {
                boolean isSuccessPath = isSuccessPath(edge);
                if (isSuccessPath && depends.indexOf("|") == -1) {
                    depends.append(prepareDependency("&&", edge));
                } else if (!isSuccessPath && depends.indexOf("&") == -1) {
                    depends.append(prepareDependency("||", edge));
                } else {
                    throw new BadRequestException("Node can't have different type of income arrows");
                }
            }
        }

        if (depends.length() == 0) {
            return null;
        }
        return depends.substring(DEPENDS_OPERATOR_LENGTH);
    }

    /**
     * Get variable successPath.
     *
     * @param edge edge
     * @return successPath value
     */
    private static boolean isSuccessPath(GraphDto.EdgeDto edge) {
        return Boolean.parseBoolean(edge.getValue().get("successPath"));
    }

    /**
     * Check failure path count.
     *
     * @param edges edges
     */
    private static void checkSourceArrows(Iterable<GraphDto.EdgeDto> edges) {
        Set<String> set = new HashSet<>();
        for (GraphDto.EdgeDto edge : edges) {
            if (!isSuccessPath(edge)) {
                if (set.contains(edge.getSource())) {
                    throw new BadRequestException("Node can't have more than one failure path");
                } else {
                    set.add(edge.getSource());
                }
            }
        }
    }

    /**
     * Create str with dependency from edge.
     *
     * @param operator operator for depends
     * @param edge     edge
     * @return str with dependency from edge
     */
    private static String prepareDependency(String operator, GraphDto.EdgeDto edge) {
        String source = edge.getSource();
        if ("&&".equals(operator)) {
            return String.format(" && %s", source);
        }
        return String.format(" || %s.Failed || %s.Errored", source, source);
    }

    /**
     * Creating Template with DAGTemplate from graph.
     *
     * @param graphDto graph for workflow
     * @return Template with dag
     */
    private static Template createTemplateWithDag(
        GraphDto graphDto,
        Map<GraphDto.NodeDto, ContainerStageConfig> containerStageConfig,
        WorkflowTemplate workflowTemplate) {
        DagTemplate dagFlow = createDagFlow(graphDto, containerStageConfig, workflowTemplate);
        return new Template().name(Constants.DAG_TEMPLATE_NAME).dag(dagFlow);
    }

    private Template createNotificationTemplate(
            String notificationType,
            String templateName,
            String sendTo,
            String mentionedUsers){
        return new Template()
                .name(templateName)
                .inputs(new Inputs()
                        .addParametersItem(new Parameter().name(Constants.NODE_NOTIFICATION_RECIPIENTS)
                                .value(sendTo))
                        .addParametersItem(new Parameter().name(Constants.NODE_NOTIFICATION_SUBJECT)
                                .value(Constants.VF_NOTIFICATION_SUBJECT))
                )
                .container(new Container()
                        .command(List.of("/bin/bash", "-c"))
                        .args(List.of(
                                String.format("java -jar /app/notification-service.jar" +
                                              " %s" +                                 // notification type
                                              " \"{{inputs.parameters.%s}}\"" +       // notification recipients
                                              " \"{{inputs.parameters.%s}}\"" +       // notification subject
                                              " \"%s\"" +                             // mentioned users
                                              " \"{{workflow.name}}\"" +              // workflow name
                                              " \"{{workflow.labels.%s}}\"" +         // workflow label
                                              " \"{{workflow.namespace}}\"" +         // workflow namespace
                                              " \"{{workflow.status}}\"" +            // workflow status
                                              " \"{{workflow.creationTimestamp}}\"" + // workflow creation timestamp
                                              " \"{{workflow.duration}}\"",           // workflow duration
                                        notificationType,
                                        Constants.NODE_NOTIFICATION_RECIPIENTS,
                                        Constants.NODE_NOTIFICATION_SUBJECT,
                                        mentionedUsers,
                                        Constants.PIPELINE_NAME_LABEL)))
                        .env(List.of(
                                new Env().name("VF_HOST").value(host),
                                new Env().name("SLACK_TOKEN").value(slackToken),
                                new Env().name("EMAIL_ADDRESS").value(mailSenderConfig.getUsername()),
                                new Env().name("EMAIL_PASS").value(mailSenderConfig.getPassword()),
                                new Env().name("SMTP_HOST").value(mailSenderConfig.getHost()),
                                new Env().name("SMTP_PORT").value(String.valueOf(mailSenderConfig.getPort())),
                                new Env().name("SMTP_STARTTLS").value(mailSenderConfig
                                        .getJavaMailProperties()
                                        .getProperty("mail.smtp.starttls.enable")),
                                new Env().name("SMTP_AUTH").value(mailSenderConfig
                                        .getJavaMailProperties()
                                        .getProperty("mail.smtp.auth"))))
                        .envFrom(List.of(new EnvFrom().secretRef(new SecretRef().name(ParamsDto.SECRET_NAME))))
                        .image(notificationImage)
                        .imagePullPolicy("Always")
                        .name(null)
                        .resources(new ResourceRequirementsBuilder()
                                .addToLimits(Map.of(Constants.CPU_FIELD,
                                        Quantity.parse("500m"),
                                        Constants.MEMORY_FIELD,
                                        Quantity.parse("500M")))
                                .addToRequests(Map.of(Constants.CPU_FIELD,
                                        Quantity.parse("100m"),
                                        Constants.MEMORY_FIELD,
                                        Quantity.parse("100M")))
                                .build()))
                .metadata(new TemplateMeta().labels(Map.of(
                        NODE_NAME, notificationType.toLowerCase() + "-notification",
                        NODE_OPERATION, NODE_OPERATION_NOTIFICATION)));
    }

    static List<DagTask> getDagTaskFromWorkflowTemplateSpec(WorkflowTemplateSpec workflowTemplateSpec) {
        List<Template> templates = workflowTemplateSpec.getTemplates();
        Template dagTemplate = templates
            .stream()
            .filter(template -> Constants.DAG_TEMPLATE_NAME.equals(template.getName()))
            .findAny()
            .orElseThrow(() -> new InternalProcessingException("Pipeline config is corrupted"));
        if (dagTemplate.getDag().getTasks() == null) {
            return Collections.emptyList();
        }
        return dagTemplate.getDag().getTasks();
    }

    /**
     * Adding flag is pipeline runnable.
     *
     * @param dagTasks        list of dag tasks
     * @param dto             dto
     * @param accessibleToRun is user have permission for run
     */
    private static void appendRunnable(
        Collection<DagTask> dagTasks, PipelineOverviewDto dto, boolean accessibleToRun) {
        dto.runnable(accessibleToRun && !dagTasks.isEmpty());
    }

    /**
     * Helper method to compose custom container template name based on different criteria
     *
     * @param withCustomCommand  if this is a template with custom command
     * @param mountProjectParams if this is a template with mounted project params
     * @return template name
     */
    private static String composeContainerTemplateName(boolean withCustomCommand, boolean mountProjectParams) {
        String name;
        if (withCustomCommand) {
            if (mountProjectParams) {
                name = CONTAINER_WITH_CMD_AND_PROJECT_PARAMS_TEMPLATE_NAME;
            } else {
                name = CONTAINER_WITH_CMD_TEMPLATE_NAME;
            }
        } else {
            if (mountProjectParams) {
                name = CONTAINER_TEMPLATE_WITH_PROJECT_PARAMS_NAME;
            } else {
                name = CONTAINER_TEMPLATE_NAME;
            }
        }
        return name;
    }

    /**
     * Helper method to compare available resources against ones required in pipeline
     *
     * @param availableCpu    available cpu in the project(namespace)
     * @param availableMemory available memory in the project(namespace)
     * @param neededCpu       needed cpu in cores
     * @param neededMemory    needed memory in bytes
     * @param stageType       type of pipeline stage
     * @param constraintType  limits or requests
     */
    private static void compareResourceSettings(
        BigDecimal availableCpu,
        BigDecimal availableMemory,
        BigDecimal neededCpu,
        BigDecimal neededMemory,
        String stageType,
        String constraintType) {
        if (neededCpu.compareTo(availableCpu) > 0) {
            throw new BadRequestException(String.format("Project doesn't have enough %s CPU to run %s - (cores) " +
                                                            "available(excluding resources for argo " +
                                                            "executor):%s; needed:%s",
                                                        constraintType,
                                                        stageType,
                                                        availableCpu,
                                                        neededCpu));
        }
        if (neededMemory.compareTo(availableMemory) > 0) {
            throw new BadRequestException(String.format("Project doesn't have enough %s RAM to run %s - (bytes) " +
                                                            "available(excluding resources for argo " +
                                                            "executor):%s; needed:%s",
                                                        constraintType,
                                                        stageType,
                                                        availableMemory,
                                                        neededMemory));
        }
    }

    /**
     * Adding parameters to dag tasks.
     *
     * @param workflowTemplate workflowTemplate
     * @param projectId        projectId
     */
    private void addParametersToDagTasks(WorkflowTemplate workflowTemplate, String projectId) {
        List<DagTask> tasks = getDagTaskFromWorkflowTemplateSpec(workflowTemplate.getSpec());
        for (DagTask dagTask : tasks) {
            Optional<ResourceRequirements> resourceRequirements = dagTask
                .getArguments()
                .getParameters()
                .stream()
                .filter(parameter -> K8sUtils.CONFIGMAP.equals(parameter.getName()))
                .findFirst()
                .map(p -> K8sUtils.getResourceRequirements(argoKubernetesService
                                                               .getConfigMap(projectId, p.getValue())
                                                               .getData()));

            switch (dagTask.getTemplate()) {
                case SPARK_TEMPLATE_NAME:
                    resourceRequirements.ifPresent(r -> {
                        Map<String, Quantity> requests = r.getRequests();
                        Map<String, Quantity> limits = r.getLimits();

                        dagTask
                            .getArguments()
                            .setParameters(dagTask
                                               .getArguments()
                                               .getParameters()
                                               .stream()
                                               .filter(parameter -> K8sUtils.CONFIGMAP.equals(parameter.getName()) ||
                                                   GRAPH_ID.equals(parameter.getName()) ||
                                                   PIPELINE_ID_LABEL.equals(parameter.getName()) ||
                                                   NODE_NAME.equals(parameter.getName()) ||
                                                   NODE_OPERATION.equals(parameter.getName()))
                                               .collect(Collectors.toList()));

                        dagTask
                            .getArguments()
                            .addParametersItem(new Parameter()
                                                   .name(LIMITS_CPU)
                                                   .value(limits.get(Constants.CPU_FIELD).toString()))
                            .addParametersItem(new Parameter()
                                                   .name(LIMITS_MEMORY)
                                                   .value(limits.get(Constants.MEMORY_FIELD).toString()))
                            .addParametersItem(new Parameter()
                                                   .name(REQUESTS_CPU)
                                                   .value(requests.get(Constants.CPU_FIELD).toString()))
                            .addParametersItem(new Parameter()
                                                   .name(REQUESTS_MEMORY)
                                                   .value(requests.get(Constants.MEMORY_FIELD).toString()));
                    });
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Creating template for spark-job.
     *
     * @return Template for spark-job
     */
    private Template createSparkTemplate(String namespace) {
        return new Template()
            .name(SPARK_TEMPLATE_NAME)
            .inputs(new Inputs()
                        .addParametersItem(new Parameter().name(K8sUtils.CONFIGMAP))
                        .addParametersItem(new Parameter().name(PIPELINE_ID_LABEL))
                        .addParametersItem(new Parameter().name(NODE_NAME))
                        .addParametersItem(new Parameter().name(NODE_OPERATION))
                        .addParametersItem(new Parameter().name(LIMITS_CPU))
                        .addParametersItem(new Parameter().name(LIMITS_MEMORY))
                        .addParametersItem(new Parameter().name(REQUESTS_CPU))
                        .addParametersItem(new Parameter().name(REQUESTS_MEMORY)))
            .podSpecPatch(String.format("{\"containers\": [{\"name\": \"main\", \"resources\": {\"limits\": " +
                                            "{\"cpu\": \"{{inputs.parameters.%s}}\", \"memory\": \"{{inputs" +
                                            ".parameters.%s}}\"}, \"requests\": {\"cpu\": \"{{inputs.parameters" +
                                            ".%s}}\", \"memory\": \"{{inputs.parameters.%s}}\"}}}]}",
                                        LIMITS_CPU,
                                        LIMITS_MEMORY,
                                        REQUESTS_CPU,
                                        REQUESTS_MEMORY))
            .container(new Container()
                           .name(K8sUtils.JOB_CONTAINER)
                           .image(sparkImage)
                           .command(List.of("/opt/spark/work-dir/entrypoint.sh"))
                           .imagePullPolicy("Always")
                           .volumeMounts(List.of(new VolumeMounts()
                                   .name(K8sUtils.PVC_VOLUME_NAME)
                                   .mountPath(pvcMountPath))
                           )
                           .env(List.of(new Env()
                                            .name("POD_IP")
                                            .valueFrom(new ValueFrom().name(new FieldRef()
                                                                                .fieldPath("status.podIP")
                                                                                .apiVersion("v1"))),
                                        new Env()
                                            .name("POD_NAME")
                                            .valueFrom(new ValueFrom().name(new FieldRef()
                                                                                .fieldPath("metadata.name")
                                                                                .apiVersion("v1"))),
                                        new Env()
                                            .name("PIPELINE_JOB_ID")
                                            .valueFrom(new ValueFrom().name(new FieldRef()
                                                                                .fieldPath("metadata.name")
                                                                                .apiVersion("v1"))),
                                        new Env().name("JOB_ID").value("{{inputs.parameters.configMap}}"),
                                        new Env().name("JOB_MASTER").value(jobMaster),
                                        new Env().name("JOB_IMAGE").value(sparkImage),
                                        new Env().name("IMAGE_PULL_SECRETS").value(imagePullSecret),
                                        new Env().name("POD_NAMESPACE").value(namespace),
                                        new Env().name("PVC_NAME").value(K8sUtils.PVC_NAME),
                                        new Env().name("MOUNT_PATH").value(pvcMountPath)))
                           .envFrom(List.of(new EnvFrom().configMapRef(new ConfigMapRef().name(
                                                "{{inputs.parameters" +
                                                    ".configMap}}")),
                                            new EnvFrom().secretRef(new SecretRef().name(ParamsDto.SECRET_NAME)))))
            .metadata(new TemplateMeta().labels(Map.of(Constants.JOB_ID_LABEL,
                                                       "{{inputs.parameters.configMap}}",
                                                        PIPELINE_ID_LABEL,
                                                       "{{inputs.parameters.pipelineId}}",
                                                        NODE_NAME,
                                                       "{{inputs.parameters.name}}",
                                                        NODE_OPERATION,
                                                       "{{inputs.parameters.operation}}")))
            .volumes(List.of(new Volumes()
                        .name(K8sUtils.PVC_VOLUME_NAME)
                        .persistentVolumeClaim(
                                new PersistentVolumeClaim()
                                        .claimName(K8sUtils.PVC_NAME)
                        ))
            );
    }

    /**
     * Creating template for custom container stage.
     *
     * @param withCustomCommand  whether container should include custom command
     * @param mountProjectParams whether container should mount project params into ENV
     * @return template
     */
    private Template createContainerTemplate(
        boolean withCustomCommand, boolean mountProjectParams) {
        Inputs inputs = new Inputs()
            .addParametersItem(new Parameter().name(LIMITS_CPU))
            .addParametersItem(new Parameter().name(LIMITS_MEMORY))
            .addParametersItem(new Parameter().name(REQUESTS_CPU))
            .addParametersItem(new Parameter().name(REQUESTS_MEMORY))
            .addParametersItem(new Parameter().name(IMAGE_LINK))
            .addParametersItem(new Parameter().name(IMAGE_PULL_POLICY))
            .addParametersItem(new Parameter().name(CONTAINER_NODE_ID))
            .addParametersItem(new Parameter().name(PIPELINE_ID_LABEL))
            .addParametersItem(new Parameter().name(GRAPH_ID))
            .addParametersItem(new Parameter().name(NODE_NAME))
            .addParametersItem(new Parameter().name(NODE_OPERATION));
        Container container = new Container()
            .image(String.format(INPUT_PARAMETER_PATTERN, IMAGE_LINK))
            .imagePullPolicy(String.format(INPUT_PARAMETER_PATTERN, IMAGE_PULL_POLICY));
        if (withCustomCommand) {
            inputs.addParametersItem(new Parameter().name(COMMAND));
            container.command(List.of("/bin/sh", "-c", "--", String.format(INPUT_PARAMETER_PATTERN, COMMAND)));
        }
        if (mountProjectParams) {
            container.envFrom(List.of(new EnvFrom().secretRef(new SecretRef().name(ParamsDto.SECRET_NAME))));
        }
        return new Template()
            .name(composeContainerTemplateName(withCustomCommand, mountProjectParams))
            .inputs(inputs)
            .podSpecPatch(String.format("{\"containers\": [{\"name\": \"main\", \"resources\": {\"limits\": " +
                                            "{\"cpu\": \"{{inputs.parameters.%s}}\", \"memory\": \"{{inputs" +
                                            ".parameters.%s}}\"}, \"requests\": {\"cpu\": \"{{inputs.parameters" +
                                            ".%s}}\", \"memory\": \"{{inputs.parameters.%s}}\"}}}]}",
                                        LIMITS_CPU,
                                        LIMITS_MEMORY,
                                        REQUESTS_CPU,
                                        REQUESTS_MEMORY))
            .container(container)
            .metadata(new TemplateMeta().labels(Map.of(Constants.CONTAINER_NODE_ID,
                                                       String.format(INPUT_PARAMETER_PATTERN,
                                                               Constants.CONTAINER_NODE_ID),
                                                       Constants.PIPELINE_ID_LABEL,
                                                       String.format(INPUT_PARAMETER_PATTERN,
                                                               Constants.PIPELINE_ID_LABEL),
                                                       NODE_NAME,
                                                       String.format(INPUT_PARAMETER_PATTERN,
                                                               Constants.NODE_NAME),
                                                       NODE_OPERATION,
                                                       String.format(INPUT_PARAMETER_PATTERN,
                                                               Constants.NODE_OPERATION))));

    }

    /**
     * Creating template for slack-job.
     *
     * @return Template for slack-job
     */
    private Template createSlackJobNotificationTemplate() {
        return new Template()
            .name(NOTIFICATION_TEMPLATE_NAME)
            .inputs(new Inputs()
                        .addParametersItem(new Parameter().name(Constants.NODE_NOTIFICATION_RECIPIENTS))
                        .addParametersItem(new Parameter().name(Constants.NODE_NOTIFICATION_MESSAGE))
                        .addParametersItem(new Parameter().name(NODE_NAME))
                        .addParametersItem(new Parameter().name(NODE_OPERATION)))
            .metadata(new TemplateMeta().labels(Map.of(NODE_NAME,
                        "{{inputs.parameters.name}}",
                        NODE_OPERATION,
                        "{{inputs.parameters.operation}}")))
            .container(new Container()
                           .image(notificationImage)
                           .command(List.of("/bin/bash", "-c", "--"))
                           .args(List.of(String.format(
                               "python3 /app/slack_job.py -m {{inputs.parameters.%s}} -a {{inputs.parameters.%s}}",
                               Constants.NODE_NOTIFICATION_MESSAGE,
                               Constants.NODE_NOTIFICATION_RECIPIENTS)))
                           .imagePullPolicy("Always")
                           .env(List.of(new Env().name("SLACK_API_TOKEN").value(slackToken)))
                           .envFrom(List.of(new EnvFrom().secretRef(new SecretRef().name(ParamsDto.SECRET_NAME))))
                           .resources(new ResourceRequirementsBuilder()
                                          .addToLimits(Map.of(Constants.CPU_FIELD,
                                                              Quantity.parse("500m"),
                                                              Constants.MEMORY_FIELD,
                                                              Quantity.parse("500M")))
                                          .addToRequests(Map.of(Constants.CPU_FIELD,
                                                                Quantity.parse("100m"),
                                                                Constants.MEMORY_FIELD,
                                                                Quantity.parse("100M")))
                                          .build()));
    }

    /**
     * Creating pipeline template.
     *
     * @return Template
     */
    private Template createPipelineTemplate() {
        return new Template()
                .name(PIPELINE_TEMPLATE_NAME)
                .inputs(new Inputs()
                        .addParametersItem(new Parameter().name(PIPELINE_ID))
                        .addParametersItem(new Parameter().name(NODE_NAME))
                        .addParametersItem(new Parameter().name(NODE_OPERATION)))
                .resource(new Resource()
                        .action(Constants.CREATE_ACTION)
                        .manifest(String.format("{\"apiVersion\": \"argoproj.io/v1alpha1\"," +
                                        "\"kind\": \"Workflow\",\"metadata\": {" +
                                        "\"generateName\": \"{{inputs.parameters.%s}}-\"," +
                                        "\"labels\": {\"workflows.argoproj.io/workflow-template\": " +
                                        "\"{{inputs.parameters.%s}}\"}},\"spec\": {" +
                                        "\"ttlStrategy\":" +
                                        "{\"secondsAfterCompletion\":%s," +
                                        "\"secondsAfterSuccess\":%s," +
                                        "\"secondsAfterFailure\":%s}," +
                                        "\"workflowTemplateRef\": {\"name\":\"{{inputs.parameters.%s}}\"}}}",
                                PIPELINE_ID,
                                PIPELINE_ID,
                                argoKubernetesService.getSecondsAfterCompletion(),
                                argoKubernetesService.getSecondsAfterSuccess(),
                                argoKubernetesService.getSecondsAfterFailure(),
                                PIPELINE_ID))
                        .successCondition("status.phase == Succeeded")
                        .failureCondition("status.phase in (Failed, Error)")
                )
                .metadata(new TemplateMeta().labels(Map.of(NODE_NAME,
                                                           "{{inputs.parameters.name}}",
                                                           NODE_OPERATION,
                                                           "{{inputs.parameters.operation}}")));
    }

    /**
     * Set spec to workflowTemplate.
     *
     * @param workflowTemplate workflow template
     * @param namespace        project's namespace in k8s
     * @param graphDto         graph with configuration for workflow template
     */
    void setSpec(WorkflowTemplate workflowTemplate, String namespace, GraphDto graphDto, PipelineParams params) {
        Map<String, ParamDto> projectParams = new HashMap<>(projectService
                                                                .getParams(namespace)
                                                                .getParams()
                                                                .stream()
                                                                .collect(Collectors.toMap(ParamDto::getKey,
                                                                                          Function.identity())));
        Map<GraphDto.NodeDto, ContainerStageConfig> containerStageConfig = graphDto
            .getNodes()
            .stream()
            .filter((GraphDto.NodeDto node) -> NODE_OPERATION_CONTAINER.equals(node
                                                                                   .getValue()
                                                                                   .get(NODE_OPERATION)))
            .collect(Collectors.toMap(Function.identity(), (GraphDto.NodeDto node) -> {
                ContainerStageConfig config =
                    ContainerStageConfig.fromContainerNode(node, projectParams, namespace, argoKubernetesService);
                if (ContainerStageConfig.ImagePullSecretType.NEW == config.getImagePullSecretType()) {
                    String secretName =
                        KubernetesService.getUniqueEntityName((String secName) -> argoKubernetesService.getSecret(
                            namespace,
                            secName));
                    config.prepareNewSecret(secretName, workflowTemplate.getMetadata().getName());
                    argoKubernetesService.createOrReplaceSecret(namespace, config.getSecret());
                }
                return config;
            }));
        Set<ImagePullSecret> pullSecrets = new HashSet<>();
        pullSecrets.add(new ImagePullSecret().name(imagePullSecret));
        containerStageConfig
            .entrySet()
            .stream()
            .filter((Map.Entry<GraphDto.NodeDto, ContainerStageConfig> p) -> p.getValue().getSecret() != null)
            .forEach((Map.Entry<GraphDto.NodeDto, ContainerStageConfig> p) -> pullSecrets.add(new ImagePullSecret().name(
                p.getValue().getSecret().getMetadata().getName())));

        List<Template> templates = new ArrayList<>();
        templates.add(createSlackJobNotificationTemplate());
        // Adding onExit template with the template names for notifications
        templates.add(createExitHandlerTemplate(params));
        // Adding notification templates into workflow template
        addNotificationTemplates(templates, params);
        templates.add(createSparkTemplate(namespace));
        templates.add(createPipelineTemplate());
        templates.add(createContainerTemplate(false, false));
        templates.add(createContainerTemplate(false, true));
        templates.add(createContainerTemplate(true, false));
        templates.add(createContainerTemplate(true, true));
        templates.add(createTemplateWithDag(graphDto, containerStageConfig, workflowTemplate));

        workflowTemplate.setSpec(new WorkflowTemplateSpec()
                                     .serviceAccountName(serviceAccount)
                                     .entrypoint(Constants.DAG_TEMPLATE_NAME)
                                     .onExit(Constants.EXIT_HANDLER)
                                     .imagePullSecrets(pullSecrets)
                                     .templates(templates)
                                     .pipelineParams(params)
        );
    }

    /**
     * Add notification templates to workflowTemplate according the conditions.
     *
     * @param templates        list of templates
     * @param pipelineParams   pipeline parameters
     */
    private void addNotificationTemplates(List<Template> templates, PipelineParams pipelineParams) {
        String emailRecipients = String.join(",", pipelineParams.getEmail().getRecipients());
        String slackChannels = String.join(",", pipelineParams.getSlack().getChannels());
        String slackRecipients = String.join(",", pipelineParams.getSlack().getRecipients());
        if(pipelineParams.getEmail().getSuccessNotify()){
            templates.add(createNotificationTemplate(
                            Constants.EMAIL_NOTIFICATION,
                            Constants.EMAIL_NOTIFY_SUCCESS,
                            emailRecipients,
                            "{{workflow.labels.startedBy}}"));
        }
        if(pipelineParams.getEmail().getFailureNotify()) {
            templates.add(createNotificationTemplate(
                            Constants.EMAIL_NOTIFICATION,
                            Constants.EMAIL_NOTIFY_FAILURE,
                            emailRecipients,
                            "{{workflow.labels.startedBy}}"));
        }
        if(pipelineParams.getSlack().getSuccessNotify()){
            templates.add(createNotificationTemplate(
                    Constants.SLACK_NOTIFICATION,
                    Constants.SLACK_NOTIFY_SUCCESS,
                    slackChannels,
                    slackRecipients));
        }
        if(pipelineParams.getSlack().getFailureNotify()) {
            templates.add(createNotificationTemplate(
                    Constants.SLACK_NOTIFICATION,
                    Constants.SLACK_NOTIFY_FAILURE,
                    slackChannels,
                    slackRecipients));
        }
    }

    /**
     * Set onExit notification handler to workflowTemplate.
     *
     * @param params pipeline parameters
     * @return template
     */
    private Template createExitHandlerTemplate(PipelineParams params) {
        return new Template()
                .name(Constants.EXIT_HANDLER)
                .inputs(new Inputs()
                        .addParametersItem(new Parameter().name(Constants.EMAIL_NOTIFY_SUCCESS)
                                .value(params.getEmail().getSuccessNotify() ? "true": "false"))
                        .addParametersItem(new Parameter().name(Constants.EMAIL_NOTIFY_FAILURE)
                                .value(params.getEmail().getFailureNotify() ? "true": "false"))
                        .addParametersItem(new Parameter().name(Constants.SLACK_NOTIFY_SUCCESS)
                                .value(params.getSlack().getSuccessNotify() ? "true": "false"))
                        .addParametersItem(new Parameter().name(Constants.SLACK_NOTIFY_FAILURE)
                                .value(params.getSlack().getFailureNotify() ? "true": "false"))
                )
                .steps(List.of(List.of(
                        new Step().name(Constants.EMAIL_NOTIFY_SUCCESS)
                                .template(Constants.EMAIL_NOTIFY_SUCCESS)
                                .when(String.format("{{workflow.status}} == Succeeded &&" +
                                        " {{inputs.parameters.%s}} == true", Constants.EMAIL_NOTIFY_SUCCESS)),
                        new Step().name(Constants.EMAIL_NOTIFY_FAILURE)
                                .template(Constants.EMAIL_NOTIFY_FAILURE)
                                .when(String.format("{{workflow.status}} != Succeeded &&" +
                                        " {{inputs.parameters.%s}} == true", Constants.EMAIL_NOTIFY_FAILURE)),
                        new Step().name(Constants.SLACK_NOTIFY_SUCCESS)
                                .template(Constants.SLACK_NOTIFY_SUCCESS)
                                .when(String.format("{{workflow.status}} == Succeeded &&" +
                                        " {{inputs.parameters.%s}} == true", Constants.SLACK_NOTIFY_SUCCESS)),
                        new Step().name(Constants.SLACK_NOTIFY_FAILURE)
                                .template(Constants.SLACK_NOTIFY_FAILURE)
                                .when(String.format("{{workflow.status}} != Succeeded &&" +
                                        " {{inputs.parameters.%s}} == true", Constants.SLACK_NOTIFY_FAILURE)))));
    }

    /**
     * Update the pipeline parameters with a predefined condition
     *
     * @param projectId projectId
     * @param targetPipelineId targetPipelineId
     * @param func function for updating pipeline ids
     */
    private void updatePipelineDependency(String projectId,
                                          String targetPipelineId,
                                          Function<PipelineParams,
                                          Set<String>> func) {
        WorkflowTemplate workflowTemplate =
                argoKubernetesService.getWorkflowTemplate(projectId, targetPipelineId);
        PipelineParams pipelineParams = workflowTemplate.getSpec().getPipelineParams();
        Set<String> nodePipelineIds = func.apply(pipelineParams);
        workflowTemplate.getSpec().getPipelineParams().setDependentPipelineIds(nodePipelineIds);
        argoKubernetesService.createOrReplaceWorkflowTemplate(projectId, workflowTemplate);
    }

    /**
     * Retrieve pipeline Ids from definition
     *
     * @param jsonNode jsonNode
     * @return Set<String> List of pipeline IDs without duplicates
     */
    private static Set<String> getPipelineIdsByDefinition(JsonNode jsonNode) {
        try {
            return GraphDto.parseGraph(Objects.requireNonNullElse(jsonNode,
                            new ObjectMapper().readTree("{\"graph\":[]}")))
                    .getNodes()
                    .stream()
                    .filter(e -> e.getValue().get(NODE_OPERATION).equals(NODE_OPERATION_PIPELINE))
                    .map(k -> k.getValue().get(NODE_PIPELINE_ID))
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (JsonProcessingException e) {
            LOGGER.error("An error occurred while parsing: {}", e.getLocalizedMessage());
            throw new BadRequestException("Required connection has incorrect structure");
        }
    }

    /**
     * Comparing pipelines to identify differences
     *
     * @param projectId     projectId
     * @param id            id
     * @param oldDefinition old definition for pipeline
     * @param newDefinition definition for pipeline with updates
     */
    public void checkPipelineDependencies(String projectId, String id,
                                          JsonNode oldDefinition,
                                          JsonNode newDefinition) {
        Set<String> oldIds = getPipelineIdsByDefinition(oldDefinition);
        Set<String> newIds = getPipelineIdsByDefinition(newDefinition);

        newIds.stream()
                .filter(element -> !oldIds.contains(element))
                .forEach((String targetPipelineId) -> {
                    updatePipelineDependency(projectId, targetPipelineId,
                            (PipelineParams pipelineParams) -> {
                                Set<String> pipelineIDs =
                                        Objects.requireNonNullElse(pipelineParams.getDependentPipelineIds(),
                                                new HashSet<>());
                                pipelineIDs.add(id);
                                LOGGER.info(
                                        "Pipeline dependency {} has been added to the pipeline {}",
                                        id,
                                        targetPipelineId);
                                return pipelineIDs;
                            });
                });

        oldIds.stream()
                .filter(element -> !newIds.contains(element))
                .forEach((String targetPipelineId) -> {
                    updatePipelineDependency(projectId, targetPipelineId,
                            ((PipelineParams pipelineParams) -> {
                                Set<String> pipelineIDs =
                                        Objects.requireNonNullElse(pipelineParams.getDependentPipelineIds(),
                                                new HashSet<>());
                                pipelineIDs = pipelineIDs.stream()
                                        .filter(pipelineId -> !pipelineId.equals(id))
                                        .collect(Collectors.toSet());
                                LOGGER.info(
                                        "Pipeline dependency {} has been removed from pipeline {}",
                                        id,
                                        targetPipelineId);
                                return pipelineIDs;
                            }));
                });
    }

    /**
     * Create and save pipeline.
     *
     * @param id         pipeline id
     * @param name       pipeline name
     * @param definition definition for pipeline
     * @return WorkflowTemplate
     */
    WorkflowTemplate createWorkflowTemplate(
        String projectId, String id, String name, JsonNode definition, PipelineParams params) {
        GraphDto graphDto = GraphDto.parseGraph(definition);
        GraphDto.validateGraphPipeline(graphDto, projectId, id, argoKubernetesService);

        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        setMeta(workflowTemplate, id, name, definition);
        setSpec(workflowTemplate, projectId, graphDto, params);

        return workflowTemplate;
    }

    /**
     * Check is pipeline name unique in project.
     *
     * @param projectId    projectId
     * @param pipelineId   pipeline id
     * @param pipelineName pipeline name
     */
    void checkPipelineName(String projectId, String pipelineId, String pipelineName) {
        List<WorkflowTemplate> workflowTemplatesByLabels =
            argoKubernetesService.getWorkflowTemplatesByLabels(projectId, Map.of(Constants.NAME, pipelineName));

        if (workflowTemplatesByLabels.size() > 1 ||
            (workflowTemplatesByLabels.size() == 1 &&
                !workflowTemplatesByLabels.get(0).getMetadata().getName().equals(pipelineId))) {
            throw new BadRequestException(String.format("Pipeline with name '%s' already exist in project %s",
                                                        pipelineName,
                                                        projectId));
        }
    }

    /**
     * Create or replace workflow template.
     *
     * @param projectId  project id
     * @param name       pipeline name
     * @param definition pipeline definition
     * @return pipeline id
     */
    public String create(String projectId, String name,
                         JsonNode definition, PipelineParams params) {
        checkPipelineName(projectId, null, name);
        String id =
                KubernetesService.getUniqueEntityName(
                        (String wfId) -> argoKubernetesService.getWorkflowTemplate(
                                projectId,
                                wfId));
        WorkflowTemplate workflowTemplate = createWorkflowTemplate(projectId, id, name, definition, params);
        addParametersToDagTasks(workflowTemplate, projectId);
        argoKubernetesService.createOrReplaceWorkflowTemplate(projectId, workflowTemplate);
        dependencyHandlerService.addDependencies(projectId, id,
                dependencyHandlerService.getNodesByDefinition(
                        dependencyHandlerService.getDefinition(workflowTemplate)));
        List<String> foundParams = ProjectService.findParamsKeysInString(definition.toString());
        if(!foundParams.isEmpty()) {
            List<ParamDto> existingParams = projectService.getParams(projectId).getParams();
            existingParams
                    .stream()
                    .filter(param -> foundParams.contains(param.getKey()))
                    .forEach(param -> param.getValue().getPipUsages().add(id));
            projectService.updateParamsWithoutProcessing(projectId, existingParams);
        }
        return id;
    }

    /**
     * Get pipeline data.
     *
     * @param projectId project id
     * @param id        workflow id
     * @return json graph
     */
    public PipelineResponseDto getById(String projectId, String id) {
        WorkflowTemplate workflowTemplate = argoKubernetesService.getWorkflowTemplate(projectId, id);
        ObjectMeta metadata = workflowTemplate.getMetadata();
        Map<String, String> annotations = metadata.getAnnotations();
        PipelineParams params = workflowTemplate.getSpec().getPipelineParams();
        boolean editable = isArgoResourceEditable(projectId, "workflowtemplates", Constants.UPDATE_ACTION);
        PipelineResponseDto pipelineResponseDto = ((PipelineResponseDto) new PipelineResponseDto()
                .id(id)
                .name(metadata.getLabels().get(Constants.NAME))
                .lastModified(annotations.get(Constants.LAST_MODIFIED))
                .status(K8sUtils.DRAFT_STATUS))
                .editable(editable)
                .params(params);
        if (argoKubernetesService.isCronWorkflowReadyOrExist(projectId, id)) {
            pipelineResponseDto.cron(true);
            pipelineResponseDto.cronSuspend(argoKubernetesService.getCronWorkflow(projectId, id)
                    .getSpec()
                    .isSuspend());
        } else {
            LOGGER.info("There is no cron: {}", id);
        }
        try {
            pipelineResponseDto.definition(MAPPER.readTree(Base64.decodeBase64(annotations.get(Constants.DEFINITION))));
        } catch (IOException e) {
            throw new InternalProcessingException("Unable to parse definition JSON", e);
        }
        appendRuntimeInfo(projectId, id, pipelineResponseDto, workflowTemplate);
        boolean accessibleToRun = isArgoResourceEditable(projectId, "workflows", Constants.CREATE_ACTION);
        appendRunnable(getDagTaskFromWorkflowTemplateSpec(workflowTemplate.getSpec()),
                pipelineResponseDto,
                accessibleToRun);

        return pipelineResponseDto;
    }

    /**
     * Get runtime data.
     *
     * @param projectId       project id
     * @param id              pipeline id
     * @param storedTemplates stored templates
     * @return runtime data for dto
     */
    private RuntimeData getRunTimeData(String projectId, String id, List<Template> storedTemplates) {
        RuntimeData runtimeData = new RuntimeData();
        Workflow workflow = argoKubernetesService.getWorkflow(projectId, id);
        List<Workflow> workflows = argoKubernetesService.getCronWorkflowsByLabel(projectId, id);
        workflows.add(workflow);
        Workflow lastStartedWorkflow = getLastStartedWorkflow(workflows);
        WorkflowStatus status = lastStartedWorkflow.getStatus();
        String currentStatus = status.getPhase();
        if (K8sUtils.FAILED_STATUS.equals(currentStatus)) {
            currentStatus = K8sUtils.ERROR_STATUS;
        }

        Map<String, String> statuses = new HashMap<>();
        Map<String, NodeStatus> nodes = status.getNodes();
        Collection<NodeStatus> nodeStatuses = new ArrayList<>();
        if (nodes != null) {
            nodeStatuses = nodes.values();
        } else {
            LOGGER.error(status.getMessage());
        }

        runtimeData.setStartedAt(DateTimeUtils.getFormattedDateTime(status.getStartedAt().toString()));
        runtimeData.setFinishedAt(DateTimeUtils.getFormattedDateTime(String.valueOf(status.getFinishedAt())));
        runtimeData.setStatus(currentStatus);
        runtimeData.setProgress(status.getProgress());
        runtimeData.setJobsStatuses(statuses);

        for (NodeStatus nodeStatus : nodeStatuses) {
            if (Constants.NODE_TYPE_POD.equals(nodeStatus.getType())) {
                String displayName = nodeStatus.getDisplayName();
                statuses.putAll(storedTemplates
                                    .stream()
                                    .filter((Template storedTemplate) -> Constants.DAG_TEMPLATE_NAME.equals(
                                        storedTemplate.getName()))
                                    .flatMap((Template storedTemplate) -> storedTemplate
                                        .getDag()
                                        .getTasks()
                                        .stream())
                                    .filter((DagTask dagTask) -> displayName.equals(dagTask.getName()))
                                    .flatMap((DagTask dagTask) -> dagTask.getArguments().getParameters().stream())
                                    .filter((Parameter parameter) -> parameter.getName().equals(GRAPH_ID))
                                    .collect(Collectors.toMap(Parameter::getValue,
                                                              (Parameter parameter) -> nodeStatus.getPhase())));
            }
        }
        WorkflowSpec spec = lastStartedWorkflow.getSpec();
        Map<Predicate<WorkflowSpec>, String> customStatusMap = Map.of((WorkflowSpec::isSuspend),
                                                                      K8sUtils.SUSPENDED_STATUS,
                                                                      ((WorkflowSpec s) -> K8sUtils.SHUTDOWN_TERMINATE.equals(
                                                                          s.getShutdown())),
                                                                      K8sUtils.TERMINATED_STATUS,
                                                                      ((WorkflowSpec s) -> K8sUtils.SHUTDOWN_STOP.equals(
                                                                          s.getShutdown())),
                                                                      K8sUtils.STOPPED_STATUS);
        if (lastStartedWorkflow.getSpec() != null) {
            Optional<Map.Entry<Predicate<WorkflowSpec>, String>> customStatus = customStatusMap
                .entrySet()
                .stream()
                .filter(predicateStringEntry -> predicateStringEntry.getKey().test(spec))
                .findFirst();
            customStatus.ifPresent((Map.Entry<Predicate<WorkflowSpec>, String> predicateStringEntry) -> runtimeData.setStatus(
                predicateStringEntry.getValue()));
        }
        return runtimeData;
    }

    /**
     * Append runtime info.
     *
     * @param projectId        project id
     * @param id               pipeline id
     * @param dto              dto
     * @param workflowTemplate workflow template
     */
    private void appendRuntimeInfo(
        String projectId, String id, PipelineOverviewDto dto, WorkflowTemplate workflowTemplate) {
        try {
            RuntimeData runtimeData = getRunTimeData(projectId, id, workflowTemplate.getSpec().getTemplates());
            dto
                .startedAt(runtimeData.getStartedAt())
                .finishedAt(runtimeData.getFinishedAt())
                .status(runtimeData.getStatus())
                .progress(runtimeData.getProgress())
                .jobsStatuses(runtimeData.getJobsStatuses());
        } catch (ResourceNotFoundException e) {
            LOGGER.info("Pipeline {} has not started yet", id);
        }
    }

    /**
     * Getting all pipelines in project.
     *
     * @param projectId project id
     * @return pipelines list
     */
    public PipelineOverviewListDto getAll(String projectId) {
        List<WorkflowTemplate> allWorkflowTemplates = argoKubernetesService.getAllWorkflowTemplates(projectId);
        boolean accessibleToRun = isArgoResourceEditable(projectId, "workflows", Constants.CREATE_ACTION);

        List<PipelineOverviewDto> pipelinesList = new ArrayList<>(allWorkflowTemplates.size());
        for (WorkflowTemplate workflowTemplate : allWorkflowTemplates) {
            ObjectMeta metadata = workflowTemplate.getMetadata();
            String id = metadata.getName();
            PipelineOverviewDto pipelineOverviewDto = new PipelineOverviewDto()
                .id(id)
                .name(metadata.getLabels().get(Constants.NAME))
                .tags(checkIfTagsExistsInTemplate(workflowTemplate.getSpec().getPipelineParams()))
                .dependentPipelineIds(checkIfDependsExistsInTemplate(workflowTemplate.getSpec().getPipelineParams()))
                .status(K8sUtils.DRAFT_STATUS)
                .lastModified(metadata.getAnnotations().get(Constants.LAST_MODIFIED));
            if (argoKubernetesService.isCronWorkflowReadyOrExist(projectId, id)) {
                pipelineOverviewDto.cron(true);
                pipelineOverviewDto.cronSuspend(argoKubernetesService.getCronWorkflow(projectId, id)
                        .getSpec()
                        .isSuspend());
            } else {
                LOGGER.info("There is no cron: {}", id);
            }
            appendRuntimeInfo(projectId, id, pipelineOverviewDto, workflowTemplate);
            appendRunnable(getDagTaskFromWorkflowTemplateSpec(workflowTemplate.getSpec()),
                           pipelineOverviewDto,
                           accessibleToRun);

            pipelinesList.add(pipelineOverviewDto);
        }

        return PipelineOverviewListDto
            .builder()
            .pipelines(pipelinesList)
            .editable(isArgoResourceEditable(projectId, "workflowtemplates", Constants.UPDATE_ACTION))
            .build();
    }

    private boolean isArgoResourceEditable(String projectId, String resource, String action) {
        return argoKubernetesService.isAccessible(projectId, resource, "argoproj.io", action);
    }

    /**
     * Updating pipeline.
     *
     * @param id         pipeline id
     * @param projectId  project id
     * @param definition new definition
     * @param name       name
     */
    public void update(final String projectId, final String id, final JsonNode definition,
                       final PipelineParams params, final String name) {
        checkPipelineName(projectId, id, name);
        try {
            dependencyHandlerService.updateDependenciesGraphPipeline(projectId, id, definition);
        } catch (ResourceNotFoundException e) {
            LOGGER.warn("Cannot find workflow template for {} pipeline", id);
            throw new BadRequestException(String.format("Pipeline with id %s doesn't exist", id), e);
        }
        try {
            argoKubernetesService.deleteWorkflow(projectId, id);
        } catch (ResourceNotFoundException e) {
            LOGGER.info("No workflows to remove");
        }
        argoKubernetesService.deleteSecretsByLabels(projectId,
                new HashMap<>(Map.of(Constants.PIPELINE_ID_LABEL, id, Constants.CONTAINER_STAGE, "true")));
        WorkflowTemplate newWorkflowTemplate = createWorkflowTemplate(projectId, id, name, definition, params);
        addParametersToDagTasks(newWorkflowTemplate, projectId);
        argoKubernetesService.createOrReplaceWorkflowTemplate(projectId, newWorkflowTemplate);

        List<String> foundParams = ProjectService.findParamsKeysInString(definition.toString());
        List<ParamDto> existingParams = projectService.getParams(projectId).getParams();
        for(ParamDto param : existingParams) {
            if(foundParams.contains(param.getKey())) {
                param.getValue().getPipUsages().add(id);
            } else {
                param.getValue().getPipUsages().remove(id);
            }
        }
        projectService.updateParamsWithoutProcessing(projectId, existingParams);
    }

    /**
     * Delete workflow template.
     *
     * @param projectId  project id
     * @param pipelineId pipeline id
     */
    public void delete(String projectId, String pipelineId) {
        WorkflowTemplate workflowTemplate = argoKubernetesService.getWorkflowTemplate(projectId, pipelineId);
        if (dependencyHandlerService.pipelineHasDepends(workflowTemplate)) {
            dependencyHandlerService.deleteDependencies(projectId, pipelineId,
                    dependencyHandlerService.getNodesByDefinition(
                            dependencyHandlerService.getDefinition(workflowTemplate)));
            argoKubernetesService.deleteWorkflowTemplate(projectId, pipelineId);
            argoKubernetesService.deleteWorkflow(projectId, pipelineId);
            argoKubernetesService.deleteSecretsByLabels(projectId,
                    new HashMap<>(Map.of(Constants.PIPELINE_ID_LABEL,
                            pipelineId,
                            Constants.CONTAINER_STAGE,
                            "true")));

            List<ParamDto> existingParams = projectService.getParams(projectId).getParams();
            existingParams.forEach(param -> param.getValue().getPipUsages().remove(pipelineId));
            projectService.updateParamsWithoutProcessing(projectId, existingParams);
        } else {
            throw new BadRequestException("The pipeline cannot be removed. There are dependencies.");
        }
    }

    /**
     * Method for recalculation all params usages in all pipelines.
     *
     * @param projectId is a project id.
     * @return true, if there were not any errors during the recalculation.
     */
    public boolean recalculateParamsPipelineUsages(final String projectId) {
        List<ParamDto> allParams = projectService.getParams(projectId).getParams();
        allParams.forEach(param -> param.getValue().getJobUsages().clear());
        List<PipelineOverviewDto> allPips = getAll(projectId).getPipelines();
        for(PipelineOverviewDto pipOverviewDto: allPips) {
            PipelineResponseDto pipDto = getById(projectId, pipOverviewDto.getId());
            List<String> foundParams = ProjectService.findParamsKeysInString(pipDto.getDefinition().toString());
            if (!foundParams.isEmpty()) {
                allParams
                        .stream()
                        .filter(param -> foundParams.contains(param.getKey()))
                        .forEach(param -> param.getValue().getPipUsages().add(pipOverviewDto.getId()));
            }
        }
        projectService.updateParamsWithoutProcessing(projectId, allParams);
        return true;
    }

    /**
     * Running pipeline.
     *
     * @param projectId project id
     * @param id        pipeline id
     */
    public void run(String projectId, String id) {
        WorkflowTemplate workflowTemplate = argoKubernetesService.getWorkflowTemplate(projectId, id);
        ResourceQuota quota = argoKubernetesService.getResourceQuota(projectId, Constants.QUOTA_NAME);
        validateResourceAvailability(quota, workflowTemplate);
        argoKubernetesService.createOrReplaceWorkflowTemplate(projectId, workflowTemplate);
        argoKubernetesService.deleteWorkflow(projectId, id);
        Workflow workflow = new Workflow();
        workflow.setMetadata(new ObjectMetaBuilder()
            .withName(id)
            .addToLabels(Constants.TYPE, Constants.TYPE_PIPELINE)
            .addToLabels(Constants.STARTED_BY, authenticationService.getUserInfo().getUsername())
            .addToLabels(Constants.PIPELINE_NAME_LABEL, workflowTemplate.getMetadata().getLabels().get("name"))
            .build()
        );
        workflow.setSpec(new WorkflowSpec().workflowTemplateRef(new WorkflowTemplateRef().name(id)));
        workflowService.trackWorkflowEvents(projectId, id);
        argoKubernetesService.createOrReplaceWorkflow(projectId, workflow);
    }

    /**
     * Suspend pipeline.
     *
     * @param projectId project id
     * @param id        pipeline id
     */
    public void suspend(String projectId, String id) {
        performArgoAction(projectId,
                          id,
                          ((RuntimeData data) -> List
                              .of(K8sUtils.PENDING_STATUS, K8sUtils.RUNNING_STATUS)
                              .contains(data.getStatus())),
                          "You cannot suspend pipeline that hasn't been run",
                          (String pId, String i) -> apiInstance.workflowServiceSuspendWorkflow(pId,
                                                                                               i,
                                                                                               new WorkflowSuspendRequest()));
    }

    /**
     * Resuming pipeline.
     *
     * @param projectId project id
     * @param id        pipeline id
     */
    public void resume(String projectId, String id) {
        performArgoAction(projectId,
                          id,
                          ((RuntimeData data) -> K8sUtils.SUSPENDED_STATUS.equals(data.getStatus())),
                          "You cannot resume pipeline that hasn't been suspended",
                          (String pId, String i) -> apiInstance.workflowServiceResumeWorkflow(pId,
                                                                                              i,
                                                                                              new WorkflowResumeRequest()));
    }

    /**
     * Create cron pipeline.
     *
     * @param projectId       project id
     * @param id              pipeline id
     * @param cronPipelineDto cron data
     */
    public void createCron(String projectId, String id, @Valid CronPipelineDto cronPipelineDto) {
        CronWorkflow cronWorkflow = new CronWorkflow();
        cronWorkflow.setMetadata(new ObjectMetaBuilder().withName(id).build());
        cronWorkflow.setSpec(CronWorkflowSpec.fromDtoAndWFTMPLName(cronPipelineDto, id));
        argoKubernetesService.createOrReplaceCronWorkflow(projectId, cronWorkflow);
    }

    /**
     * Delete cron pipeline.
     *
     * @param projectId project id
     * @param id        pipeline id
     */
    public void deleteCron(String projectId, String id) {
        argoKubernetesService.deleteCronWorkflow(projectId, id);
    }

    /**
     * Get cron pipeline data.
     *
     * @param projectId project id
     * @param id        workflow id
     * @return json graph
     */
    public CronPipelineDto getCronById(String projectId, String id) {
        CronWorkflow cronWorkflow = argoKubernetesService.getCronWorkflow(projectId, id);
        return CronPipelineDto.fromSpec(cronWorkflow.getSpec());
    }

    /**
     * Getting pipeline history
     *
     * @param projectId  project id
     * @param pipelineId pipeline id
     * @return List of pipeline history DTO
     */
    public List<PipelineHistoryResponseDto> getPipelineHistory(String projectId, String pipelineId) {
        Map<String, PipelineHistory> pipelineHistories = pipelineHistoryRepository.findAll(
                String.format("%s:%s_%s",PIPELINE_HISTORY, projectId, pipelineId)
        );

        return pipelineHistories
                .entrySet()
                .stream()
                .map(pipelineHistory -> PipelineHistoryResponseDto
                        .builder()
                        .id(pipelineHistory.getValue().getId())
                        .type(pipelineHistory.getValue().getType())
                        .status(pipelineHistory.getValue().getStatus())
                        .startedAt(pipelineHistory.getValue().getStartedAt())
                        .finishedAt(pipelineHistory.getValue().getFinishedAt())
                        .startedBy(pipelineHistory.getValue().getStartedBy())
                        .nodes(getPipelineNodesHistory(
                                        pipelineHistory.getValue().getNodes(),
                                        projectId,
                                        pipelineHistory.getKey()
                        ))
                        .build()
                )
                .collect(Collectors.toList());
    }

    /**
     * Getting pipeline nodes history for specific pipeline run
     *
     * @param nodes             pipeline child nodes
     * @param projectId         project id
     * @param pipelineHistoryId pipeline history id for specific pipeline run
     * @return List of pipeline nodes history DTO
     */
    private List<PipelineNodesHistoryResponseDto> getPipelineNodesHistory(
            List<String> nodes,
            String projectId,
            String pipelineHistoryId
    ) {
        List<PipelineNodesHistoryResponseDto> pipelineNodesHistoryResponse = new ArrayList<>();

        for (String node: nodes) {
            Map<String, PipelineNodeHistory> pipelineNodesHistory = pipelineHistoryRepository.findAll(
                    String.format("%s:%s_%s_%s", PIPELINE_NODE_HISTORY, projectId, node, pipelineHistoryId)
            );
            pipelineNodesHistory
                    .entrySet()
                    .stream()
                    .map(pipelineNodeHistory -> PipelineNodesHistoryResponseDto
                            .builder()
                            .id(pipelineNodeHistory.getValue().getId())
                            .name(pipelineNodeHistory.getValue().getName())
                            .operation(pipelineNodeHistory.getValue().getOperation())
                            .status(pipelineNodeHistory.getValue().getStatus())
                            .startedAt(pipelineNodeHistory.getValue().getStartedAt())
                            .finishedAt(pipelineNodeHistory.getValue().getFinishedAt())
                            .logId(pipelineNodeHistory.getKey())
                            .build()
                    )
                    .findFirst()
                    .ifPresent(pipelineNodesHistoryResponse::add);
        }
        return pipelineNodesHistoryResponse;
    }

    /**
     * Update cron pipeline.
     *
     * @param projectId       project id
     * @param id              pipeline id
     * @param cronPipelineDto cron data
     */
    public void updateCron(String projectId, String id, @Valid CronPipelineDto cronPipelineDto) {
        CronWorkflow cronWorkflow;
        try {
            cronWorkflow = argoKubernetesService.getCronWorkflow(projectId, id);
        } catch (ResourceNotFoundException e) {
            LOGGER.warn("Cannot find cron workflow for {} pipeline", id);
            throw new BadRequestException(String.format("Cron with id %s doesn't exist", id), e);
        }
        cronWorkflow.setSpec(CronWorkflowSpec.fromDtoAndWFTMPLName(cronPipelineDto, id));
        argoKubernetesService.createOrReplaceCronWorkflow(projectId, cronWorkflow);
    }

    /**
     * Terminate pipeline
     *
     * @param projectId project id
     * @param id        workflow id
     */
    public void terminate(String projectId, String id) {
        Workflow workflow = argoKubernetesService.getWorkflow(projectId, id);
        List<Workflow> workflows = argoKubernetesService.getCronWorkflowsByLabel(projectId, id);
        workflows.add(workflow);
        Workflow lastStartedWorkflow = getLastStartedWorkflow(workflows);
        String lId = lastStartedWorkflow.getMetadata().getName();
        performArgoAction(projectId,
                id,
                ((RuntimeData data) -> List
                        .of(K8sUtils.RUNNING_STATUS, K8sUtils.SUSPENDED_STATUS, K8sUtils.PENDING_STATUS)
                        .contains(data.getStatus())),
                "You cannot terminate pipeline that hasn't been started or suspended",
                (String pId, String i) -> apiInstance.workflowServiceTerminateWorkflow(pId,
                        lId,
                        new WorkflowTerminateRequest()));
    }

    /**
     * Stop pipeline
     *
     * @param projectId project id
     * @param id        workflow id
     */
    public void stop(String projectId, String id) {
        performArgoAction(projectId,
                          id,
                          ((RuntimeData data) -> List
                              .of(K8sUtils.RUNNING_STATUS, K8sUtils.SUSPENDED_STATUS, K8sUtils.PENDING_STATUS)
                              .contains(data.getStatus())),
                          "You cannot stop pipeline that hasn't been started " + "or suspended",
                          (String pId, String i) -> apiInstance.workflowServiceStopWorkflow(pId,
                                                                                            i,
                                                                                            new WorkflowStopRequest()));
    }

    /**
     * Retry pipeline which failed
     *
     * @param projectId project id
     * @param id        workflow id
     */
    public void retry(String projectId, String id) {
        performArgoAction(projectId,
                          id,
                          ((RuntimeData data) -> List
                              .of(K8sUtils.FAILED_STATUS, K8sUtils.ERROR_STATUS)
                              .contains(data.getStatus())),
                          "You cannot retry pipeline that hasn't failed",
                          (String pId, String i) -> apiInstance.workflowServiceRetryWorkflow(pId,
                                                                                             i,
                                                                                             new WorkflowRetryRequest()));
    }

    /**
     * Perform custom argo action on pipeline that has been executed
     *
     * @param projectId        project id
     * @param id               id
     * @param check            whether the action can be performed
     * @param onFailedCheckMsg custom message if action cannot be performed
     * @param action           custom argo action
     */
    private void performArgoAction(
        String projectId, String id, Predicate<RuntimeData> check, String onFailedCheckMsg, ArgoAction action) {
        WorkflowTemplate workflowTemplate = argoKubernetesService.getWorkflowTemplate(projectId, id);
        try {
            RuntimeData runtimeData = getRunTimeData(projectId, id, workflowTemplate.getSpec().getTemplates());
            if (!check.test(runtimeData)) {
                throw new BadRequestException(onFailedCheckMsg);
            }
        } catch (ResourceNotFoundException e) {
            throw new BadRequestException("Pipeline hasn't been run", e);
        }
        try {
            action.perform(projectId, id);
        } catch (ApiException e) {
            throw new ArgoClientException(e);
        }
    }

    /**
     * Validates resources requested by pipeline against what is available in the namespace(project)
     *
     * @param quota            resource quota in the namespace
     * @param workflowTemplate workflow template
     */
    private void validateResourceAvailability(
        ResourceQuota quota, WorkflowTemplate workflowTemplate) {
        BigDecimal executorLimitsCpu =
            Quantity.getAmountInBytes(Quantity.parse(argoKubernetesService.getArgoExecutorLimitsCpu()));
        BigDecimal executorLimitsMemory =
            Quantity.getAmountInBytes(Quantity.parse(argoKubernetesService.getArgoExecutorLimitsMemory()));
        BigDecimal executorRequestsCpu =
            Quantity.getAmountInBytes(Quantity.parse(argoKubernetesService.getArgoExecutorRequestsCpu()));
        BigDecimal executorRequestsMemory =
            Quantity.getAmountInBytes(Quantity.parse(argoKubernetesService.getArgoExecutorRequestsMemory()));
        BigDecimal limitsCpu = Quantity
            .getAmountInBytes(quota.getStatus().getHard().get(Constants.LIMITS_CPU))
            .subtract(executorLimitsCpu);
        BigDecimal limitsMemory = Quantity
            .getAmountInBytes(quota.getStatus().getHard().get(Constants.LIMITS_MEMORY))
            .subtract(executorLimitsMemory);
        BigDecimal requestsCpu = Quantity
            .getAmountInBytes(quota.getStatus().getHard().get(Constants.REQUESTS_CPU))
            .subtract(executorRequestsCpu);
        BigDecimal requestsMemory = Quantity
            .getAmountInBytes(quota.getStatus().getHard().get(Constants.REQUESTS_MEMORY))
            .subtract(executorRequestsMemory);
        workflowTemplate
            .getSpec()
            .getTemplates()
            .stream()
            .filter((Template temp) -> NOTIFICATION_TEMPLATE_NAME.equals(temp.getName()))
            .findAny()
            .ifPresent((Template notificationTemplate) -> {
                ResourceRequirements resources = notificationTemplate.getContainer().getResources();
                Map<String, Quantity> limits = resources.getLimits();
                Map<String, Quantity> requests = resources.getRequests();
                BigDecimal limMemory = Quantity.getAmountInBytes(limits.get(Constants.MEMORY_FIELD));
                BigDecimal limCpu = Quantity.getAmountInBytes(limits.get(Constants.CPU_FIELD));
                BigDecimal reqMemory = Quantity.getAmountInBytes(requests.get(Constants.MEMORY_FIELD));
                BigDecimal reqCpu = Quantity.getAmountInBytes(requests.get(Constants.CPU_FIELD));
                compareResourceSettings(limitsCpu,
                                        limitsMemory,
                                        limCpu,
                                        limMemory,
                                        "notification stage",
                                        "limits");
                compareResourceSettings(requestsCpu,
                                        requestsMemory,
                                        reqCpu,
                                        reqMemory,
                                        "notification stage",
                                        "request");
            });
        workflowTemplate
            .getSpec()
            .getTemplates()
            .stream()
            .map(Template::getDag)
            .filter(Objects::nonNull)
            .findAny()
            .map(DagTemplate::getTasks)
            .ifPresentOrElse((List<DagTask> dagTasks) -> dagTasks.forEach((DagTask dagTask) -> {
                if (NOTIFICATION_TEMPLATE_NAME.equals(dagTask.getTemplate())) {
                    return;
                }
                if (PIPELINE_TEMPLATE_NAME.equals(dagTask.getTemplate())) {
                    return;
                }
                Map<String, Parameter> parameters = dagTask
                    .getArguments()
                    .getParameters()
                    .stream()
                    .collect(Collectors.toMap(Parameter::getName, Function.identity()));
                BigDecimal stageLimitMemory =
                    Quantity.getAmountInBytes(Quantity.parse(parameters.get(LIMITS_MEMORY).getValue()));
                BigDecimal stageLimitCpu =
                    Quantity.getAmountInBytes(Quantity.parse(parameters.get(LIMITS_CPU).getValue()));
                BigDecimal stageRequestMemory =
                    Quantity.getAmountInBytes(Quantity.parse(parameters.get(REQUESTS_MEMORY).getValue()));
                BigDecimal stageRequestCpu =
                    Quantity.getAmountInBytes(Quantity.parse(parameters.get(REQUESTS_CPU).getValue()));
                compareResourceSettings(limitsCpu,
                                        limitsMemory,
                                        stageLimitCpu,
                                        stageLimitMemory,
                                        dagTask.getTemplate(),
                                        "limits");
                compareResourceSettings(requestsCpu,
                                        requestsMemory,
                                        stageRequestCpu,
                                        stageRequestMemory,
                                        dagTask.getTemplate(),
                                        "request");
            }), () -> {
                throw new BadRequestException(String.format("Dag template was not found for pipeline %s",
                                                            workflowTemplate.getMetadata().getName()));
            });
    }

    /**
     * Get last started workflow from the list of workflows
     *
     * @param  workflows   list of cron workflows
     * @return last started workflow
     */
    protected Workflow getLastStartedWorkflow(List<Workflow> workflows) {
        return workflows.stream().max(
                Comparator.comparing(wf -> ZonedDateTime.parse(
                        wf.getMetadata().getCreationTimestamp())
                )).orElseThrow();
    }

    /**
     * Getting tags if they exist in pipeline params.
     *
     * @param params pipeline params
     * @return List of tags or empty
     */
    public static List<String> checkIfTagsExistsInTemplate(PipelineParams params) {
        if (params != null && params.getTags() != null) {
            return params.getTags();
        }
        return Collections.emptyList();
    }

    /**
     * Getting dependentPipelineIds if they exist in pipeline params.
     *
     * @param params pipeline params
     * @return Set of dependentPipelineIds or empty
     */
    public static Set<String> checkIfDependsExistsInTemplate(PipelineParams params) {
        if (params != null && params.getDependentPipelineIds() != null) {
            return params.getDependentPipelineIds();
        }
        return Collections.emptySet();
    }

    private static String checkDependencyForWaitNode(
            List<GraphDto.NodeDto> waitNodes,
            String depends) {
        return waitNodes
                .stream()
                .filter(node -> node.getId().equals(depends))
                .findFirst()
                .map(GraphDto.NodeDto::getId)
                .orElse(null);
    }

    private static List<String> parseDependenciesAsList(String dependencies) {
        if(dependencies != null) {
            return Arrays.asList(dependencies.split(Pattern.quote(" && ")));
        }
        return Collections.emptyList();
    }

    /**
     * Helper interface for argo operations
     */
    @FunctionalInterface
    public interface ArgoAction {
        void perform(String projectId, String id) throws ApiException;
    }
}
