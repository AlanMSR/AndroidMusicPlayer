package com.example.androidmusicplayer

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.androidmusicplayer.data.MusicDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

object QueueManager {
    private const val BEHIND = 10   // songs kept before the current one
    private const val AHEAD = 40    // songs loaded after it
    private const val CHUNK = 30    // how many to add at a time
    private const val LOW = 10      // refill when fewer than this remain ahead

    private var player: ExoPlayer? = null
    private var dao: MusicDao? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var sourceIds: List<Long> = emptyList()
    private var order = mutableListOf<Long>()   // full play order (grows by one cycle at a time)
    private var shuffleOn = false
    private var generation = 0
    private var busy = false

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = maintain()
    }

    fun attach(p: ExoPlayer, d: MusicDao) {
        player = p
        dao = d
        p.addListener(listener)
    }

    fun detach() {
        player?.removeListener(listener)
        player = null
        dao = null
        generation++
    }

    /** playlistId = null means the whole library. startId = null means "from the top" (or random if shuffling). */
    suspend fun play(playlistId: Long?, startId: Long?, shuffle: Boolean) {
        val p = player ?: return
        val d = dao ?: return

        val ids = withContext(Dispatchers.IO) {
            if (playlistId == null) d.getAllSongIds() else d.getPlaylistSongIds(playlistId)
        }
        if (ids.isEmpty()) return

        generation++
        busy = false
        sourceIds = ids
        shuffleOn = shuffle
        order = if (shuffle) {
            val s = ids.shuffled().toMutableList()
            val first = startId?.takeIf { it in ids }
            if (first != null) { s.remove(first); s.add(0, first) }
            s
        } else ids.toMutableList()

        val startPos = if (!shuffle && startId != null) order.indexOf(startId).coerceAtLeast(0) else 0
        val from = max(0, startPos - BEHIND)
        val to = min(order.size, startPos + AHEAD + 1)
        val items = fetch(d, order.subList(from, to).toList(), from)
        if (items.isEmpty()) return

        val startIndex = items.indexOfFirst { it.queuePos == startPos }.coerceAtLeast(0)
        p.shuffleModeEnabled = false   // order is already shuffled by us
        p.repeatMode = Player.REPEAT_MODE_OFF
        p.setMediaItems(items, startIndex, 0L)
        p.prepare()
        p.play()
        maintain()
    }

    private suspend fun fetch(d: MusicDao, ids: List<Long>, firstPos: Int): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val byId = d.getSongsByIds(ids).associateBy { it.id }
            ids.mapIndexedNotNull { i, id -> byId[id]?.toMediaItem(firstPos + i) }
        }

    private fun maintain() {
        val p = player ?: return
        val d = dao ?: return
        if (busy || p.mediaItemCount == 0) return

        // Trim what is far from the current song
        val idx = p.currentMediaItemIndex
        val count = p.mediaItemCount
        if (idx > BEHIND + LOW) p.removeMediaItems(0, idx - BEHIND)
        else if (count - 1 - idx > AHEAD + CHUNK) p.removeMediaItems(idx + AHEAD + 1, count)

        // Refill if we're getting close to an edge
        val c = p.mediaItemCount
        val i = p.currentMediaItemIndex
        val firstPos = p.getMediaItemAt(0).queuePos
        val lastPos = p.getMediaItemAt(c - 1).queuePos
        if (firstPos < 0 || lastPos < 0) return   // queue wasn't built by this manager

        val gen = generation
        when {
            c - 1 - i < LOW -> { busy = true; scope.launch { growForward(d, p, lastPos, gen) } }
            i < LOW / 2 && firstPos > 0 -> { busy = true; scope.launch { growBackward(d, p, firstPos, gen) } }
        }
    }

    private suspend fun growForward(d: MusicDao, p: ExoPlayer, lastPos: Int, gen: Int) {
        var from = lastPos + 1
        var items = emptyList<MediaItem>()
        var tries = 0
        while (items.isEmpty() && tries++ < 5 && gen == generation) {
            if (from >= order.size) extendOrder()   // end reached: start a new cycle
            val to = min(order.size, from + CHUNK)
            items = fetch(d, order.subList(from, to).toList(), from)
            from = to
        }
        val ok = gen == generation && items.isNotEmpty() && player === p
        if (ok) p.addMediaItems(items)
        busy = false
        if (ok) maintain()
    }

    private suspend fun growBackward(d: MusicDao, p: ExoPlayer, firstPos: Int, gen: Int) {
        val from = max(0, firstPos - CHUNK)
        val items = fetch(d, order.subList(from, firstPos).toList(), from)
        val ok = gen == generation && items.isNotEmpty() && player === p
        if (ok) p.addMediaItems(0, items)
        busy = false
        if (ok) maintain()
    }

    private fun extendOrder() {
        val next = if (shuffleOn) sourceIds.shuffled().toMutableList() else sourceIds.toMutableList()
        // avoid the same song twice in a row across the cycle boundary
        if (shuffleOn && next.size > 1 && next.first() == order.last()) next.add(next.removeAt(0))
        order.addAll(next)
    }
}