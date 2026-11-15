# Publishing and crediting contributors on GitHub

[Русская версия](GITHUB_RELEASE.md)

1. Upload the project source and documentation to your repository. Keep test worlds, the E2E installation and third-party dependency JARs local.
2. Open **Releases → Draft a new release** and select or create `v1.1.0` at the release source commit. Use **Advanced Virtual Spawners 1.1.0** as the title.
3. Paste the bilingual [release notes](RELEASE_NOTES.md). Attach `advancedvirtualspawners-1.1.0.jar` and optionally the separate source archive. The runtime-check JAR is for development.
4. Save the draft, verify the attachments and publish when ready. [GitHub release documentation](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)

## Crediting assistance

- Credit **OpenAI Codex** in plain text in the README and release notes. Both languages already include a credit for ideas, implementation, testing and documentation. This credit needs no GitHub account.
- For a **human with a GitHub account**, describe their contribution and mention their real `@username` in the release description. GitHub adds mentioned users to the release's **Contributors** section. [GitHub release documentation](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)
- For someone who co-authored a specific commit, add `Co-authored-by: Name <email>` to that commit's message. GitHub links the attribution when the address belongs to their account. Use the plain-text assistance credit for Codex. [GitHub commit co-authorship documentation](https://docs.github.com/en/pull-requests/how-tos/commit-changes/creating-a-commit-with-multiple-authors)
