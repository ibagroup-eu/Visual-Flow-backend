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

package by.iba.vfapi.common;

import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.services.K8sUtils;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import java.util.HashMap;
import java.util.Map;

public class LoadFilePodBuilder {

    private LoadFilePodBuilder() {}

    /**
     * Initialize Pod for mounting to PVC.
     *
     * @param id              project id.
     * @param params          pod resource params.
     * @param pvcMountPath    pvc mount path.
     * @param imagePullSecret image pull secret name.
     * @return pod.
     */
    public static Pod getLoadFilePod(
            String id, Map<String, String> params, String pvcMountPath, String imagePullSecret) {
        return new PodBuilder()
                .withNewMetadata()
                .withName(K8sUtils.PVC_POD_NAME)
                .withNamespace(id)
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(K8sUtils.PVC_POD_NAME)
                .withResources(K8sUtils.getResourceRequirements(params))
                .withCommand(
                        "/bin/sh",
                        "-c",
                        "while true; " +
                                "do echo Running buffer container for uploading/downloading files; " +
                                "sleep 100;done"
                )
                .withImage(K8sUtils.PVC_POD_IMAGE)
                .withImagePullPolicy("IfNotPresent")
                .addNewVolumeMount()
                .withName(K8sUtils.PVC_VOLUME_NAME)
                .withMountPath(pvcMountPath)
                .endVolumeMount()
                .endContainer()
                .addNewVolume()
                .withName(K8sUtils.PVC_VOLUME_NAME)
                .withNewPersistentVolumeClaim()
                .withClaimName(K8sUtils.PVC_NAME)
                .endPersistentVolumeClaim()
                .endVolume()
                .addNewImagePullSecret(imagePullSecret)
                .withRestartPolicy("Always")
                .endSpec()
                .build();
    }

    /**
     * Get request/limits params for Pod to upload/download files.
     *
     * @return resource parameters.
     */
    public static Map<String, String> getBufferPVCPodParams() {
        Map<String, String> params = new HashMap<>();
        params.put(Constants.DRIVER_CORES, Constants.DRIVER_CORES_VALUE);
        params.put(Constants.DRIVER_MEMORY, Constants.DRIVER_MEMORY_VALUE);
        params.put(Constants.DRIVER_REQUEST_CORES, Constants.DRIVER_REQUEST_CORES_VALUE);
        return params;
    }
}
