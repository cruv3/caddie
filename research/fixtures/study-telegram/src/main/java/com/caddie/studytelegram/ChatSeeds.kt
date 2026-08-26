package com.caddie.studytelegram

import java.util.Collections
import java.util.Locale

enum class ChatId { ANNA, LENA, ANNE, ANNI, PROJECT, MILA, JONAS }

data class ChatSummary(val id: ChatId, val name: String, val preview: String, val avatar: String)

data class ChatMessage(val text: String, val incoming: Boolean, val dateLabel: String, val time: String)

object ChatSeeds {
    private data class Turn(val text: String, val incoming: Boolean)

    private fun out(text: String) = Turn(text, incoming = false)

    private fun `in`(text: String) = Turn(text, incoming = true)

    private fun conversation(date: String, startHour: Int, vararg turns: Turn): List<ChatMessage> =
        turns.mapIndexed { index, turn ->
            ChatMessage(
                text = turn.text,
                incoming = turn.incoming,
                dateLabel = date,
                time = "%02d:%02d".format(Locale.ROOT, startHour + index / 6, (index % 6) * 7),
            )
        }

    private fun datedAlternatingHistory(
        texts: List<String>,
        firstDate: String,
        secondDate: String,
        incomingFirst: Boolean,
    ): List<ChatMessage> {
        val midpoint = texts.size / 2
        return texts.mapIndexed { index, text ->
            ChatMessage(
                text = text,
                incoming = if (index % 2 == 0) incomingFirst else !incomingFirst,
                dateLabel = if (index < midpoint) firstDate else secondDate,
                time = "%02d:%02d".format(Locale.ROOT, 10 + index / 6, (index % 6) * 7),
            )
        }
    }

    private val histories: Map<ChatId, List<ChatMessage>> = mapOf(
        ChatId.LENA to (
            datedAlternatingHistory(
                listOf(
                    "bist du schon wach?", "leider ja 😅", "seminar um zehn?", "genau, raum 3.12",
                    "ich dachte 2.08", "wurde gestern geändert", "gut dass ich frage",
                    "ich bring dir auch den adapter mit", "heldin des tages", "übertreib nicht haha",
                    "kaffee vorher?", "unten bei der bib?", "ja zehn vor", "passt",
                    "bin zwei minuten später", "ich bestell schon mal", "wie fandest du das seminar?",
                    "ehrlich gesagt bisschen zäh", "die gruppenaufgabe war okay",
                    "ja unsere idee war wenigstens brauchbar", "hast du das protokoll?", "liegt im drive",
                    "sehe es, danke", "gehst du noch in die mensa?", "nur kurz, hab mega hunger",
                    "ich komme mit", "heute gibt es wohl curry", "hoffentlich nicht wieder so scharf",
                    "du fandest letztes mal alles scharf 😄", "frech", "samstag eigentlich noch flohmarkt?",
                    "ja wenn es nicht regnet",
                ),
                firstDate = "Dienstag",
                secondDate = "Donnerstag",
                incomingFirst = true,
            ) + conversation(
                "Heute", 9,
                out("bin gleich aus dem seminar raus"), `in`("perfekt, ich sitz noch in der bib"),
                out("hast du die folien schon?"), `in`("ja schick ich dir gleich"), out("danke 🙏"),
                `in`("und du schuldest mir noch deinen musik-tipp"), out("stimmt haha"),
                `in`("ich brauch was für den heimweg"), out("eher ruhig oder bisschen pop?"),
                `in`("pop, aber nichts komplett nerviges 😄"), out("okay dann hab ich was"),
                `in`("schieß los"), out("Song-Tipp: As It Was von Harry Styles."),
                `in`("kenn ich glaube ich nur vom hören"), out("passt perfekt für zugfahrt"),
                `in`("nice, hör ich später rein"),
            )
        ),
        ChatId.ANNA to (
            datedAlternatingHistory(
                listOf(
                    "hast du den entwurf gesehen?", "ja gerade geöffnet", "die einleitung ist schon gut",
                    "beim zweiten absatz bin ich unsicher", "ich markiere dir die stelle", "perfekt danke",
                    "brauchst du die quelle von gestern?", "ja bitte", "schicke ich dir nach dem call",
                    "kein stress", "wann ist dein termin?", "halb zwölf", "dann viel erfolg",
                    "danke, kann ich brauchen", "meld dich danach", "mach ich", "und wie lief es?",
                    "besser als gedacht", "sehr gut!", "ich war komplett nervös",
                    "hat man bestimmt nicht gemerkt", "hoffe ich 😅", "bist du morgen in der bib?",
                    "ab ungefähr zehn", "ich wahrscheinlich auch", "sollen wir den rest zusammen machen?",
                    "ja, dann sind wir schneller", "ich reserviere einen tisch", "am fenster wenn möglich",
                    "natürlich", "bringst du das ladekabel mit?", "liegt schon im rucksack",
                ),
                firstDate = "Montag",
                secondDate = "Gestern",
                incomingFirst = false,
            ) + conversation(
                "Heute", 8,
                `in`("bist du heute am campus?"), out("ja erst gummersbach und später deutz"), `in`("ah okay"),
                out("warum?"), `in`("wollte dir die unterlagen geben"), out("kann ich nachher mitnehmen"),
                `in`("super"), out("ich muss nur schauen welche bahn ich kriege"),
                `in`("fährst du wieder mit der rb25?"), out("wahrscheinlich"),
                `in`("die war gestern wieder spät"), out("ja klassiker 😅"), `in`("ich bin bis nachmittag da"),
                out("dann sollte das passen"), `in`("schreib einfach wenn du losfährst"),
                `in`("Sag mir kurz, wann du ungefähr in Deutz ankommst."),
            )
        ),
        ChatId.ANNE to datedAlternatingHistory(
            listOf(
                "hast du kurz zeit?", "ja was gibt's?", "wegen der präsentation", "die am donnerstag?",
                "genau", "ich kann den ersten teil übernehmen", "super, dann mache ich die auswertung",
                "passt", "treffen wir uns morgen?", "nach vier wäre gut", "café am campus?", "gern",
                "ich bin vielleicht fünf minuten später", "kein problem", "Kaffee um 16 Uhr passt", "bis dann",
            ),
            firstDate = "Mittwoch",
            secondDate = "Heute",
            incomingFirst = true,
        ),
        ChatId.ANNI to datedAlternatingHistory(
            listOf(
                "warst du heute da?", "nur in der ersten vorlesung", "gab es etwas wichtiges?", "zwei neue folien",
                "oh nein", "ist halb so wild", "kannst du sie mir schicken?", "ja wenn ich zuhause bin",
                "danke dir", "bist du morgen wieder da?", "wahrscheinlich", "dann sehen wir uns",
                "ich sitze wieder hinten", "wie immer 😄", "Ich schicke dir gleich die Folien", "perfekt",
            ),
            firstDate = "Dienstag",
            secondDate = "Heute",
            incomingFirst = false,
        ),
        ChatId.MILA to datedAlternatingHistory(
            listOf(
                "bist du gut angekommen?", "ja gerade eben", "war die bahn voll?", "ging tatsächlich",
                "seltenes glück", "wirklich 😄", "hast du morgen schon was vor?", "vormittags lernen",
                "später eine runde raus?", "gerne", "so gegen fünf?", "passt mir", "ich schreibe nochmal",
                "mach das", "Danke, bis später", "bis dann",
            ),
            firstDate = "Freitag",
            secondDate = "Heute",
            incomingFirst = true,
        ),
        ChatId.JONAS to datedAlternatingHistory(
            listOf(
                "wo sitzt du?", "zweiter stock", "bei den fenstern?", "ja ganz hinten", "ich sehe dich nicht",
                "warte ich stehe kurz auf", "ah jetzt", "hast du einen platz frei?", "direkt neben mir", "perfekt",
                "brauchst du noch kaffee?", "unbedingt", "ich hole zwei", "rettung", "Bin schon in der Bib", "komme hoch",
            ),
            firstDate = "Gestern",
            secondDate = "Heute",
            incomingFirst = false,
        ),
        ChatId.PROJECT to datedAlternatingHistory(
            listOf(
                "Ich habe die Agenda ergänzt.", "Danke, ich sehe sie.", "Wer übernimmt den Methodenteil?",
                "Kann ich machen.", "Dann nehme ich die Ergebnisse.", "Ich prüfe noch die Quellen.",
                "Bitte bis heute Abend.", "Schaffe ich.", "Das Diagramm ist jetzt aktuell.",
                "Sieht deutlich besser aus.", "Fehlt noch etwas?", "Nur die Zusammenfassung.",
                "Die schreibe ich nachher.", "Protokoll ist hochgeladen.", "Perfekt, danke euch.",
            ),
            firstDate = "Montag",
            secondDate = "Heute",
            incomingFirst = true,
        ),
    ).mapValues { (_, messages) -> Collections.unmodifiableList(messages) }

    private val chats: Map<ChatId, ChatSummary> = mapOf(
        ChatId.ANNA to ChatSummary(ChatId.ANNA, "Anna", "Sag mir kurz, wann du ungefähr in Deutz ankommst.", "A"),
        ChatId.LENA to ChatSummary(ChatId.LENA, "Lena", "nice, hör ich später rein", "L"),
        ChatId.ANNE to ChatSummary(ChatId.ANNE, "Anne", "bis dann", "A"),
        ChatId.ANNI to ChatSummary(ChatId.ANNI, "Anni", "perfekt", "A"),
        ChatId.PROJECT to ChatSummary(ChatId.PROJECT, "Projektgruppe", "Perfekt, danke euch.", "P"),
        ChatId.MILA to ChatSummary(ChatId.MILA, "Mila", "bis dann", "M"),
        ChatId.JONAS to ChatSummary(ChatId.JONAS, "Jonas", "komme hoch", "J"),
    )

    fun messages(id: ChatId): List<ChatMessage> = checkNotNull(histories[id])

    fun chat(id: ChatId): ChatSummary = checkNotNull(chats[id])
}
