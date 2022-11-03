# GitHub Action Orchestrator

Kotlin Scripts to manage GitHub Action Runners on self-hosted machines

Scripts have been developed against Kotlin 1.5.31 and KScript 3.1.0.

## Setup

Ensure KScript is [installed](https://github.com/holgerbrandl/kscript#installation)

You need to create a GitHub personal access token with repo permissions and set it as environment variable in your system: `GITHUB_ACTIONS_RUNNER_REGISTRATION_TOKEN={token}`

## How to use and add a repository?

Edit the script and add the desired *organization* and *repository names* that you want to orchestrate.

Then just run the orchestrator: `./github-action-orchestrator.main.kts`

It will create a new folder `github-action-runners` where all repository runners could be found. Every mentioned runner will be configured as a service.

## Hot to remove a repository?

In case you want to remove one of the repository runners just move the entry to the `repositoriesToRemove` and execute the script. It will unregister the runner for that repo and delete all remaining files.

## ToDos

- configuration should be stored as file
- proper cli
- script accepts arguments to *add* and *remove* repositories

## Inspired by:

- [Kotlin scripting examples using `kotlin-main-kts`](https://github.com/Kotlin/kotlin-script-examples)
- [Kotlin Shell](https://github.com/jakubriegel/kotlin-shell)
- [Kscript](https://github.com/holgerbrandl/kscript)
- Blog post [Ephemeral Self-Hosted Github Actions Runners](https://dev.to/wayofthepie/ephemeral-self-hosted-github-actions-runners-1h5m)
