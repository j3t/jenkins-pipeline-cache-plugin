[![CI](https://github.com/j3t/jenkins-pipeline-cache-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/j3t/jenkins-pipeline-cache-plugin/actions/workflows/ci.yml)

A cloud native file cache for Jenkins pipelines. The files are stored in a S3-Bucket. The functionality is very similar to the one provided by [GitHub Actions](https://docs.github.com/en/actions/advanced-guides/caching-dependencies-to-speed-up-workflows).

# Installation
* Download the latest version (see [releases](https://github.com/j3t/jenkins-pipeline-cache-plugin/releases))
* Complete the installation via `Manage Jenkins -> Manage Plugins -> Advanced -> Upload Plugin`

# Configuration
* Go to `Manage Jenkins -> Configure System -> Cache Plugin`
* Set `Username` (aka S3-Access-Key)
* Set `Password` (aka S3-Secret-Key)
* Set `Region`
* Click `Test connection`

# Usage
The plugin restores an existing backup before the cache step gets executed and creates a backup after the execution completed.The restore step will be skipped, if no backup exists. The same is true for the backup step if the cache already exists.  

The cache step provides the following configuration parameters:
1. `path` - (required) path to the directory you want to cache (e.g. `/home/user/.m2/repository`)
2. `key` - (required) identifier used to store and restore a cache (e.g. `maven-1234`)
3. `restoreKeys` - (optional) additional keys which can be used to restore an existing file cache, can also be just a prefix (e.g. `['maven-', 'maven-0815']`)
4. `filter` - (optional) Ant style file mask like `**/*.json` which is applied to the `path` so that only specific files are cached.

There is also a method named `hashFiles` which can be very helpful when it comes to the key-creation. The idea is to create a hash from all files in the workspace which have impact to the cache. For a maven project, the pom files are usually responsible for that.

Below you can find an example pipeline. The pipeline checks out the [spring-petclinic](https://github.com/spring-projects/spring-petclinic) project and caches the local maven repository. 
```
node {
    git url: 'https://github.com/spring-projects/spring-petclinic', branch: "main"
    cache (path: "$HOME/.m2/repository", key: "petclinic-${hashFiles('**/pom.xml')}") {
        sh './mvnw package'
    }
}
```
The `path` property of the cache step points to the local maven repository and the `key` is a generated value based on project name and project poms.

When the pipeline is executed the first time then the output is as follows:
```
[Pipeline] Start of Pipeline
[Pipeline] node
Running on ...
[Pipeline] {
[Pipeline] git
...
[Pipeline] hashFiles
[Pipeline] cache
Cache not restored (no such key found)
[Pipeline] {
[Pipeline] sh
+ ./mvnw package
...
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  03:54 min
[INFO] Finished at: 2021-12-01T14:23:33Z
[INFO] ------------------------------------------------------------------------
[Pipeline] }
Cache saved successfully (petclinic-45cf29edec4fca8696560bc477667e4d)
135628288 bytes in 11.02 secs (12329844 bytes/sec)
[Pipeline] // cache
[Pipeline] }
[Pipeline] // node
[Pipeline] End of Pipeline
Finished: SUCCESS
```
The interesting part are the following lines:
1. `Cache not restored (no such key found)`
2. `Cache saved successfully (petclinic-45cf29edec4fca8696560bc477667e4d)`

The first line is printed out because there is no cache to restore and the second line when the actual cache gets stored.

If the job gets executed again, then the whole build will be much faster because the maven artifacts must not be downloaded again (in this case 30 seconds instead of 4 minutes). 
```
[Pipeline] Start of Pipeline
[Pipeline] node
Running on ...
[Pipeline] {
[Pipeline] git
...
[Pipeline] hashFiles
[Pipeline] cache
Cache restored successfully (petclinic-45cf29edec4fca8696560bc477667e4d)
135628288 bytes in 11.02 secs (12329844 bytes/sec)
[Pipeline] {
[Pipeline] sh
+ ./mvnw package
...
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  29.985 s
[INFO] Finished at: 2021-12-01T14:25:11Z
[INFO] ------------------------------------------------------------------------
[Pipeline] }
Cache not saved (petclinic-45cf29edec4fca8696560bc477667e4d already exists)
[Pipeline] // cache
[Pipeline] }
[Pipeline] // node
[Pipeline] End of Pipeline
Finished: SUCCESS
```
Now the two lines are different:
1. `Cache restored successfully (petclinic-45cf29edec4fca8696560bc477667e4d)`
2. `Cache not saved (petclinic-45cf29edec4fca8696560bc477667e4d already exists)`

The second run restores the cache from the first run by using the same key `petclinic-45cf29edec4fca8696560bc477667e4d` and since the key already exists, the backup step is skipped.

# Advanced Configuration
* Go to `Manage Jenkins -> Configure System -> Cache Plugin`
* Update `Endpoint` in case you want to use an alternative S3 compatible storage provider (e.g. MinIO)
* Set `Threshold` in megabyte in case you want to limit the total cache size. If the value is > 0 then every hour the system checks the threshold and removes the last recently used items from the cache until `total cache size < threshold` again (LRU).

# Pitfalls
* the `hashFiles` method expects a [Glob](https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob) relative to the workspace
* caches are not overridden
* caches are not stored when the inner-step has failed (e.g. unit-test failures)
* existing files are replaced but not removed
