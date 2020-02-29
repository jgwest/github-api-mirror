## Introduction

This project is an open source framework for writing large-scale GitHub analytics tools, bots, automation tasks, and more, while avoiding the API rate limits of both GitHub and GitHub Enterprise.

GitHub makes its data available through a well-documented REST API, in addition to making that data available through the web-based UI that most users are familiar with. However, the REST API has a strict rate limit that restricts the number of requests that can be made per second.

As of this writing, the [GitHub API rate limit](https://developer.github.com/v3/#rate-limiting) is about 83 requests per minute, or only 1.4 requests per second. For instance, if you wish to retrieve the GitHub Issue data for 500 issues, this would take ~6 minutes (and this also does not take into account additional, unpublished rate-limiting heuristics that are used that further limit requests in certain cases). In contrast, the same set of requests using the GitHub API Mirror would take less than a second. 

This project, GitHub API Mirror, actively requests and stores local copies of the Issue resource exposed via the GitHub REST API. These Issue resources are requested intelligently, ensuring the local copy is up-to-date, while always remaining within the standard GitHub API rate limit.

This project consists of two components:
- A server, **GitHubApiMirrorService**, hosted within a Docker image which runs on your local machine/network.
- A Java client, **GitHubApiMirrorClient**, which queries the *GitHubApiMirrorService* and returns the requested GitHub resources.

The **GitHubApiMirrorService** (GHAM service) indexes the GitHub server API and stores the cache locally, making it much faster to retrieve these resources without sending HTTP requests over the public internet. 

Unlike the GitHub server API, the GHAM service allows unlimited connections, which both reduces network latency and allows you to dramaticaly drive up the # of requests per second you can issue. You can now fully utilize your network bandwidth (or if hosted locally, fully saturate your HD I/O bandwidth).

Other applications:
- Allows you to run multiple independent GitHub bots/automation tasks through a single GitHub account 
- Daily backup of GitHub resources (eg run a daily backup job against the GitHub data volume, with restore left as an exercise to the reader)
- Supports both public GitHub and GitHub enterprise.


## Setup

First you will need to setup a server based on the GitHubApiServer Docker image, hosted on Docker Hub. Next, once running, the server will index the GitHub API resources that you specify in the settings file.

After the server has finished indexing, add the `GitHubApiMirrorClient` dependency to your Java project as a Maven dependency, and follow the example below to connect to the GitHubApiMirror server.





## Start the GitHub API Mirror server from Docker

#### 1) Pull the `jgwest/github-api-mirror` container image and clone this GitHub project

```
docker pull jgwest/github-api-mirror
git clone https://github.com/jgwest/github-api-mirror
cd github-api-mirror
```

#### 2) Edit the GitHub API mirror settings file

You will need to provide the server authentication credentials for GitHub, so that it can begin to mirror these resources.

Edit the `github-api-mirror/GitHubApiMirrorLiberty/github-settings.yaml`. Details for each field are available in the YAML file. 

Example:
```yaml

---
githubServer: github.com
githubUsername: (your-github-username)
githubPassword: (your-github-password-or-personal-access-token)

individualRepoList:
- "jgwest/github-api-mirror"

# (an arbitrary string required by clients to access the server), don't use this one:
presharedKey: "1GQ5tSo1MDdsA9NYoSrxHi0nG28d2BBi"

```

#### 3) Run the Docker image

Run the following shell script to create the data volumes and start the container image.
```
resources/docker/run.sh
```

Or run the following command:
```shell
docker run  -d  -p 9443:9443 --name github-api-mirror-container \
    -v github-api-mirror-data-volume:/home/default/data \
    -v (path to your github-settings.yaml):/config/github-settings.yaml \
    -v github-api-mirror-config-volume:/config \
    --restart always \
    --cap-drop=all \
    --tmpfs /opt/ol/wlp/output --tmpfs /logs \
    --read-only \
    jgwest/github-api-mirror
```

You can watch the application server start, and begin to index the API resources, using the `docker logs -f (container id)` command. Any configuration errors will appear here as well.


## Build the client API, add the client library Maven dependency, then connect to the mirror server

First, build the client itself:
```shell
git clone https://github.com/jgwest/github-api-mirror
cd github-api-mirror
mvn clean install -DskipTests
```

In order to connect to the GitHubApiMirrorService server from your application, you will need to add the Maven dependency to your app, then connect to your `GitHubApiMirrorService` server from the `GitHubApiMirrorClient`. 

```xml
<dependency>
	<groupId>github-api-mirror</groupId>
	<artifactId>GitHubApiMirrorClient</artifactId>
	<version>1.0.0</version>
</dependency>
```

This project assumes a familiarity with the [GitHub API](https://developer.github.com/v3/). The Issue resources exposed through the GitHub API are mirrored by the `GitHubApiMirrorService` server (hosted by Docker), and made available through the `GitHubApiMirrorClient` client library. 

For example, the following code snippet will connect to the `GitHubApiMirrorService` and print all of the issues for a specific GitHub repository.

```java
GitHub ghamClient = new GitHub(
    new GHConnectInfo("https://(hostname for GHAM server):9443/GitHubApiMirrorService/v1",
        "(preshared key defined in GHAM YAML config file)"));

// For each repository under the github.com/eclipse organization....
ghamClient.getOrganization("eclipse").getRepositories().forEach(repo -> {

  System.out.println("repository - " + repo.getFullName() + ":");

  // For each issue under the repository...
  repo.bulkListIssues().forEach(i -> {
    System.out.println(i.getNumber() + ": " + i.getTitle());
  });

});

```


## Docker image hardening

The `github-api-mirror` container image is fully hardened against compromise using standard Docker and Linux hardening measures. 

- **Security-hardened**
  - *Run as non-root user*: Container runs as non-root user, to mitigate Linux kernel exploits that seek to escape the container through Linux kernel bugs.
  - *Read-only filesystem*: Application is mounted as a read-only file system to prevent and mitigate container compromise.
  - *Drop all container capabilities*: All kernel capabilities are dropped, which closes another door for malicious attemps to escaping the container through potential compromise of the Linux kernel.
  
The GitHubAPIMirrorService is built on [OpenLiberty](https://openliberty.io), an open source enterprise-grade application server from IBM.

