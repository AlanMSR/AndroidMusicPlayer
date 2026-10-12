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

    @Query("UPDATE Song SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE Song SET uploader = :uploader WHERE id = :id")
    suspend fun updateUploader(id: Long, uploader: String)

    // Playlists
    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("SELECT * FROM Playlist WHERE id = :id")
    fun getPlaylist(id: Long): Flow<Playlist?>

    @Query("""
    SELECT s.* FROM Song s
    JOIN PlaylistSongCrossRef r ON r.songId = s.id
    WHERE r.playlistId = :playlistId
    ORDER BY r.position ASC
""")
    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>>

    @Query("SELECT * FROM Playlist ORDER BY name COLLATE NOCASE")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    // Linking
    @Query("""
        INSERT OR IGNORE INTO PlaylistSongCrossRef (playlistId, songId, position)
        SELECT :playlistId, :songId, COALESCE(MAX(position), -1) + 1
        FROM PlaylistSongCrossRef WHERE playlistId = :playlistId
    """)
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long)

    @Query("""
        SELECT p.id AS id, p.name AS name,
            (SELECT s.thumbnailPath FROM PlaylistSongCrossRef r
             JOIN Song s ON s.id = r.songId
             WHERE r.playlistId = p.id
             ORDER BY r.position ASC LIMIT 1) AS thumbnailPath
        FROM Playlist p
        ORDER BY p.name COLLATE NOCASE
    """)
    fun getPlaylistCovers(): Flow<List<PlaylistCover>>

    @Delete
    suspend fun removeSongFromPlaylist(ref: PlaylistSongCrossRef)

    @Transaction
    @Query("SELECT * FROM Playlist WHERE id = :playlistId")
    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?>

    @Query("SELECT playlistId FROM PlaylistSongCrossRef WHERE songId = :songId")
    fun getPlaylistIdsForSong(songId: Long): Flow<List<Long>>

    @Query("UPDATE Playlist SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)
}