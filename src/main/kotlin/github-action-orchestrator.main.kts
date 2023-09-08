#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlin:kotlin-script-runtime:1.9.10")
@file:DependsOn("org.jetbrains.kotlin:kotlin-main-kts:1.9.10")
@file:DependsOn("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
@file:DependsOn("eu.jrie.jetbrains:kotlin-shell-core:0.2.1")
@file:DependsOn("io.ktor:ktor-client-core-jvm:2.3.4")
@file:DependsOn("io.ktor:ktor-client-cio-jvm:2.3.4")
@file:DependsOn("io.ktor:ktor-client-logging-jvm:2.3.4")
@file:DependsOn("io.ktor:ktor-client-content-negotiation-jvm:2.3.4")
@file:DependsOn("io.ktor:ktor-serialization-gson-jvm:2.3.4")
@file:OptIn(ExperimentalCoroutinesApi::class)

import com.google.gson.*
import eu.jrie.jetbrains.kotlinshell.shell.ExecutionMode
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

// Script
shell {
    val accessToken = env("GITHUB_ACTIONS_RUNNER_REGISTRATION_TOKEN")
    if (accessToken.isEmpty()) {
        println("Please provide a GitHub personal access token with repo permission")
        println("and set it as environment var: GITHUB_ACTIONS_RUNNER_REGISTRATION_TOKEN")
        exitProcess(1)
    }

    val orchestratorConfig: OrchestratorConfig = loadConfig()

    val client = initHttpClient()

    mkDir(orchestratorConfig.config.folder)
    cd(orchestratorConfig.config.folder)

    // Download runner
    val runnerFileName = prepareRunner(orchestratorConfig.config)

    for (repo in orchestratorConfig.repositories.filter { it.enabled }) {
        println()
        println("------------>")
        println("Processing repository to configure:  ${repo.name}")
        println("------------")

        if (dirExists(repo.name).not()) {
            installRunner(client, repo, accessToken, runnerFileName)
        } else {
            println("Runner already installed!")
        }

        println("<-----------")
    }

    for (repo in orchestratorConfig.repositories.filter { it.enabled.not() }) {
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

fun loadConfig(): OrchestratorConfig {
    val orchestratorConfigFile = File("orchestrator_config.json")
    if (orchestratorConfigFile.exists().not()) {
        println("Please provide an orchestrator_config.json")
        println("---Example---")
        println(
            """
            {
              "config": {
                "version": "2.298.2",
                "folder": "github-action-runners",
                "isArm": false
              },
              "repositories": [
                {
                  "organization": "ORG_NAME",
                  "name": "REPOSITORY_NAME",
                  "enabled": true
                }
              ]
            }
        """.trimIndent()
        )
        println("-------------")
        exitProcess(1)
    }
    val json = orchestratorConfigFile.readText()
    return Gson().fromJson(json, OrchestratorConfig::class.java)
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.prepareRunner(
    config: RunnerConfig
): String {
    val runnerFileName = if (config.isArm) {
        runnerFileArm(config.version)
    } else {
        runnerFileX64(config.version)
    }

    if (fileExists(runnerFileName).not()) {
        curl(runnerUrl(config.version, runnerFileName))
    }

    return runnerFileName
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.installRunner(
    client: HttpClient,
    repo: GitHubRepository,
    accessToken: String,
    runnerFileName: String,
) {
    println("Installing runner: ${repo.name}")
    mkDir(repo.name)
    println("Folder created")

    copy(runnerFileName, repo.name)
    cd(repo.name)
    untar(runnerFileName)
    delete(runnerFileName)
    println("Runner copied")

    // request token
    val repoToken = runnerToken(client, repo, accessToken)
    println("CurrentToken: $repoToken")

    // Configure
    "./config.sh --url https://github.com/${repo.organization}/${repo.name} --token $repoToken --work _work"()

    println()
    println("Install service and start")

    val run = "./run.sh".invoke(ExecutionMode.DETACHED)
    delay(1_000)
    run.kill()

    "./svc.sh install"()
    "./svc.sh start"()

    cd(up)
    println()
    println("Successfully installed runner: ${repo.name}!")
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.uninstallRunner(
    client: HttpClient,
    repo: GitHubRepository,
    accessToken: String
) {
    println("Uninstalling runner: ${repo.name}")
    cd(repo.name)

    val repoToken = runnerToken(client, repo, accessToken)

    "./svc.sh uninstall"()
    "./config.sh remove --token $repoToken"()

    cd(up)

    deleteDir(repo.name)
    println()
    println("Successfully uninstalled runner: ${repo.name}!")
}

fun runnerToken(
    client: HttpClient,
    repo: GitHubRepository,
    accessToken: String
): String {
    val url = "https://api.github.com/repos/${repo.organization}/${repo.name}/actions/runners/registration-token"
    val response: TokenResponse
    runBlocking {
        response = client.post(url) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
                append(HttpHeaders.Accept, "application/vnd.github+json")
            }
        }.body()
    }

    return response.token
}

// ### Shell
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.mkDir(path: String) {
    "mkdir -p $path"()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.curl(url: String) {
    "curl -O -L $url"()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Shell.untar(file: String) {
    "tar xzf $file"()
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

// ### Runner
fun runnerUrl(runnerVersion: String, runnerFile: String) =
    "https://github.com/actions/runner/releases/download/v$runnerVersion/$runnerFile"

fun runnerFileX64(runnerVersion: String) = "actions-runner-osx-x64-$runnerVersion.tar.gz"
fun runnerFileArm(runnerVersion: String) = "actions-runner-osx-arm64-$runnerVersion.tar.gz"

// #### Data
data class TokenResponse(
    val token: String,
    val expires_at: String
)

data class OrchestratorConfig(
    val config: RunnerConfig,
    val repositories: List<GitHubRepository>,
)

data class RunnerConfig(
    val version: String,
    val folder: String,
    val isArm: Boolean,
)

data class GitHubRepository(
    val organization: String,
    val name: String,
    val enabled: Boolean,
)
