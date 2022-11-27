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

package by.iba.vfapi.controllers;

import by.iba.vfapi.services.K8sUtils;
import by.iba.vfapi.services.KubernetesService;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.multipart.MultipartFile;

/**
 * File transfer controller class.
 */
@Slf4j
@Tag(name = "Files API", description = "Upload/Download files")
@RequiredArgsConstructor
@RestController
@RequestMapping("api/project")
public class FileTransferController {
    private final KubernetesService kubernetesService;
    private final AuthenticationService authenticationService;

    /**
     * Upload local file into cluster.
     *
     * @param projectID      project id
     * @param uploadFilePath upload file path
     * @param multipartFile  file for upload
     */
    @Operation(summary = "Upload local file",
               description = "Upload local file into container"
    )
    @PostMapping(value = "{projectID}/files/upload",
                 consumes = {"multipart/form-data"}
    )
    public void uploadFile(
            @RequestParam("projectID") String projectID,
            @RequestParam("uploadFilePath") String uploadFilePath,
            @RequestParam("fileToUpload") MultipartFile multipartFile
            ) {
        LOGGER.info(
                "{} - Uploading local file into cluster container",
                AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()));
        kubernetesService.uploadFile(
                projectID,
                uploadFilePath,
                K8sUtils.PVC_POD_NAME,
                multipartFile
        );
        ResponseEntity.status(HttpStatus.OK)
                .body("Successfully uploaded file " +
                        multipartFile.getOriginalFilename() + " into cluster container");
    }

    /**
     * Download file from cluster container to local.
     *
     * @param projectID        project name
     * @param fileName         file name
     * @param downloadFilePath download file path
     * @return byte array file
     */
    @Operation(summary = "Download file",
            description = "Download file from container to local"
    )
    @GetMapping("{projectID}/files/download")
    public ResponseEntity<byte[]> downloadFile(
            @RequestParam("projectID") String projectID,
            @RequestParam("fileName") String fileName,
            @RequestParam("downloadFilePath") String downloadFilePath) {
        LOGGER.info(
                "{} - Downloading file from cluster to local",
                AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()));
        byte[] file = kubernetesService.downloadFile(
                projectID,
                downloadFilePath,
                K8sUtils.PVC_POD_NAME
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + fileName)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .contentLength(file.length)
                .body(file);
    }
}