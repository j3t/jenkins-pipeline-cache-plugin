[![CI](https://github.com/j3t/jenkins-pipeline-cache-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/j3t/jenkins-pipeline-cache-plugin/actions/workflows/ci.yml)

A cloud native file cache for Jenkins build pipelines which uses an S3-Bucket as storage provider.

# Configuration
* Go to `Manage Jenkins -> Configure System -> Cache Plugin`
* Update `Username` (aka S3-Access-Key), `Password` (aka S3-Secret-Key) and `Region`
* Optional: Update `Endpoint` in case you want to use an alternative storage provider (for example MinIO)

# How it works
The plugin restores an existing backup before the cache step gets executed and creates a backup after the execution completed.The restore step will be skipped, if no backup exists. The same is true for the backup step if the cache already exists.  

You have the following configuration parameters:
1. `folder` - path to the files you want to cache
1. `key` - name to identify the cache in general
1. `hashFiles` - hash of selected files to identify changes

The idea behind `hashFiles` is to hash all files which have impact to the cache. For example, if you want to cache the Maven-Repository, then it makes sense to hash all poms. If the poms have not changed then the cache should be still the same. On the other hand, when the poms have changed then probably no backup exists. In this case the latest backup with the same `key` is restored instead.

```
node {
  cache (folder: '$HOME/.m2/repository', key: 'my-project-maven', hashFiles: '**/pom.xml') {
    // build project
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
Cache not restored (no such key found)
...
Cache saved successfully
Cache saved with key: my-project-maven-7b6b057c61d8150a0a939265b41c42de
Cache Size: 14959104 B
[Pipeline] // cache
[Pipeline] }
[Pipeline] // node
[Pipeline] End of Pipeline
Finished: SUCCESS
```
The cache has been saved, and the assigned `key` is `my-project-maven-7b6b057c61d8150a0a939265b41c42de`. The consists of the specified `key` and a suffix which is generated from the poms.

If you execute the job again, then the output is like
```
[Pipeline] Start of Pipeline
[Pipeline] node
Running on Jenkins in /var/jenkins_home/workspace/test
[Pipeline] {
[Pipeline] cache
Cache restored successfully
Cache restored from key: my-project-maven-7b6b057c61d8150a0a939265b41c42de
Cache Size: 14959104 B
...
Cache already exists (key: my-project-maven-7b6b057c61d8150a0a939265b41c42de), not saving cache.
[Pipeline] // cache
[Pipeline] }
[Pipeline] // node
[Pipeline] End of Pipeline
Finished: SUCCESS
```
The cache has been restored but since the poms are still the same, the backup is not necessary and skipped.

# Pitfalls
* `hashFiles` expects a `Glob` to filter workspace files

# Roadmap
* Limit the size of all backups
* Remove the oldest backups if the limit is reached
