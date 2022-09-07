# Set up development environment

## Requirements

1) Make sure that you have the following software installed

| Software       | Version                    | Purpose                                              |
|----------------|----------------------------|------------------------------------------------------|
| Java           | 11+                        | To work with this API as it has been written in Java |
| Maven          | 3+                         | To manage project dependencies                       |
| openssl        | any recent                 | To manage certificates for ssl                       |
| Kubernetes CLI | actual for target cluster  | To connect to Kubernetes                             |

2) Github account and access token with READ permissions/scope. It is needed to download one of the project's dependencies - _argo-client-java_. See pom.xml and settings.xml for more info

3) Following environment variables have to be set

| Name            | Explanation                                                              |
|-----------------|--------------------------------------------------------------------------|
| M2_HOME         | To be able to execute Maven commands within shell                        |
| BASE_PATH       | The sub path after hostname on which API will be available               |
| GITHUB_USERNAME | The username of your Github account that you've created access token for |
| GITHUB_TOKEN    | Github access token, needed to download java argo client dependency      |
| KEYSTORE_PASS   | Password for both p12 key store and a key within it                      |
| ARGO_SERVER_URL | URL to argo web server                                                   |
| SLACK_API_TOKEN | API token for Slack, needed to send out notifications                    |


## SSL configuration

Our API requires SSL certificate, and a private key packed up in p12 store.

1) Get a certificate, and a private key.
   If you want to roll with self-signed one(which is enough for development), you can use the command below:
   ```bash
   openssl req -x509 -out <PATH_TO_CRT_FILE> -keyout <PATH_TO_KEY_FILE> -newkey rsa:2048 -nodes -sha256 -days 365 -subj '/CN=localhost'
   ```

2) Put your certificate and a private key into p12 keystore:
   ```bash
   openssl pkcs12 -export -in <PATH_TO_CRT_FILE> -inkey <PATH_TO_KEY_FILE> -name <KEY_ALIAS> -out <PATH_TO_P12_FILE> -password pass:$KEYSTORE_PASS
   ```

_Note that in both commands we've used some placeholders. These are meant to be replaced with actual values for your system:_

* <PATH_TO_CRT_FILE> - path to a new .crt file
* <PATH_TO_KEY_FILE> - path to a new .key file
* <KEY_ALIAS> - alias of the key, meant to be referenced in server.ssl.key-alias within application.yaml
* <PATH_TO_P12_FILE> - path to a new .p12 key store file


## Kubernetes
Visual Flow app requires a Kubernetes cluster to run. You must have a kubernetes configuration file with the correct information to connect to an existing kubernetes cluster. You can install kubernetes cli and login to the cluster through it to generate a config file.
Make sure that user, which you are logged in, has permission to list all namespaces at least. But we recommend to have cluster-admin role, to allow backend create and delete projects(namespaces in kubernetes).


## Argo Workflows
[Argo Workflows](https://github.com/argoproj/argo-workflows) engine should be installed in target kubernetes cluster. Visual Flow app requires argo workflows web server to work with pipelines. You can deploy server to target kubernetes cluster or start it locally with argo workflows cli. You should put url to argo workflows web server to argo.serverUrl field in application.yaml file.
How to install and configure argo worklows: [Visual-Flow-deploy/README.md#argo-workflows](https://github.com/ibagomel/Visual-Flow-deploy#argo-workflows)


## Spark
Visual Flow app uses [spark job](https://github.com/ibagomel/Visual-Flow-jobs) to work with data. All required tools to run spark jobs located in spark job docker image. _No additional services are required._


## Application configuration
Now, when you are done with everything described above, it's time to create application.yaml
Take a look at [application.yaml.example](./src/main/resources/application.yaml.example). It will serve as a template to your future configuration.

1. Create a new application.yaml file inside src/main/resources/ and fill it with the contents from application.yaml.example.
2. Change and fill values within configuration. Existing comments can help to understand what is required.


## How to run and use

1. Login to Kubernetes server if not logged in yet.
2. Get the access token from the OAuth server (server's URL would be specified in application.yaml).
3. Build and run the application.
4. Make requests to API endpoints:
    - User info:
      ```bash
      curl --location --request GET 'https://localhost:8080/${BASE_PATH}/api/user' \
      --header 'Authorization: Bearer <OAUTH_TOKEN>'
      ```
    - Project list:
      ```bash
      curl --location --request GET 'https://localhost:8080/${BASE_PATH}/api/project' \
            --header 'Authorization: Bearer <OAUTH_TOKEN>'
      ```

*If you are using self-signed certificate, yon can see warning that server is not trusted. Just use your-client-based option to ignore that warning.
<OAUTH_TOKEN> is your access token that you've retrieved on the second step of this section.*


### Swagger
Visual Flow comes with preconfigured Swagger.

You should be able to access swagger by the following URL:

https://localhost:8080/${BASE_PATH}/swagger-ui.html

To be able to work with endpoints, you would have to be authenticated. Do it by providing oauth bearer token into Authorization header.
