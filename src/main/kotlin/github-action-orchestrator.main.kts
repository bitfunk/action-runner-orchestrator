#!/usr/bin/env kscript

@file:DependsOn("org.jetbrains.kotlin:kotlin-script-runtime:1.5.31")
@file:DependsOn("org.jetbrains.kotlin:kotlin-main-kts:1.5.31")
@file:DependsOn("eu.jrie.jetbrains:kotlin-shell-core:0.2.1")
@file:DependsOn("org.slf4j:slf4j-simple:1.7.28")
@file:DependsOn("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.31")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
@file:DependsOn("io.ktor:ktor-client-core-jvm:1.6.4")
@file:DependsOn("io.ktor:ktor-client-cio-jvm:1.6.4")
@file:DependsOn("io.ktor:ktor-client-logging-jvm:1.6.4")
@file:DependsOn("io.ktor:ktor-client-serialization-jvm:1.6.4")
@file:DependsOn("io.ktor:ktor-client-jackson:1.6.4")
@file:CompilerOptions("-Xopt-in=kotlin.RequiresOptIn")
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

import eu.jrie.jetbrains.kotlinshell.shell.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.system.exitProcess

// ### Data
val organization = "wmontwe"

val repositories = listOf<GitHubRepository>(
    // KMP - Template
    GitHubRepository(organization, "mobile-project-blueprint"),
)

val repositoriesToRemove = listOf<GitHubRepository>(
    // none
)

// Constants
val runnerVersion = "2.283.3"
val runnerFolder = "../../../github-action-runners"
val runnerUrl =
    "https://github.com/actions/runner/releases/download/v${runnerVersion}/actions-runner-osx-x64-${runnerVersion}.tar.gz"
val runnerFile = "actions-runner-osx-x64-${runnerVersion}.tar.gz"

// Script
@OptIn(ExperimentalCoroutinesApi::class)
shell {
    val accessToken = env("GITHUB_ACTIONS_RUNNER_REGISTRATION_TOKEN")
    if (accessToken.isEmpty()) {
        println("Please provide a GitHub personal access token with repo permission")
        println("and set it as environment var: GITHUB_ACTIONS_RUNNER_REGISTRATION_TOKEN")
        exitProcess(1)
    }

    val client = initHttpClient()

    mkdirp(runnerFolder)
    cd(runnerFolder)

    // Download runner
    if (fileExists(runnerFile).not()) {
        curl(runnerUrl)
    }

    for (repo in repositories) {
        println()
        println("------------>")
        println("Processing repository to configure:  ${repo.name}")
        println("------------")

        if (dirExists(repo.name).not()) {
            installRunner(client, repo, accessToken)
        } else {
            println("Runner already installed!")
        }

        println("<-----------")
    }

    for (repo in repositoriesToRemove) {
        println()
        println("------------>")
        println("Processing repository to delete:  ${repo.name}")
        println("------------")

        if (dirExists(repo.name)) {
            uninstallRunner(client, repo, accessToken)
        } else {
            println("Runner already uninstalled!")
        }

        println("<-----------")
    }

    cd(up)
    client.close()

    println("")
    println("Finished successfully")
}

// #### Helper
fun initHttpClient(): HttpClient {
    return HttpClient(CIO) {
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
    }
}

// #### Scripts
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.installRunner(client: HttpClient, repo: GitHubRepository, accessToken: String) {
    println("Installing runner")
    mkdirp(repo.name)
    println("Folder created")

    // copy runner
    copy(runnerFile, repo.name)
    cd(repo.name)
    untar(runnerFile)
    delete(runnerFile)
    println("Runner copied")

    // request token
    val repoToken = runnerToken(client, repo, accessToken)
    println("CurrentToken: $repoToken")

    // Configure
    runnerConfig(repo, repoToken)

    println()
    println("Install service and start")
    installService()
    startService()

    cd(up)
    println()
    println("Successfully installed runner!")
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.uninstallRunner(client: HttpClient, repo: GitHubRepository, accessToken: String) {
    println("Uninstalling runner")
    cd(repo.name)

    val repoToken = runnerToken(client, repo, accessToken)

    uninstallService()
    runnerConfigRemove(repoToken)

    cd(up)

    deleteDir(repo.name)
}

@OptIn(InternalAPI::class)
suspend fun runnerToken(client: HttpClient, repo: GitHubRepository, accessToken: String): String {
    val url = "https://api.github.com/repos/${repo.organization}/${repo.name}/actions/runners/registration-token"
    val response: TokenResponse = client.post(url) {
        headers {
            append(HttpHeaders.Authorization, "token $accessToken")
            append(HttpHeaders.Accept, "application/vnd.github.v3+json")
        }
    }
    return response.token
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.runnerConfig(repo: GitHubRepository, repoToken: String) {
    "./config.sh --url https://github.com/${repo.organization}/${repo.name} --token $repoToken --work _work"()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.runnerConfigRemove(repoToken: String) {
    "./config.sh remove --token $repoToken"()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.installService() {
    "./svc.sh install"()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.startService() {
    "./svc.sh start"()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.uninstallService() {
    "./svc.sh uninstall"()
}

// ### Shell
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.mkdirp(path: String) {
    "mkdir -p $path"()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.curl(url: String) {
    "curl -O -L $url"()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.untar(file: String) {
    "tar xzf ${file}"()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.copy(source: String, destination: String) {
    "cp $source $destination"()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.delete(file: String) {
    "rm $file"()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.deleteDir(name: String) {
    "rm -rf $name"()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.fileCreate(fileName: String) {
    "touch $fileName"()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.fileExists(fileName: String): Boolean {
    val result = StringBuilder().let {
        pipeline { "ls".process() pipe "grep $fileName".process() pipe it }
        it.toString()
    }
    return result.contains(fileName)
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.dirExists(dirName: String): Boolean {
    val result = StringBuilder().let {
        pipeline { "ls".process() pipe "grep $dirName".process() pipe it }
        it.toString()
    }
    return result.contains(dirName)
}


// #### Data
data class TokenResponse(
    val token: String,
    val expires_at: String
)

data class GitHubRepository(
    val organization: String,
    val name: String
)
