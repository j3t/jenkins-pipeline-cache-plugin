[![CI](https://github.com/j3t/jenkins-pipeline-cache-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/j3t/jenkins-pipeline-cache-plugin/actions/workflows/ci.yml)

A cloud native file cache for Jenkins build pipelines which uses an S3-Bucket as storage provider.

# Configuration
* Go to `Manage Jenkins -> Configure System -> Cache Plugin`
* Update `Username` (aka S3-Access-Key), `Password` (aka S3-Secret-Key) and `Region`
* Optional: Update `Endpoint` in case you want to use an alternative storage provider (for example MinIO)

# How it works
The plugin restores a given folder before the cache step gets executed and creates a backup after the execution completed.

You have the following configuration parameters:
1. folder - path to the folder you want to cache
1. key - identifier assigned to the backup
1. hashFiles (optional) - a glob pattern which is used to hash files in the workspace and then used as identifier

For example
```
node {
  cache folder: '$HOME/.m2/repository', key: 'my-project-maven', hashFiles: '**/pom.xml' {
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
In the example above, the key used to restore the folder was `bla-foo-9fc7ca5a922f2a37b84ec9dbc26a5168cee7e667` while the key used to back up the folder was `bla-foo-3cd7a0db76ff9dca48979e24c39b408c`. This is the case when no backup exists yet but another backup with same key is present. 

# TODO
* upload same key not again
