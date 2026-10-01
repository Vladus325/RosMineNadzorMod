# RosMineNadzor (RMN)

A bureaucratic overwatch for Minecraft 1.21.1 (NeoForge). Every Minecraft day it
prohibits something new — for everyone, and prohibitions accumulate. Violations
escalate: warnings → blocked controls (WASD/SPACE/SHIFT/LMB/RMB as actions) →
court with a growing chance of a real ban. A clean day brings clemency.
Banned letters are erased from the entire game.

**RosMineNadzor** — бюрократический надзор для Minecraft 1.21.1 (NeoForge).
Каждый игровой день запрещается что-то новое — на всех игроков, накопительно.
Эскалация нарушений: предупреждения → блокировка действий управления → суд
с растущим шансом бана. Чистый день даёт помилование. Запрещённые буквы
изгнаны из всей игры.

## Activate / Активация

- Item **Seal of RosMineNadzor** (right-click) / предмет **Печать РосМайнНадзора** (ПКМ)
- Commands / команды: `/rmn start`, `/rmn stop`, `/rmn ban <id>`, `/rmn reload`

## Configure / Настройка

- `config/rosminenadzor/config.json` — day length, per-type toggles, punishment ladder, court chances
- `config/rosminenadzor/custom_bans.json` — your own prohibitions (JSON constructor),
  see `custom_bans.example.json` / свои запреты — конструктором, образец рядом

## Build / Сборка

```bash
./gradlew build        # jar → build/libs/rosminenadzor-1.0.jar
```

Requires JDK 21. First build may need network access; afterwards `--offline` works.

## License

MIT
