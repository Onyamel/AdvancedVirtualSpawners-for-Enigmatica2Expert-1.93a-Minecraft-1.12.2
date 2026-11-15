# Публикация на GitHub и указание вклада

[English](GITHUB_RELEASE.en.md)

1. Загрузите исходники и документацию проекта в свой репозиторий. Каталоги тестовых миров, файлы сборки E2E и JAR зависимостей оставьте локально.
2. Откройте **Releases → Draft a new release**. Выберите или создайте тег `v1.1.0` на коммите с исходниками релиза. Заголовок — **Advanced Virtual Spawners 1.1.0**.
3. Скопируйте двуязычный текст из [RELEASE_NOTES.md](RELEASE_NOTES.md). Добавьте `advancedvirtualspawners-1.1.0.jar` и, при желании, отдельный архив исходников. Тестовый JAR предназначен для разработчиков.
4. Сохраните черновик, проверьте вложения и затем опубликуйте его. [Документация GitHub о релизах](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)

## Как указать помощника

- Для **OpenAI Codex** оставьте текстовую строку о вкладе в README и описание релиза. Готовая формулировка уже добавлена на обоих языках. Для неё не требуется GitHub-аккаунт.
- Для **человека с GitHub-аккаунтом** напишите в описании релиза, чем он помог, и укажите его реальный `@username`. GitHub добавит упомянутого пользователя в раздел **Contributors**. [Документация GitHub о релизах](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)
- Если человек действительно соавтор конкретного коммита, укажите его в конце сообщения об этом коммите через `Co-authored-by: Имя <email>`. Чтобы GitHub связал авторство с профилем, адрес должен принадлежать этому аккаунту. Для Codex используйте текстовую отметку о помощи. [Документация GitHub о соавторстве](https://docs.github.com/en/pull-requests/how-tos/commit-changes/creating-a-commit-with-multiple-authors)
