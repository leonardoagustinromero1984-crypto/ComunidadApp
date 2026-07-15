package com.comunidapp.app.data.remote.storage

object StoragePaths {
    /** Path oficial M02: ownership validado en Storage RLS. */
    fun userAvatar(userId: String, filename: String = "avatar.jpg"): String =
        "users/$userId/avatar/$filename"

    fun petPhoto(userId: String, petId: String) = "users/$userId/pets/$petId/photo.jpg"
    fun postImage(postId: String) = "posts/$postId/image.jpg"
    fun adoptionImage(adoptionId: String) = "adoptions/$adoptionId/image.jpg"
    fun lostFoundImage(postId: String) = "lost_found/$postId/image.jpg"
}
