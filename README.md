[![CI](https://github.com/j3t/jenkins-pipeline-cache-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/j3t/jenkins-pipeline-cache-plugin/actions/workflows/ci.yml)

A cloud native file cache for Jenkins build pipelines which uses an S3-Bucket as storage provider.

# Configuration
* Go to `Manage Jenkins -> Configure System -> Cache Plugin`
* Update `Username` (aka S3-Access-Key), `Password` (aka S3-Secret-Key) and `Region`
* Optional: Update `Endpoint` in case you want to use an alternative storage provider (for example MinIO)

# How it works
The plugin restores a given folder before the cache step gets executed and creates a backup after the execution completed.

You have the following configuration parameters:
1. `folder` - path to the files you want to cache
1. `key` - name to identify the cache
1. `hashFiles` - hash selected files from the workspace to identify changes (optional but highly recommended)

The idea behind `hashFiles` is to have a hash over all files which have impact to the cache. For example, in a Maven project, this is usually the `pom.xml` and in a NPM project, the `package.json`. If a dependency has changed then a new cache gets stored. The restore on the other hand will also work, the `key` is used as fallback in this case and the latest `key` with the same prefix will be used instead. The example below will demonstrate that.

```
node {
  cache (folder: '$HOME/.m2/repository', key: 'my-project-maven', hashFiles: '**/pom.xml') {
    // TODO: build project
  }
}
```
will produce the following output
```
[Pipeline] Start of Pipeline
[Pipeline] node
Running on Jenkins in /var/jenkins_home/workspace/test
[Pipeline] {
[Pipeline] cache
Cache restored successfully
Cache restored from key: my-project-maven-9fc7ca5a922f2a37b84ec9dbc26a5168cee7e667
Cache Size: 14378219 B
...
Cache saved successfully
Cache saved with key: my-project-maven-3cd7a0db76ff9dca48979e24c39b408c
Cache Size: 14959104 B
[Pipeline] // cache
[Pipeline] }
[Pipeline] // node
[Pipeline] End of Pipeline
Finished: SUCCESS
```
In the example above, the `key` used to restore the folder is `my-project-maven-9fc7ca5a922f2a37b84ec9dbc26a5168cee7e667` while the `key` used to back up the folder is `my-project-maven-3cd7a0db76ff9dca48979e24c39b408c`. This is the case when no backup exists for a given `key` but another backup with same `key` is present. 

# Pitfalls
* The plugin creates a tar archive of the folder and uploads the file to S3 with the given `key`.
* The `hashFiles` option expects a `Glob-Pattern` to create a hash of selected workspace resources which is then appended to the `key`.
* For the restore, the `key` (without hash) is used as fallback when no backup exists. The plugin tries this `key` first and if it not exists then the latest `key` with the same prefix will be used. 