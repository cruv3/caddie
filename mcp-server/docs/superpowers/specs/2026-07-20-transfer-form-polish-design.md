# Transfer Form Polish Design

## Scope

Only redesign the `transfer_screen` section in the Study Bank app. The home screen, transaction list, review screen, banking behavior, and deterministic executor selectors remain unchanged.

## Direction

Use a restrained banking aesthetic: a compact title area, one white details card, persistent labels above each input, generous but efficient spacing, and a subtle security note before the primary action. Keep Sparkasse red as an accent rather than a large decorative surface.

## Structure

- Preserve `btn_back_form`, `transfer_recipient`, `transfer_iban`, `transfer_amount`, `purpose_text`, and `btn_send`.
- Group the four fields inside a white rounded card titled `Zahlungsdetails`.
- Add visible labels so field meaning remains clear after typing.
- Add a muted `Sicher über die Sparkasse Demo` note above the existing `Weiter` action.
- Keep every control visible on the Pixel 7 without requiring a scroll before continuing.

## Verification

- Add an instrumentation assertion for the new section title and security note.
- Run the complete Study Bank instrumentation suite.
- Install the debug APK and visually inspect the transfer screen on the connected Pixel.
