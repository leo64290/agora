package com.bitchat.android.agora

import kotlin.math.sqrt

/**
 * Les « camps » cachés : l'appli regroupe les votants qui votent pareil.
 *
 * Méthode (proche de Polis) :
 *  1. On ne garde que les votants qui ont dit Pour ou Contre au moins 2 fois.
 *  2. S'il y en a moins de 5 : un seul camp.
 *  3. On prend les deux votants qui sont le plus souvent en désaccord : ce sont
 *     les « graines » des deux camps. S'ils sont d'accord plus d'une fois sur deux,
 *     tout le monde vote à peu près pareil : un seul camp.
 *  4. On range chacun près de la graine qui lui ressemble le plus, puis on affine
 *     (quelques tours de « k-moyennes »).
 *  5. Un camp de moins de 2 personnes : on revient à un seul camp.
 *
 * Les camps ne sont JAMAIS montrés aux joueurs : ils servent seulement à calculer
 * l'accord des idées et les points.
 */
class OpinionGroups(
    /** Camp (0 ou 1) de chaque votant rangé. Vide s'il n'y a qu'un camp. */
    val groupOf: Map<String, Int>,
    val groupCount: Int
)

object AgoraOpinion {

    fun compute(votes: Collection<Vote>, activeIdeas: Set<String>): OpinionGroups {
        // Vecteurs de votes (Pour/Contre seulement) par votant
        val byVoter = HashMap<String, HashMap<String, Int>>()
        for (v in votes) {
            if (v.value == 0 || v.ideaKey !in activeIdeas) continue
            byVoter.getOrPut(v.voterId) { HashMap() }[v.ideaKey] = v.value
        }
        val core = byVoter.filter { it.value.size >= 2 }.keys.sorted()
        if (core.size < AgoraRules.MIN_CLUSTER_VOTERS) return OpinionGroups(emptyMap(), 1)

        // Graines : la paire la plus en désaccord
        var bestA: String? = null
        var bestB: String? = null
        var bestDist = -1.0
        for (i in core.indices) for (j in i + 1 until core.size) {
            val d = distance(byVoter[core[i]]!!, byVoter[core[j]]!!) ?: continue
            if (d > bestDist + 1e-9) { bestDist = d; bestA = core[i]; bestB = core[j] }
        }
        if (bestA == null || bestB == null || bestDist < 0.5) return OpinionGroups(emptyMap(), 1)

        val ideas = activeIdeas.sorted()
        fun vec(voter: String): DoubleArray {
            val m = byVoter[voter]!!
            return DoubleArray(ideas.size) { (m[ideas[it]] ?: 0).toDouble() }
        }
        val vectors = core.associateWith { vec(it) }
        var centroids = arrayOf(vectors[bestA]!!.copyOf(), vectors[bestB]!!.copyOf())
        var assign = HashMap<String, Int>()
        repeat(10) {
            val next = HashMap<String, Int>()
            for (voter in core) {
                val v = vectors[voter]!!
                val s0 = cosine(v, centroids[0])
                val s1 = cosine(v, centroids[1])
                next[voter] = if (s1 > s0 + 1e-9) 1 else if (s0 > s1 + 1e-9) 0 else (assign[voter] ?: if (voter == bestB) 1 else 0)
            }
            val newCentroids = arrayOf(DoubleArray(ideas.size), DoubleArray(ideas.size))
            val counts = intArrayOf(0, 0)
            for ((voter, g) in next) {
                val v = vectors[voter]!!
                for (k in v.indices) newCentroids[g][k] += v[k]
                counts[g]++
            }
            for (g in 0..1) if (counts[g] > 0) for (k in ideas.indices) newCentroids[g][k] /= counts[g].toDouble()
            val stable = next == assign
            assign = next
            if (counts[0] > 0 && counts[1] > 0) centroids = newCentroids
            if (stable) return@repeat
        }
        val sizes = intArrayOf(0, 0)
        assign.values.forEach { sizes[it]++ }
        if (sizes[0] < 2 || sizes[1] < 2) return OpinionGroups(emptyMap(), 1)

        // Votants avec un seul vote : rangés seulement s'ils penchent nettement d'un côté
        val result = HashMap<String, Int>(assign)
        for ((voter, m) in byVoter) {
            if (voter in result || m.size != 1) continue
            val (idea, value) = m.entries.first()
            val k = ideas.indexOf(idea)
            if (k < 0) continue
            val s0 = value * centroids[0][k]
            val s1 = value * centroids[1][k]
            if (s0 - s1 > 0.4) result[voter] = 0 else if (s1 - s0 > 0.4) result[voter] = 1
        }
        return OpinionGroups(result, 2)
    }

    /** Part de désaccord sur les idées votées par les deux, ou null s'ils n'ont rien en commun. */
    fun distance(a: Map<String, Int>, b: Map<String, Int>): Double? {
        var common = 0
        var disagree = 0
        for ((k, va) in a) {
            val vb = b[k] ?: continue
            common++
            if (va != vb) disagree++
        }
        return if (common == 0) null else disagree.toDouble() / common
    }

    private fun cosine(a: DoubleArray, b: DoubleArray): Double {
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / (sqrt(na) * sqrt(nb))
    }
}
