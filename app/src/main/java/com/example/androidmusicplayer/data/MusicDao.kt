package com.example.androidmusicplayer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // Songs
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSong(song: Song): Long   // returns -1 if the videoId already exists

    @Query("SELECT * FROM Song ORDER BY title COLLATE NOCASE")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM Song ORDER BY RANDOM() LIMIT 1")
    suspend fun randomSong(): Song?

    @Query("SELECT EXISTS(SELECT 1 FROM Song WHERE videoId = :videoId)")
    suspend fun songExists(videoId: String): Boolean

    @Delete
    suspend fun deleteSong(song: Song)

    // Playlists
    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("SELECT * FROM Playlist ORDER BY name COLLATE NOCASE")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    // Linking
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(ref: PlaylistSongCrossRef)

    @Delete
    suspend fun removeSongFromPlaylist(ref: PlaylistSongCrossRef)

    @Transaction
    @Query("SELECT * FROM Playlist WHERE id = :playlistId")
    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?>

    @Query("SELECT playlistId FROM PlaylistSongCrossRef WHERE songId = :songId")
    fun getPlaylistIdsForSong(songId: Long): Flow<List<Long>>
}