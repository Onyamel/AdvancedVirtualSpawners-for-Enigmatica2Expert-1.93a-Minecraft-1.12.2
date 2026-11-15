# Advanced Virtual Spawners 1.1.0

## Русский

Forge-аддон для Minecraft 1.12.2 и Enigmatica 2: Expert 1.93a. Четыре тира виртуальных спавнеров, стаки Soul Vial до 64, три улучшения, семь рецептов, русская и английская локализации. Добыча складывается в выходной инвентарь, а ожидающие выдачи предметы сохраняются в мире.

Версия 1.1.0 включает исправления из 1.0.1. Отменённый дроп при демонтаже сохраняется для повторной выдачи, а отклонённый клик сохраняет прогресс. При `doMobLoot=false` энергия не расходуется. Очередь пропускает заблокированную добычу, а неизвестные условия Удачи обрабатывает Forge. Действуют лимиты расчёта, проверка данных души и синхронизация лимитов слотов.

Работа с AE2 Expert 3.0.0 проверена на выделенном сервере E2E. Быстрые шины передавали флаконы с точным NBT и возвращали добычу в ячейку AE2 Expert.

Для установки замените предыдущий JAR в `mods` на `advancedvirtualspawners-1.1.0.jar`. Сведения о работе и проверках находятся в `README.md` и `VALIDATION.md` репозитория. `AUDIT.md` сохраняет результаты аудита версии 1.0.0.

**Особая благодарность @Danzalex-Alex** за вклад в идею и реализацию проекта и за вдохновение, с которого всё началось.

## English

A Forge add-on for Minecraft 1.12.2 and Enigmatica 2: Expert 1.93a. It includes four virtual spawner tiers, Soul Vial stacks of up to 64, three upgrades, seven recipes, and Russian and English localizations. Loot goes to the output inventory, and pending items are persisted in the world.

Version 1.1.0 includes the fixes from 1.0.1. Cancelled item drops are saved and retried, and rejected clicks preserve progress. The machine pauses power use with `doMobLoot=false`. The queue skips blocked loot, and Forge handles unknown Luck conditions. Loot budgets, captured entity validation and server-synchronized slot limits remain in effect.

Compatibility with AE2 Expert 3.0.0 was verified on a dedicated E2E server. Expert buses transferred vials with exact NBT and returned the generated loot to an Expert cell.

Replace the previous JAR in `mods` with `advancedvirtualspawners-1.1.0.jar`. The repository files `README.en.md` and `VALIDATION.en.md` describe gameplay and test coverage. `AUDIT.en.md` preserves the 1.0.0 audit findings.

**Special thanks to @Danzalex-Alex** for shaping the original idea, contributing to its implementation, and inspiring the project.
