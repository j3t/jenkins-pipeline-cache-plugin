[![CI](https://github.com/j3t/jenkins-pipeline-cache-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/j3t/jenkins-pipeline-cache-plugin/actions/workflows/ci.yml)

A cloud native file cache for Jenkins pipelines. The files are stored in a S3-Bucket. The functionality is very similar to the one provided by [GitHub Actions](https://docs.github.com/en/actions/advanced-guides/caching-dependencies-to-speed-up-workflows).

# Motivation
The primary goal is to have a file cache for so called `hot agent nodes`. Those nodes are started on demand when an execution is scheduled by Jenkins and killed after the execution is finished (e.g. by using the [kubernetes-plugin](https://github.com/jenkinsci/kubernetes-plugin) or [nomad-plugin](https://github.com/jenkinsci/nomad-plugin)). This is fine but has also some drawbacks and some of them can be solved by having a file cache in place (e.g. to cache build dependencies or statistic data for code analysis or whatever data you want to be present for the next build execution).

# Installation
* Download the latest version (see [releases](https://github.com/j3t/jenkins-pipeline-cache-plugin/releases))
* Complete the installation via `Manage Jenkins -> Manage Plugins -> Advanced -> Upload Plugin`

# Configuration
* Go to `Manage Jenkins -> Configure System -> Cache Plugin`
* Set `Username` (aka S3-Access-Key)
* Set `Password` (aka S3-Secret-Key)
* Set `Bucket`
* Set `Region`
* Click `Test connection`

The plugin requires the following permissions in S3 for the bucket:
* s3:HeadObject
* s3:GetObject
* s3:ListBucket
* s3:PutObject
* s3:DeleteObject - Only if the CleanupTask is activated (threshold > 0)

# Usage
Below you can find an example where the local maven repository of the [spring-petclinic](https://github.com/spring-projects/spring-petclinic) project is cached.
```
node {
    git(url: 'https://github.com/spring-projects/spring-petclinic', branch: 'main')
    cache(path: "$HOME/.m2/repository", key: "petclinic-${hashFiles('**/pom.xml')}") {
        sh './mvnw package'
    }
}
```
The `path` parameter points to the local maven repository and the `key` parameter is the hash sum of all maven poms, prefixed by a dash and the project name.

The `hashFiles` method is optional but can be helpful to generate more precise keys. The idea is to collect all files which have impact to the cache and then create a hash sum from them (e.g. `hashFiles('**/pom.xml')` creates one hash sum over all maven poms in the workspace).

If the job gets executed, the plugin tries to restore the maven repository from the cache by using the given `key`. Then the inner-step gets executed and if this was successful and the cache doesn't exist yet then the `path` gets cached.

Below you can find a complete list of the `cache` step parameters:

| Name        | Required | Description                                                                                                                                                                                                                                         | Default                                                                                                    | Example                                                                                                                          |
|-------------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| path        | x        | Path to the directory which we want to be cached (absolute or relative to the workspace)                                                                                                                                                            |                                                                                                            | `$HOME/.m2/repository` - cache the local maven repository                                                                        |
| key         | x        | Identifier which is assigned to the cache.                                                                                                                                                                                                          |                                                                                                            | `maven-4f98f59e877ecb84ff75ef0fab45bac5`                                                                                         |
| restoreKeys |          | Additional keys which are used when the cache gets restored. The plugin tries to resolve them in the defined order (`key` first then the `restoreKeys`) and in case this was not successful then the latest key with the same prefix gets restored. |                                                                                                            | `['maven-', 'petclinic-']` - restore the latest cache where the key starts with `maven-` or `petclinic-` if the `key` not exists |
| filter      |          | Glob pattern applied to the `path` to filter the includes.                                                                                                                                                                                          | Includes all files except [default excludes](https://ant.apache.org/manual/dirtasks.html#defaultexcludes). | `**/*` - includes all files, `**/*.xml` - includes only XML files                                                                |

# Storage providers
Any S3 compatible storage provider should work. MinIO is supported first class, because all the integration tests are executed against MinIO.

In order to use an alternative provider, you probably have to change the `Endpoint` parameter.
* Go to `Manage Jenkins -> Configure System -> Cache Plugin`
* Update the `Endpoint` parameter
* Click `Test connection`

# Cleanup
You can define a threshold in megabyte if you want to limit the total cache size. If the value is > 0 then the plugin checks every hour the threshold and removes last recently used items from the cache as long as the total cache size is smaller than the threshold again (LRU).
* Go to `Manage Jenkins -> Configure System -> Cache Plugin`
* Update the `Threshold` parameter

# Disclaimer
Anyone which can create/execute build jobs has basically also access to all caches. The 'attacker' just needs a way to execute the plugin, and they need to know the key which is assigned to a particular cache. There is no list available where all the keys are listed but the build logs contain them. The plugin guarantees that the same key is not created twice and also that an existing key is not replaced, but it not guarantees that a restored cache was not manipulated by someone else which has access to the S3 bucket for example.

As a general advice, sensitive data or data which cannot be restored from somewhere else or not regenerated should not be stored in caches. It should also not a big deal, besides that the build takes longer, if a cache has been deleted (e.g. by accident, by the cleanup task, by a data crash or ...).

# Pitfalls
* the `hashFiles` step expects a [Glob](https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob) pattern relative to the workspace as parameter
* the `filter` parameter must be a [Glob](https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob) pattern relative to the `path`
* if the `filter` parameter is not empty then the [default excludes](https://ant.apache.org/manual/dirtasks.html#defaultexcludes) are disabled as well
* the cache gets not stored if the `key` already exists or the inner-step has been failed (e.g. unit-test failures)
* existing files are replaced but not removed when the cache gets restored
* the plugin creates a tar archive from the path and stores it as an S3 object
* the S3 object contains metadata
  * CREATED - Unix time is ms when the cache was created
  * LAST_ACCESS - Unix time is ms when the cache was accessed last

# Further reading
* [CacheStep.java](./src/main/java/io/jenkins/plugins/pipeline/cache/CacheStep.java) - implements the `cache` pipeline step
* [CacheStepTest](./src/test/java/io/jenkins/plugins/pipeline/cache/CacheStepTest.java) - checks that the cache pipeline step works as expected
* [HashFilesStep](./src/main/java/io/jenkins/plugins/pipeline/cache/HashFilesStep.java) - implements the `hashFiles` pipeline step