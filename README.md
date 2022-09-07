# About Visual Flow

Visual Flow is an ETL tool designed for effective data manipulation via convenient and user-friendly interface. The tool has the following capabilities:

- Can integrate data from heterogeneous sources:
  - DB2
  - IBM COS
  - AWS S3
  - Elastic Search
  - PostgreSQL
  - MySQL/Maria
  - MSSQL
  - Oracle
  - Cassandra
  - Mongo
  - Redis
  - Redshift
- Leverage direct connectivity to enterprise applications as sources and targets
- Perform data processing and transformation
- Run custom code
- Leverage metadata for analysis and maintenance

Visual Flow application is divided into the following repositories:

- [Visual-Flow-frontend](https://github.com/ibagomel/Visual-Flow-frontend)
- _**Visual-Flow-backend**_ (current)
- [Visual-Flow-jobs](https://github.com/ibagomel/Visual-Flow-jobs)
- [Visual-Flow-deploy](https://github.com/ibagomel/Visual-Flow-deploy)

## Visual Flow Backend

Visual Flow backend is the REST API app, that serves as a middleware between frontend application and k8s-like orchestration environments, that run jobs with Spark.
It gives you ability to manage Visual Flow entities (projects, jobs, pipelines) in the following ways:

- Create/delete project which serves as a namespace for jobs and/or pipelines
- Manage project settings
- User access management
- Create/maintain a job
- Job execution and logs analysis
- Create/maintain a pipeline
- Pipeline execution
- Cron pipelines
- Import/Export jobs and pipelines
- Slack notifications

## Development

[Check the official guide](./DEVELOPMENT.md).

## Contribution

[Check the official guide](https://github.com/ibagomel/Visual-Flow/blob/main/CONTRIBUTING.md).

## License

Visual Flow is an open-source software licensed under the [Apache-2.0 license](./LICENSE).
