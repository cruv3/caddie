# Task Cards — Study Tasks

## Task Overview (18 Total)

### Banking Tasks (2)
- `task_banking_transfer`: Transfer €30 to "Anna"
- `task_banking_balance`: Check current account balance

### Chat/Messaging Tasks (3)
- `task_chat_notes`: Send "Meeting at 3pm" in Notes app
- `task_chat_email`: Write subject "Lunch today?" to "max@example.com"
- `task_chat_messenger_send`: Send "See you tomorrow!" in Messenger

### Maps/Navigation Tasks (3)
- `task_maps_search`: Search for "Apfelstrudel" in Maps
- `task_maps_messenger_share`: Share café location via Messenger
- `task_maps_route`: Get route to "Hauptbahnhof"

### Music Tasks (2)
- `task_music_play`: Play playlist "Workout 2026"
- `task_music_playlist`: Pause music playback

### Email Tasks (2)
- `task_email_calendar`: Check calendar for today's events
- `task_email_reply`: Reply to "Re: Project deadline" with "OK, will deliver Friday"

### Gallery Tasks (3)
- `task_gallery_messenger_share`: Share last photo via Messenger
- `task_gallery_search`: Search gallery for "beach"
- `task_gallery_delete`: Delete last photo

### Rewe Shopping Tasks (3)
- `task_rewe_shopping_check`: Check shopping list in Rewe app
- `task_rewe_shopping_add`: Add "Milch" to shopping list
- `task_rewe_shopping_complete`: Mark "Brot" as completed

## Condition Mapping

| Condition | C1 Oversight | C2 Oversight | C3 Oversight |
|-----------|-------------|-------------|-------------|
| C1 | Confirm each step | N/A | N/A |
| C2 | Confirm all steps at once | Checkpoint before consequential | N/A |
| C3 | Stepwise confirmation | N/A | Voluntary intervention anytime |

## Criticality Levels

- **High** (3 tasks): Require explicit C1/C2 confirmation before execution
- **Medium** (6 tasks): Logged but no gate required
- **Low** (9 tasks): No oversight needed

## Task Ordering (Latin Square)

Each participant receives a unique task order based on their P01–P18
assignment from the counterbalancing matrix. See `matrix.py` for
generation logic.
