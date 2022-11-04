#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlin:kotlin-script-runtime:1.7.20")
@file:DependsOn("org.jetbrains.kotlin:kotlin-main-kts:1.7.20")
@file:DependsOn("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
@file:DependsOn("eu.jrie.jetbrains:kotlin-shell-core:0.2.1")
@file:DependsOn("io.ktor:ktor-client-core-jvm:2.1.3")
@file:DependsOn("io.ktor:ktor-client-cio-jvm:2.1.3")
@file:DependsOn("io.ktor:ktor-client-logging-jvm:2.1.3")
@file:DependsOn("io.ktor:ktor-client-content-negotiation-jvm:2.1.3")
@file:DependsOn("io.ktor:ktor-serialization-gson-jvm:2.1.3")
@file:OptIn(ExperimentalCoroutinesApi::class)

import eu.jrie.jetbrains.kotlinshell.shell.Shell
import eu.jrie.jetbrains.kotlinshell.shell.shell
import eu.jrie.jetbrains.kotlinshell.shell.up
import kotlin.system.exitProcess
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.ExperimentalCoroutinesApi

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
val runnerVersion = "2.298.2"
val runnerFolder = "github-action-runners"
val runnerUrl =
    "https://github.com/actions/runner/releases/download/v${runnerVersion}/actions-runner-osx-x64-${runnerVersion}.tar.gz"
val runnerFileX64 = "actions-runner-osx-x64-${runnerVersion}.tar.gz"
val runnerFileArm = "actions-runner-osx-arm64-${runnerVersion}.tar.gz"

// Script
shell {
    val accessToken = env("GITHUB_ACTIONS_RUNNER_REGISTRATION_TOKEN")
    if (accessToken.isEmpty()) {
        println("Please provide a GitHub personal access token with repo permission")
        println("and set it as environment var: GITHUB_ACTIONS_RUNNER_REGISTRATION_TOKEN")
        exitProcess(1)
    }

    val client = initHttpClient()

    mkDir(runnerFolder)
    cd(runnerFolder)

    // Download runner
    if (fileExists(runnerFileX64).not()) {
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
            logger = Logger.SIMPLE
            level = LogLevel.INFO
        }
        install(ContentNegotiation) {
            gson()
        }
    }
}

// #### Scripts
suspend fun Shell.installRunner(
    client: HttpClient,
    repo: GitHubRepository,
    accessToken: String
) {
    println("Installing runner")
    mkDir(repo.name)
    println("Folder created")

    // copy runner
    copy(runnerFileX64, repo.name)
    cd(repo.name)
    untar(runnerFileX64)
    delete(runnerFileX64)
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

suspend fun Shell.uninstallRunner(
    client: HttpClient,
    repo: GitHubRepository,
    accessToken: String
) {
    println("Uninstalling runner")
    cd(repo.name)

    val repoToken = runnerToken(client, repo, accessToken)

    uninstallService()
    runnerConfigRemove(repoToken)

    cd(up)

    deleteDir(repo.name)
}

suspend fun runnerToken(
    client: HttpClient,
    repo: GitHubRepository,
    accessToken: String
): String {
    val url = "https://api.github.com/repos/${repo.organization}/${repo.name}/actions/runners/registration-token"
    val response: TokenResponse = client.post(url) {
        headers {
            append(HttpHeaders.Authorization, "Bearer $accessToken")
            append(HttpHeaders.Accept, "application/vnd.github+json")
        }
    }.body()

    return response.token
}

// ### Runner
suspend fun Shell.runnerConfig(repo: GitHubRepository, repoToken: String) {
    "./config.sh --url https://github.com/${repo.organization}/${repo.name} --token $repoToken --work _work"()
}

suspend fun Shell.runnerConfigRemove(repoToken: String) {
    "./config.sh remove --token $repoToken"()
}

suspend fun Shell.installService() {
    "./svc.sh install"()
}

suspend fun Shell.startService() {
    "./svc.sh start"()
}

suspend fun Shell.uninstallService() {
    "./svc.sh uninstall"()
}

// ### Shell
suspend fun Shell.mkDir(path: String) {
    "mkdir -p $path"()
}

suspend fun Shell.curl(url: String) {
    "curl -O -L $url"()
}

suspend fun Shell.untar(file: String) {
    "tar xzf $file"()
}

suspend fun Shell.copy(source: String, destination: String) {
    "cp $source $destination"()
}

suspend fun Shell.delete(file: String) {
    "rm $file"()
}

suspend fun Shell.deleteDir(name: String) {
    "rm -rf $name"()
}

suspend fun Shell.fileExists(fileName: String): Boolean {
    val result = StringBuilder().let {
        pipeline { "ls".process() pipe "grep $fileName".process() pipe it }
        it.toString()
    }
    return result.contains(fileName)
}

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
