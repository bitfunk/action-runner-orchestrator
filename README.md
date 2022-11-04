[changelog]: CHANGELOG.md
[code of conduct]: CODE_OF_CONDUCT.md
[contributing]: CONTRIBUTING.md
[license]: LICENSE

[repository]: https://github.com/bitfunk/action-runner-orchestrator
[issues]: https://github.com/bitfunk/action-runner-orchestrator/issues

# GitHub Action Orchestrator

Kotlin script to manage GitHub Action Runners on self-hosted machines

Scripts have been developed against Kotlin 1.7.20

## Getting started

You need to create a GitHub personal access token with repo permissions and set it as environment variable in your
system: `export GITHUB_ACTIONS_RUNNER_REGISTRATION_TOKEN={token}`

## Usage

Create a `orchestrator_config.json` file following this example:

```json
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
```

Add the desired *organization* and *repository names* that you want to orchestrate.

Then just run the orchestrator: `./run-orchestrator.sh`

It will create a new folder `github-action-runners` where all repository runners could be found. Every runner will be configured as a service.

If you set the repository config enabled to false, the runner will be uninstalled and removed for that repository.

## Roadmap

This project is work in progress. We are working on adding more functionality, guidelines,
documentation and other improvements.

See the open [issues] for a list of proposed improvements and known issues.

## Changelog

All notable changes to this project will be documented in the [changelog].

## Versioning

We use [Semantic Versioning](http://semver.org/) as a guideline for our versioning.

## Contributing

You want to help or share a proposal? You have a specific problem? [Report a bug][issues] or [request a feature][issues].

You want to fix or change code? Read the [Code of Conduct] and [contributing guide][contributing].

## Copyright and license

Copyright (c) 2021-2022 Wolf-Martell Montwé.

Please refer to the [ISC License][license] for more information.

## Acknowledgements

- [Kotlin scripting examples using `kotlin-main-kts`](https://github.com/Kotlin/kotlin-script-examples)
- [Kotlin Shell](https://github.com/jakubriegel/kotlin-shell)
- [Kscript](https://github.com/holgerbrandl/kscript)
- Blog post [Ephemeral Self-Hosted Github Actions Runners](https://dev.to/wayofthepie/ephemeral-self-hosted-github-actions-runners-1h5m)
