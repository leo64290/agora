package com.bitchat.android.agora

/**
 * Format des messages AGORA dans le réseau BitChat (type de paquet 0x41).
 *
 * Un message = "AG1\n" puis une ligne par enregistrement. Les champs sont
 * séparés par le caractère invisible U+001F. L'auteur n'est JAMAIS écrit
 * dans le message : c'est l'expéditeur signé du paquet BitChat, donc
 * personne ne peut parler à la place d'un autre.
 *
 *   I␟id␟type␟créé_à␟clé_parent␟texte           une idée
 *   V␟clé_idée␟valeur␟à                          un vote (+1 / -1 / 0)
 *   Q␟id␟créé_à␟fin_à␟question␟choix1␞choix2…    une assemblée éclair
 *   B␟clé_assemblée␟choix␟à                      un bulletin d'assemblée
 *   F␟manche␟clé_idée␟à                          un pari Flair
 *   C␟clé_idée␟total␟à                           total d'applaudissements
 */
sealed class AgoraRecord {
    data class IdeaRec(val id: String, val kind: IdeaKind, val createdAt: Long, val parentKey: String?, val text: String) : AgoraRecord()
    data class VoteRec(val ideaKey: String, val value: Int, val at: Long) : AgoraRecord()
    data class AssemblyRec(val id: String, val createdAt: Long, val endsAt: Long, val question: String, val choices: List<String>) : AgoraRecord()
    data class BallotRec(val assemblyKey: String, val choice: Int, val at: Long) : AgoraRecord()
    data class BetRec(val round: Long, val ideaKey: String, val at: Long) : AgoraRecord()
    data class ClapRec(val ideaKey: String, val total: Int, val at: Long) : AgoraRecord()
}

object AgoraCodec {
    private const val MAGIC = "AG1"
    private const val FS = '\u001F'
    private const val LS = '\u001E'
    private const val MAX_RECORDS = 400
    private const val MAX_KEY = 64

    /** Retire les caractères de contrôle (sauts de ligne, séparateurs…) et coupe la longueur. */
    fun clean(text: String, max: Int): String {
        val sb = StringBuilder()
        for (ch in text) {
            if (ch < ' ' || ch == '\u007F') sb.append(' ') else sb.append(ch)
        }
        val collapsed = sb.toString().replace(Regex(" {2,}"), " ").trim()
        return if (collapsed.length <= max) collapsed else collapsed.substring(0, max).trim()
    }

    private fun cleanKey(k: String): String? {
        if (k.isEmpty() || k.length > MAX_KEY) return null
        if (k.any { !(it.isLetterOrDigit() || it == ':' || it == '-' || it == '_') }) return null
        return k
    }

    fun encode(records: List<AgoraRecord>): ByteArray {
        val sb = StringBuilder(MAGIC)
        for (r in records) {
            sb.append('\n')
            when (r) {
                is AgoraRecord.IdeaRec -> sb.append("I").append(FS).append(r.id).append(FS).append(r.kind.code).append(FS)
                    .append(r.createdAt).append(FS).append(r.parentKey ?: "").append(FS).append(clean(r.text, AgoraRules.MAX_TEXT))
                is AgoraRecord.VoteRec -> sb.append("V").append(FS).append(r.ideaKey).append(FS).append(r.value).append(FS).append(r.at)
                is AgoraRecord.AssemblyRec -> sb.append("Q").append(FS).append(r.id).append(FS).append(r.createdAt).append(FS)
                    .append(r.endsAt).append(FS).append(clean(r.question, AgoraRules.MAX_TEXT)).append(FS)
                    .append(r.choices.joinToString(LS.toString()) { clean(it, AgoraRules.MAX_CHOICE_TEXT) })
                is AgoraRecord.BallotRec -> sb.append("B").append(FS).append(r.assemblyKey).append(FS).append(r.choice).append(FS).append(r.at)
                is AgoraRecord.BetRec -> sb.append("F").append(FS).append(r.round).append(FS).append(r.ideaKey).append(FS).append(r.at)
                is AgoraRecord.ClapRec -> sb.append("C").append(FS).append(r.ideaKey).append(FS).append(r.total).append(FS).append(r.at)
            }
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /** Décode sans jamais planter : un enregistrement abîmé est simplement ignoré. */
    fun decode(payload: ByteArray): List<AgoraRecord> {
        val text = try { String(payload, Charsets.UTF_8) } catch (_: Exception) { return emptyList() }
        val lines = text.split('\n')
        if (lines.isEmpty() || lines[0] != MAGIC) return emptyList()
        val out = ArrayList<AgoraRecord>()
        for (line in lines.drop(1).take(MAX_RECORDS)) {
            val f = line.split(FS)
            val rec: AgoraRecord? = try {
                when (f.getOrNull(0)) {
                    "I" -> if (f.size >= 6) {
                        val id = cleanKey(f[1])
                        val kind = f[2].singleOrNull()?.let { IdeaKind.fromCode(it) }
                        val parent = if (f[4].isEmpty()) null else cleanKey(f[4])
                        val txt = clean(f.subList(5, f.size).joinToString(" "), AgoraRules.MAX_TEXT)
                        if (id != null && kind != null && txt.isNotEmpty() && (f[4].isEmpty() || parent != null))
                            AgoraRecord.IdeaRec(id, kind, f[3].toLong(), parent, txt) else null
                    } else null
                    "V" -> if (f.size == 4) {
                        val k = cleanKey(f[1]); val v = f[2].toInt()
                        if (k != null && v in -1..1) AgoraRecord.VoteRec(k, v, f[3].toLong()) else null
                    } else null
                    "Q" -> if (f.size == 6) {
                        val id = cleanKey(f[1])
                        val q = clean(f[4], AgoraRules.MAX_TEXT)
                        val choices = f[5].split(LS).map { clean(it, AgoraRules.MAX_CHOICE_TEXT) }
                        if (id != null && q.isNotEmpty() && choices.size in AgoraRules.MIN_CHOICES..AgoraRules.MAX_CHOICES && choices.all { it.isNotEmpty() })
                            AgoraRecord.AssemblyRec(id, f[2].toLong(), f[3].toLong(), q, choices) else null
                    } else null
                    "B" -> if (f.size == 4) {
                        val k = cleanKey(f[1]); val c = f[2].toInt()
                        if (k != null && c in 0 until AgoraRules.MAX_CHOICES) AgoraRecord.BallotRec(k, c, f[3].toLong()) else null
                    } else null
                    "F" -> if (f.size == 4) {
                        val k = cleanKey(f[2])
                        if (k != null) AgoraRecord.BetRec(f[1].toLong(), k, f[3].toLong()) else null
                    } else null
                    "C" -> if (f.size == 4) {
                        val k = cleanKey(f[1]); val t = f[2].toInt()
                        if (k != null && t in 0..AgoraRules.MAX_CLAPS_PER_PLAYER) AgoraRecord.ClapRec(k, t, f[3].toLong()) else null
                    } else null
                    else -> null
                }
            } catch (_: NumberFormatException) { null }
            if (rec != null) out.add(rec)
        }
        return out
    }

    /** Coupe une longue liste en plusieurs messages d'environ [maxBytes] octets. */
    fun encodeChunks(records: List<AgoraRecord>, maxBytes: Int = 1800): List<ByteArray> {
        if (records.isEmpty()) return emptyList()
        val chunks = ArrayList<ByteArray>()
        var current = ArrayList<AgoraRecord>()
        for (r in records) {
            current.add(r)
            if (encode(current).size > maxBytes && current.size > 1) {
                current.removeAt(current.size - 1)
                chunks.add(encode(current))
                current = arrayListOf(r)
            }
        }
        if (current.isNotEmpty()) chunks.add(encode(current))
        return chunks
    }
}
