package com.example.androidmusicplayer.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Junction
import androidx.room.Relation
import androidx.room.Index

@Entity(indices = [Index(value = ["videoId"], unique = true)])
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,          // YouTube id, prevents duplicates
    val title: String,
    val uploader: String,
    val durationSec: Long,
    val filePath: String,
    val thumbnailPath: String?,
    val sourceUrl: String,
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

// Links songs and playlists: this is what allows one song in many playlists
@Entity(
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(Playlist::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Song::class, ["id"], ["songId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("songId")]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val position: Int = 0
)

// Not a table, just the result shape of a "playlist with its songs" query
data class PlaylistWithSongs(
    @Embedded val playlist: Playlist,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistSongCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    val songs: List<Song>
)

data class PlaylistCover(
    val id: Long,
    val name: String,
    val thumbnailPath: String?
)